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

package io.github.kakakakacat.zxing.android

import io.github.kakakakacat.zxing.DecodeHintType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * `DecodeHintManager` decides what a hint means by comparing `DecodeHintType.getValueType()`
 * against a class literal, and in Kotlin a class literal is not always the class the Java
 * source said. `Boolean::class.java` is `boolean.class`, not `java.lang.Boolean.class`; the
 * object type needs `javaObjectType`. A comparison that silently never matches would make
 * every `Void` hint -- `TRY_HARDER`, `PURE_BARCODE`, `RETURN_CODABAR_START_END`,
 * `ALSO_INVERTED` -- parse as unsupported and be dropped, with a log line and no failure.
 *
 * These assertions are about the Kotlin-to-Java mapping rather than about this fork's code,
 * which is the point: nothing else here would notice it changing.
 */
class DecodeHintValueTypesTest {

  @Test
  fun kotlinClassLiteralsMatchTheJavaOnesDecodeHintManagerCompares() {
    assertSame(java.lang.Object::class.java, Any::class.java)
    assertSame(java.lang.String::class.java, String::class.java)
    // Void has no Kotlin counterpart, so `Void::class.java` is the java.lang class itself.
    assertSame(java.lang.Void::class.java, Void::class.java)
    assertSame(IntArray::class.java, intArrayOf().javaClass)
  }

  @Test
  fun theBoxedBooleanTrapIsReal() {
    // If this ever stops being true, the javaObjectType in DecodeHintManager is redundant
    // rather than wrong -- but while it holds, dropping it silently disables the branch.
    assertNotEquals(Boolean::class.java, Boolean::class.javaObjectType)
    assertSame(java.lang.Boolean::class.javaObjectType, Boolean::class.javaObjectType)
  }

  @Test
  fun everyValueTypeThisForkDeclaresIsOneDecodeHintManagerHandles() {
    val handled = setOf<Class<*>>(
        Any::class.java,
        Void::class.java,
        String::class.java,
        Boolean::class.javaObjectType,
        IntArray::class.java)
    // The three hints DecodeHintManager skips are specified another way.
    val skipped = setOf(DecodeHintType.CHARACTER_SET,
                        DecodeHintType.NEED_RESULT_POINT_CALLBACK,
                        DecodeHintType.POSSIBLE_FORMATS)
    val unhandled = DecodeHintType.values()
        .filter { it !in skipped }
        .filter { it.valueType !in handled }
    // Nothing is expected here. A new hint type with an unhandled value type is logged and
    // dropped at run time, which is exactly the kind of silence this pins down.
    assertEquals(emptyList<DecodeHintType>(), unhandled)
  }
}
