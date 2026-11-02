#!/usr/bin/env python3
"""Build the virtual-scene poster the camera spike decodes on an emulator.

    tools/maint/make-qr-poster.py out.png
    adb -s emulator-5556 emu virtualscene-image wall out.png

Four copies of one committed QR sample, tiled. One centred copy is not enough: the emulator's
virtual camera does not face the middle of the wall, so a single QR lands off to one side and
falls outside the scanner's centred framing rectangle. That produced a `found=false` that
looked like a decoder failure and was a framing one -- the screenshot in
reports/camera-spike-poster-single-qr-api36.png is what it actually looked like.

The source is scanner/src/androidTest/assets/frames/qrcode-2__10.png, which is already in the
tree with its expected text, so the poster carries no new licensed image.
"""
import sys
from PIL import Image

SOURCE = "scanner/src/androidTest/assets/frames/qrcode-2__10.png"
POSTER = 1600
TILE = 616
MARGIN = 140


def main(out_path: str) -> None:
    tile = Image.open(SOURCE).convert("L").resize((TILE, TILE), Image.NEAREST)
    canvas = Image.new("L", (POSTER, POSTER), 255)
    far = POSTER - MARGIN - TILE
    for xy in ((MARGIN, MARGIN), (far, MARGIN), (MARGIN, far), (far, far)):
        canvas.paste(tile, xy)
    canvas.convert("RGB").save(out_path)
    print(out_path)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
