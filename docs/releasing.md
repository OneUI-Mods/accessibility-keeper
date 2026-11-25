# リリース

JDK 27以降とAndroid SDK 37.2を用意し、`./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintRelease :app:spotlessCheck`を実行します。

配布用PKCS12鍵は非公開の`.signing/release.keystore`、パスワードは`.signing/password`、aliasは`accessibilitykeeper-release`です。同じ鍵を継続し、作り直しません。バックアップはDocumentsとWindows Bドライブの非公開署名鍵フォルダに保存します。

変更をコミットした後に`python3 tools/package_release.py --source-ref HEAD`を実行します。署名済みrelease APK、同一コミットのソースZIP、署名検証結果、release-metadata.json、SHA256SUMSをGitHubのReleaseへ添付します。タグは移動しません。
