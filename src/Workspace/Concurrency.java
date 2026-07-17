package Workspace;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveTask;
import java.util.stream.LongStream;

public class Concurrency {

  public static void main(String[] args) throws Exception {
    blockingSingleThread();
    rawThreadManualManagement();
    parallelWithExecutorService();
    asyncWithCompletableFutureMainWaits();
    asyncWithCompletableFutureFireAndForget();
    parallelWithVirtualThreads();
    cpuBoundWithParallelStream();
    cpuBoundWithForkJoinPool();
  }

  /**
   * 01 - BLOCKING (Single Thread)
   * <p>
   * Tek thread tüm işleri sıralı yapar. Her çağrı bitene kadar thread bloklanır.
   * Toplam süre = tüm sürelerin toplamı (~350ms).
   * <p>
   * Gerçek hayat: Spring MVC'de RestTemplate ile sıralı iki servis çağırmak.
   */
  public static void blockingSingleThread() {
    System.out.println("\n=== 01: Blocking (Single Thread) ===");
    long start = System.currentTimeMillis();

    String result1 = simulateIoCall("PlateValidation", 200);
    String result2 = simulateIoCall("UserInfoFetch", 150);

    long elapsed = System.currentTimeMillis() - start;
    System.out.println(
        "[" + Thread.currentThread().getName() + "] Results: " + result1 + ", " + result2);
    System.out.println("Elapsed: " + elapsed + "ms (sequential, expected ~350ms)");
  }

