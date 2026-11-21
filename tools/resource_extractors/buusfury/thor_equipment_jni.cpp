
#include <jni.h>
#include "dragonbytez/rom.hpp"
#include "dragonbytez/compression.hpp"
#include "dragonbytez/png.hpp"

#include <array>
#include <cstdio>
#include <filesystem>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

constexpr std::array<std::size_t, 162> offsets = {
    0x06F3C30, 0x06F3C70, 0x06F3CD0, 0x06F3D44, 0x06F3DC0, 0x06F3CD0, 0x06F3EB8, 0x06F3EB8,
    0x06F3F98, 0x06F3C70, 0x06F4068, 0x06F40EC, 0x06F416C, 0x06F41F0, 0x06F426C, 0x06F42BC,
    0x06F4334, 0x06F3CD0, 0x06F3D44, 0x06F44A4, 0x06F4514, 0x06F45A4, 0x06F42BC, 0x06F46A8,
    0x06F3EB8, 0x06F4794, 0x06F4794, 0x06F3CD0, 0x06F48F8, 0x06F40EC, 0x06F3D44, 0x06F46A8,
    0x06F4AF4, 0x06F3DC0, 0x06F4BF8, 0x06F4C7C, 0x06F4D00, 0x06F4D60, 0x06F4DE0, 0x06F46A8,
    0x06F4EBC, 0x06F4F38, 0x06F4FBC, 0x06F5030, 0x06F50B4, 0x06F5108, 0x06F5184, 0x06F51F8,
    0x06F5108, 0x06F52EC, 0x06F5358, 0x06F51F8, 0x06F52EC, 0x06F5358, 0x06F5108, 0x06F55B8,
    0x06F5108, 0x06F52EC, 0x06F5184, 0x06F5774, 0x06F5108, 0x06F51F8, 0x06F52EC, 0x06F5948,
    0x06F5184, 0x06F5774, 0x06F5AB0, 0x06F52EC, 0x06F5B98, 0x06F5C0C, 0x06F51F8, 0x06F5AB0,
    0x06F52EC, 0x06F5DDC, 0x06F5B98, 0x06F5ED0, 0x06F5ED0, 0x06F5FC8, 0x06F6000, 0x06F605C,
    0x06F60D0, 0x06F613C, 0x06F6000, 0x06F61FC, 0x06F605C, 0x06F62D0, 0x06F61FC, 0x06F6000,
    0x06F63EC, 0x06F6458, 0x06F61FC, 0x06F6534, 0x06F605C, 0x06F6614, 0x06F6680, 0x06F61FC,
    0x06F61FC, 0x06F67A8, 0x06F605C, 0x06F61FC, 0x06F61FC, 0x06F613C, 0x06F61FC, 0x06F69FC,
    0x06F6A5C, 0x06F60D0, 0x06F61FC, 0x06F6B88, 0x06F613C, 0x06F6C50, 0x06F6458, 0x06F6D24,
    0x06F6D80, 0x06F6DC0, 0x06F6E20, 0x06F6E7C, 0x06F6F14, 0x06F6F8C, 0x06F6FD0, 0x06F7018,
    0x06F7070, 0x06F70E8, 0x06F7170, 0x06F71EC, 0x06F6F14, 0x06F72D8, 0x06F7360, 0x06F73CC,
    0x06F7440, 0x06F74C8, 0x06F7524, 0x06F75AC, 0x06F7600, 0x06F7668, 0x06F6DC0, 0x06F7734,
    0x06F75AC, 0x06F77F4, 0x06F787C, 0x06F78E8, 0x06F793C, 0x06F799C, 0x06F7A20, 0x06F7AA8,
    0x06F7B24, 0x06F7B8C, 0x06F7C08, 0x06F7C74, 0x06F7CF8, 0x06F7D78, 0x06F7DE0, 0x06F7E54,
    0x06F793C, 0x06F7F0C, 0x06F7F78, 0x06F7FE8, 0x06F8068, 0x06F80E0, 0x06F814C, 0x06F6E20,
    0x06F822C, 0x06F8288
};

std::string from_java(JNIEnv* env, jstring str) {
    if (!str) throw std::runtime_error("Missing path");
    const char* p = env->GetStringUTFChars(str, nullptr);
    if (!p) throw std::runtime_error("Cannot read path");
    std::string result(p);
    env->ReleaseStringUTFChars(str, p);
    return result;
}

dragonbytez::Rgba rgba555(unsigned value) {
    auto convert = [](unsigned n) -> std::uint8_t {
        return static_cast<std::uint8_t>(n * 255 / 31);
    };
    return {
        convert(value & 31),
        convert((value >> 5) & 31),
        convert((value >> 10) & 31),
        255
    };
}

void extract(const std::string& rom_path,
             const std::string& directory) {
    dragonbytez::Rom rom(rom_path);

    // Dos ROM verificadas: española y USA GoodTools.
    // Los 162 bloques y la paleta son idénticos en ambas.
    const bool spanish =
        rom.size() == 10514228 &&
        rom.u32(0x179764) == 0x08917500 &&
        rom.u32(0x6B6BDC) == 0x086A89D0;

    const bool goodtools = rom.size() == 8388608;

    if ((!spanish && !goodtools) ||
        rom.game_code() != "BG3E" ||
        rom.revision() != 0) {
        throw std::runtime_error(
            "Equipment extractor: unsupported ROM variant");
    }

    std::array<dragonbytez::Rgba, 256> palette{};
    for (unsigned i = 0; i < 256; ++i) {
        palette[i] = rgba555(rom.u16(0x5652C + i * 2));
    }
    palette[0] = {54, 57, 61, 0};

    std::filesystem::create_directories(directory);

    for (std::size_t i = 0; i < offsets.size(); ++i) {
        const auto decoded =
            dragonbytez::decompress_container(rom, offsets[i], 256);

        if (decoded.data.size() != 256 ||
            decoded.kind != 1 ||
            decoded.declared_size != 256) {
            throw std::runtime_error(
                "Invalid equipment image at index " +
                std::to_string(i));
        }

        std::vector<dragonbytez::Rgba> pixels;
        pixels.reserve(256);

        for (std::uint8_t index : decoded.data) {
            auto color = palette[index];

            // Icono 124: variante amarilla comprobada píxel a píxel.
            if (i == 124) {
                switch (index) {
                    case 114:
                    case 115: color = {131, 131, 0, 255}; break;
                    case 116:
                    case 118: color = {172, 172, 0, 255}; break;
                    case 119: color = {213, 213, 0, 255}; break;
                    case 120: color = {255, 255, 0, 255}; break;
                    default: break;
                }
            }

            pixels.push_back(color);
        }

        char filename[40];
        std::snprintf(filename, sizeof(filename),
                      "thor_equipment_%03zu.png", i);

        dragonbytez::write_png_rgba(
            std::filesystem::path(directory) / filename,
            16, 16, pixels);
    }
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_org_novapowers_buusfury_thor_ThorEquipmentExtractor_generateEquipmentNative(
    JNIEnv* env, jclass, jstring romPath, jstring outputPath) {
    try {
        extract(from_java(env, romPath), from_java(env, outputPath));
    } catch (const std::exception& error) {
        if (!env->ExceptionCheck()) {
            jclass cls = env->FindClass("java/io/IOException");
            if (cls) env->ThrowNew(cls, error.what());
        }
    }
}
