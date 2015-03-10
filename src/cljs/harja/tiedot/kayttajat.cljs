(ns harja.tiedot.kayttajat
  "Käyttäjien haku ja tallennus"
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.tapahtumat :as t]

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

(defn muunna-kayttajan-tiedot
  [tiedot]
  (assoc tiedot :urakka-roolit
         (mapv #(pvm/muunna-aika % :luotu) (:urakka-roolit tiedot))))

(defn hae-kayttajan-tiedot
  "Hakee käyttäjän tarkemmat tiedot muokkausnäkymää varten."
  [kayttaja-id]
  (let [ch (chan)]
    (go
      (let [tiedot (<! (k/post! :hae-kayttajan-tiedot kayttaja-id))]
        (>! ch (muunna-kayttajan-tiedot tiedot))))
    ch))

(defn tallenna-kayttajan-tiedot
  "Tallentaa käyttäjän roolitiedot muokkausnäkymästä. Palauttaa uudet tiedot."
  [kayttaja-id tiedot]
  (log "TALLENNA KAYTTAJA " kayttaja-id "\nTIEDOILLA: " (pr-str tiedot))
  (let [ch (chan)]
    (go
      (let [lahetettava {:kayttaja-id kayttaja-id
                         :tiedot (-> tiedot
                                     (update-in [:urakka-roolit]
                                                (fn [roolit]
                                                  (mapv #(pvm/muunna-aika-js % :luotu) roolit))))}
                         
            tiedot (<! (k/post! :tallenna-kayttajan-tiedot lahetettava))]
        (>! ch (muunna-kayttajan-tiedot tiedot))))
    ch))

