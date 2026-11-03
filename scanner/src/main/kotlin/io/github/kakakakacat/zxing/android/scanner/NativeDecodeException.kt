/*
 * Copyright (C) 2026 ZXing Android maintenance fork contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.kakakakacat.zxing.android.scanner

/**
 * The decoder itself failed on a frame.
 *
 * Not "this frame holds no barcode", which is an ordinary `null` return, and not "this
 * frame is not one I can read", which is an [IllegalArgumentException]. This is zxing-cpp
 * throwing out of `ReadBarcode` — an allocation failure, or a defect.
 *
 * It exists so that the three can be told apart. Until it did, a C++ exception was caught
 * in the JNI layer and the frame was reported as holding nothing, which is the one answer
 * that makes a broken decoder look like an empty picture.
 *
 * The scanning path treats it as a lost frame: logged once per distinct message, counted
 * in [NativeFrameDecoder.diagnostics], and scanning continues. A caller driving the
 * decoder directly gets it thrown.
 *
 * Constructed from the JNI layer by `ThrowNew`, which needs the single-String
 * constructor.
 */
internal class NativeDecodeException(message: String) : RuntimeException(message)
