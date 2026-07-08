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
| `kasane.gltf` / `kasane.svg` / `kasane.json` | glTF/SVG/JSON（reverse-domain 名の
  姉妹repoへの統合が follow-up。詳細は下記） | ○ |
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

**follow-up（未着手）**: `kasane.gltf`→`org-khronos-gltf`/`org-khronos-glb` への
reader統合、`kasane.svg`→`org-w3-svg` への reader統合、`kasane.json`→既存
`kotoba-lang/json` への切替、OOXML投影（`kasane.normalize/ooxml->doc`）→既存
`ooxml`/`office`/`office-style`/`drawingml` クラスタへの委譲。いずれも既存repoが
逆方向（書き専用）または重複実装を持っており、単純削除ではなく統合PRが必要なため
このバッチでは未実施。

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
