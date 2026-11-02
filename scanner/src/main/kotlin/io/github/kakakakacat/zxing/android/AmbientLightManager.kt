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
import io.github.kakakakacat.zxing.android.camera.ScannerCamera
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
  private val torchPolicy = TorchPolicy()
  private var camera: ScannerCamera? = null
  private var lightSensor: Sensor? = null

  fun start(camera: ScannerCamera?) {
    this.camera = camera
    // A camera that has just been opened has its torch off, whatever the policy last
    // asked for on the previous one.
    torchPolicy.reset()
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
      camera = null
      lightSensor = null
      torchPolicy.reset()
    }
  }

  override fun onSensorChanged(sensorEvent: SensorEvent) {
    val manager = camera ?: return
    // Was defect A-12: this called setTorch on every callback, five a second, each one
    // reading the camera's parameters and each change tearing down and rebuilding the
    // AutoFocusManager. TorchPolicy says when a change is actually wanted, and null --
    // the common case by far -- means the camera is not touched at all.
    val wanted = torchPolicy.decide(sensorEvent.values[0], sensorEvent.timestamp) ?: return
    manager.setTorch(wanted)
  }

  override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    // do nothing
  }

}
