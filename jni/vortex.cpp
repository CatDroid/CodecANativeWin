// -------------------
#include <stdio.h>
#include <stdlib.h>
#include <semaphore.h>
#include <time.h>
#include <sys/time.h>
#include <errno.h>


#define LOG_TAG "vortex"
#include "vortex.h"
#define STREAMER_TRACE ALOGD


int jniThrowException(JNIEnv* env, const char* className, const char* msg) {
    if (env->ExceptionCheck()) {
    	ALOGE("jniThrowException Exception in Exception : Clear and Throw New Excepiion");
    	env->ExceptionClear();
    }

     jclass expclass =  env->FindClass(className );
    if (expclass == NULL) {
        ALOGE("Unable to find exception class %s", className);
        return -1;
    }

    if ( env ->ThrowNew( expclass , msg) != JNI_OK) {
        ALOGE("Failed throwing '%s' '%s'", className, msg);
        return -1;
    }

    return 0;
}
int jniThrowNullPointerException(JNIEnv* env, const char* msg) {
    return jniThrowException(env, "java/lang/NullPointerException", msg);
}

int jniThrowRuntimeException(JNIEnv* env, const char* msg) {
    return jniThrowException(env, "java/lang/RuntimeException", msg);
}

int jniThrowIOException(JNIEnv* env, int errnum) {
    const char* message = strerror(errnum);
    return jniThrowException(env, "java/io/IOException", message);
}


jboolean checkCallbackThread(JavaVM* vm , JNIEnv* isTargetEnv) {
	JNIEnv* currentThreadEnv = NULL;
    if ( vm->GetEnv( (void**) &currentThreadEnv, JNI_VERSION_1_6) != JNI_OK) {
    	return JNI_FALSE;
    }

    if (isTargetEnv != currentThreadEnv || isTargetEnv == NULL) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

void AttachThread2JVM( JavaVM* vm , JNIEnv** ppEnv ,/* out */
									const char* const threadName)
{
	JavaVMAttachArgs args;
	args.version = JNI_VERSION_1_6;
	args.name = threadName ;
	args.group = NULL;
	if ( vm->AttachCurrentThread(ppEnv, &args) != JNI_OK){
		ALOGE("Fatal Exception: AttachCurrentThread Fail ");
	}else{
		ALOGI("attach %s thread to JVM done" , threadName);
	}
}

void DetachThread2JVM(JavaVM* vm  , JNIEnv*pEnv /* in */ )
{
	if (!checkCallbackThread(vm, pEnv)) {
		return;
    }
	vm->DetachCurrentThread();

}

int64_t system_nanotime()
{
    timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return now.tv_sec * 1000000000LL + now.tv_nsec;
}


void* streamer_sem_create(int initial_count, int max_count, const char* sem_name)
{
	sem_t *sem = NULL;

	sem = (sem_t*)malloc(sizeof(sem_t));
	if( 0 == sem_init(sem, 0, initial_count) )
	{
		STREAMER_TRACE((const char*)"streamer create sem success init = %d, sem_name = %s",initial_count, sem_name);
		return sem;
	}
	free(sem);
	STREAMER_TRACE((const char*)"streamer sem create error init = %d,sem_name=%s",initial_count, sem_name);
	return NULL;
}

int streamer_sem_destroy(void* handle)
{
	if( NULL != handle)
	{
		sem_destroy((sem_t*)handle);
		free(handle);
	}
	return (0);
}

int streamer_sem_get_count(void* handle, int *count)
{
	if( NULL != handle){
		if( 0 != sem_getvalue((sem_t *)handle, count)){
			*count = -1;
		}else{
			STREAMER_TRACE((const char*)"streamer sem count(%d) \n", *count);
		}
		return 0;
	}
	return (-1);
}

int streamer_sem_post(void* handle)
{
	if(NULL != handle)
	{
		STREAMER_TRACE((const char*)"streamer sem post  handle (%p)\n", handle);
        return sem_post((sem_t*)handle);
	}
	return (-1);
}

int streamer_sem_wait(void* handle, int time_out)
{
    if(NULL != handle){
        if( 0 == time_out){
        	return sem_wait((sem_t*)handle);
        }else{
        	struct timespec abs_timeout = {0};
        	abs_timeout.tv_sec = time(NULL) + time_out/1000;
        	return sem_timedwait((sem_t*)handle, &abs_timeout);
        }
    }
    return (-1);
}

int streamer_sem_try_wait(void* handle) // = 0 获得信号量(计数减一)
{
    if(NULL != handle){
    	// 所有这些函数在成功时都返回 0；错误保持信号量值没有更改，-1 被返回，并设置 errno来指明错误
    	int ret = sem_trywait((sem_t*)handle);
    	if(ret == -1 ){
    		return -errno ;
    	}else if ( ret == 0 ){
    		return 0;
    	}else {
    		STREAMER_TRACE("sem_trywait bad return value!");
    	}

    }
    return (-1);
}


// -------------------
