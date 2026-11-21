cmake_minimum_required(VERSION 3.22.1)
project(LegacyTrilogyAudio LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)

get_filename_component(DRAGONBYTEZ_DIR
    "${CMAKE_CURRENT_LIST_DIR}/../../../../../tools/DragonByteZ"
    ABSOLUTE)

add_subdirectory("${DRAGONBYTEZ_DIR}"
    "${CMAKE_CURRENT_BINARY_DIR}/dragonbytez")

add_library(trilogy_audio SHARED TrilogyAudioNative.cpp)

target_include_directories(trilogy_audio PRIVATE
    "${DRAGONBYTEZ_DIR}/include")

target_link_libraries(trilogy_audio PRIVATE dragonbytez_core)


# Extractores independientes para preparar recursos desde ROMs importadas.
get_filename_component(TRILOGY_RESOURCES
    "${CMAKE_CURRENT_LIST_DIR}/../../../../../tools/resource_extractors"
    ABSOLUTE)

add_library(trilogy_legacy2_resources SHARED
    "${TRILOGY_RESOURCES}/legacy2/legacy2_scouter_extract.cpp"
    "${TRILOGY_RESOURCES}/legacy2/legacy2_scouter_jni.cpp"
    "${TRILOGY_RESOURCES}/legacy2/legacy2_items_extract.cpp"
    "${TRILOGY_RESOURCES}/legacy2/legacy2_items_jni.cpp"
    "${TRILOGY_RESOURCES}/legacy2/legacy2_skills_extract.cpp"
    "${TRILOGY_RESOURCES}/legacy2/legacy2_skills_jni.cpp"
    "${TRILOGY_RESOURCES}/legacy2/dragonbytez/src/rom.cpp"
    "${TRILOGY_RESOURCES}/legacy2/dragonbytez/src/compression.cpp"
)
target_include_directories(trilogy_legacy2_resources PRIVATE
    "${TRILOGY_RESOURCES}/legacy2/dragonbytez/include")
target_compile_features(trilogy_legacy2_resources PRIVATE cxx_std_17)
target_link_libraries(trilogy_legacy2_resources PRIVATE log)

add_library(trilogy_buus_maps SHARED
    "${TRILOGY_RESOURCES}/buusfury/thor_maps_jni.cpp"
    "${TRILOGY_RESOURCES}/buusfury/thor_equipment_jni.cpp"
    "${TRILOGY_RESOURCES}/buusfury/thor_inventory_jni.cpp"
    "${TRILOGY_RESOURCES}/buusfury/dragonbytez/src/analysis.cpp"
    "${TRILOGY_RESOURCES}/buusfury/dragonbytez/src/compression.cpp"
    "${TRILOGY_RESOURCES}/buusfury/dragonbytez/src/png.cpp"
    "${TRILOGY_RESOURCES}/buusfury/dragonbytez/src/rom.cpp"
)
target_include_directories(trilogy_buus_maps PRIVATE
    "${TRILOGY_RESOURCES}/buusfury/dragonbytez/include")
target_compile_features(trilogy_buus_maps PRIVATE cxx_std_17)

target_compile_options(trilogy_buus_maps PRIVATE
    -O2
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)
target_link_options(trilogy_buus_maps PRIVATE
    "-Wl,--gc-sections"
    "-Wl,--no-undefined"
)

