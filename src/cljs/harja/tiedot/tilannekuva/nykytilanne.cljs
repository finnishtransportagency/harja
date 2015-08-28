(ns harja.tiedot.tilannekuva.nykytilanne
  (:require [reagent.core :refer [atom]]

            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<!]]
            [harja.atom :refer-macros [reaction<!] :refer [paivita-periodisesti]]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [cljs-time.core :as t])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

;; Mitä haetaan?
(defonce hae-toimenpidepyynnot? (atom true))
(defonce hae-kyselyt? (atom true))
(defonce hae-tiedoitukset? (atom true))
(defonce hae-havainnot? (atom true))
(defonce hae-onnettomuudet? (atom true))
(defonce hae-tyokoneet? (atom true))

;; Millä ehdoilla haetaan?
(defonce livesuodattimen-asetukset (atom "0-4h"))

(defonce nakymassa? (atom false))
(defonce taso-nykytilanne (atom false))
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

(defn suunta-radiaaneina [tyokone]
  (let [sijainti (:sijainti tyokone)
        edellinensijainti (or (:edellinensijainti tyokone) sijainti)
        lat1 (sijainti 0)
        lon1 (sijainti 1)
        lat2 (edellinensijainti 0)
        lon2 (edellinensijainti 1)]
    (mod (Math/atan2 (* (Math/sin (- lon2 lon1))
                        (Math/cos lat2))
                     (- (* (Math/cos lat1) (Math/sin lat2))
                        (* (Math/sin lat1) (Math/cos lat2) (Math/cos (- lon2 lon1)))))
         (* 2 Math/PI))))

(defmethod kartalla-xf :tyokone [tyokone]
  (assoc tyokone
    :type :tyokone
    :alue {:type :icon
           :coordinates (:sijainti tyokone)
           :direction (- (suunta-radiaaneina tyokone))
           :img "images/tyokone.png"}))

(defmethod kartalla-xf :default [_])

(def nykytilanteen-asiat-kartalla
  (reaction
    @haetut-asiat
    (when @taso-nykytilanne
      (into [] (map kartalla-xf) @haetut-asiat))))

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
                    (apply (comp vec concat) (remove k/virhe? tulokset)))
          tulos (yhdista
                  (when @hae-tyokoneet? (<! (k/post! :hae-tyokoneseurantatiedot (kasaa-parametrit))))
                  #_(when @hae-toimenpidepyynnot? (<! (k/post! :hae-toimenpidepyynnot (kasaa-parametrit))))
                  #_(when @hae-tiedoitukset? (<! (k/post! :hae-tiedoitukset (kasaa-parametrit))))
                  #_(when @hae-kyselyt? (<! (k/post! :hae-kyselyt (kasaa-parametrit))))
                  #_(when @hae-kaluston-gps? (<! (k/post! :hae-tyokoneseurantatiedot (kasaa-parametrit))))
                  #_(when @hae-onnettomuudet? (<! (k/post! :hae-urakan-onnettomuudet (kasaa-parametrit))))
                  #_(when @hae-havainnot? (<! (k/post! :hae-urakan-havainnot (kasaa-parametrit)))))]
      (reset! haetut-asiat tulos)
      (tapahtumat/julkaise! {:aihe :uusi-tyokonedata
                             :tyokoneet (first tulos)}))))

(def +sekuntti+ 1000)
(def +minuutti+ (* 60 +sekuntti+))

(def +intervalli+ (* 5 +sekuntti+))
(def +bufferi+ (* 1 +sekuntti+))

(def asioiden-haku (reaction<!
                     [_ @hae-toimenpidepyynnot?
                      _ @hae-kyselyt?
                      _ @hae-tiedoitukset?
                      _ @hae-tyokoneet?
                      _ @hae-havainnot?
                      _ @hae-onnettomuudet?
                      _ @livesuodattimen-asetukset]
                     {:odota +bufferi+}
                     (when @nakymassa? (hae-asiat))))

(def lopeta-asioiden-haku (paivita-periodisesti asioiden-haku +intervalli+))
