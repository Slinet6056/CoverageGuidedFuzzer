package com.example.fuzzer.tools;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Crash数据导出工具
 * 用法：java -jar crash-exporter.jar [options]
 * 选项：
 * -b, --block-file &lt;path&gt;    压缩文件路径
 * -m, --meta-file &lt;path&gt;     元数据文件路径
 * -o, --output-dir &lt;path&gt;    输出目录路径
 * -l, --list                   只列出crash信息，不导出文件
 */
public class CrashDataExporter {
    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB buffer

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String blockFile = null;
        String metaFile = null;
        String outputDir = null;
        boolean listOnly = false;

        // 解析命令行参数
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-b":
                case "--block-file":
                    if (i + 1 < args.length) blockFile = args[++i];
                    break;
                case "-m":
                case "--meta-file":
                    if (i + 1 < args.length) metaFile = args[++i];
                    break;
                case "-o":
                case "--output-dir":
                    if (i + 1 < args.length) outputDir = args[++i];
                    break;
                case "-l":
                case "--list":
                    listOnly = true;
                    break;
                case "-h":
                case "--help":
                    printUsage();
                    System.exit(0);
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        // 验证必要参数
        if (blockFile == null || metaFile == null) {
            System.err.println("Error: Block file and meta file are required");
            printUsage();
            System.exit(1);
        }

        if (!listOnly && outputDir == null) {
            System.err.println("Error: Output directory is required when not in list mode");
            printUsage();
            System.exit(1);
        }

        try {
            Path blockPath = Paths.get(blockFile);
            Path metaPath = Paths.get(metaFile);

            if (!Files.exists(blockPath)) {
                throw new IOException("Block file not found: " + blockFile);
            }
            if (!Files.exists(metaPath)) {
                throw new IOException("Meta file not found: " + metaFile);
            }

            // 读取元数据
            List<CrashInfo> crashes = readMetadata(metaPath);

            if (listOnly) {
                // 只显示crash信息
                System.out.println("Found " + crashes.size() + " crashes:");
                System.out.println("ID\t\tExit Code\tSize\t\tOffset");
                System.out.println("------------------------------------------------");
                for (CrashInfo crash : crashes) {
                    System.out.printf("%d\t\t%d\t\t%d\t\t%d%n",
                            crash.crashId, crash.exitCode, crash.length, crash.offset);
                }
            } else {
                // 导出文件
                Path outputPath = Paths.get(outputDir);
                Files.createDirectories(outputPath);
                exportCrashes(blockPath, crashes, outputPath);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar crash-exporter.jar [options]");
        System.out.println("Options:");
        System.out.println("  -b, --block-file <path>    压缩文件路径");
        System.out.println("  -m, --meta-file <path>     元数据文件路径");
        System.out.println("  -o, --output-dir <path>    输出目录路径");
        System.out.println("  -l, --list                 只列出crash信息，不导出文件");
        System.out.println("  -h, --help                 显示帮助信息");
        System.out.println("\nExample:");
        System.out.println("  列出crash信息：");
        System.out.println("    java -jar crash-exporter.jar -b block_000000.gz -m block_000000.meta -l");
        System.out.println("  导出crash文件：");
        System.out.println("    java -jar crash-exporter.jar -b block_000000.gz -m block_000000.meta -o ./output");
    }

    private static List<CrashInfo> readMetadata(Path metaPath) throws IOException {
        List<CrashInfo> crashes = new ArrayList<>();
        List<String> lines = Files.readAllLines(metaPath);

        for (String line : lines) {
            String[] parts = line.split(",");
            if (parts.length >= 4) {
                crashes.add(new CrashInfo(
                        Integer.parseInt(parts[0]),  // crashId
                        Integer.parseInt(parts[1]),  // exitCode
                        Long.parseLong(parts[2]),    // offset
                        Integer.parseInt(parts[3])   // length
                ));
            }
        }

        return crashes;
    }

    private static void exportCrashes(Path blockPath, List<CrashInfo> crashes, Path outputDir)
            throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(blockPath), BUFFER_SIZE);
             GZIPInputStream gzis = new GZIPInputStream(bis)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            int bytesRead;
            while ((bytesRead = gzis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }

            byte[] data = baos.toByteArray();

            int count = 0;
            for (CrashInfo crash : crashes) {
                String filename = String.format("id_%016d_exitcode_%d.crash",
                        crash.crashId, crash.exitCode);
                Path outputFile = outputDir.resolve(filename);

                Files.write(outputFile,
                        Arrays.copyOfRange(data, (int) crash.offset,
                                (int) (crash.offset + crash.length)));
                count++;

                // 显示进度
                if (count % 100 == 0 || count == crashes.size()) {
                    System.out.printf("\rExported %d/%d crashes...", count, crashes.size());
                }
            }
            System.out.println("\nDone!");
        }
    }

    private static class CrashInfo {
        final int crashId;
        final int exitCode;
        final long offset;
        final int length;

        CrashInfo(int crashId, int exitCode, long offset, int length) {
            this.crashId = crashId;
            this.exitCode = exitCode;
            this.offset = offset;
            this.length = length;
        }
    }
}
