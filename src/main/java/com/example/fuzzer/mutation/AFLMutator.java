package com.example.fuzzer.mutation;

import java.util.Random;

public class AFLMutator implements Mutator {
    // 预定义的有趣值
    private static final byte[] INTERESTING_8 = {
        Byte.MIN_VALUE,           // -128
        (byte) -100,
        (byte) -1,
        (byte) 0,
        (byte) 1,
        (byte) 2,
        (byte) 3,
        (byte) 4,
        (byte) 7,
        (byte) 8,
        (byte) 15,
        (byte) 16,
        (byte) 31,
        (byte) 32,
        (byte) 63,
        (byte) 64,
        (byte) 100,
        Byte.MAX_VALUE           // 127
    };

    private static final short[] INTERESTING_16 = {
        Short.MIN_VALUE,         // -32768
        (short) -21555,
        (short) -666,
        (short) -100,
        (short) -1,
        (short) 0,
        (short) 1,
        (short) 7,
        (short) 8,
        (short) 16,
        (short) 32,
        (short) 64,
        (short) 100,
        (short) 128,
        (short) 255,
        (short) 256,
        (short) 512,
        (short) 666,
        (short) 1000,
        (short) 1024,
        (short) 2048,
        (short) 4096,
        (short) 8192,
        (short) 16384,
        (short) 32767,          // Short.MAX_VALUE
        (short) 0xFFFF          // 65535 (无符号最大值)
    };

    private static final int[] INTERESTING_32 = {
        Integer.MIN_VALUE,      // -2147483648
        -100663046,
        -10000000,
        -666666,
        -32769,
        -32768,
        -1000,
        -666,
        -100,
        -1,
        0,
        1,
        7,
        8,
        16,
        32,
        64,
        100,
        666,
        1000,
        1024,
        4096,
        32767,
        32768,
        65535,
        65536,
        100663045,
        Integer.MAX_VALUE,      // 2147483647
        0xFFFFFFFF              // 4294967295 (无符号最大值)
    };

    // 文件格式相关的魔数
    private static final int[] MAGIC_NUMBERS = {
        0xFFD8FFE0,    // JPEG SOI marker
        0x89504E47,    // PNG signature
        0x47494638,    // GIF signature
        0x25504446,    // PDF signature
        0x7F454C46,    // ELF signature
        0x504B0304,    // ZIP signature
        0x52494646,    // RIFF signature
        0xFFFB,        // MP3 signature
        0x424D        // BMP signature
    };

    private final Random random = new Random();
    private MutationStrategy currentStrategy = MutationStrategy.NONE;
    private int mutationPower = 1;

    @Override
    public byte[] mutate(byte[] input) {
        // 随机选择变异策略
        int strategy = random.nextInt(5);
        byte[] mutated = input.clone();

        switch (strategy) {
            case 0:
                currentStrategy = MutationStrategy.BITFLIP;
                return bitFlip(mutated);
            case 1:
                currentStrategy = MutationStrategy.ARITHMETIC;
                return arithmetic(mutated);
            case 2:
                currentStrategy = MutationStrategy.INTERESTING;
                return insertInterestingValues(mutated);
            case 3:
                currentStrategy = MutationStrategy.HAVOC;
                return havoc(mutated);
            case 4:
                currentStrategy = MutationStrategy.SPLICE;
                return splice(mutated, input);
            default:
                return mutated;
        }
    }

    @Override
    public MutatorType getType() {
        return MutatorType.AFL;
    }

    @Override
    public MutationStrategy getCurrentStrategy() {
        return currentStrategy;
    }

    @Override
    public void setMutationPower(int power) {
        this.mutationPower = Math.max(1, power);
    }

    // 位翻转变异
    private byte[] bitFlip(byte[] input) {
        if (input.length == 0) {
            return input;
        }

        // 根据变异强度选择翻转模式
        int mode = random.nextInt(6); // 0:1-bit, 1:2-bit, 2:4-bit, 3:8-bit, 4:16-bit, 5:32-bit
        byte[] result = input.clone();

        switch (mode) {
            case 0: // 1-bit flip
                flipOneBit(result);
                break;
            case 1: // 2-bit flip
                flipTwoBits(result);
                break;
            case 2: // 4-bit flip
                flipFourBits(result);
                break;
            case 3: // 8-bit flip (byte flip)
                flipByte(result);
                break;
            case 4: // 16-bit flip
                flipWord(result);
                break;
            case 5: // 32-bit flip
                flipDWord(result);
                break;
        }

        return result;
    }

    // 翻转单个位
    private void flipOneBit(byte[] data) {
        int pos = random.nextInt(data.length);
        int bit = random.nextInt(8);
        data[pos] ^= (1 << bit);
    }

