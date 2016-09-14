#include "HWH264DecodeAndEncode.h"
#include <assert.h>
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <limits.h>
#include <time.h>
#include <string.h>
//#include <linux/err.h>		// ERR_PTR,PTR_ERR,IS_ERR

#include "utils.h"

#define LOG_TAG "streamer_jni"
#include "vortex.h"
#define STREAMER_TRACE ALOGD

#define DECODE_COST 0
#define ENCODE_THREAD 1
#define SAVE_ENCODE_FILE 0
#define USING_WINDOW 0

//#define TEST_FILE "/mnt/sdcard/test1080p60fps.mp4"
//#define TEST_FILE "/mnt/sdcard/480360.mp4"
//#define TEST_FILE "/mnt/sdcard/1920960.mp4"
//#define TEST_FILE "/mnt/sdcard/19201080.mp4" // 1080i
#define TEST_FILE "/mnt/sdcard/1080p60fps.mp4"


Hwh264DecodeAndEncode* Hwh264DecodeAndEncode::createNewCodec(int32_t width,
		int32_t height, ANativeWindow *display, char* url) {
	Hwh264DecodeAndEncode* pcodeandencode = new Hwh264DecodeAndEncode(width,
			height, display, url);
	return pcodeandencode;
}

Hwh264DecodeAndEncode::~Hwh264DecodeAndEncode() {
	if (NULL != mDecode) {
		AMediaCodec_stop(mDecode);
		AMediaCodec_delete(mDecode);
		STREAMER_TRACE("HWCODEC release. ..");
	}

	if (NULL != mEncode) {
		AMediaCodec_stop(mEncode);
		AMediaCodec_delete(mEncode);
		STREAMER_TRACE("HWEncode release. ..");
	}
	if (extract) {
		AMediaExtractor_delete(extract);
	}

	if( mFd >= 0 ){
		close(mFd);
		mFd = -1 ;
	}

	if (NULL != mutex) {
		streamer_sem_destroy(mutex);
		mutex = NULL;
	}

}


int64_t Hwh264DecodeAndEncode::systemnanotime() {
	timespec now;
	clock_gettime(CLOCK_MONOTONIC, &now);
	return now.tv_sec * 1000000000LL + now.tv_nsec;
}

Hwh264DecodeAndEncode::Hwh264DecodeAndEncode(int32_t width, int32_t height,
		ANativeWindow *display, char* url) :
		window(display), mDecode(NULL), renderstart(-1), flagEOF(false), mutex(
				NULL), mFd(-1), sawInputEOS(false), sawOutputEOS(false), extract(
				NULL), num(0), alltime(0) {
	mutex = streamer_sem_create(0, 1, "hwcodec_eof");
	createCodecFormat(width, height, display, url);
}

int Hwh264DecodeAndEncode::Decode(unsigned char * stream_buf,
		unsigned int stream_size, unsigned long presentationTimeUs) {
	// TO DO
	return 0 ;
}

void Hwh264DecodeAndEncode::setEOF(bool flag) {
	// TO DO
	return ;
}

