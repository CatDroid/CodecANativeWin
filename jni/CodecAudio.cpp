

#include <fcntl.h>
#include <pthread.h>
#include <errno.h>
#include <sys/mman.h>
#include <stdlib.h>
#include <sys/prctl.h> // prctl(PR_SET_NAME,"THREAD1");
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaExtractor.h>
#include <android/native_window_jni.h> // ANativeWindow
#include <arpa/inet.h>


#define LOG_TAG "jni_codecaudio"
#include "vortex.h"
#define JAVA_CLASS_PATH "com/tom/codecanativewin/jni/NativeAudioCodec"
#define AUDIO_TRACK_COST_TIME

class JNIContext
{
public:
	jobject java_obj_ref ; // Java NativeWinCodec Obj
	ANativeWindow* pSurfaceWindow;
	JavaVM* jvm;
	int fd ;
	pthread_t mExtractth  ;
	pthread_t mDecodeOuth ;
	bool forceClose = false;
	JNIEnv* jenv ; // 对应一个attach到虚拟机JVM的线程
	AMediaCodec* decorder = NULL ;


	// ACC arguments
	int mChannelMask ; // not just channel count: in/out and 1/2/4/5
	int mBitWidth ;
	int mSampleRate ;

};

// C++ Warning: anonymous type with no linkage used to declare variable
// 匿名类型 只能是 static 不能在其他的编译单元cpp c中引用这个全局变量
// 因为其他编译单元中 没法写 extern struct ??? g_java_fields ; 类型不能确定
static struct  {
    jfieldID    context; 	// native object pointer
    jmethodID   post_event; // post event to Java Layer/Callback Function
} g_java_fields;


static void AttachDetachThread2JVM( JavaVM* vm ,
									jboolean attach ,
									JNIEnv** ppEnv /* if attach = ture,out; if attach = false,in*/,
									const char* const pThreadName = "DefaultNativeThName"
											/* if attach = true , set 'pThreadName' */) {

    if (attach  == JNI_TRUE) {
        JavaVMAttachArgs args;
        args.version = JNI_VERSION_1_6;
        args.name = pThreadName ;// 这样DDMS中就可以看到有一个线程NativeThread
        args.group = NULL;
        vm->AttachCurrentThread( ppEnv, &args);
        ALOGD("Callback thread attached: %p", *ppEnv);
    } else {
    	if (!checkCallbackThread(vm, *ppEnv)) {
    		ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__);
    		return;
        }
    	vm->DetachCurrentThread(); // detachCurrentThread 需要在原来attached的线程中调用
    }
    ALOGD("AttachDetachThread2JVM %s Done" , (attach?"Attach":"Detach"));
}

JNIContext* get_native_nwc(JNIEnv *env, jobject thiz)
{
	JNIContext* context = reinterpret_cast<JNIContext*>(env->GetLongField(thiz, g_java_fields.context));
    if (context != NULL) {
        return context ;
    }else{
    	ALOGI("Native Object Not Create or Release");
    	return NULL;
    }
}

inline int64_t systemnanotime()
{
	timespec cur_time;
    clock_gettime(CLOCK_MONOTONIC, &cur_time);
    return cur_time.tv_sec * 1000000000LL + cur_time.tv_nsec;

	/**
	 * CLOCK_REALTIME:系统实时时间,随系统实时时间改变而改变,即从UTC1970-1-1 0:0:0开始计时 中间时刻如果系统时间被用户改成其他 则对应的时间相应改变
	 * CLOCK_MONOTONIC:从系统启动这一刻起开始计时,不受系统时间被用户改变的影响
	 * CLOCK_PROCESS_CPUTIME_ID:本进程到当前代码系统CPU花费的时间
	 * CLOCK_THREAD_CPUTIME_ID:本线程到当前代码系统CPU花费的时间
	 */
}

inline int64_t systemmstime()
{
	timespec cur_time;
    clock_gettime(CLOCK_MONOTONIC, &cur_time);
    return cur_time.tv_sec * 1000LL + cur_time.tv_nsec / 1000000LL;

}

inline int64_t systemustime()
{
	struct timeval cur_time;
	gettimeofday(&cur_time, NULL); // 系统实时时间 从1970.1.1
	return cur_time.tv_sec * 1000000LL + cur_time.tv_usec ;
}


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

#define USING_WINDOW 0
#define USING_CUSTOM_FORMAT 1
#define USING_ESDS_CONFIG 1 	// only valiable while USING_CUSTOM_FORMAT = 1
#define DECODEOUT_THREAD 1
//#define TEST_ACC_DECODE_FILE "/mnt/sdcard/0.3gp"
//#define TEST_ACC_DECODE_FILE "/mnt/sdcard/fuzhubao.3gp"
//#define TEST_ACC_DECODE_FILE "/mnt/sdcard/1080p60fps.mp4"
//#define TEST_ACC_DECODE_FILE "/mnt/sdcard/1080p30fps.mp4"
//#define TEST_ACC_DECODE_FILE "/mnt/sdcard/m960.3gp"
#define TEST_ACC_DECODE_FILE "/mnt/sdcard/wushun.3gp"
//#define TEST_ACC_DECODE_FILE "/mnt/sdcard/test.aac"
//#define TEST_ACC_DECODE_FILE "/mnt/sdcard/test.mp3"
//#define TEST_ACC_DECODE_FILE "/mnt/sdcard/test2.aac"
#define TEST_DISCARD_SAMPLE 0



