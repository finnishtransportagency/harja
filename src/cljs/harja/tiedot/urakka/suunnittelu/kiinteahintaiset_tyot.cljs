(ns harja.tiedot.urakka.suunnittelu.kiinteahintaiset-tyot
  "Tämä nimiavaruus hallinnoi urakan kiinteähintaisia töitä. Töitä tehdään urakkatyypissä teiden-hoito.
  Kiinteähintaisista töistä tehdään ennen hoitokauden alkua budjetti, joka lasketaan mukaan Sampoon lähetettävään kokonaishintaiseen kustannussuunnitelmaan.
  Suunnitelma kasvattaa toimenpidekohtaista kokonaishintaista maksuerää kuukausittain."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.suunnittelu.kokonaishintaiset-tyot :as koktyot]
            [harja.tiedot.urakka :as u])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-urakan-kiinteahintaiset-tyot [{:keys [tyyppi id] :as ur}]
  (go (let [res (<! (k/post! :kiinteahintaiset-tyot id))
            hoitokaudet (u/hoito-tai-sopimuskaudet ur)]
        (keep #(koktyot/aseta-hoitokausi hoitokaudet %) res))))


(defn tallenna-kiinteahintaiset-tyot
  "Tallentaa urakan kiinteahintaiset työt, palauttaa kanavan, josta vastauksen voi lukea."
  [urakka-id sopimusnumero tyot]
  (k/post! :tallenna-kiinteahintaiset-tyot
           {:urakka-id        urakka-id
            :sopimusnumero (first sopimusnumero)
            :tyot          (into [] tyot)}))
