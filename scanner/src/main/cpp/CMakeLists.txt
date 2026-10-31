# The native decoder backend: zxing-cpp behind the FrameDecoder seam.
#
# Nothing here may be read as a performance claim. Phase 5 item 10 requires an A/B of
# this backend against the Java one on the fixed dataset before any default changes or
# any speedup is stated; see docs/maint/BENCHMARKS.md.

cmake_minimum_required(VERSION 3.22.1)
project(zxingnative CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ZXING_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/zxing-cpp")
if (NOT EXISTS "${ZXING_CPP_DIR}/core/CMakeLists.txt")
  message(FATAL_ERROR
      "third_party/zxing-cpp is empty. It is a git submodule pinned to a release tag; "
      "run: git submodule update --init --recursive")
endif()

# Readers only. The writers pull in a bundled libzint that nothing here uses, and every
# byte of it would ship in the AAR.
set(ZXING_READERS ON  CACHE BOOL "" FORCE)
set(ZXING_WRITERS OFF CACHE BOOL "" FORCE)
set(ZXING_C_API   OFF CACHE BOOL "" FORCE)
set(ZXING_EXAMPLES OFF CACHE BOOL "" FORCE)
set(ZXING_UNIT_TESTS OFF CACHE BOOL "" FORCE)
set(ZXING_BLACKBOX_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory("${ZXING_CPP_DIR}/core" "${CMAKE_CURRENT_BINARY_DIR}/zxing-cpp-core")

add_library(zxingnative SHARED zxing_native.cpp)

target_link_libraries(zxingnative PRIVATE ZXing::ZXing log)

# 16 KB page alignment, mandatory for Android 15+ on 64-bit. NDK r28 and later default to
# it, but a default is not a guarantee across toolchain upgrades and this is not a
# failure anyone would notice until an app refuses to load on a 16 KB device. Stated
# explicitly here, and verified on the built artifact by the :scanner:verifyNativeAlignment
# Gradle task, which fails the build rather than trusting this line.
target_link_options(zxingnative PRIVATE "-Wl,-z,max-page-size=16384")

target_compile_options(zxingnative PRIVATE -fvisibility=hidden -ffunction-sections -fdata-sections)
target_link_options(zxingnative PRIVATE "-Wl,--gc-sections")
