import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class App extends JFrame {

    private static final String PAPERMC_URL = "https://api.papermc.io/v2/projects/paper/versions/1.21.1/builds/123/downloads/paper-1.21.1-123.jar";
    private static final String FILENAME = "paper-1.21.1-123.jar";
    private static final String EULA_FILENAME = "eula.txt";
    private static final String SERVER_PROPERTIES_FILENAME = "server.properties";

    private JTextField directoryField;
    private JProgressBar progressBar;
    private JTextArea consoleArea;

    private Process serverProcess;
    private BufferedWriter serverInputWriter;

    private File serverDirectory;

    public App() {
        setTitle("PaperMC Server Manager");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Ensure server is shut down on app close
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                stopServer();
            }
        });

        // UI Components
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(1, 2));

        directoryField = new JTextField();
        JButton chooseDirButton = new JButton("Choose Directory");
        panel.add(directoryField);
        panel.add(chooseDirButton);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        consoleArea = new JTextArea(10, 40);
        consoleArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(consoleArea);

        // Menu bar
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenu serverMenu = new JMenu("Server");

        JMenuItem downloadItem = new JMenuItem("Download Server");
        JMenuItem startItem = new JMenuItem("Start Server");
        JMenuItem stopItem = new JMenuItem("Stop Server");
        JMenuItem saveItem = new JMenuItem("Save All");
        JMenuItem sendCommandItem = new JMenuItem("Send Command");
        JMenuItem modifyPropertiesItem = new JMenuItem("Modify server.properties");

        // Add keyboard shortcuts
        downloadItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK));
        startItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK));
        stopItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        sendCommandItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        modifyPropertiesItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));

        // Add menu items
        fileMenu.add(downloadItem);
        fileMenu.add(modifyPropertiesItem);
        serverMenu.add(startItem);
        serverMenu.add(stopItem);
        serverMenu.add(saveItem);
        serverMenu.add(sendCommandItem);

        menuBar.add(fileMenu);
        menuBar.add(serverMenu);

        setJMenuBar(menuBar);

        // Button Actions
        chooseDirButton.addActionListener(e -> chooseDirectory());
        downloadItem.addActionListener(e -> downloadServer());
        startItem.addActionListener(e -> startServer());
        stopItem.addActionListener(e -> stopServer());
        saveItem.addActionListener(e -> saveAll());
        sendCommandItem.addActionListener(e -> sendCommand());
        modifyPropertiesItem.addActionListener(e -> openServerProperties());

        // Layout
        add(panel, BorderLayout.NORTH);
        add(progressBar, BorderLayout.SOUTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void chooseDirectory() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            serverDirectory = fileChooser.getSelectedFile();
            directoryField.setText(serverDirectory.getAbsolutePath());
        }
    }

    private void downloadServer() {
        String directory = directoryField.getText();
        if (directory.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a directory first!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        serverDirectory = new File(directory);
        if (!serverDirectory.exists()) {
            JOptionPane.showMessageDialog(this, "Directory does not exist!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        progressBar.setValue(0);
        new Thread(() -> {
            try {
                // Download the file
                File jarFile = new File(serverDirectory, FILENAME);
                downloadFileWithProgress(PAPERMC_URL, jarFile);

                // Create EULA file
                File eulaFile = new File(serverDirectory, EULA_FILENAME);
                try (FileWriter writer = new FileWriter(eulaFile)) {
                    writer.write("eula=true");
                }

                // Create start script
                createStartScript(serverDirectory);

                JOptionPane.showMessageDialog(this, "Server downloaded and setup successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }

    private void downloadFileWithProgress(String fileUrl, File outputFile) throws IOException {
        @SuppressWarnings("deprecation")
        HttpURLConnection connection = (HttpURLConnection) new URL(fileUrl).openConnection();
        connection.setRequestMethod("GET");

        int fileSize = connection.getContentLength();
        try (InputStream in = connection.getInputStream();
             FileOutputStream out = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            int totalBytesRead = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                int progress = (int) (((double) totalBytesRead / fileSize) * 100);
                SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
            }
        }

        if (connection.getResponseCode() != 200) {
            throw new IOException("Failed to download file: HTTP " + connection.getResponseCode());
        }
    }

    private void createStartScript(File outputDir) throws IOException {
        File scriptFile;
        String content;

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            scriptFile = new File(outputDir, "start.bat");
            content = "@echo off\njava -jar " + FILENAME + " nogui\npause";
        } else {
            scriptFile = new File(outputDir, "start.sh");
            content = "#!/bin/sh\njava -jar " + FILENAME + " nogui";
            scriptFile.setExecutable(true);
        }

        try (FileWriter writer = new FileWriter(scriptFile)) {
            writer.write(content);
        }
    }

    private void startServer() {
        String directory = directoryField.getText();
        if (directory.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a directory first!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File outputDir = new File(directory);
        String scriptFileName = System.getProperty("os.name").toLowerCase().contains("win") ? "start.bat" : "start.sh";
        File scriptFile = new File(outputDir, scriptFileName);

        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(scriptFile.getAbsolutePath());
                pb.directory(outputDir);
                serverProcess = pb.start();

                serverInputWriter = new BufferedWriter(new OutputStreamWriter(serverProcess.getOutputStream()));

                // Read process output
                BufferedReader reader = new BufferedReader(new InputStreamReader(serverProcess.getInputStream()));
                String line;

                while ((line = reader.readLine()) != null) {
                    appendToConsole(line);
                }

                reader.close();

                JOptionPane.showMessageDialog(this, "Server process completed.", "Info", JOptionPane.INFORMATION_MESSAGE);

            } catch (IOException ex) {
                appendToConsole("Error starting server: " + ex.getMessage());
            }
        }).start();
    }

    private void stopServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            try {
                serverInputWriter.write("stop\n");
                serverInputWriter.flush();
                serverProcess.destroy();
                appendToConsole("Server stopped.");
            } catch (IOException ex) {
                appendToConsole("Error stopping server: " + ex.getMessage());
            }
        }
    }

    private void saveAll() {
        if (serverProcess != null && serverProcess.isAlive()) {
            try {
                serverInputWriter.write("save-all\n");
                serverInputWriter.flush();
                appendToConsole("Save-all command sent.");
            } catch (IOException ex) {
                appendToConsole("Error sending save-all command: " + ex.getMessage());
            }
        }
    }

    private void sendCommand() {
        if (serverProcess != null && serverProcess.isAlive()) {
            String command = JOptionPane.showInputDialog(this, "Enter command to send to the server:");
            if (command != null && !command.trim().isEmpty()) {
                try {
                    serverInputWriter.write(command + "\n");
                    serverInputWriter.flush();
                    appendToConsole("Command sent: " + command);
                } catch (IOException ex) {
                    appendToConsole("Error sending command: " + ex.getMessage());
                }
            }
        } else {
            JOptionPane.showMessageDialog(this, "Server is not running!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openServerProperties() {
        File propertiesFile = new File(serverDirectory, SERVER_PROPERTIES_FILENAME);
        if (propertiesFile.exists()) {
            try {
                StringBuilder content = new StringBuilder();
                BufferedReader reader = new BufferedReader(new FileReader(propertiesFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();

                // Create a scrollable text area for the properties file content
                JTextArea textArea = new JTextArea(content.toString());
                textArea.setCaretPosition(0);  // Set cursor to the top of the file
                JScrollPane scrollPane = new JScrollPane(textArea);

                // Create the dialog for editing server.properties
                JDialog editDialog = new JDialog(this, "Edit server.properties", true);
                editDialog.setSize(500, 400);
                editDialog.setLocationRelativeTo(this);

                // Add Save button to the dialog
                JPanel dialogPanel = new JPanel(new BorderLayout());
                dialogPanel.add(scrollPane, BorderLayout.CENTER);

                JPanel buttonPanel = new JPanel();
                JButton saveButton = new JButton("Save");
                buttonPanel.add(saveButton);
                dialogPanel.add(buttonPanel, BorderLayout.SOUTH);

                saveButton.addActionListener(e -> {
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(propertiesFile))) {
                        writer.write(textArea.getText());
                        appendToConsole("server.properties updated.");
                        editDialog.dispose();
                    } catch (IOException ex) {
                        appendToConsole("Error saving server.properties: " + ex.getMessage());
                    }
                });

                editDialog.getContentPane().add(dialogPanel);
                editDialog.setVisible(true);

            } catch (IOException ex) {
                appendToConsole("Error opening server.properties: " + ex.getMessage());
            }
        } else {
            JOptionPane.showMessageDialog(this, "server.properties file does not exist!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void appendToConsole(String message) {
        SwingUtilities.invokeLater(() -> consoleArea.append(message + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            App manager = new App();
            manager.setVisible(true);
        });
    }
}
