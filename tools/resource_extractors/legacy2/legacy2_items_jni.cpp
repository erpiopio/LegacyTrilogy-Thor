
#include <jni.h>
#include <filesystem>
#include <stdexcept>
#include <string>

namespace legacy2 {
void extract_items(
    const std::string&,
    const std::filesystem::path&);
}

extern "C" JNIEXPORT void JNICALL
Java_org_erpiopio_legacygoku2_thor_Legacy2ItemResources_extractNative(
    JNIEnv* env, jclass, jstring source, jstring output) {

    try {
        auto convert = [&](jstring value) -> std::string {
            if (!value)
                throw std::invalid_argument("Missing item resource path");

            const char* p = env->GetStringUTFChars(value, nullptr);
            if (!p)
                throw std::runtime_error("Cannot read resource path");

            std::string result(p);
            env->ReleaseStringUTFChars(value, p);
            return result;
        };

        const std::string rom = convert(source);
        const std::string directory = convert(output);

        legacy2::extract_items(rom, directory);

    } catch (const std::exception& error) {
        if (!env->ExceptionCheck()) {
            jclass exception = env->FindClass("java/io/IOException");
            if (exception)
                env->ThrowNew(exception, error.what());
        }
    }
}
