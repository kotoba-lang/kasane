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

(defn decode
  "Apply a named codec to unsigned-byte `data`.
   NOTE: :lzw (TIFF compression 5 / PDF LZWDecode / GIF) is deferred — the
   bit-exact code-width-change timing needs validation against real-file
   fixtures, which this environment can't generate. See ADR-2606272100."
  [codec data]
  (case codec
    :raw     (vec data)
    :rle     (packbits data)
    (:zip
     :flate
     :inflate) (inflate/inflate data)
    (throw (ex-info "kasane.codec: unknown/deferred codec" {:codec codec}))))
