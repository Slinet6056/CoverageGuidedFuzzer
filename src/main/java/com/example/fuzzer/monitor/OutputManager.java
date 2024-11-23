package com.example.fuzzer.monitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 管理模糊测试的输出目录和文件
 */
public class OutputManager {
    private static final String CMDLINE_FILE = "cmdline";
    private static final String FUZZ_BITMAP = "fuzz_bitmap";
    private static final String FUZZER_SETUP = "fuzzer_setup";
    private static final String FUZZER_STATS = "fuzzer_stats";
    private static final String PLOT_DATA = "plot_data";
    private static final String README = "README.txt";
    private static final AtomicInteger uniqueCrashCount = new AtomicInteger(0);
    private static final AtomicInteger uniqueHangCount = new AtomicInteger(0);
    private final Path outputDir;
    private final Path queueDir;
    private final Path crashesDir;
    private final Path currentDir;
    private final Path hangsDir;
    private final Path metadataDir;  // 新增：元数据目录
    private final Path plotsDir;      // 新增：图表目录
    private final Path logsDir;       // 新增：日志目录
    // 新增：记录每个测试用例的元数据
    private final Map<String, Map<String, String>> inputMetadata = new HashMap<>();

    public OutputManager(String outputPath) throws IOException {
        this.outputDir = Paths.get(outputPath);
        this.queueDir = outputDir.resolve("queue");
        this.crashesDir = outputDir.resolve("crashes");
        this.currentDir = outputDir.resolve("current");
        this.hangsDir = outputDir.resolve("hangs");
        this.metadataDir = outputDir.resolve("metadata");  // 新增
        this.plotsDir = outputDir.resolve("plots");        // 新增
        this.logsDir = outputDir.resolve("logs");          // 新增
        initializeDirectories();
        createReadme();
    }

    private void initializeDirectories() throws IOException {
        // 创建所需的目录
        Files.createDirectories(outputDir);
        Files.createDirectories(queueDir);
        Files.createDirectories(crashesDir);
        Files.createDirectories(currentDir);
        Files.createDirectories(hangsDir);
        Files.createDirectories(metadataDir);  // 新增
        Files.createDirectories(plotsDir);      // 新增
        Files.createDirectories(logsDir);       // 新增

        // 清理current目录中的临时文件
        cleanDirectory(currentDir);
    }

