package com.example.fuzzer.monitor;

import org.apache.commons.text.similarity.CosineDistance;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 高效的crash输入存储管理器
 * - 使用分块存储减少inode使用
 * - 使用压缩减少存储空间
 * - 使用去重避免重复存储
 * - 使用异步写入提高性能
 */
public class CrashStorageManager implements AutoCloseable {
    private static final int BLOCK_SIZE = 100; // 每个文件存储的crash数量
    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB写入缓冲区
    private static final int QUEUE_SIZE = 10000;
    private static final double ERROR_SIMILARITY_THRESHOLD = 0.8;

    private final Path storageDir;
    private final BlockingQueue<CrashEntry> writeQueue;
    private final Map<Integer, List<CrashMetadata>> crashIndex;
    private final List<CrashEntry> pendingCrashes = Collections.synchronizedList(new ArrayList<>());
    private final Thread writerThread;
    private volatile boolean isRunning;
    private int currentBlockId;

    public CrashStorageManager(Path storageDir) throws IOException {
        this.storageDir = storageDir;
        this.writeQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
        this.crashIndex = new ConcurrentHashMap<>();
        this.isRunning = true;
        this.currentBlockId = 0;

        // 确保存储目录存在
        Files.createDirectories(storageDir);

        // 启动异步写入线程
        this.writerThread = new Thread(this::processWriteQueue);
        this.writerThread.setDaemon(true);
        this.writerThread.start();

        // 加载现有的crash索引
        loadExistingIndex();
    }

