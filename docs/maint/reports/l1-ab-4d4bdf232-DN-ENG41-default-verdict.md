# L1 comparison

verdict: **regressed**

| | baseline | candidate |
| --- | --- | --- |
| run_id | l1-device-java-default-20261031T070917Z | l1-device-native-default-20261031T070949Z |
| commit | 4d4bdf232 | 4d4bdf232 |
| runner | device | device |
| backend | zxing-java | zxing-cpp |
| backend_build | backend=zxing-java | backend=zxing-cpp;abi=arm64-v8a;neon=1;zxingcpp=3.1.1 |
| device | DN-ENG41 | DN-ENG41 |

| Status | baseline | candidate | delta |
| --- | --- | --- | --- |
| CORRECT | 862 | 820 | -42 |
| INCORRECT | 4 | 3 | -1 |
| NOT_FOUND | 254 | 297 | +43 |
| CORRECT_NO_RESULT | 46 | 48 | +2 |
| FALSE_POSITIVE | 2 | 0 | -2 |
| ERROR | 0 | 0 | +0 |

91 regressions, 51 improvements, 3 changed another way.

| Sample | kind | expected | baseline | candidate |
| --- | --- | --- | --- | --- |
| `aztec-2/03` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/04` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/06` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/07` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/08` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/10` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/11` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/12` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/13` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/14` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/15` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/16` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/17` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/19` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/20` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/21` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `aztec-2/22` | improvement | AZTEC | NOT_FOUND (None) | CORRECT (AZTEC) |
| `code128-2/06` | regression | CODE_128 | CORRECT (CODE_128) | NOT_FOUND (None) |
| `code128-2/09` | improvement | CODE_128 | NOT_FOUND (None) | CORRECT (CODE_128) |
| `code128-2/28` | regression | CODE_128 | CORRECT (CODE_128) | NOT_FOUND (None) |
| `code39-1/2` | regression | CODE_39 | CORRECT (CODE_39) | NOT_FOUND (None) |
| `code39-2/1` | changed | CODE_39 | INCORRECT (CODE_39) | NOT_FOUND (None) |
| `code39-2/2` | changed | CODE_39 | INCORRECT (CODE_39) | NOT_FOUND (None) |
| `datamatrix-1/C40` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-2/03` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-2/11` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-2/16` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-2/17` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-2/18` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-3/abcd-120x8` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-3/abcd-144x8` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-3/abcd-40x26` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-3/abcd-44x20` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-3/abcd-64x12` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-3/abcd-64x16` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-3/abcd-64x24` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-3/abcd-64x26` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-3/abcd-64x8` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-3/abcd-80x8` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-3/abcd-88x12` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `datamatrix-3/abcd-96x8` | improvement | DATA_MATRIX | NOT_FOUND (None) | CORRECT (DATA_MATRIX) |
| `ean13-2/05` | regression | EAN_13 | CORRECT (EAN_13) | NOT_FOUND (None) |
| `ean13-2/09` | regression | EAN_13 | CORRECT (EAN_13) | NOT_FOUND (None) |
| `ean13-4/01` | improvement | EAN_13 | INCORRECT (UPC_E) | CORRECT (EAN_13) |
| `ean13-4/11` | regression | EAN_13 | CORRECT (EAN_13) | NOT_FOUND (None) |
| `falsepositives-2/10` | improvement | None | FALSE_POSITIVE (EAN_8) | CORRECT_NO_RESULT (None) |
| `falsepositives-2/13` | improvement | None | FALSE_POSITIVE (UPC_E) | CORRECT_NO_RESULT (None) |
| `pdf417-1/12` | regression | PDF_417 | CORRECT (PDF_417) | NOT_FOUND (None) |
| `pdf417-1/13` | regression | PDF_417 | CORRECT (PDF_417) | NOT_FOUND (None) |
| `pdf417-2/22` | regression | PDF_417 | CORRECT (PDF_417) | NOT_FOUND (None) |
| `pdf417-3/13` | regression | PDF_417 | CORRECT (PDF_417) | NOT_FOUND (None) |
| `pdf417-3/14` | regression | PDF_417 | CORRECT (PDF_417) | NOT_FOUND (None) |
| `pdf417-3/15` | regression | PDF_417 | CORRECT (PDF_417) | NOT_FOUND (None) |
| `qrcode-1/14` | improvement | QR_CODE | NOT_FOUND (None) | CORRECT (QR_CODE) |
| `qrcode-2/16` | regression | QR_CODE | CORRECT (QR_CODE) | INCORRECT (QR_CODE) |
| `qrcode-2/25` | improvement | QR_CODE | NOT_FOUND (None) | CORRECT (QR_CODE) |
| `qrcode-2/28` | regression | QR_CODE | CORRECT (QR_CODE) | NOT_FOUND (None) |
| `qrcode-2/5` | improvement | QR_CODE | NOT_FOUND (None) | CORRECT (QR_CODE) |
| `qrcode-3/04` | improvement | QR_CODE | NOT_FOUND (None) | CORRECT (QR_CODE) |
| `qrcode-3/21` | improvement | QR_CODE | NOT_FOUND (None) | CORRECT (QR_CODE) |
| `qrcode-3/22` | improvement | QR_CODE | NOT_FOUND (None) | CORRECT (QR_CODE) |
| `qrcode-3/23` | improvement | QR_CODE | NOT_FOUND (None) | CORRECT (QR_CODE) |
| `qrcode-4/09` | regression | QR_CODE | CORRECT (QR_CODE) | NOT_FOUND (None) |
| `qrcode-4/14` | improvement | QR_CODE | NOT_FOUND (None) | CORRECT (QR_CODE) |
| `qrcode-4/19` | improvement | QR_CODE | NOT_FOUND (None) | CORRECT (QR_CODE) |
| `qrcode-4/41` | improvement | QR_CODE | NOT_FOUND (None) | CORRECT (QR_CODE) |
| `rss14-1/1` | regression | RSS_14 | CORRECT (RSS_14) | NOT_FOUND (None) |
| `rss14-1/2` | regression | RSS_14 | CORRECT (RSS_14) | NOT_FOUND (None) |
| `rss14-1/3` | regression | RSS_14 | CORRECT (RSS_14) | NOT_FOUND (None) |
| `rss14-1/4` | regression | RSS_14 | CORRECT (RSS_14) | NOT_FOUND (None) |
| `rss14-1/5` | regression | RSS_14 | CORRECT (RSS_14) | NOT_FOUND (None) |
| `rss14-1/6` | regression | RSS_14 | CORRECT (RSS_14) | NOT_FOUND (None) |
| `rss14-2/11` | changed | RSS_14 | INCORRECT (UPC_E) | NOT_FOUND (None) |
| `rss14-2/13` | regression | RSS_14 | CORRECT (RSS_14) | NOT_FOUND (None) |
| `rss14-2/24` | regression | RSS_14 | CORRECT (RSS_14) | NOT_FOUND (None) |
| `rss14-2/7` | regression | RSS_14 | CORRECT (RSS_14) | NOT_FOUND (None) |
| `rss14-2/8` | regression | RSS_14 | CORRECT (RSS_14) | NOT_FOUND (None) |
| `rssexpanded-3/13` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | INCORRECT (RSS_EXPANDED) |
| `rssexpanded-3/66` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | INCORRECT (RSS_EXPANDED) |
| `rssexpandedstacked-1/1` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/10` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/11` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/12` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/14` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/15` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/16` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/17` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/18` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/2` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/21` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/24` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/25` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/26` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/27` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/28` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/29` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/3` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/30` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/31` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/32` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/33` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/34` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/35` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/36` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/37` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/38` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/39` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/4` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/40` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/41` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/42` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/43` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/44` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/45` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/46` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/47` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/48` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/49` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/5` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/50` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/51` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/52` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/53` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/54` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/55` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/56` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/57` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/58` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/59` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/6` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/60` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/61` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/65` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/7` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/8` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-1/9` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-2/1` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `rssexpandedstacked-2/1000` | regression | RSS_EXPANDED | CORRECT (RSS_EXPANDED) | NOT_FOUND (None) |
| `upca-1/3` | improvement | UPC_A | NOT_FOUND (None) | CORRECT (UPC_A) |
| `upca-2/41` | regression | UPC_A | CORRECT (UPC_A) | NOT_FOUND (None) |
| `upca-4/15` | regression | UPC_A | CORRECT (UPC_A) | NOT_FOUND (None) |
| `upca-5/04` | improvement | UPC_A | NOT_FOUND (None) | CORRECT (UPC_A) |
| `upce-2/24` | regression | UPC_E | CORRECT (UPC_E) | NOT_FOUND (None) |
| `upce-2/33` | regression | UPC_E | CORRECT (UPC_E) | NOT_FOUND (None) |
| `upce-3/02` | regression | UPC_E | CORRECT (UPC_E) | NOT_FOUND (None) |
