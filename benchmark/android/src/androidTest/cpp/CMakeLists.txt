cmake_minimum_required(VERSION 3.22.1)
project(roiPack LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
add_library(roiPack SHARED roi_jni.cpp roi_kernel.cpp)
target_compile_options(roiPack PRIVATE -O3 -DNDEBUG -fvisibility=hidden -ffunction-sections -fdata-sections)
target_link_options(roiPack PRIVATE -Wl,--gc-sections -Wl,-z,max-page-size=16384)
