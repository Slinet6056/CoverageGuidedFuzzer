FROM ubuntu:22.04

# Install OpenJDK, required tools and Chinese support
RUN apt-get update && apt-get install -y \
    openjdk-11-jdk \
    maven \
    fonts-wqy-microhei \
    fonts-wqy-zenhei \
    language-pack-zh-hans \
    && rm -rf /var/lib/apt/lists/*

# Set Chinese locale
ENV LANG zh_CN.UTF-8

# Set working directory
WORKDIR /app

# Copy the project files
COPY pom.xml .
COPY src src/

# Build the project
RUN mvn clean package

# Environment variables for fuzzer configuration
ENV MUTATOR_TYPE="AFL"
ENV ENERGY_SCHEDULER_TYPE="COVERAGE_BASED"
ENV SEED_SORTER_TYPE="HEURISTIC"
ENV NUM_THREADS=""
ENV DURATION_MINUTES="1"
ENV TIMEOUT="1"
ENV PROGRAM_ARGS="@@"

# Create directories
RUN mkdir -p /app/fuzz_target /app/fuzz_seeds /app/fuzz_output

# Copy the entry script
COPY docker/docker-entrypoint.sh /
RUN chmod +x /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
