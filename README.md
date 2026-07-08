# kasane（重ね）

[![CI](https://github.com/kotoba-lang/kasane/actions/workflows/ci.yml/badge.svg)](https://github.com/kotoba-lang/kasane/actions/workflows/ci.yml)

Adobe(AI/PSD) を **外部依存ゼロの純 cljc + EDN データ駆動文法**で扱うライブラリ。
binary 文法を EDN で宣言し、小さな純 cljc エンジンで解析する（Kaitai Struct を
EDN データ + Clojure 解釈で再構成）。

SSoT: `90-docs/adr/2606272100-adobe-edn-kasane.md`（superproject 側）

## 構成（コアエンジン + kasane 自身の native フォーマット、外部依存ゼロ）

| ns | 役割 | WASM(kotoba-clj) |
|---|---|---|
| `kasane.bytes` | byte cursor + read primitive（u8/u16/u32/i*・endian・固定長） | ○ |
| `kasane.decode` | **EDN 文法を解釈する純エンジン** `(decode grammar bytes)→EDN` | ○ |
| `kasane.codec` | PackBits（PSD :rle channels 用） | ○ |
| `kasane.normalize` | raw → 共通 `:kasane/doc` モデル（PSD/BMP/Sketch 用に加え、外部
  フォーマットrepo の生 parse 出力からも呼べる純関数群 — 下記参照） | ○ |
| `kasane.schema` | **malli = 共通モデルの SSoT**（検証/生成。WASM 経路外） | ✕ |
| `kasane.gltf` / `kasane.svg` / `kasane.json` | glTF/SVG/JSON —
  既存 reverse-domain 姉妹repoへの薄い adapter（2026-07-08 統合済み。詳細は下記） | ○ |
| `resources/kasane/grammar/{psd,bmp}.edn` | kasane 自身の native フォーマット文法（データ） | data |

## 2026-07 分解: 個別フォーマットのDSLを reverse-domain 名の別リポジトリへ抽出

このリポジトリはもともと PSD/PDF/PNG/JPEG/GIF/TIFF/ZIP/TTF/WOFF/ISOBMFF/EPUB/ODF
等、十数個の独立した外部フォーマット仕様を1リポジトリに同居させていた。kotoba-lang
の既存命名規約（`org-<標準化団体>-<spec>` reverse-domain 命名、ADR-2607052300 以降）
に揃えるため、`kasane` 固有でない各フォーマットの実装を個別リポジトリへ抽出した
（`com-junkawasaki/root` ADR 精度2607072500 の pdk→org-synopsys-liberty/org-si2-lef
分離と同型のバッチ）。

| 旧 ns | 移動先 |
|---|---|
| `kasane.codec.inflate`（DEFLATE/zlib） | [`org-ietf-deflate`](https://github.com/kotoba-lang/org-ietf-deflate) |
| `kasane.png` | [`org-w3-png`](https://github.com/kotoba-lang/org-w3-png) |
| `kasane.gif` | [`org-compuserve-gif`](https://github.com/kotoba-lang/org-compuserve-gif) |
| `kasane.tiff` | [`org-adobe-tiff`](https://github.com/kotoba-lang/org-adobe-tiff) |
| `kasane.zip` | [`org-pkware-zip`](https://github.com/kotoba-lang/org-pkware-zip) |
| `kasane.jpeg` + `kasane.jpeg.decode` | [`org-iso-jpeg`](https://github.com/kotoba-lang/org-iso-jpeg) |
| `kasane.ttf` | [`org-iso-opentype`](https://github.com/kotoba-lang/org-iso-opentype) |
| `kasane.woff` | [`org-w3-woff`](https://github.com/kotoba-lang/org-w3-woff) |
| `kasane.isobmff` | [`org-iso-isobmff`](https://github.com/kotoba-lang/org-iso-isobmff)（`utsushi` の MP4 demux/mux/remux と統合） |
| `kasane.cos`（PDF） | [`org-iso-pdf`](https://github.com/kotoba-lang/org-iso-pdf) |
| epub->doc | [`org-w3-epub`](https://github.com/kotoba-lang/org-w3-epub) |
| odf->doc | [`org-oasis-odf`](https://github.com/kotoba-lang/org-oasis-odf) |

## 2026-07-08 追記: gltf/svg/json は既存姉妹repoへの薄い adapter に統合済み

- `kasane.json` → [`kotoba-lang/json`](https://github.com/kotoba-lang/json)
  （`json.core/decode`）に委譲。既存repoは string-keyed map を返すため、
  kasane側の元契約（keyword-keyed map、`kasane.gltf`/`sketch->doc` の
  `(:_class layer)` 等が前提）を保つ keywordize wrapper を追加。
- `kasane.svg` → [`org-w3-svg`](https://github.com/kotoba-lang/org-w3-svg)
  （新設 `svg.reader` ns）に委譲。既存 `svg.core/attrs` は EDN要素→属性map
  （write側）で、kasaneが必要とする「属性文字列→map」（read側）とは入力形状が
  違うため、衝突を避けて `svg.reader` という別nsに read側を追加した。
- `kasane.gltf` → [`org-khronos-glb`](https://github.com/kotoba-lang/org-khronos-glb)
  （`glb/parse-glb` + `glb.json/parse`）に委譲。調査の結果、org-khronos-glb/
  org-khronos-gltf は既に**kasane.gltfより高機能なreader**（accessor decode
  まで含む完全実装）を別セッションで先行実装済みだったと判明 — 「reader追加」
  ではなく「単純に既存のより完成した実装へ委譲」で済んだ。

**follow-up（未着手）**: OOXML投影（`kasane.normalize/ooxml->doc`）→既存
`ooxml`/`office`/`office-style`/`drawingml` クラスタへの委譲。`office.opc/
open-package` はJVM専用の`java.util.zip`直接依存（`#?(:cljs (throw ...))`）で
書かれており、kasaneの「純cljc・ホスト依存ゼロ」原則と設計思想が食い違う
（kasaneは`org-pkware-zip`で既にunzip済みのentriesを受け取る想定）。安全な
統合には`office/graph.cljc`のデータモデルの理解と、zip読み込み層の扱いを
別途詰める必要があり、このバッチでは見送った。

kasane に残るのは: EDN文法エンジン本体、共通 `:kasane/doc` モデル（他repoの
parse出力も受けられる純粋な射影関数として）、そして PSD/BMP/Sketch という
kasane 自身の flagship native フォーマット。

## 使い方

```clojure
(require '[clojure.edn :as edn] '[clojure.java.io :as io]
         '[kasane.decode :as d] '[kasane.normalize :as norm])

(let [g   (edn/read-string (slurp (io/resource "kasane/grammar/psd.edn")))
      raw (d/decode g (slurp-bytes "design.psd"))]   ; bytes = seq of 0-255
  (norm/->doc :psd raw))
;; => #:kasane{:format :psd
;;             :canvas {:width 4 :height 3 :unit :px :color-mode :rgb ...}
;;             :nodes [#:node{:kind :raster :blend :normal :bbox [1 2 2 3] ...}]}
```

EDN 文法は宣言的データ:

```clojure
{:meta  {:endian :big :root :psd-file}
 :enums {:color-mode {3 :rgb 4 :cmyk ...}}
 :types {:psd-file [{:id :sig :type :magic :value "8BPS"}
                    {:id :width :type :u32} ...]}}
```

## テスト

```bash
bb test            # 純 cljc スイート（外部依存なし・速い）
clojure -M:test    # JVM test-runner
```

## R0 スコープ / 既知の限界（ADR-2606272100）

- **PSD**: ヘッダ・color-mode/image-resource blob・レイヤレコードまで。channel image data は
  layer-and-mask の length で skip。
- raster ピクセル実体は EDN/git にインラインせず B2+DataLad の CID 参照（CLAUDE.md 規律）。
- WASM 化は kotoba-clj の EDN-subset 成熟に追随（`decode` から段階適用）。
