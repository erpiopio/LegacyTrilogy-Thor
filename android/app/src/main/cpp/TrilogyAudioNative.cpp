#include <jni.h>
#include <dragonbytez/rom.hpp>
#include <dragonbytez/gsf_player.hpp>
#include <dragonbytez/log1_runtime.hpp>

#include <filesystem>
#include <fstream>
#include <string>
#include <stdexcept>
#include <vector>
#include <cstdint>

namespace fs = std::filesystem;

static std::string javaString(JNIEnv* env, jstring value) {
    if (!value) throw std::runtime_error("Ruta vacía");
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) throw std::runtime_error("No se pudo leer la ruta");
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_erpiopio_legacytrilogy_thor_TrilogyAudio_nativeGenerate(
    JNIEnv* env, jclass, jint game,
    jstring romPath, jstring outputPath) {

    std::string error;
    fs::path temporaryRom;

    try {
        if (game < 0 || game > 2)
            throw std::runtime_error("Juego no válido");

        fs::path source = javaString(env, romPath);
        fs::path destination = javaString(env, outputPath);

        if (!fs::is_regular_file(source))
            throw std::runtime_error("ROM no encontrada");

        fs::path renderSource = source;

        if (game == 2 && fs::file_size(source) == 10514228ULL) {
            temporaryRom = destination;
            temporaryRom += ".gba.tmp";

            std::ifstream input(source, std::ios::binary);
            std::ofstream output(temporaryRom, std::ios::binary);

            if (!input || !output)
                throw std::runtime_error("No se pudo preparar Buu's Fury");

            std::vector<char> buffer(65536);
            std::size_t remaining = 8388608;

            while (remaining > 0) {
                const auto amount =
                    std::min(remaining, buffer.size());

                input.read(buffer.data(),
                    static_cast<std::streamsize>(amount));

                if (input.gcount() !=
                    static_cast<std::streamsize>(amount))
                    throw std::runtime_error("ROM incompleta");

                output.write(buffer.data(),
                    static_cast<std::streamsize>(amount));

                if (!output)
                    throw std::runtime_error("Error al preparar ROM");

                remaining -= amount;
            }

            output.close();
            renderSource = temporaryRom;
        }

        dragonbytez::Rom rom(renderSource);

        if (game == 0) {
            dragonbytez::render_log1_runtime_track_preview_wav(
                rom, 15, destination, 75);
        } else if (game == 1) {
            dragonbytez::render_bgm_preview_wav(
                rom, 15, destination, 116);
        } else {
            dragonbytez::render_bgm_preview_wav(
                rom, 45, destination, 120);
        }

        if (!fs::exists(destination) ||
            fs::file_size(destination) <= 44)
            throw std::runtime_error("No se generó audio válido");

    } catch (const std::exception& ex) {
        error = ex.what();
    } catch (...) {
        error = "Error desconocido al generar música";
    }

    if (!temporaryRom.empty()) {
        std::error_code ec;
        fs::remove(temporaryRom, ec);
    }

    return env->NewStringUTF(error.c_str());
}
