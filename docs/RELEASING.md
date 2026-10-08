# Releasing: automatic signed builds on GitHub

Every push to the **`main`** branch builds a **signed release APK** and publishes it as a
[GitHub Release](https://github.com/riyadmondol2006/ApkRepacker-Revived/releases). This page is the one-time setup. It takes about five minutes.

The workflow lives in [`.github/workflows/release.yml`](../.github/workflows/release.yml). It signs
the APK with **your** key, which you give GitHub as encrypted *repository secrets*. The key is never
stored in the repository.

---

## 1. Create your signing key (once)

You need a keystore file. If you already have one for this app, skip to step 2 and reuse it.

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -alias apkrepacker \
  -keyalg RSA -keysize 4096 -validity 36500
```

`keytool` comes with any JDK. It asks for two passwords (the keystore password and the key
password) and some name details. Remember or save both passwords and the alias (`apkrepacker`
above).

> **Back this file up** (a password manager or an encrypted drive, not the repository). Android only
> installs an update if it is signed with the same key as the installed app. If you lose the key you
> can never update existing installs.

## 2. Turn the keystore into text

GitHub secrets are text, so encode the file as base64.

| System | Command |
|---|---|
| Linux | `base64 -w0 release.jks > release.jks.b64` |
| macOS | `base64 -i release.jks -o release.jks.b64` |
| Windows (PowerShell) | `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) > release.jks.b64` |

Open `release.jks.b64` and copy the whole content (one long line).

## 3. Add the four secrets

On GitHub: your repository → **Settings** → **Secrets and variables** → **Actions** →
**New repository secret**. Add these exactly (names are case-sensitive):

| Secret name | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | the text you copied from `release.jks.b64` |
| `SIGNING_STORE_PASSWORD` | the keystore password |
| `SIGNING_KEY_ALIAS` | the alias, e.g. `apkrepacker` |
| `SIGNING_KEY_PASSWORD` | the key password |

With the [GitHub CLI](https://cli.github.com) it is quicker:

```bash
gh secret set SIGNING_KEYSTORE_BASE64 < release.jks.b64
gh secret set SIGNING_STORE_PASSWORD
gh secret set SIGNING_KEY_ALIAS
gh secret set SIGNING_KEY_PASSWORD
```

Then delete `release.jks.b64`. The `.gitignore` already blocks `*.jks`, `*.keystore` and
`*.jks.b64` so they can't be committed by accident.

## 4. Make `main` the default branch

The workflow runs on pushes to `main`. If your repository's default branch is something else
(for example `new-bugs`):

1. Push the branch: `git push -u origin main`
2. **Settings** → **Branches** → **Default branch** → switch to `main` → **Update**.

## 5. Push and watch it run

```bash
git push origin main
```

Open the **Actions** tab. The *Build and release* run takes roughly 5 to 10 minutes the first time
(faster after, Gradle is cached). When it finishes, the **Releases** page has a new release
containing:

* `ApkRepackerRevived-v<version>.apk`: the signed APK
* notes with the commit, the APK's SHA-256 and the commits since the previous version

Releases are named after the version only: `v<version>` (for example `v1.0.6`). Each push to
`main` publishes the next patch version automatically (`v1.0.6`, `v1.0.7`, ...), so you never edit
the version by hand for an ordinary release. Re-running a run for the same commit reuses its version
and replaces that release. A plain number such as `1.0.6` is published as a stable release.

You can also start a build by hand: **Actions** → *Build and release* → **Run workflow**.

---

## Changing the version

The workflow picks the version in its *Pick the version* step: the highest `vX.Y.Z` tag plus one
patch (`v1.0.6` → `1.0.7`), passed to Gradle as `-PappVersionName` and `-PappVersionCode`.
`versionCode` follows the version (`major × 1000000 + minor × 1000 + patch`, so `1.0.7` is
`1000007`) and always goes up, so every release installs as an update.

The `versionName` default in [`app/build.gradle`](../app/build.gradle) is the minimum version. To
start a new major or minor version, raise it there (for example to `1.1.0` or `2.0.0`); the next push
publishes exactly that and later pushes continue from it (`1.1.1`, ...). Local builds without the
`-P` options use that default.

A `versionName` with `beta`, `alpha` or `rc` (for example `1.1.0-beta1`) is used exactly as written,
with no automatic bump: it is published as a *pre-release*, and each push replaces it until you change
the name again.

## Troubleshooting

| Problem | Fix |
|---|---|
| Run fails at *Check the signing secrets exist* | A secret is missing or misspelled. The error lists which. |
| `Keystore was tampered with, or password was incorrect` | `SIGNING_STORE_PASSWORD` is wrong, or the base64 text was cut or has line breaks. Re-copy it (use `-w0` on Linux). |
| `Cannot recover key` | `SIGNING_KEY_PASSWORD` (or the alias) is wrong. |
| `Resource not accessible by integration` when publishing | **Settings** → **Actions** → **General** → *Workflow permissions* → **Read and write permissions**. |
| No run starts after a push | The push wasn't to `main`, or Actions are disabled under **Settings** → **Actions**. |

## Building a signed release on your own computer

Set the same four values as environment variables and run Gradle:

```bash
export SIGNING_STORE_FILE=/path/to/release.jks
export SIGNING_STORE_PASSWORD=...
export SIGNING_KEY_ALIAS=apkrepacker
export SIGNING_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

Without those variables the release build is simply left unsigned, and `:app:assembleDebug` is
the normal choice for development.
