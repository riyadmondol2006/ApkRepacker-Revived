# ARSCLib 1.4.0

Library used by AntiSplit to merge split APKs (resources tables, manifests, dex) without
decompiling them.

- Project: https://github.com/REAndroid/ARSCLib
- File: `ARSCLib-1.4.0.jar` from the official release
  https://github.com/REAndroid/ARSCLib/releases/tag/V1.4.0
- SHA-256: `0cc95b160ba4c731a5cadb455f32b79f4e64b4160bc5ec3e5183ea3676b385ce`
- License: Apache License 2.0

The jar is kept unmodified. `app/build.gradle` repackages it at build time without its desktop-only
parts: the bundled Android framework tables (`frameworks/`) and its copies of Android's own
`android.*` and `org.xmlpull.*` classes, which the device already provides.

It lives in this sub-folder so the `libs/*.jar` file tree doesn't pick up the unfiltered jar.
