# A real barcode, in front of a real camera, through `ScannerView`

**2026-11-03, DN-ENG41 (Android 14, API 34, 640x960, no GMS), both camera backends.**

Until today every decode this project had driven off a camera was an emulator virtual
scene. `README.md` and `HANDOFF.md` both said so, and it was the one functional check left
between CameraX and the shipping default. It is done: a second Android device
(**DN-ENG81**, 1200x1920) displays a symbol this fork's own `:core` generated, and
DN-ENG41 reads it off that screen.

The harness is `tools/maint/scan-off-screen.sh`, which generates the symbol, **reads it
back before showing it**, displays it, and then runs N cold starts per backend.

## What was read

| Backend | Symbol | Runs | Decoded |
| --- | --- | --- | --- |
| Camera1 | `CODE_128` `ZXING-CAMERAX-001` | 6 | 6/6 exact |
| CameraX | `CODE_128` `ZXING-CAMERAX-001` | 6 | 6/6 exact |
| Camera1 | `EAN_13` `9780201310054` | 1 | exact, typed ISBN |
| CameraX | `EAN_13` `9780201310054` | 1 | exact, typed ISBN |

Screenshots, each showing the result screen with its thumbnail and result points drawn
over the symbol: `real-barcode-camerax-DN-ENG41-20261103.png`,
`real-barcode-camera1-DN-ENG41-20261103.png`.

## The backend is proven from the trace, not from the preference

The preference is set before the run and **checked after writing it** — the script refuses
rather than reports if it did not take, which is the trap §11 of `BENCHMARKS.md` records
twice. Beyond that, `atrace -a <pkg>` was captured over a whole session and carries
`ScannerTrace`'s own span:

```
S|zxing.backend.camerax|1   ... F|zxing.backend.camerax|1
S|zxing.camera.open|1       ... F|zxing.camera.open|1
S|zxing.first.frame|1       ... F|zxing.first.frame|1
S|zxing.first.scan|1        ... F|zxing.first.scan|1
```

**`zxing.first.scan` has never fired before.** `ScannerTrace` documents it as "camera ready
→ the first successful decode. Needs a barcode in front of the lens", and until today
nothing did. Both backends emit it now.

The intervals from those two single sessions, **which are not timings anyone may quote**:
neither device locks clocks here, DN-ENG41 is permanently thermally throttled, and the
symbol's distance and angle are whatever the desk happened to be. §6 keeps readings like
these out of every L layer. They are recorded only because they are in the same trace.

| | camera.open | first.frame | first.scan |
| --- | --- | --- | --- |
| Camera1 | 404 ms | 1164 ms | 3494 ms |
| CameraX | 1094 ms | 1039 ms | 3578 ms |

## The mojibake scan was not a defect, and not CameraX's

A scan taken by hand earlier the same morning left
`óóóó1234óóabózz` in the app's history, read from a physical label as `CODE_128`, and it
was reported as a CameraX problem. It is neither a corruption nor a camera-backend
difference:

- **`ó` is U+00F3, and Code 128 has a meaning for it.** `FNC4` shifts the following
  character into extended ASCII (+128); `'s'` (0x73) shifted is 0xF3. The symbol carries
  FNC4-shifted characters and the decoders render them, correctly, as `ó`.
- **Reproduced deliberately.** A `CODE_128` encoded from
  `ôsôsôsôs1234ôsôsabôszz` — `ô` being
  `Code128Writer`'s FNC4 escape — decodes to exactly `óóóó1234óóabózz` on the host, and
  off the screen on **both** camera backends, 3/3 each.
- **So it is content, not transport.** Camera1 and CameraX returned the same string every
  time. Nothing here distinguishes the backends.

**What it did turn up is a real round-trip asymmetry, and it is upstream's by design.**
`Code128Writer` reads U+00F1..U+00F4 in its *input* as the FNC1..FNC4 escapes, while the
reader emits U+00F1..U+00F4 for *decoded* FNC content. So:

```
encode("óóóó1234óóabózz")  ->  decode  ->  "1234abzz"
```

The four escape code points cannot survive a round trip through Code 128 in this library,
and an application that lets users encode arbitrary text can hand them a symbol that does
not say what they typed. Nothing in this fork causes it and nothing here changes it; it is
written down so the next person who sees an `ó` does not go looking for a decoder bug.

## What this still does not verify

- **Auto-focus on the CameraX path**, still. DN-ENG41 reports auto-focus OFF only; a
  screen at a fixed distance does not exercise focus on either backend.
- **A symbol on paper**, at an angle, in poor light, or moving. A second device's panel is
  a bright, flat, perfectly rendered target — the easy case, deliberately, because the
  question being answered was "does any symbol get through at all".
- **Anything about speed.** See above.
- **Android 15 and 16 hardware.** The Mix5 was not used for this; no Android 16 device
  exists here.
