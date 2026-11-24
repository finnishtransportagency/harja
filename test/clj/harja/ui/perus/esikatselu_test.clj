(ns harja.ui.perus.esikatselu-test
  "Rekisterin jokaisella komponentilla on täsmälleen yksi esikatselun rekisteröinti.
  Rekisteröinti on cljs-puolella muodossa `(register! :id ...)`, joten se tarkistetaan lähdekoodista."
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [harja.ui.perus.esimerkkisivut :as esimerkkisivut]
            [harja.ui.perus.rekisteri :as rekisteri]))

(def ^:private cljs-lahdekoodit
  (->> (file-seq (io/file "src/cljs"))
    (filter #(str/ends-with? (str %) ".cljs"))
    (map slurp)))

(defn- rekisterointien-maara [id]
  (let [kutsu (re-pattern (str "\\(register! :" (java.util.regex.Pattern/quote (name id)) "\\s"))]
    (reduce + 0 (map #(count (re-seq kutsu %)) cljs-lahdekoodit))))

(defn- renderoitavat-komponentit []
  (->> cljs-lahdekoodit
    (mapcat #(re-seq #"(?s)\(register!\s+:([\w?!-]+)\s+\{[^}]*:renderoi\b" %))
    (map (comp keyword second))
    set))

(defn- esimerkkisivujen-komponentit []
  (->> esimerkkisivut/esimerkkisivut
    (mapcat :puu)
    (tree-seq #(or (sequential? %) (map? %))
              #(if (map? %)
                 (:lapset %)
                 (seq %)))
    (keep :komponentti)
    set))

(deftest jokaisella-komponentilla-on-tasan-yksi-esikatselu
  (doseq [{:keys [id]} rekisteri/komponentit]
    (testing (str id)
      (is (= 1 (rekisterointien-maara id))
        (str id ": esikatselun rekisteröintejä pitää olla täsmälleen yksi"))))
  (is (seq cljs-lahdekoodit) "cljs-lähdekoodeja ei löytynyt"))

(deftest jokaisella-renderoijalla-on-esimerkkisivu
  (let [renderoitavat (renderoitavat-komponentit)
        esimerkeissa (esimerkkisivujen-komponentit)]
    (is (empty? (set/difference renderoitavat esimerkeissa))
        (str "Renderöijillä ei ole esimerkkisivua: "
          (sort (set/difference renderoitavat esimerkeissa))))
    (is (empty? (set/difference esimerkeissa renderoitavat))
        (str "Esimerkkisivun komponenteilla ei ole :renderoi-rekisteröintiä: "
          (sort (set/difference esimerkeissa renderoitavat))))))
