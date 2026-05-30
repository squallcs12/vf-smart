cmake_minimum_required(VERSION 3.22.1)
project("vfsmart_native")

add_library(vfsmart_native SHARED audioswb.cpp)

find_library(log-lib log)
target_link_libraries(vfsmart_native ${log-lib})
