package client;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;
import java.io.*;
import coordinator.CoordinatorInterface;
import common.User;

public class ClientApp {
    private static String token = null;
    private static String username = null;
    private static String role = null;

    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            CoordinatorInterface coordinator = (CoordinatorInterface) registry.lookup("CoordinatorService");

            Scanner sc = new Scanner(System.in);
            System.out.println("=== Distributed File System Client ===");
            System.out.print("Enter username: ");
            username = sc.nextLine();
            System.out.print("Enter password: ");
            String password = sc.nextLine();

            token = coordinator.login(username, password);
            if (token == null) {
                System.out.println("Login failed.");
                return;
            }

            System.out.println("Login successful.");
            while (true) {
                System.out.println("\n1. Upload File\n2. Request File\n3. Delete File\n4. Edit File\n5. Register New User (Manager only)\n6. Exit");
                System.out.print("Choose: ");
                int choice = sc.nextInt();
                sc.nextLine();

                switch (choice) {
                    case 1:
                        System.out.print("Enter full path of the file to upload: ");
                        String path = sc.nextLine();
                        File file = new File(path);
                        if (!file.exists()) {
                            System.out.println("File not found.");
                            break;
                        }

                        System.out.print("Enter filename to save as: ");
                        String filename = sc.nextLine();
                        byte[] data = new byte[(int) file.length()];
                        new FileInputStream(file).read(data);

                        if (coordinator.uploadFile(token, filename, data)) {
                            System.out.println("File uploaded successfully.");
                        } else {
                            System.out.println("Upload failed.");
                        }
                        break;

                    case 2:
                        System.out.print("Enter filename to request: ");
                        String fileToRequest = sc.nextLine();
                        byte[] received = coordinator.requestFile(token, fileToRequest);
                        if (received == null) {
                            System.out.println("File not found.");
                        } else {
                            File output = new File("downloads/" + fileToRequest);
                            output.getParentFile().mkdirs();
                            new FileOutputStream(output).write(received);
                            System.out.println("File downloaded to: " + output.getAbsolutePath());
                        }
                        break;
                    case 3:
                        System.out.print("Enter filename to delete: ");
                        String fileToDelete = sc.nextLine();
                        if (coordinator.deleteFile(token, fileToDelete)) {
                            System.out.println("File deleted.");
                        } else {
                            System.out.println("Deletion failed or unauthorized.");
                        }
                        break;

                    case 4:
                        System.out.print("Enter full path of new content file: ");
                        String newPath = sc.nextLine();
                        File newFile = new File(newPath);
                        if (!newFile.exists()) {
                            System.out.println("File not found.");
                            break;
                        }
                        System.out.print("Enter target filename to overwrite: ");
                        String targetFilename = sc.nextLine();
                        byte[] newData = new byte[(int) newFile.length()];
                        new FileInputStream(newFile).read(newData);
                        if (coordinator.editFile(token, targetFilename, newData)) {
                            System.out.println("File edited successfully.");
                        } else {
                            System.out.println("Edit failed.");
                        }
                        break;
                    case 5:
                        System.out.print("Enter new username: ");
                        String newUser = sc.nextLine();
                        System.out.print("Enter department: ( \"DEV\" _ \"UI\" ) ");
                        String dept = sc.nextLine();
                        User newUserObj = new User(newUser, "employee", dept);
                        boolean reg = coordinator.registerUser(newUserObj, token);
                        System.out.println(reg ? "User registered." : "Permission denied.");
                        break;

                    case 6:
                        System.out.println("Exiting.");
                        return;

                    default:
                        System.out.println("Invalid option.");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
