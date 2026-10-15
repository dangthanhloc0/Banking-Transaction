package com.ldang03.transaction.loadtest;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Manual runner (not a JUnit test).
 * Usage: TestTransaction [total] [threads] [amount] [users] [rounds] [ports]
 * Example: TestTransaction 1000 50 2001 10000 1 2000,2001
 * Request i goes to ports[i % ports.length], so the TransactionService instances get an equal share.
 */
public class TestTransaction {

    private static final String URL_TEMPLATE = "http://localhost:%s/transactions/v1/transactions";

    public static void main( String[] args) throws Exception {
        final int total = args.length > 0 ? Integer.parseInt(args[0]) : 500;
        final int threads = args.length > 1 ? Integer.parseInt(args[1]) : 50;
        final int amount = args.length > 2 ? Integer.parseInt(args[2]) : 1007;
        final int users = args.length > 3 ? Integer.parseInt(args[3]) : 10000;
        final int rounds = args.length > 4 ? Integer.parseInt(args[4]) : 20;
        final String[] ports = (args.length > 5 ? args[5] : "2000,2001").split(",");
        if (users < 2) {
            throw new IllegalArgumentException("users must be >= 2");
        }
        final URI[] targets = Arrays.stream(ports)
                .map(port -> URI.create(URL_TEMPLATE.formatted(port.trim())))
                .toArray(URI[]::new);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        for (int round = 1; round <= rounds; round++) {
            System.out.println("=== round " + round + "/" + rounds + " ===");
            runRound(client, targets, total, threads, amount, users);
            if (round < rounds) {
                Thread.sleep(10_000);   // let the pipeline drain a bit before the next round
            }
        }
    }

    private static void runRound(HttpClient client, URI[] targets, int total, int threads, int amount, int users) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        Map<Integer, AtomicInteger> statusCount = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> perInstance = new ConcurrentHashMap<>();   // port -> 202 responses
        AtomicInteger networkErrors = new AtomicInteger();
        AtomicReference<String> firstBadBody = new AtomicReference<>();
        long[] latenciesNs = new long[total];
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                startGate.await();
                URI target = targets[index % targets.length];
                HttpRequest request = HttpRequest.newBuilder(target)
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", UUID.randomUUID().toString())   // UUID v4, one per transfer intent
                        .POST(HttpRequest.BodyPublishers.ofString(buildBody(amount, users)))
                        .build();
                long t0 = System.nanoTime();
                try {
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    latenciesNs[index] = System.nanoTime() - t0;
                    statusCount.computeIfAbsent(response.statusCode(), k -> new AtomicInteger()).incrementAndGet();
                    if (response.statusCode() == 202) {
                        perInstance.computeIfAbsent(String.valueOf(target.getPort()), k -> new AtomicInteger()).incrementAndGet();
                    } else {
                        firstBadBody.compareAndSet(null, response.statusCode() + " " + response.body());
                    }
                } catch (Exception e) {
                    networkErrors.incrementAndGet();
                    firstBadBody.compareAndSet(null, "network error: " + e);
                }
                return null;
            }));
        }

        long begin = System.nanoTime();
        startGate.countDown();                 // go!
        for (Future<?> f : futures) {
            f.get();                           // wait for every request
        }
        long tookMs = (System.nanoTime() - begin) / 1_000_000;
        pool.shutdown();

        long[] ok = Arrays.stream(latenciesNs).filter(v -> v > 0).sorted().toArray();
        System.out.println("total requests = " + total + "   threads = " + threads + "   amount = " + amount + "   users = " + users);
        System.out.println("by HTTP status = " + statusCount);
        System.out.println("202 per port   = " + new java.util.TreeMap<>(perInstance));
        System.out.println("network errors = " + networkErrors.get());
        System.out.println("took           = " + tookMs + " ms   (" + String.format("%.1f", total * 1000.0 / Math.max(tookMs, 1)) + " requests/s)");
        if (ok.length > 0) {
            System.out.println("latency ms     = p50 " + pct(ok, 0.50) + "  p95 " + pct(ok, 0.95) + "  p99 " + pct(ok, 0.99) + "  max " + ok[ok.length - 1] / 1_000_000);
        }
        if (firstBadBody.get() != null) {
            System.out.println("first non-202  = " + firstBadBody.get());
        }
    }

    private static long pct(long[] sorted, double p) {
        return sorted[Math.min(sorted.length - 1, (int) (sorted.length * p))] / 1_000_000;
    }

    private static String buildBody(int amount, int users) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int debtor = random.nextInt(1, users + 1);          // 1..users
        int creditor = random.nextInt(1, users);            // 1..users-1, then skip the debtor
        if (creditor >= debtor) {
            creditor++;
        }
        return """
                {"userId":"01900000-0000-7000-8000-%012d",
                 "debtorAccount":"10%08d",
                 "creditorAccount":"10%08d",
                 "amount":%d}
                """.formatted(debtor, debtor, creditor, amount);
    }
}
