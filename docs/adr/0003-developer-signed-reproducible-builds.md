# One developer-held signing key across F-Droid and Play

By default, F-Droid signs apps with its own key. Instead, we sign with our own key and make builds reproducible, so F-Droid publishes our signed APK. Google Play will get a copy of the same key: we upload it rather than accept a Google-generated key, and must do so before the first open-testing or production release.

This gives one signature everywhere. Users can switch between stores without uninstalling, which would otherwise wipe their device-only data. It also means only one key to register with Google's developer verification. This has to be in place before the first F-Droid release, because changing the signature later forces every user to reinstall.

## Consequences

The build must be reproducible:
- sign with `apksigner` from build-tools 34;
- disable the APK's dependency-metadata block (`dependenciesInfo`);
- make baseline profiles deterministic;
- build releases from clean, tagged commits.

A lost key cannot be recovered or re-registered, so keep offline backups of it.