#define AudioManager_STREAM_MUSIC 3
#define AudioFormat_CHANNEL_OUT_MONO 0x4
#define AudioFormat_CHANNEL_OUT_STEREO 0x4|0x8
								/*  如果AudioTrack通道配置错误了，比如音源是双的, 配置成单的, 声音就会缓了,类似44.1的声源成了22k
	            					AudioFormat.CHANNEL_OUT_FRONT_LEFT = 0x4;
	            					AudioFormat.CHANNEL_OUT_FRONT_RIGHT = 0x8;
	            					AudioFormat.CHANNEL_OUT_MONO = CHANNEL_OUT_FRONT_LEFT;
	            					AudioFormat.CHANNEL_OUT_STEREO = (CHANNEL_OUT_FRONT_LEFT | CHANNEL_OUT_FRONT_RIGHT);
	             	 	 	 	 */
#define AudioFormat_ENCODING_PCM_16BIT 2
#define AudioFormat_ENCODING_PCM_8BIT  3
								/*	AudioFormat.ENCODING_PCM_16BIT = 2;
	            					AudioFormat.ENCODING_PCM_8BIT = 3;
	             	 	 	 	 */
#define AudioTrack_MODE_STATIC 0
#define AudioTrack_MODE_STREAM 1
								 /*
	            					AudioTrack.MODE_STATIC = 0;
	            					AudioTrack.MODE_STREAM = 1;
	             	 	 	 	 */

// refer  ffmpeg mpeg4audio.h
enum AudioObjectType {
    AOT_NULL,					// Support?                Name
    AOT_AAC_MAIN,              ///< Y                       Main
    AOT_AAC_LC,                ///< Y                       Low Complexity
    AOT_AAC_SSR,               ///< N (code in SoC repo)    Scalable Sample Rate
    AOT_AAC_LTP,               ///< Y                       Long Term Prediction
    AOT_SBR,                   ///< Y                       Spectral Band Replication
    AOT_AAC_SCALABLE,          ///< N                       Scalable
};
// refer ffmpeg mpeg4audio.c
const int avpriv_mpeg4audio_sample_rates[16] = {
    96000, 88200, 64000, 48000, 44100, 32000,
    24000, 22050, 16000, 12000, 11025, 8000, 7350
};


