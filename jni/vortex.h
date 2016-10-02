/*
 * Copyright (C) 2016 The OCOCCI's Vortex Project
 *
 * visit
 *
 *		http://www.ococci.com
 *
 * for more information
 *
 * Author: 		hanlon
 * Email:		tom@ococci.com
 * Created on: 	2016.06.07
 * File: 		vortex.h
 */
#ifndef VORTEX_H_
#define VORTEX_H_

#include <jni.h>
#include <android/log.h>
#include <string.h>
#include <stdio.h>	// asprintf
#include <linux/types.h>

// Copy From ALog-priv.h
#ifndef LOG_NDEBUG
#ifdef NDEBUG
#define LOG_NDEBUG 1
#else
#define LOG_NDEBUG 0
#endif
#endif

#ifndef LOG_TAG
#warning "you should define LOG_TAG in your source file. use default now!"
#define LOG_TAG "default"
#endif


/*
 * Basic log message macros intended to emulate the behavior of log/log.h
 * in system core.  This should be dependent only on ndk exposed logging
 * functionality.
 */

#ifndef ALOG
#define ALOG(priority, tag, fmt...) \
    __android_log_print(ANDROID_##priority, tag, fmt)
#endif

#ifndef ALOGV
#if LOG_NDEBUG
#define ALOGV(...)   ((void)0)
#else
#define ALOGV(...) ((void)ALOG(LOG_VERBOSE, LOG_TAG, __VA_ARGS__))
#endif
#endif

#ifndef ALOGD
#define ALOGD(...) ((void)ALOG(LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#endif

#ifndef ALOGI
#define ALOGI(...) ((void)ALOG(LOG_INFO, LOG_TAG, __VA_ARGS__))
#endif

#ifndef ALOGW
#define ALOGW(...) ((void)ALOG(LOG_WARN, LOG_TAG, __VA_ARGS__))
#endif

#ifndef ALOGE
#define ALOGE(...) ((void)ALOG(LOG_ERROR, LOG_TAG, __VA_ARGS__))
#endif


// Copy and Modify From JNIHelp.h
#ifndef NELEM
#	define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif

#ifdef __cplusplus
#define jniRegisterNativeMethods(ENV,CLASS,METHODS,NUM) 						\
{ 																				\
	JNIEnv* jenv = ENV ; 														\
	const char* className = CLASS;												\
	JNINativeMethod* gMethods = METHODS;										\
	int numMethods = NUM;														\
																				\
    ALOGV("Registering %s's %d native methods...", className, numMethods);		\
    																			\
    jclass c = jenv->FindClass(className);										\
    if (c == NULL) {															\
    	ALOGE("[vortex.h] Java Class Not Found");								\
        char* msg;																\
        asprintf(&msg, "Native registration unable to find class '%s';"			\
											" aborting...", className);			\
        jenv->FatalError(msg);													\
    }																			\
    																			\
    if (jenv->RegisterNatives( c, gMethods, numMethods) < 0) {					\
    	ALOGE("[vortex.h] Register Native Function Error");						\
        char* msg;																\
        asprintf(&msg, "RegisterNatives failed for '%s';"						\
        									" aborting...", className);			\
        jenv->FatalError(msg);													\
    }																			\
}


struct field {
    const char *class_name;
    const char *field_name;
    const char *field_type;
    jfieldID   *jfield;
};


#define find_fields(ENV, FIELDS, COUNT)											\
{                                                                               \
	 JNIEnv *jenv = ENV ;                                                       \
	 field *fields = FIELDS ;                                                   \
	 int count = COUNT ;                                                        \
                                                                                \
    for (int i = 0; i < count; i++) {                                           \
        field *f = &fields[i];                                                  \
        jclass clazz = jenv->FindClass(f->class_name);                           \
        if (clazz == NULL) {                                                    \
            ALOGE("Can't find %s", f->class_name);                              \
            char* msg;                                                          \
            asprintf(&msg, "Can't find %s", f->class_name);                     \
            jenv->FatalError(msg);                                              \
        }                                                                       \
                                                                                \
        jfieldID field = jenv->GetFieldID(clazz, f->field_name, f->field_type);  \
        if (field == NULL) {                                                    \
            ALOGE("Can't find %s.%s", f->class_name, f->field_name);            \
            char* msg;                                                          \
            asprintf(&msg, "Can't find %s.%s", f->class_name, f->field_name);   \
            jenv->FatalError(msg);                                              \
        }                                                                       \
                                                                                \
        *(f->jfield) = field;                                                   \
    }                                                                           \
}

/// semphored-related
 void* streamer_sem_create(int initial_count, int max_count, const char* sem_name);
 int streamer_sem_destroy(void* handle);
 int streamer_sem_get_count(void* handle, int *count);
 int streamer_sem_post(void* handle);
 int streamer_sem_wait(void* handle, int time_out);
 int streamer_sem_try_wait(void* handle);

 //// JNI-Thread-related
 void DetachThread2JVM(JavaVM* vm  , JNIEnv*pEnv /* in */ );
 void AttachThread2JVM( JavaVM* vm , JNIEnv** ppEnv ,/* out */
 									const char* const threadName);
 jboolean checkCallbackThread(JavaVM* vm , JNIEnv* isTargetEnv);

 /// JNI-Exception-related
 int jniThrowIOException(JNIEnv* env, int errnum);
 int jniThrowRuntimeException(JNIEnv* env, const char* msg) ;
 int jniThrowNullPointerException(JNIEnv* env, const char* msg);
 int jniThrowException(JNIEnv* env, const char* className, const char* msg);


#else
#define jniRegisterNativeMethods(ENV,CLASS,METHODS,NUM) 						\
{ 																				\
	JNIEnv* jenv = ENV ; 														\
	const char* className = CLASS;												\
	JNINativeMethod* gMethods = METHODS;										\
	int numMethods = NUM;														\
																				\
    ALOGV("Registering %s's %d native methods...", className, numMethods);		\
    																			\
    jclass c = (*jenv)->FindClass(jenv, className);								\
    if (c == NULL) {															\
    	ALOGE("[vortex.h] Java Class Not Found");								\
        char* msg;																\
        asprintf(&msg, "Native registration unable to find class '%s';"			\
											" aborting...", className);			\
        jenv->FatalError(msg);													\
    }																			\
    																			\
    if ((*jenv)->RegisterNatives(jenv, c, gMethods, numMethods) < 0) {			\
    	ALOGE("[vortex.h] Register Native Function Error");						\
        char* msg;																\
        asprintf(&msg, "RegisterNatives failed for '%s';"						\
        									" aborting...", className);			\
        jenv->FatalError(msg);													\
    }																			\
}
#endif

#endif /* VORTEX_H_ */
