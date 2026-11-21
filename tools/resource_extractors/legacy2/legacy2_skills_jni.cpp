#include <jni.h>
#include <filesystem>
#include <stdexcept>
#include <string>
namespace legacy2 {void extract_skills(const std::string&,const std::filesystem::path&);}
extern "C" JNIEXPORT void JNICALL
Java_org_erpiopio_legacygoku2_thor_Legacy2SkillResources_extractNative(
        JNIEnv*env,jclass,jstring source,jstring output){
    try{
        auto convert=[&](jstring value){
            if(!value)throw std::invalid_argument("Missing skill resource path");
            const char*p=env->GetStringUTFChars(value,nullptr);
            if(!p)throw std::runtime_error("Cannot read skill resource path");
            std::string result(p);env->ReleaseStringUTFChars(value,p);return result;
        };
        auto rom=convert(source);auto directory=convert(output);
        legacy2::extract_skills(rom,directory);
    }catch(const std::exception&error){
        if(!env->ExceptionCheck()){
            jclass type=env->FindClass("java/io/IOException");
            if(type){env->ThrowNew(type,error.what());env->DeleteLocalRef(type);}
        }
    }
}