// 创建线程
#if DECODEOUT_THREAD
static void* decodeACCOutput_thread(void* argv)
{

	JNIContext* nwc = (JNIContext*)argv;
	JNIEnv* current_thread_jenv ;
	jint buffer_size;
	jobject audio_track;
	jclass audiotrack_cls;
	jbyteArray byteArray;

	AttachDetachThread2JVM(nwc->jvm ,true,&current_thread_jenv , "decodeACCOutput_thread" );


	audiotrack_cls = current_thread_jenv->FindClass( "android/media/AudioTrack");

	// static public int getMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat)
	jmethodID min_buff_size_id = current_thread_jenv->GetStaticMethodID(
								audiotrack_cls, "getMinBufferSize",  "(III)I");

	// 	public int setStereoVolume(float leftGain, float rightGain)
	jmethodID audiotrack_setStereoVolume = current_thread_jenv->GetMethodID(audiotrack_cls,"setStereoVolume","(FF)I");

	//	public void play() throws IllegalStateException
	jmethodID audiotrack_play = current_thread_jenv->GetMethodID(audiotrack_cls, "play", "()V");

	// 	public int write(byte[] audioData, int offsetInBytes, int sizeInBytes)
	jmethodID audiotrack_write = current_thread_jenv->GetMethodID(audiotrack_cls,"write","([BII)I");

	jmethodID audiotrack_stop = current_thread_jenv->GetMethodID(audiotrack_cls, "stop", "()V");
	jmethodID audiotrack_release = current_thread_jenv->GetMethodID(audiotrack_cls,"release","()V");

	buffer_size = current_thread_jenv->CallStaticIntMethod( audiotrack_cls, min_buff_size_id,
					nwc->mSampleRate,
					nwc->mChannelMask,
					nwc->mBitWidth);

	ALOGI("getMinBufferSize = %d", buffer_size);


	jmethodID constructor_id = current_thread_jenv->GetMethodID( audiotrack_cls, "<init>",
	            "(IIIIII)V");
	audio_track = current_thread_jenv->NewObject( audiotrack_cls,
	            constructor_id,
	            AudioManager_STREAM_MUSIC ,
	            nwc->mSampleRate,
	            nwc->mChannelMask,
	            nwc->mBitWidth,
	            buffer_size ,
	            AudioTrack_MODE_STREAM
	);


	current_thread_jenv->CallIntMethod( audio_track, audiotrack_setStereoVolume, 1.0, 1.0);

	current_thread_jenv->CallVoidMethod(audio_track, audiotrack_play);

	byteArray = current_thread_jenv->NewByteArray( buffer_size );

	/*
	jint read ;
	current_thread_jenv->SetByteArrayRegion( buffer, 0, read, (jbyte*)buf);
	current_thread_jenv->CallVoidMethod( audio_track, audiotrack_write, buffer, 0, read);
	*/


	AMediaCodec* decoder  = nwc->decorder ;
	bool sawOutputEOS = false ;
	uint64_t outputCount = 0 ;
	uint32_t channel_count = (nwc->mChannelMask== AudioFormat_CHANNEL_OUT_MONO)? 1 : 2 ;
	uint32_t bitwidth = (nwc->mBitWidth == AudioFormat_ENCODING_PCM_16BIT) ? 2 : 1 ;

	while( !sawOutputEOS && !nwc->forceClose ){
		if (!sawOutputEOS) {
			AMediaCodecBufferInfo info;

//			if(outputCount % 20 == 0 ){
//				usleep(1000*1000);
//				ALOGD("force sleep");
//			}

			ssize_t status = AMediaCodec_dequeueOutputBuffer(decoder, &info, 1000000); //10ms  timeoutUs
			if (status >= 0) {
					if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
						ALOGD("decode output EOS");
						sawOutputEOS = true;
					}


					size_t outsize = 0;
					uint8_t* outbuffer = AMediaCodec_getOutputBuffer(decoder, status, &outsize);
					//	outsize 是整个buffer的大小
					//	解码的结果大小要看 BufferInfo.size

					if( outsize > buffer_size ){
						ALOGE("byteArray is Too Small! MinBufferSize %d info.size %d outsize %zd "
											, buffer_size , info.size , outsize );

						buffer_size = outsize ;
						current_thread_jenv->DeleteLocalRef( byteArray );
						byteArray = current_thread_jenv->NewByteArray( buffer_size );
					}

					outputCount ++ ;


					// (info.size)  PCM数据/通道数/bit-width = PCM样本数目        PCM样本数目/ 44100 = 播放时间
					// 44100 双通道  16bit ==> [PCM] data size = 4096 , sample count = 4096/2/2 = 1024 个样本      1024*1000/44100 ~= 23ms
					// 44100 单通道  16bit ==> [PCM] data size = 2048 , sample count = 1024 playtime = 23.21995


					// ALOGW("size is %d " , info.size);


//					int sample_count = info.size/channel_count/bitwidth ;
//					ALOGD("[PCM] data size = %d , sample count = %d playtime = %f ms " ,
//											info.size ,
//											sample_count ,
//											sample_count * 1000.0f / nwc->mSampleRate  );


#ifdef AUDIO_TRACK_COST_TIME
					struct timeval tb_pre ;
					gettimeofday(&tb_pre, NULL);
#endif

					current_thread_jenv->SetByteArrayRegion(byteArray, 0, info.size, (jbyte *)outbuffer);
					if(current_thread_jenv->ExceptionCheck()) {
						ALOGE("SetByteArrayRegion Error ");
						current_thread_jenv->ExceptionDescribe();
						current_thread_jenv->ExceptionClear();
						goto ExceptionExit ;
					}
					current_thread_jenv->CallIntMethod(audio_track, audiotrack_write, byteArray, 0, info.size);
					if(current_thread_jenv->ExceptionCheck()) {
						ALOGE("AudioTrack Write Error");
						current_thread_jenv->ExceptionDescribe();
						current_thread_jenv->ExceptionClear();
						goto ExceptionExit ;
					}


#ifdef AUDIO_TRACK_COST_TIME
					struct timeval tb_post ;
					gettimeofday(&tb_post, NULL);
					ALOGI("audio write cost = %llu us ", (tb_post.tv_sec - tb_pre.tv_sec) * 1000uL * 1000uL +   (tb_post.tv_usec - tb_pre.tv_usec) );
#endif

					AMediaCodec_releaseOutputBuffer(decoder, status, info.size != 0); // render = true 如果configure时候配置了surface 就render到surface上
			} else if (status == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
				ALOGD("output buffers changed");
			} else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
				AMediaFormat *format = NULL;
				format = AMediaCodec_getOutputFormat(decoder);
				ALOGD("format changed to: %s", AMediaFormat_toString(format));
				AMediaFormat_delete(format);
			} else if (status == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
				//ALOGD("no output buffer right now, try again later ");
			} else {
				ALOGD("unexpected info code: %zd", status);
			}
		}
	}

	current_thread_jenv->CallVoidMethod(audio_track, audiotrack_stop);
	current_thread_jenv->CallVoidMethod(audio_track, audiotrack_release);

ExceptionExit:
	current_thread_jenv->DeleteLocalRef( audio_track );
	current_thread_jenv->DeleteLocalRef( byteArray );

	AttachDetachThread2JVM(nwc->jvm , false, &current_thread_jenv );


}
#endif

