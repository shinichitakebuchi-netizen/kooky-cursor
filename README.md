# OneTouch Cursor（デモ v0.2.0 トリガー版）

画面端の縦長トリガーを長押し→スワイプでカーソルを出し、**指を離した位置をタップ**する片手操作アプリ。
対象：Android 14 以降（Galaxy S26 想定）

## インストール・アップデート（ランチャー／キーボードと同じ流れ）

1. mainにプッシュすると GitHub Actions が自動ビルド
2. Releases の「Latest build」（タグ `latest`）に `app-debug.apk` が上書きされる
3. 初回：Galaxyで https://github.com/shinichitakebuchi-netizen/kooky-cursor/releases/latest を開いて `app-debug.apk` をDL → インストール
4. 2回目以降：アプリ内の「UPDATE」ボタン → 同じページが開く → DLして上書きインストール

## 初回セットアップ（Galaxy）

1. アプリを開く →「ユーザー補助の設定を開く」
2. インストール済みアプリ → OneTouch Cursor → ON
3. **ONにできない場合**（ブラウザ等からAPKを入れたアプリは制限される）：
   設定 → アプリ → OneTouch Cursor → 右上「︙」→「制限付き設定を許可」→ もう一## 操作

| 操作 | 結果 |
|---|---|
| トリガー（画面端の帯）を長押し | 振動してカーソルが出る |
| そのままスワイプ | カーソルが指の動き×速度で移動（速く動かすと加速） |
| 指を離す | カーソル位置をタップしてカーソルが消える |
| トリガーの上に指を戻して離す | キャンセル（何もしない） |
| 長押し前に動かす／すぐ離す | 何もしない（誤操作防止） |

設定：左端/右端、上下の位置、長さ、太さ、透明度、色、長押し時間、カーソル速度・サイズ・色、振動、キャンセル ON/OFF

帰） |

## 構成

```
app/src/main/java/jp/kooky/onetouchcursor/
  MainActivity.kt        設定画面（Jetpack Compose）
  CursorService.kt       ユーザー補助サービス：オーバーレイ表示 + dispatchGesture
  TriggerView.kt         画面端トリガーのタッチ判定（長押し→移動→離してタップ）
  CursorOverlayView.kt   円形カーソル描画
  Settings.kt            設定値（SharedPreferences）
app/src/main/res/xml/accessibility_service_config.xml
.github/workflows/build.yml
keystore/debug.keystore  上書きインストールのための固定署名キー（デモ用）
```

## 技術的な制約・未対応

- トリガーの帯の上は、下にあるアプリを直接タッチできない（帯がタッチを受け取るため）。太さ・長さ・位置で調整
- Galaxyの「戻る」ジェスチャー／エッジパネルと重なる場合の優先順位は端末次第。
  戻るジェスチャーより優先する指定（systemGestureExclusionRects）は入れているが、効くかは未確認
- ダブルクリック・長押し・ドラッグ・スクロールはこの版では外している
- dispatchGesture は一部アプリ（銀行系・ゲーム等）で無視・ブロックされる可能性がある
