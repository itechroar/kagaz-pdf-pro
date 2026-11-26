# Signing the Android app

Release builds are signed with your own key when these GitHub secrets exist; otherwise they use a
debug key (installable, but not suitable for the Play Store, and updates must use the same key).

1. Create a key once and keep the file and passwords safe. Losing it means users must uninstall to update.

       keytool -genkeypair -v -keystore kagaz.jks -alias kagaz -keyalg RSA -keysize 4096 -validity 10000

2. Encode it: `base64 -w0 kagaz.jks` (macOS: `base64 -i kagaz.jks`).
3. In the repo go to Settings, Secrets and variables, Actions, and add:
   `KAGAZ_KEYSTORE_B64` (the encoded text), `KAGAZ_KEYSTORE_PASSWORD`, `KAGAZ_KEY_ALIAS` (`kagaz`), `KAGAZ_KEY_PASSWORD`.
4. Publish a release; the APK comes out signed.

Never commit the `.jks` file.
