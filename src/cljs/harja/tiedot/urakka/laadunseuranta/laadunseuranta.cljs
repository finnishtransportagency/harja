(ns harja.tiedot.urakka.laadunseuranta.laadunseuranta
  "Tämä nimiavaruus hallinnoi laadunseurantaa sekä havaintoja ja tarkastuksia"
  (:require [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce laadunseurannassa? (atom false))                   ; jos true, laadunseurantaosio nyt käytössä
(defonce valittu-valilehti (atom :tarkastukset))

(defonce tienumero (atom nil))                              ;; tienumero, tai kaikki
(defonce tarkastustyyppi (atom nil))                        ;; nil = kaikki, :tiesto, :talvihoito, :soratie

; FIXME Pitäisi siirtää tarkastuksiin, mutta namespace-requiretus sekoaa
(def +tarkastustyyppi->nimi+ {:tiesto "Tiestötarkastus"
                              :talvihoito "Talvihoitotarkastus"
                              :soratie "Soratien tarkastus"
                              :laatu "Laaduntarkastus"
                              :pistokoe "Pistokoe"})