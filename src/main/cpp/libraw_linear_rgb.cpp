#include <jni.h>
#include <libraw/libraw.h>
#include <cstdlib>
#include <cstring>

extern "C" {

JNIEXPORT jobject JNICALL
Java_com_photoeditor_raw_NativeRawBridge_decodeRawToDirectBuffer(
    JNIEnv *env, jobject obj, jstring filePath, jobject outMetadata) {

    const char *path = env->GetStringUTFChars(filePath, nullptr);

    LibRaw rawProcessor;
    
    // Configurazione LibRaw per output Lineare Float32
    rawProcessor.imgdata.params.output_bps = 16;
    rawProcessor.imgdata.params.gamm[0] = 1.0; // Nessuna curva gamma (lineare)
    rawProcessor.imgdata.params.gamm[1] = 1.0;
    rawProcessor.imgdata.params.no_auto_bright = 1;
    rawProcessor.imgdata.params.use_camera_wb = 1;

    if (rawProcessor.open_file(path) != LIBRAW_SUCCESS ||
        rawProcessor.unpack() != LIBRAW_SUCCESS ||
        rawProcessor.dcraw_process() != LIBRAW_SUCCESS) {
        env->ReleaseStringUTFChars(filePath, path);
        return nullptr;
    }

    libraw_processed_image_t *img = rawProcessor.dcraw_make_mem_image();
    env->ReleaseStringUTFChars(filePath, path);

    if (!img || img->type != LIBRAW_IMAGE_BITMAP) {
        return nullptr;
    }

    int width = img->width;
    int height = img->height;
    int totalPixels = width * height;

    // Popolamento Metadati in Java via Reflection/Field ID
    jclass metaClass = env->GetObjectClass(outMetadata);
    env->SetIntField(outMetadata, env->GetFieldID(metaClass, "width", "I"), width);
    env->SetIntField(outMetadata, env->GetFieldID(metaClass, "height", "I"), height);
    
    // Gains WB As-Shot
    float rGain = rawProcessor.imgdata.color.cam_mul[0] / rawProcessor.imgdata.color.cam_mul[1];
    float bGain = rawProcessor.imgdata.color.cam_mul[2] / rawProcessor.imgdata.color.cam_mul[1];
    env->SetFloatField(outMetadata, env->GetFieldID(metaClass, "rGain", "F"), rGain);
    env->SetFloatField(outMetadata, env->GetFieldID(metaClass, "gGain", "F"), 1.0f);
    env->SetFloatField(outMetadata, env->GetFieldID(metaClass, "bGain", "F"), bGain);

    // Allocazione buffer direct float (3 canali RGB float32)
    size_t bufferSize = totalPixels * 3 * sizeof(float);
    float *floatBuffer = (float *)malloc(bufferSize);

    // Conversione da 16-bit ushort a 32-bit float [0.0, 1.0]
    unsigned short *srcData = (unsigned short *)img->data;
    for (int i = 0; i < totalPixels * 3; i++) {
        floatBuffer[i] = (float)srcData[i] / 65535.0f;
    }

    LibRaw::dcraw_clear_mem(img);
    rawProcessor.recycle();

    // Ritorna un java.nio.ByteBuffer diretto
    return env->NewDirectByteBuffer(floatBuffer, bufferSize);
}

JNIEXPORT void JNICALL
Java_com_photoeditor_raw_NativeRawBridge_freeNativeBuffer(
    JNIEnv *env, jobject obj, jobject buffer) {
    void *ptr = env->GetDirectBufferAddress(buffer);
    if (ptr) {
        free(ptr);
    }
}

}