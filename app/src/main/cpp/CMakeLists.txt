cmake_minimum_required(VERSION 3.22.1)
project(multituner LANGUAGES CXX)

add_library(multituner SHARED
    yin.cpp
    tuner_jni.cpp
)

target_compile_features(multituner PRIVATE cxx_std_17)
target_compile_options(multituner PRIVATE -Wall -Wextra)

find_library(log-lib log)
target_link_libraries(multituner ${log-lib})
