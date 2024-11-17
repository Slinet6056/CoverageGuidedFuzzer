#include <jni.h>
#include "com_example_fuzzer_sharedmemory_Shm.h"
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/types.h>
#include <stdlib.h>
#include <string.h>

JNIEXPORT jint JNICALL Java_com_example_fuzzer_sharedmemory_Shm_createSharedMemory
  (JNIEnv *env, jobject obj, jint size) {
    int shmId = shmget(IPC_PRIVATE, size, IPC_CREAT | IPC_EXCL | 0600);
    return shmId;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_fuzzer_sharedmemory_Shm_readSharedMemory
  (JNIEnv *env, jobject obj, jint shmId, jint size) {
    // 连接到共享内存
    void *shmAddr = shmat(shmId, NULL, SHM_RDONLY);
    if (shmAddr == (void *) -1) {
        return NULL;
    }

    // 复制共享内存中的数据
    jbyteArray result = (*env)->NewByteArray(env, size);
    (*env)->SetByteArrayRegion(env, result, 0, size, (jbyte *) shmAddr);

    // 分离共享内存
    shmdt(shmAddr);
    return result;
}

JNIEXPORT void JNICALL Java_com_example_fuzzer_sharedmemory_Shm_destroySharedMemory
  (JNIEnv *env, jobject obj, jint shmId) {
    shmctl(shmId, IPC_RMID, NULL);
}


//java -Djava.library.path=src/main/native your.main.Class