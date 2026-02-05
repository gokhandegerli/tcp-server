import java.nio.charset.StandardCharsets;

public class ByteVsChar {
  public static void main(String[] args) {
    String text = "Merhaba çağ!🚀";

    int codePoint = 128640;
    String rocket = Character.toString(codePoint);

    // 1. String → Byte
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    System.out.println("=== BYTE (Sayılar) ===");
    for (byte b : bytes) {
      System.out.print(b + " ");
    }
    System.out.println("\n");

    // 2. Byte → Char (Manuel)
    System.out.println("=== CHAR (Karakterler) ===");
    String decoded = new String(bytes, StandardCharsets.UTF_8);
    for (char c : decoded.toCharArray()) {
      System.out.print(c + " ");
    }
    System.out.println("\n");

    // 3. Karşılaştırma
    System.out.println("=== KARŞILAŞTIRMA ===");
    System.out.println("Byte sayısı: " + bytes.length);
    System.out.println("Char sayısı: " + decoded.length());
    System.out.println("Orijinal: " + text);
    System.out.println("Decoded:  " + decoded);
    System.out.println(codePoint + "=> " + rocket); // Çıktı: 🚀
  }
}