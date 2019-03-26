(ns harja.ui.aikajana
  "Aikajananäkymä, jossa voi useita eri asioita näyttää aikajanalla.
  Vähän kuten paljon käytetty gantt kaavio."
  (:require
   [clojure.spec.alpha :as s]
   [harja.domain.valitavoite :as vt-domain]
   #?@(:cljs [[reagent.core :as r]
              [harja.ui.dom :as dom]
              [cljs-time.core :as t]
              [harja.ui.debug :as debug]
              [cljs.core.async :refer [<!]]]
       :clj [[clj-time.core :as t]
             [clojure.core.async :refer [<! go]]
             [harja.tyokalut.spec :refer [defn+]]])
   [harja.pvm :as pvm]
   [clojure.string :as str])
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

(s/def ::drag vector?) ;; [yllapitokohde-id, :tyyppi (:paallystys/:tiemerkinta/:kohde/:tarkka-aikataulu), tarkka-aikataulu-id]

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
  ;; Lasketaan laatikon tarvitsema leveys kirjainten leveyden mukaan -> päästään noin suurin piirtein oikeaan tulokseen
  ;; Toinen, tarkempi tapa, olisi renderöidä teksti ensin näkymättömänä ja ottaa piirretyn elementin todellinen leveys.
  (let [kirjaimen-leveys 5.3
        leveys (* (count text) kirjaimen-leveys)]
    (when (and x y text)
      [:g
       [:rect {:x (- x (/ leveys 2))
               :y (- y 14)
               :width (* (count text) kirjaimen-leveys)
               :height 26
               :rx 10 :ry 10
               :style {:fill "black"
                       :opacity "0.55"}}]
       [:text {:x x :y (+ y 4)
               :font-size 10
               :style {:fill "white"}
               :text-anchor "middle"}
        text]])))

