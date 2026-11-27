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

# [VIDEO-MP3-1] LAME 3.100 (LGPL) compilado aquí mismo (código en cpp/lame/, sin librerías de terceros)
file(GLOB LAME_SRC ${CMAKE_CURRENT_SOURCE_DIR}/lame/libmp3lame/*.c)
add_library(lame_static STATIC ${LAME_SRC})
target_include_directories(lame_static PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/lame/include
                                       PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/lame/libmp3lame)
target_compile_definitions(lame_static PRIVATE STDC_HEADERS=1 HAVE_STDINT_H=1 HAVE_LIMITS_H=1
                           HAVE_STRCHR=1 HAVE_MEMCPY=1 LAME_LIBRARY_BUILD=1 ieee754_float32_t=float)   # float32 de LAME (en su configure sale de config.h)
target_compile_options(lame_static PRIVATE -fPIC -w -Wno-implicit-function-declaration
                       -Wno-int-conversion -Wno-incompatible-pointer-types)
add_library(castv_lame SHARED jni_lame.cpp)
target_link_libraries(castv_lame lame_static ${log-lib})
