package com.example.fuzzer.sharedmemory;

public class Shm {
    static {
        System.loadLibrary("shm");
    }

    // 本地方法：创建共享内存，返回共享内存 ID（shm_id）
    public native int createSharedMemory(int size);

    // 本地方法：读取共享内存数据
    public native byte[] readSharedMemory(int shmId, int size);

    // 本地方法：销毁共享内存
    public native void destroySharedMemory(int shmId);
}
