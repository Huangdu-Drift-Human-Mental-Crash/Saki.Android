# Release build and versioning

## Version rules

Saki.Android derives each build version from the `master` history:

```text
MAJOR.MINOR.PATCH-BRANCH-COMMIT[-dirty]
```

The Android `versionName` omits the display-only `v` prefix. APK filenames and
Git tags include it. For example:

```text
versionName: 0.1.1-master-abcdef0
release APK: Saki.Android-Release-v0.1.1-master-abcdef0.apk
debug APK:   Saki.Android-Debug-v0.1.1-master-abcdef0.apk
```

The PATCH number belongs to the `master` line:

- A `master` build uses the distance from the nearest reachable
  `vMAJOR.MINOR.PATCH` tag along its first-parent history.
- A branch build finds its merge base with `origin/master` and uses the next
  master PATCH. Commits on that branch keep the same numeric version and are
  distinguished by the sanitized branch name and seven-character commit hash.
- A dirty local worktree appends `-dirty`. Ignored files do not make a build
  dirty.
- Until the first version tag exists, `saki.versionBase` and
  `saki.versionBaseRef` in `gradle.properties` define the fallback baseline.

The current fallback maps master commit `d7ea7f8` to `v0.1.0`. A branch forked
therefore builds as `v0.1.1-<branch>-<commit>`, and the next master commit builds
as `v0.1.1-master-<commit>`.

Android `versionCode` is independent of the visible version. Master uses its
first-parent commit count; a branch uses the current `origin/master` tip's count
plus one. A stale candidate can therefore still update the latest installed
master build, while the visible PATCH continues to describe the branch's actual
fork point. PATCH can grow beyond 99.

To start a new minor or major line, tag the selected clean master commit with an
exact version tag such as:

```bash
git tag -a v0.2.0 -m "Saki.Android v0.2.0"
git push origin v0.2.0
```

CI checks out full Git history and supplies the event branch and commit so
detached GitHub Actions checkouts produce deterministic versions. Source archive
builds without Git metadata must provide both overrides:

```bash
./gradlew assembleDebug \
  -Psaki.versionName=0.1.1-archive-abcdef0 \
  -Psaki.versionCode=189
```

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

On pushes to any branch, CI stages and uploads both APK artifacts:

```text
app-debug   -> Saki.Android-Debug-v<VERSION>.apk
app-release -> Saki.Android-Release-v<VERSION>.apk
```

The artifact container names remain stable for automation. Pull requests build
and test both variants but do not upload artifacts.

## Telegram delivery from CI

After a successful branch push build, a separate CI job downloads
both artifacts, verifies both APK signatures, confirms that their versions
match, and sends them together as a Telegram media group. Pull requests never
run the delivery job. If the release is unsigned, signature verification fails
before any upload.

The captions contain the derived version, short commit SHA, commit message,
commit URL, build type, and each APK's SHA-256 checksum. Debug and Release use
different signing keys and the same application ID, so Android does not allow
one variant to update the other without uninstalling it first.

### Telegram setup

1. Create a bot with [BotFather](https://t.me/BotFather) and keep its token private.
2. Add the bot to the destination group or channel. In a group, allow it to send messages. In a channel, make it an administrator with permission to post messages.
3. Determine the destination chat ID. The Bot API accepts a numeric chat ID (channel and supergroup IDs usually start with `-100`) or a public channel username such as `@saki_builds`. For a private destination, send a new message after adding the bot, call the Bot API `getUpdates` method locally, and read `message.chat.id` or `channel_post.chat.id` from the response.
4. For a forum topic, send a message in that topic and read its `message_thread_id` from `getUpdates`. Leave the secret unset to post to the main chat.

### GitHub repository configuration

Open **Settings > Secrets and variables > Actions** in the GitHub repository and configure:

| Type | Name | Required | Value |
| --- | --- | --- | --- |
| Repository secret | `TELEGRAM_BOT_TOKEN` | Yes | Token issued by BotFather. Never store this as a variable. |
| Repository secret | `TELEGRAM_CHAT_ID` | Yes | Numeric chat ID or public `@channel_username`. |
| Repository secret | `TELEGRAM_MESSAGE_THREAD_ID` | No | Positive numeric ID of a forum topic. |

The existing four `SAKI_RELEASE_*` signing secrets must also be configured. Missing or invalid Telegram values cause the eligible delivery job to fail with an error, without printing the bot token.

Saki currently uses the official cloud `sendMediaGroup` endpoint. Each APK must
remain within the cloud Bot API document limit. If either artifact eventually
exceeds it, revisit a local Bot API server instead of silently skipping delivery.
