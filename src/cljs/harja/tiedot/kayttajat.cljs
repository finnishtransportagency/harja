(ns harja.tiedot.kayttajat
  "Käyttäjien haku ja tallennus"
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.tapahtumat :as t]

            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-kayttajat
  "Hakee käyttäjiä hakuehdolla ja start/limit rajauksilla. Palauttaa vektorin käyttäjiä, jonka
metadata on mäppi, jossa seuraavat :lkm avaimella tieto siitä kuinka monta käyttäjää kaikenkaikkiaan on haettavissa.
Jos halutaan hakea kaikki käyttäjät, jotka käyttäjällä on oikeus nähdä, voi hakuehto olla nil."
  [hakuehto alku maara]
  (k/post! :hae-kayttajat [hakuehto alku maara]))

