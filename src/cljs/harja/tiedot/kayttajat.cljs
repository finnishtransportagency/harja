(ns harja.tiedot.kayttajat
  "Käyttäjien haku ja tallennus"
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.tapahtumat :as t]

            [harja.ui.protokollat :refer [Haku hae]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [chan <! >!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-kayttajat
  "Hakee käyttäjiä hakuehdolla ja start/limit rajauksilla. Palauttaa vektorin käyttäjiä, jonka
metadata on mäppi, jossa seuraavat :lkm avaimella tieto siitä kuinka monta käyttäjää kaikenkaikkiaan on haettavissa.
Jos halutaan hakea kaikki käyttäjät, jotka käyttäjällä on oikeus nähdä, voi hakuehto olla nil."
  [hakuehto alku maara]
  (k/post! :hae-kayttajat [hakuehto alku maara]))


(defn hae-kayttajan-tiedot
  "Hakee käyttäjän tarkemmat tiedot muokkausnäkymää varten."
  [kayttaja-id]
  (let [ch (chan)]
    (go
      (let [tiedot (<! (k/post! :hae-kayttajan-tiedot kayttaja-id))]
        (>! ch tiedot)))
    ch))

(defn hae-organisaation-urakat
  "Hakee kaikki organisaation urakat."
  [org-id]
  (k/post! :hae-organisaation-urakat org-id))


(defn tallenna-kayttajan-tiedot!
  "Tallentaa käyttäjän roolitiedot muokkausnäkymästä. Palauttaa uudet tiedot."
  [kayttaja organisaatio tiedot]
  (log "TALLENNA KAYTTAJA " kayttaja " \nTIEDOILLA: " (pr-str tiedot))
  (let [ch (chan)]
    (go
      (let [lahetettava {:kayttaja-id (:id kayttaja)
                         :organisaatio-id (:id organisaatio)
                         :kayttajatunnus (:kayttajatunnus kayttaja)
                         :tiedot tiedot}
                         
            tiedot (<! (k/post! :tallenna-kayttajan-tiedot lahetettava))]
        (>! ch tiedot)))
    ch))

(defn poista-kayttaja! [id]
  (k/post! :poista-kayttaja id))

(defn hae-fim-kayttaja [tunnus]
  (k/post! :hae-fim-kayttaja tunnus))

(def organisaatio-haku
  "Hakulähde organisaatioiden hakemiseksi."
  (reify Haku
    (hae [_ teksti]
      (k/post! :hae-organisaatioita teksti))))
