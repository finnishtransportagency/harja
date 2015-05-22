(ns harja.ui.validointi
  "Harjan käyttöön soveltuva geneerinen muokattava ruudukkokomponentti."
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :refer [tee-kentta]]

            [cljs.core.async :refer [<! put! chan]]
            [clojure.string :as str]
            [schema.core :as s :include-macros true])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Validointi
;; Rivin skeema voi määritellä validointisääntöjä.
;; validoi-saanto multimetodi toteuttaa tarkastuksen säännön keyword tyypin mukaan
;;
(defmulti validoi-saanto (fn [saanto nimi data rivi taulukko & optiot] saanto))

(defmethod validoi-saanto :ei-tyhja [_ nimi data _ _ & [viesti]]
  (when (str/blank? data)
    viesti))

(defmethod validoi-saanto :uniikki [_ nimi data _ taulukko & [viesti]]
  (let [rivit-arvoittain (group-by nimi (vals taulukko))]
    (log "rivit-arvoittain:" (pr-str rivit-arvoittain) " JA DATA: " data)
    (when (> (count (get rivit-arvoittain data)) 1)
      viesti)))


(defn validoi-saannot
  "Palauttaa kaikki validointivirheet kentälle, jos tyhjä niin validointi meni läpi."
  [nimi data rivi taulukko saannot]
  (keep (fn [saanto]
          (if (fn? saanto)
            (saanto data rivi)
            (let [[saanto & optiot] saanto]
              (apply validoi-saanto saanto nimi data rivi taulukko optiot))))
    saannot))

(defn validoi-rivi
  "Tekee validoinnin yhden rivin kaikille kentille. Palauttaa mäpin kentän nimi -> virheet vektori."
  [taulukko rivi skeema]
  (loop [v {}
         [s & skeema] skeema]
    (if-not s
      v
      (let [{:keys [nimi hae validoi]} s]
        (if (empty? validoi)
          (recur v skeema)
          (let [virheet (validoi-saannot nimi (if hae
                                                (hae rivi)
                                                (get rivi nimi))
                          rivi taulukko
                          validoi)]
            (recur (if (empty? virheet) v (assoc v nimi virheet))
              skeema)))))))