void* Hwh264DecodeAndEncode::decode_thread(void* argv) {

	Hwh264DecodeAndEncode* pThis = (Hwh264DecodeAndEncode*) argv;
	AMediaCodecBufferInfo info;
	bool decode_done = false;
	int64_t clear_buffer = 0;
#ifdef  WAIT_FOR_INIT_DONE
	int try_again_timeout = 2 ;
#endif

	STREAMER_TRACE("decode_thread enter ");
	while (!decode_done) {
		ssize_t status = AMediaCodec_dequeueOutputBuffer(pThis->mDecode, &info, 3000000L); // 3s

		/*
		 * 	由于放第一帧开始解码的时候   需要等待一段时间才能有输入
		 * 	所以如果 Extractor提取线程 这时候还往解码器放入帧的话，就会导致解码器输入缓存满了
		 * 	即使后面解码的速度跟上了提取线程的速度(mp4文件每帧的时间戳)，由于之前的输入缓存还有需解码的帧，导致后面放进去需解码的帧延迟了
		 *
		 *
		 * 	另外如果有B帧的话，还需要继续推送NAL/编码帧进入才能 继续解码
		 */


		if (status >= 0) {

			if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
				STREAMER_TRACE("mDecode: output EOS");
				decode_done = true;
			}

			size_t outsize = 0;
			uint8_t* outbuffer = AMediaCodec_getOutputBuffer(pThis->mDecode, status, &outsize);



			pThis->mDecodeFrameCount++ ;
			if(pThis->mDecodeFrameCount % 100 == 0){
				struct timeval current_time;
				gettimeofday(&current_time, NULL);
				uint64_t nowms = current_time.tv_sec * 1000UL + current_time.tv_usec / 1000UL;
				STREAMER_TRACE("decode fps = %llu " , pThis->mDecodeFrameCount * 1000 / (nowms - pThis->mStartDecodeTimeMs) );

				/*
				 * 1080p 60fps 使用MediaCodec解码帧率 只能去到最大50fps(只解码)
				 * 1. 先用MediaExtra 再把数据给到  MediaCodec 到MediaServer解码 涉及进程间数据传输,MediaPlayer则取文件数据和解码都在同是MediaServer进程
				 * 2. MediaPlayer接口可能解码的时候会丢帧?丢帧应该会卡?
				 *
				 * */
			}
#if DECODE_COST
			STREAMER_TRACE("decode cost = %llu ms " , nowms - info.presentationTimeUs / 1000 );
#endif

#if ENCODE_THREAD
			{
				int try_timeout = 10 ;
				while(--try_timeout){ 	//	避免丢掉刚刚解码出来的那帧数据
					ssize_t bufidx2 = -1;
					bufidx2 = AMediaCodec_dequeueInputBuffer(pThis->mEncode, 20000);
					//STREAMER_TRACE("encode: buffer %zd", bufidx2);
					if (bufidx2 >= 0) {
						size_t bufsize2;
						uint8_t *buf2 = AMediaCodec_getInputBuffer(pThis->mEncode,
								bufidx2, &bufsize2);

						if (NULL != buf2) {

							if(bufsize2 < info.size){
								STREAMER_TRACE("encode: _getInputBuffer is small %d %d " ,
										bufsize2 , info.size );
							}
							memcpy(buf2, outbuffer, info.size);

							#if 0
							static int tempfd = -1 ;
							if( tempfd < 0 ){
								tempfd = open("/mnt/sdcard/yuv420p.yuv",O_CREAT | O_WRONLY | O_TRUNC );
								if(tempfd < 0 ){
									STREAMER_TRACE("can not save yuv420p.yuv");
								}
							}

							if(tempfd >= 0 ){
								write(tempfd, outbuffer ,info.size );
							}
							#endif

							AMediaCodec_queueInputBuffer(pThis->mEncode, bufidx2, 0,
									info.size, info.presentationTimeUs,
									decode_done == true ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM :0);

						}else{
							STREAMER_TRACE("encode: _getInputBuffer is NULL");
						}
						break; 			//	成功  把解码的帧 放到编码的buffer中
					} else if (bufidx2 == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
						//STREAMER_TRACE("encode: no input buffer right now try again try_timeout = %d " , try_timeout );
						continue ;
					} else {
						STREAMER_TRACE("encode: dequeueInput something wrong ,so return");
					}
				}
				if(try_timeout == 0) STREAMER_TRACE("lose one frame reason: no encode buffer to process");

			}
#endif
			AMediaCodec_releaseOutputBuffer(pThis->mDecode, status, info.size != 0 );

#ifdef  WAIT_FOR_INIT_DONE
			if(clear_buffer > 0 ){
				clear_buffer -- ;
				STREAMER_TRACE("Decode:clear_buffer = %lld , Extractor_buffer = %lld !" ,
									clear_buffer , pThis->mExtractFrameCount );
				if( clear_buffer == 0) {
					if( pThis->mCodecComplete != NULL ){
						STREAMER_TRACE("Decode:clear done!");
						streamer_sem_post(pThis->mCodecComplete) ;
					}
				}
			}
#endif
		} else if (status == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
			STREAMER_TRACE("Decode:  output buffers changed");
		} else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
			AMediaFormat *format = NULL;
			format = AMediaCodec_getOutputFormat(pThis->mDecode);
			STREAMER_TRACE("Decode: format changed to: %s", AMediaFormat_toString(format));
			AMediaFormat_delete(format);

			struct timeval current_time; memset(&current_time, 0, sizeof(struct timeval));
			gettimeofday(&current_time, NULL);
			pThis->mStartDecodeTimeMs = current_time.tv_sec * 1000UL + current_time.tv_usec / 1000UL;

#ifdef WAIT_FOR_INIT_DONE
			if( pThis->mCodecComplete != NULL  ){
				STREAMER_TRACE("Decode:Codec Initial Done!");
				streamer_sem_post(pThis->mCodecComplete) ;
			}

			if( pThis->mPauseExtractor != NULL){
				streamer_sem_wait(pThis->mPauseExtractor , 0 );
				streamer_sem_destroy(pThis->mPauseExtractor);
				pThis->mPauseExtractor = NULL;
				STREAMER_TRACE("Decode:Extract Pause Done!");
				clear_buffer = pThis->mExtractFrameCount ;
			}
#endif
		} else if (status == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
			STREAMER_TRACE("Decode: no output buffer right now");

#ifdef WAIT_FOR_INIT_DONE
			if( pThis->mCodecComplete != NULL && clear_buffer != 0 ){
				try_again_timeout -- ;
				if(try_again_timeout == 0 ){
					clear_buffer = 0 ;
					STREAMER_TRACE("Decode:clear done try_again_timeout!");
					streamer_sem_post(pThis->mCodecComplete) ;
				}
			}
#endif
		} else {
			STREAMER_TRACE("Decode: unexpected info code: %zd", status);
		}
	}
	STREAMER_TRACE("decode_thread exit ");

	/*
	 *
	 * 		Extractor 00 00 00 01 41 11394722 	<< 这时候提取出来放进去编码的
			decode done frametime 11127788
			decode done frametime 11144477
			Extractor 00 00 00 01 01 11361355   << 这一帧时间戳比较前 所以虽然提取得晚但是 decode出来的早
			Extractor 00 00 00 01 01 11378044
			decode done frametime 11161155
			Extractor 00 00 00 01 41 11428088
			decode done frametime 11177844
			Extractor 00 00 00 01 01 11411411
			decode done frametime 11194522
			Extractor 00 00 00 01 41 11461455
			decode done frametime 11211211
			Extractor 00 00 00 01 01 11444777
			decode done frametime 11227888
			Extractor 00 00 00 01 41 11511511
			Extractor 00 00 00 01 01 11478144
			decode done frametime 11244577
			decode done frametime 11261255
			Extractor 00 00 00 01 01 11494822
			decode done frametime 11277944
			Extractor 00 00 00 01 41 11544877
			decode done frametime 11294622
			Extractor 00 00 00 01 01 11528188
			decode done frametime 11311311
			Extractor 00 00 00 01 41 11594922
			decode done frametime 11327988
			decode done frametime 11344677
			Extractor 00 00 00 01 01 11561555
			decode done frametime 11361355			<< 放进去要晚 但是decode出来要早点
			decode done frametime 11378044
			Extractor 00 00 00 01 01 11578244
			Extractor 00 00 00 01 41 11611611
			Extractor 00 00 00 01 41 11628288
			decode done frametime 11394722			<< 这时候才取出来，间隔放进去了16帧(Extractor)


			解码器是将编码顺序的数据重新按照解码后的播放顺序输出的

			编码器是把数据根据解码需要的顺序重新排序保存的

			当然，以上情况只在有B帧的情况下才有用，否则只有IP帧的话解码和编码的顺序是一样的
			比如：解码后的数据是IBBP 那要将这个数据编码的话 编码后的数据保存的格式就是IPBB
	 * */
}

