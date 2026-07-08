(ns kasane.testutil
  "Portable (:clj / :cljs-via-nbb) resource loading for kasane's test suite.
   :clj uses clojure.java.io/resource (JVM classpath). :cljs (run under nbb,
   ClojureScript-on-Node — see CLAUDE.md's `.cljc`/`.kotoba` runtime priority
   order, nbb ranks above plain JVM) has no classpath/resource-loader
   concept, so it reads resources/-relative paths directly via node:fs.
   This means nbb-run tests must be invoked with the kasane repo root as
   cwd (both `clojure -M:test` and the nbb invocation documented in
   README.md already assume this)."
  (:require #?(:clj [clojure.java.io :as io])
            #?(:cljs ["node:fs" :as fs])))

(defn slurp-resource
  "Read a resources/-relative path (e.g. \"kasane/grammar/psd.edn\") as a
   string."
  [path]
  #?(:clj (slurp (io/resource path))
     :cljs (fs/readFileSync (str "resources/" path) "utf8")))