static void* extractAAC_thread(void* argv)
{
	// 只有attach到JVM的线程 才能在DDMS中看到
	// prctl(PR_SET_NAME,"extractAAC_thread");

	JNIContext* nwc = (JNIContext*)argv;
	AttachDetachThread2JVM(nwc->jvm ,true,&nwc->jenv , "extractAAC_thread" );

	int acc_profile = 0 ;
	int bit_rate = 0 ;
	int channel = 0 ;
	int bit_witdh = 0 ;
	int channel_mask = 0 ;
	int frame_rate = 0;
	int sample_rate = 0;
	int sample_count = 0 ;
	int is_adts = 0 ;
	int duration = 0;
	int max_input_size = 0;
	int64_t render_startup_time_us = -1 ;
	bool sawInputEOS = false, sawOutputEOS = false ;

	int fd = open(TEST_ACC_DECODE_FILE, O_RDONLY);
	if (fd < 0) {
		ALOGE ("failed: %d (%s)", fd, strerror(errno));
		return NULL;
	}

	AMediaExtractor* audio_extract = AMediaExtractor_new();
	media_status_t err = AMediaExtractor_setDataSourceFd(audio_extract, fd, 0, LONG_MAX);
	close(fd);
	if (err != AMEDIA_OK) {
		ALOGE("setDataSource error: %d", err);
		return NULL;
	}

	int numtracks = AMediaExtractor_getTrackCount(audio_extract);
	for (int i = 0; i < numtracks; i++) {
		AMediaFormat *format = AMediaExtractor_getTrackFormat(audio_extract, i);
		if (format == NULL) {
			ALOGE("Can Not Get AMediaFormat for track %d " , i );
			continue;
		}
		const char *s = AMediaFormat_toString(format);
		const char *mime;
		if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
			ALOGE("no mime type");
			return NULL;
		} else if (!strncmp(mime, "audio/", 6)) {
			/*
			    public static final String MIMETYPE_AUDIO_AMR_NB = 	"audio/3gpp";
				public static final String MIMETYPE_AUDIO_AMR_WB = 	"audio/amr-wb";
				public static final String MIMETYPE_AUDIO_MPEG 	 = 	"audio/mpeg"; // mp3
				public static final String MIMETYPE_AUDIO_AAC    = 	"audio/mp4a-latm";
				public static final String MIMETYPE_AUDIO_VORBIS = 	"audio/vorbis"
				public static final String MIMETYPE_AUDIO_RAW    = 	"audio/raw";


				audio/3gpp" 	- AMR narrowband audio
				audio/amr-wb" 	- AMR wideband audio
				audio/mpeg" 	- MPEG1/2 audio layer III
				audio/mp4a-latm"- AAC audio (note, this is raw AAC packets, not packaged in LATM!)  这个不是latm打包 是裸ACC数据
				audio/vorbis" 	- vorbis audio
				audio/g711-alaw"- G.711 alaw audio
				audio/g711-mlaw"- G.711 ulaw audio


				MediaCodecInfo.CodecCapabilities 定义了颜色空间
				 配合   AudioCapabilities VideoCapabilities EncoderCapabilities 可确定某种格式是否支持

				MediaCodecInfo.CodecProfileLevel 定义了OMX中视频和音频的 profile
				// from OMX_AUDIO_AACPROFILETYPE
				public static final int AACObjectMain       = 1;
				public static final int AACObjectLC         = 2;
				public static final int AACObjectSSR        = 3;
				public static final int AACObjectLTP        = 4;
				public static final int AACObjectHE         = 5;
				public static final int AACObjectScalable   = 6;
				public static final int AACObjectERLC       = 17; (序号就是 MPEG-4 Audio Object Types中定义的 有些没有支持 所以序号不连续)
				public static final int AACObjectLD         = 23;
				public static final int AACObjectHE_PS      = 29;
				public static final int AACObjectELD        = 39;


			 * */

			AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, &bit_rate);
			AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_AAC_PROFILE, &acc_profile);
			AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channel);
			//AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, &frame_rate); // 0
			//AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_MASK, &channel_mask); // 0
			AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_IS_ADTS, &is_adts);
			AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &sample_rate);
			AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_DURATION, &duration);
			AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_MAX_INPUT_SIZE, &max_input_size);
			AMediaFormat_getInt32(format, "bit-width", &bit_witdh);




			 /**
				* 返回 0或者1
				* =1 如果AAC音频帧 AAC帧是与ADTS头 开始的话
				* 只能用于解码器 不能用于编码器
				*/

			ALOGD("acc_profile %d , channel %d , bit_witdh = %d , sample_rate = %d , is_adts = %d " ,
					acc_profile , channel , bit_witdh , sample_rate , is_adts   );
			AMediaExtractor_selectTrack(audio_extract, i);
			nwc->decorder = AMediaCodec_createDecoderByType(mime);
			s = AMediaFormat_toString(format);
			ALOGD("src track %d format: %s", i, s);

			/*
			 *  MTK平台
				acc_profile 2 , channel 2 , channel_mask = 0
				src track 1 format: mime: string(audio/mp4a-latm), durationUs: int64(79360000),
							channel-count: int32(2), 				通道数 = 2
							sample-rate: int32(48000), 				采样率 48k
							aac-profile: int32(2), 					Profile:ACC-LC
							bit-width: int32(16), 	  				宽度PCM16
							pcm-type: int32(1),						线性PCM ??? u-law a-law
							max-input-size: int32(564),				????
							csd-0: data}							???csd-0


				format changed to: mime: string(audio/raw), channel-count: int32(2),
								sample-rate: int32(48000), bit-width: int32(16),
								what: int32(1869968451)}

				LG 高通平台  (不同的平台,同一个视频源,MediaExtractor获取到的参数都不一样 比如"bit-width")
				src track 1 format: mime: string(audio/mp4a-latm), durationUs: int64(604786938),
							channel-count: int32(2),
							sample-rate: int32(44100),
							aac-profile: int32(2),
							max-input-size: int32(598),
							csd-0: data}

				format changed to: mime: string(audio/raw), channel-count: int32(2),
								sample-rate: int32(44100), what: int32(1869968451)

				小米5 高通平台
				src track 1 format: mime: string(audio/mp4a-latm), durationUs: int64(604786938)
							bit-rate: int32(0),
							channel-count: int32(2),
							sample-rate: int32(44100),
							aac-profile: int32(2),
							max-input-size: int32(65538),
							bitrate: int32(0),
							csd-0: data,
							file-format: string(video/mp4),
							aac-format-adif: int32(0)}

				format changed to: mime: string(audio/raw), channel-count: int32(2),
								sample-rate: int32(44100), bit-width: int32(16), what: int32(1869968451)}


			 */


			{
				//  H264 sps pps 参数  可以通过MediaExtra 得到的AMediaFormat得到 csd-0: data, csd-1: data
				//  ACC 也有类似参数 在  csd-0:data
				unsigned char * sps = NULL ; size_t sps_length = 0;
				unsigned char * pps = NULL;  size_t pps_length = 0;
				AMediaFormat_getBuffer(format, "csd-0",(void**)&sps , &sps_length);
				AMediaFormat_getBuffer(format, "csd-1",(void**)&pps , &pps_length);
				unsigned char* pBuffer = (unsigned char*)malloc( sps_length*5 + pps_length*5 + sps_length/4 +  pps_length/4  + 3 +  10/*barrier*/);
				unsigned char* p = pBuffer ;
				size_t loop = 0 ; int increased = 0 ;
				if(sps != NULL){
					for( loop = 0 ; loop < sps_length ; loop++  ){
						increased = snprintf( (char *)p , 6 ,"0x%02x,",  *(sps + loop) );
						if( increased < 0){
							ALOGE("dump csd-0 error");
							break;
						}
						p += 5 ;
						if( (loop + 1 ) % 4 == 0 ) { snprintf( (char *)p , 2 ,"\n"); p += 1;}
					}
				}else{
					ALOGE("csd-0 is null");
				}

				snprintf( (char *)p , 4 ,"\n-\n"); p += 3;
				if(pps != NULL){

					for( loop = 0 ; loop < pps_length ; loop++  ){
						increased = snprintf( (char *)p ,6 ,"0x%02x,",  *(pps + loop) );
						if( increased < 0){
							ALOGE("dump csd-1 error");
							break;
						}
						p += 5 ;
						if( (loop + 1 ) % 4 == 0 ) { snprintf( (char *)p , 2 ,"\n"); p += 1;}
					}
				}else{
					ALOGE("csd-1 is null");
				}
				ALOGD("dump csd-0 csd-1 start ");
				ALOGD("%s", pBuffer);
				ALOGD("dump csd-0 csd-1 end ");
				free(pBuffer);

				/*
				 * 	ACC音频没有csd-1 只有csd-0
					E/jni_codecaudio( 7038): csd-1 is null
					D/jni_codecaudio( 7038): dump csd-0 csd-1 start
					D/jni_codecaudio( 7038): 0x11,0x90,
					D/jni_codecaudio( 7038): -
					D/jni_codecaudio( 7038): dump csd-0 csd-1 end

				 * */

			}


			sawInputEOS = false;
			sawOutputEOS = false;
			media_status_t res;
