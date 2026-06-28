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
  (let [page (str "{\"layers\":[{\"_class\":\"artboard\",\"name\":\"Home\","
                  "\"frame\":{\"x\":0,\"y\":0,\"width\":375,\"height\":812},"
                  "\"layers\":[{\"_class\":\"rectangle\",\"name\":\"BG\",\"frame\":{\"x\":0,\"y\":0,\"width\":375,\"height\":100},"
                  "\"style\":{\"fills\":[{\"isEnabled\":true,\"color\":{\"red\":1,\"green\":0,\"blue\":0,\"alpha\":1}}]}},"
                  "{\"_class\":\"shapePath\",\"name\":\"Tri\",\"frame\":{\"x\":0,\"y\":0,\"width\":10,\"height\":10},"
                  "\"points\":[{\"point\":\"{0, 0}\"},{\"point\":\"{1, 0}\"},{\"point\":\"{0.5, 1}\"}]},"
                  "{\"_class\":\"text\",\"name\":\"Title\",\"frame\":{\"x\":16,\"y\":40,\"width\":200,\"height\":24}}]},"
                  "{\"_class\":\"artboard\",\"name\":\"Detail\",\"frame\":{\"x\":420,\"y\":0,\"width\":375,\"height\":812}}]}")
        entries (zip/parse (make-zip [["document.json" "{\"pages\":[]}"]
                                      ["pages/A1B2.json" page]]))
        doc     (norm/sketch->doc entries)]
    (is (= :sketch (:kasane/format doc)))
    (is (true? (get-in doc [:kasane/meta :has-document])))
    (is (= 2 (get-in doc [:kasane/meta :artboards])))
    (let [home (first (:kasane/nodes doc))]
      (is (= :artboard (:node/kind home)))
      (is (= "Home" (:node/name home)))
      (is (= [0 0 375 812] (:node/bbox home)))
      (testing "nested layer tree + fill/points geometry"
        (is (= 3 (count (:node/children home))))
        (is (= [:vector :vector :text] (mapv :node/kind (:node/children home))))
        (let [bg (first (:node/children home)) tri (second (:node/children home))]
          (is (= [0 0 375 100] (:node/bbox bg)))
          (is (= "#ff0000" (:node/fill bg)))                 ; red solid fill
          (is (= [[0 0] [1 0] [0.5 1]] (:vector/points tri))))))))

(deftest ooxml-normalize
  (testing "docx detection + w:t text extraction"
    (let [docx "<?xml version=\"1.0\"?><w:document><w:body><w:p><w:r><w:t>Hello</w:t></w:r><w:r><w:t xml:space=\"preserve\"> world</w:t></w:r></w:p></w:body></w:document>"
          doc (norm/ooxml->doc (zip/parse (make-zip [["[Content_Types].xml" "<x/>"]
                                                     ["word/document.xml" docx]
                                                     ["_rels/.rels" "<r/>"]])))]
      (is (= :docx (:kasane/format doc)))
      (is (= ["Hello" " world"] (mapv #(-> % :text/runs first :text) (:kasane/nodes doc))))))
  (testing "pptx detection + shape geometry (EMU) + a:t text"
    (let [slide (str "<p:sld><p:cSld><p:spTree>"
                     "<p:sp><p:spPr><a:xfrm><a:off x=\"914400\" y=\"457200\"/>"
                     "<a:ext cx=\"1828800\" cy=\"685800\"/></a:xfrm>"
                     "<a:prstGeom prst=\"ellipse\"/><a:solidFill><a:srgbClr val=\"00FF88\"/></a:solidFill></p:spPr>"
                     "<p:txBody><a:p><a:r><a:t>Slide title</a:t></a:r></a:p></p:txBody></p:sp>"
                     "</p:spTree></p:cSld></p:sld>")
          doc (norm/ooxml->doc (zip/parse (make-zip [["[Content_Types].xml" "<x/>"]
                                                     ["ppt/slides/slide1.xml" slide]])))]
      (is (= :pptx (:kasane/format doc)))
      (is (= 1 (get-in doc [:kasane/meta :shapes])))
      (let [n (first (:kasane/nodes doc))]
        (is (= [914400 457200 1828800 685800] (:node/bbox n)))
        (is (= "#00ff88" (:node/fill n)))                    ; solid fill srgbClr
        (is (= :ellipse (:svg/tag n)))                       ; preset geometry
        (is (= "Slide title" (-> n :text/runs first :text)))))))
