package Workspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnonymousInner {

  public static void main(String[] args) {

    List<String> boundedList = ArrayAnonymous();

    boundedList.add("a");
    boundedList.add("b");
    System.out.println("--- İlk Durum (Kapasite: 2) ---");
    boundedList.forEach(System.out::println);

    boundedList.add("yeni");
    System.out.println("--- 'yeni' Eklendikten Sonra ---");
    boundedList.forEach(System.out::println);

    // Örnek Map Kullanımı:
    LinkedHashMap<String, Object> cache = getFixedCache();
    cache.put("1", "bir");
    cache.put("2", "iki");
    cache.put("3", "üç");
    cache.put("4", "dört"); // "1" anahtarlı en eski kayıt silinecektir (Max: 3)

    System.out.println("--- Cache Durumu ---");
    cache.forEach((k, v) -> System.out.println(k + ": " + v));
  }

  private static List<String> ArrayAnonymous() {
    return new ArrayList<>() {
      @Override
      public boolean add(String e) {
        if (size() >= 2) {
          remove(0);  // En eskiyi sil (FIFO)
        }
        return super.add(e);
      }
    };
  }

  private static <K, V> LinkedHashMap<K, V> getFixedCache() {
    final int MAX = 3;
    // initialCapacity: 16, loadFactor: 0.75f, accessOrder: true (LRU / Access order için)
    return new LinkedHashMap<>(16, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > MAX;
      }
    };
  }
}