void* Hwh264DecodeAndEncode::encode_thread(void* argv) {

	Hwh264DecodeAndEncode* pThis = (Hwh264DecodeAndEncode*) argv;
	AMediaCodecBufferInfo encode_info;
	bool encode_done = false;

	STREAMER_TRACE("encode_thread enter ");
	while (!encode_done) {
		ssize_t status = AMediaCodec_dequeueOutputBuffer(pThis->mEncode,
				&encode_info, 3000000L); //3s
		if (status >= 0) {
			if (encode_info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
				STREAMER_TRACE("encode: output EOS");
				encode_done = true;
			}


			pThis->mEncodeFrameCount++ ;
			if(pThis->mEncodeFrameCount % 100 == 0){ // 64
				struct timeval current_time;
				gettimeofday(&current_time, NULL);
				uint64_t nowms = current_time.tv_sec * 1000UL + current_time.tv_usec / 1000UL;
				STREAMER_TRACE("encode fps = %llu " , pThis->mEncodeFrameCount * 1000 / (nowms - pThis->mStartEncodeTimeMs) );
			}

#if DECODE_COST
			STREAMER_TRACE("encode cost = %llu ms %llu" , nowms - encode_info.presentationTimeUs / 1000 , encode_info.presentationTimeUs );
#endif

#if SAVE_ENCODE_FILE
			if(-1 == pThis->mFd)
			{
				pThis->mFd = open("/mnt/sdcard/test.h264", O_WRONLY | O_TRUNC | O_CREAT );
				if ( pThis->mFd < 0 )
				{
					STREAMER_TRACE((const char*)"Could not open output YUV file\n");
				}
			}
			if( pThis->mFd >= 0 ){
				size_t outsize2 = 0;
				uint8_t* outbuffer2 = AMediaCodec_getOutputBuffer(pThis->mEncode, status,&outsize2);

				//STREAMER_TRACE("NAL type %d " , outbuffer2[4] & 0x1F );

				write(pThis->mFd, outbuffer2, encode_info.size );
			}
#endif

			AMediaCodec_releaseOutputBuffer(pThis->mEncode, status, false);
			// false configure的时候没有定义surface来render

		} else if (status == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
			STREAMER_TRACE("encode:  buffers changed");
		} else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
			AMediaFormat *format = NULL;
			format = AMediaCodec_getOutputFormat(pThis->mEncode);
			int rate = 0 ;
			AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, &rate);
			STREAMER_TRACE("encode: format changed to: %s rate %d " , AMediaFormat_toString(format), rate );
			AMediaFormat_delete(format);


			struct timeval current_time; memset(&current_time, 0, sizeof(struct timeval));
			gettimeofday(&current_time, NULL);
			pThis->mStartEncodeTimeMs = current_time.tv_sec * 1000UL + current_time.tv_usec / 1000UL;


		} else if (status == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
			STREAMER_TRACE("encode: no output buffer right now");
		} else {
			STREAMER_TRACE("encode: unexpected info code: %zd", status);
		}
	}
	STREAMER_TRACE("encode_thread exit ");
}

