# Multithreaded File Processor
> Task 3 · Java Developer Internship · Difficulty: Medium

A high-performance Java application that uses **Multithreading and Synchronization** to process multiple files concurrently.

---

## Features
- Concurrent file processing using a fixed thread pool
- Thread pool management via `ExecutorService`
- `CountDownLatch` synchronization — main thread waits until all workers finish
- Lock-free shared statistics with `AtomicLong` / `AtomicInteger`
- Thread-safe result collection using `ConcurrentLinkedQueue`
- Graceful error handling — one bad file won't crash others
- Detailed summary report with per-file stats (lines, words, bytes, thread, time)

---

## Project Structure

```
multithreaded-file-processor/
├── src/
│   ├── main/java/com/fileprocessor/
│   │   ├── MultiThreadedFileProcessor.java   # Core app + thread pool
│   │   └── SampleFileGenerator.java          # Generates demo input files
│   └── test/java/com/fileprocessor/
│       └── MultiThreadedFileProcessorTest.java  # 10 JUnit 5 test cases
├── pom.xml                                   # Maven build config
└── README.md
```

---

## Requirements
- Java 17+
- Maven 3.6+

---

## Run

```bash
# Compile the project
mvn compile

# Run the application
mvn exec:java -Dexec.mainClass=com.fileprocessor.MultiThreadedFileProcessor
```

Sample output:
```
Found 8 files. Launching processor with 4 threads...

[pool-1-thread-1] Starting: server_access.log.txt
[pool-1-thread-2] Starting: employee_records.txt
[pool-1-thread-3] Starting: q1_sales_report.txt
[pool-1-thread-4] Starting: config_properties.txt

═════════════════════════════════════════════════════════════════
           MULTITHREADED FILE PROCESSOR — RESULTS
═════════════════════════════════════════════════════════════════
  Thread Pool Size   : 4 threads
  Files Processed    : 8
  Total Lines        : 1,903
  Total Words        : 17,241
  Total Bytes        : 89,472
  Errors             : 0
═════════════════════════════════════════════════════════════════

  Total Wall-Clock Time: 28ms
  Outcome: Professional industry skills acquired ✓
```

---

## Test

```bash
mvn test
```

| Test | Description | Status |
|------|-------------|--------|
| TC-01 | Single file processed correctly | ✅ PASS |
| TC-02 | Multiple files processed concurrently | ✅ PASS |
| TC-03 | Word count accuracy (spaces, empty lines) | ✅ PASS |
| TC-04 | Non-existent file handled gracefully | ✅ PASS |
| TC-05 | Empty file returns zero counts | ✅ PASS |
| TC-06 | Thread safety under heavy load (20 files) | ✅ PASS |
| TC-07 | Mixed valid/invalid files in same batch | ✅ PASS |
| TC-08 | Multiple threads actually used | ✅ PASS |
| TC-09 | Large file (10,000 lines) processed correctly | ✅ PASS |
| TC-10 | Processing duration is recorded | ✅ PASS |

**10/10 tests passing.**

---

## Concurrency Design

```
main()
  └── ExecutorService (Fixed Thread Pool × 4)
        ├── Thread-1 → FileProcessingTask → AtomicLong (stats)
        ├── Thread-2 → FileProcessingTask → AtomicLong (stats)
        ├── Thread-3 → FileProcessingTask → AtomicLong (stats)
        └── Thread-4 → FileProcessingTask → AtomicLong (stats)
                                  ↓
                        CountDownLatch.await()   ← main thread blocks here
                                  ↓
                        ConcurrentLinkedQueue    ← all results collected
                                  ↓
                           Summary Report
```

---

## Technologies Used
- **Java 17** — core language
- **ExecutorService** — thread pool management
- **CountDownLatch** — synchronization barrier
- **AtomicLong / AtomicInteger** — lock-free counters
- **ConcurrentLinkedQueue** — thread-safe result storage
- **JUnit 5** — unit testing
- **Maven** — build and dependency management

---

## Outcome
> *Acquire professional industry skills.* ✓

Built as part of a Java Developer Internship program to demonstrate real-world concurrent programming patterns used in production systems.
