/*
 * native_msg.h
 *
 *  Created on: 2016年9月29日
 *      Author: rd0394
 */

#ifndef NATIVE_MSG_H_
#define NATIVE_MSG_H_

#define MEDIA_PREPARED 				 	1
#define MEDIA_SEEK_COMPLETE				2
#define MEDIA_INFO_PLAY_COMPLETED		3
#define MEDIA_INFO_PAUSE_COMPLETED		4
#define MEDIA_BUFFER_DATA				5
#define MEDIA_TIME_UPDATE				6
#define MEDIA_H264_SAMPLE				7


#define THREAD_LOOP_END 				999
#define	THREAD_STARTED					(THREAD_LOOP_END + 1)
#define	THREAD_STOPED					(THREAD_LOOP_END + 2)
#define	THREAD_EXCEPTION				(THREAD_LOOP_END + 3)
#endif /* NATIVE_MSG_H_ */
