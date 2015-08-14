(ns harja.tiedot.tilannekuva
  (:require [reagent.core :refer [atom]]

            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<!]]
            [harja.atom :refer-macros [reaction<!]])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

;; Mitä haetaan?
(defonce hae-ilmoitukset? (atom true))
(defonce hae-kaluston-gps? (atom true))
(defonce hae-turvallisuuspoikkeamat? (atom true))
(defonce hae-tarkastukset? (atom true))
(defonce hae-havainnot? (atom true))
(defonce hae-paikkaustyot? (atom true))
(defonce hae-paallystystyot? (atom true))
(defonce haettavat-toteumatyypit (atom []))

;; Millä ehdoilla haetaan?
(defonce valittu-aikasuodatin (atom :live))
(defonce livesuodattimen-asetukset (atom {}))
(defonce historiasuodattimen-asetukset (atom {}))
(defonce haettavat-urakat (atom []))
(defonce haettavat-hallintayksikot (atom []))

(defonce nakymassa? (atom false))
(defonce taso-tilannekuva (atom false))

(def haetut-asiat (atom nil))

(def pollaus-id (atom nil))
(def +sekuntti+ 1000)
(def +minuutti+ (* 60 +sekuntti+))
(def +intervalli+ (* 10 +sekuntti+))

(defn oletusalue [asia]
  {:type :circle
   :coordinates (:sijainti asia)
   :color "green"
   :radius 5000
   :stroke {:color "black" :width 10}})

(defmulti kartalla-xf :tyyppi)
(defmethod kartalla-xf :ilmoitus [ilmoitus]
  (assoc ilmoitus
    :type :ilmoitus
    :alue (oletusalue ilmoitus)))

(defmethod kartalla-xf :havainto [havainto]
  (assoc havainto
    :type :havainto
    :alue (oletusalue havainto)))

(defmethod kartalla-xf :tarkastus [tarkastus]
  (assoc tarkastus
    :type :tarkastus
    :alue (oletusalue tarkastus)))

(defmethod kartalla-xf :toteuma [toteuma]
  (assoc toteuma
    :type :toteuma
    :alue (oletusalue toteuma)))

(defmethod kartalla-xf :tyokone [tyokone]
  (assoc tyokone
    :type :tyokone
    :alue (oletusalue tyokone)))

(defmethod kartalla-xf :turvallisuuspoikkeama [tp]
  (assoc tp
    :type :turvallisuuspoikkeama
    :alue (oletusalue tp)))

(defmethod kartalla-xf :paallystystyo [pt]
  (assoc pt
    :type :paallystystyo
    :alue (oletusalue pt)))

(defmethod kartalla-xf :paikkaustyo [pt]
  (assoc pt
    :type :paikkaustyo
    :alue (oletusalue pt)))

(def tilannekuvan-asiat-kartalla
  (reaction
    @haetut-asiat
    (when @taso-tilannekuva
     (into [] (map kartalla-xf) @haetut-asiat))))

(defn hae-asiat []
  (go
    (let [yhdista (fn [& tulokset]
                    (concat (remove k/virhe? tulokset)))
          tulos (yhdista
                  (when @hae-ilmoitukset? (<! (k/post! :hae-ilmoitukset {})))
                  (when @hae-kaluston-gps? (<! (k/post! :hae-tyokoneseurantatiedot {})))
                  (when @hae-turvallisuuspoikkeamat? (<! (k/post! :hae-turvallisuuspoikkeamat {})))
                  (when @hae-tarkastukset? (<! (k/post! :hae-urakan-tarkastukset {})))
                  (when @hae-havainnot? (<! (k/post! :hae-urakan-havainnot {})))
                  (when @hae-paikkaustyot? (<! (k/post! :hae-paikkaustyot {})))
                  (when @hae-paallystystyot? (<! (k/post! :hae-paallystystyot {})))
                  (when-not (empty? @haettavat-toteumatyypit) (<! (k/post! :hae-kaikki-toteumat {}))))]
      (reset! haetut-asiat tulos))))

(defn lopeta-pollaus
  []
  (when @pollaus-id
    (js/clearInterval @pollaus-id)
    (reset! pollaus-id nil)))

(defn aloita-pollaus
  []
  (when @pollaus-id (lopeta-pollaus))
  (reset! pollaus-id (js/setInterval hae-asiat +intervalli+)))

#_(run! (if @nakymassa? (aloita-pollaus) (lopeta-pollaus)))
