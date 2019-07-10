(ns harja.tiedot.urakka.suunnittelu.budjettisuunnittelu
  "Tämä nimiavaruus hallinnoi urakan budjetointia. Toiminnallisuuksia käytetään urakkatyypissä teiden-hoito (MHU)."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.suunnittelu.kokonaishintaiset-tyot :as koktyot]
            [harja.tiedot.urakka :as u])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-urakan-budjetoidut-tyot [{:keys [tyyppi id] :as ur}]
  (go (let [res (<! (k/post! :budjetoidut-tyot id))
            hoitokaudet (u/hoito-tai-sopimuskaudet ur)]
        (keep #(koktyot/aseta-hoitokausi hoitokaudet %) res))))


(defn tallenna-budjetoidut-tyot
  "Tallentaa teiden hoidon budjetoidut työt: kiinteähintaiset työt, kustannusarvioidut työt, yksikköhintaiset työt"
  [urakka-id sopimusnumero tyot]
  (k/post! :tallenna-budjetoidut-tyot
           {:urakka-id        urakka-id
            :sopimusnumero (first sopimusnumero)
            :tyot          (into [] tyot)}))