#?(:cljs
   (defn- aikajana-ui-tila [rivit {:keys [muuta! ennen-muokkausta] :as optiot} komponentti]
     (r/with-let [tooltip (r/atom nil)
                  valitut-palkit (r/atom #{}) ;; Käytössä, jos valitaan erikseen (yleensä useita) palkkeja raahattavaksi
                  drag-kursori (r/atom nil) ; Nykyisen raahauksen kursorin tiedot
                  lopetetaan-raahaus? (atom false)
                  drag (r/atom [])]
       [:div.aikajana
        [komponentti rivit optiot
         {:tooltip @tooltip
          :show-tooltip! #(reset! tooltip %)
          :hide-tooltip! #(reset! tooltip nil)
          :valitut-palkit @valitut-palkit
          ;; drag on vector mappeja, joka sisältää raahattavien palkkien tiedot. Mapissa avaimet:
          ;; ::alku (raahauksen uusi pvm), ::loppu (raahauksen uusi pvm),
          ;; ::alkup-alku (raahauksen alussa ollut alkupvm), ::alkup-loppu (raahauksen alussa ollut loppupvm)
          ;; ::drag, palkin ::drag tiedot: [id jana-tyyppi tarkka-aikajana-id],
          ;; :avain, mitä aikaa raahataan: :alku/:loppu/:palkki)
          :drag @drag
          ;; drag-kursori sisältää kursorin tiedot raahauksessa. Mapissa avaimet:
          ;; :tooltip-x, :tooltip-y, :drag-alku-koordinaatti [x, y]
          :drag-kursori drag-kursori
          :click-select! (fn [e jana avain]
                           (.preventDefault e)
                           (when (.-ctrlKey e)
                             (let [olemassa-oleva-valinta (first (filter #(= (::drag %) (::drag jana))
                                                                         @valitut-palkit))]
                               (if olemassa-oleva-valinta
                                 (reset! valitut-palkit (set (remove #(= % olemassa-oleva-valinta) @valitut-palkit)))
                                 (reset! valitut-palkit (conj @valitut-palkit jana))))))
          :drag-start! (fn [e jana avain]
                         (.preventDefault e)
                         (when-not (.-ctrlKey e)
                           (if (empty? @valitut-palkit)
                             ;; Ei erikseen valittuja palkkeja, raahaa tätä janaa
                             (reset! drag [{::alku (::alku jana)
                                            ::loppu (::loppu jana)
                                            ::drag (::drag jana)
                                            ::sahkopostitiedot (::sahkopostitiedot jana)
                                            ::kohde-nimi (::kohde-nimi jana)
                                            ::alkup-alku (::alku jana)
                                            ::alkup-loppu (::loppu jana)
                                            :avain avain}])
                             ;; Käyttäjä on erikseen valinnut raahattavat janat, lisää ne raahaukseen
                             (reset! drag (map #(-> {::alku (::alku %)
                                                     ::loppu (::loppu %)
                                                     ::drag (::drag %)
                                                     ::sahkopostitiedot (::sahkopostitiedot %)
                                                     ::kohde-nimi (::kohde-nimi %)
                                                     ::alkup-alku (::alku %)
                                                     ::alkup-loppu (::loppu %)
                                                     :avain avain})
                                               @valitut-palkit)))))
          :drag-move! (fn [alku-x hover-y x->paiva]
                        (fn [e]
                          (.preventDefault e)
                          (when (and (not (.-ctrlKey e))
                                     (not @lopetetaan-raahaus?)
                                     (not (empty? @drag)))
                            (when (not (zero? (.-buttons e))) ;; Hiiren nappi edelleen alhaalla
                              (let [[svg-x svg-y _ _] (dom/sijainti (dom/elementti-idlla "aikajana"))
                                    cx (.-clientX e) ; Hiiren nykyinen koordinaatti koko sivulla
                                    cy (.-clientY e)
                                    x (- cx svg-x alku-x) ; Hiiren nykyinen koordinatti aikajanan sisällä
                                    y (- cy svg-y)
                                    lahto-x-pvm (when-let [raahaus-alku-x (first (:drag-alku-koordinaatti @drag-kursori))]
                                                  (x->paiva raahaus-alku-x))
                                    nykyinen-x-pvm (x->paiva x)
                                    pvm-ero (when (and lahto-x-pvm nykyinen-x-pvm)
                                              (if (t/before? lahto-x-pvm nykyinen-x-pvm)
                                                (t/in-days (t/interval lahto-x-pvm nykyinen-x-pvm))
                                                (- (t/in-days (t/interval nykyinen-x-pvm lahto-x-pvm)))))
                                    tooltip-x (+ alku-x x)
                                    tooltip-y (hover-y y)]

                                ;; Otetaan raahauksen alkutilanne ylös
                                (when-not (:drag-alku-koordinaatti @drag-kursori)
                                  (reset! drag-kursori {:tooltip-x tooltip-x
                                                        :tooltip-y tooltip-y
                                                        :drag-alku-koordinaatti [x y]}))

                                ;; Raahaa palkkeja
                                (reset! drag
                                        (map (fn [{avain :avain :as drag}]
                                               (let [uusi-alku (t/plus (::alkup-alku drag) (t/days pvm-ero))
                                                     uusi-loppu (t/plus (::alkup-loppu drag) (t/days pvm-ero))]
                                                 (cond
                                                   ;; Alku tai loppu. Varmistetaan, että venyy oikeaan suuntaan
                                                   ;; ja on aina validi.
                                                   ;; Siirretään palkkia muutoksen verran tiettyyn suuntaan
                                                   ;; (ei voida suoraan asettaa kursorin koordinattia uudeksi aluksi/lopuksi,
                                                   ;; koska usean raahauksessa ei toimi hyvin)
                                                   (and (= avain ::alku) (pvm/sama-tai-ennen? uusi-alku (::loppu drag)))
                                                   (assoc drag avain uusi-alku)

                                                   (and (= avain ::loppu) (pvm/sama-tai-jalkeen? uusi-loppu (::alku drag)))
                                                   (assoc drag avain uusi-loppu)

                                                   ;; Koko palkki, siirretään alkua ja loppua eron verran
                                                   (= avain ::palkki)
                                                   (assoc drag ::alku uusi-alku ::loppu uusi-loppu)
                                                   :default drag)))
                                             @drag)))))))
          :on-mouse-up! (fn [e]
                          (let [tyhjenna-muokkaustila! (fn []
                                                         (reset! drag [])
                                                         (reset! drag-kursori nil)
                                                         (reset! lopetetaan-raahaus? false)
                                                         (reset! valitut-palkit #{}))
                                tallenna-muutos! (fn []
                                                   (go
                                                     (<! (muuta! (map #(select-keys % #{::drag ::alku ::loppu}) @drag)))
                                                     (tyhjenna-muokkaustila!)))
                                peru-muutos! (fn []
                                               (tyhjenna-muokkaustila!))]
                            ;; Ei raahata mitään, tehdään ohi klikkaus ilman CTRL:ää -> poista kaikki valinnat
                            (when (and (not (.-ctrlKey e))
                                       (empty? @drag))
                              (reset! valitut-palkit #{}))

                            ;; Käsittele muutos, jos raahattiin palkkeja
                            (when-not (empty? @drag)
                              (reset! lopetetaan-raahaus? true)
                              (if ennen-muokkausta
                                (ennen-muokkausta @drag tallenna-muutos! peru-muutos!)
                                (tallenna-muutos!)))))
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

(defn- aikajana* [rivit optiot {:keys [tooltip show-tooltip! hide-tooltip! valitut-palkit drag-kursori
                                       drag click-select! drag-start! drag-move! on-mouse-up! leveys] :as asetukset}]
  (let [rivit #?(:cljs rivit
                 :clj  (map jodaksi rivit))
        rivin-korkeus 20
        alku-x 150 ; Kuvaa pistettä, josta aikajana alkaa (kohteiden nimien jälkeen)
        alku-y 50
        korkeus (+ alku-y (* (count rivit) rivin-korkeus))
        kaikki-ajat (mapcat ::ajat rivit)
        valitut-palkit (or valitut-palkit #{})
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
            raahataan? (not (empty? drag))
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
          :on-context-menu (fn [e]
                             ;; Ainakin Mac-koneissa Ctrl+Click = Hiiren kakkospainikkeella klikkaus
                             ;; Ctrl+Click käytetään asioiden valitsemiseen, estetään siis oletus-action eli context-menu
                             (.preventDefault e)
                             false)
          :on-mouse-up on-mouse-up!
          :on-mouse-move (when drag-move!
                           (drag-move! alku-x hover-y x->paiva))
          :style {:cursor (when raahataan? "ew-resize")}}

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
                        taman-janan-raahaus (first (filter #(= (::drag %) (::drag jana)) drag))
                        [alku loppu] (if (and raahataan? taman-janan-raahaus)
                                       [(::alku taman-janan-raahaus) (::loppu taman-janan-raahaus)]
                                       [alku loppu])
                        ;; Jos on alku, x asettuu ensimmäiselle päivälle, muuten viimeiseen päivään
                        x (inc (paiva-x (or alku loppu)))
                        jana-valittu? (valitut-palkit jana)
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
                         [:g [:rect (merge
                                     (when voi-raahata?
                                       {:style {:cursor "move"}
                                        :on-mouse-down (fn [e]
                                                         (click-select! e jana ::palkki)
                                                         (drag-start! e jana ::palkki))})
                                     {:x x :y y
                                      :width jana-leveys
                                      :height korkeus
                                      :fill (or vari "white")
                                      ;; Jos väriä ei ole, piirretään valkoinen mutta opacity 0
                                      ;; (täysin läpinäkyvä), jotta hover kuitenkin toimii
                                      :fill-opacity (if vari 1.0 0.0)
                                      :stroke (if jana-valittu? "red" reuna)
                                      :rx 3 :ry 3
                                      :on-mouse-over #(show-tooltip! {:x (+ x (/ jana-leveys 2))
                                                                      :y (hover-y y)
                                                                      :text teksti})
                                      :on-mouse-out hide-tooltip!})]
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
                                   :vari vari :suunta (if alku :oikea :vasen)})))

                     ;; Välitavoitteet
                     (map-indexed
                      (fn [i valitavoite]
                        (let [vari-kesken "#FFA500"
                              vari-valmis "#00cc25"
                              vari-myohassa "#da252e"
                              vari (case (vt-domain/valmiustila valitavoite)
                                     :valmis vari-valmis
                                     :myohassa vari-myohassa
                                     vari-kesken)
                              x (paiva-x (:takaraja valitavoite))]
                          (when (>= x alku-x)
                            ^{:key i}
                            [:rect {:x x
                                    :y y
                                    :width 5
                                    :height korkeus
                                    :fill vari
                                    :fill-opacity 1.0
                                    :on-mouse-over #(show-tooltip!
                                                     {:x x
                                                      :y (hover-y y)
                                                      :text (str (:nimi valitavoite)
                                                                 " ("
                                                                 (str/lower-case
                                                                  (vt-domain/valmiustilan-kuvaus-yksinkertainen
                                                                   valitavoite))
                                                                 ", takaraja "
                                                                 (pvm/pvm (:takaraja valitavoite))
                                                                 ")")})
                                    :on-mouse-out hide-tooltip!}])))
                      (filter :takaraja (::valitavoitteet rivi)))]))
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
            [tooltip* (if raahataan?
                        {:x (:tooltip-x @drag-kursori)
                         :y (:tooltip-y @drag-kursori)
                         :text (if (= (count drag) 1)
                                 (str (pvm/pvm (::alku (first drag))) " \u2013 "
                                      (pvm/pvm (::loppu (first drag))))
                                 ;; Näytetään valittujen palkkien aikaisin ja viimeisin pvm
                                 (str "Koko aikaväli: "
                                      (pvm/pvm (first (sort t/before? (map ::alku drag)))) " \u2013 "
                                      (pvm/pvm (last (sort t/before? (map ::loppu drag))))))}
                        tooltip)])]))))

(defn aikajana
  "Aikajanakomponentti, joka näyttää gantt-kaavion tyylisen aikajanan.
  Komponentti sovittaa alku- ja loppuajat automaattisesti kaikkien aikojen perusteella ja lisää
  alkuun ja loppuun 14 päivää. Komponentti mukautuu selaimen leveyteen ja sen korkeus määräytyy
  rivimäärän perusteella."
  ([rivit] (aikajana {} rivit))
  ([{:keys [muuta!] :as optiot} rivit]
   #?(:cljs [aikajana-ui-tila rivit optiot aikajana*]
      :clj  (aikajana* rivit optiot {:leveys (or (:leveys optiot) 750)}))))
