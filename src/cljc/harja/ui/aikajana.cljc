(ns harja.ui.aikajana
  "Aikajananäkymä, jossa voi useita eri asioita näyttää aikajanalla.
  Vähän kuten paljon käytetty gantt kaavio."
  (:require [clojure.spec.alpha :as s]
    #?(:cljs [reagent.core :as r])
    #?(:cljs [harja.ui.dom :as dom])
            [harja.pvm :as pvm]
    #?(:cljs [cljs-time.core :as t]
       :clj
            [clj-time.core :as t])
    #?(:cljs [harja.ui.debug :as debug])
    #?(:cljs [cljs.core.async :refer [<!]]
       :clj
            [clojure.core.async :refer [<! go]])
    #?(:clj
            [harja.tyokalut.spec :refer [defn+]]))
  #?(:cljs (:require-macros [harja.tyokalut.spec :refer [defn+]]
             [cljs.core.async.macros :refer [go]])))

(s/def ::rivi (s/keys :req [::otsikko ::ajat]))
(s/def ::rivit (s/every ::rivi))
(s/def ::ajat (s/every ::aika))

(s/def ::aika (s/keys :req [::teksti ::alku ::loppu
                            (or ::vari ::reuna)]))

(s/def ::teksti string?)
(s/def ::vari string?)
(s/def ::reuna string?)

(s/def ::date #?(:cljs t/date? :clj inst?))
(s/def ::alku ::date)
(s/def ::loppu ::date)

(s/def ::min-max (s/cat :min ::date :max ::date))

(s/def ::paivat (s/every ::date))

(s/def ::optiot (s/keys :opt [::alku ::loppu]))

(defn+ min-ja-max-aika [ajat ::ajat pad int?] ::min-max
                       (let [ajat (concat (keep ::alku ajat)
                                          (keep ::loppu ajat))
                             ajat-jarjestyksessa (sort pvm/ennen? ajat)
                             aikaisin (first ajat-jarjestyksessa)
                             myohaisin (last ajat-jarjestyksessa)]
                         (when (and aikaisin myohaisin)
                           [(t/minus aikaisin (t/days pad))
                            (t/plus myohaisin (t/days pad))])))

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

(defn- paivat-ja-viikot
  "Näyttää pystyviivan jokaisen päivän kohdalla ja viikon vaihtuessa maanantain
  kohdalle viikonnumero teksti ylös."
  [paiva-x alku-x alku-y korkeus paivat]
  [:g.aikajana-paivaviivat

   ;; Label "VIIKKO" viikonpäivien kohdalle
   [:text {:x (- alku-x 10) :y (- alku-y 10)
           :text-anchor "end"
           :font-size 8}
    "VIIKKO"]

   (loop [acc (list)
          viikko nil
          [p & paivat] paivat]
     (if-not p
       acc
       (let [x (paiva-x p)
             viikko-nyt #?(:cljs (.getWeekNumber p)
                           :clj  (t/week-number-of-year p))
             acc (conj acc
                       ^{:key p}
                       [:line {:x1 x :y1 (- alku-y 5)
                               :x2 x :y2 korkeus
                               :stroke "lightGray"}])]
         (if (and (= 1 #?(:cljs (.getWeekday p)
                          :clj  (t/day-of-week p))) (not= viikko-nyt viikko))
           ;; Maanantai ja eri viikko, lisätään viikko-indikaattori
           (recur (conj acc
                        ^{:key (str viikko-nyt x)}
                        [:text {:x x :y (- alku-y 10)
                                :font-size 8}
                         (str viikko-nyt)])
                  viikko-nyt
                  paivat)
           (recur acc viikko paivat)))))])

(defn- nykyhetki
  "Näyttää pystyviivan nykyisen päivän kohdalla"
  [paiva-x alku-y korkeus]
  (let [nykyhetki (t/now)
        x (paiva-x nykyhetki)]
    [:g.aikajana-nykyhetki-viiva
     [:line {:x1 x :y1 (- alku-y 5)
             :x2 x :y2 korkeus
             :stroke "red"}]]))

