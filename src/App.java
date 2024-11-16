import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class PaperMCServerManager extends JFrame {

    private static final String PAPERMC_URL = "https://api.papermc.io/v2/projects/paper/versions/1.21.1/builds/123/downloads/paper-1.21.1-123.jar";
    private static final String FILENAME = "paper-1.21.1-123.jar";
    private static final String EULA_FILENAME = "eula.txt";

    private JTextField directoryField;
    private JButton chooseDirButton, downloadButton, startServerButton;
    private JProgressBar progressBar;
    private JTextArea consoleArea;

    public PaperMCServerManager() {
        setTitle("PaperMC Server Manager");
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // UI Components
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(5, 1));

        JPanel dirPanel = new JPanel(new BorderLayout());
        directoryField = new JTextField();
        chooseDirButton = new JButton("Choose Directory");
        dirPanel.add(directoryField, BorderLayout.CENTER);
        dirPanel.add(chooseDirButton, BorderLayout.EAST);

        downloadButton = new JButton("Download Server");
        startServerButton = new JButton("Start Server");
        startServerButton.setEnabled(false);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        consoleArea = new JTextArea(10, 40);
        consoleArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(consoleArea);

        panel.add(new JLabel("Select Output Directory:"));
        panel.add(dirPanel);
        panel.add(downloadButton);
        panel.add(progressBar);
        panel.add(startServerButton);

        add(panel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // Button Actions
        chooseDirButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chooseDirectory();
            }
        });

        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                downloadServer();
            }
        });

        startServerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startServer();
            }
        });
    }

    private void chooseDirectory() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = fileChooser.getSelectedFile();
            directoryField.setText(selectedDir.getAbsolutePath());
        }
    }

    private void downloadServer() {
        String directory = directoryField.getText();
        if (directory.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a directory first!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File outputDir = new File(directory);
        if (!outputDir.exists()) {
            JOptionPane.showMessageDialog(this, "Directory does not exist!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        progressBar.setValue(0);
        new Thread(() -> {
            try {
                // Download the file
                File jarFile = new File(outputDir, FILENAME);
                downloadFileWithProgress(PAPERMC_URL, jarFile);

                // Create EULA file
                File eulaFile = new File(outputDir, EULA_FILENAME);
                try (FileWriter writer = new FileWriter(eulaFile)) {
                    writer.write("eula=true");
                }

                // Create start script
                createStartScript(outputDir);

                JOptionPane.showMessageDialog(this, "Server downloaded and setup successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                startServerButton.setEnabled(true);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }

    private void downloadFileWithProgress(String fileUrl, File outputFile) throws IOException {
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
            // Windows batch file
            scriptFile = new File(outputDir, "start.bat");
            content = "@echo off\njava -jar " + FILENAME + " nogui\npause";
        } else {
            // Unix shell script
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
                Process process = pb.start();

                // Read process output
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
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

    private void appendToConsole(String text) {
        SwingUtilities.invokeLater(() -> consoleArea.append(text + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PaperMCServerManager manager = new PaperMCServerManager();
            manager.setVisible(true);
        });
    }
}
