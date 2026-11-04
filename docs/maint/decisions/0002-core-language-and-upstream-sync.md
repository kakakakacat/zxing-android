# 0002 — `core/`: leave it Java, and keep syncing it

**Status:** **accepted**, 2026-11-04. No code changes were made by this record. It settles
a question that was asked twice ("can `core/` be Kotlin, or C++?") and names the one piece
of work that follows from it (the sync cadence, below).
**Date:** 2026-11-04.
**Phase:** maintenance. Supersedes nothing; it writes down the reasoning behind the
one-line decision already in `HANDOFF.md` ("`core/` stays Java on purpose").

Everything below that is a count was taken from this tree with the command shown. Nothing
here is a performance claim — no timing was measured for this record, and none is needed
for what it decides.

---

## The short version

**The C++ rewrite already happened, and `core/` still cannot go away.** zxing-cpp is
vendored, wired behind the `FrameDecoder` seam and has been the shipping default since
`ebe1708b4`. What `core/` provides today is not the fast path — it is the public API's
type vocabulary, the fallback for ABIs with no native build, and two layers zxing-cpp
does not implement at all.

Converting `core/` to Kotlin would cost roughly **35 upstream fixes a year**, most of them
hardening fixes on an attacker-controlled input path, in exchange for a language statistic.

| Option | Verdict |
| --- | --- |
| Rewrite `core/` in C++, Android-only | **already done** — zxing-cpp `v3.1.1`, default backend |
| Convert `core/` to Kotlin | **rejected** — breaks `sync-core-from-upstream.sh`, no other effect |
| Delete `core/` now that native is default | **blocked** — three reasons, below |
| Trim `core/`'s role without rewriting it | **the open path** — staged plan at the end |

## What `core/` actually is

```
git ls-files 'core/src/main/java/*.java' | wc -l    →  239 files
git ls-files 'core/*.java' | xargs wc -c | tail -1  →  2,097,579 bytes
```

That is 89% of the Java in the tree and about 64% of the tree's source bytes, which is
what prompts the question in the first place. By package:

| | files | | files |
| --- | --- | --- | --- |
| `oned` | 57 | `datamatrix` | 23 |
| `client/result` | 37 | `aztec` | 12 |
| `pdf417` | 27 | `multi` | 6 |
| `qrcode` | 25 | `maxicode` | 4 |
| `common` | 24 | | |

It is a *vendored* fork: upstream `core/`, renamed into `io.github.kakakakacat.zxing` by
`tools/maint/sync-core-from-upstream.sh`. It is not code this project wrote.

## Why it cannot simply be deleted

**1. It is the public API, not an implementation detail.**
`scanner/build.gradle.kts:109` reads `api(project(":core"))` — `api`, not
`implementation`. `BarcodeFormat`, `Result`, `ResultPoint`, `LuminanceSource`,
`DecodeHintType`, `ResultMetadataType`, `PlanarYUVLuminanceSource` and `BinaryBitmap` all
cross the AAR boundary. Removing `core/` is not an internal change; it is a new API for
every consumer.

**2. The Java decoder still has real coverage to serve.**
The native library is built for `arm64-v8a` and `armeabi-v7a` only
(`scanner/build.gradle.kts:36,40`); `x86_64` is added for debug so CI's emulator can run
the native correctness tests (`:77,81`). On an x86 device — ChromeOS, some tablets, any
emulator a consumer runs — `JavaFrameDecoder` is not a fallback, it is the only decoder.

**3. zxing-cpp does not implement two layers this project ships.**
- **Result parsing.** `core/client/result` is 37 parsers turning a decoded string into
  `WIFI:`, `mailto:`, vCard and the rest. zxing-cpp decodes to a string and stops. 19
  source files in `android/` depend on these parsers.
- **Encoding.** `CMakeLists.txt:23` sets `ZXING_WRITERS OFF` deliberately, to keep a
  bundled libzint out of the AAR. `android/.../encode/QRCodeEncoder.kt` needs a writer,
  and several `:scanner` tests build their fixtures with one.

## Why Kotlin is the wrong trade

The conversion is mechanical and large — 239 files of bit manipulation, array indexing and
exception-driven control flow, the shape of code Kotlin improves least. The bytecode, the
API and the behaviour would all be equivalent.

