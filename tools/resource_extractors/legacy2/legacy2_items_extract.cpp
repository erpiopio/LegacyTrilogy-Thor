
// Original ALFP inventory graphics extraction for Thor.
// Reads only the user's supplied ROM. No bundled artwork.
#include "dragonbytez/rom.hpp"
#include "dragonbytez/compression.hpp"

#include <array>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

namespace legacy2 {

static void write_bytes(
    const std::filesystem::path& path,
    const std::vector<std::uint8_t>& data) {

    std::ofstream f(path, std::ios::binary);
    if (!f) throw std::runtime_error("Cannot create resource");

    f.write(
        reinterpret_cast<const char*>(data.data()),
        static_cast<std::streamsize>(data.size())
    );

    if (!f) throw std::runtime_error("Cannot write resource");
}

static std::vector<std::uint8_t> untile_icon(
    const std::vector<std::uint8_t>& input,
    unsigned width) {

    if (input.size() != width * width || width % 8 != 0)
        throw std::runtime_error("Invalid item image dimensions");

    std::vector<std::uint8_t> output(input.size());
    unsigned tiles_per_row = width / 8;

    for (unsigned y = 0; y < width; ++y) {
        for (unsigned x = 0; x < width; ++x) {

            unsigned tile_x = x / 8;
            unsigned tile_y = y / 8;

            unsigned offset =
                (tile_y * tiles_per_row + tile_x) * 64
                + (y % 8) * 8
                + (x % 8);

            output[y * width + x] = input[offset];
        }
    }

    return output;
}

void extract_items(
    const std::string& source,
    const std::filesystem::path& output) {

    dragonbytez::Rom rom(source);

    if (rom.size() != 8388608 ||
        rom.u32(0xAC) != 0x50464C41)
        throw std::runtime_error("Inventory requires ALFP ROM");

    constexpr unsigned table = 0x696BC8;
    constexpr unsigned count = 46;
    constexpr unsigned stride = 56;

    // Preserve the menu palette as an indexed GBA palette.
    constexpr unsigned palette_offset = 0x6A87F8;

    std::filesystem::create_directories(output);

    write_bytes(
        output / "items.pal",
        rom.slice(palette_offset, 512)
    );

    // Paleta del menú original para las imágenes ampliadas.
    write_bytes(
        output / "items-large.pal",
        rom.slice(0x6A85F8, 512)
    );

    std::ofstream manifest(output / "manifest.json");

    if (!manifest)
        throw std::runtime_error("Cannot create item manifest");

    manifest
        << "{\"extractorVersion\":3,"
        << "\"romCode\":\"ALFP\","
        << "\"romSha1\":\"db36ff52fcd63f753f9d66439aa3d2216701c326\","
        << "\"palette\":\"items.pal\","
        << "\"items\":[";

    constexpr std::array<unsigned, 4> fields = {
        0x14, 0x24, 0x28, 0x2C
    };

    unsigned total = 0;

    for (unsigned id = 0; id < count; ++id) {

        if (id) manifest << ",";

        manifest << "{\"id\":" << id << ",\"graphics\":{";

        bool first = true;

        for (unsigned field : fields) {

            std::uint32_t pointer =
                rom.u32(table + id * stride + field);

            if (pointer == 0)
                continue;

            if (!rom.is_rom_pointer(pointer))
                throw std::runtime_error(
                    "Invalid item graphic pointer"
                );

            auto decoded = dragonbytez::decompress_container(
                rom,
                rom.pointer_to_offset(pointer),
                1024
            ).data;

            if (decoded.size() != 256 &&
                decoded.size() != 1024)
                throw std::runtime_error(
                    "Unexpected item graphic dimensions"
                );

            unsigned width =
                decoded.size() == 256 ? 16 : 32;

            auto pixels = decoded;

            std::string filename =
                "item_" + std::to_string(id)
                + "_" + std::to_string(field) + ".idx";

            write_bytes(output / filename, pixels);

            if (!first) manifest << ",";
            first = false;

            manifest
                << "\"" << field << "\":{"
                << "\"file\":\"" << filename << "\","
                << "\"width\":" << width << ","
                << "\"height\":" << width
                << "}";

            ++total;
        }

        manifest << "}}";
    }

    manifest << "],\"graphicsCount\":" << total << "}";
    manifest.close();

    if (!manifest)
        throw std::runtime_error("Cannot finish item manifest");
}

} // namespace legacy2

#ifdef LEGACY2_ITEMS_CLI
int main(int argc, char** argv) {
    if (argc != 3) return 2;
    try {
        legacy2::extract_items(argv[1], argv[2]);
        return 0;
    } catch (const std::exception& e) {
        std::cerr << e.what() << '\n';
        return 1;
    }
}
#endif