    // 翻转相邻两位
    private void flipTwoBits(byte[] data) {
        if (data.length == 0) return;
        int pos = random.nextInt(data.length);
        int startBit = random.nextInt(7); // 确保有空间翻转两位
        data[pos] ^= (3 << startBit); // 3 = 0b11，同时翻转两位
    }

    // 翻转相邻四位
    private void flipFourBits(byte[] data) {
        if (data.length == 0) return;
        int pos = random.nextInt(data.length);
        int startBit = random.nextInt(5); // 确保有空间翻转四位
        data[pos] ^= (15 << startBit); // 15 = 0b1111，同时翻转四位
    }

    // 翻转整个字节
    private void flipByte(byte[] data) {
        if (data.length == 0) return;
        int pos = random.nextInt(data.length);
        data[pos] = (byte) ~data[pos];
    }

    // 翻转两个字节（16位）
    private void flipWord(byte[] data) {
        if (data.length < 2) return;
        int pos = random.nextInt(data.length - 1); // 确保有空间翻转两个字节
        
        // 读取16位值
        int value = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        value = ~value; // 翻转所有位
        
        // 写回两个字节
        data[pos] = (byte) (value >> 8);
        data[pos + 1] = (byte) value;
    }

    // 翻转四个字节（32位）
    private void flipDWord(byte[] data) {
        if (data.length < 4) return;
        int pos = random.nextInt(data.length - 3); // 确保有空间翻转四个字节
        
        // 读取32位值
        int value = ((data[pos] & 0xFF) << 24) |
                   ((data[pos + 1] & 0xFF) << 16) |
                   ((data[pos + 2] & 0xFF) << 8) |
                   (data[pos + 3] & 0xFF);
        value = ~value; // 翻转所有位
        
        // 写回四个字节
        data[pos] = (byte) (value >> 24);
        data[pos + 1] = (byte) (value >> 16);
        data[pos + 2] = (byte) (value >> 8);
        data[pos + 3] = (byte) value;
    }

    // 算术变异
    private byte[] arithmetic(byte[] input) {
        if (input.length < 1) return input;

        byte[] result = input.clone();
        // 选择变异大小：8位、16位或32位
        int mode = random.nextInt(3);
        
        switch (mode) {
            case 0: // 8-bit
                arithmetic8(result);
                break;
            case 1: // 16-bit
                arithmetic16(result);
                break;
            case 2: // 32-bit
                arithmetic32(result);
                break;
        }
        
        return result;
    }

    // 8位算术变异
    private void arithmetic8(byte[] data) {
        if (data.length == 0) {
            return;
        }

        int pos = random.nextInt(data.length);
        int val = data[pos] & 0xFF;
        
        switch (random.nextInt(8)) {
            case 0: val += 1; break;
            case 1: val -= 1; break;
            case 2: val *= 2; break;
            case 3: val /= 2; break;
            case 4: val += random.nextInt(256); break;
            case 5: val -= random.nextInt(256); break;
            case 6: val <<= (1 + random.nextInt(7)); break;
            case 7: val >>= (1 + random.nextInt(7)); break;
        }
        
        data[pos] = (byte)val;
    }

    // 16位算术变异
    private void arithmetic16(byte[] data) {
        if (data.length < 2) {
            return;
        }

        int pos = random.nextInt(data.length - 1);
        int val = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        
        switch (random.nextInt(8)) {
            case 0: val += 1; break;
            case 1: val -= 1; break;
            case 2: val *= 2; break;
            case 3: val /= 2; break;
            case 4: val += random.nextInt(65536); break;
            case 5: val -= random.nextInt(65536); break;
            case 6: val <<= (1 + random.nextInt(15)); break;
            case 7: val >>= (1 + random.nextInt(15)); break;
        }
        
        data[pos] = (byte)(val >> 8);
        data[pos + 1] = (byte)val;
    }

    // 32位算术变异
    private void arithmetic32(byte[] data) {
        if (data.length < 4) {
            return;
        }

        int pos = random.nextInt(data.length - 3);
        int val = ((data[pos] & 0xFF) << 24) | 
                 ((data[pos + 1] & 0xFF) << 16) |
                 ((data[pos + 2] & 0xFF) << 8) |
                 (data[pos + 3] & 0xFF);
        
        switch (random.nextInt(8)) {
            case 0: val += 1; break;
            case 1: val -= 1; break;
            case 2: val *= 2; break;
            case 3: val /= 2; break;
            case 4: val += random.nextInt(); break;
            case 5: val -= random.nextInt(); break;
            case 6: val <<= (1 + random.nextInt(31)); break;
            case 7: val >>= (1 + random.nextInt(31)); break;
        }
        
        data[pos] = (byte)(val >> 24);
        data[pos + 1] = (byte)(val >> 16);
        data[pos + 2] = (byte)(val >> 8);
        data[pos + 3] = (byte)val;
    }

