(ns harja.domain.valitavoite
  (:require [harja.pvm :as pvm]
    #?(:cljs [cljs-time.core :as t])
    #?(:clj
            [clj-time.core :as t])
            [harja.fmt :as fmt]))

(defn valmiustila-fmt [{:keys [valmispvm takaraja] :as valitavoite} tila]
  (case tila
    :uusi "Uusi"
    :valmis "Valmistunut"
    :kesken
    (let [paivia-valissa (pvm/paivia-valissa (pvm/nyt) takaraja)]
      (str "Ei valmis" (when (pos? paivia-valissa)
                         (str " (" (fmt/kuvaile-paivien-maara paivia-valissa
                                                              {:lyhenna-yksikot? true})
                              " jäljellä)"))))

    :myohassa
    (let [paivia-valissa (pvm/paivia-valissa takaraja (pvm/nyt))]
      (str "Myöhässä" (when (pos? paivia-valissa)
                        (str " (" (fmt/kuvaile-paivien-maara paivia-valissa
                                                             {:lyhenna-yksikot? true})
                             ")"))))))

(def valmiustila-fmt-yksinkertainen
  {:uusi "Uusi"
   :valmis "Valmistunut"
   :kesken "Ei valmis"
   :myohassa "Myöhässä"})

(defn valmiustila [{:keys [valmispvm takaraja] :as valitavoite}]
  (cond (nil? takaraja)
        :uusi

        (and takaraja valmispvm)
        :valmis

        (and takaraja (nil? valmispvm) (pvm/sama-tai-ennen? (pvm/nyt) takaraja))
        :kesken

        (and takaraja (nil? valmispvm) (t/after? (pvm/nyt) takaraja))
        :myohassa))

(defn valmiustilan-kuvaus [valitavoite]
  (valmiustila-fmt valitavoite (valmiustila valitavoite)))

(defn valmiustilan-kuvaus-yksinkertainen [valitavoite]
  (valmiustila-fmt-yksinkertainen (valmiustila valitavoite)))
