/*
 * ABuffer_jni.cpp
 *
 *  Created on: 2016年10月3日
 *      Author: hanlon
 */

#include <assert.h>
#include "jni.h"
#define LOG_TAG "jni_abuffer"
#include "vortex.h"
#include "NativeBuffer.h"

#define JAVA_CLASS_PATH "com/tom/codecanativewin/jni/ABuffer"

JNIEXPORT void JNICALL  native_release( JNIEnv * env , jobject jobj , jlong ctx )
{
	ABuffer* pABuffer = (ABuffer*) ctx ;
	unsigned char* buffer = (unsigned char*)pABuffer->mData ;
	ALOGI("release buffer %p %p [%02x %02x %02x %02x %02x]" , pABuffer , pABuffer->mData ,
										buffer[0]  ,
										buffer[1]  ,
										buffer[2]  ,
										buffer[3]  ,
										buffer[4]   );
	pABuffer->mpSelfManager->releaseBuffer(pABuffer);
}


JNIEXPORT long JNICALL  native_malloc( JNIEnv * env , jclass jcls , jint size ) {
	uint8_t* ptr = (uint8_t*)malloc(size);
	memset(ptr, 1, size);
	ALOGD("malloc ptr %p", ptr );
	return (long)ptr;
}

JNIEXPORT void JNICALL  native_free( JNIEnv * env , jclass jcls , jlong buf ) {
	uint8_t* ptr = (uint8_t*)buf;
	ALOGD("free ptr %p", ptr );
	free(ptr);
	return ;
}

JNIEXPORT long JNICALL  native_new( JNIEnv * env , jclass jcls , jint size ) {
	uint8_t* ptr =  new uint8_t[size] ;
	memset(ptr, 2, size);
	ALOGD("malloc ptr %p", ptr );
	return (long)ptr;
}

JNIEXPORT void JNICALL  native_del( JNIEnv * env , jclass jcls , jlong buf ) {
	uint8_t* ptr = (uint8_t*)buf;
	ALOGD("free ptr %p", ptr );
	delete[] ptr ;
	return ;
}

class JNIDataCallBack
{
public:
	jobject jbytearray_ref;
	jbyte* data ;
};



JNIEXPORT jlong JNICALL  native_new_byteArray( JNIEnv * env , jclass jcls , jbyteArray array ) {

//Android.mk LOCAL_CFLAGS	+=   -UNDEBUG
#ifdef NDEBUG
#error "Oops, NDEBUG is defined"
#endif

//   assert(false);
// assert(true);

	int8_t* buffer = NULL;
	jboolean  copy = JNI_TRUE;
	jsize length = env->GetArrayLength(array);
	buffer = (int8_t*)env->GetByteArrayElements(array, &copy);
	ALOGD("newByteArray length %d , buffer %p, copy %s ", length, buffer, (copy==JNI_TRUE?"true":"false") );
	*(buffer+5) = (int8_t)0x87;
	JNIDataCallBack* cbCtx = new JNIDataCallBack();
	cbCtx->data = buffer;
	//cbCtx->jbytearray_ref = array; // 如果Java层没有强引用 这里GC就会释放!
	cbCtx->jbytearray_ref = env->NewGlobalRef(array);
	return (jlong)cbCtx;
}

JNIEXPORT void JNICALL  native_del_byteArray( JNIEnv * env , jclass jcls , jlong ctx ) {

	JNIDataCallBack* cbCtx = (JNIDataCallBack*) ctx;
	env->ReleaseByteArrayElements((jbyteArray)cbCtx->jbytearray_ref, (jbyte*)cbCtx->data, JNI_ABORT);
	env->DeleteGlobalRef( cbCtx->jbytearray_ref );
	//env->DeleteLocalRef(cbCtx->jbytearray_ref);
	delete cbCtx;
	return ;
}





JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
	ALOGI("ABuffer_jni.cpp JNI_OnLoad");
    JNIEnv* env ;
    if ( vm->GetEnv( (void**) &env, JNI_VERSION_1_6 )  != JNI_OK) {
    	ALOGE("GetEnv Err");
    	return JNI_ERR;
    }

	jclass clazz;
    clazz = env->FindClass(JAVA_CLASS_PATH );
	if( env->ExceptionCheck() || clazz == NULL ){
		jthrowable jt __attribute__((unused)) = env->ExceptionOccurred();
		env->ExceptionClear();
		ALOGD("Error Occured GetStaticMethodID !");
	}


    JNINativeMethod method_table[] = {
		{ "native_del", "(J)V", (void*)native_del },
		{ "native_new", "(I)J", (void*)native_new },
    	{ "native_release", "(J)V", (void*)native_release },
		{ "native_malloc", "(I)J", (void*)native_malloc },
		{ "native_free", "(J)V", (void*)native_free },
		{ "native_new_byteArray", "([B)J", (void*)native_new_byteArray },
		{ "native_del_byteArray", "(J)V", (void*)native_del_byteArray },
    };
	jniRegisterNativeMethods( env, JAVA_CLASS_PATH ,  method_table, NELEM(method_table)) ;


	if( env->ExceptionCheck() ){
		jthrowable jt __attribute__((unused)) = env->ExceptionOccurred();
		env->ExceptionClear();
		ALOGD("Error Occured GetStaticMethodID !");
	}

	return JNI_VERSION_1_6 ;
}

