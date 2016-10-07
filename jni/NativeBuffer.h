/*
 * NativeBuffer.h
 *
 *  Created on: 2016年10月2日
 *      Author: hanlon
 */

#ifndef NATIVEBUFFER_H_
#define NATIVEBUFFER_H_

#include <list>
#include <pthread.h>

class ABufferManager ;

class ABuffer
{
public:
	ABuffer(ABufferManager* selfmanager, int cap );
	~ABuffer();
	int mDataType;
	int mTimestamp;
	int mCaptical ;
	int mActualSize ;
	void* mData;
	ABufferManager* mpSelfManager ;
	void release();
};

class ABufferManager
{
public:
	ABufferManager(int buffer_size, int count);
	~ABufferManager();
	ABuffer* obtainBuffer();
	void stop_offer();
	void releaseBuffer(ABuffer* buf);
private:
	//std::list<ABuffer*> mUsed ;
	std::list<ABuffer*> mAvailable ;
	std::list<ABuffer*> mTotal ;
	pthread_mutex_t mBufferMutex ;
	pthread_cond_t mBufferCond ;
	bool mStopOffer ;
};



#endif /* NATIVEBUFFER_H_ */