#if USING_CUSTOM_FORMAT
			AMediaFormat_delete(format);
			format  = AMediaFormat_new();
			const int const_channel = 1 ;
			const int const_sample_rate = 44100 ;
			const int const_bit_witdh = 16 ;
			if(format == NULL){
				ALOGE("AMediaFormat_new fail\n");
				ALOGE("AMediaFormat_new fail\n");
				ALOGE("AMediaFormat_new fail\n");
				return NULL;
			}else{
				ALOGD("USING_CUSTOM_FORMAT");
			}

			AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, "audio/mp4a-latm");
			AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, const_channel); // 双通道
			AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, const_sample_rate ); // 采样率
			//AMediaFormat_setInt32(format, "bit-width", const_bit_witdh);
			//AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_AAC_PROFILE, 2); // ACC-LC only for encoder
			//AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_MAX_INPUT_SIZE, max_input_size );

#if USING_ESDS_CONFIG == 1
//			unsigned char esds[] = {
//					0x12,0x10
//			};
			int sampling_index = 0 ;
			for( ; sampling_index < sizeof(avpriv_mpeg4audio_sample_rates) ; sampling_index++ ){
				if( avpriv_mpeg4audio_sample_rates[sampling_index] == const_sample_rate  ){
					break;
				}
			}
			if( sampling_index == sizeof(avpriv_mpeg4audio_sample_rates) ){
				sampling_index = 	4 ; //default
				ALOGE("sample rate does NOT support , use default now");
			}

			uint16_t esds = (0xFFFF & (AOT_AAC_LC << 11)) |
							(0xFFFF & (sampling_index << 7)) |
							(0xFFFF & (const_channel << 3) )  ;

			esds = htons(esds);
			ALOGD("esds 0x%x " , esds);

			AMediaFormat_setBuffer(format , "csd-0" , &esds , sizeof(esds) );