void Hwh264DecodeAndEncode::createCodecFormat(int width, int height,
		ANativeWindow *window, char* url) {

	int rate = 0;



	int fd = open(TEST_FILE, O_RDONLY);
	if (fd < 0) {
		STREAMER_TRACE("failed: %d (%s)", fd, strerror(errno));
		STREAMER_TRACE("@@@ createFromatFromMediafile return\n");
		return;
	}

	extract = AMediaExtractor_new();
	media_status_t err = AMediaExtractor_setDataSourceFd(extract, fd, 0,
			LONG_MAX);
	close(fd);
	if (err != AMEDIA_OK) {
		STREAMER_TRACE("setDataSource error: %d", err);
		return;
	}

	int numtracks = AMediaExtractor_getTrackCount(extract);
	for (int i = 0; i < numtracks; i++) {
		AMediaFormat *format = AMediaExtractor_getTrackFormat(extract, i);
		if (format == NULL) {
			STREAMER_TRACE("Can Not Get AMediaFormat for track %d " , i );
			continue;
		}
		const char *s = AMediaFormat_toString(format);
		//STREAMER_TRACE("1111track %d format: %s", i, s);
		const char *mime;
		if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
			STREAMER_TRACE("no mime type");
			return;
		} else if (!strncmp(mime, "video/", 6)) {

			AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_WIDTH, &width);
			AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_HEIGHT, &height);
			AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, &rate);

			/*	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_FORMAT, 21);
			 *
			 	 设置了window给codec 颜色空间固定为2130706433(Vendor自定义) 不能修改 , 而且通过AMediaCodec_getOutputBuffer拿出来的都是info.size = 8 不像是一帧图片
				没有设置的话 否则是19  也是不能通过KEY_COLOR_FORMAT来修改 固定19  也不能设置为2130706433(Vendor自定义)
				可以查看format change
				frameworks/native/include/media/openmax/OMX_IVCommon.h



				*/
			STREAMER_TRACE("width %d , height %d , rate = %d " , width , height , rate);
			AMediaExtractor_selectTrack(extract, i);
			mDecode = AMediaCodec_createDecoderByType(mime);
			s = AMediaFormat_toString(format);
			STREAMER_TRACE("src track %d format: %s", i, s);
			sawInputEOS = false;
			sawOutputEOS = false;
			media_status_t res;
