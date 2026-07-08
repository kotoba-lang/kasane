;; nbb entry point for kasane's portable (.cljc) test suite.
;; Run from the repo root: nbb -cp "$(clojure -A:test -Spath)" test/run.cljs
;;
;; kasane.ooxml-test is deliberately excluded — it's a .clj (not .cljc) file
;; because its fixture-generation helper uses java.util.zip.ZipOutputStream
;; to build synthetic zip archives, and no portable (JVM-free) zip *writer*
;; exists anywhere in this dependency graph yet (org-pkware-zip only reads).
;; `clojure -M:test` still runs it.
(require '[clojure.test :as t]
         '[kasane.codec-test]
         '[kasane.decode-test]
         '[kasane.bmp-test]
         '[kasane.json-test]
         '[kasane.vector-test])

(let [{:keys [fail error]} (t/run-tests 'kasane.codec-test
                                        'kasane.decode-test
                                        'kasane.bmp-test
                                        'kasane.json-test
                                        'kasane.vector-test)]
  (js/process.exit (if (pos? (+ fail error)) 1 0)))
