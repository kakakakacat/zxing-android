/*
 * Copyright (C) 2012 ZXing authors
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

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.kakakakacat.zxing.android.camera.CameraManager
import io.github.kakakakacat.zxing.android.camera.FrontLightMode

/**
 * Detects ambient light and switches on the front light when very dark, and off again when
 * sufficiently light.
 *
 * @author Sean Owen
 * @author Nikolaus Huber
 */
class AmbientLightManager(
    private val context: Context,
    frontLightMode: FrontLightMode?
) : SensorEventListener {

  private val frontLightMode: FrontLightMode = frontLightMode ?: FrontLightMode.OFF
  private var cameraManager: CameraManager? = null
  private var lightSensor: Sensor? = null

  fun start(cameraManager: CameraManager?) {
    this.cameraManager = cameraManager
    if (frontLightMode == FrontLightMode.AUTO) {
      val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
      val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
      lightSensor = sensor
      if (sensor != null) {
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
      }
    }
  }

  fun stop() {
    if (lightSensor != null) {
      val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
      sensorManager.unregisterListener(this)
      cameraManager = null
      lightSensor = null
    }
  }

  override fun onSensorChanged(sensorEvent: SensorEvent) {
    val ambientLightLux = sensorEvent.values[0]
    val manager = cameraManager
    if (manager != null) {
      if (ambientLightLux <= TOO_DARK_LUX) {
        manager.setTorch(true)
      } else if (ambientLightLux >= BRIGHT_ENOUGH_LUX) {
        manager.setTorch(false)
      }
    }
  }

  override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    // do nothing
  }

  private companion object {
    // `private` on each one, not just on the companion: a `const val` in a private
    // companion is still emitted as a *public* static field on the enclosing class, and
    // these were private in Java. The API diff is what noticed.
    private const val TOO_DARK_LUX = 45.0f
    private const val BRIGHT_ENOUGH_LUX = 450.0f
  }
}
