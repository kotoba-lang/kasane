(ns kasane.jpeg-decode-test
  "Baseline JPEG pixel decode vs Pillow's decode of the same file. JPEG is lossy
   and IDCT rounding differs between implementations, so we assert bit-CLOSE
   (mean abs error small, max error bounded), not bit-exact."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [kasane.jpeg.decode :as jd]))

(defn- rd [p] (mapv #(bit-and (int %) 0xff)
                    (with-open [in (io/input-stream (io/resource p))] (.readAllBytes in))))

(deftest baseline-decode-vs-pillow
  (let [exp (edn/read-string (slurp (io/resource "kasane/fixtures/sample_rgb.edn")))
        out (jd/decode-rgb (rd "kasane/fixtures/sample.jpg"))
        e   (:rgb exp) g (:rgb out)]
    (testing "dimensions"
      (is (= (:w exp) (:width out)))
      (is (= (:h exp) (:height out)))
      (is (= (count e) (count g))))
    (testing "bit-close to Pillow"
      (let [diffs (map (fn [a b] (Math/abs (- a b))) e g)
            n     (count diffs)
            mean  (/ (double (reduce + diffs)) n)
            mx    (reduce max diffs)]
        (println (format "JPEG decode vs Pillow: mean=%.3f max=%d n=%d" mean mx n))
        (is (< mean 3.0) (str "mean abs error " mean))
        (is (< mx 40) (str "max abs error " mx))))))
