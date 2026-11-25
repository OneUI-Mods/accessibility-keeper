# 開発メモ

## 仕組み（BZI9）

- `AccessibilityManagerService.onPackagesForceStoppedLocked(String[], AccessibilityUserState)` が、強制終了したパッケージのサービスを `mEnabledServices` とショートカットから外し、`enabled_accessibility_services` に書き戻す。
- フックは、有効なサービスを持ち、利用者が除外していないパッケージを引数から外して元の処理を呼ぶ。外したサービスは `mBindingServices` から外し、モードに応じて `mCrashedServices` に入れる（待つ）か外して再接続させる。
- プロセス終了で `AccessibilityServiceConnection.binderDied` が `mCrashedServices` に入れるため、「すぐ再接続」では直近に保持したサービスだけをそこから外して再接続させる。
- 「待つ」では `ACTION_PACKAGE_UNSTOPPED` を受けて、そのパッケージのサービスを `mCrashedServices` から外し再接続させる。

## 設定

アプリと system_server は libxposed のリモート設定（グループ `settings`）で共有する。`enabled`（既定 true）、`reconnect`（既定 false）、`excluded`（除外パッケージの集合）。
