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

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.BinaryBitmap;
import io.github.kakakakacat.zxing.EncodeHintType;
import io.github.kakakakacat.zxing.MultiFormatReader;
import io.github.kakakakacat.zxing.MultiFormatWriter;
import io.github.kakakakacat.zxing.RGBLuminanceSource;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.common.BitMatrix;
import io.github.kakakakacat.zxing.common.HybridBinarizer;
import io.github.kakakakacat.zxing.multi.GenericMultipleBarcodeReader;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumMap;
import javax.imageio.ImageIO;

/**
 * Draws one symbol, centred and large, on a sheet a second device can display for a phone
 * to read off its screen -- and then reads the sheet back, so a run cannot be looking at a
 * symbol that says something other than the operator thinks.
 *
 * <p>Both halves use this fork's own {@code :core}: the encoder writes the symbol and the
 * decoder states what that symbol actually carries. They are not the same thing. Code 128
 * takes U+00F1..U+00F4 in the writer's input as the FNC1..FNC4 escapes, so a string
 * containing those characters does not come back from a round trip -- which is a property
 * of the escape convention and is exactly the sort of thing this check exists to print.
 *
 * <pre>
 *   javac -cp core/build/libs/core-&lt;v&gt;.jar -d out tools/maint/BarcodeSheet.java
 *   java  -cp core/build/libs/core-&lt;v&gt;.jar:out BarcodeSheet CODE_128 'TEXT' 1000 320 out.png
 * </pre>
 */
public final class BarcodeSheet {

  /** Sheet size; a 1200x1920 panel shows this 1:1. */
  private static final int SHEET_W = 1200;
  private static final int SHEET_H = 1920;

  private BarcodeSheet() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 5) {
      System.err.println("usage: BarcodeSheet <FORMAT> <text> <width> <height> <out.png>");
      System.exit(2);
    }
    BarcodeFormat format = BarcodeFormat.valueOf(args[0]);
    String text = args[1];
    int width = Integer.parseInt(args[2]);
    int height = Integer.parseInt(args[3]);
    File out = new File(args[4]);

    var hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
    hints.put(EncodeHintType.MARGIN, 4);
    BitMatrix matrix = new MultiFormatWriter().encode(text, format, width, height, hints);

    BufferedImage sheet = new BufferedImage(SHEET_W, SHEET_H, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = sheet.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, SHEET_W, SHEET_H);
    g.setColor(Color.BLACK);
    int x = (SHEET_W - matrix.getWidth()) / 2;
    int y = (SHEET_H - matrix.getHeight()) / 2;
    for (int j = 0; j < matrix.getHeight(); j++) {
      for (int i = 0; i < matrix.getWidth(); i++) {
        if (matrix.get(i, j)) {
          g.fillRect(x + i, y + j, 1, 1);
        }
      }
    }
    g.dispose();
    ImageIO.write(sheet, "png", out);

    // Say what the sheet carries, not what it was asked to carry.
    for (Result r : readBack(sheet)) {
      System.out.println(r.getBarcodeFormat() + "\t" + r.getText());
    }
    System.out.println(out.getPath());
  }

  private static Result[] readBack(BufferedImage image) throws Exception {
    int w = image.getWidth();
    int h = image.getHeight();
    int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
    var bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w, h, pixels)));
    return new GenericMultipleBarcodeReader(new MultiFormatReader()).decodeMultiple(bitmap);
  }
}
