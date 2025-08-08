#include <jni.h>
#include <string>
#include <opencv2/core.hpp>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_vprdemo1_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_vprdemo1_MainActivity_cvVersion(JNIEnv* env, jobject /*thiz*/){
    const std::string v = cv::getVersionString();
    return env->NewStringUTF(v.c_str());
}
