/*
 * ABuffer_jni.cpp
 *
 *  Created on: 2016年10月3日
 *      Author: hanlon
 */

#include <assert.h>
#include <android/bitmap.h>
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


class JNIBitmapCallBack
{
public:
	jobject jbitmap_ref;
	uint8_t* bmp_data ;
	uint32_t bmp_size ;
};
JNIEXPORT jlong JNICALL native_new_Bitmap( JNIEnv * env , jclass jcls , jobject jbitmap ) {

	AndroidBitmapInfo info ;
	int ret = AndroidBitmap_getInfo(env, jbitmap, &info);
	if( ret != ANDROID_BITMAP_RESUT_SUCCESS){
		ALOGE( "AndroidBitmap_getInfo fail");
		return 0;
	}
	ALOGD( "native_new_Bitmap format[0x%x] width[%d] height[%d] flags[0x%x] stride[%d]" ,
		       info.format , info.width , info.height , info.flags , info.stride );

	if(info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 /*1*/ && info.format != ANDROID_BITMAP_FORMAT_RGB_565 /*4*/){
		ALOGE("native_new_Bitmap format not support info.format=%d " , info.format);
		return 0;
	}
	if(info.width * info.height < 5 ){
		ALOGE("pic too small ");
		return 0 ;
	}
	uint8_t* cdata = NULL ;
	ret = AndroidBitmap_lockPixels(env, jbitmap, (void**)&cdata  );
	if( ret != ANDROID_BITMAP_RESUT_SUCCESS || cdata == NULL){
		ALOGE("pushNewBitmap AndroidBitmap_lockPixels fail ret = %d cdata = %p " , ret , cdata);
		return 0;
	}


	JNIBitmapCallBack* cbCtx = new JNIBitmapCallBack();
	cbCtx->jbitmap_ref = env->NewGlobalRef( jbitmap ) ;
	cbCtx->bmp_data = cdata ;
	cbCtx->bmp_size =  info.width * info.height * (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888?4:2) ;
	ALOGD("add %p [0x%x 0x%x 0x%x 0x%x 0x%x 0x%x 0x%x 0x%x 0x%x 0x%x]", (void*)cbCtx,
		  cbCtx->bmp_data[ 0 ], cbCtx->bmp_data[ 1 ], cbCtx->bmp_data[ 2 ], cbCtx->bmp_data[ 3 ], cbCtx->bmp_data[ 4 ]  ,
		  cbCtx->bmp_data[ 5 ], cbCtx->bmp_data[ 6 ], cbCtx->bmp_data[ 7 ], cbCtx->bmp_data[ 8 ], cbCtx->bmp_data[ 9 ] );

	return (jlong)cbCtx;
}

JNIEXPORT void JNICALL  native_del_Bitmap( JNIEnv * env , jclass jcls , jlong ctx ) {
	JNIBitmapCallBack* cbCtx = (JNIBitmapCallBack*) ctx;
	// 随机内存访问
	if( (void*)ctx == 0 ){
		ALOGE("native_del_Bitmap ctx %p " , (void*)ctx );
		return ;
	}

 	ALOGD("del %p [0x%x 0x%x 0x%x 0x%x 0x%x 0x%x 0x%x 0x%x 0x%x 0x%x]", (void*)cbCtx,
		  cbCtx->bmp_data[ 0 ], cbCtx->bmp_data[ 1 ], cbCtx->bmp_data[ 2 ], cbCtx->bmp_data[ 3 ], cbCtx->bmp_data[ 4 ]  ,
		  cbCtx->bmp_data[ 5 ], cbCtx->bmp_data[ 6 ], cbCtx->bmp_data[ 7 ], cbCtx->bmp_data[ 8 ], cbCtx->bmp_data[ 9 ] );

	for( int i = cbCtx->bmp_size - 10 ; i < cbCtx->bmp_size ; i ++ ){
		cbCtx->bmp_data[ i ] = 0xFF;
	}
	ALOGD("del done");

	AndroidBitmap_unlockPixels(env,  cbCtx->jbitmap_ref );
	env->DeleteGlobalRef( cbCtx->jbitmap_ref );
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
		{ "native_new_Bitmap", "(Ljava/lang/Object;)J", (void*)native_new_Bitmap },
		{ "native_del_Bitmap", "(J)V", (void*)native_del_Bitmap },
    };
	jniRegisterNativeMethods( env, JAVA_CLASS_PATH ,  method_table, NELEM(method_table)) ;


	if( env->ExceptionCheck() ){
		jthrowable jt __attribute__((unused)) = env->ExceptionOccurred();
		env->ExceptionClear();
		ALOGD("Error Occured GetStaticMethodID !");
	}

	return JNI_VERSION_1_6 ;
}

