(ns kasane.bmp-test
  "BMP structural decode + normalize (kasane's native format, not
   extracted — see README for the formats that moved to their own
   reverse-domain-named repos)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])
            [kasane.decode :as d]
            [kasane.normalize :as norm]))

(defn- res [p] (edn/read-string #?(:clj (slurp (io/resource p)) :cljs (throw (ex-info "n/a" {})))))
(def bmp-grammar (res "kasane/grammar/bmp.edn"))

;; little-endian builders
(defn- u16le [n] [(bit-and n 0xff) (bit-and (bit-shift-right n 8) 0xff)])
(defn- u32le [n] [(bit-and n 0xff) (bit-and (bit-shift-right n 8) 0xff)
                  (bit-and (bit-shift-right n 16) 0xff) (bit-and (bit-shift-right n 24) 0xff)])
(defn- ascii [s] (mapv int s))

(deftest bmp-decode
  (let [bytes (vec (concat (ascii "BM") (u32le 70) (u32le 0) (u32le 54)   ; file hdr
                           (u32le 40) (u32le 8) (u32le 6) (u16le 1) (u16le 24) ; DIB: w=8 h=6 bpp=24
                           (u32le 0) (u32le 16) (u32le 2835) (u32le 2835) (u32le 0) (u32le 0)))
        doc   (norm/->doc :bmp (d/decode bmp-grammar bytes))]
    (is (= :bmp (:kasane/format doc)))
    (is (= {:width 8 :height 6 :unit :px :dpi 72 :depth 24 :compression :rgb} (:kasane/canvas doc)))
    (is (= [0 0 8 6] (:node/bbox (first (:kasane/nodes doc)))))))
