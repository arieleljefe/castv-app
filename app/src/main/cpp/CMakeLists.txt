# [IA-2a] Puente propio de CASTV con whisper.cpp v1.9.3 (MIT)
cmake_minimum_required(VERSION 3.22.1)
project(castv_whisper C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Todo estático: whisper y ggml quedan dentro de libcastv_whisper.so
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
# ggml: solo CPU, sin OpenMP, sin optimizar para la máquina que compila
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

# El build de GitHub es Debug (assembleDebug): sin esto ggml iría sin optimizar
set(CMAKE_C_FLAGS_DEBUG   "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG")

add_subdirectory(whisper.cpp)

add_library(castv_whisper SHARED jni_whisper.cpp)
target_include_directories(castv_whisper PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/include)
find_library(log-lib log)
target_link_libraries(castv_whisper whisper ${log-lib})
