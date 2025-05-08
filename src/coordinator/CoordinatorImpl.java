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

        // ساويت يبعت للعقد التلاتة وفينك تعدل انو يبلش اول عقدة اذا فشلت ينتقل للتانية وهكذا
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

                // طريقة تانية
//                if ("OK".equals(response)) {
//                    return true;
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//               // هون بتعالج في حال فشلت اول عقدة بتتجاهلها وبتنتقل للتانية
//            }
//        }
//
//        return false;
//    }
                if ("OK".equals(response)) {
                    System.out.println("File uploaded to node: " + ip);
                } else {
                    System.out.println("Failed to upload to node: " + ip);
                    return false;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        return true;  // تم رفع الملف بنجاح إلى جميع العقد
    }
    @Override
    public boolean deleteFile(String token, String filename) throws RemoteException {
        User user = TokenManager.validateToken(token);
        if (user == null) return false;

        String department = user.getDepartment();

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
                if ("Deleted".equals(response)) return true;
            } catch (IOException e) {
                e.printStackTrace();
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
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (!fileExists) {
            return false;
        }

        return uploadFile(token, filename, newData);
    }


    @Override
public byte[] requestFile(String token, String filename) throws RemoteException {
    User user = TokenManager.validateToken(token);
    if (user == null) return null;

    ExecutorService executor = Executors.newFixedThreadPool(nodeIPs.size());
    CompletionService<byte[]> completionService = new ExecutorCompletionService<>(executor);

        for (String ipPort : nodeIPs) {
            String[] parts = ipPort.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            completionService.submit(() -> {
                try (
                        Socket socket = new Socket(ip, port); //
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
            }
        });
    }

    try {
        for (int i = 0; i < nodeIPs.size(); i++) {
            Future<byte[]> future = completionService.take(); // أول من يرد
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