    // 插入有趣的值
    private byte[] insertInterestingValues(byte[] input) {
        if (input.length < 1) return input;
        byte[] result = input.clone();
        
        // 选择变异模式：标准值/魔数/组合
        int mode = random.nextInt(3);
        
        switch (mode) {
            case 0: // 标准有趣值
                insertStandardValue(result);
                break;
            case 1: // 文件格式魔数
                insertMagicNumber(result);
                break;
            case 2: // 组合插入
                if (random.nextBoolean()) {
                    insertStandardValue(result);
                }
                if (random.nextBoolean()) {
                    insertMagicNumber(result);
                }
                break;
        }
        
        return result;
    }

    // 插入标准有趣值
    private void insertStandardValue(byte[] data) {
        if (data.length < 1) return;
        
        // 选择值类型：8位/16位/32位
        int type = random.nextInt(3);
        
        switch (type) {
            case 0: // 8-bit
                if (data.length >= 1) {
                    int pos = random.nextInt(data.length);
                    data[pos] = INTERESTING_8[random.nextInt(INTERESTING_8.length)];
                }
                break;
                
            case 1: // 16-bit
                if (data.length >= 2) {
                    int pos = random.nextInt(data.length - 1);
                    short value = INTERESTING_16[random.nextInt(INTERESTING_16.length)];
                    
                    // 随机选择字节序
                    if (random.nextBoolean()) {
                        // 大端序
                        data[pos] = (byte) (value >> 8);
                        data[pos + 1] = (byte) value;
                    } else {
                        // 小端序
                        data[pos] = (byte) value;
                        data[pos + 1] = (byte) (value >> 8);
                    }
                }
                break;
                
            case 2: // 32-bit
                if (data.length >= 4) {
                    int pos = random.nextInt(data.length - 3);
                    int value = INTERESTING_32[random.nextInt(INTERESTING_32.length)];
                    
                    // 随机选择字节序
                    if (random.nextBoolean()) {
                        // 大端序
                        data[pos] = (byte) (value >> 24);
                        data[pos + 1] = (byte) (value >> 16);
                        data[pos + 2] = (byte) (value >> 8);
                        data[pos + 3] = (byte) value;
                    } else {
                        // 小端序
                        data[pos] = (byte) value;
                        data[pos + 1] = (byte) (value >> 8);
                        data[pos + 2] = (byte) (value >> 16);
                        data[pos + 3] = (byte) (value >> 24);
                    }
                }
                break;
        }
    }