(defn- kuukausiotsikot
  "Väliotsikot kuukausille"
  [paiva-x korkeus kuukaudet]
  [:g.aikajana-kuukausiotsikot
   (for [{:keys [alku loppu otsikko]} kuukaudet
         :let [x (paiva-x alku)
               width (dec (- (paiva-x loppu) x))]
         :when (> width 75)]
     ^{:key otsikko}
     [:g
      [:text {:x (+ 5 x) :y 10} otsikko]
      [:line {:x1 x :y1 0
              :x2 x :y2 korkeus
              :stroke "gray"}]])])

(defn- tooltip* [{:keys [x y text] :as tooltip}]
  (when tooltip
    [:g
     [:rect {:x (- x 110) :y (- y 14) :width 220 :height 26
             :rx 10 :ry 10
             :style {:fill "black"}}]
     [:text {:x x :y (+ y 4)
             :font-size 10
             :style {:fill "white"}
             :text-anchor "middle"}
      text]]))

#?(:cljs
   (defn- aikajana-ui-tila [rivit {:keys [muuta!] :as optiot} komponentti]
     (r/with-let [tooltip (r/atom nil)
                  drag (r/atom nil)]
       [:div.aikajana
        [komponentti rivit optiot
         {:tooltip @tooltip
          :show-tooltip! #(reset! tooltip %)
          :hide-tooltip! #(reset! tooltip nil)

          :drag @drag
          :drag-stop! #(when-let [d @drag]
                         (go
                           (<! (muuta! (select-keys d #{::drag ::alku ::loppu})))
                           (reset! drag nil)))
          :drag-start! (fn [e jana avain]
                         (.preventDefault e)
                         (reset! drag
                                 (assoc (select-keys jana #{::alku ::loppu ::drag})
                                   :avain avain)))
          :drag-move! (fn [alku-x hover-y x->paiva]
                        (fn [e]
                          (.preventDefault e)
                          (when @drag
                            (if (zero? (.-buttons e))
                              ;; Ei nappeja pohjassa, lopeta raahaus
                              (reset! drag nil)

                              (let [[svg-x svg-y _ _] (dom/sijainti (dom/elementti-idlla "aikajana"))
                                    cx (.-clientX e)
                                    cy (.-clientY e)
                                    x (- cx svg-x alku-x)
                                    y (- cy svg-y)
                                    paiva (x->paiva x)
                                    tooltip-x (+ alku-x x)
                                    tooltip-y (hover-y y)]
                                (swap! drag
                                       (fn [{avain :avain :as drag}]
                                         (merge
                                           {:x tooltip-x :y tooltip-y}
                                           (if (or (and (= avain ::alku)
                                                        (pvm/ennen? paiva (::loppu drag)))
                                                   (and (= avain ::loppu)
                                                        (pvm/jalkeen? paiva (::alku drag))))
                                             (assoc drag avain (x->paiva x))
                                             drag)))))))))
          :leveys (* 0.95 @dom/leveys)}]])))

#?(:clj
   (defn- jodaksi [rivi]
     (update rivi ::ajat
             (fn [ajat]
               (mapv #(-> %
                          (update ::alku pvm/joda-timeksi)
                          (update ::loppu pvm/joda-timeksi)) ajat)))))

(defn- marker [{:keys [x y reuna korkeus hover-y teksti alku-x
                       paivan-leveys show-tooltip! hide-tooltip! suunta vari]}]
  ;; Varmistetaan, ettei stringeihin mene murtolukuja.
  (let [x (float x)
        y (float y)
        korkeus (float korkeus)
        paivan-leveys (float paivan-leveys)
        puolikas-paiva (float (/ paivan-leveys 2))
        puolikas-korkeus (float (/ korkeus 2))]
    (when (>= x alku-x)
      [:g {:transform (str "translate( " (case suunta
                                           :vasen (- x paivan-leveys puolikas-paiva)
                                           :oikea x)
                           ", " y ")")}
       [:path {:d (case suunta
                    :oikea
                    (str "M " 0 " " 0
                         " L " (+ 0 paivan-leveys) " " 0
                         " L " (+ 0 paivan-leveys puolikas-paiva) " " (+ 0 puolikas-korkeus)
                         " L " (+ 0 paivan-leveys) " " (+ 0 korkeus)
                         " L " 0 " " (+ 0 korkeus)
                         " L 0 0")
                    :vasen
                    (str "M " (+ 0 paivan-leveys puolikas-paiva) " " 0
                         " L " (+ 0 puolikas-paiva) " " 0
                         " L " 0 " " (+ 0 puolikas-korkeus)
                         " L " (+ 0 puolikas-paiva) " " (+ 0 korkeus)
                         " L " (+ 0 paivan-leveys puolikas-paiva) " " (+ 0 korkeus)
                         " L " (+ 0 paivan-leveys puolikas-paiva) " " 0))
               :fill vari
               :fill-opacity (if vari 1.0 0.0)
               :stroke reuna
               :on-mouse-over #(show-tooltip! {:x x
                                               :y (hover-y y)
                                               :text teksti})
               :on-mouse-out hide-tooltip!}]])))

