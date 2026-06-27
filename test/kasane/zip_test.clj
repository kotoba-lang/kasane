(ns kasane.zip-test
  "ZIP decode validated against real archives built by java.util.zip
   (ZipOutputStream defaults to raw DEFLATE → exercises inflate-raw).
   The library itself never uses java.util.zip."
  (:require [clojure.test :refer [deftest is testing]]
            [kasane.zip :as zip]
            [kasane.normalize :as norm]))

(defn- make-zip [entries]                                     ; [[name content] ...]
  (let [out (java.io.ByteArrayOutputStream.)
        zos (java.util.zip.ZipOutputStream. out)]
    (doseq [[n c] entries]
      (.putNextEntry zos (java.util.zip.ZipEntry. ^String n))
      (.write zos (.getBytes ^String c "UTF-8"))
      (.closeEntry zos))
    (.close zos)
    (mapv #(bit-and (int %) 0xff) (.toByteArray out))))

(defn- entry-text [entries name]
  (apply str (map char (:bytes (zip/entry entries name)))))

(deftest zip-roundtrip
  (testing "inflate-raw recovers ZIP DEFLATE members"
    (let [content "{\"key\": \"value with some repetition repetition repetition\"}"
          entries (zip/parse (make-zip [["a.txt" "hello"] ["b.json" content]]))]
      (is (= #{"a.txt" "b.json"} (set (zip/names entries))))
      (is (= "hello" (entry-text entries "a.txt")))
      (is (= content (entry-text entries "b.json"))))))

(deftest sketch-normalize
  (let [entries (zip/parse (make-zip [["document.json" "{}"]
                                      ["meta.json" "{}"]
                                      ["pages/A1B2.json" "{}"]
                                      ["pages/C3D4.json" "{}"]]))
        doc     (norm/sketch->doc entries)]
    (is (= :sketch (:kasane/format doc)))
    (is (true? (get-in doc [:kasane/meta :has-document])))
    (is (= 2 (get-in doc [:kasane/meta :pages])))
    (is (= 2 (count (:kasane/nodes doc))))
    (is (= :artboard (:node/kind (first (:kasane/nodes doc)))))))

(deftest ooxml-normalize
  (testing "docx detection"
    (let [doc (norm/ooxml->doc (zip/parse (make-zip [["[Content_Types].xml" "<x/>"]
                                                     ["word/document.xml" "<w/>"]
                                                     ["_rels/.rels" "<r/>"]])))]
      (is (= :docx (:kasane/format doc)))
      (is (= 3 (get-in doc [:kasane/meta :entries])))))
  (testing "pptx detection"
    (let [doc (norm/ooxml->doc (zip/parse (make-zip [["[Content_Types].xml" "<x/>"]
                                                     ["ppt/presentation.xml" "<p/>"]])))]
      (is (= :pptx (:kasane/format doc))))))