#if USING_WINDOW
			res = AMediaCodec_configure(mDecode, format, window, NULL, 0);
#else
			res = AMediaCodec_configure(mDecode, format, NULL, NULL, 0);
#endif
			if (res != AMEDIA_OK) {
				STREAMER_TRACE("AMediaCodec_configure res<%d>\n",res);
			}

			res = AMediaCodec_start(mDecode);
			if (res != AMEDIA_OK) {
				STREAMER_TRACE("AMediaCodec_start res<%d>\n",res);
			}

		}
		AMediaFormat_delete(format);
	}

#if ENCODE_THREAD
	{
		mEncode = AMediaCodec_createEncoderByType("video/avc");
		if (NULL == mEncode) {
			STREAMER_TRACE("HWENCODE GET encode fail\n");
			return;
		}

		AMediaFormat *format2 = AMediaFormat_new();
		AMediaFormat_setString(format2, AMEDIAFORMAT_KEY_MIME, "video/avc");
		AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_WIDTH, width);
		AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_HEIGHT, height);
		AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_FRAME_RATE, 60);
		AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_COLOR_FORMAT, 19);// 不能是 2130706433 (如果MeidaCodec config了Window 输出是这种格式)

		/* frameworks/av/media/libstagefright/colorconversion/ColorConverter.cpp
		 *	YUV422/YUV420 ->RGB565
		 *	颜色空间
		 *	由 		OMX_COLOR_FormatCbYCrY OMX_COLOR_FormatYUV420Planar OMX_COLOR_FormatYUV420SemiPlanar
		 *	 转换成 	OMX_COLOR_Format16bitRGB565
		 */
		AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_BIT_RATE, 2048 * 2048);
		AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 1);
		const char *s2 = AMediaFormat_toString(format2);
		STREAMER_TRACE("encode format: %s", s2);
		media_status_t res2;
		res2 = AMediaCodec_configure(mEncode, format2, NULL, NULL, 1);
		if (res2 != AMEDIA_OK) {
			STREAMER_TRACE("HWENCODE AMediaCodec_configure res<%d>\n",res2);
		}

		res2 = AMediaCodec_start(mEncode);
		if (res2 != AMEDIA_OK) {
			STREAMER_TRACE("HWENCODE AMediaCodec_start res<%d>\n",res2);
		}

		if (NULL != format2) {
			AMediaFormat_delete(format2);
		} else {
			STREAMER_TRACE("HWENCODE format id NULL..\n");
		}
	}