(defn- aikajana* [rivit optiot {:keys [tooltip show-tooltip! hide-tooltip!
                                       drag drag-start! drag-move! drag-stop!
                                       leveys]}]
  (let [rivit #?(:cljs rivit
                 :clj  (map jodaksi rivit))
        rivin-korkeus 20
        alku-x 150
        alku-y 50
        korkeus (+ alku-y (* (count rivit) rivin-korkeus))
        kaikki-ajat (mapcat ::ajat rivit)
        alkuajat (sort-by ::alku pvm/ennen? kaikki-ajat)
        loppuajat (sort-by ::loppu pvm/jalkeen? kaikki-ajat)

        ;; Otetaan alku ja loppu aikajanoista (+/- 14 päivää).
        ;; Ylikirjoitetaan optioista otetuilla arvoilla, jos annettu.
        [min-aika max-aika] (min-ja-max-aika kaikki-ajat 14)
        min-aika (or (::alku optiot) min-aika)
        max-aika (or (::loppu optiot) max-aika)
        text-y-offset 8
        bar-y-offset 3
        taustapalkin-korkeus (- rivin-korkeus 6)
        jana-korkeus 10]

    (when (and min-aika max-aika)
      (let [paivat (pvm/paivat-valissa min-aika max-aika)
            paivia (count paivat)
            paivan-leveys (/ (- leveys alku-x) paivia)
            rivin-y #(+ alku-y (* rivin-korkeus %))
            hover-y (fn [y]
                      (let [rivi (int (/ (- y alku-y) rivin-korkeus))
                            y (rivin-y rivi)]
                        (if (> (+ y 50) korkeus)
                          (- y 15)
                          (+ y 30))))
            paiva-x #(+ alku-x (* (- leveys alku-x)
                                  ((if (pvm/ennen? % min-aika)
                                     - +)
                                    (/ (or (pvm/paivia-valissa-opt % min-aika)
                                           0)
                                       paivia))))
            x->paiva #(t/plus min-aika
                              (t/days (/ % paivan-leveys)))
            kuukaudet (kuukaudet paivat)

            rajaa-nakyvaan-alueeseen (fn [x jana-leveys]
                                       (let [x1 (max alku-x x)
                                             x2 (+ x jana-leveys)]
                                         [x1 (min (- x2 x1) (- leveys alku-x))]))]
        [:svg#aikajana
         {#?@(:clj [:xmlns "http://www.w3.org/2000/svg"])
          :width leveys :height korkeus
          :viewBox (str "0 0 " leveys " " korkeus)
          :on-mouse-up drag-stop!
          :on-mouse-move (when drag-move!
                           (drag-move! alku-x hover-y x->paiva))
          :style {:cursor (when drag "ew-resize")}}

         #?(:cljs
            [paivat-ja-viikot paiva-x alku-x alku-y korkeus paivat]
            :clj
            (paivat-ja-viikot paiva-x alku-x alku-y korkeus paivat))

         (map-indexed
           (fn [i {ajat ::ajat :as rivi}]
             (let [y (rivin-y i)]
               ^{:key i}
               [:g
                [:rect {:x (inc alku-x) :y (- y bar-y-offset)
                        :width (- leveys alku-x)
                        :height taustapalkin-korkeus
                        :fill (if (even? i) "#f0f0f0" "#d0d0d0")}]
                (keep-indexed
                  (fn [j {alku ::alku loppu ::loppu vari ::vari reuna ::reuna
                          teksti ::teksti :as jana}]
                    (let [alku-ja-loppu? (and alku loppu)
                          [alku loppu] (if (and drag (= (::drag drag)
                                                        (::drag jana)))
                                         [(::alku drag) (::loppu drag)]
                                         [alku loppu])
                          ;; Jos on alku, x asettuu ensimmäiselle päivälle, muuten viimeiseen päivään
                          x (inc (paiva-x (or alku loppu)))
                          jana-leveys (- (+ paivan-leveys (- (paiva-x loppu) x)) 2)
                          [x jana-leveys] (rajaa-nakyvaan-alueeseen x jana-leveys)
                          ;; Vähennä väritetyn korkeutta 2px
                          y (if vari (inc y) y)
                          korkeus (if vari (- jana-korkeus 2) jana-korkeus)
                          voi-raahata? (some? (::drag jana))]
                      ^{:key j}
                      [:g
                       (if alku-ja-loppu?
                         ;; Piirä yksittäinen aikajana
                         (when (pos? jana-leveys)
                           [:g [:rect {:x x :y y
                                       :width jana-leveys
                                       :height korkeus
                                       :fill (or vari "white")
                                       ;; Jos väriä ei ole, piirretään valkoinen mutta opacity 0
                                       ;; (täysin läpinäkyvä), jotta hover kuitenkin toimii
                                       :fill-opacity (if vari 1.0 0.0)
                                       :stroke reuna
                                       :rx 3 :ry 3
                                       :on-mouse-over #(show-tooltip! {:x (+ x (/ jana-leveys 2))
                                                                       :y (hover-y y)
                                                                       :text teksti})
                                       :on-mouse-out hide-tooltip!}]
                            ;; kahvat draggaamiseen
                            (when voi-raahata?
                              [:rect {:x (- x 3) :y y :width 7 :height korkeus
                                      :style {:fill "white" :opacity 0.0
                                              :cursor "ew-resize"}
                                      :on-mouse-down #(drag-start! % jana ::alku)}])
                            (when voi-raahata?
                              [:rect {:x (+ x jana-leveys -3) :y y :width 7 :height korkeus
                                      :style {:fill "white" :opacity 0.0
                                              :cursor "ew-resize"}
                                      :on-mouse-down #(drag-start! % jana ::loppu)}])])
                         ;; Vain alku tai loppu, piirrä marker
                         #?(:cljs
                            [marker {:x x :hover-y hover-y :teksti teksti
                                     :korkeus korkeus :alku-x alku-x
                                     :paivan-leveys paivan-leveys
                                     :y y :show-tooltip! show-tooltip!
                                     :hide-tooltip! hide-tooltip! :reuna reuna
                                     :vari vari :suunta (if alku :oikea :vasen)}]
                            :clj
                            (marker {:x x :hover-y hover-y :teksti teksti
                                     :korkeus korkeus :alku-x alku-x
                                     :y y :show-tooltip! show-tooltip!
                                     :paivan-leveys paivan-leveys
                                     :hide-tooltip! hide-tooltip! :reuna reuna
                                     :vari vari :suunta (if alku :oikea :vasen)})))]))
                  ajat)
                [:text {:x 0 :y (+ text-y-offset y)
                        :font-size 10}
                 (::otsikko rivi)]]))
           rivit)

         #?(:cljs
            [nykyhetki paiva-x alku-y korkeus]
            :clj
            (nykyhetki paiva-x alku-y korkeus))

         #?(:cljs [kuukausiotsikot paiva-x korkeus kuukaudet]
            :clj  (kuukausiotsikot paiva-x korkeus kuukaudet))

         #?(:cljs
            [tooltip* (if drag
                        {:x (:x drag)
                         :y (:y drag)
                         :text (str (pvm/pvm (::alku drag)) " \u2013 "
                                    (pvm/pvm (::loppu drag)))}
                        tooltip)])]))))

(defn aikajana
  "Aikajanakomponentti, joka näyttää gantt-kaavion tyylisen aikajanan.
  Komponentti sovittaa alku-/loppuajat automaattisesti kaikkien aikojen perusteella ja lisää
  alkuun ja loppuun 14 päivää. Komponentti mukautuu selaimen leveyteen ja sen korkeus määräytyy
  rivimäärän perusteella."
  ([rivit] (aikajana {} rivit))
  ([{:keys [muuta!] :as optiot} rivit]
    #?(:cljs [aikajana-ui-tila rivit optiot aikajana*]
       :clj  (aikajana* rivit optiot {:leveys (or (:leveys optiot) 750)}))))
