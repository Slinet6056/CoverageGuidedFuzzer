# 覆盖率引导的模糊测试工具

这是一个基于Java实现的覆盖率引导的模糊测试工具，通过智能的调度策略和变异算法，帮助发现程序中的潜在漏洞和错误。该工具支持多线程并行测试，具有高效的覆盖率收集和智能的种子调度机制。

演示视频：[哔哩哔哩](https://www.bilibili.com/video/BV1cz6tYpELL/)

## 功能特性

- 支持多线程并行模糊测试
- 基于共享内存的高效覆盖率收集
- 智能的能量调度和种子选择策略
- 多种变异算法支持
- Docker容器化部署
- 实时监控和统计分析
- 崩溃用例的自动保存和分类

## 系统架构

项目采用模块化设计，主要包含以下组件：

```
src/main/java/com/example/fuzzer/
├── Fuzzer.java           # 模糊测试器主类
├── execution/            # 测试用例执行模块
├── logging/             # 日志记录模块
├── monitor/            # 监控和结果管理
├── mutation/           # 变异策略实现
├── schedule/           # 调度管理
│   ├── core/          # 核心调度器
│   ├── energy/        # 能量调度策略
│   ├── model/         # 数据模型
│   └── sort/          # 种子排序策略
├── sharedmemory/       # 共享内存实现
└── tools/             # 辅助工具
```

## 部署和使用

项目提供了两种部署方式：标准测试容器和交互式容器。标准测试容器适合批量自动化测试，交互式容器适合开发调试。

### 标准测试容器（slinet6056/fuzzer:latest）

标准测试容器通过环境变量配置运行参数，适合自动化测试场景。

```bash
# 拉取镜像
docker pull slinet6056/fuzzer:latest

# 运行容器
docker run -v /path/to/target:/app/fuzz_target/target \
           -v /path/to/seeds:/app/fuzz_seeds \
           -v /path/to/output:/app/fuzz_output \
           -e MUTATOR_TYPE=AFL \
           -e ENERGY_SCHEDULER_TYPE=COVERAGE_BASED \
           -e SEED_SORTER_TYPE=HEURISTIC \
           -e NUM_THREADS=4 \
           -e DURATION_MINUTES=60 \
           -e TIMEOUT=2 \
           -e PROGRAM_ARGS="@@" \
           slinet6056/fuzzer:latest
```

#### 环境变量配置

标准测试容器中的目标程序和种子目录通过卷挂载指定：
- `/app/fuzz_target/target`：测试目标程序
- `/app/fuzz_seeds`：AFL种子目录
- `/app/fuzz_output`：测试结果输出目录

可配置的环境变量：
- `MUTATOR_TYPE`：变异策略（可选值：AFL, RANDOM），默认为AFL
- `ENERGY_SCHEDULER_TYPE`：能量调度策略（可选值：BASIC, COVERAGE_BASED），默认为COVERAGE_BASED
- `SEED_SORTER_TYPE`：种子排序策略（可选值：FIFO, COVERAGE, EXECUTION_TIME, HEURISTIC），默认为HEURISTIC
- `NUM_THREADS`：并行线程数量，默认为CPU核心数
- `DURATION_MINUTES`：运行时长（分钟）
- `TIMEOUT`：单个测试用例的超时时间，默认为1秒
- `PROGRAM_ARGS`：目标程序的完整命令行，使用@@作为输入文件占位符。例如：'@@'

此外，在`docker/docker-compose.yml`中提供了一个使用标准测试容器进行批量测试的配置示例。

### 交互式容器（slinet6056/fuzzer:interactive）

交互式容器提供了完整的命令行参数控制，适合开发调试场景。

```bash
# 拉取镜像
docker pull slinet6056/fuzzer:interactive

# 运行交互式容器
docker run -it --rm \
           -v /path/to/dir:/dir \
           slinet6056/fuzzer:interactive

# 在容器内运行jar包
java -Djava.library.path=./src/main/native \
     -jar target/coverage-guided-fuzzer-jar-with-dependencies.jar [参数]
```

#### 命令行参数说明

必需参数：
- `-p, --program <path>`：目标程序路径
- `-s, --seed-dir <path>`：AFL种子目录

可选参数：
- `-t, --time <minutes>`：运行时长（分钟），默认为无限制
- `-to, --timeout <seconds>`：单个测试用例的超时时间，默认为1秒
- `-m, --mutator <type>`：变异策略（可选值：AFL, RANDOM），默认为AFL
- `-e, --energy <type>`：能量调度策略（可选值：BASIC, COVERAGE_BASED），默认为COVERAGE_BASED
- `-ss, --seed-sort <type>`：种子排序策略（可选值：FIFO, COVERAGE, EXECUTION_TIME, HEURISTIC），默认为HEURISTIC
- `-j, --threads <number>`：并行线程数量，默认为CPU核心数
- `-c, --target-cmdline <args>`：目标程序的完整命令行，使用@@作为输入文件占位符。例如：'-a @@' 或 '-d @@'

#### 预备文件

容器中已经准备了10个模糊测试目标，位于`/workspace/fuzz-targets`目录下：

```
/workspace/fuzz-targets
├── cxxfilt/
│   ├── cxxfilt, cxxfilt.orig
│   └── seeds/ (cmin, raw, tmin)
├── djpeg/
│   ├── djpeg
│   └── seeds/ (cmin, raw, tmin)
├── lua/
│   ├── lua
│   └── seeds/ (cmin, raw, tmin)
├── mjs/
│   ├── mjs
│   └── seeds/ (cmin, raw, tmin)
├── nm-new/
│   ├── nm-new
│   └── seeds/ (cmin, raw, tmin)
├── objdump/
│   ├── objdump
│   └── seeds/ (cmin, raw, tmin)
├── readelf/
│   ├── readelf
│   └── seeds/ (cmin, raw, tmin)
├── readpng/
│   ├── readpng, readpng.orig
│   └── seeds/ (cmin, raw, tmin)
├── tcpdump/
│   ├── tcpdump
│   └── seeds/ (cmin, raw, tmin)
└── xmllint/
    ├── xmllint
    └── seeds/ (cmin, raw, tmin)
```

每个测试目标都包含可执行文件和对应的种子目录。其中`cxxfilt`和`readpng`使用了特殊的脚本将标准输入转换为文件输入，在测试这两个目标时需要将`-c, --target-cmdline <args>`参数设置为`@@`。

此外，`/workspace/fuzz-results`目录中存放了这10个目标运行24小时后的结果，具体目录结构将在后续章节中详细说明。

## 输出目录结构

模糊测试过程中，所有的输出文件都会被保存在指定的输出目录中。目录结构如下：

### 目录说明

- `queue/`：存放所有测试用例的目录。每个测试用例都会被分配一个唯一的ID，并记录其执行结果和是否产生新的覆盖率。
- `crashes/`：存放导致程序崩溃的输入文件。每个崩溃输入都会被自动分类和保存。
- `hangs/`：存放导致程序超时的输入文件。
- `plots/`：存放覆盖率和性能相关的图表，包括：
  - `performance_chart.png`：执行速度和覆盖率趋势图
  - `anomaly_trend_chart.png`：崩溃和超时趋势图
- `logs/`：存放详细的执行日志，用于调试和分析。

### 文件说明

- `cmdline`：记录运行目标程序时使用的命令行参数。
- `fuzz_bitmap`：存储覆盖率信息的位图数据。
- `fuzzer_setup`：记录模糊测试的配置信息。
- `fuzzer_stats`：记录模糊测试的实时统计信息。
- `plot_data`：存储用于生成图表的原始统计数据。
- `coverage_report.html`：覆盖率报告，可视化展示代码覆盖情况。

所有统计数据和图表都会定期更新（默认每30秒），以反映最新的测试进展。

### 崩溃输入的存储与导出

为了节省存储空间和inode，崩溃输入会被分块压缩存储。每个压缩块由两个文件组成：
- `block_XXXXXX.gz`：压缩的崩溃输入数据
- `block_XXXXXX.meta`：崩溃输入的元数据（ID、退出码、偏移量等）

导出工具的使用方法：
```bash
# 查看帮助
java -jar target/crash-exporter-jar-with-dependencies.jar -h

# 列出崩溃信息（不导出文件）
java -jar target/crash-exporter-jar-with-dependencies.jar -b block_000000.gz -m block_000000.meta -l

# 导出崩溃文件
java -jar target/crash-exporter-jar-with-dependencies.jar -b block_000000.gz -m block_000000.meta -o output_dir
```

导出的文件名格式为：`id_XXXXXXXXXXXXXXXX_exitcode_X.crash`
