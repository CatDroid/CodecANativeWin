//
// Created by hl.he on 2018/4/16.
//

#include <jni.h>
#include <unistd.h>
#include <pthread.h>
#include <android/log.h>

extern "C"
JNIEXPORT void JNICALL
Java_com_tom_MemThread_ThreadActivity_ThreadProc(JNIEnv *env, jobject instance, jint stack_size,jboolean forever) {

    uint8_t buff[stack_size];

    memset(buff,0x55, stack_size );

    size_t stackSize;
    pthread_attr_t thread_attr;
    pthread_attr_init(&thread_attr);
    pthread_attr_getstacksize(&thread_attr,&stackSize );
    __android_log_print(ANDROID_LOG_DEBUG, "ThreadActivity", "stackSize = %zd", stackSize);
    //  1032192字节 = 1008KiloByte
    //  1040384字节 = 1016KiloByte

    while(forever){
        sleep(1000);
    }

}