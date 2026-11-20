(ns harja.ui.komponentit.esikatselu-test
  "Rekisterin jokaisella komponentilla on täsmälleen yksi esikatselun rekisteröinti.
  Rekisteröinti on cljs-puolella muodossa `(register! :id ...)`, joten se tarkistetaan lähdekoodista."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [harja.ui.komponentit.rekisteri :as rekisteri]))

(def ^:private cljs-lahdekoodit
  (->> (file-seq (io/file "src/cljs"))
    (filter #(str/ends-with? (str %) ".cljs"))
    (map slurp)))

(defn- rekisterointien-maara [id]
  (let [kutsu (re-pattern (str "\\(register! :" (java.util.regex.Pattern/quote (name id)) "\\s"))]
    (reduce + 0 (map #(count (re-seq kutsu %)) cljs-lahdekoodit))))

(deftest jokaisella-komponentilla-on-tasan-yksi-esikatselu
  (doseq [{:keys [id]} rekisteri/komponentit]
    (testing (str id)
      (is (= 1 (rekisterointien-maara id))
        (str id ": esikatselun rekisteröintejä pitää olla täsmälleen yksi"))))
  (is (seq cljs-lahdekoodit) "cljs-lähdekoodeja ei löytynyt"))
