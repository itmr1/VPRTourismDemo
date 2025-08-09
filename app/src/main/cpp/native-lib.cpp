#include <jni.h>
#include <string>
#include <opencv2/core.hpp>
#include <vector>
#include <cstring>
#include <opencv2/imgproc.hpp>

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

static inline uint8_t* addr(JNIEnv* env, jobject directBuf) {
    return reinterpret_cast<uint8_t*>(env->GetDirectBufferAddress(directBuf));
}

// Pack Android YUV_420_888 planes to contiguous I420 (Y + U + V)
static void pack_to_i420(
        uint8_t* dstI420,
        const uint8_t* srcY, int yRowStride,
        const uint8_t* srcU, int uRowStride, int uPixStride,
        const uint8_t* srcV, int vRowStride, int vPixStride,
        int width, int height) {

    const int chromaW = (width + 1) / 2;
    const int chromaH = (height + 1) / 2;

    // Copy Y plane row by row
    for (int r = 0; r < height; ++r) {
        std::memcpy(dstI420 + r * width, srcY + r * yRowStride, width);
    }

    uint8_t* dstU = dstI420 + width * height;
    uint8_t* dstV = dstU + chromaW * chromaH;

    for (int r = 0; r < chromaH; ++r) {
        const uint8_t* uRow = srcU + r * uRowStride;
        const uint8_t* vRow = srcV + r * vRowStride;
        uint8_t* du = dstU + r * chromaW;
        uint8_t* dv = dstV + r * chromaW;

        if (uPixStride == 1 && vPixStride == 1) {
            std::memcpy(du, uRow, chromaW);
            std::memcpy(dv, vRow, chromaW);
        } else {
            for (int c = 0; c < chromaW; ++c) {
                du[c] = uRow[c * uPixStride];
                dv[c] = vRow[c * vPixStride];
            }
        }
    }
}

// NEW: stub processor; converts YUV→RGB, rotates, resizes (fixed size), returns a checksum
extern "C" JNIEXPORT jlong JNICALL
Java_com_example_vprdemo1_MainActivity_processYuv420(
        JNIEnv* env, jclass,
        jobject yBuf, jobject uBuf, jobject vBuf,
        jint width, jint height,
        jint yRowStride, jint uRowStride, jint vRowStride,
        jint uPixStride, jint vPixStride,
        jint rotationDegrees) {

    uint8_t* y = addr(env, yBuf);
    uint8_t* u = addr(env, uBuf);
    uint8_t* v = addr(env, vBuf);
    if (!y || !u || !v) return -2;

    const int chromaW = (width + 1) / 2;
    const int chromaH = (height + 1) / 2;
    const size_t need = static_cast<size_t>(width) * height + 2u * (chromaW * chromaH);

    static thread_local std::vector<uint8_t> i420;
    if (i420.size() < need) i420.resize(need);

    pack_to_i420(i420.data(), y, yRowStride, u, uRowStride, uPixStride, v, vRowStride, vPixStride, width, height);

    cv::Mat yuv(height + height / 2, width, CV_8UC1, i420.data());
    cv::Mat rgb;
    cv::cvtColor(yuv, rgb, cv::COLOR_YUV2RGB_I420);

    if (rotationDegrees == 90) cv::rotate(rgb, rgb, cv::ROTATE_90_CLOCKWISE);
    else if (rotationDegrees == 180) cv::rotate(rgb, rgb, cv::ROTATE_180);
    else if (rotationDegrees == 270) cv::rotate(rgb, rgb, cv::ROTATE_90_COUNTERCLOCKWISE);

    // Downscale to a small fixed tensor-like size to keep work bounded (no model yet)
    cv::Mat input;
    cv::resize(rgb, input, cv::Size(320, 240), 0, 0, cv::INTER_AREA);

    // Cheap checksum as a deterministic "place id" stub
    uint64_t sum = 0;
    const int step = 97;
    const uint8_t* p = input.ptr<uint8_t>(0);
    const size_t N = static_cast<size_t>(input.total()) * input.channels();
    for (size_t i = 0; i < N; i += step) sum += p[i];
    return static_cast<jlong>(sum % 1000);
}
