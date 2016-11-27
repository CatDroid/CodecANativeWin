
#define LOG_TAG "fast_convert"
#include "../vortex.h"
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#define JAVA_CLASS_PATH "com/tom/opengl/two/FastinOnePlane"


#define TO_420P 	20	// ImageFormat.YUY2
#define TO_420SP 	17 	// ImageFormat.NV21

JNIEXPORT jboolean JNICALL native_convert(JNIEnv * env , jclass clazz , jobject uv_target , jobject u , jobject v , int width , int height, int stride , int to )
{
    unsigned char * uv_buf = (unsigned char *)env->GetDirectBufferAddress(uv_target);
    unsigned char * u_buf =  (unsigned char *)env->GetDirectBufferAddress(u);
    unsigned char * v_buf =  (unsigned char *)env->GetDirectBufferAddress(v);


    if(to == TO_420P){
      	if(stride == 1 || stride == 2){
			unsigned char* limit = uv_buf + width * height / 4 ;
			while( uv_buf < limit ){
				*(uv_buf++) = *u_buf ; u_buf+=stride;
			}
			limit += width * height / 4 ;
			while( uv_buf < limit ){
				*(uv_buf++) = *v_buf ; v_buf+=stride;
			}
			return true ;
		}else {
			return false;
		}

    }else if( to == TO_420SP ){

    	if(stride == 1 || stride == 2){
    		unsigned char* limit = uv_buf + width * height /2 ;
    		while( uv_buf < limit ){
    			*(uv_buf++) = *u_buf ; u_buf+=stride;
    			*(uv_buf++) = *v_buf ; v_buf+=stride;
    		}
    	}else {
    		return false;
    	}

    	return true ;
    }
    return false;
}

JNIEXPORT jboolean JNICALL native_convert2(JNIEnv * env , jclass clazz , jobject yuv_target ,jobject y , jobject u , jobject v , int width , int height, int stride , int to )
{
    unsigned char * yuv_buf = (unsigned char *)env->GetDirectBufferAddress(yuv_target);
    unsigned char * y_buf =  (unsigned char *)env->GetDirectBufferAddress(y);
    unsigned char * u_buf =  (unsigned char *)env->GetDirectBufferAddress(u);
    unsigned char * v_buf =  (unsigned char *)env->GetDirectBufferAddress(v);

    unsigned char * bak_buf = yuv_buf ;

    ALOGD("%p %p %p %p" , yuv_buf , y_buf , u_buf , v_buf );
    if(to == TO_420P){
      	if(stride == 1 || stride == 2){
      		memcpy(yuv_buf , y_buf ,  width * height );
      		yuv_buf +=  width * height;
			unsigned char* limit = yuv_buf + width * height / 4 ;
			while( yuv_buf < limit ){
				*(yuv_buf++) = *u_buf ; u_buf+=stride;
			}
			limit += width * height / 4 ;
			while( yuv_buf < limit ){
				*(yuv_buf++) = *v_buf ; v_buf+=stride;
			}

//			static int test = 0 ;
//			if(test++ == 30){
//				int fd = open("/mnt/sdcard/temp.yuv" , O_CREAT | O_RDWR | O_TRUNC , 0755);
//				write(fd , bak_buf   , width * height * 3/2  );
//				close(fd);
//			}

			return true ;
		}else {
			return false;
		}

    }else if( to == TO_420SP ){
    	if(stride == 1 || stride == 2){

    		memcpy(yuv_buf , y_buf ,  width * height );
    		yuv_buf +=  width * height;
    		unsigned char* limit = yuv_buf + width * height /2 ;
    		while( yuv_buf < limit ){
    			*(yuv_buf++) = *u_buf ; u_buf+=stride;
    			*(yuv_buf++) = *v_buf ; v_buf+=stride;
    		}
    	}else {
    		return false;
    	}

    }
    return false;
}


JNIEXPORT jboolean JNICALL native_convert3(JNIEnv * env , jclass clazz , jobject yuv_target ,jobject y , jobject u , jobject v , int width , int height, int stride , int to )
{
    unsigned char * yuv_buf = (unsigned char *)env->GetDirectBufferAddress(yuv_target);
    unsigned char * y_buf =  (unsigned char *)env->GetDirectBufferAddress(y);
    unsigned char * u_buf =  (unsigned char *)env->GetDirectBufferAddress(u);
    unsigned char * v_buf =  (unsigned char *)env->GetDirectBufferAddress(v);

    unsigned char * bak_buf = yuv_buf ;

    ALOGD("%p %p %p %p" , yuv_buf , y_buf , u_buf , v_buf );
    if(to == TO_420P){
      	if(stride == 1 || stride == 2){
      		memcpy(yuv_buf , y_buf ,  width * height );
      		yuv_buf +=  width * height;
			unsigned char* limit = yuv_buf + width * height / 4 ;
			while( yuv_buf < limit ){
				*(yuv_buf++) = *u_buf ; u_buf+=stride;
			}
			limit += width * height / 4 ;
			while( yuv_buf < limit ){
				*(yuv_buf++) = *v_buf ; v_buf+=stride;
			}

//			static int test = 0 ;
//			if(test++ == 30){
//				int fd = open("/mnt/sdcard/temp.yuv" , O_CREAT | O_RDWR | O_TRUNC , 0755);
//				write(fd , bak_buf   , width * height * 3/2  );
//				close(fd);
//			}

			return true ;
		}else {
			return false;
		}

    }else if( to == TO_420SP ){
    	if(stride == 1 || stride == 2){

    		memcpy(yuv_buf , y_buf ,  width * height );
    		yuv_buf +=  width * height;
    		unsigned char* limit = yuv_buf + width * height /2 ;
    		while( yuv_buf < limit ){
    			*(yuv_buf++) = *u_buf ; u_buf+=stride;
    			*(yuv_buf++) = *v_buf ; v_buf+=stride;
    		}
    	}else {
    		return false;
    	}

    }
    return false;
}


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{


    JNIEnv* env ;
    if ( vm->GetEnv( (void**) &env, JNI_VERSION_1_6 )  != JNI_OK) {
    	ALOGE("GetEnv Err");
    	return JNI_ERR;
    }


	jclass clazz;
    clazz = env->FindClass(JAVA_CLASS_PATH );
    if (clazz == NULL) {
		ALOGE("%s:Class Not Found" , JAVA_CLASS_PATH );
		return JNI_ERR ;
    }
	if( env->ExceptionCheck() ){
		jthrowable jt = env->ExceptionOccurred();
		env->ExceptionClear();
		ALOGD("Error Occured GetStaticMethodID !");
	}

    JNINativeMethod method_table[] = {
    	{ "native_convert", "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;IIII)Z", (void*)native_convert },
    	{ "native_convert2", "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;IIII)Z", (void*)native_convert2 },
    };
	jniRegisterNativeMethods( env, JAVA_CLASS_PATH ,  method_table, NELEM(method_table)) ;
	if( env->ExceptionCheck() ){
		jthrowable jt = env->ExceptionOccurred();
		env->ExceptionClear();
		ALOGD("Error Occured GetStaticMethodID !");
	}

	return JNI_VERSION_1_6 ;
}
