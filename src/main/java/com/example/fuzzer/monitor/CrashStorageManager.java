package com.example.fuzzer.monitor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final int BLOCK_SIZE = 1000; // 每个文件存储的crash数量
    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB写入缓冲区
    private static final int QUEUE_SIZE = 10000;

    private final Path storageDir;
    private final BlockingQueue<CrashEntry> writeQueue;
    private final Set<String> crashHashes;
    private final Map<Integer, List<CrashMetadata>> crashIndex;
    private volatile boolean isRunning;
    private final Thread writerThread;
    private int currentBlockId;

    private static class CrashEntry {
        final byte[] input;
        final int exitCode;
        final int crashId;

        CrashEntry(byte[] input, int exitCode, int crashId) {
            this.input = input;
            this.exitCode = exitCode;
            this.crashId = crashId;
        }
    }

    private static class CrashMetadata {
        final int crashId;
        final int exitCode;
        final long offset;
        final int length;

        CrashMetadata(int crashId, int exitCode, long offset, int length) {
            this.crashId = crashId;
            this.exitCode = exitCode;
            this.offset = offset;
            this.length = length;
        }
    }

    public CrashStorageManager(Path storageDir) throws IOException {
        this.storageDir = storageDir;
        this.writeQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
        this.crashHashes = Collections.synchronizedSet(new HashSet<>());
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
                                    Integer.parseInt(parts[3])   // length
                            ));
                        }
                    }

                    if (!metadata.isEmpty()) {
                        crashIndex.put(blockId, metadata);
                        // 恢复已存在的crash哈希值
                        for (CrashMetadata meta : metadata) {
                            byte[] crashData = retrieveCrash(meta.crashId);
                            if (crashData != null) {
                                crashHashes.add(calculateHash(crashData));
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean storeCrash(byte[] input, int exitCode, int crashId) {
        // 计算输入的哈希值用于去重
        String hash = calculateHash(input);
        if (!crashHashes.add(hash)) {
            return false; // 重复的crash
        }

        try {
            writeQueue.put(new CrashEntry(input, exitCode, crashId));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
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
                metadata.add(new CrashMetadata(entry.crashId, entry.exitCode, offset, entry.input.length));
                offset += entry.input.length;
            }

            crashIndex.put(currentBlockId, metadata);

            // 保存元数据到单独的文件
            List<String> metadataLines = new ArrayList<>();
            for (CrashMetadata meta : metadata) {
                metadataLines.add(String.format("%d,%d,%d,%d",
                        meta.crashId, meta.exitCode, meta.offset, meta.length));
            }
            Files.write(metadataPath, metadataLines);

            currentBlockId++;

        } catch (IOException e) {
            // 在实际应用中，这里应该有更好的错误处理机制
            e.printStackTrace();
        }
    }

    private String calculateHash(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
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
            writerThread.join(5000); // 等待最多5秒让写入线程完成
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
