package client;

import coordinator.CoordinatorInterface;
import common.User;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ClientGUI {
    private static CoordinatorInterface coordinator;
    private static String token;
    private static String username;
    private static JFrame frame;

    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            coordinator = (CoordinatorInterface) registry.lookup("CoordinatorService");

            SwingUtilities.invokeLater(ClientGUI::showLoginWindow);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to connect to the server.");
        }
    }

    private static void showLoginWindow() {
        frame = new JFrame("Distributed File System - Login");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 200);
        frame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(3, 2));
        JTextField userField = new JTextField();
        JPasswordField passField = new JPasswordField();

        panel.add(new JLabel("Username:"));
        panel.add(userField);
        panel.add(new JLabel("Password:"));
        panel.add(passField);

        JButton loginBtn = new JButton("Login");
        panel.add(new JLabel()); // empty cell
        panel.add(loginBtn);

        loginBtn.addActionListener(e -> {
            username = userField.getText();
            String password = new String(passField.getPassword());
            try {
                token = coordinator.login(username, password);
                if (token != null) {
                    showMainWindow();
                    frame.dispose();
                } else {
                    JOptionPane.showMessageDialog(frame, "Login failed!");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        frame.add(panel);
        frame.setVisible(true);
    }

    private static void showMainWindow() {
        JFrame mainFrame = new JFrame("File System - Welcome " + username);
        mainFrame.setSize(400, 300);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel(new GridLayout(6, 1, 10, 10));

        JButton uploadBtn = new JButton("Upload File");
        JButton downloadBtn = new JButton("Download File");
        JButton deleteBtn = new JButton("Delete File");
        JButton editBtn = new JButton("Edit File");
        JButton registerBtn = new JButton("Register New User");
        JButton exitBtn = new JButton("Exit");

        panel.add(uploadBtn);
        panel.add(downloadBtn);
        panel.add(deleteBtn);
        panel.add(editBtn);
        panel.add(registerBtn);
        panel.add(exitBtn);

        uploadBtn.addActionListener(e -> uploadFile());
        downloadBtn.addActionListener(e -> downloadFile());
        deleteBtn.addActionListener(e -> deleteFile());
        editBtn.addActionListener(e -> editFile());
        registerBtn.addActionListener(e -> registerUser());
        exitBtn.addActionListener(e -> mainFrame.dispose());

        mainFrame.add(panel);
        mainFrame.setVisible(true);
    }

    private static void uploadFile() {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            String name = JOptionPane.showInputDialog("Save as filename:");
            try {
                byte[] data = new byte[(int) file.length()];
                new FileInputStream(file).read(data);
                boolean success = coordinator.uploadFile(token, name, data);
                JOptionPane.showMessageDialog(null, success ? "Uploaded!" : "Upload failed.");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private static void downloadFile() {
        String filename = JOptionPane.showInputDialog("Enter filename to download:");
        try {
            byte[] data = coordinator.requestFile(token, filename);
            if (data != null) {
                File output = new File("downloads/" + filename);
                output.getParentFile().mkdirs();
                new FileOutputStream(output).write(data);
                JOptionPane.showMessageDialog(null, "File saved to: " + output.getAbsolutePath());
            } else {
                JOptionPane.showMessageDialog(null, "File not found.");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void deleteFile() {
        String filename = JOptionPane.showInputDialog("Enter filename to delete:");
        try {
            boolean success = coordinator.deleteFile(token, filename);
            JOptionPane.showMessageDialog(null, success ? "Deleted." : "Failed or unauthorized.");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void editFile() {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            String name = JOptionPane.showInputDialog("Enter target filename to overwrite:");
            try {
                byte[] data = new byte[(int) file.length()];
                new FileInputStream(file).read(data);
                boolean success = coordinator.editFile(token, name, data);
                JOptionPane.showMessageDialog(null, success ? "Edited!" : "Edit failed.");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private static void registerUser() {
        String newUsername = JOptionPane.showInputDialog("New Username:");
        String department = JOptionPane.showInputDialog("Department (DEV or UI):");
        User user = new User(newUsername, "employee", department);
        try {
            boolean success = coordinator.registerUser(user, token);
            JOptionPane.showMessageDialog(null, success ? "User Registered." : "Permission denied.");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