#endif


#endif
			ALOGD("configure to: %s", AMediaFormat_toString(format));
#if USING_WINDOW
			res = AMediaCodec_configure(nwc->decorder, format, nwc->pSurfaceWindow, NULL, 0);
#else
			res = AMediaCodec_configure(nwc->decorder, format, NULL, NULL, 0);
#endif
			if (res != AMEDIA_OK) {
				ALOGE("AMediaCodec_configure res<%d>\n",res);
			}

			res = AMediaCodec_start(nwc->decorder);
			if (res != AMEDIA_OK) {
				ALOGE("AMediaCodec_start res<%d>\n",res);
			}

		}
		AMediaFormat_delete(format);
	}

	switch( bit_witdh)
	{
		case 8 :
			nwc->mBitWidth = AudioFormat_ENCODING_PCM_8BIT ;
			break;
		case 16:
			nwc->mBitWidth = AudioFormat_ENCODING_PCM_16BIT;
			break;
		default:
			ALOGE("unsupported bit witdh %d Force to 16bit" , bit_witdh);
			nwc->mBitWidth = AudioFormat_ENCODING_PCM_16BIT ;
			break;
	}

	switch( channel )
	{
		case 1 :
			nwc->mChannelMask = AudioFormat_CHANNEL_OUT_MONO ;
			break;
		case 2:
			nwc->mChannelMask = AudioFormat_CHANNEL_OUT_STEREO;
			break;
		default:
			ALOGE("unsupported channel count %d " , channel );
			nwc->mChannelMask = -1 ;
			break;
	}

	if( sample_rate > 0 ){
		nwc->mSampleRate = sample_rate ;
	}else{
		ALOGE("unsupported sample rate %d " , sample_rate);
		nwc->mSampleRate = -1 ;
	}


	if( nwc->mBitWidth == -1  || nwc->mChannelMask == -1 || nwc->mSampleRate == -1  ){
		ALOGE("invalid or unsupported ACC format");
		goto INVALID_FORMAT_OUT ;
	}

#if DECODEOUT_THREAD
	{
		int retTempTh = pthread_create(&nwc->mDecodeOuth, NULL, decodeACCOutput_thread, nwc );
		if( retTempTh < 0){
			ALOGD("decodeOuth264_thread create error!");
		}
	}
#endif

// esds必须配置 !!!
#if USING_CUSTOM_FORMAT
#if USING_ESDS_CONFIG == 0
	{
		ssize_t esds_index = AMediaCodec_dequeueInputBuffer(nwc->decorder, -1);
		size_t  esds_size = 0 ;
		uint8_t *buf = AMediaCodec_getInputBuffer(nwc->decorder, esds_index, &esds_size);
		unsigned char esds[] = {
				0x12,0x10
		};
		memcpy(buf , esds , sizeof(esds));
		AMediaCodec_queueInputBuffer(nwc->decorder,
			esds_index,
			0,
			sizeof(esds),
			0,
			AMEDIACODEC_CONFIGURE_FLAG_ENCODE /* 在小米上 也可以是  0 */);
	}
