(ns harja.tiedot.urakka.yhteystiedot
  "Tämä nimiavaruus hallinnoi urakan yhteystietoja ja päivystäjiä."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))




(defn tallenna-urakan-yhteyshenkilot
  "Tallentaa urakan yhteyshenkilöt, palauttaa kanavan, josta vastauksen voi lukea."
  [urakka-id yhteyshenkilot poistettavat]
  (log "TALLENNA URAKAN YHTEYSHENKILOT: " (pr-str yhteyshenkilot) " \n JA POISTETAAN: " (pr-str poistettavat))
  (k/post! :tallenna-urakan-yhteyshenkilot
           {:urakka-id urakka-id
            :yhteyshenkilot yhteyshenkilot
            :poistettu poistettavat}))

(defn hae-yhteyshenkilotyypit []
  (comment
    (k/post! :hae-yhteyshenkilotyypit nil))
  (let [ch (chan)]
    (go (>! ch ["Aluevastaava" "Työmaapäällikkö" "Kunnossapitopäällikkö"
                "Sillanvalvoja" "Kelikeskus" "Tieliikennekeskus"]))
    ch))


   
(defn hae-urakan-paivystajat [urakka-id]
  (k/post! :hae-urakan-paivystajat urakka-id
           (map #(pvm/muunna-aika % :alku :loppu))))


(defn hae-urakan-yhteyshenkilot [urakka-id]
  (k/post! :hae-urakan-yhteyshenkilot urakka-id))


(defn tallenna-urakan-paivystajat
  "Tallentaa urakan päivystäjät. Palauttaa kanavan, josta vastauksen voi lukea."
  [urakka-id paivystajat poistettavat]
  (k/post! :tallenna-urakan-paivystajat
           {:urakka-id urakka-id
            :paivystajat paivystajat
            :poistettu poistettavat}
           (map #(pvm/muunna-aika % :alku :loppu))))


