LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := cc
LOCAL_SRC_FILES := linux_Select_Client.c
LOCAL_LDLIBS    := -llog
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE    := ss
LOCAL_SRC_FILES := linux_Select_Server.c
LOCAL_LDLIBS    := -llog
include $(BUILD_EXECUTABLE)



 
