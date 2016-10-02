

#include <fcntl.h>
#include <pthread.h>
#include <errno.h>
#include <sys/mman.h>
#include <stdlib.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaExtractor.h>
#include <android/native_window_jni.h> // ANativeWindow

#include "HWH264DecodeAndEncode.h"

#define LOG_TAG "jni_deen"
#include "vortex.h"
#define De2FileAndEn2File_CLASS_PATH "com/tom/codecanativewin/jni/De2FileAndEn2File"

class JNIContext
{
public:
	jobject java_obj_ref ; // Java NativeWinCodec Obj
	ANativeWindow* pSurfaceWindow;
	JavaVM* jvm;
	int fd ;
	pthread_t thread  ;
	bool forceClose = false;
	JNIEnv* jenv ; // 对应一个attach到虚拟机JVM的线程
	AMediaCodec* decorder = NULL ;

};

struct De2FileAndEn2File_java_fields_t {
    jfieldID    context; 	// native object pointer
    jmethodID   post_event; // post event to Java Layer/Callback Function
} g_De2FileAndEn2File_java_fields;



static int msgCallback( int a ,int b)
{
	//ALOGD("msgCallback ! a %d  b %d " , a , b );
	return 0;
}

static void* decode_and_encode_thread(void* argv)
{
	JNIContext* nwc = (JNIContext*)argv;
	AttachThread2JVM(nwc->jvm, &nwc->jenv , "deencodeTh");

	Hwh264DecodeAndEncode* hdecodeAndEncode = new Hwh264DecodeAndEncode();

	hdecodeAndEncode->setCallbackftn(msgCallback);
	hdecodeAndEncode->Decode(0,0,nwc->pSurfaceWindow,NULL);

	delete hdecodeAndEncode ;


	nwc->thread = -1 ;
	nwc->jenv->SetLongField(nwc->java_obj_ref ,g_De2FileAndEn2File_java_fields.context, 0);
	nwc->jenv->DeleteGlobalRef(nwc->java_obj_ref);

	ANativeWindow_release(nwc->pSurfaceWindow);

	JavaVM * jvm = nwc->jvm ;
	JNIEnv * env = nwc->jenv ;
	delete nwc;
	DetachThread2JVM(jvm,env);
	return NULL;
}

JNIContext* get_native_nwc(JNIEnv *env, jobject thiz)
{
	JNIContext* context = reinterpret_cast<JNIContext*>(env->GetLongField(thiz, g_De2FileAndEn2File_java_fields.context));
    if (context != NULL) {
        return context ;
    }else{
    	ALOGI("Native Object Not Create or Release");
    	return NULL;
    }

}


JNIEXPORT void JNICALL native_decodeAndEncode(JNIEnv * env , jobject defile_and_encode_jobj , jobject objSurface)
{
	char result[256];
	JNIContext* nwc = get_native_nwc(env,defile_and_encode_jobj);
	if( nwc != NULL){
		// 重复调用
		ALOGE("Can Not setAndPlay running !");
		return ;
	}else{
		JNIContext* nwc = new JNIContext();
		nwc->thread = -1 ;
		nwc->java_obj_ref = env->NewGlobalRef(defile_and_encode_jobj);
		env->GetJavaVM(&nwc->jvm);
		nwc->pSurfaceWindow = ANativeWindow_fromSurface(env, objSurface);
		env->SetLongField(defile_and_encode_jobj ,g_De2FileAndEn2File_java_fields.context,  (long)nwc);

		// 创建线程
		int ret = pthread_create(&nwc->thread, NULL, decode_and_encode_thread, nwc );
		if(  ret != 0 ){

			ANativeWindow_release(nwc->pSurfaceWindow);
			env->DeleteGlobalRef(nwc->java_obj_ref);
			env->SetLongField(nwc->java_obj_ref ,g_De2FileAndEn2File_java_fields.context, 0);

			// android/libnativehelper
			snprintf(result, sizeof(result), "Can Not Create Thread with %s %d!", strerror(errno),ret);
			ALOGE("%s",result);


			return ;
		}
	}
}


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env ;
    if ( vm->GetEnv( (void**) &env, JNI_VERSION_1_6 )  != JNI_OK) {
    	ALOGE("GetEnv Err");
    	return JNI_ERR;
    }

	jclass clazz;
    clazz = env->FindClass(De2FileAndEn2File_CLASS_PATH );
    if (clazz == NULL) {
		ALOGE("%s:Class Not Found" , De2FileAndEn2File_CLASS_PATH );
		return JNI_ERR ;
    }

    JNINativeMethod method_table[] = {
    	/*
    	 * Method:    decodeAndEncode
    	 * Signature: ()V
    	 */
    	{ "decodeAndEncode","(Landroid/view/Surface;)V", (void*)native_decodeAndEncode },


    };
	jniRegisterNativeMethods( env, De2FileAndEn2File_CLASS_PATH ,  method_table, NELEM(method_table)) ;

	// 查找Java对应field属性
    field fields_to_find[] = {
        { De2FileAndEn2File_CLASS_PATH , "mNativeContext",  "J", &g_De2FileAndEn2File_java_fields.context },
    };

    find_fields( env , fields_to_find, NELEM(fields_to_find) );


	return JNI_VERSION_1_6 ;
}

