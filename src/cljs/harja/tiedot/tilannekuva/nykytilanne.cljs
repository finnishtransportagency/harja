(ns harja.tiedot.tilannekuva.nykytilanne
  (:require [reagent.core :refer [atom]]

            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<!]]
            [harja.atom :refer-macros [reaction<!]]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [cljs-time.core :as t])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

;; Mitä haetaan?
(defonce hae-toimenpidepyynnot? (atom true))
(defonce hae-kyselyt? (atom true))
(defonce hae-tiedoitukset? (atom true))
(defonce hae-kaluston-gps? (atom true))
(defonce hae-havainnot? (atom true))
(defonce hae-onnettomuudet? (atom true))
(defonce hae-tyokoneet? (atom true))

;; Millä ehdoilla haetaan?
(defonce livesuodattimen-asetukset (atom "0-4h"))

(defonce nakymassa? (atom false))
(defonce taso-nykytilanne (atom false))

(defonce filtterit-muuttui?
         (reaction @hae-toimenpidepyynnot?
                   @hae-kyselyt?
                   @hae-tiedoitukset?
                   @hae-kaluston-gps?
                   @hae-havainnot?
                   @hae-onnettomuudet?
                   @hae-tyokoneet?
                   @nav/valittu-hallintayksikko
                   @nav/valittu-urakka
                   @livesuodattimen-asetukset))

(def haetut-asiat (atom nil))

(defn oletusalue [asia]
  {:type        :circle
   :coordinates (:sijainti asia)
   :color       "green"
   :radius      5000
   :stroke      {:color "black" :width 10}})

(defmulti kartalla-xf :tyyppi)

(defmethod kartalla-xf :ilmoitus [ilmoitus]
  (assoc ilmoitus
    :type :ilmoitus
    :alue (oletusalue ilmoitus)))

(defmethod kartalla-xf :havainto [havainto]
  (assoc havainto
    :type :havainto
    :alue (oletusalue havainto)))

(defmethod kartalla-xf :tyokone [tyokone]
  (assoc tyokone
    :type :tyokone
    :alue {:type :icon
           :coordinates (:sijainti tyokone)
           :img "images/tyokone.png"}))

(def nykytilanteen-asiat-kartalla
  (reaction
    @haetut-asiat
    (when @taso-nykytilanne
      (into [] (map kartalla-xf) (first @haetut-asiat)))))

(defn kasaa-parametrit []
  {:hallintayksikko @nav/valittu-hallintayksikko-id
   :urakka          (:id @nav/valittu-urakka)
   :alue            @nav/kartalla-nakyva-alue
   :alku            (pvm/nyt)
   :loppu           (t/plus (pvm/nyt) (case @livesuodattimen-asetukset
                                        "0-4h" (t/hours 4)
                                        "0-12h" (t/hours 12)
                                        "0-24h" (t/hours 24)))})

(defn hae-asiat []
  (go
    (let [yhdista (fn [& tulokset]
                    (concat (remove k/virhe? tulokset)))
          tulos (yhdista
                 (when @hae-tyokoneet? (<! (k/post! :hae-tyokoneseurantatiedot (kasaa-parametrit))))
                  #_(when @hae-toimenpidepyynnot? (<! (k/post! :hae-toimenpidepyynnot (kasaa-parametrit))))
                  #_(when @hae-tiedoitukset? (<! (k/post! :hae-tiedoitukset (kasaa-parametrit))))
                  #_(when @hae-kyselyt? (<! (k/post! :hae-kyselyt (kasaa-parametrit))))
                  #_(when @hae-kaluston-gps? (<! (k/post! :hae-tyokoneseurantatiedot (kasaa-parametrit))))
                  #_(when @hae-onnettomuudet? (<! (k/post! :hae-urakan-onnettomuudet (kasaa-parametrit))))
                  #_(when @hae-havainnot? (<! (k/post! :hae-urakan-havainnot (kasaa-parametrit)))))]
      (reset! haetut-asiat tulos))))

(def pollaus-id (atom nil))
(def +sekuntti+ 1000)
(def +intervalli+ (* 5 +sekuntti+))

(defn lopeta-pollaus
  []
  (when @pollaus-id
    (log "lopetetaan pollaus")
    (js/clearInterval @pollaus-id)
    (reset! pollaus-id nil)))

(defn aloita-pollaus
  []
  (when (not @pollaus-id)
    (log "aloitetaan pollaus")
    (hae-asiat)
    (reset! pollaus-id (js/setInterval hae-asiat +intervalli+))))

(run! (if @nakymassa? (aloita-pollaus) (lopeta-pollaus)))
(run! (when @filtterit-muuttui?
        (hae-asiat)))
