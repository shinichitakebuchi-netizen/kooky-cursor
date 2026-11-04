# OneTouch Cursor（デモ / MVP v0.1.0）

画面下部のトラックパッドでカーソルを動かし、**指を離した位置をタップ**する片手操作アプリ。
対象：Android 14 以降（Galaxy S26 想定）

## ビルド手順（iPadのみ）

1. GitHub で新しいリポジトリ（例：`kooky-cursor`）を作る
2. この zip を展開した中身をそのままアップロード（`.github` フォルダと `keystore/debug.keystore` も必須）
   - 隠しフォルダ `.github` がアップロードされない場合は、GitHub上で
     `.github/workflows/build.yml` を「Add file → Create new file」で作り、中身を貼り付け
3. Actions タブで「Build APK」が走る → 完了後 Releases に `OneTouchCursor.apk`
4. Galaxy でダウンロードしてインストール

## 初回セットアップ（Galaxy）

1. アプリを開く →「ユーザー補助の設定を開く」
2. インストール済みアプリ → OneTouch Cursor → ON
3. **ONにできない場合**（ブラウザ等からAPKを入れたアプリは制限される）：
   設定 → アプリ → OneTouch Cursor → 右上「︙」→「制限付き設定を許可」→ もう一度 ON

## 操作

| 操作 | 結果 |
|---|---|
| パッドを1本指でなぞる | カーソル移動（相対・速く動かすと加速） |
| 指を離す | カーソル位置をタップ |
| 動かさずにタップ | カーソル位置をタップ |
| 動かさず2回タップ | ダブルクリック |
| 動かさず長押し（振動）→ 離す | 長押し（右クリック相当） |
| 長押し（振動）→ そのまま動かす → 離す | ドラッグ |
| 2本指でなぞる → 離す | カーソル位置からスクロール |
| 「◎ 中央」 | カーソルを画面中央へ |
| 「隠す」 | パネルを小さい丸に収納（丸をタップで復帰） |

## 構成

```
app/src/main/java/jp/kooky/onetouchcursor/
  MainActivity.kt        設定画面（Jetpack Compose）
  CursorService.kt       ユーザー補助サービス：オーバーレイ表示 + dispatchGesture
  TrackpadView.kt        パッドのタッチ判定（移動/タップ/ダブル/長押し/ドラッグ/スクロール）
  CursorOverlayView.kt   円形カーソル描画
  Settings.kt            設定値（SharedPreferences）
app/src/main/res/xml/accessibility_service_config.xml
.github/workflows/build.yml
keystore/debug.keystore  上書きインストールのための固定署名キー（デモ用）
```

## 指示書との違い・技術的な制約

- **SYSTEM_ALERT_WINDOW は使っていない**：ユーザー補助サービスの `TYPE_ACCESSIBILITY_OVERLAY` で
  パネルとカーソルを表示できるため不要。権限は「ユーザー補助」と「振動」だけ。
  また Android 12 以降、通常のオーバーレイ（他アプリの上に表示）はタッチを下に通す際に
  制限を受けることがあるが、ユーザー補助のオーバーレイはその対象外。
- **オーバーレイ部分は Compose ではなく通常の View**：サービス上のウィンドウで Compose を
  動かすにはライフサイクルの自前実装が必要になるため、MVPでは View で実装。設定画面は Compose。
- **ドラッグとスクロールはリアルタイムではない**：指を離した時点で軌跡をまとめて再生する方式。
  dispatchGesture は「ジェスチャーを組み立てて送る」APIのため、指に完全追従する実装は
  continueStroke を細切れに連続送信する必要があり、MVPでは見送り。
- **ダブルクリックONの時、動かさずにタップすると約0.28秒遅れて反応**：2回目のタップを待つため。
  OFFにすると即時反応。
- **パネル自身の上**をクリックしたい場合は、一時的にパネルを透過させてタップを送る。
- dispatchGesture は一部アプリ（銀行系・ゲーム等）で無視・ブロックされる可能性がある。
- 本当の「右クリック」はタッチ端末に存在しないため、長押しで代用。
- 未対応（次の段階）：ドラッグ/スクロールのリアルタイム追従、誤タップ防止のキャンセル操作、
  パネルの指でのドラッグ移動、画面回転時のパネル位置最適化。
