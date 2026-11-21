
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

struct Entry {
    int id;
    std::size_t offset;
};

constexpr std::array<Entry, 54> icons{{
    {0, 0x03BBE70},
    {1, 0x03BBEFC},
    {2, 0x03BBF54},
    {3, 0x03BBF98},
    {4, 0x03BC010},
    {5, 0x03BC074},
    {6, 0x03BC0EC},
    {7, 0x03BC148},
    {8, 0x03BC1B8},
    {10, 0x03BC2E8},
    {11, 0x03BC35C},
    {12, 0x03BC3C4},
    {13, 0x03BC428},
    {14, 0x03BC48C},
    {15, 0x03BC4F0},
    {16, 0x03BC554},
    {17, 0x03BC5C4},
    {18, 0x03BC61C},
    {19, 0x03BC674},
    {20, 0x03BC6E8},
    {21, 0x03BC760},
    {22, 0x03BC7A0},
    {23, 0x03BC800},
    {24, 0x03BC85C},
    {25, 0x03BC7A0},
    {26, 0x03BC89C},
    {27, 0x03BC914},
    {28, 0x03BC98C},
    {29, 0x03BCA08},
    {30, 0x03BCA84},
    {31, 0x03BCAFC},
    {32, 0x03BCB7C},
    {33, 0x03BCBF4},
    {37, 0x03BCE70},
    {38, 0x03BCEF8},
    {39, 0x03BCF5C},
    {40, 0x03BCEF8},
    {41, 0x03BCEF8},
    {42, 0x03BCFE4},
    {68, 0x03BD290},
    {69, 0x03BD290},
    {70, 0x03BD290},
    {71, 0x03BD290},
    {72, 0x03BD290},
    {73, 0x03BD290},
    {74, 0x03BD290},
    {75, 0x03BD290},
    {76, 0x03BD290},
    {77, 0x03BD290},
    {78, 0x03BD290},
    {79, 0x03BD290},
    {80, 0x03BD290},
    {81, 0x03BD290},
    {82, 0x03BD290}
}};

std::string path_from_java(JNIEnv* env, jstring input) {
    if (!input) throw std::runtime_error("Missing path");
    const char* chars = env->GetStringUTFChars(input, nullptr);
    if (!chars) throw std::runtime_error("Cannot read path");
    std::string result(chars);
    env->ReleaseStringUTFChars(input, chars);
    return result;
}

void generate(const std::string& romPath,
              const std::string& outputPath) {
    dragonbytez::Rom rom(romPath);

    // Variantes verificadas: española y GoodTools.
    const bool spanish =
        rom.size() == 10514228 &&
        rom.u32(0x179764) == 0x08917500 &&
        rom.u32(0x6B6BDC) == 0x086A89D0;
    const bool goodtools = rom.size() == 8388608;

    if ((!spanish && !goodtools) ||
        rom.game_code() != "BG3E" ||
        rom.revision() != 0) {
        throw std::runtime_error("Unsupported inventory ROM variant");
    }

    std::array<dragonbytez::Rgba, 256> palette{};

    for (unsigned i = 0; i < 256; ++i) {
        unsigned value = rom.u16(0x5652C + i * 2);
        auto expand = [](unsigned n) -> std::uint8_t {
            return static_cast<std::uint8_t>(n * 255 / 31);
        };
        palette[i] = {
            expand(value & 31),
            expand((value >> 5) & 31),
            expand((value >> 10) & 31),
            255
        };
    }

    palette[0] = {54, 57, 61, 0};

    std::filesystem::create_directories(outputPath);

    for (const Entry& entry : icons) {
        auto decoded = dragonbytez::decompress_container(
            rom, entry.offset, 256);

        if (decoded.data.size() != 256 ||
            decoded.declared_size != 256) {
            throw std::runtime_error(
                "Invalid inventory icon " + std::to_string(entry.id));
        }

        std::vector<dragonbytez::Rgba> pixels;
        pixels.reserve(256);

        for (auto index : decoded.data)
            pixels.push_back(palette[index]);

        char filename[40];
        std::snprintf(filename, sizeof(filename),
                      "thor_inventory_%03d.png", entry.id);

        dragonbytez::write_png_rgba(
            std::filesystem::path(outputPath) / filename,
            16, 16, pixels);
    }

    // THOR_INVENTORY_83: recursos adicionales desde la tabla ROM.
    // Los 54 iconos previamente verificados no se modifican.
    for (int id : {9, 34, 35, 36, 43}) {
        const std::size_t record =
            0x3BD310 + static_cast<std::size_t>(id) * 0x34;

        const std::uint32_t pointer = rom.u32(record);
        const unsigned width = rom.u32(record + 4);
        const unsigned height = rom.u32(record + 8);

        if (!rom.is_rom_pointer(pointer))
            throw std::runtime_error("Invalid inventory pointer");

        const unsigned expected_width = id == 9 ? 32 : 16;
        const unsigned expected_height =
            (id == 35 || id == 36) ? 8 : 16;
        const std::size_t expected_bytes =
            (id == 9) ? 512 :
            (id == 35 || id == 36) ? 128 : 1024;

        if (width != expected_width ||
            height != expected_height)
            throw std::runtime_error(
                "Unexpected inventory dimensions: " +
                std::to_string(id));

        auto decoded = dragonbytez::decompress_container(
            rom, rom.pointer_to_offset(pointer), 1024);

        if (decoded.data.size() != expected_bytes ||
            decoded.declared_size != expected_bytes)
            throw std::runtime_error(
                "Unexpected inventory data: " +
                std::to_string(id));

        // El bloque de 1024 bytes contiene cuatro páginas
        // posibles de 16x16: usar la primera como estado inicial.
        // El recurso de 32x16 se recorta al centro.
        // Los de 16x8 se centran verticalmente.
        const unsigned crop_x = width == 32 ? 8 : 0;
        const unsigned pad_y = height == 8 ? 4 : 0;

        std::vector<dragonbytez::Rgba> pixels(
            256, palette[0]);

        for (unsigned y = 0; y < height; ++y) {
            for (unsigned x = 0; x < 16; ++x) {
                const std::size_t source =
                    static_cast<std::size_t>(y) * width +
                    x + crop_x;
                const std::size_t destination =
                    static_cast<std::size_t>(y + pad_y) * 16 + x;

                pixels[destination] = palette[
                    decoded.data[source]];
            }
        }

        char filename[40];
        std::snprintf(filename, sizeof(filename),
                      "thor_inventory_%03d.png", id);

        const auto source_file =
            std::filesystem::path(outputPath) / filename;

        dragonbytez::write_png_rgba(
            source_file, 16, 16, pixels);

        // Los registros 043-067 apuntan al mismo recurso.
        if (id == 43) {
            for (int other = 44; other <= 67; ++other) {
                char duplicate[40];
                std::snprintf(
                    duplicate, sizeof(duplicate),
                    "thor_inventory_%03d.png", other);

                std::filesystem::copy_file(
                    source_file,
                    std::filesystem::path(outputPath) / duplicate,
                    std::filesystem::copy_options::overwrite_existing);
            }
        }
    }


    // THOR_PORTRAITS_ROM_8
    // Retratos originales de la ROM: imagen lineal 64x64, 8 bpp.
    constexpr std::array<Entry, 8> portraits{{
        {0, 0x6CB4FC},
        {1, 0x6C3C18},
        {2, 0x6CDC28},
        {3, 0x6E9CB0},
        {4, 0x6EDAA4},
        {5, 0x6CFA70},
        {6, 0x6EEDCC},
        {8, 0x6F0094}
    }};

    for (const Entry& portrait : portraits) {
        auto decoded = dragonbytez::decompress_container(
            rom, portrait.offset, 4096);

        if (decoded.data.size() != 4096 ||
            decoded.declared_size != 4096) {
            throw std::runtime_error(
                "Invalid portrait " + std::to_string(portrait.id));
        }

        std::vector<dragonbytez::Rgba> pixels;
        pixels.reserve(4096);

        for (auto index : decoded.data)
            pixels.push_back(palette[index]);

        char filename[40];
        std::snprintf(filename, sizeof(filename),
                      "thor_portrait_%d.png", portrait.id);

        dragonbytez::write_png_rgba(
            std::filesystem::path(outputPath) / filename,
            64, 64, pixels);
    }

}


