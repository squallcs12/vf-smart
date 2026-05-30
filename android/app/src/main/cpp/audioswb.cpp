#include <jni.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <cerrno>
#include <android/log.h>

#define TAG  "AudioSwb"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jint JNICALL
Java_com_daotranbang_vfsmart_camera_AudioSwb_nativeOpen(JNIEnv*, jclass) {
    int fd = open("/dev/AUDIOSWB", O_RDWR);
    if (fd < 0) LOGE("open /dev/AUDIOSWB failed errno=%d", errno);
    else        LOGI("opened fd=%d", fd);
    return fd;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_daotranbang_vfsmart_camera_AudioSwb_nativeIoctl(
        JNIEnv*, jclass, jint fd, jint request, jint arg) {
    int ret = ioctl(fd, (unsigned long) request, arg);
    if (ret < 0) LOGE("ioctl(0x%x, %d) failed errno=%d", request, arg, errno);
    else         LOGI("ioctl(0x%x, %d) = %d", request, arg, ret);
    return ret >= 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_daotranbang_vfsmart_camera_AudioSwb_nativeClose(JNIEnv*, jclass, jint fd) {
    if (fd >= 0) {
        close(fd);
        LOGI("closed fd=%d", fd);
    }
}
