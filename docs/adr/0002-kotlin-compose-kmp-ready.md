# Native Kotlin + Jetpack Compose, structured for a possible iOS port

The app is Android-only, written in Kotlin with Jetpack Compose. Domain logic and persistence (Room, which supports Kotlin Multiplatform) sit in modules with no Android UI dependencies, so a future iOS version could reuse them through Compose Multiplatform instead of being rewritten. We rejected:

- **Compose Multiplatform from day one:** it costs build work now for an iOS version that may never happen, and adds F-Droid recipe work.
- **Flutter:** plugins that ship prebuilt native code must be rebuilt from source for F-Droid.
- **React Native/Expo:** the most F-Droid friction of the options.

Keep Android-specific code out of the domain and data modules, even when it would be convenient.