  /**
   * 02 - RAW THREAD (Manual Management)
   * <p>
   * Thread'leri elle oluşturup yönetirsin. Lifecycle, hata yönetimi, koordinasyon
   * tamamen sana ait. Havuz yok, thread tekrar kullanımı yok.
   *
   * <p><b>⚠️ Production'da ASLA kullanma!</b></p>
   * <ul>
   *   <li>Thread reuse yok — her iş için yeni OS thread (pahalı)</li>
   *   <li>Backpressure yok — binlerce thread açılabilir, OOM riski</li>
   *   <li>Task queue yok — iş yığılması yönetilemez</li>
   *   <li>Hata yönetimi zor — exception handler elle bağlanmalı</li>
   * </ul>
   * <p>
   * Bunun yerine: {@link Executors#newFixedThreadPool} veya Virtual Threads kullan.
   */
  public static void rawThreadManualManagement() throws InterruptedException {
    System.out.println("\n=== 02: Raw Thread (Manual — DO NOT use in production) ===");
    long start = System.currentTimeMillis();

    Thread t1 = new Thread(() -> System.out.println(
        "[" + Thread.currentThread().getName() + "] " + simulateIoCall("PlateValidation",
            200)), "raw-plate");
    Thread t2 = new Thread(() -> System.out.println(
        "[" + Thread.currentThread().getName() + "] " + simulateIoCall("UserInfoFetch", 150)),
        "raw-user");

    t1.start();
    t2.start();

    t1.join(); // manually waiting for completion — no Future, no callback
    t2.join();

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + "ms (parallel but unmanaged, no reuse)");
  }

  /**
   * 03 - MULTITHREADING with ExecutorService
   * <p>
   * İşler havuz thread'lerinde paralel çalışır. Main thread get() ile sonucu bekler (BLOCKED).
   * Havuz thread'leri de I/O süresince bloklanır — ama en azından thread yönetimi otomatik.
   * <p>
   * Toplam süre = max(süreler) (~200ms). Main thread get()'te donar.
   */
  public static void parallelWithExecutorService() {
    System.out.println("\n=== 03: Multithreading (ExecutorService) ===");
    long start = System.currentTimeMillis();

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<String> f1 = pool.submit(() -> simulateIoCall("PlateValidation", 200));
      Future<String> f2 = pool.submit(() -> simulateIoCall("UserInfoFetch", 150));

      String result1 = f1.get(); // main thread BLOCKED here
      String result2 = f2.get(); // main thread BLOCKED here

      long elapsed = System.currentTimeMillis() - start;
      System.out.println(
          "[" + Thread.currentThread().getName() + "] Results: " + result1 + ", " + result2);
      System.out.println("Elapsed: " + elapsed + "ms (parallel, expected ~200ms)");
    } catch (InterruptedException | ExecutionException e) {
      Thread.currentThread().interrupt();
      System.err.println("Error: " + e.getMessage());
    }
  }

  /**
   * 04 - COMPLETABLE FUTURE (Main Waits)
   * <p>
   * Async task'lar custom pool'da hemen başlar. thenCombine ile iki sonuç birleştirilir.
   * Sonunda join() çağrılır — main burada bekler.
   *
   * <p>Havuz thread'leri I/O süresince hâlâ BLOCKED (blocking client kullanıldığı için).
   * Ama main thread iş başlatma anında serbest — join()'e kadar başka iş yapabilir.</p>
   * <p>
   * Toplam süre ~200ms (paralel).
   */
  public static void asyncWithCompletableFutureMainWaits() {
    System.out.println("\n=== 04: CompletableFuture (Main Waits with join) ===");
    long start = System.currentTimeMillis();

    try (ExecutorService ioPool = Executors.newFixedThreadPool(2)) {

      CompletableFuture<String> f1 = CompletableFuture.supplyAsync(
          () -> simulateIoCall("PlateValidation", 200), ioPool);
      CompletableFuture<String> f2 = CompletableFuture.supplyAsync(
          () -> simulateIoCall("UserInfoFetch", 150), ioPool);

      CompletableFuture<Void> combined = f1.thenCombine(f2, (s1, s2) -> {
        long elapsed = System.currentTimeMillis() - start;
        System.out.println(
            "[" + Thread.currentThread().getName() + "] Results: " + s1 + ", " + s2);
        System.out.println("Elapsed: " + elapsed + "ms (parallel + async initiation)");
        return null;
      }).thenAccept(v -> {});

      // main blocks here — in a real Spring Boot app you would NOT call join(),
      // the framework handles the future lifecycle.
      combined.join();
    }
  }

  /**
   * 05 - COMPLETABLE FUTURE (Fire-and-Forget — Main Does NOT Wait)
   *
   * <p>Task'lar gönderilir ve main hemen devam eder. join()/get() çağrılmaz.
   * Bu "fire-and-forget" pattern'idir (Spring'deki {@code @Async}'in karşılığı).
   *
   * <h3>Sonucu kim işliyor?</h3>
   * <pre>
   * supplyAsync(() -> simulateIoCall(...), ioPool)   // 1) ioPool thread'i çalıştırır
   *     .thenAccept(result -> print(result));        // 2) callback — sonuç gelince tetiklenir
   * </pre>
   *
   * <ul>
   *   <li><b>supplyAsync</b> → İşi ioPool'daki bir thread'e verir. Main devam eder.</li>
   *   <li><b>thenAccept</b> → Callback. İş bitince sonucu alan ve işleyen fonksiyon.</li>
   * </ul>
   *
   * <h3>thenAccept hangi thread'de çalışır?</h3>
   * <ul>
   *   <li>Eğer supplyAsync bittiğinde thenAccept henüz zincire eklenmemişse
   *       → thenAccept'i zincire ekleyen thread (main) çalıştırır.</li>
   *   <li>Eğer supplyAsync zaten bitmişse thenAccept eklendiği anda
   *       → supplyAsync'i çalıştıran pool thread'i devam eder ve callback'i çalıştırır.</li>
   *   <li>Pratikte genellikle: <b>pool thread'i</b> (çünkü I/O işi main'den yavaş).</li>
   *   <li>Garanti istiyorsan: {@code thenAcceptAsync(callback, executor)} ile hangi
   *       pool'da çalışacağını açıkça belirt.</li>
   * </ul>
   *
   * <h3>Spring @Async ile farkı</h3>
   * <ul>
   *   <li>{@code @Async} → Spring proxy ile metodu başka thread'de çalıştırır.
   *       Return type void ise tamamen fire-and-forget. {@code Future<T>} dönerse
   *       çağıran isterse bekleyebilir.</li>
   *   <li>CompletableFuture → Framework bağımsız. Callback zinciri (thenAccept, thenApply)
   *       ile sonuç dönünce ne yapılacağını tanımlarsın. Kimse join() çağırmadığı
   *       sürece fire-and-forget.</li>
   * </ul>
   *
   * <h3>Akış özeti</h3>
   * <pre>
   * ZAMAN  main thread              pool-thread-1              pool-thread-2
   *  0ms   │                         │                          │
   *        ├─ supplyAsync ──────────►│ AuditLogWrite başlar     │
   *        ├─ supplyAsync ───────────┼─────────────────────────►│ NotificationSend başlar
   *        ├─ print "returned" (1ms) │ (hâlâ çalışıyor...)      │ (hâlâ çalışıyor...)
   *        ├─ (main işi bitti,       │                          │
   *        │   artık beklemez)       │                          │
   *        │                         │                          │
   * 250ms  │                         │                          ├─ NotificationSend bitti
   *        │                         │                          ├─ thenAccept AYNI THREAD'de
   *        │                         │                          │   çalışır → print result
   *        │                         │                          │   (pool-thread-2)
   *        │                         │                          │
   * 300ms  │                         ├─ AuditLogWrite bitti     │
   *        │                         ├─ thenAccept AYNI THREAD  │
   *        │                         │   'de çalışır → print    │
   *        │                         │   (pool-thread-1)        │
   *        ▼                         ▼                          ▼
   *
   * ÖNEMLİ: thenAccept callback'i, supplyAsync'i bitiren AYNI pool thread'inde çalışır.
   * Çünkü iş bittiğinde "sonraki adım ne?" diye bakar → thenAccept'i bulur → hemen çalıştırır.
   * Main thread bu noktada çoktan gitmiş, callback'le işi yok.
   * </pre>
   *
   * <p><b>Dikkat:</b> Standalone uygulamada JVM, task'lar bitmeden çıkabilir.
   * Spring Boot'ta uygulama ayakta kaldığı için task'lar her zaman tamamlanır.</p>
   */
  public static void asyncWithCompletableFutureFireAndForget() throws InterruptedException {
    System.out.println(
        "\n=== 05: CompletableFuture (Fire-and-Forget — Main Does NOT Wait) ===");
    long start = System.currentTimeMillis();
    ExecutorService ioPool = Executors.newFixedThreadPool(2);

    CompletableFuture.supplyAsync(() -> simulateIoCall("AuditLogWrite", 300), ioPool)
        .thenAccept(result -> System.out.println(
            "[" + Thread.currentThread().getName() + "] Background task done: " + result));

    CompletableFuture.supplyAsync(() -> simulateIoCall("NotificationSend", 250), ioPool)
        .thenAccept(result -> System.out.println(
            "[" + Thread.currentThread().getName() + "] Background task done: " + result));

    // Main does NOT wait — continues immediately
    long elapsed = System.currentTimeMillis() - start;
    System.out.println(
        "[" + Thread.currentThread().getName() + "] Main returned immediately! Elapsed: "
            + elapsed + "ms (expected ~0ms)");
    System.out.println("Background tasks are still running on pool threads...");

    // Only sleeping here so the demo doesn't exit before tasks print their output.
    // In a long-running app (Spring Boot), this sleep is unnecessary.
    Thread.sleep(400);
    ioPool.shutdown();
  }

  /**
   * 06 - VIRTUAL THREADS (Java 21+)
   * <p>
   * JVM tarafından yönetilen hafif thread'ler. Blocking I/O'ya denk gelince VT "park" olur
   * ve carrier (platform) thread serbest bırakılır — başka VT'ler onu kullanabilir.
   *
   * <p>Blocking kod yazar gibi yazarsın ama non-blocking gibi ölçeklenir.
   * Go'nun goroutine'leri / Kotlin coroutine'leri ile aynı konsept.</p>
   * <p>
   * Toplam süre ~200ms (paralel), carrier thread'ler I/O boyunca serbest.
   */
  public static void parallelWithVirtualThreads() throws InterruptedException {
    System.out.println("\n=== 06: Virtual Threads (Java 21+) ===");
    long start = System.currentTimeMillis();

    Thread vt1 = Thread.ofVirtual()
        .name("vt-plate")
        .start(() -> System.out.println(
            "[" + Thread.currentThread() + "] " + simulateIoCall("PlateValidation", 200)));
    Thread vt2 = Thread.ofVirtual()
        .name("vt-user")
        .start(() -> System.out.println(
            "[" + Thread.currentThread() + "] " + simulateIoCall("UserInfoFetch", 150)));

    vt1.join();
    vt2.join();
    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + "ms (VTs parked during I/O, carrier free)");
  }

  /**
   * 07 - CPU-BOUND with parallelStream
   * <p>
   * Common ForkJoinPool'u kullanarak hesaplamayı tüm core'lara dağıtır.
   * Side-effect'siz, CPU-yoğun koleksiyon işlemleri için idealdir.
   *
   * <p><b>Dikkat:</b> I/O-bound iş için KULLANMA — common pool'u aç bırakır
   * ve uygulamanın diğer parallelStream çağrılarını da bloklar.</p>
   */
  public static void cpuBoundWithParallelStream() {
    System.out.println("\n=== 07: CPU-Bound (parallelStream) ===");
    long start = System.currentTimeMillis();

    long sum = LongStream.rangeClosed(1, 50_000_000).parallel().reduce(0, Long::sum);

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("[" + Thread.currentThread().getName() + "] Sum: " + sum);
    System.out.println("Elapsed: " + elapsed + "ms (uses common ForkJoinPool, all cores)");
  }

  /**
   * 08 - CPU-BOUND with ForkJoinPool (Divide-and-Conquer)
   * <p>
   * Explicit work-stealing pool + RecursiveTask. İşi alt task'lara böler,
   * boşta kalan thread'ler başkalarının kuyruğundan iş çalar.
   * <p>
   * En uygun: recursive CPU-bound hesaplama (merge sort, tree traversal, matrix multiply).
   *
   * @see SumTask
   */
  public static void cpuBoundWithForkJoinPool() {
    System.out.println("\n=== 08: CPU-Bound (ForkJoinPool — Divide and Conquer) ===");
    long start = System.currentTimeMillis();

    ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    long result = pool.invoke(new SumTask(1, 50_000_000));

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("[" + Thread.currentThread().getName() + "] Sum: " + result);
    System.out.println("Elapsed: " + elapsed + "ms (work-stealing, divide-and-conquer)");
    pool.shutdown();
  }

  /**
   * Blocking bir I/O çağrısını simüle eder (HTTP request, DB sorgusu vb.).
   * Thread.sleep() ile belirtilen süre kadar thread'i bloklar.
   */
  private static String simulateIoCall(String taskName, long durationMs) {
    try {
      System.out.println(
          "[" + Thread.currentThread().getName() + "] " + taskName + " started...");
      Thread.sleep(durationMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return taskName + " completed (" + durationMs + "ms)";
  }

  /**
   * ForkJoinPool için RecursiveTask implementasyonu — bir aralığı ikiye bölerek toplar.
   *
   * <h3>RecursiveTask nedir?</h3>
   * {@code java.util.concurrent} paketindeki abstract class.
   * ForkJoinPool içinde çalıştırılmak üzere tasarlanmış.
   * Tek abstract metodu: {@link #compute()}. Pool bir task'ı çalıştırdığında bunu çağırır.
   *
   * <h3>compute() ne yapıyor?</h3>
   * "Bu task'ın işi nedir?" sorusunun cevabı. Override edip lojiğini tanımlarsın.
   * Küçükse doğrudan hesapla (base case), büyükse böl (recursive case).
   *
   * <h3>Divide-and-Conquer akışı</h3>
   * <ol>
   *   <li>İş küçük mü? (≤ THRESHOLD) → Doğrudan hesapla (base case)</li>
   *   <li>Büyükse ikiye böl, her yarıyı yeni bir SumTask olarak oluştur</li>
   *   <li>{@code left.fork()} → Sol yarıyı pool'a gönder</li>
   *   <li>{@code right.compute()} → Sağ yarıyı mevcut thread kendisi hesaplasın</li>
   *   <li>{@code left.join()} → Fork edilen solun bitmesini bekle</li>
   *   <li>İki sonucu topla, döndür</li>
   * </ol>
   *
   * <h3>Görsel örnek — [1..400_000], THRESHOLD=100_000</h3>
   * <pre>
   *           [1 ─────────── 400_000]
   *            /                   \
   *      fork [1..200K]       compute [200K..400K]
   *        /        \               /          \
   *   [1..100K]  [100K..200K]  [200K..300K]  [300K..400K]
   *    (base)      (base)        (base)        (base)
   * </pre>
   *
   * <h3>Neden left.fork() + right.compute()? İkisini de fork etmiyor muyuz?</h3>
   * İkisini de fork etsen mevcut thread boşa oturur beklerken.
   * Birini fork edip diğerini kendin hesaplarsan → thread boşa durmaz, CPU utilization artar.
   *
   * <h3>Kavramlar</h3>
   * <ul>
   *   <li><b>fork()</b> — Task'ı ForkJoinPool'un kuyruğuna koyar. Pool'daki başka bir thread
   *       bu task'ı alıp (steal) çalıştırabilir. Asenkron başlatma — hemen döner,
   *       sonucu beklemez.</li>
   *   <li><b>join()</b> — Fork edilmiş task'ın bitmesini bekler ve sonucunu döndürür.
   *       {@code Future.get()} gibi ama ForkJoinPool'a özel — beklerken thread
   *       başka task çalabilir (work-stealing).</li>
   *   <li><b>compute()</b> — Task'ın asıl iş mantığı. ForkJoinPool tarafından çağrılır.
   *       Override edip ne yapılacağını tanımlarsın.</li>
   *   <li><b>work-stealing</b> — Bir thread kendi kuyruğunu bitirince başka thread'in
   *       kuyruğundan iş çalar. Böylece hiçbir core boşta kalmaz.</li>
   *   <li><b>THRESHOLD</b> — Ne zaman bölmeyi bırakıp doğrudan hesaplamaya geçeceğin.
   *       Çok küçükse → fork/join overhead artar. Çok büyükse → paralellik azalır.</li>
   * </ul>
   */
  static class SumTask extends RecursiveTask<Long> {

    private static final int THRESHOLD = 100_000;
    private final long from;
    private final long to;

    SumTask(long from, long to) {
      this.from = from;
      this.to = to;
    }

    @Override
    protected Long compute() {
      // Base case: range is small enough, compute directly
      if ((to - from) <= THRESHOLD) {
        long sum = 0;
        for (long i = from; i <= to; i++) {
          sum += i;
        }
        return sum;
      }

      // Recursive case: split in half
      long mid = (from + to) / 2;
      SumTask left = new SumTask(from, mid);
      SumTask right = new SumTask(mid + 1, to);

      left.fork();                        // submit left to pool queue (async)
      long rightResult = right.compute(); // current thread computes right directly
      long leftResult = left.join();      // wait for forked left result

      return leftResult + rightResult;
    }
  }
}
