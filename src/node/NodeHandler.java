package node;

import java.io.*;
import java.net.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NodeHandler implements Runnable {
    private Socket socket;
    private String storageRoot;

    // خريطة لتزامن الوصول إلى الملفات باستخدام قفل لكل ملف
    private static final Map<String, Object> fileLocks = new ConcurrentHashMap<>();

    public NodeHandler(Socket socket, String storageRoot) {
        this.socket = socket;
        this.storageRoot = storageRoot;
    }

    public static Object getLockForFile(String filePath) {
        return fileLocks.computeIfAbsent(filePath, k -> new Object());
    }

    public void run() {
        try (
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            String command = in.readUTF();
            String department = in.readUTF();
            String filename = in.readUTF();

            switch (command) {
                case "upload": {
                    File file = new File(storageRoot + "/" + department + "/" + filename);
                    file.getParentFile().mkdirs();

                    int size = in.readInt();
                    byte[] data = new byte[size];
                    in.readFully(data);

                    // تأمين الوصول إلى الملف أثناء الكتابة
                    Object fileLockObj = getLockForFile(file.getAbsolutePath());
                    synchronized (fileLockObj) {
                        try (
                                RandomAccessFile raf = new RandomAccessFile(file, "rw");
                                FileChannel channel = raf.getChannel();
                                FileLock lock = channel.lock()) {
                            raf.setLength(0);
                            raf.write(data);
                            out.writeUTF("OK");
                        } catch (IOException e) {
                            e.printStackTrace();
                            out.writeUTF("Failed");
                        }
                    }
                    break;
                }

                case "read": {
                    File targetFile = null;

                    if ("any".equalsIgnoreCase(department)) {
                        File root = new File(storageRoot);
                        File[] dirs = root.listFiles(File::isDirectory);
                        if (dirs != null) {
                            for (File dir : dirs) {
                                File candidate = new File(dir, filename);
                                if (candidate.exists()) {
                                    targetFile = candidate;
                                    break;
                                }
                            }
                        }
                    } else {
                        targetFile = new File(storageRoot + "/" + department + "/" + filename);
                    }

                    if (targetFile == null || !targetFile.exists()) {
                        out.writeInt(-1);
                        break;
                    }

                    // تأمين الوصول إلى الملف أثناء القراءة
                    Object fileLockObj = getLockForFile(targetFile.getAbsolutePath());
                    synchronized (fileLockObj) {
                        try (
                                RandomAccessFile raf = new RandomAccessFile(targetFile, "r");
                                FileChannel channel = raf.getChannel();
                                FileLock lock = channel.lock(0L, Long.MAX_VALUE, true)) {
                            byte[] fileData = new byte[(int) raf.length()];
                            raf.readFully(fileData);
                            out.writeInt(fileData.length);
                            out.write(fileData);
                        }
                    }
                    break;
                }

                case "delete": {
                    File file = new File(storageRoot + "/" + department + "/" + filename);
                    if (file.exists() && file.delete()) {
                        out.writeUTF("Deleted");
                    } else {
                        out.writeUTF("Not Found");
                    }
                    break;
                }
                case "list": {
                    File root = new File(storageRoot);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(bos);

                    Map<String, List<String>> filesPerDept = new HashMap<>();

                    for (File deptDir : root.listFiles(File::isDirectory)) {
                        List<String> fileList = new ArrayList<>();
                        for (File f : deptDir.listFiles()) {
                            fileList.add(f.getName());
                        }
                        filesPerDept.put(deptDir.getName(), fileList);
                    }

                    oos.writeObject(filesPerDept);
                    oos.flush();
                    byte[] data = bos.toByteArray();
                    out.writeInt(data.length);
                    out.write(data);
                    break;
                }

                default:
                    out.writeUTF("Invalid command");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void synchronizeWithPeers(String thisNodeRoot, int thisPort, List<String> peerPorts) {
        System.out.println("Start the synchronization process on the node: " + thisPort);

        for (String peer : peerPorts) {
            try {
                String[] parts = peer.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);

                if (port == thisPort) continue;

                try (
                        Socket socket = new Socket(host, port);
                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        DataInputStream in = new DataInputStream(socket.getInputStream());
                ) {
                    out.writeUTF("list");
                    out.writeUTF(""); // dummy
                    out.writeUTF(""); // dummy

                    int size = in.readInt();
                    byte[] data = new byte[size];
                    in.readFully(data);

                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
                    Map<String, List<String>> theirFiles = (Map<String, List<String>>) ois.readObject();

                    for (Map.Entry<String, List<String>> entry : theirFiles.entrySet()) {
                        String dept = entry.getKey();
                        for (String filename : entry.getValue()) {
                            File localFile = new File(thisNodeRoot + "/" + dept + "/" + filename);
                            if (!localFile.exists()) {
                                downloadFileFromPeer(host, port, dept, filename, thisNodeRoot);
                                System.out.println("File synced: " + filename + " from node " + port);
                            }
                        }
                    }

                    System.out.println("Successfully synced with node: " + port);

                } catch (Exception e) {
                    System.err.println("Failed to connect or synchronize with node: " + port);
                    e.printStackTrace();
                }

            } catch (Exception e) {
                System.err.println("Error processing node address: " + peer);
                e.printStackTrace();
            }
        }

        System.out.println("Synchronization on node has ended: " + thisPort);
    }

    private static void downloadFileFromPeer(String host, int port, String dept, String filename, String storageRoot) {
        try (
                Socket socket = new Socket(host, port);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());
        ) {
            out.writeUTF("read");
            out.writeUTF(dept);
            out.writeUTF(filename);

            int size = in.readInt();
            if (size == -1) return;

            byte[] data = new byte[size];
            in.readFully(data);

            File file = new File(storageRoot + "/" + dept + "/" + filename);
            file.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
