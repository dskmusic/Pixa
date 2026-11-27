// Superresolucion x4 con ncnn (Vulkan si el dispositivo lo tiene, CPU si no).
// Modelo: Real-ESRGAN general-x4v3 (SRVGGNetCompact). Entrada "data", salida "output",
// RGB en 0..1. El troceado en teselas lo hace Kotlin; aqui solo se ejecuta una tesela.
#include <jni.h>
#include <android/asset_manager_jni.h>
#include <net.h>
#include <gpu.h>
#include <algorithm>

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_dskmusic_pixa_ml_NcnnSr_create(JNIEnv* env, jobject, jobject assets, jstring param,
                                        jstring bin, jboolean gpu) {
    AAssetManager* mgr = AAssetManager_fromJava(env, assets);
    auto* net = new ncnn::Net();
    net->opt.use_vulkan_compute = gpu && ncnn::get_gpu_count() > 0;
    const char* p = env->GetStringUTFChars(param, nullptr);
    const char* b = env->GetStringUTFChars(bin, nullptr);
    int r = net->load_param(mgr, p);
    if (r == 0) r = net->load_model(mgr, b);
    env->ReleaseStringUTFChars(param, p);
    env->ReleaseStringUTFChars(bin, b);
    if (r != 0) {
        delete net;
        return 0;
    }
    return reinterpret_cast<jlong>(net);
}

// Devuelve (w*scale) x (h*scale) pixeles ARGB, o null si la inferencia falla.
JNIEXPORT jintArray JNICALL
Java_com_dskmusic_pixa_ml_NcnnSr_run(JNIEnv* env, jobject, jlong handle, jintArray px, jint w,
                                     jint h, jint scale) {
    auto* net = reinterpret_cast<ncnn::Net*>(handle);
    ncnn::Mat in(w, h, 3);
    jint* src = env->GetIntArrayElements(px, nullptr);
    float* r = in.channel(0);
    float* g = in.channel(1);
    float* b = in.channel(2);
    for (int i = 0; i < w * h; i++) {
        jint p = src[i];
        r[i] = ((p >> 16) & 0xFF) / 255.f;
        g[i] = ((p >> 8) & 0xFF) / 255.f;
        b[i] = (p & 0xFF) / 255.f;
    }
    env->ReleaseIntArrayElements(px, src, JNI_ABORT);

    ncnn::Mat out;
    {
        ncnn::Extractor ex = net->create_extractor();
        if (ex.input("data", in) != 0 || ex.extract("output", out) != 0) return nullptr;
    }
    if (out.w != w * scale || out.h != h * scale || out.c != 3) return nullptr;

    const int n = out.w * out.h;
    jintArray res = env->NewIntArray(n);
    if (!res) return nullptr;
    jint* dst = env->GetIntArrayElements(res, nullptr);
    const float* orr = out.channel(0);
    const float* og = out.channel(1);
    const float* ob = out.channel(2);
    auto q = [](float v) { return (int)std::min(255.f, std::max(0.f, v * 255.f + 0.5f)); };
    for (int i = 0; i < n; i++) {
        dst[i] = (jint)(0xFF000000u | (q(orr[i]) << 16) | (q(og[i]) << 8) | q(ob[i]));
    }
    env->ReleaseIntArrayElements(res, dst, 0);
    return res;
}

JNIEXPORT void JNICALL
Java_com_dskmusic_pixa_ml_NcnnSr_destroy(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<ncnn::Net*>(handle);
}

}  // extern "C"
