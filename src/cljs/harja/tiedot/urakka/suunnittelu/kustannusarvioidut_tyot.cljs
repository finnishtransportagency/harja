(ns harja.tiedot.urakka.suunnittelu.kustannusarvioidut-tyot
  "Tämä nimiavaruus hallinnoi urakan kustannusarvioituja töitä.
  Kustannusarvioiduista töistä tehdään ennen hoitokauden alkua budjetti, joka lasketaan mukaan Sampoon lähetettävään kustannussuunnitelmaan.
  Arvio ei kuitenkaan kasvata maksuerää. Maksuerään summautuvat euromäärät saadaan urakoitsijan syöttämiltä laskuilta. Ne nivoutuvat tehtävähierarkian kautta kustannusarvioihin.
  Kustannusarvioita ja laskutustietoja vertaillaan raporteilla."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.suunnittelu.kokonaishintaiset-tyot :as koktyot]
            [harja.tiedot.urakka :as u])
  (:require-macros [cljs.core.async.macros :refer [go]]))



(defn hae-urakan-kustannusarvioidut-tyot [{:keys [tyyppi id] :as ur}]
  (go (let [res (<! (k/post! :kustannusarvioidut-tyot id))
            hoitokaudet (u/hoito-tai-sopimuskaudet ur)]
        (keep #(koktyot/aseta-hoitokausi hoitokaudet %) res))))


(defn tallenna-kustannusarvioidut-tyot
  "Tallentaa urakan kustannusarvioidut työt, palauttaa kanavan, josta vastauksen voi lukea."
  [urakka-id sopimusnumero tyot]
  (k/post! :tallenna-kustannusarvioidut-tyot
           {:urakka-id        urakka-id
            :sopimusnumero (first sopimusnumero)
            :tyot          (into [] tyot)}))
