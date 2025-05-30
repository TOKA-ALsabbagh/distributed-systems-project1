package coordinator;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.util.*;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;

import common.User;
import common.TokenManager;



public class CoordinatorImpl extends UnicastRemoteObject implements CoordinatorInterface {
    private Map<String, String> credentials = new HashMap<>();
    private Map<String, User> users = new HashMap<>();
    private final Map<String, Integer> nodeLoadMap = new ConcurrentHashMap<>();

    //    private List<String> nodeIPs = Arrays.asList("127.0.0.1");
private List<String> nodeIPs = Arrays.asList("127.0.0.1:5000", "127.0.0.1:5001", "127.0.0.1:5002");


    public CoordinatorImpl() throws RemoteException {
        // Sample admin
        credentials.put("admin", "admin123");
        users.put("admin", new User("admin", "manager", "all"));
    }

    @Override
    public String login(String username, String password) throws RemoteException {
        if (credentials.containsKey(username) && credentials.get(username).equals(password)) {
            return TokenManager.generateToken(users.get(username));
        }
        return null;
    }

    @Override
    public boolean registerUser(User user, String adminToken) throws RemoteException {
        User admin = TokenManager.validateToken(adminToken);
        if (admin != null && "manager".equals(admin.getRole())) {
            credentials.put(user.getUsername(), "1234"); // Default password
            users.put(user.getUsername(), user);
            return true;
        }
        return false;
    }

    @Override
    public boolean uploadFile(String token, String filename, byte[] data) throws RemoteException {
        User user = TokenManager.validateToken(token);
        if (user == null) return false;

        String department = user.getDepartment();
        int successCount = 0;
        int requiredSuccess = Math.max(1, nodeIPs.size() / 2 + 1); // مثال: 2 من 3

        for (String ipPort : nodeIPs) {
            String[] parts = ipPort.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            try (
                    Socket socket = new Socket(ip, port);
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream());
            ) {
                out.writeUTF("upload");
                out.writeUTF(department);
                out.writeUTF(filename);
                out.writeInt(data.length);
                out.write(data);

                String response = in.readUTF();
                if ("OK".equals(response)) {
                    successCount++;
                    System.out.println("Uploaded to node: " + ipPort);
                } else {
                    System.out.println("Failed on node: " + ipPort);
                }
            } catch (IOException e) {
                System.out.println("Node unreachable: " + ipPort);
            }

            // إذا تحقق العدد المطلوب من النجاح نعتبر العملية ناجحة
            if (successCount >= requiredSuccess) {
                return true;
            }
        }

        return false; // لم تحقق العملية النصاب المطلوب
    }
    @Override
    public boolean deleteFile(String token, String filename) throws RemoteException {
        User user = TokenManager.validateToken(token);
        if (user == null) return false;

        String department = user.getDepartment();
        int successCount = 0;
        int requiredSuccess = Math.max(1, nodeIPs.size() / 2 + 1); // على الأقل 2 من 3

        for (String ipPort : nodeIPs) {
            String[] parts = ipPort.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            try (
                    Socket socket = new Socket(ip, port);
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream())
            ) {
                out.writeUTF("delete");
                out.writeUTF(department);
                out.writeUTF(filename);

                String response = in.readUTF();
                if ("Deleted".equals(response)) {
                    successCount++;
                    System.out.println("Deleted from node: " + ipPort);
                }
            } catch (IOException e) {
                System.out.println("Failed to delete from node: " + ipPort);
            }
            if (successCount >= requiredSuccess) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean editFile(String token, String filename, byte[] newData) throws RemoteException {
        User user = TokenManager.validateToken(token);
        if (user == null) return false;

        String department = user.getDepartment();
        boolean fileExists = false;

        for (String ipPort : nodeIPs) {
            String[] parts = ipPort.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            try (
                    Socket socket = new Socket(ip, port);
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream())
            ) {
                out.writeUTF("read");
                out.writeUTF(department);
                out.writeUTF(filename);

                int size = in.readInt();
                if (size != -1) {
                    fileExists = true;
                    break; // كافٍ وجود الملف في عقدة واحدة
                }
            } catch (IOException e) {
                System.out.println("Node unreachable during edit check: " + ipPort);
            }
        }
        if (!fileExists) return false;
        // تعديل الملف عبر إعادة رفعه باستخدام منطق التحمل
        return uploadFile(token, filename, newData);
    }


    @Override
    public byte[] requestFile(String token, String filename) throws RemoteException {
        User user = TokenManager.validateToken(token);
        if (user == null) return null;
        // تهيئة الأحمال للعقد إذا لم تكن موجودة
        synchronized (nodeLoadMap) {
            for (String node : nodeIPs) {
                nodeLoadMap.putIfAbsent(node, 0);
            }
        }
        // ترتيب العقد حسب الحمل الأقل
        List<String> sortedNodes = new ArrayList<>(nodeIPs);
        sortedNodes.sort(Comparator.comparingInt(nodeLoadMap::get));

        ExecutorService executor = Executors.newFixedThreadPool(nodeIPs.size());
        CompletionService<byte[]> completionService = new ExecutorCompletionService<>(executor);

        for (String ipPort : sortedNodes) {
            completionService.submit(() -> {
                nodeLoadMap.put(ipPort, nodeLoadMap.get(ipPort) + 1); // زيادة الحمل

                String[] parts = ipPort.split(":");
                String ip = parts[0];
                int port = Integer.parseInt(parts[1]);

                try (
                        Socket socket = new Socket(ip, port);
                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        DataInputStream in = new DataInputStream(socket.getInputStream())
                ) {
                    out.writeUTF("read");
                    out.writeUTF("any");
                    out.writeUTF(filename);

                    int size = in.readInt();
                    if (size == -1) return null;

                    byte[] data = new byte[size];
                    in.readFully(data);
                    return data;
                } catch (IOException e) {
                    return null;
                } finally {
                    nodeLoadMap.put(ipPort, nodeLoadMap.get(ipPort) - 1); // تقليل الحمل بعد الانتهاء
                }
            });
        }
        try {
            for (int i = 0; i < sortedNodes.size(); i++) {
                Future<byte[]> future = completionService.take(); // أول نتيجة
                byte[] result = future.get();
                if (result != null) {
                    executor.shutdownNow();
                    return result;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
        return null;
    }

}
