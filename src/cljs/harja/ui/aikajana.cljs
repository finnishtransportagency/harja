(ns harja.ui.aikajana
  "Aikajananäkymä, jossa voi useita eri asioita näyttää aikajanalla.
  Vähän kuten paljon käytetty gantt kaavio."
  (:require [clojure.spec :as s]
            [reagent.core :as r]
            [harja.ui.dom :as dom]
            [harja.pvm :as pvm]
            [cljs-time.core :as t]
            [harja.ui.debug :as debug])
  (:require-macros [harja.tyokalut.spec :refer [defn+]]))

(s/def ::rivi (s/keys :req [::otsikko ::ajat]))
(s/def ::rivit (s/every ::rivi))
(s/def ::ajat (s/every ::aika))

(s/def ::aika (s/keys :req [::teksti ::alku ::loppu
                            (or ::vari ::reuna)]))

(s/def ::teksti string?)
(s/def ::vari string?)
(s/def ::reuna string?)

(s/def ::alku t/date?)
(s/def ::loppu t/date?)

(s/def ::min-max (s/cat :min t/date? :max t/date?))

(s/def ::paivat (s/every t/date?))

(defn+ min-ja-max-aika [ajat ::ajat pad int?] ::min-max
  (loop [min nil
         max nil
         [{::keys [alku loppu]} & ajat] ajat]
    (if-not alku
      [(and min (t/minus min (t/days pad)))
       (and max (t/plus max (t/days pad)))]
      (recur (cond
               (nil? min) alku
               (pvm/ennen? alku min) alku
               (pvm/ennen? loppu min) loppu
               :default min)
             (cond
               (nil? max) loppu
               (pvm/jalkeen? loppu max) loppu
               (pvm/jalkeen? alku max) alku
               :else max)
             ajat))))

(defn+ kuukaudet
  "Ottaa sekvenssin järjestyksessä olevia päiviä ja palauttaa ne kuukausiin jaettuna.
  Palauttaa sekvenssin kuukausia {:alku alkupäivä :loppu loppupäivä :otsikko kk-formatoituna}."
  [paivat ::paivat] any?
  (reduce
   (fn [kuukaudet paiva]
     (let [viime-kk (last kuukaudet)]
       (if (or (nil? viime-kk)
               (not (pvm/sama-kuukausi? (:alku viime-kk) paiva)))
         (conj kuukaudet {:alku paiva
                          :otsikko (pvm/koko-kuukausi-ja-vuosi paiva)
                          :loppu paiva})
         (update kuukaudet (dec (count kuukaudet))
                 assoc :loppu paiva))))
   []
   paivat))

(defn+ aikajana
  "Aikajanakomponentti, joka näyttää gantt kaavion tyylisen aikajanan.
  Komponentti sovittaa alku/loppuajat automaattisesti kaikkien aikojen perusteella ja lisää
  alkuun ja loppuun 14 päivää. Komponentti mukautuu selaimen leveyteen ja sen korkeus määräytyy
  rivimäärän perusteella."
  [rivit ::rivit] vector?
  (r/with-let [tooltip (r/atom nil)]
    (let [rivin-korkeus 20
          leveys (* 0.95 @dom/leveys)
          alku-x 150
          alku-y 50
          korkeus (+ alku-y (* (count rivit) rivin-korkeus))
          kaikki-ajat (mapcat ::ajat rivit)
          alkuajat (sort-by ::alku pvm/ennen? kaikki-ajat)
          loppuajat (sort-by ::loppu pvm/jalkeen? kaikki-ajat)
          [min-aika max-aika] (min-ja-max-aika kaikki-ajat 14)
          text-y-offset 8
          bar-y-offset 3
          bar-height (- rivin-korkeus 6)]
      (when (and min-aika max-aika)
        (let [paivat (pvm/paivat-valissa min-aika max-aika)
              paivia (count paivat)
              paivan-leveys (/ (- leveys alku-x) paivia)
              rivin-y #(+ alku-y (* rivin-korkeus %))
              paiva-x #(+ alku-x (* (- leveys alku-x) (/ (pvm/paivia-valissa % min-aika) paivia)))
              kuukaudet (kuukaudet paivat)]
          [:div
           [debug/debug rivit]
           [:svg {:width leveys :height korkeus
                  :viewBox (str "0 0 " leveys " " korkeus)}


            [:g.aikajana-paivaviivat
             (for [p paivat
                   :let [x (paiva-x p)]]
               ^{:key p}
               [:line {:x1 x :y1 (- alku-y 5)
                       :x2 x :y2 korkeus
                       :style {:stroke "lightGray"}}])]

            (map-indexed
             (fn [i {::keys [ajat] :as rivi}]
               (let [y (rivin-y i)]
                 ^{:key i}
                 [:g
                  [:rect {:x (inc alku-x) :y (- y bar-y-offset)
                          :width (- leveys alku-x)
                          :height bar-height
                          :fill (if (even? i) "#f0f0f0" "#d0d0d0")}]
                  (map-indexed
                   (fn [j {::keys [alku loppu vari reuna teksti]}]
                     (let [x (inc (paiva-x alku))
                           width (- (+ paivan-leveys (- (paiva-x loppu) x)) 2)]
                       ^{:key j}
                       [:rect {:x x :y y
                               :width width
                               :height 10
                               :style {:fill (or vari "white")
                                       ;; Jos väriä ei ole, piirretään valkoinen mutta opacity 0
                                       ;; (täysin läpinäkyvä), jotta hover kuitenkin toimii
                                       :fill-opacity (if vari 1.0 0.0)
                                       :stroke reuna}
                               :rx 3 :ry 3
                               :on-mouse-over #(reset! tooltip {:x (+ x (/ width 2))
                                                                :y (+ y 30)
                                                                :text teksti})
                               :on-mouse-out #(reset! tooltip nil)
                               }]))
                   ajat)
                  [:text {:x 0 :y (+ text-y-offset y)
                          :font-size 10}
                   (::otsikko rivi)]]))
             rivit)

            ;; Tehdään eri kuukausille väliotsikot
            (for [{:keys [alku loppu otsikko]} kuukaudet
                  :let [x (paiva-x alku)]]
              ^{:key otsikko}
              [:g
               [:text {:x (+ 5 x) :y 10} otsikko]
               [:line {:x1 x :y1 0
                       :x2 x :y2 korkeus
                       :style {:stroke "gray"}}]])

            ;; tooltip, jos on
            (when-let [tooltip @tooltip]
              (let [{:keys [x y text]} tooltip]
                [:g
                 [:rect {:x (- x 110) :y (- y 14) :width 220 :height 26
                         :rx 10 :ry 10
                         :style {:fill "black"}}]
                 [:text {:x x :y (+ y 4)
                         :font-size 10
                         :style {:fill "white"}
                         :text-anchor "middle"}
                  text]]))
            ]])))))
