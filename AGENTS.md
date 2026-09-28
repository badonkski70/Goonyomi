# Project instructions

## Branding (critical — do not break)

- This app is **Goonyomi** (a rebranded Aniyomi/Tachiyomi fork).
- The app icon is the **white water-droplet** (`ic_launcher_emoji` / `ic_goonyomi_emoji`). Never restore or reference the old Aniyomi icon assets (`ic_ani`, `ic_ani_monochrome_launcher`, `ic_launcher_foreground`, `ic_launcher_background`, or any `app/src/debug/res/mipmap/` launcher overrides — those were the old icon and are deleted).
- **Do NOT change `applicationId = "xyz.jmir.tachiyomi.mi"`** in `app/build.gradle.kts`. Keeping the Aniyomi package name (and signing with the stable `release.jks`, passwords "aniyomi-goat") is what makes updates install **over the existing app without uninstalling**. A different package id makes Goonyomi a separate app and breaks in-place updates.

## Building / device testing

- Build the **release** APK for the phone, never the debug APK — the debug variant gets `applicationIdSuffix = ".dev"` and installs as a *separate* app next to the real Goonyomi.
  ```
  export JAVA_HOME=/home/mark/jdk17
  export ANDROID_HOME=/home/mark/Android/Sdk
  export ANDROID_SDK_ROOT=/home/mark/Android/Sdk
  export PATH=$PATH:$JAVA_HOME/bin:$ANDROID_HOME/platform-tools
  ./gradlew :app:assembleRelease
  adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk
  ```
- `./gradlew :app:compileReleaseKotlin` is the fast correctness gate (~5-15 s incremental, versus ~2-3.5 min for a full `assembleRelease`). CI enforces `spotlessCheck`; run `spotlessApply` first, the check alone only tells you it failed.
- Test device: Xiaomi 11 Lite 5G NE (adb device `eb8d8789`), which does connect over USB — install the arm64 APK. There is **no `/dev/kvm` on this machine**, so the x86_64 `emulator-5554` cannot boot; if no device is attached, you cannot test on-device at all.
- Driving the phone: `screencap` is 1:1 with the 1080×2400 screen, but re-read a fresh screenshot before every tap. Acting on a coordinate eyeballed several steps earlier is how a video got started and left floating in picture-in-picture.
- Coil logs nothing on failure by default. For image bugs, turn on **More → Settings → Advanced → Verbose logging**, reproduce, read `logcat`, then turn it off again.
- App package id: `xyz.jmir.tachiyomi.mi`.
