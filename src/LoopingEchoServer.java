import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * LoopingEchoServer - Tek Client, Çoklu Mesaj
 *
 * <p><b>BasicEchoServer'dan Farkı:</b>
 * <ul>
 *   <li>Tek mesaj yerine LOOP ile çoklu mesaj</li>
 *   <li>"quit" komutu ile client bağlantıyı kapatabilir</li>
 *   <li>Server kapanmaz, sadece client disconnect olur</li>
 * </ul>
 *
 * <p><b>Yeni Kavramlar:</b>
 * <ul>
 *   <li>while loop: Sürekli mesaj okuma</li>
 *   <li>null check: Client disconnect kontrolü</li>
 *   <li>equalsIgnoreCase: Case-insensitive karşılaştırma</li>
 *   <li>Graceful shutdown: "quit" komutu ile temiz kapanış</li>
 * </ul>
 *
 * <p><b>Test Senaryosu:</b>
 * <pre>
 * Terminal 1:
 *   javac LoopingEchoServer.java
 *   java LoopingEchoServer
 *
 * Terminal 2:
 *   nc localhost 8002
 *   Hello          → Echo: Hello
 *   How are you?   → Echo: How are you?
 *   quit           → (Bağlantı kapanır)
 * </pre>
 *
 * <p><b>Limitasyonlar:</b>
 * <ul>
 *   <li>Hala tek client (yeni client bağlanamaz)</li>
 *   <li>Server kapanıyor (bir client sonrası)</li>
 *   <li>Paralel işlem yok</li>
 * </ul>
 *
 * @author Gokhan D.
 * @version 1.0
 * @since 2026-02-05
 */
public class LoopingEchoServer {

  /**
   * Server port numarası.
   *
   * <p><b>Neden 8002?</b>
   * <ul>
   *   <li>BasicEchoServer 8001 kullanıyor</li>
   *   <li>Her server farklı port kullanmalı</li>
   *   <li>Aynı anda iki server çalıştırabilmek için</li>
   * </ul>
   */
  private static final int PORT = 8002;

  /**
   * Server'ın ana giriş noktası.
   *
   * <p><b>Akış:</b>
   * <ol>
   *   <li>ServerSocket oluştur (port 8002)</li>
   *   <li>Client bağlantısını bekle</li>
   *   <li>LOOP: Mesaj oku → Echo gönder</li>
   *   <li>"quit" gelirse loop'tan çık</li>
   *   <li>Bağlantıyı kapat</li>
   * </ol>
   *
   * @param args Komut satırı argümanları (kullanılmıyor)
   */
  public static void main(String[] args) {  // ✅ public ekledik

    System.out.println("Single Client, Multiple Messages");
    System.out.println("Starting TCP Server on port: " + PORT + "...\n");

    try (ServerSocket serverSocket = new ServerSocket(PORT)) {

      System.out.println("Server Listening on port " + PORT);
      System.out.println("Waiting for client connection... \n");

      /*
       * Client Bağlantısını Kabul Etme
       * -------------------------------
       * BasicEchoServer ile aynı (değişiklik yok)
       */
      Socket clientSocket = serverSocket.accept();
      System.out.println("Client Connected: " + clientSocket.getInetAddress());

      /*
       * Input/Output Stream'leri Açma
       * ------------------------------
       * BasicEchoServer ile aynı (değişiklik yok)
       */
      BufferedReader in = new BufferedReader(
          new InputStreamReader(clientSocket.getInputStream()));
      PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

      /*
       * ═══════════════════════════════════════════════════════════════════════
       * YENİ KAVRAM: Message Loop (Mesaj Döngüsü)
       * ═══════════════════════════════════════════════════════════════════════
       *
       * BasicEchoServer'da:
       * -------------------
       * String message = in.readLine();  // Tek mesaj
       * out.println("Echo: " + message);
       *
       * LoopingEchoServer'da:
       * ---------------------
       * while (koşul) {
       *     String message = in.readLine();  // Çoklu mesaj
       *     out.println("Echo: " + message);
       * }
       *
       * Loop Koşulu:
       * ------------
       * 1. message != null
       *    - readLine() null dönerse client bağlantıyı kapattı
       *    - EOF (End of File) durumu
       *    - Örnek: Client Ctrl+C ile kapandı
       *
       * 2. !message.equalsIgnoreCase("quit")
       *    - "quit" mesajı gelirse loop'tan çık
       *    - equalsIgnoreCase: "quit", "QUIT", "Quit" hepsi çalışır
       *    - Graceful shutdown (temiz kapanış)
       *
       * Neden && (AND) operatörü?
       * -------------------------
       * - İki koşul da true olmalı (devam etmek için)
       * - message null ise equalsIgnoreCase çağrılmaz (NullPointerException önleme)
       * - Short-circuit evaluation: İlk false görünce durur
       *
       * Örnek Senaryolar:
       * -----------------
       * 1. Normal mesaj:
       *    message = "Hello"
       *    message != null → true
       *    !message.equalsIgnoreCase("quit") → true
       *    → Loop devam eder
       *
       * 2. Quit komutu:
       *    message = "quit"
       *    message != null → true
       *    !message.equalsIgnoreCase("quit") → false
       *    → Loop durur
       *
       * 3. Client disconnect:
       *    message = null
       *    message != null → false
       *    → Loop durur (equalsIgnoreCase çağrılmaz)
       */
      String message;
      while ((message = in.readLine()) != null && !message.equalsIgnoreCase("quit")) {

        System.out.println("Received: " + message);

        /*
         * Echo Response Gönderme
         * ----------------------
         * BasicEchoServer ile aynı
         * Her mesaj için "Echo: " prefix ekle ve gönder
         */
        String response = "Echo: " + message;
        out.println(response);
        System.out.println("Sent: " + response);
      }

      /*
       * ═══════════════════════════════════════════════════════════════════════
       * YENİ KAVRAM: Graceful Shutdown (Temiz Kapanış)
       * ═══════════════════════════════════════════════════════════════════════
       *
       * Loop'tan çıkış sebepleri:
       * -------------------------
       * 1. Client "quit" gönderdi
       *    - message = "quit"
       *    - Kontrollü kapanış
       *
       * 2. Client bağlantıyı kapattı
       *    - message = null
       *    - Beklenmedik kapanış (Ctrl+C, network hatası vs)
       *
       * Hangi sebeple çıktığını kontrol edelim:
       * ----------------------------------------
       */
      if (message == null) {
        System.out.println("\nClient disconnected unexpectedly.");
      } else {
        System.out.println("\nClient sent 'quit' command.");
      }

      /*
       * Bağlantıyı Kapatma
       * ------------------
       * BasicEchoServer ile aynı
       * Socket'i kapat, kaynakları temizle
       */
      clientSocket.close();
      System.out.println("Server shutting down.");

    } catch (IOException ex) {
      /*
       * Exception Handling
       * ------------------
       * BasicEchoServer ile aynı
       *
       * Ek olası hatalar:
       * - Client beklenmedik şekilde kapandı (loop sırasında)
       * - Network timeout
       * - Stream kapalı
       */
      System.err.println("I/O Error: " + ex.getMessage());
      ex.printStackTrace();
    }

  }

}