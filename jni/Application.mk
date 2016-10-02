#all 所有平台 armeabi armeabi-v7a mips x86
#可以选择一个平台 编译 
# MT6735 arm64-v8a 
#		Unable to find a compatible ABI
#		ABI's supported by the application: armeabi_v7a
#		ABI's supported by the device: arm64-v8a, null
APP_ABI := arm64-v8a
# With this you don't have to add -g to your compiler flags, ndk-build will do so automatically.
#AAPP_OPTIM := debug

# 不加下面一行  会出现 
#  #include <list>
# 				^
#				No such file or directory
# 
# 不用每个so库在Android.mk都加这个了,可能是cpp std更换了 不再是简化的system std
# LOCAL_STATIC_LIBRARIES := stlport_static
APP_STL := stlport_static

# system - 使用默认最小的C++运行库，这样生成的应用体积小，内存占用小，但部分功能将无法支持
# stlport_static - 使用STLport作为静态库，推荐的
# stlport_shared - STLport 作为动态库，这个可能产生兼容性和部分低版本的Android固件，目前不推荐使用。
# gnustl_static - 使用 GNU libstdc++ 作为静态库



# 与 AndroidMainfest.xml miniSDK必须一致  否则Unknown Application ABI
# AndroidMainfest.xml miniSDK必须要大于等于 目标版本  因为后面的版本可能开放更多API e.g NdkMediaCodec
APP_PLATFORM := android-21


# 指出你的应用程序工程目录
#APP_PROJECT_PATH := $(call my-dir)

# 默认情况下，NDK 生成系统将寻找一个名为 Android.mk 文件在 $(APP_PROJECT_PATH)/jni 目录下
#APP_BUILD_SCRIPT := $(APP_PROJECT_PATH)/jni/Android.mk