#endif


	STREAMER_TRACE("Extractor: Thread Enter ");
	bool extract_end = false ;
	mStartPlayNs = -1 ;
	mDecodeFrameCount = 0 ;
	mEncodeFrameCount = 0 ;
	mExtractFrameCount = 0 ;
	mStartEncodeTimeMs = 0 ;
	mStartDecodeTimeMs = 0 ;


#ifdef WAIT_FOR_INIT_DONE
	mCodecComplete = streamer_sem_create(0, 1, "Decode Init Complete");
	mPauseExtractor = streamer_sem_create(0, 1, "Pause Extractor");
#endif
	int ret = pthread_create(&this->mDecodeTh, NULL, Hwh264DecodeAndEncode::decode_thread, this);
	if (ret != 0) {
		STREAMER_TRACE("pthread_create mDecodeTh Err!");
		// Fix Me:
		return;
	}


#if ENCODE_THREAD
	ret = pthread_create(&this->mEncodeTh, NULL, Hwh264DecodeAndEncode::encode_thread, this);
	if (ret != 0) {
		STREAMER_TRACE("pthread_create mEncodeTh Err!");
		// Fix Me:
		return;
	}
#endif



	while (!extract_end) {
		ssize_t bufidx = -1;
		bufidx = AMediaCodec_dequeueInputBuffer(mDecode, 20000 ); //3000000L 3s
		if (bufidx >= 0) {
			size_t bufsize = 0;
			uint8_t *buf = AMediaCodec_getInputBuffer(mDecode, bufidx,
					&bufsize);
			ssize_t sampleSize = AMediaExtractor_readSampleData(extract, buf,
					bufsize);
			if (sampleSize < 0) {
				sampleSize = 0;
				extract_end = true;
				STREAMER_TRACE("file EOS");
			}

			int64_t sampleTimeUs = AMediaExtractor_getSampleTime(extract);
			//int nalu_type =  buf[4] & 0x1F ;
			//STREAMER_TRACE("Extractor %02x %02x %02x %02x %02x %llu" , buf[0] , buf[1] , buf[2]  , buf[3]  , buf[4] , sampleTimeUs );


			/* 有些mp4 全部都是:
				00 00 00 01 09
				从mp4文件读取  所有的sample都是 nalu_type = 9 ??
				需要6帧以上才能解码出来

				有些mp4:
				00 00 00 01 65(IDR)
				00 00 00 01 41  后面很多41 只有一两个是65(IDR)
				需要4帧就可以解码出来

				有些mp4:
				00 00 00 01 06(SEI)
				00 00 00 01 41  后面很多41 只有一两个是65(IDR)
				需要5帧就可以解码出来

				有些mp4:
				00 00 00 01 65(IDR)
				00 00 00 01 41
				00 00 00 01 01
				00 00 00 01 01
				00 00 00 01 41	隔两帧 出现41 也会偶尔出现65 IDR
				00 00 00 01 01
				00 00 00 01 01

				总结:不同的MP4文件/容器  需要预先堆放到不同数量的解码帧 到 MediaCodec中才能有解码输出

				这里就会有缓存在MediaCodec中

				如果解码的帧率 大于  输入/接收的帧率  那么延时就会逐渐减低，否则延时就有 MediaCodec缓存数目*帧率倒数 那么大

				*/

#if DECODE_COST
			struct timeval extract_time ;
			gettimeofday(&extract_time, NULL);
			uint64_t extractTimeUs = extract_time.tv_sec * 1000UL * 1000UL + extract_time.tv_usec;
			STREAMER_TRACE("Extractor %llu" , extractTimeUs);
#endif

			AMediaCodec_queueInputBuffer(mDecode, bufidx, 0, sampleSize,
#if DECODE_COST
					extractTimeUs,
#else
					sampleTimeUs,
#endif
					extract_end ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM : 0);

			AMediaExtractor_advance(extract);

#ifdef WAIT_FOR_INIT_DONE
			mExtractFrameCount ++;
			// 等待解码器ready
			if(mCodecComplete != NULL){
				ret = streamer_sem_try_wait(mCodecComplete);// 非阻塞等待解码器完成初始化
				STREAMER_TRACE("ret = %d " , ret );
				if( ret == 0  ){
					streamer_sem_post(mPauseExtractor);		// 握手:告诉decode_thread已经停止放数据到解码器
					STREAMER_TRACE("Extractor: wait done");
					streamer_sem_wait(mCodecComplete, 0) ; 	// 再次等待消化缓存完毕
					streamer_sem_destroy(mCodecComplete);
					mCodecComplete = NULL;
					STREAMER_TRACE("Extractor:clear Done");

					int64_t presentationNano = sampleTimeUs * 1000;
			       	int64_t now = systemnanotime() ;
					mStartPlayNs = now  -  presentationNano;
				}else if (ret < 0 && ret !=  -EAGAIN){
					STREAMER_TRACE("Extractor:Semaphore error!");
				}
			}
#endif
			// TO DO 控制速度 !!! 控制从文件获取放到解码器的速度
#if 1
            int64_t presentationNano = sampleTimeUs * 1000;
            int64_t now = systemnanotime() ;
            if (mStartPlayNs < 0) {
            	mStartPlayNs = now  -  presentationNano;
            }
            int64_t delay = (mStartPlayNs + presentationNano) - now ;
            if (delay > 0  && !extract_end  ) {
            	ALOGD("sleep %ld us " , delay/1000);
                usleep(delay / 1000);
            }
#endif

            /*  1080i 隔行扫描的  获取的时间戳有点特别 尚未知道原因  解码之后 奇偶场会合并成一帧   容器里面会提示先发奇还是偶场 而且会有原始帧率和帧率两个概念
             * 	1080p 逐行扫描的  时间戳是按照帧率倒数隔开的 比如30fps: 33333 66666 100000
            	更正 即使是1080p  progressive的  也会发现 提取的时间戳 不是递增的  但是同一Nal type是递增的

            	Extractor 00 00 00 01 65 16688
				Extractor 00 00 00 01 41 33377
				Extractor 00 00 00 01 41 83422
				Extractor 00 00 00 01 01 50055
				Extractor 00 00 00 01 01 66744
				Extractor 00 00 00 01 41 133477   所有同样41:Nal_type = 1 ref = 2 ? P帧？ 时间戳都是递增的
				Extractor 00 00 00 01 01 100111
				Extractor 00 00 00 01 01 116788   所有同样01:Nal_type = 1 ref = 0 ? B帧? 时间戳是递增的
				Extractor 00 00 00 01 41 183522                  <<< 提前了是因为??后面的B帧依赖这个P帧?
				Extractor 00 00 00 01 01 150155  但是41和01的时间戳不是递增的
				Extractor 00 00 00 01 01 166844
				Extractor 00 00 00 01 41 233577 ??? H264帧间压缩 前后帧关联???
				Extractor 00 00 00 01 01 200211

				decode done frametime 16688
				decode done frametime 33377
				decode done frametime 50055
				decode done frametime 66744
				decode done frametime 83422
				decode done frametime 100111
				decode done frametime 116788
				decode done frametime 133477
				decode done frametime 150155		解码出来的时间戳都是按时间递增的

             * */


		} else {
			//STREAMER_TRACE("Decode: get inputbuffer fail <%d>\n",bufidx);
		}

	}

	pthread_join(this->mDecodeTh, NULL);
#if ENCODE_THREAD
	pthread_join(this->mEncodeTh, NULL);
#endif
	STREAMER_TRACE("Extractor: Thread Exit ");

}