    private void cleanDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.list(dir).forEach(file -> {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    // 忽略删除失败的文件
                }
            });
        }
    }

    private void createReadme() throws IOException {
        Path readmePath = outputDir.resolve(README);
        if (!Files.exists(readmePath)) {
            StringBuilder readme = new StringBuilder();
            readme.append("Coverage-Guided Fuzzer Output Directory\n");
            readme.append("===================================\n\n");
            readme.append("Directory Structure:\n");
            readme.append("- cmdline: Command line used for running the target\n");
            readme.append("- crashes/: Test inputs that caused crashes\n");
            readme.append("- hangs/: Test inputs that caused timeouts\n");
            readme.append("- queue/: Test inputs that increased coverage\n");
            readme.append("- current/: Temporary files for ongoing test inputs\n");
            readme.append("- metadata/: Detailed information about test cases\n");  // 新增
            readme.append("- plots/: Coverage and performance graphs\n");           // 新增
            readme.append("- logs/: Detailed execution logs\n");                    // 新增
            readme.append("- fuzz_bitmap: Fuzzer bitmap data\n");
            readme.append("- fuzzer_setup: Fuzzer configuration details\n");
            readme.append("- fuzzer_stats: Internal fuzzer state statistics\n");
            readme.append("- plot_data: Fuzzing test statistical data\n");
            readme.append("- coverage_report.html: Interactive coverage report\n\n");  // 新增
            readme.append("Created: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");

            Files.write(readmePath, readme.toString().getBytes(), StandardOpenOption.CREATE);
        }
    }

    public void writeCmdline(String[] args) throws IOException {
        Path cmdlinePath = outputDir.resolve(CMDLINE_FILE);
        Files.write(cmdlinePath, Arrays.asList(args), StandardOpenOption.CREATE);
    }

    public void writeFuzzerSetup(String setupInfo) throws IOException {
        Path setupPath = outputDir.resolve(FUZZER_SETUP);
        Files.write(setupPath, setupInfo.getBytes(), StandardOpenOption.CREATE);

        // 新增：记录到日志
        appendToLog("fuzzer_setup.log", setupInfo);
    }

    public void updateFuzzerStats(String stats) throws IOException {
        Path statsPath = outputDir.resolve(FUZZER_STATS);
        Files.write(statsPath, stats.getBytes(), StandardOpenOption.CREATE);

        // 新增：记录到日志
        appendToLog("fuzzer_stats.log", stats);
    }

    public void appendPlotData(String plotLine) throws IOException {
        Path plotPath = outputDir.resolve(PLOT_DATA);
        Files.write(plotPath, plotLine.getBytes(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public void writeFuzzBitmap(byte[] bitmap) throws IOException {
        Path bitmapPath = outputDir.resolve(FUZZ_BITMAP);
        Files.write(bitmapPath, bitmap, StandardOpenOption.CREATE);
    }

    public Path saveQueueInput(byte[] input, String id) throws IOException {
        Path inputPath = queueDir.resolve("id_" + id);
        Files.write(inputPath, input, StandardOpenOption.CREATE);

        // 创建元数据
        Map<String, String> metadata = new HashMap<>();
        metadata.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        metadata.put("size", String.valueOf(input.length));
        metadata.put("type", "queue");
        metadata.put("id", id);

        // 保存元数据
        saveMetadata(inputPath, metadata);

        return inputPath;
    }

    public Path saveCrashInput(byte[] input, int exitCode) throws IOException {
        int crashId = uniqueCrashCount.incrementAndGet();
        String crashName = String.format("crash-%d-time-%d-exitcode-%d",
                crashId,
                System.currentTimeMillis(),
                exitCode);
        Path crashPath = crashesDir.resolve(crashName);
        Files.write(crashPath, input, StandardOpenOption.CREATE);

        // 创建元数据
        Map<String, String> metadata = new HashMap<>();
        metadata.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        metadata.put("size", String.valueOf(input.length));
        metadata.put("type", "crash");
        metadata.put("exit_code", String.valueOf(exitCode));
        metadata.put("id", String.valueOf(crashId));

        // 保存元数据
        saveMetadata(crashPath, metadata);

        // 新增：记录到日志
        String logEntry = String.format("[%s] New crash found: %s (exit code: %d)\n",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                crashName, exitCode);
        appendToLog("crashes.log", logEntry);

        return crashPath;
    }

    public Path saveHangInput(byte[] input, long executionTime) throws IOException {
        int hangId = uniqueHangCount.incrementAndGet();
        String hangName = String.format("hang-%d-time-%d-exec-%dms",
                hangId,
                System.currentTimeMillis(),
                executionTime);
        Path hangPath = hangsDir.resolve(hangName);
        Files.write(hangPath, input, StandardOpenOption.CREATE);

        // 创建元数据
        Map<String, String> metadata = new HashMap<>();
        metadata.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        metadata.put("size", String.valueOf(input.length));
        metadata.put("type", "hang");
        metadata.put("execution_time", String.valueOf(executionTime));
        metadata.put("id", String.valueOf(hangId));

        // 保存元数据
        saveMetadata(hangPath, metadata);

        // 新增：记录到日志
        String logEntry = String.format("[%s] New hang found: %s (execution time: %d ms)\n",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                hangName, executionTime);
        appendToLog("hangs.log", logEntry);

        return hangPath;
    }

    public Path saveCurrentInput(byte[] input) throws IOException {
        Path currentPath = currentDir.resolve("current_input");
        Files.write(currentPath, input, StandardOpenOption.CREATE);
        return currentPath;
    }

    private void saveMetadata(Path inputPath, Map<String, String> metadata) throws IOException {
        String filename = inputPath.getFileName().toString();
        Path metaPath = metadataDir.resolve(filename + ".meta");

        StringBuilder metaContent = new StringBuilder();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            metaContent.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        Files.write(metaPath, metaContent.toString().getBytes(), StandardOpenOption.CREATE);
        inputMetadata.put(filename, metadata);
    }

    private void appendToLog(String logFile, String content) throws IOException {
        Path logPath = logsDir.resolve(logFile);
        Files.write(logPath, content.getBytes(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public Map<String, String> getInputMetadata(String filename) {
        return inputMetadata.getOrDefault(filename, new HashMap<>());
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public Path getQueueDir() {
        return queueDir;
    }

    public Path getCrashesDir() {
        return crashesDir;
    }

    public Path getCurrentDir() {
        return currentDir;
    }

    public Path getHangsDir() {
        return hangsDir;
    }

    public Path getMetadataDir() {
        return metadataDir;
    }

    public Path getPlotsDir() {
        return plotsDir;
    }

    public Path getLogsDir() {
        return logsDir;
    }

    public int getUniqueCrashCount() {
        return uniqueCrashCount.get();
    }

    public int getUniqueHangCount() {
        return uniqueHangCount.get();
    }
}
