package com.example.fuzzer.sharedmemory;

import java.io.IOException;

public class SharedMemoryManager {
    private Shm shm;
    private int shmId;
    private int size;

    public SharedMemoryManager(int size) throws IOException {
        this.size = size;
        shm = new Shm();
        shmId = shm.createSharedMemory(size);
        if (shmId < 0) {
            throw new IOException("Failed to create shared memory");
        }
    }

    public int getShmId() {
        return shmId;
    }

    public byte[] readSharedMemory() {
        return shm.readSharedMemory(shmId, size);
    }

    public void destroySharedMemory() {
        shm.destroySharedMemory(shmId);
    }
}
