cmake_minimum_required(VERSION 3.22.1)
project(pixasr CXX)

set(ncnn_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ncnn/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

add_library(pixasr SHARED pixasr.cpp)
target_link_libraries(pixasr ncnn android)
# OpenMP estatico (no hay libomp.so que empaquetar) y paginas de 16 KB (Android 15+).
target_link_options(pixasr PRIVATE -static-openmp -fopenmp -Wl,-z,max-page-size=16384)
