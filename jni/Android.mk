LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_LDLIBS    := -llog -landroid -lmediandk


LOCAL_MODULE    := CodecANativeWin
LOCAL_SRC_FILES := CodecANativeWin.cpp

include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)

LOCAL_LDLIBS    := -llog -landroid -lmediandk


LOCAL_MODULE    := DeFileAndEnCode
LOCAL_SRC_FILES := DeFileAndEnCode.cpp HWH264DecodeAndEncode.cpp utils.cpp

include $(BUILD_SHARED_LIBRARY)

##############################################################################
include $(LOCAL_PATH)/tcp_udp/Android.mk