# SCP Reader

An Android app for reading the SCP Foundation wiki. Browse and search the archive,
save articles to read offline, and play the narration when it's available.

> Unofficial — not affiliated with the SCP Foundation. Article text and the emblem
> come from the SCP Wiki and are licensed under CC BY-SA 3.0.

## Features

- Browse and search articles, tales and GoI documents
- Save articles (and narration) for offline reading
- Bookmarks and a recently-viewed list
- Adjustable text size, light/dark themes
- Resumes where you left off

## Building

You need the Android SDK and JDK 17.

```
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`.
