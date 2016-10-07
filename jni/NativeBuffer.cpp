/*
 * NativeBuffer.cpp
 *
 *  Created on: 2016年10月2日
 *      Author: hanlon
 */

#include "NativeBuffer.h"

#define LOG_TAG "abuffer"
#include "vortex.h"

ABuffer::ABuffer(ABufferManager* selfmanager , int cap ):
	mDataType(-1) , mTimestamp(-1), mCaptical(-1) , mActualSize (-1), mData(NULL),
	mpSelfManager(selfmanager)
{
	mData = malloc(cap);
	if( mData == NULL ) {
		ALOGE("NOT MORE MEMORY !");
		mCaptical = -1 ;
	}else{
		mCaptical = cap ;
	}

}

ABuffer::~ABuffer()
{
	if(mData!=NULL) free(mData );
}

void ABuffer::release()
{
	mpSelfManager->releaseBuffer(this);
}


ABufferManager::ABufferManager(const int buffer_size, const int count):
														mStopOffer(false)
{
	pthread_mutex_init(&mBufferMutex, NULL );
	pthread_cond_init(&mBufferCond , NULL);

	for(int i = 0 ; i < count ; i++){
		ABuffer* p = new ABuffer(this, buffer_size);
		this->mAvailable.push_back(p);
		this->mTotal.push_back(p);
	}

	ALOGI("dump total buffer address : ");
	for (std::list<ABuffer*>::iterator i = this->mTotal.begin(); i != this->mTotal.end(); i++)
	{
		ALOGI("[ptr:%p cap:%d]" , (*i)->mData  , (*i)->mCaptical );
	}
}

ABufferManager::~ABufferManager()
{
	pthread_mutex_lock(&mBufferMutex);
	ALOGI("~ABufferManage dump total buffer address : ");
	for (std::list<ABuffer*>::iterator i = this->mTotal.begin(); i != this->mTotal.end(); i++)
	{
		ALOGI("[ptr:%p cap:%d]" , (*i)->mData  , (*i)->mCaptical );
	}

	ALOGI("~ABufferManage dump avaible buffer address : ");
	for (std::list<ABuffer*>::iterator i = this->mAvailable.begin(); i != this->mAvailable.end(); i++)
	{
		ALOGI("[ptr:%p cap:%d]" , (*i)->mData  , (*i)->mCaptical );
	}

	unsigned int collect = this->mTotal.size() ;
	ALOGI("~ABufferManage total in list %d " , collect);
	while(this->mAvailable.size() != collect ){
		pthread_cond_wait(&mBufferCond , &mBufferMutex );// 如果在jni接口 可能挂死main UI线程 导致无法释放buffer
	}

	ABuffer* buf = NULL;
	while( ! this->mTotal.empty() ){
		buf = this->mTotal.front();
		this->mTotal.pop_front();
		delete buf ;
	}

	pthread_mutex_unlock(&mBufferMutex);

	pthread_mutex_destroy(&mBufferMutex);
	pthread_cond_destroy(&mBufferCond);
	ALOGI("~ABufferManager done !");
}

void ABufferManager::stop_offer()
{
	pthread_mutex_lock(&mBufferMutex);
	mStopOffer = true ;
	pthread_cond_signal(&this->mBufferCond);
	pthread_mutex_unlock(&mBufferMutex);
}


ABuffer* ABufferManager::obtainBuffer()
{
	ABuffer* buf = NULL;
	pthread_mutex_lock(&mBufferMutex);
	if(!mStopOffer) {
		do{
			if( this->mAvailable.empty() == false )
			{
				buf = this->mAvailable.front();
				this->mAvailable.pop_front();
			}else{
				ALOGD("no more buffer , wait for buffer ");
				pthread_cond_wait(&mBufferCond , &mBufferMutex );
				ALOGD("no more buffer , get one buffer ");
			}
		}while(buf == NULL && mStopOffer == false);
	}else{
		ALOGI("obtainBuffer fail because stop offerring!");
	}
	pthread_mutex_unlock(&mBufferMutex);
	return buf;
}

void ABufferManager::releaseBuffer(ABuffer* buffer )
{
	ALOGI("releaseBuffer %p! " , buffer);
	pthread_mutex_lock(&mBufferMutex);
	bool old_empty = this->mAvailable.empty() ;
	this->mAvailable.push_back(buffer);
	if( old_empty || mStopOffer ){
		pthread_cond_signal(&this->mBufferCond);
	}
	pthread_mutex_unlock(&mBufferMutex);
}






