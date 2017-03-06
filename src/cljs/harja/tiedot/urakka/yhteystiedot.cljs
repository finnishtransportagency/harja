(ns harja.tiedot.urakka.yhteystiedot
  "Tämä nimiavaruus hallinnoi urakan yhteystietoja ja päivystäjiä."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def yhteyshenkilotyypit-kaikille-urakoille
  (into [] (sort ["Kunnossapitopäällikkö" "Tieliikennekeskus"])))

(def yhteyshenkilotyypit-oletus
  (into [] (sort (concat yhteyshenkilotyypit-kaikille-urakoille
                         ["Sillanvalvoja" "Kelikeskus"]))))

(def yhteyshenkilotyypit-paallystys
  (into [] (sort (concat yhteyshenkilotyypit-kaikille-urakoille
                         ["Aluevastaava" "Tiemerkintäurakan tilaaja" "Siltainsinööri"
                          "Turvallisuuskoordinaattori"]))))

(def yhteyshenkilotyypit-tiemerkinta
  (into [] (sort (concat yhteyshenkilotyypit-kaikille-urakoille
                         ["Aluevastaava" "Päällystysurakan tilaaja"
                          "Turvallisuuskoordinaattori"]))))

(defn urakkatyypin-mukaiset-yhteyshenkilotyypit [urakkatyyppi]
  (case urakkatyyppi
    :paallystys yhteyshenkilotyypit-paallystys
    :tiemerkinta yhteyshenkilotyypit-tiemerkinta
    yhteyshenkilotyypit-oletus))

(defn tallenna-urakan-yhteyshenkilot
  "Tallentaa urakan yhteyshenkilöt, palauttaa kanavan, josta vastauksen voi lukea."
  [urakka-id yhteyshenkilot poistettavat]
  ;;(log "TALLENNA URAKAN YHTEYSHENKILOT: " (pr-str yhteyshenkilot) " \n JA POISTETAAN: " (pr-str poistettavat))
  (log " YHTEYSHENKILOT: " yhteyshenkilot)
  (k/post! :tallenna-urakan-yhteyshenkilot
           {:urakka-id urakka-id
            :yhteyshenkilot yhteyshenkilot
            :poistettu poistettavat}))

(defn hae-urakan-kayttajat [urakka-id]
  (k/post! :hae-urakan-kayttajat urakka-id nil true))

(defn hae-urakan-vastuuhenkilot [urakka-id]
  (k/post! :hae-urakan-vastuuhenkilot urakka-id))

(defn hae-urakan-paivystajat [urakka-id]
  (k/post! :hae-urakan-paivystajat urakka-id))

(defn hae-urakan-yhteyshenkilot [urakka-id]
  (k/post! :hae-urakan-yhteyshenkilot urakka-id))

(defn tallenna-urakan-paivystajat
  "Tallentaa urakan päivystäjät. Palauttaa kanavan, josta vastauksen voi lukea."
  [urakka-id paivystajat poistettavat]
  (k/post! :tallenna-urakan-paivystajat
           {:urakka-id urakka-id
            :paivystajat paivystajat
            :poistettu poistettavat}))

(defn tallenna-urakan-vastuuhenkilot-roolille [urakka-id rooli vastuuhenkilo varahenkilo]
  (k/post! :tallenna-urakan-vastuuhenkilot-roolille
           {:urakka-id urakka-id
            :rooli rooli
            :vastuuhenkilo vastuuhenkilo
            :varahenkilo varahenkilo}))
