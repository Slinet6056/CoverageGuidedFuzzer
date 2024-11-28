package com.example.fuzzer.mutation;

import java.util.Random;

public class CxxfiltMutator implements Mutator {
    // C++常见类型
    private static final String[] CPP_TYPES = {
            "v",    // void
            "b",    // bool
            "c",    // char
            "h",    // unsigned char
            "a",    // signed char
            "s",    // short
            "t",    // unsigned short
            "i",    // int
            "j",    // unsigned int
            "l",    // long
            "m",    // unsigned long
            "x",    // long long
            "y",    // unsigned long long
            "f",    // float
            "d",    // double
            "e",    // long double
            "Ds",   // string
            "PKc"   // const char*
    };
    // C++常见操作符
    private static final String[] CPP_OPERATORS = {
            "pl",   // +
            "mi",   // -
            "ml",   // *
            "dv",   // /
            "rm",   // %
            "an",   // &
            "or",   // |
            "eo",   // ^
            "aS",   // =
            "pL",   // +=
            "mI",   // -=
            "mL",   // *=
            "dV",   // /=
            "rM",   // %=
            "aN",   // &=
            "oR",   // |=
            "eO"    // ^=
    };
    // 常见的C++命名空间和类名
    private static final String[] CPP_NAMESPACES = {
            "std", "boost", "google", "apache", "eastl",
            "core", "util", "detail", "internal", "impl"
    };
    private final Random random = new Random();
    private MutationStrategy currentStrategy = MutationStrategy.NONE;

    @Override
    public byte[] mutate(byte[] input) {
        if (input == null || input.length == 0) {
            return generateNewSeed().getBytes();
        }

        String current = new String(input);
        String mutated;

        int strategy = random.nextInt(5);
        switch (strategy) {
            case 0:
                currentStrategy = MutationStrategy.INTERESTING;
                mutated = mutateExistingSymbol(current);
                break;
            case 1:
                currentStrategy = MutationStrategy.INTERESTING;
                mutated = addNamespace(current);
                break;
            case 2:
                currentStrategy = MutationStrategy.INTERESTING;
                mutated = addTemplateParameters(current);
                break;
            case 3:
                currentStrategy = MutationStrategy.INTERESTING;
                mutated = addFunctionParameters(current);
                break;
            default:
                currentStrategy = MutationStrategy.HAVOC;
                mutated = generateNewSeed();
        }

        return mutated.getBytes();
    }

    @Override
    public MutatorType getType() {
        return MutatorType.CXXFILT;
    }

    @Override
    public MutationStrategy getCurrentStrategy() {
        return currentStrategy;
    }

    private String mutateExistingSymbol(String input) {
        if (!input.startsWith("_Z")) {
            return "_Z" + input;
        }

        StringBuilder sb = new StringBuilder(input);
        int pos = random.nextInt(input.length());

        if (random.nextBoolean()) {
            // 插入一个类型
            sb.insert(pos, CPP_TYPES[random.nextInt(CPP_TYPES.length)]);
        } else {
            // 插入一个操作符
            sb.insert(pos, CPP_OPERATORS[random.nextInt(CPP_OPERATORS.length)]);
        }

        return sb.toString();
    }

    private String addNamespace(String input) {
        if (!input.startsWith("_Z")) {
            input = "_Z" + input;
        }

        String namespace = CPP_NAMESPACES[random.nextInt(CPP_NAMESPACES.length)];
        return input + "N" + namespace.length() + namespace + "E";
    }

    private String addTemplateParameters(String input) {
        if (!input.startsWith("_Z")) {
            input = "_Z" + input;
        }

        StringBuilder sb = new StringBuilder(input);
        sb.append("I");

        // 添加1-3个模板参数
        int paramCount = 1 + random.nextInt(3);
        for (int i = 0; i < paramCount; i++) {
            sb.append(CPP_TYPES[random.nextInt(CPP_TYPES.length)]);
        }

        sb.append("E");
        return sb.toString();
    }

    private String addFunctionParameters(String input) {
        if (!input.startsWith("_Z")) {
            input = "_Z" + input;
        }

        StringBuilder sb = new StringBuilder(input);

        // 添加1-4个函数参数
        int paramCount = 1 + random.nextInt(4);
        for (int i = 0; i < paramCount; i++) {
            sb.append(CPP_TYPES[random.nextInt(CPP_TYPES.length)]);
        }

        return sb.toString();
    }

    private String generateNewSeed() {
        StringBuilder sb = new StringBuilder("_Z");

        // 添加函数名长度和名称
        String funcName = CPP_NAMESPACES[random.nextInt(CPP_NAMESPACES.length)];
        sb.append(funcName.length()).append(funcName);

        // 随机添加命名空间
        if (random.nextBoolean()) {
            sb.append("N");
            String namespace = CPP_NAMESPACES[random.nextInt(CPP_NAMESPACES.length)];
            sb.append(namespace.length()).append(namespace);
            sb.append("E");
        }

        // 随机添加参数
        int paramCount = random.nextInt(4);
        for (int i = 0; i < paramCount; i++) {
            sb.append(CPP_TYPES[random.nextInt(CPP_TYPES.length)]);
        }

        return sb.toString();
    }
}
