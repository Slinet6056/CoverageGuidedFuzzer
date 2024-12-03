package com.example.fuzzer.tools;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 用于导出和查看压缩存储的crash文件的工具类
 */
public class CrashExporter {
    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB buffer

    /**
     * 将压缩存储的crash文件导出为单独的文件
     *
     * @param blockFile 压缩文件路径
     * @param outputDir 输出目录
     * @return 导出的文件数量
     */
    public static int exportCrashes(Path blockFile, Path outputDir) throws IOException {
        if (!Files.exists(blockFile)) {
            throw new IOException("Block file not found: " + blockFile);
        }

        Files.createDirectories(outputDir);
        int count = 0;
        long offset = 0;

        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(blockFile), BUFFER_SIZE);
             GZIPInputStream gzis = new GZIPInputStream(bis)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            int bytesRead;
            while ((bytesRead = gzis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }

            // 解析文件名以获取block ID
            String blockId = blockFile.getFileName().toString().replaceAll("block_|.gz", "");

            // 读取元数据文件（如果存在）
            Path metadataFile = blockFile.resolveSibling("block_" + blockId + ".meta");
            Map<Long, CrashInfo> offsetMap = new HashMap<>();

            if (Files.exists(metadataFile)) {
                List<String> lines = Files.readAllLines(metadataFile);
                for (String line : lines) {
                    String[] parts = line.split(",");
                    if (parts.length >= 4) {
                        long crashOffset = Long.parseLong(parts[2]);
                        offsetMap.put(crashOffset, new CrashInfo(
                                Integer.parseInt(parts[0]),  // crashId
                                Integer.parseInt(parts[1]),  // exitCode
                                Integer.parseInt(parts[3])   // length
                        ));
                    }
                }
            }

            byte[] data = baos.toByteArray();

            // 如果有元数据，按照元数据导出
            if (!offsetMap.isEmpty()) {
                for (Map.Entry<Long, CrashInfo> entry : offsetMap.entrySet()) {
                    CrashInfo info = entry.getValue();
                    String filename = String.format("id_%016d_exitcode_%d.crash",
                            info.crashId, info.exitCode);
                    Path outputFile = outputDir.resolve(filename);

                    Files.write(outputFile,
                            Arrays.copyOfRange(data, (int) entry.getKey().longValue(),
                                    (int) (entry.getKey() + info.length)));
                    count++;
                }
            } else {
                // 如果没有元数据，尝试按固定大小分割（不推荐，但作为备选方案）
                String filename = String.format("unknown_block_%s.crash", blockId);
                Path outputFile = outputDir.resolve(filename);
                Files.write(outputFile, data);
                count = 1;
            }
        }

        return count;
    }

    /**
     * 导出指定目录下的所有压缩crash文件
     *
     * @param blockDir  包含压缩文件的目录
     * @param outputDir 输出目录
     * @return 导出的文件总数
     */
    public static int exportAllCrashes(Path blockDir, Path outputDir) throws IOException {
        if (!Files.isDirectory(blockDir)) {
            throw new IOException("Block directory not found: " + blockDir);
        }

        int totalCount = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(blockDir, "block_*.gz")) {
            for (Path blockFile : stream) {
                totalCount += exportCrashes(blockFile, outputDir);
            }
        }
        return totalCount;
    }

    private static class CrashInfo {
        final int crashId;
        final int exitCode;
        final int length;

        CrashInfo(int crashId, int exitCode, int length) {
            this.crashId = crashId;
            this.exitCode = exitCode;
            this.length = length;
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: CrashExporter <block_directory> <output_directory>");
            System.exit(1);
        }

        try {
            Path blockDir = Paths.get(args[0]);
            Path outputDir = Paths.get(args[1]);
            int count = exportAllCrashes(blockDir, outputDir);
            System.out.println("Successfully exported " + count + " crash files to " + outputDir);
        } catch (Exception e) {
            System.err.println("Error exporting crashes: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
