package com.example.fuzzer.mutation;

import java.util.Random;

public class AFLMutator implements Mutator {
    // 预定义的有趣值
    private static final byte[] INTERESTING_8 = {
            -128, -1, 0, 1, 16, 32, 64, 100, 127
    };
    private static final short[] INTERESTING_16 = {
            -32768, -129, 128, 255, 256, 512, 1000, 1024, 4096, 32767
    };
    private static final int[] INTERESTING_32 = {
            -2147483648, -100663046, -32769, 32768, 65535, 65536, 100663045, 2147483647
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
        int pos = random.nextInt(input.length);
        int bit = random.nextInt(8);
        input[pos] ^= (1 << bit);
        return input;
    }

    // 算术变异
    private byte[] arithmetic(byte[] input) {
        if (input.length < 1) return input;

        int pos = random.nextInt(input.length);
        byte value = input[pos];

        switch (random.nextInt(4)) {
            case 0: // +1
                input[pos] = (byte) (value + 1);
                break;
            case 1: // -1
                input[pos] = (byte) (value - 1);
                break;
            case 2: // *2
                input[pos] = (byte) (value * 2);
                break;
            case 3: // /2
                input[pos] = (byte) (value / 2);
                break;
        }
        return input;
    }

    // 插入有趣的值
    private byte[] insertInterestingValues(byte[] input) {
        if (input.length < 1) return input;

        int pos = random.nextInt(input.length);
        int type = random.nextInt(3);

        switch (type) {
            case 0: // 8-bit
                input[pos] = INTERESTING_8[random.nextInt(INTERESTING_8.length)];
                break;
            case 1: // 16-bit
                if (pos + 1 < input.length) {
                    short value = INTERESTING_16[random.nextInt(INTERESTING_16.length)];
                    input[pos] = (byte) (value >> 8);
                    input[pos + 1] = (byte) value;
                }
                break;
            case 2: // 32-bit
                if (pos + 3 < input.length) {
                    int value = INTERESTING_32[random.nextInt(INTERESTING_32.length)];
                    input[pos] = (byte) (value >> 24);
                    input[pos + 1] = (byte) (value >> 16);
                    input[pos + 2] = (byte) (value >> 8);
                    input[pos + 3] = (byte) value;
                }
                break;
        }
        return input;
    }

    // havoc变异（多重随机变异）
    private byte[] havoc(byte[] input) {
        int numMutations = 1 + random.nextInt(5); // 1-5次随机变异

        for (int i = 0; i < numMutations; i++) {
            switch (random.nextInt(4)) {
                case 0:
                    input = bitFlip(input);
                    break;
                case 1:
                    input = arithmetic(input);
                    break;
                case 2:
                    input = insertInterestingValues(input);
                    break;
                case 3:
                    // 随机字节替换
                    if (input.length > 0) {
                        int pos = random.nextInt(input.length);
                        input[pos] = (byte) random.nextInt(256);
                    }
                    break;
            }
        }
        return input;
    }

    // splice变异（交叉合并）
    private byte[] splice(byte[] input1, byte[] input2) {
        if (input1.length < 2 || input2.length < 2) {
            return input1;
        }

        // 选择切割点
        int cut1 = random.nextInt(input1.length);
        int cut2 = random.nextInt(input2.length);

        // 创建新的数组并合并
        byte[] result = new byte[cut1 + (input2.length - cut2)];
        System.arraycopy(input1, 0, result, 0, cut1);
        System.arraycopy(input2, cut2, result, cut1, input2.length - cut2);

        return result;
    }
}