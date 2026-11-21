#include <jni.h>

#include "dragonbytez/analysis.hpp"
#include "dragonbytez/rom.hpp"

#include <exception>
#include <stdexcept>
#include <string>

namespace {

std::string java_string(JNIEnv* env, jstring value) {
    if (!value) {
        throw std::invalid_argument("Missing ROM or output path");
    }

    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) {
        throw std::runtime_error("Cannot read Java string");
    }

    try {
        std::string result(chars);
        env->ReleaseStringUTFChars(value, chars);
        return result;
    } catch (...) {
        env->ReleaseStringUTFChars(value, chars);
        throw;
    }
}

}

extern "C" JNIEXPORT void JNICALL
Java_org_novapowers_buusfury_thor_ThorMapExtractor_generateMapsNative(
    JNIEnv* env, jclass, jstring romPath, jstring outputPath) {

    try {
        const auto rom = java_string(env, romPath);
        const auto output = java_string(env, outputPath);

        dragonbytez::Rom cartridge(rom);
        dragonbytez::export_thor_maps(cartridge, output);

    } catch (const std::exception& error) {
        if (!env->ExceptionCheck()) {
            jclass ioException = env->FindClass("java/io/IOException");
            if (ioException) {
                env->ThrowNew(ioException, error.what());
            }
        }
    }
}
