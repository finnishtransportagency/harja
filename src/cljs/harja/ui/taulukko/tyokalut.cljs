(ns harja.ui.taulukko.tyokalut
  (:require [harja.loki :as loki]
            [harja.ui.taulukko.protokollat :as p]))

(defn numero-re
  ([] (numero-re {}))
  ([{kokonaisosan-maara :kokonaisosan-maara desimaalien-maara :desimaalien-maara
     positiivinen? :positiivinen? kokonaisluku? :kokonaisluku?
     :or {kokonaisosan-maara 10 desimaalien-maara 10 positiivinen? false kokonaisluku? false}}]
   (str (when-not positiivinen? "-?")
        "\\d{1," kokonaisosan-maara "}"
        (when-not kokonaisluku? (str "((\\.|,)\\d{0," desimaalien-maara "})?")))))

(defn positiivinen-numero-re
  ([] (positiivinen-numero-re {}))
  ([asetukset]
    (numero-re (assoc asetukset :positiivinen? true))))

(defmulti lisaa-kaytos
          (fn [kaytos _]
            (cond
              (map? kaytos) (first (keys kaytos))
              (keyword? kaytos) kaytos
              :else nil)))

(defmethod lisaa-kaytos :eventin-arvo
  [_ toiminto]
  (comp toiminto
        (fn [event]
          (.. event -target -value))))

(defmethod lisaa-kaytos :positiivinen-numero
  [_ toiminto]
  (comp toiminto
        (fn [arvo]
          (let [positiivinen-arvo? (re-matches (re-pattern (positiivinen-numero-re)) arvo)]
            (when (or (= "" arvo) positiivinen-arvo?)
              arvo)))))

(defmethod lisaa-kaytos :default
  [kaytos toiminto]
  (loki/warn "KAYTOSTÄ: " kaytos " EI OLE MÄÄRITETTY!")
  toiminto)


(defn jana
  "Palauttaa jana(t) joiden id vastaa annettua"
  [taulukko id]
  (filter #(p/janan-id? % id) taulukko))

(defn janan-osa
  "Palauttaa janan elementi(t) joiden id vastaa annettua"
  [jana id]
  (filter #(p/osan-id? % id) (p/janan-osat jana)))

(defn janan-index
  "Palauttaa janan indeksin taulukossa"
  [taulukko jana]
  (first (keep-indexed #(when (= (p/janan-id %2) (p/janan-id jana))
                          %1)
                       taulukko)))

(defn osan-polku-taulukossa
  [taulukko osa]
  (first (keep-indexed (fn [rivin-index jana]
                         (when-let [osan-index (p/osan-index jana osa)]
                           [rivin-index osan-index]))
                       taulukko)))