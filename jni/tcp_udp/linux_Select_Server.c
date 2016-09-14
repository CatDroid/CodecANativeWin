#include<stdio.h>
#include<errno.h>
#include<stdlib.h>
#include<unistd.h>
#include<string.h>

#include<sys/socket.h>
#include<arpa/inet.h>
#include<pthread.h> 

#define PORT "10000"
#define REMOTE_IP "192.168.43.1"

#define MAX_QUEUE 10
#define MAX_CLIENT 10
#define WELCOME "welcome to server 2012\n"
#define MAX_PACKET_SIZE 1456
#define FREQUENCY 15

// 下标代表 所有客户的socket
int isconnect[MAX_CLIENT];
// 服务器的socket
int sock;

void scan(void* arg){
	char result[5];
	int fd;
	while(1){
		printf("Horizon Server\n");
		printf("IF You want to Exit,Please Enter \"exit\"\n");
		fgets(result,sizeof(result),stdin);
		if(!strncasecmp(result,"exit",4)){
			// .............
			for(fd=sock+1;fd<MAX_CLIENT;fd++){
				if(isconnect[fd]){
					write(fd,"exit",sizeof("exit"));
					close(fd);
				}
			}
			break;
		}
	}
	close(sock);
	printf("scan thread over\n");
	exit(0); // 进程关闭
}

int main(int argc,char**argv){
	//用于循环
	int fd;
	
	// 用于 bind
	struct sockaddr_in server_addr;
	
	// 用于select
	fd_set fds;
	int change;
	
	// 用于accept
	struct sockaddr_in client_addr;
	//int len;
	socklen_t len;
	int sock_client;
	
	// 用于read write
	unsigned char* rbuf = (unsigned char*)malloc(MAX_PACKET_SIZE);
	memset( rbuf, 0, MAX_PACKET_SIZE );
	//char wbuf[256]; strcpy(wbuf,"ok");
	int ret;

	sock=socket(AF_INET,SOCK_STREAM,0);
	printf("%d\n",sock);
	if(sock==-1){
		perror("socket error");
		exit(1);
	}else{
		printf("socket success\n");
	}
	bzero(&server_addr,sizeof(struct sockaddr_in));
	server_addr.sin_family=AF_INET;// AF_INET AF_INET6
	server_addr.sin_port=htons(atoi(PORT));
	server_addr.sin_addr.s_addr= htonl(INADDR_ANY) ;
			// INADDR_NONE = 255.255.255.255 ; // inet_addr(REMOTE_IP);

	if( bind(sock,(struct sockaddr*)&server_addr,sizeof(struct sockaddr)) ==-1){
		perror("bind error");
		exit(1);
	}else{
		printf("bind success\n");
	}
	
	if( listen(sock,MAX_QUEUE) ==-1){
		perror("listen error");
		exit(1);
	}else{
		printf("listen success\n");
	}
	
	for(fd=0;fd<MAX_CLIENT;fd++){
		isconnect[fd]=0;
	}
	
	pthread_t pid;
	if( pthread_create(&pid,NULL,(void*)scan,NULL) ){
		perror("thread create error");
	}
	
	unsigned long start_time = 0 ;
	unsigned long current_time = 0 ;
	unsigned long last_time = 0 ;
	unsigned long packet_num = 0 ;
	unsigned long transfer_each = 1 * 1000uL * 1000uL / FREQUENCY ; // us
	unsigned long delayus = 0 ;
	struct timeval current ; memset(&current,0,sizeof(struct timeval));
	
	while(1){
		// initialization
		FD_ZERO(&fds);
		FD_SET(sock,&fds);
		for(fd=sock+1;fd<MAX_CLIENT;fd++){
			if(isconnect[fd]){
				FD_SET(fd,&fds);
			}
		}
		if(change=select(MAX_CLIENT,&fds,NULL,NULL,NULL),change>0){
			//printf("change=%d\n",change);
			for(fd=sock;fd<MAX_CLIENT&&change>0;fd++){
				if(FD_ISSET(fd,&fds)){
					change--;
					if(sock==fd){	// 用于监听的socket
						len=sizeof(struct sockaddr_in);
						bzero(&client_addr,sizeof(struct sockaddr_in));
						sock_client=accept(sock,(struct sockaddr*)&client_addr,&len);
						//printf("%d\n",sock_client);
						if(sock_client==-1){
							perror("accept error");
							continue;
						}
						isconnect[sock_client]=1;
						FD_SET(sock_client,&fds);
						ssize_t welcone_done = write(sock_client,WELCOME,sizeof(WELCOME));
						printf("connect from %s write %d done \n", inet_ntoa(client_addr.sin_addr)
																, welcone_done );
						
						packet_num = 0 ;
						struct timeval start ; memset(&start,0,sizeof(struct timeval));
						gettimeofday( &start, NULL );
						start_time =   start.tv_sec * 1000uL * 1000uL +   start.tv_usec ;
						last_time = start_time; 
						
						// 获取socket Buf的大小
						unsigned int curSize;
						unsigned int sizeSize = sizeof curSize;
						if (getsockopt(sock_client, SOL_SOCKET, SO_RCVBUF,
							(char*)&curSize, &sizeSize) < 0) {
							printf("i'm server getsockopt  SO_RCVBUF error = %s\n" , strerror(errno) );
						}else{
							printf("i'm server client_socket rcvbuf_size  = %d\n" , curSize );
						}
						if (getsockopt(sock_client, SOL_SOCKET, SO_SNDBUF,
							(char*)&curSize, &sizeSize) < 0) {
							printf("i'm server getsockopt  SO_SNDBUF error = %s\n" , strerror(errno) );
						}else{
							printf("i'm server client_socket sndbuf_size  = %d\n" , curSize );
						}
  

					}else{			// 其他已经建立连接的socket
						
						ret = 0;
 
						if( ret = read( fd, rbuf, MAX_PACKET_SIZE ), ret > 0 ){ 
						
							// 控制速度 
							gettimeofday( &current, NULL ); 
							current_time = current.tv_sec * 1000uL * 1000uL +   current.tv_usec ;
			
							delayus = transfer_each  + last_time - current_time ;
							if( delayus > 0 ){
								//printf("sleep %lu\n", delayus );
								usleep( delayus  );
							}
							gettimeofday( &current, NULL ); 
							last_time =  current.tv_sec * 1000uL * 1000uL +   current.tv_usec ;
							
							packet_num++;
							if( (packet_num & 0x3F) == 0 ){ // FF 256*N per 256 ; 3F 64*N per 64
								//struct timeval current ; memset(&current,0,sizeof(struct timeval));
								//gettimeofday( &current, NULL );
								current_time =   current.tv_sec * 1000uL * 1000uL +   current.tv_usec ;
								printf("packet_num = %lu , freq = %lu\n", packet_num , packet_num * 1000uL * 1000uL /( current_time - start_time ) );
								printf("recv %d, rbuf[0]=0x%02x, rbuf[%d]=0x%02x\n" ,
									ret , rbuf[0] , 
									(ret==MAX_PACKET_SIZE)?MAX_PACKET_SIZE-1:100, 
									*(rbuf + ((ret==MAX_PACKET_SIZE)?MAX_PACKET_SIZE-1:100)) );
							}
							
						}else{
							printf("read error ret = %d %s " , ret ,strerror(errno) );
							
							printf("can not read and write %d\nPlease reConnect\n",fd);
							isconnect[fd]=0;
							FD_CLR(fd,&fds);
							close(fd);
						}
						
					}
					
				}
			}
			
		}
	}
	close(sock);
	return 0;
}
