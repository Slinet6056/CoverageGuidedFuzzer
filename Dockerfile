FROM ubuntu:22.04

# Install OpenJDK and required tools
RUN apt-get update && apt-get install -y \
    openjdk-11-jdk \
    maven \
    && rm -rf /var/lib/apt/lists/*

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
COPY docker-entrypoint.sh /
RUN chmod +x /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
