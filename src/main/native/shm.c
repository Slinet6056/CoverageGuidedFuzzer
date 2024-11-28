#include <jni.h>
#include "com_example_fuzzer_sharedmemory_Shm.h"
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/types.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#define AFL_SHM_ENV_VAR "__AFL_SHM_ID"

JNIEXPORT jint JNICALL Java_com_example_fuzzer_sharedmemory_Shm_createSharedMemory
  (JNIEnv *env, jobject obj, jint size) {
    // 尝试获取AFL++的共享内存ID
    const char* afl_shm_id = getenv(AFL_SHM_ENV_VAR);
    if (afl_shm_id != NULL) {
        // 如果AFL++已经创建了共享内存，直接使用它的ID
        return atoi(afl_shm_id);
    }

    // 使用固定的key，与AFL++保持一致
    key_t shm_key = 0x4141594c;  // "LAFL" in hex
    int shmId = shmget(shm_key, size, IPC_CREAT | 0600);
    if (shmId == -1) {
        // 如果失败，尝试使用IPC_PRIVATE作为后备方案
        fprintf(stderr, "Failed to create shared memory with fixed key: %s\n", strerror(errno));
        shmId = shmget(IPC_PRIVATE, size, IPC_CREAT | IPC_EXCL | 0600);
    }
    return shmId;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_fuzzer_sharedmemory_Shm_readSharedMemory
  (JNIEnv *env, jobject obj, jint shmId, jint size) {
    // 连接到共享内存，使用读写权限
    void *shmAddr = shmat(shmId, NULL, 0);
    if (shmAddr == (void *) -1) {
        fprintf(stderr, "Failed to attach shared memory: %s\n", strerror(errno));
        return NULL;
    }

    // 复制共享内存中的数据
    jbyteArray result = (*env)->NewByteArray(env, size);
    if (result == NULL) {
        fprintf(stderr, "Failed to create byte array\n");
        shmdt(shmAddr);
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, result, 0, size, (jbyte *) shmAddr);

    // 检查共享内存中是否有数据
    int hasData = 0;
    for (int i = 0; i < size; i++) {
        if (((jbyte *)shmAddr)[i] != 0) {
            hasData = 1;
            break;
        }
    }
    if (!hasData) {
        fprintf(stderr, "Warning: Shared memory is empty\n");
    }

    // 分离共享内存
    if (shmdt(shmAddr) == -1) {
        fprintf(stderr, "Failed to detach shared memory: %s\n", strerror(errno));
    }
    return result;
}

JNIEXPORT void JNICALL Java_com_example_fuzzer_sharedmemory_Shm_destroySharedMemory
  (JNIEnv *env, jobject obj, jint shmId) {
    shmctl(shmId, IPC_RMID, NULL);
}

//java -Djava.library.path=src/main/native your.main.Class