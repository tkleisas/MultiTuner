cmake_minimum_required(VERSION 3.22.1)
project(multituner LANGUAGES CXX)

add_library(multituner SHARED
    yin.cpp
    tuner_jni.cpp
    metronome_jni.cpp
    cassette_jni.cpp
    audio/Transport.cpp
    audio/Metronome.cpp
    audio/DrumSynth.cpp
    audio/DrumMachine.cpp
    audio/TapeEmulation.cpp
    audio/Reverb.cpp
    audio/Delay.cpp
    audio/CassettePlayer.cpp
    audio/MetronomeEngine.cpp
    audio/InternalRecorder.cpp
)

target_compile_features(multituner PRIVATE cxx_std_17)
target_compile_options(multituner PRIVATE -Wall -Wextra)
target_include_directories(multituner PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    target_link_options(multituner PRIVATE "-Wl,-z,max-page-size=16384")
endif()

find_package(oboe REQUIRED CONFIG)
find_library(log-lib log)
target_link_libraries(multituner oboe::oboe ${log-lib})
