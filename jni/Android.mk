LOCAL_PATH := $(call my-dir)

##############################################################################
include $(CLEAR_VARS)
LOCAL_LDLIBS    := -llog -landroid -lmediandk
LOCAL_MODULE    := libCommon
LOCAL_SRC_FILES := vortex.cpp NativeContext.cpp
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
LOCAL_LDLIBS    := -llog -landroid -lmediandk
LOCAL_MODULE    := DecodeH264
LOCAL_SRC_FILES := DecodeH264.cpp  
LOCAL_SHARED_LIBRARIES := libCommon
include $(BUILD_SHARED_LIBRARY)
##############################################################################
include $(LOCAL_PATH)/tcp_udp/Android.mk