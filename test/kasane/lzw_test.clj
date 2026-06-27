(ns kasane.lzw-test
  "LZW validated against REAL files. Fixtures in resources/kasane/fixtures were
   encoded by libtiff/Pillow with ground-truth pixels recorded in expected*.edn
   (re-read via Pillow). The library is dependency-free; Pillow is only the
   fixture oracle.

   TIFF (MSB, early-change) is verified bit-exact — including a 96x40 image that
   crosses the 9→10→11→12-bit code-width boundaries. GIF (LSB, non-early)
   decodes to the correct pixel COUNT but exact values still disagree with the
   Pillow oracle at row/dict boundaries; GIF pixel decode is therefore
   EXPERIMENTAL (see ADR-2606272100). The shared LZW core is proven by TIFF."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [kasane.tiff :as tiff]
            [kasane.gif :as gif]))

(defn- rd [p] (mapv #(bit-and (int %) 0xff)
                    (with-open [in (io/input-stream (io/resource p))] (.readAllBytes in))))
(defn- expected [p] (edn/read-string (slurp (io/resource p))))

(deftest tiff-lzw-pixels
  (testing "small 8x4 grayscale LZW vs libtiff ground truth"
    (is (= (get-in (expected "kasane/fixtures/expected.edn") [:tiff-gray :samples])
           (tiff/pixels (rd "kasane/fixtures/lzw_gray.tif")))))
  (testing "96x40 LZW crossing 9→10→11→12-bit code-width boundaries (bit-exact)"
    (let [exp (get-in (expected "kasane/fixtures/expected_big.edn") [:tiff :samples])]
      (is (= 3840 (count exp)))
      (is (= exp (tiff/pixels (rd "kasane/fixtures/lzw_big.tif")))))))

(deftest gif-lzw-pixel-count
  ;; EXPERIMENTAL: assert structural validity (right pixel count) only.
  (testing "small 4x2"
    (is (= 8 (count (gif/first-frame-indices (rd "kasane/fixtures/lzw_idx.gif"))))))
  (testing "96x40 decodes to the full pixel count"
    (is (= 3840 (count (gif/first-frame-indices (rd "kasane/fixtures/lzw_big.gif")))))))
