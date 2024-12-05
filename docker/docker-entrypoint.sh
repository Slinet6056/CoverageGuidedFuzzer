#!/bin/bash

# Set NUM_THREADS to available CPU cores if not specified
if [ -z "$NUM_THREADS" ]; then
    NUM_THREADS=$(nproc)
fi

# Ensure fuzz_output directory exists
cd /app

# Construct Java command
JAVA_CMD="java -Djava.library.path=/app/src/main/native"
JAVA_CMD="$JAVA_CMD -jar target/coverage-guided-fuzzer-jar-with-dependencies.jar"
JAVA_CMD="$JAVA_CMD -p /app/fuzz_target/target"
JAVA_CMD="$JAVA_CMD -s /app/fuzz_seeds"
JAVA_CMD="$JAVA_CMD -m $MUTATOR_TYPE"
JAVA_CMD="$JAVA_CMD -e $ENERGY_SCHEDULER_TYPE"
JAVA_CMD="$JAVA_CMD -ss $SEED_SORTER_TYPE"
JAVA_CMD="$JAVA_CMD -j $NUM_THREADS"
JAVA_CMD="$JAVA_CMD -t $DURATION_MINUTES"
JAVA_CMD="$JAVA_CMD -to $TIMEOUT"
JAVA_CMD="$JAVA_CMD -c \"$PROGRAM_ARGS\""

# Make fuzz target executable
chmod +x fuzz_target/*

echo "Starting fuzzer with command: $JAVA_CMD"
eval $JAVA_CMD