    private void loadExistingIndex() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDir, "block_*.gz")) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                int blockId = Integer.parseInt(fileName.substring(6, fileName.length() - 3));
                currentBlockId = Math.max(currentBlockId, blockId + 1);

                // 加载对应的元数据文件
                Path metadataPath = storageDir.resolve(String.format("block_%06d.meta", blockId));
                if (Files.exists(metadataPath)) {
                    List<String> lines = Files.readAllLines(metadataPath);
                    List<CrashMetadata> metadata = new ArrayList<>();

                    for (String line : lines) {
                        String[] parts = line.split(",");
                        if (parts.length >= 4) {
                            metadata.add(new CrashMetadata(
                                    Integer.parseInt(parts[0]),  // crashId
                                    Integer.parseInt(parts[1]),  // exitCode
                                    Long.parseLong(parts[2]),   // offset
                                    Integer.parseInt(parts[3]),  // length
                                    parts.length > 4 ? parts[4] : "" // errorMessage
                            ));
                        }
                    }

                    if (!metadata.isEmpty()) {
                        crashIndex.put(blockId, metadata);
                    }
                }
            }
        }
    }

    private boolean isErrorMessageSimilar(String newError, String existingError) {
        if (newError == null || existingError == null) {
            return false;
        }

        // 预处理错误信息
        newError = preprocessErrorMessage(newError);
        existingError = preprocessErrorMessage(existingError);

        // 如果预处理后完全相同，直接返回true
        if (newError.equals(existingError)) {
            return true;
        }

        // 使用余弦距离计算相似度
        CosineDistance cosineDistance = new CosineDistance();
        double distance = cosineDistance.apply(newError, existingError);
        double similarity = 1.0 - distance;

        // 如果相似度大于ERROR_SIMILARITY_THRESHOLD，认为是相似的错误
        return similarity > ERROR_SIMILARITY_THRESHOLD;
    }

    private String preprocessErrorMessage(String error) {
        // 1. 转换为小写
        error = error.toLowerCase();

        // 2. 移除时间戳、内存地址、数字等变化的部分
        error = error.replaceAll("0x[0-9a-f]+", "ADDR")  // 内存地址
                .replaceAll("\\d{2}:\\d{2}:\\d{2}", "TIME")  // 时间戳
                .replaceAll("\\d+", "NUM")  // 数字
                .replaceAll("\\s+", " ")  // 多余的空白字符
                .trim();

        // 3. 提取关键词
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with"
        ));

        // 分词并移除停用词
        String[] words = error.split("\\s+");
        StringBuilder processed = new StringBuilder();
        for (String word : words) {
            if (!stopWords.contains(word)) {
                processed.append(word).append(" ");
            }
        }

        return processed.toString().trim();
    }

    public boolean storeCrash(byte[] input, int exitCode, int crashId, String errorMessage) {
        // 检查已存储的crash
        for (List<CrashMetadata> metadataList : crashIndex.values()) {
            for (CrashMetadata metadata : metadataList) {
                if (isErrorMessageSimilar(errorMessage, metadata.errorMessage)) {
                    return false;
                }
            }
        }

        // 检查待写入的crash
        synchronized (pendingCrashes) {
            for (CrashEntry entry : pendingCrashes) {
                if (isErrorMessageSimilar(errorMessage, entry.errorMessage)) {
                    return false;
                }
            }
            CrashEntry newEntry = new CrashEntry(input, exitCode, crashId, errorMessage);
            pendingCrashes.add(newEntry);
            try {
                writeQueue.put(newEntry);
                return true;
            } catch (InterruptedException e) {
                pendingCrashes.remove(newEntry);
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private void processWriteQueue() {
        List<CrashEntry> batch = new ArrayList<>(BLOCK_SIZE);

        while (isRunning || !writeQueue.isEmpty()) {
            try {
                CrashEntry entry = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    batch.add(entry);
                }

                if (batch.size() >= BLOCK_SIZE || (!isRunning && !batch.isEmpty())) {
                    writeBlock(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 确保最后的batch被写入
        if (!batch.isEmpty()) {
            writeBlock(batch);
        }
    }

    private void writeBlock(List<CrashEntry> entries) {
        if (entries.isEmpty()) return;

        String blockId = String.format("%06d", currentBlockId);
        Path blockPath = storageDir.resolve("block_" + blockId + ".gz");
        Path metadataPath = storageDir.resolve("block_" + blockId + ".meta");
        List<CrashMetadata> metadata = new ArrayList<>();

        try (GZIPOutputStream gzos = new GZIPOutputStream(
                new BufferedOutputStream(Files.newOutputStream(blockPath), BUFFER_SIZE))) {

            long offset = 0;
            for (CrashEntry entry : entries) {
                gzos.write(entry.input);
                metadata.add(new CrashMetadata(entry.crashId, entry.exitCode, offset, entry.input.length, entry.errorMessage));
                offset += entry.input.length;
            }

            crashIndex.put(currentBlockId, metadata);

            // 保存元数据到单独的文件
            List<String> metadataLines = new ArrayList<>();
            for (CrashMetadata meta : metadata) {
                metadataLines.add(String.format("%d,%d,%d,%d,%s",
                        meta.crashId, meta.exitCode, meta.offset, meta.length, meta.errorMessage));
            }
            Files.write(metadataPath, metadataLines);

            currentBlockId++;

            // 写入成功后，从pendingCrashes中移除这些entries
            synchronized (pendingCrashes) {
                pendingCrashes.removeAll(entries);
            }

        } catch (IOException e) {
            // 写入失败时记录错误并尝试保存到备用位置
            e.printStackTrace();
            try {
                Path emergencyPath = storageDir.resolve("emergency_" + blockId + ".gz");
                Files.write(emergencyPath, entries.get(0).input);
                for (int i = 1; i < entries.size(); i++) {
                    Files.write(emergencyPath, entries.get(i).input, java.nio.file.StandardOpenOption.APPEND);
                }
            } catch (IOException e2) {
                e2.printStackTrace();
            }
        }
    }

    /**
     * 手动保存所有pending的crash
     * 这个方法可以在任何时候调用，不会影响正常的写入流程
     */
    public void flushPendingCrashes() {
        synchronized (pendingCrashes) {
            if (!pendingCrashes.isEmpty()) {
                List<CrashEntry> remainingCrashes = new ArrayList<>(pendingCrashes);
                writeBlock(remainingCrashes);
            }
        }
    }

    public byte[] retrieveCrash(int crashId) throws IOException {
        for (Map.Entry<Integer, List<CrashMetadata>> entry : crashIndex.entrySet()) {
            for (CrashMetadata metadata : entry.getValue()) {
                if (metadata.crashId == crashId) {
                    Path blockPath = storageDir.resolve(String.format("block_%06d.gz", entry.getKey()));
                    try (GZIPInputStream gzis = new GZIPInputStream(
                            new BufferedInputStream(Files.newInputStream(blockPath), BUFFER_SIZE))) {
                        // 跳过之前的数据
                        gzis.skip(metadata.offset);
                        byte[] data = new byte[metadata.length];
                        int read = gzis.read(data);
                        if (read != metadata.length) {
                            throw new IOException("Failed to read complete crash data");
                        }
                        return data;
                    }
                }
            }
        }
        throw new IOException("Crash not found: " + crashId);
    }

    @Override
    public void close() {
        isRunning = false;
        try {
            // 给写入线程更多时间完成
            writerThread.join(30000); // 等待最多30秒

            // 如果还有未写入的crash，强制写入
            synchronized (pendingCrashes) {
                if (!pendingCrashes.isEmpty()) {
                    List<CrashEntry> remainingCrashes = new ArrayList<>(pendingCrashes);
                    writeBlock(remainingCrashes);
                }
            }
        } catch (InterruptedException e) {
            // 如果在等待过程中被中断，仍然尝试保存剩余的crash
            synchronized (pendingCrashes) {
                if (!pendingCrashes.isEmpty()) {
                    List<CrashEntry> remainingCrashes = new ArrayList<>(pendingCrashes);
                    writeBlock(remainingCrashes);
                }
            }
            Thread.currentThread().interrupt();
        }
    }

    private static class CrashEntry {
        final byte[] input;
        final int exitCode;
        final int crashId;
        final String errorMessage;

        CrashEntry(byte[] input, int exitCode, int crashId, String errorMessage) {
            this.input = input;
            this.exitCode = exitCode;
            this.crashId = crashId;
            this.errorMessage = errorMessage;
        }
    }

    private static class CrashMetadata {
        final int crashId;
        final int exitCode;
        final long offset;
        final int length;
        final String errorMessage;

        CrashMetadata(int crashId, int exitCode, long offset, int length, String errorMessage) {
            this.crashId = crashId;
            this.exitCode = exitCode;
            this.offset = offset;
            this.length = length;
            this.errorMessage = errorMessage;
        }
    }
}
