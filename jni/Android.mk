LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_LDLIBS    := -llog -landroid -lmediandk


LOCAL_MODULE    := CodecANativeWin
LOCAL_SRC_FILES := CodecANativeWin.cpp

include $(BUILD_SHARED_LIBRARY)
