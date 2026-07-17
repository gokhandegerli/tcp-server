package Workspace;

import java.util.List;
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
    parallelWithExecutorService();
    asyncWithCompletableFutureMainWaits();
    asyncWithCompletableFutureFireAndForget();
    parallelWithVirtualThreads();
    cpuBoundWithParallelStream();
    cpuBoundWithForkJoinPool();
  }

  // ──────────────────────────────────────────────────────────────────────────────
  // 01 - BLOCKING (Single Thread)
  // One thread does all work sequentially. Each call blocks until complete.
  // Total time = sum of all durations (~350ms).
  // ──────────────────────────────────────────────────────────────────────────────
  public static void blockingSingleThread() {
    System.out.println("\n=== 01: Blocking (Single Thread) ===");
    long start = System.currentTimeMillis();

    String result1 = simulateIoCall("PlateValidation", 200);
    String result2 = simulateIoCall("UserInfoFetch", 150);

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("[" + Thread.currentThread().getName() + "] Results: " + result1 + ", " + result2);
    System.out.println("Elapsed: " + elapsed + "ms (sequential, expected ~350ms)");
  }

  // ──────────────────────────────────────────────────────────────────────────────
  // 02 - MULTITHREADING with ExecutorService
  // Tasks run in parallel on pool threads. Main thread blocks on get() waiting
  // for results. Pool threads are BLOCKED during I/O wait.
  // Total time = max(durations) (~200ms).
  // ──────────────────────────────────────────────────────────────────────────────
  public static void parallelWithExecutorService() {
    System.out.println("\n=== 02: Multithreading (ExecutorService) ===");
    long start = System.currentTimeMillis();

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<String> f1 = pool.submit(() -> simulateIoCall("PlateValidation", 200));
      Future<String> f2 = pool.submit(() -> simulateIoCall("UserInfoFetch", 150));

      String result1 = f1.get(); // main thread BLOCKED here
      String result2 = f2.get(); // main thread BLOCKED here

      long elapsed = System.currentTimeMillis() - start;
      System.out.println("[" + Thread.currentThread().getName() + "] Results: " + result1 + ", " + result2);
      System.out.println("Elapsed: " + elapsed + "ms (parallel, expected ~200ms)");
    } catch (InterruptedException | ExecutionException e) {
      Thread.currentThread().interrupt();
      System.err.println("Error: " + e.getMessage());
    }
  }

  // ──────────────────────────────────────────────────────────────────────────────
  // 03 - COMPLETABLE FUTURE (Main Waits)
  // Async tasks start immediately on a custom pool. thenCombine merges results
  // when both complete. join() at the end forces main to wait.
  // Pool threads are still BLOCKED during I/O (blocking client inside).
  // Total time ~200ms (parallel), but main could do other work before join().
  // ──────────────────────────────────────────────────────────────────────────────
  public static void asyncWithCompletableFutureMainWaits() {
    System.out.println("\n=== 03: CompletableFuture (Main Waits with join) ===");
    long start = System.currentTimeMillis();
    ExecutorService ioPool = Executors.newFixedThreadPool(2);

    CompletableFuture<String> f1 = CompletableFuture.supplyAsync(
        () -> simulateIoCall("PlateValidation", 200), ioPool);
    CompletableFuture<String> f2 = CompletableFuture.supplyAsync(
        () -> simulateIoCall("UserInfoFetch", 150), ioPool);

    CompletableFuture<Void> combined = f1.thenCombine(f2, (s1, s2) -> {
      long elapsed = System.currentTimeMillis() - start;
      System.out.println("[" + Thread.currentThread().getName() + "] Results: " + s1 + ", " + s2);
      System.out.println("Elapsed: " + elapsed + "ms (parallel + async initiation)");
      return null;
    }).thenAccept(v -> {});

    // main blocks here — in a real Spring Boot app you would NOT call join(),
    // the framework handles the future lifecycle.
    combined.join();
    ioPool.shutdown();
  }

  // ──────────────────────────────────────────────────────────────────────────────
  // 04 - COMPLETABLE FUTURE (Fire-and-Forget — Main Does NOT Wait)
  // Tasks are submitted and main continues immediately without blocking.
  // This is the "fire-and-forget" pattern (like @Async in Spring).
  // Main does NOT call join()/get() — it prints and moves on.
  // Note: In a standalone app, JVM may exit before tasks finish.
  //       In Spring Boot, the app stays alive so tasks always complete.
  // ──────────────────────────────────────────────────────────────────────────────
  public static void asyncWithCompletableFutureFireAndForget() throws InterruptedException {
    System.out.println("\n=== 04: CompletableFuture (Fire-and-Forget — Main Does NOT Wait) ===");
    long start = System.currentTimeMillis();
    ExecutorService ioPool = Executors.newFixedThreadPool(2);

    CompletableFuture.supplyAsync(() -> simulateIoCall("AuditLogWrite", 300), ioPool)
        .thenAccept(result -> System.out.println("[" + Thread.currentThread().getName()
            + "] Background task done: " + result));

    CompletableFuture.supplyAsync(() -> simulateIoCall("NotificationSend", 250), ioPool)
        .thenAccept(result -> System.out.println("[" + Thread.currentThread().getName()
            + "] Background task done: " + result));

    // Main does NOT wait — continues immediately
    long elapsed = System.currentTimeMillis() - start;
    System.out.println("[" + Thread.currentThread().getName()
        + "] Main returned immediately! Elapsed: " + elapsed + "ms (expected ~0ms)");
    System.out.println("Background tasks are still running on pool threads...");

    // Only sleeping here so the demo doesn't exit before tasks print their output.
    // In a long-running app (Spring Boot), this sleep is unnecessary.
    Thread.sleep(400);
    ioPool.shutdown();
  }

  // ──────────────────────────────────────────────────────────────────────────────
  // 05 - VIRTUAL THREADS (Java 21+)
  // Lightweight threads managed by JVM. When a VT hits blocking I/O,
  // it "parks" and the carrier (platform) thread is released for other VTs.
  // Looks like blocking code but scales like non-blocking.
  // Total time ~200ms (parallel), carrier threads stay free during I/O.
  // ──────────────────────────────────────────────────────────────────────────────
  public static void parallelWithVirtualThreads() throws InterruptedException {
    System.out.println("\n=== 05: Virtual Threads (Java 21+) ===");
    long start = System.currentTimeMillis();

    Thread vt1 = Thread.ofVirtual().name("vt-plate").start(
        () -> System.out.println("[" + Thread.currentThread() + "] " + simulateIoCall("PlateValidation", 200)));
    Thread vt2 = Thread.ofVirtual().name("vt-user").start(
        () -> System.out.println("[" + Thread.currentThread() + "] " + simulateIoCall("UserInfoFetch", 150)));

    vt1.join();
    vt2.join();
    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + "ms (VTs parked during I/O, carrier free)");
  }

  // ──────────────────────────────────────────────────────────────────────────────
  // 06 - CPU-BOUND with parallelStream
  // Uses the common ForkJoinPool to parallelize computation across all cores.
  // Good for: side-effect-free, CPU-intensive operations on collections.
  // Bad for: I/O-bound work (would starve the common pool).
  // ──────────────────────────────────────────────────────────────────────────────
  public static void cpuBoundWithParallelStream() {
    System.out.println("\n=== 06: CPU-Bound (parallelStream) ===");
    long start = System.currentTimeMillis();

    long sum = LongStream.rangeClosed(1, 50_000_000)
        .parallel()
        .reduce(0, Long::sum);

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("[" + Thread.currentThread().getName() + "] Sum: " + sum);
    System.out.println("Elapsed: " + elapsed + "ms (uses common ForkJoinPool, all cores)");
  }

  // ──────────────────────────────────────────────────────────────────────────────
  // 07 - CPU-BOUND with ForkJoinPool (Divide-and-Conquer)
  // Explicit work-stealing pool with RecursiveTask. Splits work into subtasks,
  // each fork steals work from others when idle.
  // Best for: recursive CPU-bound computation (merge sort, tree traversal, etc.)
  // ──────────────────────────────────────────────────────────────────────────────
  public static void cpuBoundWithForkJoinPool() {
    System.out.println("\n=== 07: CPU-Bound (ForkJoinPool — Divide and Conquer) ===");
    long start = System.currentTimeMillis();

    ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    long result = pool.invoke(new SumTask(1, 50_000_000));

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("[" + Thread.currentThread().getName() + "] Sum: " + result);
    System.out.println("Elapsed: " + elapsed + "ms (work-stealing, divide-and-conquer)");
    pool.shutdown();
  }

  // ──────────────────────────────────────────────────────────────────────────────
  // HELPER: Simulates a blocking I/O call (HTTP request, DB query, etc.)
  // ──────────────────────────────────────────────────────────────────────────────
  private static String simulateIoCall(String taskName, long durationMs) {
    try {
      System.out.println("[" + Thread.currentThread().getName() + "] " + taskName + " started...");
      Thread.sleep(durationMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return taskName + " completed (" + durationMs + "ms)";
  }

  // ──────────────────────────────────────────────────────────────────────────────
  // HELPER: RecursiveTask for ForkJoinPool — sums a range by splitting in half
  // ──────────────────────────────────────────────────────────────────────────────
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
      if ((to - from) <= THRESHOLD) {
        long sum = 0;
        for (long i = from; i <= to; i++) {
          sum += i;
        }
        return sum;
      }
      long mid = (from + to) / 2;
      SumTask left = new SumTask(from, mid);
      SumTask right = new SumTask(mid + 1, to);
      left.fork();
      long rightResult = right.compute();
      long leftResult = left.join();
      return leftResult + rightResult;
    }
  }
}
