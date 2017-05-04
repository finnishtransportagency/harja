(ns harja.tiedot.hallintayksikot
  "Hallinnoi hallintayksiköiden tietoja"
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<! >! chan close!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t])

  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Hallintayksiköt atomia käytetään ympäri Harjaa lukemaan nykyiset hallintayksiköt.
;; Vesiväyläliikennemuodon lisäämisen vuoksi hallintayksiköiden sisältö voi nyt vaihtua.
;; Hallintayksiköt-atomi tulee jatkossakin olemaan se paikka, mistä nykykontekstissa kiinnostavien
;; hallintayksiköiden tiedot pitää lukea, mutta jos väylämuotoa vaihdetaan, pitää muistaa kutsua
;; aseta-hallintayksikot-vaylamuodolle! funktiota.

(def
  ^{:doc "Sisältää yhden väylämuodon hallintayksiköt"}
  hallintayksikot (atom nil))

(def
  ^{:private true
    :doc "Kaikki palvelimelta haetut hallintayksiköt, ryhmiteltynä liikennemuodon mukaan"}
  haetut-hallintayksikot (atom nil))

(defn- vaylamuoto-str->keyword [vaylamuoto]
  ({"V" :vesi
    "T" :tie} vaylamuoto))

(defn- hae-hallintayksikot!
  ([]
   (go (reset! haetut-hallintayksikot
               (group-by :liikennemuoto
                         (into []
                               (comp (map #(assoc % :type :hy))
                                     (map #(update % :liikennemuoto vaylamuoto-str->keyword)))
                               (<! (k/post! :hallintayksikot {:liikennemuoto nil}))))))))

(defn aseta-hallintayksikot-vaylamuodolle!
  "Hakee haetut-hallintayksikot atomista väylämuodon hallintayksiköt, ja asettaa
  sen hallintayksikot-atomin sisällöksi."
  [vaylamuoto]
  (go
    (reset! hallintayksikot ((or @haetut-hallintayksikot (<! (hae-hallintayksikot!))) vaylamuoto))
    (harja.loki/log "MURU: aseta-hallintayksikot-vaylamuodolle!")))

(defn- hallintayksikon-vaylamuoto* [haetut-hallintayksikot id]
  (go
    (let [hyt (or @haetut-hallintayksikot (<! (hae-hallintayksikot!)))]
      (first (keep (fn [[muoto hyt]] (when (some #(= id (:id %)) hyt) muoto)) hyt)))))
(def hallintayksikon-vaylamuoto (partial hallintayksikon-vaylamuoto* haetut-hallintayksikot))

(defn elynumero-ja-nimi [{nro :elynumero nimi :nimi}]
  (if-not nro
    nimi
    (str nro " " nimi)))

(hae-hallintayksikot!)