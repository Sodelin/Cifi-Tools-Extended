# CIFI Tools Extended — source preview

The app opens the existing CIFI Tools website inside a WebView. Its web storage is separate from other browsers. The menu can open the original website in a browser for the user to sign in there; that browser session and its data are separate from the app. Refresh, verification and website-sync actions are disabled in this public snapshot; the phone-specific Termux launcher is an unavailable stub. In-app sign-in, automatic game capture and a portable release are not implemented or verified here.

To produce an **unsigned ARM64** APK, install JDK 17, Python 3, Android SDK tools (`aapt2`, `d8`, `zipalign`), Android platform files and a Rust `aarch64-linux-android` toolchain and linker. Then run:

```sh
ANDROID_JAR=/path/to/android.jar OUTPUT_APK=/path/to/new-unsigned.apk bash build-unsigned.sh
```

When cross-compiling, set `RUST_TARGET=aarch64-linux-android` and configure the target linker. The script packages the experimental Rust math library, which is not connected to website simulations. An unsigned APK was built on the development phone using Android SDK 36; it has not been installed or tested on Android. Sign with your own Android signing key before installation. A differently signed APK cannot update an existing installation; uninstalling can erase that app's data.

The included Gradle module builds the Java/WebView source but does not yet package the Rust library; its full build has not been tested. The website and game remain separate third-party services.