#endif
#endif

	while(
#if DECODEOUT_THREAD == 0
			(!sawOutputEOS ||
#endif
			!sawInputEOS
#if DECODEOUT_THREAD == 0
			)
#endif
			&& !nwc->forceClose ){
		ssize_t bufidx = -1;
		if (!sawInputEOS) {

			bufidx = AMediaCodec_dequeueInputBuffer(nwc->decorder, 2000);

				if (bufidx >= 0) {

					++sample_count ;

					size_t bufsize;// buffer的大小
					uint8_t *buf = AMediaCodec_getInputBuffer(nwc->decorder, bufidx, &bufsize);

		            ssize_t sampleSize = AMediaExtractor_readSampleData(audio_extract, buf, bufsize);
		            int64_t presentationTimeUs = AMediaExtractor_getSampleTime(audio_extract);


		            // ALOGD("[AAC] %02x %02x %02x %02x %d " , buf[0], buf[1] ,buf[2] ,buf[3] , sampleSize );
		            /*
		             * 没有任何ADTS/LATM header的raw aac data
		             * e.g:
		             * 	channel_pair_element()
						channel_pair_element()
						channel_pair_element()
						channel_pair_element()
					 *
		             * 最前面3bit是 element type:
		             * 0: SCE single channel element(codes a single audio channel)
		             * 1: CPE channel pair element(codes a stereo audio channel)
		             * ....
		             * 6: FIL fill element (pad space/extension data)
		             * ....
		             *
		             * 音源1: .3gp audio/mp4a-latm channel:1  sample-rate:44100 aac-profile:2 (LC) bit-width:16
		             * 		 csd-0:0x12,0x08 (codec special data  类似LATM头)
		             *			01 40 22 80
		             *			00 e2 36 28
		             *			00 e0 36 2c
		             *			00 f0 36 2d
		             *			00 f8 36 2f
		             *			01 02 36 2f
		             *			01 0e 36 2f
		             *			01 0e 36 2f
		             *			01 1e 36 2f
		             *			01 2a 36 2f
		             *			01 30 36 2f
		             *			01 32 36 2f
		             *			01 2e 36 2f
		             *			01 32 36 2f
		             *
		             *
		             * 音源2: .3gp audio/mp4a-latm channel:2  sample-rate:44100 aac-profile:2 (LC) bit-width:16
		             *			csd-0:0x12,0x10
		             *			da 00 4c 61
		             *			21 10 04 60
		             *			21 10 04 60
		             *			21 10 04 60
		             *			21 10 04 60
		             *			21 10 04 60
		             *			21 10 04 60
		             *			21 30 04 60
		             *			21 5e c6 3f
		             *			21 5e 6c 80
		             *			21 5e c6 80
		             *
		             *	音源3: .mp4 audio/mp4a-latm channel:2  sample-rate:44100 aac-profile:2 (LC) bit-width:16
		             *		 	csd-0:0x12,0x10
		             *		 	21 00 05 00
		             *		 	21 10 05 00
		             *		 	21 10 05 00
		             *		 	21 10 05 00
		             *		 	21 1a 93 75
		             *		 	21 1a 93 fd
		             *		 	21 0a 94 35
		             *		 	21 0a 94 35
		             *		 	21 0a 94 25
		             *
		             *
		             *	音源4: .acc audio/mp4a-latm channel:2  sample-rate:22050 aac-profile:2 (LC) bit-width:16
		             *			csd-0: 0x13,0x90,0x56,0xe5,0xa5,0x48,0x00,  ??? 为什么这么长的 ??? 从虾米音乐下载的acc文件???
		             *
		             *			src track 0 format: mime: string(audio/mp4a-latm), durationUs: int64(244134603),
		             *								bit-rate: int32(0),
		             *								channel-count: int32(2),
		             *								sample-rate: int32(22050), ??? 实际音频应该是44100 ???
		             *								encoder-delay: int32(2336), encoder-padding: int32(632),
		             *								aac-profile: int32(2), max-input-size: int32(65543),
		             *								bitrate: int32(0), csd-0: data,
		             *								file-format: string(video/mp4),
		             *								aac-format-adif: int32(0)}
		             *
		             *			format changed to: mime: string(audio/raw),
		             *							channel-count: int32(2),
		             *							sample-rate: int32(44100), ??? 为什么这个会变成44.1k???
		             *							bit-width: int32(16), what: int32(1869968451)}
		             *			21 1a d3 40
		             *			21 1a d3 ed
		             *			21 1a d3 bd
		             *			21 1a d3 f5
		             *			21 1a d4 1d
		             *			21 1a d4 3d
		             *
		             *	音源5: .mp3 	audio/mpeg channel:2  sample-rate:22050 aac-profile:2 (LC) bit-width:16
		             *			没有 csd-0 csd-1
		             *			src track 0 format: mime: string(audio/mpeg), durationUs: int64(14987979460513),
		             *								bit-rate: int32(128000),
		             *								channel-count: int32(2),
		             *								sample-rate: int32(44100),
		             *								encoder-delay: int32(576), encoder-padding: int32(1105),
		             *								max-input-size: int32(3000),
		             *								bitrate: int32(128000),
		             *								file-format: string(audio/mpeg)}
		             *
		             *			format changed to: mime: string(audio/raw),
		             *								channel-count: int32(2),
		             *								sample-rate: int32(44100),
		             *								bit-width: int32(16),
		             *								what: int32(1869968451)
		             *			ff fb 90 64
		             *			ff fb 92 64
		             *			ff fb 92 64
		             *			ff fb 92 64
		             *			ff fb 92 64
		             *			ff fb 92 64
		             *			ff fb 92 64   ADTS可以作为MPEG-2(mp3文件 audio/mpeg ) 或者 MPEG-4(acc文件 audio/?)
		             *
		             *
		             *
		             * */

		            if (sampleSize < 0) {
		                sampleSize = 0;
		                sawInputEOS = true;
		                ALOGD("file input EOS");
		            }


#if TEST_DISCARD_SAMPLE
		            // 跳过一段sample 不会影响后面sample的解码 而且跳过了之后就没有解码输出
		            if( sample_count > 500 && sample_count < 700 ){
		            	ALOGD("skil this sample");
		            	AMediaCodec_queueInputBuffer(nwc->decorder,
							bufidx,
							0,
							0,
							0,
							sawInputEOS ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM : 0);
		            }else{
						AMediaCodec_queueInputBuffer(nwc->decorder,
							bufidx,
							0,
							sampleSize,
							presentationTimeUs,
							sawInputEOS ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM : 0);
		            }
#else
					AMediaCodec_queueInputBuffer(nwc->decorder,
						bufidx,
						0,
						sampleSize,
						presentationTimeUs,
						sawInputEOS ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM : 0 );
#endif
					 AMediaExtractor_advance(audio_extract);

					 // ltam 只要一个sample 就会导致  output buffers changed
					 // 	 不过要推进去三个sample 才会导致 有一个解码输出
					 // if(sample_count == 3){
					 //   sleep(100000000);
					 // }

#if 0
					usleep(30000);
#endif
#if 0
					if( sample_count % 100 == 0 ){
						int sleepRand = rand()%10 ;
						ALOGD("sleep %d " ,  sleepRand );
						sleep(sleepRand);
					 }
#endif

	            	if (render_startup_time_us < 0)
					{
	            		ALOGD("clock_gettime = %lld gettimeofday = %lld\n" , systemmstime() , systemustime()/1000  );
	            		render_startup_time_us = systemustime() - presentationTimeUs;
	            	}
	            	int64_t delay_us = (render_startup_time_us + presentationTimeUs) - systemustime();
	            	if (delay_us > 0 )
					{
	            		//ALOGD("delay %ld" , delay_us);
	            		usleep(delay_us);
	           		}

				}else{
						//ALOGD("no input buffer right now, bufidx = %d " , bufidx);
				}
			}


#if DECODEOUT_THREAD

#else
			if (!sawOutputEOS) {
				AMediaCodecBufferInfo info;
				ssize_t status = AMediaCodec_dequeueOutputBuffer(decoder, &info, 0);
				if (status >= 0) {
						if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
							ALOGD("decode output EOS");
							sawOutputEOS = true;
						}
						// 显示???
						AMediaCodec_releaseOutputBuffer(decoder, status, info.size != 0); // render = true 如果configure时候配置了surface 就render到surface上
				} else if (status == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
					ALOGD("output buffers changed");
				} else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
					AMediaFormat *format = NULL;
					format = AMediaCodec_getOutputFormat(decoder);
					ALOGD("format changed to: %s", AMediaFormat_toString(format));
					AMediaFormat_delete(format);
				} else if (status == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
					//ALOGD("no output buffer right now, try again later ");
				} else {
					ALOGD("unexpected info code: %zd", status);
				}
			}
