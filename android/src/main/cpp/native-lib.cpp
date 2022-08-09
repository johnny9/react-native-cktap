#include <tap_protocol/tap_protocol.h>
#include <tap_protocol/cktapcard.h>
#include <jni.h>

extern "C"
JNIEXPORT void JNICALL
Java_com_example_tap_1protocol_1nativesdk_MainActivity_cardStatus(JNIEnv *env, jobject thiz,
                                                               jobject card) {

    auto transport = tap_protocol::MakeDefaultTransport([=](const tap_protocol::Bytes &in) {
        jclass isoDepClass = env->FindClass("android/nfc/tech/IsoDep");
        jmethodID tranceiveMethodID = env->GetMethodID(isoDepClass, "transceive", "([B)[B");
        auto bytesToSend = env->NewByteArray(in.size());
        env->SetByteArrayRegion(bytesToSend, 0, in.size(), (jbyte *) in.data());

        auto bytesReceive = (jbyteArray) env->CallObjectMethod(card, tranceiveMethodID, bytesToSend);
        env->DeleteLocalRef(bytesToSend);

        auto firstByte = env->GetByteArrayElements(bytesReceive, JNI_FALSE);
        tap_protocol::Bytes result((char *) firstByte, (char *) firstByte + env->GetArrayLength(bytesReceive));
        env->ReleaseByteArrayElements(bytesReceive, firstByte, JNI_ABORT);
        return result;
    });

    tap_protocol::Tapsigner tapsigner(std::move(transport));

    // Run command `status`
    auto status = tapsigner.Status();
}
