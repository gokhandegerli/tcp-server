import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * BasicEchoServer - En Basit TCP Server Implementasyonu
 *
 * <p>Bu server şunları yapar:
 * <ul>
 *   <li>Tek bir client bağlantısını kabul eder</li>
 *   <li>Client'tan tek bir mesaj alır</li>
 *   <li>Mesajı "Echo: " prefix'i ile geri gönderir</li>
 *   <li>Bağlantıyı kapatır ve server'ı durdurur</li>
 * </ul>
 *
 * <p><b>Öğrenilen Kavramlar:</b>
 * <ul>
 *   <li>ServerSocket: TCP server oluşturma</li>
 *   <li>Socket: Client bağlantısını temsil eder</li>
 *   <li>InputStream/OutputStream: Network üzerinden veri okuma/yazma</li>
 *   <li>BufferedReader: Satır satır text okuma</li>
 *   <li>PrintWriter: Text yazma (auto-flush ile)</li>
 *   <li>Blocking I/O: accept() ve readLine() bekler</li>
 * </ul>
 *
 * <p><b>Nasıl Test Edilir:</b>
 * <pre>
 * Terminal 1:
 *   javac BasicEchoServer.java
 *   java BasicEchoServer
 *
 * Terminal 2:
 *   nc localhost 8001
 *   Hello Server  (Enter'a bas)
 *   Echo: Hello Server  (Server'dan gelen cevap)
 * </pre>
 *
 * <p><b>Limitasyonlar:</b>
 * <ul>
 *   <li>Sadece 1 client kabul eder</li>
 *   <li>Sadece 1 mesaj alır</li>
 *   <li>Mesaj sonrası server kapanır</li>
 *   <li>Paralel bağlantı yok (sequential)</li>
 * </ul>
 *
 * <p><b>Kavram Özeti:</b>
 * <table border="1">
 *   <tr>
 *     <th>Kavram</th>
 *     <th>Ne Yapar?</th>
 *     <th>Neden Kullanıyoruz?</th>
 *   </tr>
 *   <tr>
 *     <td>ServerSocket</td>
 *     <td>Port dinler, client kabul eder</td>
 *     <td>TCP server oluşturmak için</td>
 *   </tr>
 *   <tr>
 *     <td>Socket</td>
 *     <td>Belirli bir client ile iletişim</td>
 *     <td>Client ile veri alışverişi</td>
 *   </tr>
 *   <tr>
 *     <td>InputStream</td>
 *     <td>Network'ten byte okur</td>
 *     <td>Raw data almak için</td>
 *   </tr>
 *   <tr>
 *     <td>InputStreamReader</td>
 *     <td>Byte → char çevirir</td>
 *     <td>Encoding (UTF-8) için</td>
 *   </tr>
 *   <tr>
 *     <td>BufferedReader</td>
 *     <td>Char → String, satır okur</td>
 *     <td>Kullanım kolaylığı</td>
 *   </tr>
 *   <tr>
 *     <td>PrintWriter</td>
 *     <td>String → byte, gönderir</td>
 *     <td>Text göndermek için</td>
 *   </tr>
 *   <tr>
 *     <td>Auto-flush</td>
 *     <td>Her println() sonrası gönder</td>
 *     <td>Hemen cevap için</td>
 *   </tr>
 *   <tr>
 *     <td>try-with-resources</td>
 *     <td>Otomatik kaynak temizliği</td>
 *     <td>Memory leak önleme</td>
 *   </tr>
 *   <tr>
 *     <td>Blocking</td>
 *     <td>İşlem bitene kadar bekler</td>
 *     <td>Basit, sıralı işlem</td>
 *   </tr>
 * </table>
 *
 * @author [Senin Adın]
 * @version 1.0
 * @since 2026-02-04
 */
public class BasicEchoServer {

  /**
   * Server'ın dinleyeceği port numarası.
   *
   * <p><b>Port Numarası Seçimi:</b>
   * <ul>
   *   <li>0-1023: System ports (root gerektirir) - HTTP:80, HTTPS:443</li>
   *   <li>1024-49151: Registered ports (bazıları kullanımda) - MySQL:3306, PostgreSQL:5432</li>
   *   <li>49152-65535: Dynamic ports (güvenli) - Geçici bağlantılar için</li>
   *   <li>8001: Development için yaygın (8080, 8000 gibi)</li>
   * </ul>
   *
   * <p><b>Neden 8001?</b>
   * <ul>
   *   <li>Root yetkisi gerektirmez</li>
   *   <li>Genelde boş (çakışma riski düşük)</li>
   *   <li>Development standartlarına uygun</li>
   *   <li>8080 (Tomcat) ile karışmaz</li>
   * </ul>
   */
  private static final int PORT = 8001;

  /**
   * Server'ın ana giriş noktası.
   *
   * <p><b>Execution Flow (Çalışma Akışı):</b>
   * <ol>
   *   <li>ServerSocket oluştur (port 8001'i dinle)</li>
   *   <li>Client bağlantısını bekle (BLOCKING)</li>
   *   <li>Client bağlandığında Socket al</li>
   *   <li>Input/Output stream'leri aç</li>
   *   <li>Mesaj oku, echo gönder</li>
   *   <li>Bağlantıyı kapat</li>
   * </ol>
   *
   * @param args Komut satırı argümanları (kullanılmıyor)
   */
  public static void main(String[] args) {

    System.out.println("Single Client, Single Message");
    System.out.println("Starting TCP Server on port: " + PORT + "...\n");

    /*
     * ═══════════════════════════════════════════════════════════════════════
     * KAVRAM 1: try-with-resources (Otomatik Kaynak Yönetimi)
     * ═══════════════════════════════════════════════════════════════════════
     *
     * try-with-resources nedir?
     * - Java 7'den beri var
     * - AutoCloseable interface'ini implement eden sınıflar için
     * - Try bloğu bitince otomatik close() çağrılır
     * - Exception olsa bile kaynak temizlenir
     *
     * Eski yol (Java 6 ve öncesi):
     * --------------------------------
     * ServerSocket serverSocket = null;
     * try {
     *     serverSocket = new ServerSocket(8001);
     *     // ... işlemler ...
     * } catch (IOException e) {
     *     e.printStackTrace();
     * } finally {
     *     if (serverSocket != null) {
     *         try {
     *             serverSocket.close();  // Manuel kapatma
     *         } catch (IOException e) {
     *             e.printStackTrace();
     *         }
     *     }
     * }
     *
     * Yeni yol (Java 7+):
     * -------------------
     * try (ServerSocket serverSocket = new ServerSocket(8001)) {
     *     // ... işlemler ...
     * }  // Otomatik kapanır!
     *
     * Avantajlar:
     * - Daha az kod (boilerplate azalır)
     * - Memory leak riski azalır
     * - Exception handling daha güvenli
     * - Okunabilirlik artar
     *
     * Neden önemli?
     * - ServerSocket bir sistem kaynağı (port tutar)
     * - Kapatılmazsa port "Address already in use" hatası verir
     * - Çoklu kaynak yönetiminde kritik (Socket, Stream vs)
     */
    try (ServerSocket serverSocket = new ServerSocket(PORT)) {

      System.out.println("Server Listening on port " + PORT);
      System.out.println("Waiting for client connection... \n");

      /*
       * ═══════════════════════════════════════════════════════════════════════
       * KAVRAM 2: ServerSocket vs Socket (Kapı vs Telefon Hattı)
       * ═══════════════════════════════════════════════════════════════════════
       *
       * ServerSocket nedir?
       * - TCP server'ın "kapısı"
       * - Belirli bir port'u dinler (8001)
       * - Gelen bağlantıları kabul eder (accept)
       * - Sadece 1 tane olur (port başına)
       *
       * Socket nedir?
       * - Belirli bir client ile "telefon hattı"
       * - Her client için ayrı Socket oluşur
       * - Veri alışverişi bu Socket üzerinden olur
       * - Çoklu client = Çoklu Socket
       *
       * Analoji:
       * --------
       * ServerSocket = Restoran girişi
       *   ↓ accept()
       * Socket = Müşterinin masası (1-1 hizmet)
       *
       * Kod Örneği:
       * -----------
       * ServerSocket server = new ServerSocket(8001);  // 1 tane (kapı)
       * Socket client1 = server.accept();              // 1. müşteri
       * Socket client2 = server.accept();              // 2. müşteri
       * Socket client3 = server.accept();              // 3. müşteri
       *
       * Her Socket farklı client'ı temsil eder!
       *
       * accept() metodu:
       * ----------------
       * - BLOCKING: Client bağlanana kadar bekler
       * - Client bağlandığında yeni Socket döner
       * - Bu Socket ile client'a özel iletişim kurulur
       * - ServerSocket açık kalır (yeni client'lar için)
       */
      Socket clientSocket = serverSocket.accept();
      System.out.println("Client Connected: " + clientSocket.getInetAddress());

      /*
       * ═══════════════════════════════════════════════════════════════════════
       * KAVRAM 3: Input Stream Katmanları (Network → Java String)
       * ═══════════════════════════════════════════════════════════════════════
       *
       * Neden 3 katman?
       * ---------------
       * Network'ten gelen veri byte (sayı) formatında gelir.
       * Biz String (text) olarak kullanmak istiyoruz.
       * Bu dönüşüm 3 aşamada yapılır:
       *
       * Katman 1: InputStream (Byte Stream)
       * ------------------------------------
       * - clientSocket.getInputStream()
       * - Network'ten raw byte'lar alır
       * - Örnek: [72, 101, 108, 108, 111] (Hello'nun ASCII kodları)
       * - Encoding bilmiyor (UTF-8, ASCII vs)
       * - Satır kavramı yok
       *
       * Katman 2: InputStreamReader (Character Stream)
       * -----------------------------------------------
       * - Byte'ları char'a çevirir
       * - Encoding kullanır (default: UTF-8)
       * - Örnek: [72, 101, 108, 108, 111] → ['H', 'e', 'l', 'l', 'o']
       * - Türkçe karakter desteği (ç, ğ, ş vs)
       * - Hala satır kavramı yok
       *
       * Katman 3: BufferedReader (Buffered Character Stream)
       * -----------------------------------------------------
       * - Char'ları buffer'da toplar
       * - Satır satır okuma (readLine)
       * - '\n' (newline) görene kadar okur
       * - String döner (kullanımı kolay)
       * - Performans avantajı (buffering)
       *
       * Görsel Akış:
       * ------------
       * Network: [72, 101, 108, 108, 111, 10]  (byte)
       *    ↓ InputStream
       * Java: [72, 101, 108, 108, 111, 10]     (byte array)
       *    ↓ InputStreamReader (UTF-8)
       * Java: ['H', 'e', 'l', 'l', 'o', '\n']  (char array)
       *    ↓ BufferedReader.readLine()
       * Java: "Hello"                          (String)
       *
       * Neden ayrı katmanlar?
       * ---------------------
       * - Separation of Concerns (her katman bir iş yapar)
       * - InputStream: Network protokolü (byte)
       * - InputStreamReader: Encoding (byte → char)
       * - BufferedReader: Kullanım kolaylığı (char → String)
       *
       * Alternatif (kötü yol):
       * ----------------------
       * InputStream in = clientSocket.getInputStream();
       * int b;
       * StringBuilder sb = new StringBuilder();
       * while ((b = in.read()) != -1 && b != '\n') {
       *     sb.append((char) b);  // Manuel dönüşüm (zor!)
       * }
       * String message = sb.toString();
       *
       * Bizim yol (iyi):
       * ----------------
       * BufferedReader in = new BufferedReader(
       *     new InputStreamReader(clientSocket.getInputStream())
       * );
       * String message = in.readLine();  // Tek satır!
       */
      BufferedReader in = new BufferedReader(
          new InputStreamReader(clientSocket.getInputStream()));

      /*
       * ═══════════════════════════════════════════════════════════════════════
       * KAVRAM 4: Auto-flush (Otomatik Gönderme)
       * ═══════════════════════════════════════════════════════════════════════
       *
       * PrintWriter nedir?
       * ------------------
       * - Output stream'e text yazmak için
       * - String'i byte'a çevirir
       * - println(), print(), printf() metodları
       * - İki parametre: OutputStream, auto-flush (boolean)
       *
       * Auto-flush = false (default):
       * -----------------------------
       * PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), false);
       * out.println("Hello");  // Buffer'da bekler!
       * // Client GÖRMEZ!
       * out.flush();           // Manuel gönder
       * // Şimdi client görür
       *
       * Auto-flush = true:
       * ------------------
       * PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
       * out.println("Hello");  // Hemen gönderir!
       * // Client HEMEN görür
       *
       * Buffer nedir?
       * -------------
       * - Geçici bellek alanı
       * - Veriyi toplar, dolunca gönderir
       * - Performans için (her byte için network çağrısı yapmaz)
       *
       * Neden auto-flush = true kullanıyoruz?
       * --------------------------------------
       * - Interaktif uygulama (chat gibi)
       * - Tek mesaj (buffer dolmaz)
       * - Hemen cevap istiyoruz
       * - Kullanıcı deneyimi (UX)
       *
       * Neden auto-flush = false kullanılır?
       * -------------------------------------
       * - Çok fazla mesaj (performans kritik)
       * - Batch işlemler (toplu gönderim)
       * - Buffer dolunca otomatik gönderir
       * - Network overhead azalır
       *
       * Örnek Senaryo:
       * --------------
       * // Auto-flush = false
       * out.println("Line 1");  // Buffer'da
       * out.println("Line 2");  // Buffer'da
       * out.println("Line 3");  // Buffer'da
       * out.flush();            // 3'ü birden gönder (1 network call)
       *
       * // Auto-flush = true
       * out.println("Line 1");  // Hemen gönder (1 network call)
       * out.println("Line 2");  // Hemen gönder (1 network call)
       * out.println("Line 3");  // Hemen gönder (1 network call)
       *
       * Bizim durumumuzda:
       * ------------------
       * - Tek mesaj gönderiyoruz
       * - Hemen görmek istiyoruz
       * - Auto-flush = true ideal
       */
      PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);


      /*
       * ═══════════════════════════════════════════════════════════════════════
       * KAVRAM 5: Blocking I/O (Bekleyerek Okuma/Yazma)
       * ═══════════════════════════════════════════════════════════════════════
       *
       * Blocking nedir?
       * ---------------
       * - İşlem bitene kadar beklemek
       * - Thread durur (başka iş yapamaz)
       * - İşlem tamamlanınca devam eder
       *
       * Blocking metodlar:
       * ------------------
       * 1. serverSocket.accept()
       *    - Client bağlanana kadar BEKLER
       *    - Thread durur
       *    - Client bağlandığında devam eder
       *
       * 2. in.readLine()
       *    - Client mesaj gönderene kadar BEKLER
       *    - '\n' (newline) görene kadar okur
       *    - Mesaj geldiğinde String döner
       *
       * 3. in.read()
       *    - Tek byte okur
       *    - Byte gelene kadar BEKLER
       *
       * Örnek Akış:
       * -----------
       * System.out.println("Before accept");
       * Socket client = serverSocket.accept();  // ← BEKLER
       * System.out.println("After accept");     // Client bağlandıktan sonra
       *
       * System.out.println("Before readLine");
       * String msg = in.readLine();             // ← BEKLER
       * System.out.println("After readLine");   // Mesaj geldikten sonra
       *
       * Analoji:
       * --------
       * Blocking = Telefonda beklemek
       * "Lütfen hatta kalın, müsait bir temsilciye bağlanıyorsunuz..."
       * → Başka bir şey yapamazsın (thread durur)
       *
       * Non-blocking = Mesaj bırakmak
       * "Müsait olunca ararım"
       * → Başka işler yapabilirsin (thread devam eder)
       *
       * Blocking'in Avantajları:
       * ------------------------
       * - Basit kod (anlaşılır)
       * - Sıralı işlem (sequential)
       * - Hata yönetimi kolay
       * - Küçük uygulamalar için ideal
       *
       * Blocking'in Dezavantajları:
       * ---------------------------
       * - Thread bekler (kaynak israfı)
       * - Paralel işlem yok
       * - Scalability düşük (çok client = çok thread)
       * - Yavaş client tüm server'ı bloklar
       *
       * Non-blocking Alternatifi:
       * -------------------------
       * - Java NIO (New I/O)
       * - Selector pattern
       * - Tek thread, çoklu client
       * - Daha karmaşık kod
       * - Yüksek performans
       *
       * Bizim durumumuzda:
       * ------------------
       * - Tek client (blocking sorun değil)
       * - Öğrenme amaçlı (basitlik önemli)
       * - Blocking ideal seçim
       *
       * readLine() Detayları:
       * ---------------------
       * - '\n' (newline) görene kadar okur
       * - '\n' karakterini String'e dahil etmez
       * - EOF (End of File) durumunda null döner
       * - Client bağlantıyı kapatırsa null döner
       *
       * Örnek:
       * ------
       * Client gönderir: "Hello\n"
       * readLine() döner: "Hello"
       *
       * Client gönderir: "Hello"  (newline yok)
       * readLine() BEKLER (newline gelene kadar)
       */
      String message = in.readLine();
      System.out.println("Received: " + message);

      /*
       * Echo Response Oluşturma ve Gönderme
       * ------------------------------------
       *
       * println() metodu:
       * - String'e '\n' (newline) ekler
       * - Auto-flush aktif olduğu için hemen gönderir
       * - Client'ta readLine() ile okunabilir
       *
       * Akış:
       * -----
       * 1. response = "Echo: Hello"
       * 2. println(response) → "Echo: Hello\n"
       * 3. PrintWriter → String'i byte'a çevirir
       * 4. OutputStream → Network'e gönderir
       * 5. Auto-flush → Hemen gönder (buffer'da bekleme)
       * 6. Client → readLine() ile okur
       *
       * Örnek:
       * ------
       * message = "Hello"
       * response = "Echo: Hello"
       * Network'e gönderilen: "Echo: Hello\n" (byte array)
       * Client'ta okunan: "Echo: Hello" (String)
       */
      String response = "Echo: " + message;
      out.println(response);
      System.out.println("Sent: " + response);

      /*
       * Bağlantıyı Kapatma
       * ------------------
       *
       * clientSocket.close() ne yapar?
       * - TCP bağlantısını sonlandırır (FIN paketi gönderir)
       * - Input/Output stream'leri otomatik kapanır
       * - Client'ta bağlantı koptuğunu anlar
       * - Port'u serbest bırakır
       *
       * Neden kapatıyoruz?
       * ------------------
       * - Kaynak temizliği (memory leak önleme)
       * - Port'u serbest bırakma (yeniden kullanım)
       * - Client'a "iş bitti" sinyali
       * - Best practice (her açılan kaynak kapatılmalı)
       *
       * Kapatma sırası:
       * ---------------
       * 1. clientSocket.close() → Socket kapatılır
       * 2. in.close() → Otomatik (Socket kapatınca)
       * 3. out.close() → Otomatik (Socket kapatınca)
       * 4. serverSocket.close() → try-with-resources ile otomatik
       *
       * Not: serverSocket try-with-resources ile otomatik kapanır
       * (try bloğu bitince veya exception olunca)
       */
      clientSocket.close();
      System.out.println("\nClient disconnected. Server shutting down.");


    } catch (IOException ex) {
      /*
       * IOException Yakalama
       * --------------------
       *
       * Olası hatalar:
       * --------------
       * 1. Port zaten kullanımda
       *    - "Address already in use"
       *    - Başka bir program 8001 portunu kullanıyor
       *    - Çözüm: Port değiştir veya diğer programı kapat
       *
       * 2. Network bağlantısı koptu
       *    - Client beklenmedik şekilde kapandı
       *    - Network hatası
       *    - Çözüm: Graceful shutdown, retry logic
       *
       * 3. Okuma/yazma hatası
       *    - Stream kapalı
       *    - Timeout
       *    - Çözüm: Exception handling, logging
       *
       * 4. Permission denied
       *    - Port'a erişim izni yok (1024 altı portlar)
       *    - Firewall bloğu
       *    - Çözüm: Root yetkisi veya port değiştir
       *
       * printStackTrace():
       * ------------------
       * - Hatanın nereden geldiğini gösterir (stack trace)
       * - Development'ta yararlı (debugging)
       * - Production'da log'a yazılmalı (console'a değil)
       *
       * Best Practice:
       * --------------
       * - Specific exception'ları yakala (BindException, SocketException vs)
       * - Logging framework kullan (SLF4J, Log4j)
       * - Kullanıcıya anlamlı mesaj göster
       * - Retry logic ekle (gerekirse)
       */
      System.err.println("Server error: " + ex.getMessage());
      ex.printStackTrace();
    }

  }

}