void generate_portraits(const std::string& romPath,
                        const std::string& outputPath) {
    dragonbytez::Rom rom(romPath);

    if ((rom.size() != 8388608 && rom.size() != 10514228) ||
        rom.game_code() != "BG3E" || rom.revision() != 0)
        throw std::runtime_error("Unsupported portrait ROM");

    std::array<dragonbytez::Rgba, 256> palette{};

    for (unsigned i = 0; i < 256; ++i) {
        unsigned value = rom.u16(0x5652C + i * 2);
        auto expand = [](unsigned n) -> std::uint8_t {
            return static_cast<std::uint8_t>(n * 255 / 31);
        };
        palette[i] = {
            expand(value & 31),
            expand((value >> 5) & 31),
            expand((value >> 10) & 31),
            255
        };
    }

    palette[0] = {54, 57, 61, 0};

    constexpr std::array<Entry, 8> portraits{{
        {0, 0x6CB4FC}, {1, 0x6C3C18},
        {2, 0x6CDC28}, {3, 0x6E9CB0},
        {4, 0x6EDAA4}, {5, 0x6CFA70},
        {6, 0x6EEDCC}, {8, 0x6F0094}
    }};

    std::filesystem::create_directories(outputPath);

    for (const Entry& entry : portraits) {
        auto decoded = dragonbytez::decompress_container(
            rom, entry.offset, 4096);

        if (decoded.data.size() != 4096 ||
            decoded.declared_size != 4096)
            throw std::runtime_error("Invalid portrait " +
                                     std::to_string(entry.id));

        std::vector<dragonbytez::Rgba> pixels;
        pixels.reserve(4096);

        for (auto index : decoded.data)
            pixels.push_back(palette[index]);

        char name[40];
        std::snprintf(name, sizeof(name),
                      "thor_portrait_%d.png", entry.id);

        dragonbytez::write_png_rgba(
            std::filesystem::path(outputPath) / name,
            64, 64, pixels);
    }
}

}

extern "C" JNIEXPORT void JNICALL
Java_org_novapowers_buusfury_thor_ThorInventoryExtractor_generateNative(
        JNIEnv* env, jclass, jstring romPath, jstring outputPath) {
    try {
        generate(path_from_java(env, romPath),
                 path_from_java(env, outputPath));
    } catch (const std::exception& error) {
        if (!env->ExceptionCheck()) {
            jclass cls = env->FindClass("java/io/IOException");
            if (cls) env->ThrowNew(cls, error.what());
        }
    }
}


extern "C" JNIEXPORT void JNICALL
Java_org_novapowers_buusfury_thor_ThorPortraitExtractor_generateNative(
        JNIEnv* env, jclass, jstring romPath, jstring outputPath) {
    try {
        generate_portraits(path_from_java(env, romPath),
                           path_from_java(env, outputPath));
    } catch (const std::exception& error) {
        if (!env->ExceptionCheck()) {
            jclass cls = env->FindClass("java/io/IOException");
            if (cls) env->ThrowNew(cls, error.what());
        }
    }
}
