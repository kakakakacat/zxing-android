/*
 * Copyright (C) 2008 ZXing authors
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

package io.github.kakakakacat.zxing.android.share

import android.graphics.drawable.Drawable

internal class AppInfo(
    private val packageName: String,
    private val label: String,
    private val icon: Drawable?
) : Comparable<AppInfo> {

  fun getPackageName(): String = packageName

  fun getIcon(): Drawable? = icon

  override fun toString(): String = label

  override fun compareTo(other: AppInfo): Int = label.compareTo(other.label)

  override fun hashCode(): Int = label.hashCode()

  override fun equals(other: Any?): Boolean = other is AppInfo && label == other.label
}
