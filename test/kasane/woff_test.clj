(ns kasane.woff-test
  "WOFF1 decode validated by reassembling the wrapped SFNT and checking it
   yields the SAME metadata as the original TTF (fixture wraps noto-lycian.ttf
   with per-table zlib)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [kasane.woff :as woff]
            [kasane.ttf :as ttf]
            [kasane.normalize :as norm]))

(defn- rd [p] (mapv #(bit-and (int %) 0xff)
                    (with-open [in (io/input-stream (io/resource p))] (.readAllBytes in))))

(deftest woff-matches-ttf
  (let [from-ttf  (ttf/parse (rd "kasane/fixtures/noto-lycian.ttf"))
        from-woff (woff/parse (rd "kasane/fixtures/noto-lycian.woff"))]
    (testing "reassembled SFNT decodes identically"
      (is (true? (:woff from-woff)))
      (is (true? (:magic-ok? from-woff)))                      ; head table intact after reassembly
      (is (= (:family from-ttf) (:family from-woff)))
      (is (= (:num-glyphs from-ttf) (:num-glyphs from-woff)))
      (is (= (:units-per-em from-ttf) (:units-per-em from-woff)))
      (is (= (:tables from-ttf) (:tables from-woff))))))

(deftest woff-normalize
  (let [doc (norm/->doc :woff (woff/parse (rd "kasane/fixtures/noto-lycian.woff")))]
    (is (= :woff (:kasane/format doc)))
    (is (re-find #"Noto" (get-in doc [:kasane/meta :family])))
    (is (pos? (get-in doc [:kasane/meta :glyphs])))))
