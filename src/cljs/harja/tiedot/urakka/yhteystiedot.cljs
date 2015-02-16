(ns harja.tiedot.urakka.yhteystiedot
  "Tämä nimiavaruus hallinnoi urakan yhteystietoja ja päivystäjiä."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-yhteyshenkilotyypit []
  (k/post! :hae-yhteyshenkilotyypit nil))

   
(defn hae-urakan-paivystajat [urakka-id]
  (let [paivystajat (atom [])]
    (go
      (reset! paivystajat
              (<! (k/post! :hae-urakan-paivystajat urakka-id))))
    paivystajat))

(defn hae-urakan-yhteyshenkilot [urakka-id]
  (k/post! :hae-urakan-yhteyshenkilot urakka-id))


(defn tallenna-urakan-paivystajat [urakka-id paivystajat]
  (k/post! :tallenna-urakan-paivystajat [urakka-id paivystajat]))
