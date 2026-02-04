# TCP Server Learning Journey

Java ile TCP server geliştirme öğrenme süreci. Her dosya bir konsepti gösteriyor.

## Versiyonlar

### 1. BasicEchoServer.java
- Tek client, tek mesaj
- En basit TCP server implementasyonu
- `ServerSocket.accept()` ve `Socket` kullanımı

### 2. LoopingEchoServer.java (Yakında)
- Tek client, sürekli mesaj
- Loop ile mesaj alma
- "quit" komutu ile çıkış

### 3. SequentialEchoServer.java (Yakında)
- Çoklu client desteği
- Sıralı işlem (blocking)

### 4. ThreadPerClientServer.java (Yakında)
- Her client için ayrı thread
- Paralel işlem

### 5. ThreadPoolServer.java (Yakında)
- Thread pool kullanımı
- Tomcat benzeri yapı

## Nasıl Çalıştırılır?

### Compile:
```bash
javac src/BasicEchoServer.java


## Kavramlar (Concepts)

### 1. try-with-resources
**Ne yapar?** Otomatik kaynak temizliği (AutoCloseable)  
**Neden?** Memory leak önleme, exception safety  
**Örnek:** `try (ServerSocket s = new ServerSocket(8001)) { }`

### 2. ServerSocket vs Socket
**ServerSocket:** Port dinler, client kabul eder (kapı)  
**Socket:** Belirli bir client ile iletişim (telefon hattı)  
**Analoji:** Restoran girişi vs Müşterinin masası

### 3. Input Stream Katmanları
**InputStream:** Network → byte (raw data)  
**InputStreamReader:** byte → char (encoding)  
**BufferedReader:** char → String (satır okuma)  
**Neden 3 katman?** Separation of concerns

### 4. Auto-flush
**false:** Buffer'da bekler, dolunca gönderir (performans)  
**true:** Her println() sonrası hemen gönderir (interaktif)  
**Bizim seçimimiz:** true (tek mesaj, hemen cevap)

### 5. Blocking I/O
**accept():** Client bağlanana kadar bekler  
**readLine():** Mesaj gelene kadar bekler  
**Avantaj:** Basit kod, anlaşılır  
**Dezavantaj:** Thread bekler, paralel yok