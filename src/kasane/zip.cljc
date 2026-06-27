(ns kasane.zip
  "ZIP container decode (PKZIP). Sketch (.sketch), OOXML (.docx/.xlsx/.pptx),
   ODF and EPUB are all ZIPs — this unpacks entries by reading the central
   directory and inflating each member with the pure inflate (ZIP uses *raw*
   DEFLATE, so inflate-raw). No external library. See ADR-2606272100.

   R0 returns the entry table + decompressed bytes; JSON/XML semantic parsing
   (Sketch document.json, OOXML XML) is a follow-up (would add a parser dep)."
  (:require [kasane.bytes :as b]
            [kasane.codec.inflate :as inflate]))

(defn- u16 [bv o] (+ (nth bv o) (* 256 (nth bv (+ o 1)))))
(defn- u32 [bv o] (+ (nth bv o) (* 256 (nth bv (+ o 1)))
                     (* 65536 (nth bv (+ o 2))) (* 16777216 (nth bv (+ o 3)))))

(defn- find-eocd
  "Scan backward for the End Of Central Directory signature (PK\\05\\06)."
  [bv]
  (loop [i (- (count bv) 22)]
    (cond
      (< i 0) (throw (ex-info "zip: no EOCD (not a zip?)" {}))
      (and (= (nth bv i) 0x50) (= (nth bv (+ i 1)) 0x4b)
           (= (nth bv (+ i 2)) 0x05) (= (nth bv (+ i 3)) 0x06)) i
      :else (recur (dec i)))))

(defn parse
  "Parse ZIP `data` → vector of {:name :method :size :bytes} (bytes = inflated)."
  [data]
  (let [bv        (vec data)
        eocd      (find-eocd bv)
        cd-count  (u16 bv (+ eocd 10))
        cd-offset (u32 bv (+ eocd 16))]
    (loop [i 0 off cd-offset entries []]
      (if (>= i cd-count)
        entries
        (let [method (u16 bv (+ off 10))
              csize  (u32 bv (+ off 20))
              usize  (u32 bv (+ off 24))
              nlen   (u16 bv (+ off 28))
              elen   (u16 bv (+ off 30))
              clen   (u16 bv (+ off 32))
              lho    (u32 bv (+ off 42))                ; local header offset
              name   (b/bytes->ascii (subvec bv (+ off 46) (+ off 46 nlen)))
              ldata  (+ lho 30 (u16 bv (+ lho 26)) (u16 bv (+ lho 28)))  ; skip local hdr+name+extra
              raw    (subvec bv ldata (+ ldata csize))
              bytes  (case method
                       0 raw                            ; stored
                       8 (inflate/inflate-raw raw)      ; ZIP deflate = RAW deflate
                       raw)]                            ; other methods: passthrough
          (recur (inc i) (+ off 46 nlen elen clen)
                 (conj entries {:name name :method method :size usize :bytes bytes})))))))

(defn names [entries] (mapv :name entries))
(defn entry [entries name] (first (filter #(= (:name %) name) entries)))
