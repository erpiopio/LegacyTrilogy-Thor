#include <jni.h>
#include <filesystem>
#include <string>
#include <stdexcept>
namespace legacy2 { void extract_scouter(const std::string&,const std::filesystem::path&); }
extern "C" JNIEXPORT void JNICALL
Java_org_erpiopio_legacygoku2_thor_Legacy2ScouterResources_extractNative(
        JNIEnv* env,jclass,jstring source,jstring output) {
    auto str=[&](jstring s){
        if(!s)throw std::invalid_argument("Missing Scouter path");
        const char* p=env->GetStringUTFChars(s,nullptr);
        if(!p)throw std::runtime_error("Cannot read Scouter path");
        std::string result(p);env->ReleaseStringUTFChars(s,p);return result;
    };
    try {legacy2::extract_scouter(str(source),str(output));}
    catch(const std::exception& e){
        if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/io/IOException"),e.what());
    }
}
