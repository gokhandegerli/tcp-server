import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * BasicTerminalServer - A simple TCP server that executes system commands.
 *
 * <p>This server accepts a single client connection and allows the client to execute
 * system commands remotely. The server reads commands from the client, executes them
 * using the system shell, and sends back the output.</p>
 *
 * <p><strong>⚠️ WARNING:</strong> This server is for educational purposes only!
 * It has NO authentication, NO encryption, and allows execution of ANY system command.
 * Only run this on localhost for learning purposes.</p>
 *
 * <h3>Architecture:</h3>
 * <pre>
 * Client (nc/telnet) ←→ TCP Socket ←→ BasicTerminalServer ←→ System Shell (sh/cmd)
 * </pre>
 *
 * <h3>Usage:</h3>
 * <pre>
 * 1. Compile: javac BasicTerminalServer.java
 * 2. Run:     java BasicTerminalServer
 * 3. Connect: nc localhost 8003
 * 4. Execute: ls -la, pwd, whoami, etc.
 * 5. Exit:    type "exit"
 * </pre>
 *
 * @author Gokhan D.
 * @version 1.0
 * @since 2026-02-05
 */
public class BasicTerminalServer {

  /**
   * The port number on which the server listens for incoming connections.
   */
  private static final int PORT = 8003;


  /**
   * Main entry point for the BasicTerminalServer.
   *
   * <p>This method creates a ServerSocket, waits for a single client connection,
   * and then enters a command processing loop. Each command received from the client
   * is executed using the system shell, and the output is sent back to the client.</p>
   *
   * <h3>Flow:</h3>
   * <ol>
   *   <li>Create ServerSocket on PORT 8003</li>
   *   <li>Wait for client connection (blocking)</li>
   *   <li>Setup input/output streams</li>
   *   <li>Send welcome message</li>
   *   <li>Read commands in a loop</li>
   *   <li>Execute each command and send output</li>
   *   <li>Close connection on "exit" command</li>
   * </ol>
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    System.out.println("⚠️ Terminal Server - Only for Local!");
    System.out.println("Starting Terminal Server on port: " + PORT + "...\n");


    try (ServerSocket serverSocket = new ServerSocket(PORT)) {

      System.out.println("Server Listening on port " + PORT);
      System.out.println("Waiting for client connection... \n");

      // Blocking call - waits until a client connects
      Socket clientSocket = serverSocket.accept();
      System.out.println("Accepted connection from client: " + clientSocket.getInetAddress());

      // Setup input stream to read commands from client
      BufferedReader in = new BufferedReader(
          new InputStreamReader(clientSocket.getInputStream()));

      // Setup output stream to send results to client (auto-flush enabled)
      PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

      // Send welcome banner to client
      out.println("=================================");
      out.println("  Basic Terminal Server v1.0");
      out.println("  Type 'exit' to quit");
      out.println("=================================");
      out.println();

      // Command processing loop
      String command;
      while ((command = in.readLine()) != null) {

        System.out.println("Received command: " + command);

        // Check for exit command
        if (command.equalsIgnoreCase("exit")) {
          out.println("Terminal Server shutting down.");
          out.println("GoodBye!");
          break;
        }

        // Execute the command and send output to client
        try {
          executeCommand(command, out);
        } catch (Exception e) {
          out.println("ERROR: " + e.getMessage());
        }

        // Send prompt for next command
        out.println();
        out.println("$ "); // Prompt
      }


    } catch (IOException e) {
      System.err.println("Server error: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Executes a system command and sends the output to the client.
   *
   * <p>This method uses {@link ProcessBuilder} to execute commands through the system shell.
   * On Windows, it uses "cmd.exe /c", and on Linux/Mac, it uses "sh -c".</p>
   *
   * <h3>Process Flow:</h3>
   * <pre>
   * Java Program                    System Process
   * ─────────────                   ──────────────
   * executeCommand()
   *     │
   *     ├─→ ProcessBuilder.start()  → Creates "sh -c ls"
   *     │
   *     ├─→ getInputStream()        ← Process stdout (normal output)
   *     │                             "file1.txt"
   *     │                             "file2.txt"
   *     │
   *     ├─→ getErrorStream()        ← Process stderr (error output)
   *     │                             "ls: cannot access..."
   *     │
   *     └─→ waitFor()               ← Wait for process to finish
   *                                   Exit code: 0 (success) or non-zero (error)
   * </pre>
   *
   * <h3>Stream Perspective:</h3>
   * <ul>
   *   <li><strong>process.getInputStream()</strong> - Reads the process's stdout
   *       (from Java's perspective, this is INPUT)</li>
   *   <li><strong>process.getErrorStream()</strong> - Reads the process's stderr
   *       (error output from the command)</li>
   *   <li><strong>process.getOutputStream()</strong> - Writes to the process's stdin
   *       (not used in this implementation)</li>
   * </ul>
   *
   * @param command the system command to execute (e.g., "ls -la", "pwd", "whoami")
   * @param out the PrintWriter to send command output to the client
   * @throws IOException if an I/O error occurs while reading process output
   *
   * @see ProcessBuilder
   * @see Process#getInputStream()
   * @see Process#getErrorStream()
   * @see Process#waitFor()
   */
  private static void executeCommand(String command, PrintWriter out) throws IOException {
    // Detect operating system
    String os = System.getProperty("os.name").toLowerCase();
    ProcessBuilder processBuilder;

    if (os.contains("win")) {
      // Windows: use cmd.exe to execute command
      processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
    } else {
      // Linux/Mac: use sh to execute command
      processBuilder = new ProcessBuilder("sh", "-c", command);
    }

    // Start the process
    Process process = processBuilder.start();

    // Read normal output from process (stdout)
    // Note: getInputStream() reads the PROCESS's OUTPUT (stdout)
    BufferedReader processOutput = new BufferedReader(
        new InputStreamReader(process.getInputStream()));

    // Read error output from process (stderr)
    BufferedReader errorOutput = new BufferedReader(
        new InputStreamReader(process.getErrorStream()));

    // Send normal output to client
    String line;
    while ((line = processOutput.readLine()) != null) {
      out.println(line);
    }

    // Send error output to client
    while ((line = errorOutput.readLine()) != null) {
      out.println("ERROR: " + line);
    }

    // Wait for process to complete and check exit code
    try {
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        out.println("[Process exited with code: " + exitCode + "]");
      }
    } catch (InterruptedException e) {
      out.println("Command interrupted!");
    }
  }

}