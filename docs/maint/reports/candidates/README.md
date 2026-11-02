# Candidate L2 results

A pull request that changes the decode path puts its measured result here, as one
`l2-*.json` written by `benchmark/tools/l2_report.py`. CI picks the matching committed
baseline out of `../l2-baselines.json` and runs the section 10.6 gates over the pair, so
the pull request carries a verdict instead of an opinion.

**CI does not measure anything, and cannot.** A shared runner has no locked clocks, no
known thermal state and no device. Every number in a file here was taken by an operator on
a reference device under the protocol in `../../BENCHMARKS.md` section 7, and the file
records the conditions it was taken under — that is what the gates read.

## Putting one here

```sh
./gradlew :benchmark-android:connectedReleaseAndroidTest   # on the reference device
python3 -I benchmark/tools/l2_report.py \
    --input <path>/…-benchmarkData.json \
    --goldens benchmark/android/goldens/frames.golden.json \
    --run-id <topic>-<date>-DN-ENG41 --commit <sha> --device-label DN-ENG41 \
    --out docs/maint/reports/candidates/l2-<sha>-DN-ENG41-<config>.json
python3 -I benchmark/tools/ci_compare.py        # the same verdict CI will print
```

The candidate's device, `config` and `backend` decide which baseline it is judged
against. If nothing is baselined for that combination the verdict is **unjudged**, not
pass — take the baseline first, or say in the pull request why this run is not a
regression check.

## Taking it away again

A candidate is a question about one change, not a record. Once the change has landed:

- **It became the baseline.** `git mv` it up into `docs/maint/reports/` and point the
  matching entry of `l2-baselines.json` at it, keeping the entry's `note` honest about
  what the run was.
- **It did not.** Delete it. A stale candidate is re-judged against a moving baseline on
  every later pull request and will eventually produce a verdict about nothing.

Either way this directory is empty between changes, and an empty directory is not a pass:
CI says so in the job summary.
