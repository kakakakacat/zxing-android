# L4 baseline — DN-ENG41, both camera backends, 2026-11-02

The first L4 numbers this project has. Two soaks of 12 minutes, one per camera backend,
back to back in one session. ~125 samples each, 5 s apart. Miss path: nothing was in front
of the lens, so every frame decoded nothing — which §10 measured as the most expensive case
and is what live preview does almost every frame.

Result files: `l4-{camera1,camerax}-DN-ENG41-20261102.json`.

Conditions, recorded as warnings in both files: the device sat at **thermal status 3** for
the whole of both soaks, so these are numbers for a throttled device; and it was on charge,
so **battery drain was not measured** and must not be read off this run.

## What was measured

| | camera1 | camerax | delta |
| --- | --- | --- | --- |
| CPU, % of one core | **74** [64–88] | **118** [103–130] | **+59%** |
| RSS | 150,293 kB | 165,193 kB | +14,900 kB |
| Total PSS | 61,136 kB | 77,731 kB | +16,595 kB |
| Native heap | 7,731 kB | 19,897 kB | +12,166 kB |
| Java heap | 11,764 kB | 11,899 kB | +135 kB |
| Threads | 27 | 44 | +17 |
| GC collections | 311 | 298 | −13 |
| GC pause, total | 1,265 ms | 1,113 ms | −152 ms |
| GC time, total | 40,946 ms | 48,133 ms | +7,187 ms |

Means over the soak, with min–max in brackets for CPU.

## The finding: CameraX costs about 60% more CPU to keep scanning

74% of one core against 118%, with ranges that do not come close to touching — 64–88
against 103–130. On a sustained workload, measured on the same device minutes apart, on the
same decode path.

It is not memory pressure and it is not GC: the Java heap is identical to within 1%, and
CameraX actually collects slightly *less often* and pauses slightly *less*. The extra cost
is elsewhere — 17 more threads and 12 MB more native heap point at CameraX's own pipeline
(it ships `libimage_processing_util_jni.so` and `libsurface_util_jni.so`, and binds an
analysis use case with its own executor and buffer queue), but nothing here profiles inside
it, so that is where to look next and not a conclusion.

**This is the number the Phase 6 decision record did not have.** That record weighed startup
latency, APK size and maintenance. Steady-state CPU was listed under "what was *not*
measured" — "whether CameraX's steady-state frame delivery costs more or less CPU than
Camera1's is an L4 question and L4 does not exist". It exists now, and the answer is: more,
by a lot, on this device.

## Nothing leaks, and nothing degrades over twelve minutes

RSS slope is **+134 kB/min on camera1** and **+62 kB/min on camerax**, against a residual
spread of about 1,900 kB in both. Over the whole soak that predicts a rise well inside the
sample noise, which is why neither file carries an `RSS_GROWTH` warning. Thread counts are
flat. The first-half/second-half split is flat on every case for both backends — CPU 72→76
and 119→117, RSS 149,874→150,711 and 165,287→165,100.

So on the evidence here: no leak, no allocation treadmill, and no thermal cliff within
twelve minutes. The device was already throttled when both soaks started, which is the
honest qualification — a soak starting cold might find a cliff this one could not.

## What this does and does not settle

It does not reverse the Phase 6 decision, which rests on maintenance and deprecation rather
than performance, and it is one device. **It does raise the cost of adopting CameraX by
something the record did not price**, and it is a third independent measurement pointing the
same way as L3's "+200 ms to a frame": on this hardware, CameraX's frame path is the more
expensive one.

Read alongside L3's other finding — the Camera1 path blocks the main thread for ~400 ms at
startup — the two backends are not simply better and worse. Camera1 is cheaper to run and
janks the launch; CameraX launches smoothly and costs more to keep running.

## Not measured

- **The hit path.** Needs the fixed scene §11 still does not have.
- **Battery drain.** The phone was on charge.
- **A soak from a cold thermal state**, which this device cannot provide.
- **Anything beyond twelve minutes**, or on any other device.
- **Where CameraX's extra CPU actually goes.** That needs a profiler, not this harness.
