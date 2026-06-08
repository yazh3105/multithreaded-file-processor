package com.fileprocessor;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test Suite: Multithreaded File Processor
 * Validates: correctness, concurrency safety, error handling, edge cases.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MultiThreadedFileProcessorTest {

    @TempDir
    Path tempDir;

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private Path createFile(String name, String... lines) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, Arrays.asList(lines));
        return file;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1: Single file — basic correctness
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(1)
    @DisplayName("TC-01: Single file processed correctly")
    void testSingleFileProcessing() throws Exception {
        Path file = createFile("single.txt",
                "Hello World",
                "Java Multithreading",
                "Concurrent Programming");

        MultiThreadedFileProcessor processor = new MultiThreadedFileProcessor(1, 1);
        processor.submit(file);
        processor.awaitCompletion();

        List<MultiThreadedFileProcessor.FileResult> results = processor.getResults();
        assertEquals(1, results.size());

        var r = results.get(0);
        assertTrue(r.success, "File should be processed successfully");
        assertEquals("single.txt", r.fileName);
        assertEquals(3, r.lines, "Should count 3 lines");
        assertEquals(6, r.words, "Should count 6 words");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2: Multiple files with multiple threads
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(2)
    @DisplayName("TC-02: Multiple files processed concurrently")
    void testMultipleFilesConcurrent() throws Exception {
        int fileCount = 8;
        List<Path> files = new ArrayList<>();
        for (int i = 1; i <= fileCount; i++) {
            files.add(createFile("file_" + i + ".txt",
                    "Line one of file " + i,
                    "Line two of file " + i,
                    "Line three of file " + i));
        }

        MultiThreadedFileProcessor processor = new MultiThreadedFileProcessor(4, fileCount);
        files.forEach(processor::submit);
        processor.awaitCompletion();

        List<MultiThreadedFileProcessor.FileResult> results = processor.getResults();
        assertEquals(fileCount, results.size(), "All files should be processed");

        long totalLines = results.stream().mapToLong(r -> r.lines).sum();
        assertEquals(fileCount * 3L, totalLines, "Total lines should be 3 per file");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3: Word count accuracy
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(3)
    @DisplayName("TC-03: Word count is accurate")
    void testWordCountAccuracy() throws Exception {
        Path file = createFile("words.txt",
                "one two three",       // 3 words
                "four five",           // 2 words
                "   six   seven   ",   // 2 words (extra spaces)
                "");                   // 0 words (empty line)

        MultiThreadedFileProcessor processor = new MultiThreadedFileProcessor(1, 1);
        processor.submit(file);
        processor.awaitCompletion();

        var result = processor.getResults().get(0);
        assertTrue(result.success);
        assertEquals(4, result.lines, "Should count 4 lines including empty");
        assertEquals(7, result.words, "Should count exactly 7 words");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 4: Non-existent file error handling
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(4)
    @DisplayName("TC-04: Non-existent file handled gracefully")
    void testNonExistentFile() throws Exception {
        Path ghost = tempDir.resolve("ghost_file.txt");

        MultiThreadedFileProcessor processor = new MultiThreadedFileProcessor(1, 1);
        processor.submit(ghost);
        processor.awaitCompletion();

        List<MultiThreadedFileProcessor.FileResult> results = processor.getResults();
        assertEquals(1, results.size());

        var r = results.get(0);
        assertFalse(r.success, "Should report failure for missing file");
        assertNotNull(r.errorMessage, "Should have an error message");
        assertEquals("ghost_file.txt", r.fileName);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 5: Empty file
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(5)
    @DisplayName("TC-05: Empty file returns zero counts")
    void testEmptyFile() throws Exception {
        Path file = createFile("empty.txt");

        MultiThreadedFileProcessor processor = new MultiThreadedFileProcessor(1, 1);
        processor.submit(file);
        processor.awaitCompletion();

        var r = processor.getResults().get(0);
        assertTrue(r.success);
        assertEquals(0, r.lines);
        assertEquals(0, r.words);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 6: Thread safety — no data corruption under heavy load
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(6)
    @DisplayName("TC-06: Thread safety under heavy concurrent load")
    void testThreadSafety() throws Exception {
        int fileCount = 20;
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < fileCount; i++) {
            files.add(createFile("stress_" + i + ".txt",
                    "word1 word2 word3 word4 word5")); // exactly 5 words per file
        }

        MultiThreadedFileProcessor processor = new MultiThreadedFileProcessor(8, fileCount);
        files.forEach(processor::submit);
        processor.awaitCompletion();

        List<MultiThreadedFileProcessor.FileResult> results = processor.getResults();
        assertEquals(fileCount, results.size(), "All files must be processed");

        long totalWords = results.stream().mapToLong(r -> r.words).sum();
        assertEquals(fileCount * 5L, totalWords, "Word count must be race-condition free");

        long totalLines = results.stream().mapToLong(r -> r.lines).sum();
        assertEquals(fileCount, totalLines, "Line count must be accurate");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 7: Mixed success/failure batch
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(7)
    @DisplayName("TC-07: Mixed valid and invalid files in same batch")
    void testMixedBatch() throws Exception {
        Path valid1 = createFile("valid1.txt", "Hello World");
        Path valid2 = createFile("valid2.txt", "Concurrent Java");
        Path ghost  = tempDir.resolve("nonexistent.txt");

        int total = 3;
        MultiThreadedFileProcessor processor = new MultiThreadedFileProcessor(3, total);
        processor.submit(valid1);
        processor.submit(valid2);
        processor.submit(ghost);
        processor.awaitCompletion();

        List<MultiThreadedFileProcessor.FileResult> results = processor.getResults();
        assertEquals(3, results.size());

        long successes = results.stream().filter(r -> r.success).count();
        long failures  = results.stream().filter(r -> !r.success).count();

        assertEquals(2, successes, "2 files should succeed");
        assertEquals(1, failures,  "1 file should fail");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 8: Thread pool uses multiple threads (not sequential)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(8)
    @DisplayName("TC-08: Multiple threads are actually used")
    void testMultipleThreadsUsed() throws Exception {
        int fileCount = 10;
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < fileCount; i++) {
            files.add(createFile("thread_test_" + i + ".txt", "content " + i));
        }

        MultiThreadedFileProcessor processor = new MultiThreadedFileProcessor(4, fileCount);
        files.forEach(processor::submit);
        processor.awaitCompletion();

        List<MultiThreadedFileProcessor.FileResult> results = processor.getResults();
        long distinctThreads = results.stream()
                .map(r -> r.threadName)
                .distinct()
                .count();

        assertTrue(distinctThreads > 1,
                "Expected multiple threads to be used, got: " + distinctThreads);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 9: Large file processing
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(9)
    @DisplayName("TC-09: Large file (10,000 lines) processed correctly")
    void testLargeFile() throws Exception {
        Path file = tempDir.resolve("large.txt");
        int lineCount = 10_000;
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
            for (int i = 0; i < lineCount; i++) {
                w.println("word1 word2 word3"); // 3 words per line
            }
        }

        MultiThreadedFileProcessor processor = new MultiThreadedFileProcessor(2, 1);
        processor.submit(file);
        processor.awaitCompletion();

        var r = processor.getResults().get(0);
        assertTrue(r.success);
        assertEquals(lineCount, r.lines);
        assertEquals(lineCount * 3L, r.words);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 10: Duration is recorded
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(10)
    @DisplayName("TC-10: Processing duration is recorded")
    void testDurationRecorded() throws Exception {
        Path file = createFile("timed.txt", "Measuring performance", "Is a core skill");

        MultiThreadedFileProcessor processor = new MultiThreadedFileProcessor(1, 1);
        processor.submit(file);
        processor.awaitCompletion();

        var r = processor.getResults().get(0);
        assertTrue(r.success);
        assertTrue(r.durationMs >= 0, "Duration should be non-negative");
    }
}