#endif

		}


	if( nwc->mDecodeOuth != -1 ){
		// 等待解码输出线程  获取晚最后一个数据
		pthread_join(nwc->mDecodeOuth , NULL);
	}

INVALID_FORMAT_OUT:
	if(nwc->decorder  != NULL){
		AMediaCodec_stop(nwc->decorder);
		AMediaCodec_delete(nwc->decorder);
	}

	/*End 	... */
	nwc->mExtractth = -1 ;
	nwc->jenv->SetLongField(nwc->java_obj_ref ,g_java_fields.context, 0);
	nwc->jenv->DeleteGlobalRef(nwc->java_obj_ref);

	ANativeWindow_release(nwc->pSurfaceWindow);

	JavaVM * jvm = nwc->jvm ;
	JNIEnv * env = nwc->jenv ;
	delete nwc;
	AttachDetachThread2JVM(jvm , false, &env);

	return NULL;
}


JNIEXPORT jboolean JNICALL native_decodeAudioFile(JNIEnv * env , jobject callback_jobj , jobject objSurface)
{
	char result[256];
	JNIContext* nwc = get_native_nwc(env,callback_jobj);
	if( nwc != NULL){
		// 重复调用
		ALOGE("native_decodeH264File Err it's running  !");
		return JNI_FALSE;
	}else{
		JNIContext* nwc = new JNIContext();
		nwc->mExtractth = -1 ;
		nwc->mDecodeOuth = -1 ;
		nwc->java_obj_ref = env->NewGlobalRef(callback_jobj);
		env->GetJavaVM(&nwc->jvm);
		nwc->pSurfaceWindow = ANativeWindow_fromSurface(env, objSurface);
		env->SetLongField(callback_jobj ,g_java_fields.context,  (long)nwc);

		// 创建线程
		int ret = pthread_create(&nwc->mExtractth, NULL, extractAAC_thread, nwc );
		if(  ret != 0 ){
			ANativeWindow_release(nwc->pSurfaceWindow);
			env->DeleteGlobalRef(nwc->java_obj_ref);
			env->SetLongField(nwc->java_obj_ref ,g_java_fields.context, 0);

			snprintf(result, sizeof(result), "Can Not Create Thread with %s %d!", strerror(errno),ret);
			ALOGE("%s",result);
			return JNI_FALSE;
		}
		return JNI_TRUE;
	}
}

JNIEXPORT void JNICALL native_forceClose(JNIEnv * env , jobject callback_jobj  )
{
	JNIContext* nwc = get_native_nwc(env,callback_jobj);
	if( nwc != NULL){

		if( nwc->mExtractth != -1 ){
			nwc->forceClose = true;
			pthread_join(nwc->mExtractth , NULL);
			nwc->mExtractth = -1 ;
			ALOGD("Child Thread Close");
		}
	}else{
		ALOGE("Native STOP Before");
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
    clazz = env->FindClass(JAVA_CLASS_PATH );
    if (clazz == NULL) {
		ALOGE("%s:Class Not Found" , JAVA_CLASS_PATH );
		return JNI_ERR ;
    }

    JNINativeMethod method_table[] = {
    	{ "decodeAudioFile","(Landroid/view/Surface;)Z", (void*)native_decodeAudioFile },
    	{ "decodeForceClose","()V", (void*)native_forceClose },
    };
	jniRegisterNativeMethods( env, JAVA_CLASS_PATH ,  method_table, NELEM(method_table)) ;

	// 查找Java对应field属性
    field fields_to_find[] = {
        { JAVA_CLASS_PATH , "mNativeContext",  "J", &g_java_fields.context },
    };

    find_fields( env , fields_to_find, NELEM(fields_to_find) );


	return JNI_VERSION_1_6 ;
}

