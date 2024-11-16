import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.*;

public class Console {

    private static Process serverProcess;
    private static BufferedReader serverOutputReader;
    private static BufferedWriter serverInputWriter;
    private static boolean serverRunning = false;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java Console <command> <directory>");
            return;
        }

        String command = args[0];  // Command like 'download', 'start', or 'modify-properties'
        String dir = args.length > 1 ? args[1] : ".";  // Default to current directory if not specified

        switch (command) {
            case "download":
                downloadServer(dir);
                break;
            case "start":
                startServer(dir);
                break;
            case "modify-properties":
                modifyServerProperties(dir);
                break;
            default:
                System.out.println("Unknown command: " + command);
                break;
        }
    }

    private static void downloadServer(String dir) {
        System.out.println("Downloading server...");

        // URL of the PaperMC JAR file (replace with appropriate version)
        String serverUrl = "https://api.papermc.io/v2/projects/paper/versions/1.21.1/builds/123/downloads/paper-1.21.1-123.jar";
        String destinationFile = dir + "/paper-1.21.1-123.jar";

        try {
            // Open connection to the URL
            @SuppressWarnings("deprecation")
            HttpURLConnection connection = (HttpURLConnection) new URL(serverUrl).openConnection();
            connection.setRequestMethod("GET");

            // Get the response code and check for success
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Open input stream to read the file
                InputStream inputStream = connection.getInputStream();
                FileOutputStream fileOutputStream = new FileOutputStream(destinationFile);
                byte[] buffer = new byte[4096];
                int bytesRead;

                // Read the file and write it to the destination
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }

                inputStream.close();
                fileOutputStream.close();

                System.out.println("Server JAR downloaded to: " + destinationFile);
            } else {
                System.out.println("Download failed. HTTP response code: " + responseCode);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error downloading server: " + e.getMessage());
        }

        // Now we can create the necessary files (e.g., EULA.txt)
        File eulaFile = new File(dir, "eula.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(eulaFile))) {
            writer.write("eula=true");
            System.out.println("EULA accepted.");
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create start script
        createStartScript(dir);
    }

    private static void startServer(String dir) {
        if (serverRunning) {
            System.out.println("Server is already running.");
            return;
        }

        try {
            System.out.println("Starting server in directory: " + dir);

            // Start the server process (e.g., running a Minecraft Paper server)
            ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar", "paper-1.21.1-123.jar", "nogui");
            processBuilder.directory(new File(dir));

            serverProcess = processBuilder.start();
            serverRunning = true;

            serverOutputReader = new BufferedReader(new InputStreamReader(serverProcess.getInputStream()));
            serverInputWriter = new BufferedWriter(new OutputStreamWriter(serverProcess.getOutputStream()));

            // Handle server output in a separate thread
            new Thread(() -> {
                try {
                    String line;
                    while ((line = serverOutputReader.readLine()) != null) {
                        System.out.println(line);  // Print server output
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    stopServer();
                }
            }).start();

            // Read commands from stdin
            handleServerInput();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleServerInput() {
        Scanner scanner = new Scanner(System.in);

        // Listening for user input
        while (serverRunning) {
            if (!serverProcess.isAlive()) {
                // If the server process is not alive, stop the program
                stopServer();
                break;
            }

            String input = scanner.nextLine();

            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                sendCommandToServer("stop");  // Send stop command
                break;
            } else {
                sendCommandToServer(input);
            }
        }
        scanner.close();
    }

    private static void sendCommandToServer(String command) {
        try {
            if (serverProcess.isAlive()) {
                serverInputWriter.write(command + "\n");
                serverInputWriter.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void stopServer() {
        try {
            if (serverRunning) {
                System.out.println("Stopping server...");
                serverProcess.destroy();  // Stop the server process
                serverRunning = false;

                // Close streams
                if (serverOutputReader != null) serverOutputReader.close();
                if (serverInputWriter != null) serverInputWriter.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.exit(0);  // Exit the program
        }
    }

    private static void createStartScript(String dir) {
        File startScript = new File(dir, "start.sh");
        String scriptContent = "#!/bin/bash\njava -jar paper-1.21.1-123.jar nogui";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(startScript))) {
            writer.write(scriptContent);
            System.out.println("Start script created at: " + startScript.getAbsolutePath());

            // Make the script executable on Unix-based systems
            if (System.getProperty("os.name").toLowerCase().contains("nix")) {
                startScript.setExecutable(true);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("deprecation")
    private static void modifyServerProperties(String dir) {
        String os = System.getProperty("os.name").toLowerCase();
        File serverProperties = new File(dir, "server.properties");

        if (!serverProperties.exists()) {
            System.out.println("server.properties not found in " + dir);
            return;
        }

        try {
            if (os.contains("win")) {
                // Open the server.properties file in Notepad on Windows
                Runtime.getRuntime().exec("notepad.exe " + serverProperties.getAbsolutePath());
            } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
                // Open the server.properties file in vi or nano on Unix-based systems
                String editor = "nano"; // You can switch to "vi" if preferred
                Runtime.getRuntime().exec(new String[]{editor, serverProperties.getAbsolutePath()});
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
