(ns kasane.codec
  "Pure-cljc codecs, named so EDN grammars can request them data-driven
   (e.g. `{:codec :zip}` / `{:codec :flate}`). DEFLATE lives in
   kasane.codec.inflate; the simple ones live here."
  (:require [kasane.codec.inflate :as inflate]))

(defn packbits
  "Decode PackBits RLE (used by PSD :rle channels and TIFF).
   `data` = seq of unsigned bytes. Returns a vector of unsigned bytes."
  [data]
  (let [v (vec data) n (count v)]
    (loop [i 0 out []]
      (if (>= i n)
        out
        (let [h (nth v i)]
          (cond
            (= h 128) (recur (inc i) out)                          ; no-op
            (< h 128) (let [cnt (inc h)]                           ; literal run
                        (recur (+ i 1 cnt) (into out (subvec v (inc i) (+ i 1 cnt)))))
            :else     (let [cnt (- 257 h) b (nth v (inc i))]       ; replicate run
                        (recur (+ i 2) (into out (repeat cnt b))))))))))

;; ---- LZW (TIFF compression 5 / PDF LZWDecode = MSB,early-change; GIF = LSB)
;; The code-width-change timing is the classic LZW gotcha; the constants here
;; were locked against a real libtiff-encoded fixture (TIFF, MSB, early-change:
;; widen when the next free code == 2^width - 1). GIF (non-early) widens at
;; == 2^width. See resources/kasane/fixtures + kasane.tiff/kasane.gif.
(defn- lzw-reader [order data]
  {:order order :data (vec data) :len (count data) :bp (atom 0) :bi (atom 0)})

(defn- lzw-bits [r n]
  (loop [i 0 acc 0]
    (if (= i n) acc
        (let [bp @(:bp r) bi @(:bi r)]
          (if (>= bp (:len r))
            nil
            (let [byte (nth (:data r) bp)
                  bit  (if (= (:order r) :lsb)
                         (bit-and (bit-shift-right byte bi) 1)
                         (bit-and (bit-shift-right byte (- 7 bi)) 1))]
              (if (= bi 7) (do (reset! (:bi r) 0) (swap! (:bp r) inc)) (reset! (:bi r) (inc bi)))
              (recur (inc i) (if (= (:order r) :lsb)
                               (bit-or acc (bit-shift-left bit i))
                               (bit-or (bit-shift-left acc 1) bit)))))))))

(defn lzw
  "Decode an LZW stream → vector of unsigned bytes.
   opts: :order (:msb default | :lsb), :min-code-size (default 8),
   :early-change (true for TIFF/PDF, false for GIF)."
  [data {:keys [order min-code-size early-change]
         :or   {order :msb min-code-size 8 early-change true}}]
  (let [clear (bit-shift-left 1 min-code-size)
        eoi   (inc clear)
        fw    (inc min-code-size)
        base  (mapv vector (range clear))
        fresh (-> base (conj nil) (conj nil))
        r     (lzw-reader order data)]
    (loop [width fw, dict fresh, nextc (+ clear 2), prev nil, out (transient [])]
      (let [code (lzw-bits r width)]
        (cond
          (nil? code)    (persistent! out)
          (= code clear) (recur fw fresh (+ clear 2) nil out)
          (= code eoi)   (persistent! out)
          :else
          (let [entry (cond
                        (and (< code (count dict)) (some? (nth dict code))) (nth dict code)
                        (= code nextc) (conj prev (nth prev 0))
                        :else (throw (ex-info "kasane.codec/lzw: bad code" {:code code :nextc nextc})))
                out2  (reduce conj! out entry)]
            (if (nil? prev)
              (recur width dict nextc entry out2)
              (let [nextc2 (inc nextc)
                    width2 (if (and (< width 12)
                                    (= nextc2 (- (bit-shift-left 1 width) (if early-change 1 0))))
                             (inc width) width)]
                (recur width2 (conj dict (conj prev (nth entry 0))) nextc2 entry out2)))))))))

(defn decode
  "Apply a named codec to unsigned-byte `data`."
  [codec data]
  (case codec
    :raw     (vec data)
    :rle     (packbits data)
    (:zip
     :flate
     :inflate) (inflate/inflate data)
    :lzw     (lzw data {:order :msb :min-code-size 8 :early-change true})   ; TIFF/PDF
    (throw (ex-info "kasane.codec: unknown codec" {:codec codec}))))
