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

#include "utils.h"

#define LOG_TAG "streamer_jni"
#include "vortex.h"
#define STREAMER_TRACE ALOGD

#define ENCODE_THREAD 1

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
				NULL), fp_out(NULL), sawInputEOS(false), sawOutputEOS(false), extract(
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

	STREAMER_TRACE("decode_thread enter ");
	while (!decode_done) {
		ssize_t status = AMediaCodec_dequeueOutputBuffer(pThis->mDecode, &info, 3000000L); // 3s
		if (status >= 0) {

			if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
				STREAMER_TRACE("mDecode: output EOS");
				decode_done = true;
			}

			size_t outsize = 0;
			uint8_t* outbuffer = AMediaCodec_getOutputBuffer(pThis->mDecode, status, &outsize);

			STREAMER_TRACE("decode done frametime %llu" ,info.presentationTimeUs );

			pThis->mDecodeFrameCount++ ;
			struct timeval current_time; memset(&current_time, 0, sizeof(struct timeval));
			gettimeofday(&current_time, NULL);
			uint64_t nowms = current_time.tv_sec * 1000UL + current_time.tv_usec / 1000UL;
			STREAMER_TRACE("decode fps = %llu " , pThis->mDecodeFrameCount * 1000 / (nowms - pThis->mStartDecodeTimeMs) );

#if ENCODE_THREAD
			{
				int try_timeout = 10 ;
				while(--try_timeout){ 	//	避免丢掉刚刚解码出来的那帧数据
					ssize_t bufidx2 = -1;
					bufidx2 = AMediaCodec_dequeueInputBuffer(pThis->mEncode, 30000);
					//STREAMER_TRACE("encode: buffer %zd", bufidx2);
					if (bufidx2 >= 0) {
						size_t bufsize2;
						uint8_t *buf2 = AMediaCodec_getInputBuffer(pThis->mEncode,
								bufidx2, &bufsize2);

						if (NULL != buf2) {
							memcpy(buf2, outbuffer, info.size);

							int64_t presentus = info.presentationTimeUs;
							AMediaCodec_queueInputBuffer(pThis->mEncode, bufidx2, 0,
									info.size, presentus,
									decode_done == true ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM :0);

						}
						break; 			//	成功把解码的帧 放到编码的buffer中
					} else if (bufidx2 == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
						STREAMER_TRACE("encode: no input buffer right now try again try_timeout = %d " , try_timeout );
						continue ;
					} else {
						STREAMER_TRACE("encode: dequeueInput something wrong ,so return");
					}
				}
				if(try_timeout == 0) STREAMER_TRACE("lose one frame reason: no encode buffer to process");

			}
#endif
			AMediaCodec_releaseOutputBuffer(pThis->mDecode, status, info.size != 0 );

		} else if (status == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
			STREAMER_TRACE("Decode:  output buffers changed");
		} else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
			AMediaFormat *format = NULL;
			format = AMediaCodec_getOutputFormat(pThis->mDecode);
			STREAMER_TRACE("Decode: format changed to: %s", AMediaFormat_toString(format));
			AMediaFormat_delete(format);
		} else if (status == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
			STREAMER_TRACE("Decode: no output buffer right now");
		} else {
			STREAMER_TRACE("Decode: unexpected info code: %zd", status);
		}
	}
	STREAMER_TRACE("decode_thread exit ");
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

			//struct timeval current_time; memset(&current_time, 0, sizeof(struct timeval));
			//gettimeofday(&current_time, NULL);
			//uint64_t nowus = current_time.tv_sec * 1000UL * 1000UL
			//		+ current_time.tv_usec;
			//uint64_t beforeus = encode_info.presentationTimeUs;
			//uint64_t diffus = nowus - beforeus;
			//STREAMER_TRACE("Total cost %llu us  %llu ms" , diffus , diffus/1000);

			pThis->mEncodeFrameCount++ ;
			struct timeval current_time; memset(&current_time, 0, sizeof(struct timeval));
			gettimeofday(&current_time, NULL);
			uint64_t nowms = current_time.tv_sec * 1000UL + current_time.tv_usec / 1000UL;
			STREAMER_TRACE("encode fps = %llu " , pThis->mEncodeFrameCount * 1000 / (nowms - pThis->mStartEncodeTimeMs) );


