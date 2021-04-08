(ns harja.validointi
  "Validointeja jotka toimivat sekä cljs- että clj-koodissa."
  (:require
    [clojure.string :as str]))

(defn validoi-numero
  "Validaattori numeroille, params: [numero alaraja ylaraja desimaalien-max-maara]"
  [numero alaraja ylaraja desimaalien-max-maara]
  (let [numero (str/replace numero "," ".")
        desimaalit (when numero
                     (count (second (str/split numero #"\."))))]
    (and
      (<= alaraja numero ylaraja)
      ;; Estetään kirjoittamasta , tai . kokonaislukuun
      (or (not= 0 desimaalien-max-maara) (not (str/includes? numero ".")))
      (or (nil? numero)
          (<= desimaalit desimaalien-max-maara)))))