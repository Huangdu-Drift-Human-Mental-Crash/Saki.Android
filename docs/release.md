# Release build and versioning

## Version rules

Saki.Android uses stable SemVer for `versionName`:

```text
MAJOR.MINOR.PATCH
```

The source of truth is `saki.versionName` in `gradle.properties`.

Android `versionCode` is derived automatically from `versionName`:

```text
versionCode = MAJOR * 10000 + MINOR * 100 + PATCH
```

Examples:

| versionName | versionCode |
| --- | ---: |
| `0.1.0` | `100` |
| `0.1.1` | `101` |
| `1.0.0` | `10000` |
| `1.1.0` | `10100` |
| `2.0.0` | `20000` |

Constraints:

- `versionName` must contain exactly three numeric components.
- `MINOR` and `PATCH` must be in `0..99`.
- Never decrease `versionName`; the derived `versionCode` must stay monotonically increasing for Android updates.
- Bump `PATCH` for fixes, `MINOR` for user-visible feature batches, and `MAJOR` for compatibility-breaking releases or major product milestones.

## Local signed release

Release signing is intentionally local-only. Put these keys in `local.properties`:

```properties
releaseStoreFile=/absolute/or/project-relative/path/to/release.jks
releaseStorePassword=...
releaseKeyAlias=...
releaseKeyPassword=...
```

When all four properties are present, `assembleRelease` produces a signed APK:

```bash
./gradlew clean assembleRelease
```

Output:

```text
app/build/outputs/apk/release/app-release.apk
```

Keystores (`*.jks`) must not be committed.

## CI release build

CI runs `assembleDebug`, `assembleRelease`, and unit tests for code changes.

To produce signed release APK artifacts in CI, configure these repository secrets:

| Secret | Description |
| --- | --- |
| `SAKI_RELEASE_KEYSTORE_BASE64` | Base64-encoded release keystore file. |
| `SAKI_RELEASE_STORE_PASSWORD` | Keystore password. |
| `SAKI_RELEASE_KEY_ALIAS` | Release key alias. |
| `SAKI_RELEASE_KEY_PASSWORD` | Release key password. |

Create the keystore secret from a local keystore without committing the file:

```bash
base64 -w0 release.jks
```

CI writes the decoded keystore to the runner temporary directory and passes its path to Gradle through `SAKI_RELEASE_STORE_FILE`.

If any signing secret is missing, CI still runs `assembleRelease`, but the artifact is unsigned. This keeps pull request validation working without exposing signing credentials.

On pushes to `master`, CI uploads both debug and release APK artifacts.
