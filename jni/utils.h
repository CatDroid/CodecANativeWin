#ifndef __UTILS___
#define __UTILS___

void* streamer_sem_create(int initial_count, int max_count, const char* sem_name);
int streamer_sem_destroy(void* handle);
int streamer_sem_get_count(void* handle, int *count);
int streamer_sem_post(void* handle);
int streamer_sem_wait(void* handle, int time_out);
int streamer_sem_try_wait(void* handle);
#endif//__UTILS___
