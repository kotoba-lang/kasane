(ns kasane.json
  "Minimal dependency-free JSON reader (pure cljc, WASM-friendly). Objects →
   maps (string keys kept as keywords), arrays → vectors, null → nil. Used by
   the Sketch path (document.json / pages/*.json). See ADR-2606272100."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defn- ws? [c] (or (= c \space) (= c \tab) (= c \newline) (= c \return)))
(defn- peek-c [st] (let [i @(:i st)] (when (< i (:n st)) (nth (:s st) i))))
(defn- next-c [st] (let [c (peek-c st)] (swap! (:i st) inc) c))
(defn- skip-ws [st] (loop [] (when (ws? (peek-c st)) (next-c st) (recur))))

(declare parse-value)

(defn- parse-string [st]
  (next-c st)                                                   ; opening "
  (loop [acc []]
    (let [c (next-c st)]
      (cond
        (nil? c)  (apply str acc)
        (= c \")  (apply str acc)
        (= c \\)  (let [e (next-c st)]
                    (case e
                      \" (recur (conj acc \"))
                      \\ (recur (conj acc \\))
                      \/ (recur (conj acc \/))
                      \n (recur (conj acc \newline))
                      \r (recur (conj acc \return))
                      \t (recur (conj acc \tab))
                      \b (recur (conj acc \backspace))
                      \f (recur (conj acc \formfeed))
                      \u (let [hex (apply str [(next-c st) (next-c st) (next-c st) (next-c st)])]
                           (recur (conj acc (char (#?(:clj Long/parseLong :cljs js/parseInt) hex 16)))))
                      (recur (conj acc e))))
        :else (recur (conj acc c))))))

(defn- parse-number [st]
  (let [t (loop [acc []]
            (let [c (peek-c st)]
              (if (and c (or (<= (int \0) (int c) (int \9)) (#{\- \+ \. \e \E} c)))
                (do (next-c st) (recur (conj acc c)))
                (apply str acc))))]
    (edn/read-string t)))

(defn- parse-array [st]
  (next-c st)                                                   ; [
  (loop [acc []]
    (skip-ws st)
    (cond
      (= (peek-c st) \]) (do (next-c st) acc)
      :else (let [v (parse-value st)]
              (skip-ws st)
              (when (= (peek-c st) \,) (next-c st))
              (recur (conj acc v))))))

(defn- parse-object [st]
  (next-c st)                                                   ; {
  (loop [m {}]
    (skip-ws st)
    (cond
      (= (peek-c st) \}) (do (next-c st) m)
      :else (let [k (parse-string st)
                  _ (skip-ws st)
                  _ (next-c st)                                 ; :
                  v (parse-value st)]
              (skip-ws st)
              (when (= (peek-c st) \,) (next-c st))
              (recur (assoc m (keyword k) v))))))

(defn- parse-value [st]
  (skip-ws st)
  (let [c (peek-c st)]
    (cond
      (= c \{) (parse-object st)
      (= c \[) (parse-array st)
      (= c \") (parse-string st)
      (= c \t) (do (dotimes [_ 4] (next-c st)) true)
      (= c \f) (do (dotimes [_ 5] (next-c st)) false)
      (= c \n) (do (dotimes [_ 4] (next-c st)) nil)
      :else    (parse-number st))))

(defn parse
  "Parse a JSON string → Clojure data (objects=maps with keyword keys)."
  [s]
  (parse-value {:s s :i (atom 0) :n (count s)}))
