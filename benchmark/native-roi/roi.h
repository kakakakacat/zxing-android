// SPDX-License-Identifier: Apache-2.0
#pragma once
#include <cstddef>
#include <cstdint>
// Source and destination must be valid, nonoverlapping allocations of the declared lengths.
using RoiPack = int (*)(const uint8_t*, size_t, uint8_t*, size_t,
                       size_t, size_t, size_t, size_t, size_t, size_t);