    // 插入魔数
    private void insertMagicNumber(byte[] input) {
        if (input.length < 4) {
            return;
        }

        // 常见文件格式魔数
        byte[][] magicNumbers = {
            // JPEG
            {(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0},
            {(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE1},
            // PNG
            {(byte)0x89, (byte)0x50, (byte)0x4E, (byte)0x47},
            // GIF
            {(byte)0x47, (byte)0x49, (byte)0x46, (byte)0x38},
            // PDF
            {(byte)0x25, (byte)0x50, (byte)0x44, (byte)0x46},
            // ZIP
            {(byte)0x50, (byte)0x4B, (byte)0x03, (byte)0x04},
            // ELF
            {(byte)0x7F, (byte)0x45, (byte)0x4C, (byte)0x46}
        };

        // 随机选择一个魔数
        byte[] magic = magicNumbers[random.nextInt(magicNumbers.length)];
        
        // 随机选择插入位置，优先考虑文件开头和文件中部
        int pos;
        if (random.nextInt(4) == 0) { // 25%概率插在开头
            pos = 0;
        } else if (random.nextInt(3) == 0) { // 33%概率插在中间
            pos = input.length / 2;
        } else { // 其他情况随机位置
            pos = random.nextInt(input.length - magic.length + 1);
        }

        // 复制魔数
        System.arraycopy(magic, 0, input, pos, magic.length);
    }

    // havoc变异（多重随机变异）
    private byte[] havoc(byte[] input) {
        if (input.length == 0) {
            return input;
        }
        
        byte[] result = input.clone();
        // 根据变异强度决定变异次数，范围1-8次
        int numMutations = 1 + random.nextInt(Math.min(8, mutationPower));

        for (int i = 0; i < numMutations; i++) {
            switch (random.nextInt(12)) {  // 增加到12种变异操作
                case 0:  // 位翻转
                    flipOneBit(result);
                    break;
                    
                case 1:  // 字节翻转
                    flipByte(result);
                    break;
                    
                case 2:  // 算术变异
                    arithmetic8(result);
                    break;
                    
                case 3:  // 插入有趣值
                    insertStandardValue(result);
                    break;
                    
                case 4:  // 字节随机化
                    if (result.length > 0) {
                        int pos = random.nextInt(result.length);
                        result[pos] = (byte) random.nextInt(256);
                    }
                    break;
                    
                case 5:  // 字节序交换
                    if (result.length >= 4) {
                        int pos = random.nextInt(result.length - 3);
                        byte temp = result[pos];
                        result[pos] = result[pos + 3];
                        result[pos + 3] = temp;
                        temp = result[pos + 1];
                        result[pos + 1] = result[pos + 2];
                        result[pos + 2] = temp;
                    }
                    break;
                    
                case 6:  // 块复制
                    if (result.length >= 4) {
                        int srcPos = random.nextInt(result.length - 2);
                        int dstPos = random.nextInt(result.length - 2);
                        int len = 1 + random.nextInt(Math.min(4, result.length - Math.max(srcPos, dstPos)));
                        System.arraycopy(result, srcPos, result, dstPos, len);
                    }
                    break;
                    
                case 7:  // 块交换
                    if (result.length >= 4) {
                        int pos1 = random.nextInt(result.length - 2);
                        int pos2 = random.nextInt(result.length - 2);
                        int len = 1 + random.nextInt(Math.min(4, 
                            Math.min(result.length - pos1, result.length - pos2)));
                        
                        byte[] temp = new byte[len];
                        System.arraycopy(result, pos1, temp, 0, len);
                        System.arraycopy(result, pos2, result, pos1, len);
                        System.arraycopy(temp, 0, result, pos2, len);
                    }
                    break;
                    
                case 8:  // 重复字节
                    if (result.length >= 2) {
                        int pos = random.nextInt(result.length - 1);
                        result[pos + 1] = result[pos];
                    }
                    break;
                    
                case 9:  // 插入魔数
                    insertMagicNumber(result);
                    break;
                    
                case 10: // 算术变异（16位）
                    arithmetic16(result);
                    break;
                    
                case 11: // 算术变异（32位）
                    arithmetic32(result);
                    break;
            }
        }
        
        return result;
    }

    // splice变异（交叉合并）
    private byte[] splice(byte[] input1, byte[] input2) {
        // 确保输入有效且足够长
        if (input1.length < 4 || input2.length < 4) {
            return input1;
        }

        // 创建输入1的副本
        byte[] result = input1.clone();

        // 计算切分点
        int cutPoint1, cutPoint2;
        
        // 优先选择"有趣"的切分点
        if (random.nextInt(4) == 0) { // 25%的概率选择文件头部
            cutPoint1 = 0;
            cutPoint2 = 0;
        } else if (random.nextInt(3) == 0) { // 33%的概率选择文件中部
            cutPoint1 = result.length / 2;
            cutPoint2 = input2.length / 2;
        } else { // 其他情况随机选择，但避免太小的片段
            int minSize = Math.min(32, Math.min(result.length, input2.length) / 4);
            cutPoint1 = minSize + random.nextInt(result.length - minSize);
            cutPoint2 = minSize + random.nextInt(input2.length - minSize);
        }

        // 计算要复制的长度
        int copyLen = Math.min(result.length - cutPoint1, input2.length - cutPoint2);
        copyLen = Math.min(copyLen, 1024); // 限制最大复制长度

        // 执行splice操作
        System.arraycopy(input2, cutPoint2, result, cutPoint1, copyLen);

        // 有一定概率对拼接结果进行havoc变异
        if (random.nextInt(2) == 0) { // 50%的概率
            result = havoc(result);
        }

        return result;
    }

    // 辅助方法：寻找特征字节序列
    private int findPattern(byte[] data, int startPos, byte[] pattern) {
        for (int i = startPos; i <= data.length - pattern.length; i++) {
            boolean found = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }

    // 辅助方法：检查是否是文件头
    private boolean isFileHeader(byte[] data, int pos) {
        if (pos > 8) return false; // 文件头通常在开始的几个字节

        // 检查常见的文件头模式
        byte[][] headers = {
            {(byte)0xFF, (byte)0xD8}, // JPEG
            {(byte)0x89, (byte)0x50}, // PNG
            {(byte)0x47, (byte)0x49}, // GIF
            {(byte)0x25, (byte)0x50}, // PDF
            {(byte)0x50, (byte)0x4B}, // ZIP
            {(byte)0x7F, (byte)0x45}  // ELF
        };

        for (byte[] header : headers) {
            if (pos + header.length <= data.length) {
                boolean match = true;
                for (int i = 0; i < header.length; i++) {
                    if (data[pos + i] != header[i]) {
                        match = false;
                        break;
                    }
                }
                if (match) return true;
            }
        }
        return false;
    }
}