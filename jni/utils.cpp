// -------------------
#include <stdio.h>
#include <stdlib.h>
#include <semaphore.h>
#include <time.h>
#include <sys/time.h>

#include "utils.h"

#define LOG_TAG "streamer_sem"
#include "vortex.h"
#define STREAMER_TRACE ALOGD

void* streamer_sem_create(int initial_count, int max_count, const char* sem_name)
{
	sem_t *sem = NULL;

	sem = (sem_t*)malloc(sizeof(sem_t));
	if( 0 == sem_init(sem, 0, initial_count) )
	{
		STREAMER_TRACE((const char*)"streamer create sem success init = %d, sem_name = %s",initial_count, sem_name);
		return sem;
	}
	free(sem);
	STREAMER_TRACE((const char*)"streamer sem create error init = %d,sem_name=%s",initial_count, sem_name);
	return NULL;
}

int streamer_sem_destroy(void* handle)
{
	if( NULL != handle)
	{
		sem_destroy((sem_t*)handle);
		free(handle);
	}
	return (0);
}

int streamer_sem_get_count(void* handle, int *count)
{
	if( NULL != handle){
		if( 0 != sem_getvalue((sem_t *)handle, count)){
			*count = -1;
		}else{
			STREAMER_TRACE((const char*)"streamer sem count(%d) \n", *count);
		}
		return 0;
	}
	return (-1);
}

int streamer_sem_post(void* handle)
{
	if(NULL != handle)
	{
		STREAMER_TRACE((const char*)"streamer sem post  handle (%p)\n", handle);
        return sem_post((sem_t*)handle);
	}
	return (-1);
}

int streamer_sem_wait(void* handle, int time_out)
{
    if(NULL != handle){
        if( 0 == time_out){
        	return sem_wait((sem_t*)handle);
        }else{
        	struct timespec abs_timeout = {0};
        	abs_timeout.tv_sec = time(NULL) + time_out/1000;
        	return sem_timedwait((sem_t*)handle, &abs_timeout);
        }
    }
    return (-1);
}

// -------------------
