// ALFP regional Scouter resources, decoded with DragonByteZ.
#include "dragonbytez/rom.hpp"
#include "dragonbytez/compression.hpp"
#include <filesystem>
#include <fstream>
#include <stdexcept>
#include <string>

namespace legacy2 {
void extract_scouter(const std::string& source, const std::filesystem::path& out) {
    dragonbytez::Rom rom(source);
    if (rom.size()!=8388608 || rom.u32(0xAC)!=0x50464C41)
        throw std::runtime_error("Scouter requires ALFP");
    constexpr unsigned base=0x08000000, table=0x3039E4;
    if (rom.u32(0x303AF4)!=68 || rom.u32(0x303AF8)!=base+table)
        throw std::runtime_error("Invalid ALFP Scouter table");
    std::filesystem::create_directories(out);
    std::ofstream meta(out / "manifest.json");
    meta << "{\"extractorVersion\":1,\"romSha1\":\"db36ff52fcd63f753f9d66439aa3d2216701c326\",\"tileSize\":8,\"atlasTiles\":128,\"regions\":[";
    auto dump=[&](const std::string& name,const std::vector<std::uint8_t>& data) {
        std::ofstream f(out/name,std::ios::binary);
        f.write(reinterpret_cast<const char*>(data.data()),data.size());
        if (!f) throw std::runtime_error("Cannot write Scouter resource");
    };
    // The original menu installs its shared palette (ALFP C2E0 / DE02).
    dump("background.pal",rom.slice(0x6A85F8,512));
    dump("objects.pal",rom.slice(0x6A85F8,512));
    // Regional marker descriptor at 08303D44 points to this 8x8 image.
    dump("player-marker.indices",rom.slice(0x303AFC,64));
    unsigned total=0;
    for(unsigned i=0;i<68;++i) {
        unsigned g=rom.pointer_to_offset(rom.u32(table+i*4));
        auto gfx=dragonbytez::decompress_container(rom,rom.pointer_to_offset(rom.u32(g)),65536).data;
        std::string file="region_"+std::to_string(i)+".tiles";
        dump(file,gfx);
        unsigned count=rom.u32(g+20);
        if(count>512)throw std::runtime_error("Invalid room count");
        meta << (i?",":"") << "{\"id\":"<<i<<",\"source\":"<<(g+base)<<",\"graphics\":\""<<file<<"\",\"graphicsBytes\":"<<gfx.size()<<",\"rooms\":[";
        for(unsigned j=0;j<count;++j) {
            unsigned p=rom.pointer_to_offset(rom.u32(g+24+j*4));
            unsigned a=rom.u8(p)|(unsigned(rom.u8(p+1))<<8);
            unsigned bit=rom.u8(p+2)|(unsigned(rom.u8(p+3))<<8);
            int b[8];for(unsigned k=0;k<8;++k)b[k]=static_cast<int32_t>(rom.u32(p+4+k*4));
            unsigned bytes=0;
            for(unsigned k=0;k<8;k+=4) {
                if(b[k+2]<b[k]||b[k+3]<b[k+1]||b[k+2]-b[k]>256||b[k+3]-b[k+1]>256)
                    throw std::runtime_error("Invalid room rectangle");
                bytes+=unsigned((b[k+2]-b[k])*(b[k+3]-b[k+1]))*2;
            }
            std::string room="room_"+std::to_string(i)+"_"+std::to_string(j)+".tilemap";
            dump(room,rom.slice(p+36,bytes));
            meta << (j?",":"") << "{\"area\":"<<a<<",\"visitedBit\":"<<bit<<",\"source\":"<<(p+base)<<",\"tilemap\":\""<<room<<"\",\"rectangles\":[";
            for(unsigned k=0;k<8;++k)meta<<(k?",":"")<<b[k];
            meta<<"]}";++total;
        }
        meta<<"]}";
    }
    meta<<"],\"roomCount\":"<<total<<"}";
    if(!meta)throw std::runtime_error("Cannot write Scouter manifest");
}
}
#ifdef LEGACY2_SCOUTER_CLI
#include <iostream>
int main(int argc,char** argv) {
    try {if(argc!=3)return 2;legacy2::extract_scouter(argv[1],argv[2]);}
    catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}
}
#endif