#if 0
			if(NULL == fp_out)
			{
				fp_out = fopen("/mnt/sdcard/test.264", "wb");
				if (!fp_out)
				{
					STREAMER_TRACE((const char*)"Could not open output YUV file\n");
				}
			}
			size_t outsize2 = 0;
			uint8_t* outbuffer2 = AMediaCodec_getOutputBuffer(pThis->mEncode, status,&outsize2);
			fwrite(outbuffer2, 1, info2.size, fp_out);
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
			STREAMER_TRACE("encode:  rate %d , format changed to: %s ", rate , AMediaFormat_toString(format));
			AMediaFormat_delete(format);
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
	int fd = open("/mnt/sdcard/1920960.mp4", O_RDONLY);
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
			STREAMER_TRACE("width %d , height %d , rate = %d " , width , height , rate);
			AMediaExtractor_selectTrack(extract, i);
			mDecode = AMediaCodec_createDecoderByType(mime);
			s = AMediaFormat_toString(format);
			STREAMER_TRACE("src track %d format: %s", i, s);
			sawInputEOS = false;
			sawOutputEOS = false;
			media_status_t res;
			//res = AMediaCodec_configure(mDecode, format, window, NULL, 0);
			res = AMediaCodec_configure(mDecode, format, NULL, NULL, 0);
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
		AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_COLOR_FORMAT, 19);
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

	STREAMER_TRACE("Extractor: Thread Enter ");
	bool extract_end = false ;
	mStartPlayNs = -1 ;
	mDecodeFrameCount = 0 ;
	mEncodeFrameCount = 0 ;
	struct timeval current_time; memset(&current_time, 0, sizeof(struct timeval));
	gettimeofday(&current_time, NULL);
	mStartDecodeTimeMs = current_time.tv_sec * 1000UL + current_time.tv_usec / 1000UL;
	mStartEncodeTimeMs = mStartDecodeTimeMs ;

	while (!extract_end) {
		ssize_t bufidx = -1;
		bufidx = AMediaCodec_dequeueInputBuffer(mDecode, 3000000L); // 3s
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
			//struct timeval extract_time ; memset(&extract_time,0,sizeof(extract_time) );
			//gettimeofday(&extract_time, NULL);
			//uint64_t presentationTimeUs = extract_time.tv_sec * 1000UL * 1000UL + extract_time.tv_usec;
			//STREAMER_TRACE("presentationTimeUs = %llu " , presentationTimeUs);

			STREAMER_TRACE("Extractor %llu" , sampleTimeUs);

			AMediaCodec_queueInputBuffer(mDecode, bufidx, 0, sampleSize,
					sampleTimeUs,
					extract_end ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM : 0);

			AMediaExtractor_advance(extract);

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
            	D/streamer_jni(14148): Extractor 0
				D/streamer_jni(14148): Extractor 16683
				D/streamer_jni(14148): Extractor 100100
				D/streamer_jni(14148): Extractor 116783
				D/streamer_jni(14148): Extractor 33366
				D/streamer_jni(14148): Extractor 50050
				D/streamer_jni(14148): Extractor 66733
				D/streamer_jni(14148): Extractor 83416
				D/streamer_jni(14148): Extractor 200200
				D/streamer_jni(14148): Extractor 216883
				D/streamer_jni(14148): Extractor 133466
				D/streamer_jni(14148): Extractor 150150
				D/streamer_jni(14148): Extractor 166833
				D/streamer_jni(14148): Extractor 183516
				D/streamer_jni(14148): Extractor 300300
				D/streamer_jni(14148): Extractor 316983
				D/streamer_jni(14148): Extractor 233566
				D/streamer_jni(14148): Extractor 250250
				D/streamer_jni(14148): Extractor 266933
				D/streamer_jni(14148): Extractor 283616
             * */


		} else {
			STREAMER_TRACE("Decode: get inputbuffer fail <%d>\n",bufidx);
		}

	}

	pthread_join(this->mDecodeTh, NULL);
#if ENCODE_THREAD
	pthread_join(this->mEncodeTh, NULL);
#endif
	STREAMER_TRACE("Extractor: Thread Exit ");

}

