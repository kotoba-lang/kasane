;; nbb entry point for kasane's portable (.cljc) test suite.
;; Run from the repo root: nbb -cp "$(clojure -A:test -Spath)" test/run.cljs
;;
;; kasane.ooxml-test (.clj, not .cljc) is still excluded — its fixture-
;; generation helper uses java.util.zip.ZipOutputStream to build synthetic
;; zip archives at test time, and no portable (JVM-free) zip *writer*
;; exists anywhere in this dependency graph. `clojure -M:test` still runs
;; it. kasane.ooxml-cljc-test sidesteps this: it reads pre-built real
;; fixture *files* (committed bytes) via org-pkware-zip's zip.core/parse,
;; a portable *reader* -- this is the first OOXML coverage that runs here.
(require '[clojure.test :as t]
         '[kasane.codec-test]
         '[kasane.decode-test]
         '[kasane.bmp-test]
         '[kasane.json-test]
         '[kasane.vector-test]
         '[kasane.ooxml-cljc-test])

(let [{:keys [fail error]} (t/run-tests 'kasane.codec-test
                                        'kasane.decode-test
                                        'kasane.bmp-test
                                        'kasane.json-test
                                        'kasane.vector-test
                                        'kasane.ooxml-cljc-test)]
  (js/process.exit (if (pos? (+ fail error)) 1 0)))
