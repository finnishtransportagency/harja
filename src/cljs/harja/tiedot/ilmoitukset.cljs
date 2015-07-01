(ns harja.tiedot.ilmoitukset
  (:require [reagent.core :refer [atom]]

            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.urakka :as u]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<!]])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

;; FILTTERIT
(defonce ilmoitusnakymassa? (atom false))
(defonce valittu-ilmoitus (atom nil))

(defonce valittu-hallintayksikko (reaction @nav/valittu-hallintayksikko))
(defonce valittu-urakka (reaction @nav/valittu-urakka))
(defonce valitut-tilat (atom {:suljetut true :avoimet true}))
(defonce valittu-aikavali (reaction [(first @u/valittu-hoitokausi) (second @u/valittu-hoitokausi)]))
(defonce valitut-ilmoitusten-tyypit (atom {:kysely true :toimenpidepyynto true :tiedoitus true}))
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

(defn kasaa-parametrit []
  (let [valitut (vec (keep #(when (val %) (key %)) @valitut-ilmoitusten-tyypit))    ;; Jos ei yhtäkään valittuna,
        tyypit (if (empty? valitut) (keep key @valitut-ilmoitusten-tyypit) valitut) ;; lähetetään kaikki tyypit.
        ret {:hallintayksikko (:id @valittu-hallintayksikko)
             :urakka (:id @valittu-urakka)
             :tilat @valitut-tilat
             :tyypit tyypit
             :aikavali @valittu-aikavali
             :hakuehto @hakuehto}]
    (log (pr-str ret))
    ret))

(defn hae-ilmoitukset
  []
  (go
    (log "Post! :hae-ilmoitukset")
    (let [tulos (<! (k/post! :hae-ilmoitukset (kasaa-parametrit)))]

      (log ":hae-ilmoitukset")
      (log (pr-str tulos))


      (when-not (k/virhe? tulos)
        (reset! haetut-ilmoitukset tulos))
      (reset! filttereita-vaihdettu? false)

      tulos)))

(defn lopeta-pollaus
  []
  (log "Lopetetaan pollaus!")
  (when @pollaus-id
    (js/clearInterval @pollaus-id)
    (reset! pollaus-id nil)))

(reaction (when @filttereita-vaihdettu?) (lopeta-pollaus))

(defn aloita-pollaus
  []
  (log "Aloitetaan pollaus!")
  (when @pollaus-id (lopeta-pollaus))
  #_(reset! pollaus-id (js/setInterval hae-ilmoitukset +intervalli+)))