package com.example.fuzzer.monitor;

import com.example.fuzzer.execution.ExecutionResult;
import com.example.fuzzer.logging.FuzzingLogger;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
    private static final long COVERAGE_REPORT_UPDATE_INTERVAL = 30000; // 每30秒更新一次报告
    private static final long STATS_UPDATE_INTERVAL = 30000; // 每30秒更新一次性能统计
    private static final long CHART_UPDATE_INTERVAL = 30000; // 每30秒更新一次图表
    private final Path outputDir;
    private final Path queueDir;
    private final Path crashesDir;
    private final Path hangsDir;
    private final Path plotsDir;      // 新增：图表目录
    private final Path logsDir;       // 新增：日志目录
    private final FuzzingLogger logger;
    private final TimeSeries execSpeedSeries;
    private final TimeSeries coverageSeries;
    private final TimeSeries crashSeries;
    private final TimeSeries hangSeries;
    private final TimeSeriesCollection performanceDataset;
    private final TimeSeriesCollection anomalyDataset;
    private volatile long lastCoverageReportTime = 0;
    private volatile long lastStatsUpdateTime = 0;
    private volatile long lastChartUpdateTime = 0;

    public OutputManager(String outputPath) throws IOException {
        this.outputDir = Paths.get(outputPath);
        this.queueDir = outputDir.resolve("queue");
        this.crashesDir = outputDir.resolve("crashes");
        this.hangsDir = outputDir.resolve("hangs");
        this.plotsDir = outputDir.resolve("plots");
        this.logsDir = outputDir.resolve("logs");

        initializeDirectories();
        this.logger = FuzzingLogger.createInstance(this.logsDir);
        createReadme();

        // 初始化时间序列
        execSpeedSeries = new TimeSeries("Execution Speed");
        coverageSeries = new TimeSeries("Coverage");
        crashSeries = new TimeSeries("Crashes");
        hangSeries = new TimeSeries("Hangs");

        // 创建数据集
        performanceDataset = new TimeSeriesCollection();
        performanceDataset.addSeries(execSpeedSeries);
        performanceDataset.addSeries(coverageSeries);

        anomalyDataset = new TimeSeriesCollection();
        anomalyDataset.addSeries(crashSeries);
        anomalyDataset.addSeries(hangSeries);
    }

    private void initializeDirectories() throws IOException {
        // 创建所需的目录
        Files.createDirectories(outputDir);
        Files.createDirectories(queueDir);
        Files.createDirectories(crashesDir);
        Files.createDirectories(hangsDir);
        Files.createDirectories(plotsDir);      // 新增
        Files.createDirectories(logsDir);       // 新增
    }

    private void createReadme() throws IOException {
        Path readmePath = outputDir.resolve(README);
        if (!Files.exists(readmePath)) {
            String readme = "Coverage-Guided Fuzzer Output Directory\n" +
                    "===================================\n\n" +
                    "Directory Structure:\n" +
                    "- queue/: Directory containing all test cases\n" +
                    "- crashes/: Directory containing inputs that caused crashes\n" +
                    "- hangs/: Directory containing inputs that caused hangs\n" +
                    "- plots/: Coverage and performance graphs\n" +
                    "- logs/: Detailed execution logs\n" +
                    "\nFiles:\n" +
                    "- cmdline: Command line used for running the target\n" +
                    "- fuzz_bitmap: Fuzzer bitmap data\n" +
                    "- fuzzer_setup: Fuzzer configuration details\n" +
                    "- fuzzer_stats: Internal fuzzer state statistics\n" +
                    "- plot_data: Fuzzing test statistical data\n" +
                    "- coverage_report.html: Interactive coverage report\n\n" +
                    "Created: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n";

            Files.write(readmePath, readme.getBytes(), StandardOpenOption.CREATE);
        }
    }

    public void writeCmdline(String[] args) throws IOException {
        Path cmdlinePath = outputDir.resolve(CMDLINE_FILE);
        String cmdline = String.join(" ", args) + System.lineSeparator();
        Files.write(cmdlinePath, cmdline.getBytes(), StandardOpenOption.CREATE);
    }

    public void writeFuzzerSetup(String targetProgram, String[] args, String outputPath) throws IOException {
        Path setupPath = outputDir.resolve(FUZZER_SETUP);
        StringBuilder setup = new StringBuilder();

        // Environment variables
        setup.append("# environment variables:\n");
        setup.append("CUSTOM_FUZZER_TARGET=").append(targetProgram).append("\n");
        setup.append("CUSTOM_FUZZER_OUTPUT=").append(outputPath).append("\n");

        // Command line
        setup.append("# command line:\n");
        setup.append("'custom-fuzzer'");
        if (args != null && args.length > 0) {
            setup.append(" '").append(String.join("' '", args)).append("'");
        }
        setup.append(" -- '").append(targetProgram).append("'\n");

        Files.write(setupPath, setup.toString().getBytes(), StandardOpenOption.CREATE);
    }

    public void updateFuzzerStats(
            long startTime,
            long totalExecutions,
            double execsPerSec,
            int queueCount,
            int crashCount,
            int hangCount,
            double bitmapCoverage,
            int edgesFound,
            long lastFindTime,
            long lastCrashTime,
            long lastHangTime) throws IOException {

        Path statsPath = outputDir.resolve(FUZZER_STATS);
        StringBuilder stats = new StringBuilder();
        long currentTime = System.currentTimeMillis();

        // 检查更新间隔
        if (currentTime - lastStatsUpdateTime < STATS_UPDATE_INTERVAL) {
            return;
        }
        lastStatsUpdateTime = currentTime;

        // 更新时间序列数据
        Second currentSecond = new Second(new Date(currentTime));
        execSpeedSeries.addOrUpdate(currentSecond, execsPerSec);
        coverageSeries.addOrUpdate(currentSecond, bitmapCoverage);
        crashSeries.addOrUpdate(currentSecond, crashCount);
        hangSeries.addOrUpdate(currentSecond, hangCount);

        // 定期更新图表
        if (currentTime - lastChartUpdateTime >= CHART_UPDATE_INTERVAL) {
            lastChartUpdateTime = currentTime;
            try {
                // 生成性能图表
                ChartGenerator.generatePerformanceChart(
                        plotsDir.resolve("performance_chart.png").toFile(),
                        performanceDataset
                );

                // 生成异常趋势图表
                ChartGenerator.generateAnomalyTrendChart(
                        plotsDir.resolve("anomaly_trend_chart.png").toFile(),
                        anomalyDataset
                );
            } catch (IOException e) {
                logger.error("生成图表时发生错误:", e);
            }
        }

        currentTime = currentTime / 1000;  // Convert to seconds

        stats.append(String.format("start_time        : %d\n", startTime / 1000));
        stats.append(String.format("last_update       : %d\n", currentTime));
        stats.append(String.format("run_time          : %d\n", (currentTime - startTime / 1000)));
        stats.append(String.format("fuzzer_pid        : %d\n", ProcessHandle.current().pid()));
        stats.append(String.format("execs_done        : %d\n", totalExecutions));
        stats.append(String.format("execs_per_sec     : %.2f\n", execsPerSec));
        stats.append(String.format("corpus_count      : %d\n", queueCount));
        stats.append(String.format("saved_crashes     : %d\n", crashCount));
        stats.append(String.format("saved_hangs       : %d\n", hangCount));
        stats.append(String.format("last_find         : %d\n", lastFindTime / 1000));
        stats.append(String.format("last_crash        : %d\n", lastCrashTime / 1000));
        stats.append(String.format("last_hang         : %d\n", lastHangTime / 1000));
        stats.append(String.format("bitmap_cvg        : %.2f%%\n", bitmapCoverage));
        stats.append(String.format("edges_found       : %d\n", edgesFound));

        Files.write(statsPath, stats.toString().getBytes(), StandardOpenOption.CREATE);

        // 记录到性能日志
        String logEntry = String.format("Executions: %d (%.2f/s), Coverage: %.2f%%, Queue: %d, Crashes: %d, Hangs: %d",
                totalExecutions, execsPerSec, bitmapCoverage, queueCount, crashCount, hangCount);
        logger.performance(logEntry);
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

    public void saveQueueInput(byte[] input, String id, ExecutionResult result, boolean newCoverage) throws IOException {
        // 只在覆盖率增加时保存文件
        if (!newCoverage) {
            return;
        }

        // 构建文件名：使用执行时间而不是系统时间
        String filename = String.format("id:%016d,exec_time:%d,execs:%d",
                Long.parseLong(id),
                result.getExecutionTime(),
                result.getExecutionCount());

        Path inputPath = queueDir.resolve(filename);
        Files.write(inputPath, input, StandardOpenOption.CREATE);

        try {
            logger.coverage(id);
        } catch (IOException e) {
            System.err.println("写入日志时出错: " + e.getMessage());
        }
    }

    public void saveCrashInput(byte[] input, int exitCode) throws IOException {
        int crashId = uniqueCrashCount.incrementAndGet();
        String crashName = String.format("id:%016d,exitcode:%d",
                crashId,
                exitCode);
        Path crashPath = crashesDir.resolve(crashName);
//        Files.write(crashPath, input, StandardOpenOption.CREATE);
    }

    public void saveHangInput(byte[] input, long executionTime) throws IOException {
        int hangId = uniqueHangCount.incrementAndGet();
        String hangName = String.format("id:%016d,exec_time:%d",
                hangId,
                executionTime);
        Path hangPath = hangsDir.resolve(hangName);
//        Files.write(hangPath, input, StandardOpenOption.CREATE);
    }

    public void writeCoverageReport(byte[] bitmap, AtomicLong totalExecutions, long startTime,
                                    double peakExecSpeed, int queueSize, int crashCount, int hangCount) throws IOException {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCoverageReportTime < COVERAGE_REPORT_UPDATE_INTERVAL) {
            return;  // 如果距离上次更新时间不够，就跳过
        }
        lastCoverageReportTime = currentTime;

        Path reportPath = outputDir.resolve("coverage_report.html");
        StringBuilder report = new StringBuilder();
        report.append("<!DOCTYPE html>\n")
                .append("<html>\n")
                .append("<head>\n")
                .append("    <title>Fuzzing Coverage Report</title>\n")
                .append("    <style>\n")
                .append("        body { font-family: Arial, sans-serif; margin: 40px; }\n")
                .append("        .container { max-width: 1200px; margin: 0 auto; }\n")
                .append("        .stat-box { background: #f5f5f5; padding: 20px; margin: 10px 0; border-radius: 5px; }\n")
                .append("        .progress-bar { background: #eee; height: 20px; border-radius: 10px; overflow: hidden; }\n")
                .append("        .progress { background: #4CAF50; height: 100%; transition: width 0.3s ease; }\n")
                .append("        .timestamp { color: #666; font-size: 0.9em; }\n")
                .append("        .stats-table { width: 100%; border-collapse: collapse; margin-top: 20px; }\n")
                .append("        .stats-table td, .stats-table th { padding: 8px; border: 1px solid #ddd; }\n")
                .append("        .stats-table th { background-color: #f5f5f5; }\n")
                .append("    </style>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("    <div class='container'>\n")
                .append("        <h1>Fuzzing Coverage Report</h1>\n")
                .append("        <p class='timestamp'>Last Updated: ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("</p>\n");

        // 如果提供了执行统计信息，添加执行统计表格
        if (totalExecutions != null) {
            double execPerSec = totalExecutions.get() / Math.max(1, (currentTime - startTime) / 1000.0);
            report.append("        <div class='stat-box'>\n")
                    .append("            <h2>Execution Statistics</h2>\n")
                    .append("            <table class='stats-table'>\n")
                    .append("                <tr><th>Metric</th><th>Value</th></tr>\n")
                    .append(String.format("                <tr><td>Total Executions</td><td>%,d</td></tr>\n", totalExecutions.get()))
                    .append(String.format("                <tr><td>Execution Speed</td><td>%.0f/s</td></tr>\n", execPerSec))
                    .append(String.format("                <tr><td>Peak Speed</td><td>%.0f/s</td></tr>\n", peakExecSpeed))
                    .append(String.format("                <tr><td>Queue Size</td><td>%d</td></tr>\n", queueSize))
                    .append(String.format("                <tr><td>Crashes</td><td>%d</td></tr>\n", crashCount))
                    .append(String.format("                <tr><td>Hangs</td><td>%d</td></tr>\n", hangCount))
                    .append("            </table>\n")
                    .append("        </div>\n");
        }

        // 计算统计信息
        int totalEdges = bitmap.length;
        int coveredEdges = 0;
        int neverHit = 0;
        int rarelyHit = 0;   // 1-4次
        int commonHit = 0;   // 5-99次
        int frequentHit = 0; // 100+次

        for (byte b : bitmap) {
            if (b != 0) {
                coveredEdges++;
                if (b < 5) rarelyHit++;
                else if (b < 100) commonHit++;
                else frequentHit++;
            } else {
                neverHit++;
            }
        }

        // 总体覆盖率
        double coveragePercent = (coveredEdges * 100.0) / totalEdges;
        report.append("        <div class='stat-box'>\n")
                .append("            <h2>Overall Coverage</h2>\n")
                .append("            <div class='progress-bar'>\n")
                .append(String.format("                <div class='progress' style='width: %.2f%%'></div>\n", coveragePercent))
                .append("            </div>\n")
                .append(String.format("            <p>%.2f%% (%d of %d edges)</p>\n", coveragePercent, coveredEdges, totalEdges))
                .append("        </div>\n");

        // 添加性能趋势图
        report.append("        <div class='stat-box'>\n")
                .append("            <h2>Performance Trend</h2>\n")
                .append("            <img src='plots/performance_chart.png' alt='Performance Trend' style='width:100%; max-width:1000px;'/>\n")
                .append("        </div>\n");

        // 添加异常发现趋势图
        report.append("        <div class='stat-box'>\n")
                .append("            <h2>Anomaly Discovery Trend</h2>\n")
                .append("            <img src='plots/anomaly_trend_chart.png' alt='Anomaly Trend' style='width:100%; max-width:1000px;'/>\n")
                .append("        </div>\n");

        // 详细统计
        report.append("        <div class='stat-box'>\n")
                .append("            <h2>Detailed Statistics</h2>\n")
                .append("            <table width='100%'>\n")
                .append("                <tr><td>Never Hit:</td>")
                .append(String.format("<td>%d (%.2f%%)</td></tr>\n", neverHit, (neverHit * 100.0) / totalEdges))
                .append("                <tr><td>Rarely Hit (1-4 times):</td>")
                .append(String.format("<td>%d (%.2f%%)</td></tr>\n", rarelyHit, (rarelyHit * 100.0) / totalEdges))
                .append("                <tr><td>Common Hit (5-99 times):</td>")
                .append(String.format("<td>%d (%.2f%%)</td></tr>\n", commonHit, (commonHit * 100.0) / totalEdges))
                .append("                <tr><td>Frequent Hit (100+ times):</td>")
                .append(String.format("<td>%d (%.2f%%)</td></tr>\n", frequentHit, (frequentHit * 100.0) / totalEdges))
                .append("            </table>\n")
                .append("        </div>\n");

        report.append("    </div>\n")
                .append("</body>\n")
                .append("</html>");

        Files.write(reportPath, report.toString().getBytes(), StandardOpenOption.CREATE);
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

    public Path getHangsDir() {
        return hangsDir;
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
