(ns harja.tiedot.hallintayksikot
  "Hallinnoi hallintayksiköiden tietoja"
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<! >! chan close! promise-chan put!]]
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
  vaylamuodon-hallintayksikot (atom nil))

(def
  ^{:private false
    :doc "Kaikki palvelimelta haetut hallintayksiköt, ryhmiteltynä liikennemuodon mukaan"}
  haetut-hallintayksikot (atom nil))

;; Promise-channel, joka on asetettu kun haku on käynnissä. Nil kun ei ole käynnissä.
;; Estää useampien samanaikaisten HTTP-kutsujen käynnistymisen (race condition).
(def ^:private haku-kaynnissa (atom nil))

(defn nollaa-tila!
  "Nollaa hallintayksiköiden tilan. Käytetään testeissä."
  []
  (reset! haku-kaynnissa nil)
  (reset! haetut-hallintayksikot nil)
  (reset! vaylamuodon-hallintayksikot nil))

(defn- vaylamuoto-str->keyword [vaylamuoto]
  ({"V" :vesi
    "T" :tie} vaylamuoto))

(defn hae-hallintayksikot!
  "Hakee hallintayksiköt palvelimelta. Jos haku on jo käynnissä, odottaa sen valmistumista
  sen sijaan että käynnistäisi uuden HTTP-kutsun. Palauttaa kanavan, josta saa ryhmitellyn mapin."
  ([]
   (if-let [kaynnissa @haku-kaynnissa]
     ;; Haku jo käynnissä — palautetaan sama promise-channel
     kaynnissa
     ;; Ei hakua käynnissä — käynnistetään uusi
     (let [ch (promise-chan)]
       (reset! haku-kaynnissa ch)
       (go
         (let [tulos (group-by :liikennemuoto
                               (into []
                                     (comp (map #(assoc % :type :hy))
                                           (map #(update % :liikennemuoto vaylamuoto-str->keyword)))
                                     (<! (k/post! :elinvoimakeskukset {:liikennemuoto nil}))))]
           (reset! haetut-hallintayksikot tulos)
           (reset! haku-kaynnissa nil)
           (put! ch tulos)))
       ch))))

(defn aseta-hallintayksikot-vaylamuodolle!
  "Hakee haetut-hallintayksikot atomista väylämuodon hallintayksiköt, ja asettaa
  sen hallintayksikot-atomin sisällöksi."
  [vaylamuoto]
  (go
    (let [hyt (or @haetut-hallintayksikot (<! (hae-hallintayksikot!)))]
      (reset! vaylamuodon-hallintayksikot (get hyt vaylamuoto)))))

(defn hallintayksikon-vaylamuoto* [haetut-hallintayksikot id]
  (go
    (let [hyt (or @haetut-hallintayksikot (<! (hae-hallintayksikot!)))]
      (first (keep (fn [[muoto hyt]]
                     (when (some #(= id (:id %)) hyt) muoto))
                   hyt)))))
(def hallintayksikon-vaylamuoto (partial hallintayksikon-vaylamuoto* haetut-hallintayksikot))

(defn evknumero-ja-nimi [{nro :evknumero nimi :nimi}]
  (if-not nro
    nimi
    (str nro " " nimi)))

(hae-hallintayksikot!)
