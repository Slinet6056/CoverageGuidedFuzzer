package com.example.fuzzer.schedule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// TODO:
// - 优化能量计算算法
// - 考虑种子文件的复杂度
// - 添加种子最小化功能
// - 实现种子去重机制
public class AFLSeedGenerator {
    private String aflInputDir;

    public AFLSeedGenerator(String aflInputDir) {
        this.aflInputDir = aflInputDir;
    }

    public List<Seed> generateInitialSeeds() throws IOException {
        List<Seed> seeds = new ArrayList<>();
        Path inputPath = Paths.get(aflInputDir);

        if (!Files.exists(inputPath)) {
            throw new IOException("AFL++ 输入目录不存在: " + aflInputDir);
        }

        // 读取AFL++输入目录中的所有文件
        List<Path> seedFiles = Files.walk(inputPath)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());

        for (Path seedFile : seedFiles) {
            byte[] seedData = Files.readAllBytes(seedFile);
            Seed seed = new Seed(seedData);
            // 根据文件大小设置初始能量
            seed.setEnergy(calculateInitialEnergy(seedData.length));
            seeds.add(seed);
        }

        return seeds;
    }

    private int calculateInitialEnergy(int seedSize) {
        // 根据种子大小计算初始能量值
        // 这里使用一个简单的算法，可以根据需要调整
        return Math.min(10, Math.max(10, seedSize / 2));
    }
}