The cost is not "future merges get harder". It is that `sync-core-from-upstream.sh` stops
working, because it replays upstream Java over our tree. Measured against the last twelve
months of upstream:

```
git log --since=2025-11-04 --format='%an' upstream/master | wc -l            →  78
… | grep -c dependabot                                                      →  23
git log --since=2025-11-04 --format='%an' upstream/master -- core/src/main \
  | grep -vc dependabot                                                      →  35
```

**35 human commits touched `core/src/main` in twelve months**, and they are overwhelmingly
hardening rather than refactoring — a concentrated audit through 2026-05 … 2026-07 plus
two recent decoder fixes:

- ReDoS: `fix quadratic backtracking in URIResultParser isPossiblyMaliciousURI` (#2110),
  `fix catastrophic backtracking in email domain regex` (#2099)
- Out-of-bounds reads: pdf417 `decodeMacroBlock` (#2083), maxicode `getMessage` (#2079),
  datamatrix `decodeBase256Segment` (#2084)
- Overflow: `reject negative and overflowing crop in core LuminanceSource classes` (#2112)
- Liveness: `Fix PDF infinite loop` (#2128), `NegativeArraySizeException in aztec` (#2091)
- 2026-10: QR third finder pattern (#2155), Code 128 function-code checksums (#2151)

Every one of those is on a path fed by whatever barcode is held in front of the camera —
attacker-controlled input. Giving them up for a language statistic is not a trade worth
making.

Note where two of them landed: `URIResultParser` and the email regex are in
`client/result` — the layer zxing-cpp has no equivalent for. Even in a future where every
decode runs native, that Java and its sync link stay.

## Upstream is maintained, and this fork is current

```
git merge-base --is-ancestor upstream/master HEAD      →  true
git rev-list --left-right --count upstream/master...HEAD  →  0   149
```

`upstream/master` tip is `56b8a9a7b`, 2026-10-28. **This fork is 0 commits behind and 149
ahead.** Upstream's pace is uneven — 1 commit in 2026-02, 22 in 2026-06 — which is
maintenance mode, not abandonment; dependabot opened PRs on 2026-11-02.

**The cadence this needs.** "Can sync" and "did sync" are different states, and nothing in
the repo currently forces the second. Run `tools/maint/sync-core-from-upstream.sh`
**quarterly, and on any upstream release**, and record the upstream SHA in the commit
message as the script already does. A sync that finds nothing is a cheap result; a sync
that is never run is how a vendored fork silently becomes a permanent one.

## If the concern is the language bar on GitHub

`core/` is vendored code under a sync script, which is exactly what Linguist's
`linguist-vendored` attribute is for. A `.gitattributes` line —

```
core/src/main/java/** linguist-vendored
```

— makes the statistic report the code this project maintains, which is Kotlin-first. This
is a labelling fix, not a cosmetic one; it is an accurate description of `core/`'s status.
**Not applied by this record** — it changes what the repository page shows, so it is the
user's call.

## The staged plan, if `core/`'s footprint is to shrink

None of this is started. Stages are independent and each is worth doing alone.

**Stage 0 — measure, change nothing.** What does `core/` actually cost after R8?
`android/build/outputs/mapping/release/usage.txt` already records what was stripped. And
per `BENCHMARKS.md`, an A/B of the native and Java backends on the fixed dataset is what
the fallback's real cost has to be read from. Output is a number that decides whether
stages 1–3 are worth anything.

**Stage 1 — shrink what the Java path has to cover (low risk).** Weigh adding `x86_64` to
the release `abiFilters`. Cost is AAR bytes; benefit is demoting `JavaFrameDecoder` from
"the only decoder on some real hardware" to a true fallback.

**Stage 2 — split the module (medium risk).** `core` into reader / writer / client-result,
so a consumer that only scans does not ship 37 parsers and an encoder. Independent of the
language question and carries its own size win.

**Stage 3 — the only coherent route to less Java (breaking; 1.0).** Give `:scanner` its own
Kotlin API types and demote `api(project(":core"))` to `implementation`. `core/` becomes an
implementation detail that can be trimmed, swapped or displaced by native code without
touching consumers. This is a major-version break and needs a 1.0 roadmap first.

---

**What would reopen this record:** upstream going quiet for a year; zxing-cpp growing a
result-parser layer and an encoder that fits the AAR; or a 1.0 that is already breaking the
API for other reasons, which makes stage 3 nearly free.
