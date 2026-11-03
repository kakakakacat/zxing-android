# `EXTRA_STREAM` in `EncodeActivity`: a guard, and a crash that was already there

**2026-11-03, DN-ENG41 (Android 14, API 34).** Raised by CodeQL alert #1,
`java/android/unsafe-content-uri-resolution`, which only became visible once the CodeQL
upload was fixed at `1388b3f87` — the scan had been running and failing to publish.

## What was wrong

`EncodeActivity` is `exported="true"` for `ACTION_SEND` of `text/x-vcard`; that is how the
Contacts app shares a card. `QRCodeEncoder.encodeFromStreamExtra` took the caller's
`EXTRA_STREAM` [Uri] and handed it straight to `contentResolver.openInputStream`, so any
application on the device could choose a URI and have **this** process open it:

- `file:///data/data/<this package>/databases/barcode_scanner_history.db` — the user's
  whole scan history, readable by this uid and by no one else. The sender cannot open it
  and this app can.
- `content://<this package>.fileprovider/...` — this app's own provider, `exported="false"`,
  asked to read this app's files back to itself.

What limits it is that the bytes have to parse as a vCard or the encode is refused, and
the result is drawn on this app's own screen rather than handed back to the sender. It is
a confused deputy with a narrow exit, not an open one.

## And a crash underneath it, which is the part a user would have met

`openInputStream` answers a URI the app was never granted with **`SecurityException`**,
which is not an `IOException`, and only `IOException` was caught. So any app — or one
`adb shell am start` — could **crash** the exported activity with a single intent. This
was not introduced by the fix; it was found by testing the fix, because the shell grants
nothing. Stack, before:

```
java.lang.SecurityException: ... has no access to content://media/external/file/...
    at android.content.ContentResolver.openInputStream(ContentResolver.java:1530)
    at ...QRCodeEncoder.encodeFromStreamExtra(QRCodeEncoder.kt:159)
    at ...EncodeActivity.onResume(EncodeActivity.kt:154)
```

## What the fix does

`rejectUnsafeStream` runs before the open and refuses two shapes: a scheme other than
`content`, and an authority that `PackageManager` resolves to this app's own package.
`SecurityException` and `IllegalArgumentException` from the resolver now become
`WriterException` like every other unreadable stream.

It deliberately does **not** try to verify that the sender could read the URI. An activity
cannot: `Binder.getCallingUid` is this process outside a transaction, and `callingPackage`
is null unless the sender used `startActivityForResult`, which the share flow does not.
The grant the sender attaches is what the resolver then enforces — and now enforces
without taking the app down.

## Checked on the device, all four cases

Each one `am start`ed at the exported activity, screenshotted, and read out of logcat.

| Sent as `EXTRA_STREAM` | Result |
| --- | --- |
| `file:///data/data/<pkg>/databases/barcode_scanner_history.db` | refused by the guard — `EXTRA_STREAM is not a content URI: file` |
| `content://<pkg>.fileprovider/exports/x.vcf` | refused by the guard — `EXTRA_STREAM names this application's own provider` |
| `content://media/...` with no grant | **passed the guard**, refused by the resolver, `SecurityException` wrapped — and no longer a crash |
| `content://com.android.contacts/contacts/as_vcard/<lookup>`, granted | **encoded** |

The third row is what proves the guard is not simply blocking everything: the refusal
moves from the guard's own message to the resolver's, which is the resolver doing its job.

The user-visible refusal is one dialog, **"Could not encode a barcode from the data
provided."** — `encode-stream-guard-refused-DN-ENG41-20261103.png`.

The accepted case rendered the card, `encode-stream-guard-accepted-DN-ENG41-20261103.png`,
and the QR it drew was **decoded back with this fork's own `:core`** rather than eyeballed:

```
QR_CODE   MECARD:N:Guard Tester;TEL:+1-555-123-4567;;
```

Driving that last row needed a real contact, because `am start --grant-read-uri-permission`
grants the intent's **data and ClipData, never an extra** — which is why three earlier
attempts through MediaStore all came back `SecurityException` and looked like the guard
misfiring. The contact was created through `content insert` against
`content://com.android.contacts`, shared with the URI in *both* the data slot and the
extra, and deleted afterwards. **A grant that is not on the data URI is not a grant**, and
that cost most of the verification.

## The alert is dismissed, not closed, and that is deliberate

CodeQL re-ran on the fix and **kept the alert open**. The query sanitizes only the
path-prefix form its own help shows — normalize `getPath()`, reject `/data` — and does not
model a scheme restriction or an authority allow list, although its Recommendation text
asks for exactly those. The taint it still sees runs from the parcelable extra to
`openInputStream`; what it cannot see is that nothing dangerous survives the two checks in
between.

Alert #1 is therefore dismissed as a **false positive**, with the fix commit and this file
named in the dismissal. The alternative was to add the path normalization as well, purely
so the query would recognise it — unreachable code, because the scheme has already been
restricted to `content` by then and there is no filesystem path left to normalize. A check
that exists to satisfy a tool rather than an attacker is the kind of thing this repository
has spent the whole project removing.

**If the guard is ever relaxed, the dismissal has to be revisited**: it is justified by
`rejectUnsafeStream` existing and refusing those two shapes, not by the finding having been
wrong.

## Not covered

`:app` has no test source set, on purpose, so none of this is pinned by a test — it is
four `am start`s and four screenshots, repeatable from the table above. The other three
`ACTION_SEND` paths into `EncodeActivity` (plain text, HTML text, subject) take no URI and
were not touched.
