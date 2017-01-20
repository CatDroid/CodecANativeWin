LOCAL_PATH := $(call my-dir)


##############################################################################
include $(CLEAR_VARS)
LOCAL_LDLIBS    := -llog -landroid -lmediandk
LOCAL_MODULE    := libCommon
LOCAL_SRC_FILES := vortex.cpp NativeContext.cpp NativeBuffer.cpp
LOCAL_CFLAGS	+= -Wall
#LOCAL_CFLAGS	+= -Wreturn-type
include $(BUILD_SHARED_LIBRARY)
##############################################################################

##############################################################################
include $(CLEAR_VARS)
LOCAL_LDLIBS    := -llog -landroid -lmediandk -Wall 
LOCAL_MODULE    := Abuffer
LOCAL_SRC_FILES := ABuffer_jni.cpp
LOCAL_CFLAGS	+= -Wunused-variable
# LOCAL_CFLAGS 同样适用于C++ C源文件
LOCAL_SHARED_LIBRARIES := libCommon
include $(BUILD_SHARED_LIBRARY)
##############################################################################

##############################################################################
include $(CLEAR_VARS)
LOCAL_LDLIBS    := -llog -landroid -lmediandk
LOCAL_MODULE    := CodecANativeWin
LOCAL_SRC_FILES := CodecANativeWin.cpp
LOCAL_SHARED_LIBRARIES := libCommon
include $(BUILD_SHARED_LIBRARY)
##############################################################################
include $(CLEAR_VARS)
LOCAL_LDLIBS    := -llog -landroid -lmediandk
LOCAL_MODULE    := DeFileAndEnCode
LOCAL_SRC_FILES := DeFileAndEnCode.cpp HWH264DecodeAndEncode.cpp
LOCAL_SHARED_LIBRARIES := libCommon
include $(BUILD_SHARED_LIBRARY)
##############################################################################
include $(CLEAR_VARS)
LOCAL_LDLIBS    := -llog -landroid -lmediandk
LOCAL_MODULE    := CodecAudio
LOCAL_SRC_FILES := CodecAudio.cpp  
LOCAL_SHARED_LIBRARIES := libCommon
include $(BUILD_SHARED_LIBRARY)
##############################################################################
include $(CLEAR_VARS)
LOCAL_LDLIBS    := -llog -landroid -lmediandk -lGLESv1_CM -lEGL
LOCAL_MODULE    := DecodeH264
LOCAL_SRC_FILES := DecodeH264.cpp  
LOCAL_SHARED_LIBRARIES := libCommon
include $(BUILD_SHARED_LIBRARY)
##############################################################################

#include $(LOCAL_PATH)/tcp_udp/Android.mk
include $(LOCAL_PATH)/openGL/Android.mk


 
.PHONY: clean 
clean:
	echo "excute .PHONY: clean "
	del /f/s/q tempdir	
	for /d %%i in (c:\*) do echo %%i  
	for /r jni  %%a in ("*.c","*.cpp") do echo %%a
	for %%i in (for1 for2 for3) do echo %%i
	

# del /f/s/q tempdir 只会下面的文件 不包含本文件夹和下面的文件夹
# 没有rm命令    所以要改用del
# 没有for命令   所以变成了 windows上面的for %%a 不能用 %%abc %%temp 等多于一个字符的
# 没有 cat --help 命令 