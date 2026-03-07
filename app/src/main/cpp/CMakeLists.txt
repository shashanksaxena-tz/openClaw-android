# CMakeLists.txt for llama.cpp JNI bridge
#
# This builds the llama_bridge shared library that provides JNI bindings
# for llama.cpp on Android. Requires the llama.cpp source to be placed
# in app/src/main/cpp/llama.cpp/ (as a git submodule or extracted archive).
#
# Setup:
#   cd app/src/main/cpp
#   git submodule add https://github.com/ggerganov/llama.cpp.git llama.cpp
#
# Or download a release:
#   curl -L https://github.com/ggerganov/llama.cpp/archive/refs/tags/b4000.tar.gz | tar xz
#   mv llama.cpp-b4000 llama.cpp

cmake_minimum_required(VERSION 3.22.1)
project(llama_bridge)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── llama.cpp configuration ──────────────────────────────────────────────────

set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# Check if llama.cpp source exists
if(NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(WARNING
        "llama.cpp source not found at ${LLAMA_CPP_DIR}. "
        "The JNI bridge will compile but model loading will be stubbed out. "
        "To enable local inference, add llama.cpp source: "
        "cd app/src/main/cpp && git submodule add https://github.com/ggerganov/llama.cpp.git"
    )

    # Build a stub library that returns errors for all operations
    add_library(llama_bridge SHARED llama_bridge.cpp)
    target_compile_definitions(llama_bridge PRIVATE LLAMA_STUB=1)
    target_link_libraries(llama_bridge android log)
    return()
endif()

# Build llama.cpp as a static library
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# Android-specific: enable NEON on arm64
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
endif()

add_subdirectory(${LLAMA_CPP_DIR} llama.cpp)

# ── JNI bridge library ───────────────────────────────────────────────────────

add_library(llama_bridge SHARED llama_bridge.cpp)

target_include_directories(llama_bridge PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
)

target_link_libraries(llama_bridge
    llama
    ggml
    common
    android
    log
)
