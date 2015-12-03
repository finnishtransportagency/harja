(ns harja.tiedot.urakka.laadunseuranta
  "Tämä nimiavaruus hallinnoi laadunseurantaa sekä havaintoja ja tarkastuksia"
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce laadunseurannassa? (atom false)) ; jos true, laadunseurantaosio nyt käytössä
(defonce valittu-valilehti (atom :tarkastukset))