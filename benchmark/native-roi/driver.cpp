// SPDX-License-Identifier: Apache-2.0
#include "roi.h"
#include <dlfcn.h>
#include <chrono>
#include <fstream>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>
using Clock = std::chrono::steady_clock;
static std::vector<uint8_t> read(const std::string& path) {
  std::ifstream in(path, std::ios::binary);
  if (!in) throw std::runtime_error("cannot read fixture");
  return {std::istreambuf_iterator<char>(in), std::istreambuf_iterator<char>()};
}
int main(int argc, char** argv) {
  if (argc != 6) return 2;
  void* lib = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
  if (!lib) { std::cerr << dlerror(); return 2; }
  auto pack = reinterpret_cast<RoiPack>(dlsym(lib, "roi_pack"));
  if (!pack) return 2;
  std::ifstream manifest(argv[2]);
  const int samples = std::stoi(argv[3]);
  const double warm_ms = std::stod(argv[4]);
  const double target_ms = std::stod(argv[5]);
  std::string line;
  while (std::getline(manifest, line)) {
    std::istringstream fields(line);
    std::string name, source_path, expected_path;
    size_t w,h,row,pixel,left,top;
    if (!(fields >> name >> source_path >> expected_path >> w >> h >> row >> pixel >> left >> top)) return 2;
    auto src = read(source_path), expected = read(expected_path);
    std::vector<uint8_t> out(expected.size(), 0);
    auto batch = [&](size_t n) {
      auto start = Clock::now();
      for (size_t i = 0; i < n; ++i)
        if (pack(src.data(), src.size(), out.data(), out.size(), w,h,row,pixel,left,top) != 0)
          throw std::runtime_error("kernel rejected fixture");
      return std::chrono::duration<double,std::nano>(Clock::now()-start).count();
    };
    batch(1);
    if (out != expected) throw std::runtime_error("incorrect output before timing");
    size_t iterations = 1;
    while (batch(iterations) < target_ms * 1e6 && iterations < (1U<<22)) iterations *= 2;
    auto warm_start = Clock::now();
    while (std::chrono::duration<double,std::milli>(Clock::now()-warm_start).count() < warm_ms) batch(iterations);
    std::cout << "{\"case\":\"" << name << "\",\"iterations\":" << iterations << ",\"ns_per_copy\":[";
    for (int i=0; i<samples; ++i) {
      const double elapsed = batch(iterations) / iterations;
      if (out != expected) throw std::runtime_error("incorrect output after timing");
      if (i) std::cout << ',';
      std::cout << elapsed;
    }
    std::cout << "]}" << std::endl;
  }
  dlclose(lib);
}
