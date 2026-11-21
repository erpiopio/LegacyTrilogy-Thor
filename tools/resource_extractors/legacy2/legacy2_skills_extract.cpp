// Original ALFP combat illustrations, decoded with the bundled DragonByteZ.
// Generated on the device from its verified imported ROM, never bundled artwork.
#include "dragonbytez/rom.hpp"
#include "dragonbytez/compression.hpp"
#include <algorithm>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

namespace legacy2 {
namespace {
void write(const std::filesystem::path& p,const std::vector<std::uint8_t>& bytes) {
    std::ofstream f(p,std::ios::binary);
    f.write(reinterpret_cast<const char*>(bytes.data()),bytes.size());
    if(!f)throw std::runtime_error("Cannot write skill illustration");
}
void picture(const std::filesystem::path& p,unsigned w,unsigned h,unsigned palette,
             const std::vector<std::uint8_t>& pixels) {
    if(!w||!h||w>64||h>64||pixels.size()!=w*h)
        throw std::runtime_error("Invalid skill illustration dimensions");
    std::vector<std::uint8_t> data={static_cast<std::uint8_t>(w),
        static_cast<std::uint8_t>(h),static_cast<std::uint8_t>(palette),0};
    data.insert(data.end(),pixels.begin(),pixels.end());write(p,data);
}
std::vector<std::uint8_t> frame(const dragonbytez::Rom& rom,std::size_t offset,
                              unsigned& width,unsigned& height) {
    width=rom.u8(offset+2);height=rom.u8(offset+3);
    const auto valid=[](unsigned n){return n==8||n==16||n==32||n==64;};
    if(!valid(width)||!valid(height))throw std::runtime_error("Invalid combat frame");
    auto raw=dragonbytez::decompress_container(rom,
        rom.pointer_to_offset(rom.u32(offset+8)),width*height).data;
    if(raw.size()!=width*height)throw std::runtime_error("Incomplete combat frame");
    std::vector<std::uint8_t> pixels(raw.size());
    const unsigned attr=rom.u32(offset+4)>>16;
    for(unsigned y=0;y<height;++y)for(unsigned x=0;x<width;++x){
        unsigned sx=(attr&0x1000)?width-1-x:x;
        unsigned sy=(attr&0x2000)?height-1-y:y;
        pixels[y*width+x]=raw[((sy/8)*(width/8)+sx/8)*64+(sy%8)*8+sx%8];
    }
    return pixels;
}
}
void extract_skills(const std::string& source,const std::filesystem::path& output) {
    dragonbytez::Rom rom(source);
    if(rom.size()!=8388608||rom.game_code()!="ALFP")
        throw std::runtime_error("Skills require ALFP ROM");
    std::filesystem::create_directories(output);
    write(output/"menu.pal",rom.slice(0x6A85F8,512));
    write(output/"objects.pal",rom.slice(0x6A87F8,512));
    // STATUS 0800E1CA..E1D8: 0802B288+3C+character*8+technique*4.
    for(unsigned character=0;character<5;++character)for(unsigned slot=0;slot<2;++slot){
        auto pointer=rom.u32(0x2B2C4+character*8+slot*4);
        auto pixels=dragonbytez::decompress_container(rom,
            rom.pointer_to_offset(pointer),144).data;
        if(pixels.size()!=144)throw std::runtime_error("Invalid STATUS skill icon");
        // The original linear menu icon uses index 1 for its surrounding black.
        // Flood only that surrounding area to transparent, preserving enclosed ink.
        // Native destination rectangle at 0800E1DC..E1EA is (32,4,50,12): 18x8.
        std::vector<unsigned> pending;
        for(unsigned x=0;x<18;++x){pending.push_back(x);pending.push_back(126+x);}
        for(unsigned y=0;y<8;++y){pending.push_back(y*18);pending.push_back(y*18+17);}
        while(!pending.empty()){
            unsigned i=pending.back();pending.pop_back();
            if(pixels[i]!=1)continue;pixels[i]=0;
            if(i%18)pending.push_back(i-1);if(i%18<17)pending.push_back(i+1);
            if(i>=18)pending.push_back(i-18);if(i<126)pending.push_back(i+18);
        }
        picture(output/("special-"+std::to_string(character)+"-"+
            std::to_string(slot+1)+".idx"),18,8,0,pixels);
    }
    // 08015786 reads record+8 as the live template; template+1 is its sprite ID.
    // DragonByteZ's ALFP sprite table and twelve-byte frame format are reused.
    for(unsigned id=0;id<22;++id){
        unsigned sprite=rom.u8(0x2A4E8+id*20+1);
        if(sprite<7||sprite>156)throw std::runtime_error("Invalid combat sprite ID");
        auto structure=rom.pointer_to_offset(rom.u32(0x692E68+(sprite-7)*4));
        unsigned animation=rom.u8(structure+2)>6?6:0;
        auto sequence=rom.u32(structure+0x4C+(animation*4+1)*4);
        if(!rom.is_rom_pointer(sequence))sequence=rom.u32(structure+0x4C+animation*16);
        auto offset=rom.pointer_to_offset(sequence);
        // Second frame of the melee sequence shows the actual strike.
        if(animation==6&&rom.u8(offset+12+2)!=0&&rom.u8(offset+12+3)!=0)offset+=12;
        unsigned w=0,h=0;auto pixels=frame(rom,offset,w,h);
        picture(output/("physical-"+std::to_string(id)+".idx"),w,h,1,pixels);
    }
    std::ofstream manifest(output/"manifest.json");
    manifest<<"{\"extractorVersion\":1,\"romCode\":\"ALFP\","
        "\"romSha1\":\"db36ff52fcd63f753f9d66439aa3d2216701c326\","
        "\"specialCount\":10,\"physicalCount\":22}";
    if(!manifest)throw std::runtime_error("Cannot finish skill manifest");
}
}
#ifdef LEGACY2_SKILLS_CLI
int main(int argc,char**argv){
    if(argc!=3)return 2;
    try{legacy2::extract_skills(argv[1],argv[2]);return 0;}
    catch(const std::exception&e){std::cerr<<e.what()<<'\n';return 1;}
}
#endif
