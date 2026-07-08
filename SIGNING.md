# Release signing

Release builds (`assembleRelease`) are signed with a keystore that is **not** part of this repo.
Without it, `release` builds fall back to debug signing so the build never breaks for
contributors or CI — you just won't get an installable-as-update, properly signed APK.

## Local builds

Create `keystore.properties` at the project root (same folder as this file). It's gitignored —
never commit it.

```properties
storeFile=/absolute/or/relative/path/to/your.jks
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

`storeFile` is resolved relative to the project root if it's a relative path.

## GitHub Actions

The release workflow (`.github/workflows/release.yml`) reads the keystore from repo secrets
instead. Add these under **Settings → Secrets and variables → Actions**:

| Secret name         | Value                                              |
|----------------------|-----------------------------------------------------|
| `KEYSTORE_BASE64`    | Your `.jks`/`.keystore` file, base64-encoded        |
| `KEYSTORE_PASSWORD`  | The keystore's store password                       |
| `KEY_ALIAS`          | The signing key's alias                              |
| `KEY_PASSWORD`       | The signing key's password                           |

The workflow decodes `KEYSTORE_BASE64` into a temporary file (outside the checkout, deleted at
the end of the job either way) and points Gradle at it via the `KEYSTORE_FILE` env var, mirroring
`keystore.properties`' fields.

If none of these secrets are set (e.g. on a fork), the workflow still runs — the release APK it
produces is just debug-signed.

### Encoding your keystore on Windows

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\your.jks")) | Set-Clipboard
```

This copies the base64 text straight to your clipboard — paste it as the `KEYSTORE_BASE64`
secret's value. (If you'd rather write it to a file instead of the clipboard:
`[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\your.jks")) | Out-File keystore.b64 -Encoding ascii`.)
