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

