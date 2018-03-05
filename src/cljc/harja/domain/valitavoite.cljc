(ns harja.domain.valitavoite
  (:require [harja.pvm :as pvm]
            [cljs-time.core :as t]
            [harja.fmt :as fmt]))

(defn valmiustila-fmt [{:keys [valmispvm takaraja] :as valitavoite} tila]
  (case tila
        :uusi "Uusi"
        :valmistunut "Valmistunut"
        :ei-valmis
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

(defn valmiustila [{:keys [valmispvm takaraja]}]
  (cond (nil? takaraja)
        :uusi

        (and takaraja valmispvm)
        :valmistunut

        (and takaraja (nil? valmispvm) (pvm/sama-tai-ennen? (pvm/nyt) takaraja))
        :ei-valmis

        (and takaraja (nil? valmispvm) (t/after? (pvm/nyt) takaraja))
        :myohassa))

(defn valmiustilan-kuvaus [valitavoite]
  (valmiustila-fmt valitavoite (valmiustila valitavoite)))