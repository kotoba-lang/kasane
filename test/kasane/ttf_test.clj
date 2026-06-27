(ns kasane.ttf-test
  "SFNT decode validated against a real OFL TrueType font (NotoSansLycian).
   The 'head' table magicNumber (0x5F0F3CF5) is a fixed constant, so reading it
   correctly proves the table-directory + offset parsing end to end."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [kasane.ttf :as ttf]
            [kasane.normalize :as norm]))

(defn- rd [p] (mapv #(bit-and (int %) 0xff)
                    (with-open [in (io/input-stream (io/resource p))] (.readAllBytes in))))

(deftest sfnt-parse
  (let [p (ttf/parse (rd "kasane/fixtures/noto-lycian.ttf"))]
    (testing "header + table directory"
      (is (= 0x00010000 (:sfnt-version p)))                    ; TrueType
      (is (= 10 (:num-tables p)))
      (is (every? (:tables p) ["head" "maxp" "name" "cmap" "glyf"])))
    (testing "head magicNumber proves offset parsing"
      (is (true? (:magic-ok? p)))
      (is (= 0x5F0F3CF5 (:magic p))))
    (testing "metadata"
      (is (contains? #{1000 2048} (:units-per-em p)))
      (is (pos? (:num-glyphs p)))
      (is (re-find #"Noto" (:family p))))))

(deftest ttf-normalize
  (let [doc (norm/->doc :ttf (ttf/parse (rd "kasane/fixtures/noto-lycian.ttf")))]
    (is (= :ttf (:kasane/format doc)))
    (is (re-find #"Noto" (get-in doc [:kasane/meta :family])))
    (is (pos? (get-in doc [:kasane/meta :glyphs])))))
