LOCAL_PATH := $(call my-dir)
 
include $(CLEAR_VARS)
LOCAL_LDLIBS    := -llog 
LOCAL_MODULE    := fast_convert
LOCAL_SRC_FILES := fast_convert.cpp
LOCAL_SHARED_LIBRARIES := libCommon
include $(BUILD_SHARED_LIBRARY)
 

