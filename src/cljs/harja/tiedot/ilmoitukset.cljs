(ns harja.tiedot.ilmoitukset
  (:require [reagent.core :refer [atom]]

            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.urakka :as u])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

;; FILTTERIT
(defonce ilmoitusnakymassa? (atom false))
(defonce valittu-ilmoitus (atom nil))
(defonce valittu-hallintayksikko (reaction @nav/valittu-hallintayksikko))
(defonce valittu-urakka (reaction @nav/valittu-urakka))
(defonce valitut-tilat (atom {:vastatut true :avoimet true}))
(defonce valittu-aikavali (reaction [(first @u/valittu-hoitokausi) (second @u/valittu-hoitokausi)]))
(defonce valitut-ilmoitusten-tyypit (atom {:kysely true :toimenpidepyynto true :ilmoitus true}))
(defonce hakuehto (atom nil))

;; POLLAUS
(def pollaus-id (atom nil))
(def +sekuntti+ 1000)
(def +minuutti+ (* 60 +sekuntti+))
(def +intervalli+ +minuutti+)

(defonce filttereita-vaihdettu? (reaction
                                  @valittu-hallintayksikko
                                  @valittu-urakka
                                  @valitut-tilat
                                  @valittu-aikavali
                                  @valitut-ilmoitusten-tyypit
                                  @hakuehto
                                  true))

(defonce haetut-ilmoitukset (atom [{:ilmoitettu "Tänään" :sijainti "Täällä" :tyyppi "Se" :vastattu? "Ei"}]))

(defn hae-ilmoitukset
  []
  (go
    (let [tulos (k/post! :hae-ilmoitukset
                         {:hallintayksikko @valittu-hallintayksikko
                          :urakka @valittu-urakka
                          :tilat (vec (keep #(when (val %) (key %)) @valitut-tilat))
                          :tyypit (vec (keep #(when (val %) (key %)) @valitut-ilmoitusten-tyypit))
                          :aikavali @valittu-aikavali})]


      (reset! haetut-ilmoitukset tulos)
      (reset! filttereita-vaihdettu? false)

      tulos)))

(defn lopeta-pollaus
  []
  (when @pollaus-id
    (js/clearInterval @pollaus-id)
    (reset! pollaus-id nil)))

(reaction (when @filttereita-vaihdettu?) (lopeta-pollaus))

(defn aloita-pollaus
  []
  (when @pollaus-id (lopeta-pollaus))
  (reset! pollaus-id (js/setInterval hae-ilmoitukset +intervalli+)))

(defn hae-ilmoitukset-ja-aloita-pollaus []
  (hae-ilmoitukset)
  (aloita-pollaus))