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