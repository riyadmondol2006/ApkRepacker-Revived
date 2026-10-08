# Making patches for Apk Repacker Revived

A **patch** is a `.zip` file that tells Apk Repacker how to change a decompiled
project: replace smali code, add or remove files, edit resources, and so on. You
build the patch once and it applies to any app that matches, so a patch is a
reusable, shareable recipe.

This page documents the patch format the app understands. It is written for
patch authors — if you can read a little smali, you can write a patch.

> **Use responsibly.** Only patch apps you own or are allowed to modify. You are
> responsible for complying with the law and with the terms of any app you change.

---

## 1. Anatomy of a patch zip

A patch is a plain zip with a **`patch.txt` at its root**, plus any files the
rules copy into the project.

```
MyPatch.zip
├── patch.txt          ← required, the script
├── Fix.smali          ← a file an [ADD_FILES] rule copies in
└── extra.zip          ← a bundle a [MERGE] rule pulls from
```

Only `patch.txt` is mandatory. Everything else is referenced by a rule.

## 2. How `patch.txt` is read

The file is read line by line. The rules of the game:

- A **tag** looks like `[TAG]`. It starts a header field or a rule.
- A **keyword** inside a rule looks like `KEYWORD:`. **Its value is on the next
  line**, not on the same line. This is the single most common mistake — put the
  value on its own line.
- Blank lines are ignored. Lines starting with `#` are comments.
- Leading and trailing spaces around a value are trimmed, so indentation is fine.

```
[AUTHOR]
Jane Doe
```

Here `[AUTHOR]` is the tag and `Jane Doe` (the next line) is its value.

## 3. Header

Put these three at the top. All are optional but recommended.

| Tag | Value on next line | Meaning |
|---|---|---|
| `[MIN_ENGINE_VER]` | a number, e.g. `2` | Minimum engine version the patch needs. The current engine is version **2**. |
| `[AUTHOR]` | free text | Who wrote the patch (shown for credit). |
| `[PACKAGE]` | a package name or `*` | Which app the patch targets. `*` (or `ALL`) means any app. |

```
[MIN_ENGINE_VER]
2
[AUTHOR]
Jane Doe
[PACKAGE]
*
```

## 4. Rules

Every rule opens with `[RULE_NAME]` and closes with `[/RULE_NAME]`. Rules run
**top to bottom**. Any rule may carry an optional label so other rules can refer
to it:

```
NAME:
my_label
```

### Paths and targets

Many rules take a `TARGET:`. A target can be:

- **An exact path** relative to the project root, e.g. `AndroidManifest.xml` or
  `smali/com/example/A.smali`.
- **A wildcard**, where `*` matches any run of characters, e.g. `smali*/*.smali`
  matches every `.smali` file in every `smali` / `smali_classesN` folder. This is
  the usual way to scan all code.
- **A component selector**, resolved from the app's manifest to the matching
  smali file(s):
  - `[APPLICATION]` – the `<application>` class
  - `[ACTIVITIES]` – every activity
  - `[LAUNCHER_ACTIVITIES]` – the launcher activity

You can combine selectors, e.g. `[ACTIVITIES][LAUNCHER_ACTIVITIES]`.

### Variables

A `MATCH_ASSIGN` rule can capture text into a variable. Anywhere later you can
expand it with `${NAME}`. Regex capture groups are available as `${GROUP1}`,
`${GROUP2}`, … inside `REPLACE:` and `ASSIGN:` values.

---

### `[MATCH_REPLACE]` — the workhorse

Find text in the target file(s) and replace it.

| Keyword | Value | Notes |
|---|---|---|
| `TARGET:` | path / wildcard / selector | required |
| `REGEX:` | `true` or `false` | `true` = `MATCH` is a Java regex |
| `DOTALL:` | `true` or `false` | optional; makes `.` match newlines in regex mode |
| `MATCH:` | one or more lines | the text/pattern to find (ends at the next keyword) |
| `REPLACE:` | one or more lines | what to put in its place (may be empty to delete) |

Regex example — redirect a device-id call:

```
[MATCH_REPLACE]
TARGET:
smali*/*.smali
MATCH:
    invoke-virtual \{([pv]\d+)\}, Landroid/telephony/TelephonyManager;->getDeviceId\(\)Ljava/lang/String;
REGEX:
true
REPLACE:
invoke-static {}, Lcom/example/Ids;->id()Ljava/lang/String;
[/MATCH_REPLACE]
```

