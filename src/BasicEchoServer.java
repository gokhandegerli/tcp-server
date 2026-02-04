import java.io.*;
import java.net.*;

public class BasicEchoServer {

  private static final int PORT = 8001;

  public static void main(String[] args) {

    System.out.println("Single Client, Single Message");
    System.out.println("Starting TCP Server on port: " + PORT + "...\n");

    // autoclosable bir serverSocket client olusturuyoruz
    try (ServerSocket serverSocket = new ServerSocket(PORT)) {

      System.out.println("Server Listening on port " + PORT);
      System.out.println("Waiting for client connection... \n");

      // Blocking sekilde client connection gelmesini bekliyor, baglanti saglaninca sonraki
      // satirlardan devam edecek
      Socket clientSocket = serverSocket.accept();
      System.out.println("Client Connected: " + clientSocket.getInetAddress());

      BufferedReader in = new BufferedReader(
          new InputStreamReader(clientSocket.getInputStream()));

      PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);




    } catch (IOException e) {
      throw new RuntimeException(e);
    }


  }


}