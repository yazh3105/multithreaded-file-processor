package com.fileprocessor;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/**
 * Task 3: Multithreaded App
 * High-performance Java application using Multithreading and Synchronization
 * to process multiple files concurrently.
 *
 * Outcome: Acquire professional industry skills.
 */
public class MultiThreadedFileProcessor {

    private static final Logger LOGGER = Logger.getLogger(MultiThreadedFileProcessor.class.getName());

    // ─── Shared Statistics (synchronized via AtomicLong) ───────────────────────
    private final AtomicLong totalFilesProcessed = new AtomicLong(0);
    private final AtomicLong totalLinesRead       = new AtomicLong(0);
    private final AtomicLong totalWordsCount      = new AtomicLong(0);
    private final AtomicLong totalBytesRead       = new AtomicLong(0);
    private final AtomicInteger errorCount        = new AtomicInteger(0);

    // ─── Thread-safe result collector ──────────────────────────────────────────
    private final ConcurrentLinkedQueue<FileResult> results = new ConcurrentLinkedQueue<>();

    // ─── Configurable thread pool size ─────────────────────────────────────────
    private final int threadCount;
    private final ExecutorService executorService;

    // ─── Synchronization: ensure summary is printed after all tasks ────────────
    private final CountDownLatch latch;

    public MultiThreadedFileProcessor(int threadCount, int fileCount) {
        this.threadCount     = threadCount;
        this.executorService = Executors.newFixedThreadPool(threadCount);
        this.latch           = new CountDownLatch(fileCount);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FileResult: immutable value object per processed file
    // ─────────────────────────────────────────────────────────────────────────
    public static class FileResult {
        public final String  fileName;
        public final long    lines;
        public final long    words;
        public final long    bytes;
        public final long    durationMs;
        public final String  threadName;
        public final boolean success;
        public final String  errorMessage;

        public FileResult(String fileName, long lines, long words, long bytes,
                          long durationMs, String threadName) {
            this.fileName     = fileName;
            this.lines        = lines;
            this.words        = words;
            this.bytes        = bytes;
            this.durationMs   = durationMs;
            this.threadName   = threadName;
            this.success      = true;
            this.errorMessage = null;
        }

        public FileResult(String fileName, String errorMessage, String threadName) {
            this.fileName     = fileName;
            this.lines        = 0;
            this.words        = 0;
            this.bytes        = 0;
            this.durationMs   = 0;
            this.threadName   = threadName;
            this.success      = false;
            this.errorMessage = errorMessage;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FileProcessingTask: Runnable submitted to the thread pool
    // ─────────────────────────────────────────────────────────────────────────
    private class FileProcessingTask implements Runnable {
        private final Path filePath;

        public FileProcessingTask(Path filePath) {
            this.filePath = filePath;
        }

        @Override
        public void run() {
            String threadName = Thread.currentThread().getName();
            String fileName   = filePath.getFileName().toString();
            long   start      = System.currentTimeMillis();

            LOGGER.info(String.format("[%s] Starting: %s", threadName, fileName));

            try {
                long lineCount = 0;
                long wordCount = 0;
                long byteCount = Files.size(filePath);

                try (BufferedReader reader = Files.newBufferedReader(filePath)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lineCount++;
                        // Count words (split on whitespace)
                        String[] words = line.trim().split("\\s+");
                        if (!line.trim().isEmpty()) {
                            wordCount += words.length;
                        }
                    }
                }

                long durationMs = System.currentTimeMillis() - start;

                // ── Atomic updates to shared statistics ──
                totalFilesProcessed.incrementAndGet();
                totalLinesRead.addAndGet(lineCount);
                totalWordsCount.addAndGet(wordCount);
                totalBytesRead.addAndGet(byteCount);

                FileResult result = new FileResult(fileName, lineCount, wordCount,
                                                   byteCount, durationMs, threadName);
                results.add(result);

                LOGGER.info(String.format("[%s] Done: %s | Lines=%d Words=%d Bytes=%d Time=%dms",
                        threadName, fileName, lineCount, wordCount, byteCount, durationMs));

            } catch (IOException e) {
                errorCount.incrementAndGet();
                results.add(new FileResult(fileName, e.getMessage(), threadName));
                LOGGER.severe(String.format("[%s] ERROR processing %s: %s",
                        threadName, fileName, e.getMessage()));
            } finally {
                latch.countDown(); // Signal this task is done
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // submit: Add a file processing task to the thread pool
    // ─────────────────────────────────────────────────────────────────────────
    public void submit(Path filePath) {
        executorService.submit(new FileProcessingTask(filePath));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // awaitCompletion: Block until all tasks finish
    // ─────────────────────────────────────────────────────────────────────────
    public void awaitCompletion() throws InterruptedException {
        latch.await(); // Synchronization point
        executorService.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // printSummary: Thread-safe summary report
    // ─────────────────────────────────────────────────────────────────────────
    public void printSummary() {
        System.out.println("\n" + "═".repeat(65));
        System.out.println("           MULTITHREADED FILE PROCESSOR — RESULTS");
        System.out.println("═".repeat(65));
        System.out.printf("  Thread Pool Size   : %d threads%n", threadCount);
        System.out.printf("  Files Processed    : %d%n",  totalFilesProcessed.get());
        System.out.printf("  Total Lines        : %,d%n", totalLinesRead.get());
        System.out.printf("  Total Words        : %,d%n", totalWordsCount.get());
        System.out.printf("  Total Bytes        : %,d%n", totalBytesRead.get());
        System.out.printf("  Errors             : %d%n",  errorCount.get());
        System.out.println("─".repeat(65));
        System.out.printf("  %-25s %-8s %-8s %-10s %s%n",
                "File", "Lines", "Words", "Thread", "Time(ms)");
        System.out.println("─".repeat(65));

        results.stream()
               .sorted(Comparator.comparing(r -> r.fileName))
               .forEach(r -> {
                   if (r.success) {
                       System.out.printf("  %-25s %-8d %-8d %-10s %dms%n",
                               r.fileName, r.lines, r.words, r.threadName, r.durationMs);
                   } else {
                       System.out.printf("  %-25s ERROR: %s%n", r.fileName, r.errorMessage);
                   }
               });

        System.out.println("═".repeat(65));
    }

    public List<FileResult> getResults() {
        return new ArrayList<>(results);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN — Entry Point
    // ─────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║        TASK 3: Multithreaded File Processor              ║");
        System.out.println("║        High-Performance Java Concurrent App              ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        // ── Step 1: Setup sample input directory ──────────────────────────────
        Path inputDir = Paths.get("sample_files");
        SampleFileGenerator.generate(inputDir);

        // ── Step 2: Collect all .txt files ────────────────────────────────────
        List<Path> files;
        try (var stream = Files.list(inputDir)) {
            files = stream.filter(p -> p.toString().endsWith(".txt"))
                          .sorted()
                          .toList();
        }

        if (files.isEmpty()) {
            System.err.println("No .txt files found in: " + inputDir);
            return;
        }

        System.out.printf("Found %d files. Launching processor with 4 threads...%n%n", files.size());

        // ── Step 3: Create processor and submit all files ─────────────────────
        int threadPoolSize = Math.min(4, files.size());
        MultiThreadedFileProcessor processor =
                new MultiThreadedFileProcessor(threadPoolSize, files.size());

        long wallClockStart = System.currentTimeMillis();

        for (Path file : files) {
            processor.submit(file);
        }

        // ── Step 4: Wait (synchronization via CountDownLatch) ─────────────────
        processor.awaitCompletion();

        long wallClockMs = System.currentTimeMillis() - wallClockStart;

        // ── Step 5: Print results ─────────────────────────────────────────────
        processor.printSummary();
        System.out.printf("%n  Total Wall-Clock Time: %dms%n%n", wallClockMs);
        System.out.println("  Outcome: Professional industry skills acquired ✓");
        System.out.println();
    }
}