Notes:
- In regex mode, special characters in `MATCH` must be escaped (`\{`, `\(`, `\.`
  …). `${GROUP1}` in `REPLACE` is the first captured group.
- To **delete** matched text, leave `REPLACE:` empty.
- In plain mode (`REGEX: false`), `MATCH` is compared line by line with
  surrounding whitespace trimmed, so a multi-line block matches regardless of
  indentation.

### `[MATCH_ASSIGN]` — capture a value into a variable

Runs a regex (`REGEX:` must be `true`) on the first matching file and stores
capture groups into variables.

```
[MATCH_ASSIGN]
TARGET:
    [LAUNCHER_ACTIVITIES]
MATCH:
\.class (public final|public) (.+)
REGEX:
true
ASSIGN:
    MAIN_CLASS=${GROUP2}
[/MATCH_ASSIGN]
```

Afterwards `${MAIN_CLASS}` can be used in later rules.

### `[MATCH_GOTO]` — conditional jump

If the target matches, jump to the rule whose `NAME:` equals `GOTO:`. Keywords:
`TARGET:`, `MATCH:`, `REGEX:`, `DOTALL:`, `GOTO:`.

### `[GOTO]` — unconditional jump

Jump to the named rule.

```
[GOTO]
GOTO:
my_label
[/GOTO]
```

### `[ADD_FILES]` — copy a file into the project

| Keyword | Value |
|---|---|
| `SOURCE:` | a path inside the patch zip |
| `TARGET:` | where to write it in the project |
| `EXTRACT:` | `true` to treat `SOURCE` as a nested zip and unpack it |

```
[ADD_FILES]
SOURCE:
Fix.smali
TARGET:
smali/com/example/Fix.smali
[/ADD_FILES]
```

### `[REMOVE_FILES]` — delete files or folders

List one path per line under `TARGET:`.

```
[REMOVE_FILES]
TARGET:
smali/com/example/Splash.smali
res/drawable/splash.png
[/REMOVE_FILES]
```

### `[MERGE]` — merge a resource/smali bundle

`SOURCE:` is a zip inside the patch; its resources and smali are merged into the
project, renumbering resource IDs in `res/values/public.xml` as needed.

### `[SIGNATURE_REVISE]` — fill signature placeholders

For a target smali file, substitutes `%PACKAGE_NAME%` and `%RSA_DATA%` with the
project's values. Used by signature-verification patches.

### `[EXECUTE_DEX]` — run code shipped in the patch

Loads a `.dex` from the patch and calls one method. Only use patches from
authors you trust — this runs real code with the app's permissions.

| Keyword | Value |
|---|---|
| `SCRIPT:` | the `.dex` file inside the patch |
| `MAIN_CLASS:` | fully-qualified class to load |
| `ENTRANCE:` | method name to call |
| `PARAM:` | one or more lines passed as a string argument |
| `SMALI_NEEDED:` | `true` if the project must be decompiled to smali first |
| `INTERFACE_VERSION:` | must be `1` |

The entrance method is called as
`entrance(apkPath, patchZipPath, projectPath, param)`.

### `[DUMMY]`

Does nothing. Handy as a `GOTO` landing spot.

---

## 5. A complete example

Hide mock locations in any app:

```
[MIN_ENGINE_VER]
2
[AUTHOR]
Jane Doe
[PACKAGE]
*

[MATCH_REPLACE]
TARGET:
smali*/*.smali
MATCH:
invoke-virtual \{([pv]\d+)\}, Landroid/location/Location;->isFromMockProvider\(\)Z\n\n    move-result ([pv]\d+)
REGEX:
true
REPLACE:
invoke-virtual {${GROUP1}}, Landroid/location/Location;->isFromMockProvider()Z
const/4 ${GROUP2}, 0x0
[/MATCH_REPLACE]
```

## 6. Applying a patch

1. Decompile the app in Apk Repacker (to smali if the patch edits code).
2. Open the project, tap **Patcher**.
3. Add one or more `.zip` patches, then **Apply**.
4. Watch the log. When all rules are applied, rebuild and sign.

## 7. Tips and gotchas

- **Values go on the next line**, never after the `KEYWORD:` on the same line.
- Scanning `smali*/*.smali` touches every code file, so prefer a narrow
  `TARGET:` when you know the file.
- Test regexes against a real smali dump; smali formatting (register names,
  blank lines) matters.
- Keep a patch focused. Several small rules are easier to debug than one giant
  one.
- `MIN_ENGINE_VER` higher than the installed engine will stop the patch, so set
  it to the lowest version your rules actually need.
