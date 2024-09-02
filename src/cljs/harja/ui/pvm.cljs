(ns harja.ui.pvm
  "Päivämäärän valintakomponentti"
  (:require [reagent.core :refer [atom] :as r]
            [cljs-time.core :as t]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.ikonit :as ikonit]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [harja.ui.dom :as dom]
            [harja.ui.komponentti :as komp]
            [harja.fmt :as fmt]
            [harja.loki :as loki]))

(def +paivat+ ["Ma" "Ti" "Ke" "To" "Pe" "La" "Su"])


(defn selvita-kalenterin-suunta [komponentti sijainti-atom]
  (let [etaisyys-alareunaan (dom/elementin-etaisyys-viewportin-alareunaan
                              (.-parentNode (r/dom-node komponentti)))
        etaisyys-oikeaan-reunaan (dom/elementin-etaisyys-viewportin-oikeaan-reunaan
                                   (.-parentNode (r/dom-node komponentti)))
        uusi-suunta (if (< etaisyys-alareunaan 250)
                      (if (< etaisyys-oikeaan-reunaan 200)
                        :ylos-vasen
                        :ylos-oikea)
                      (if (< etaisyys-oikeaan-reunaan 200)
                        :alas-vasen
                        :alas-oikea))]
    (reset! sijainti-atom uusi-suunta)))

(defn- pilko-viikoiksi [vuosi kk]
  (let [kk (inc kk)                                         ;; cljs-time käyttää luonnollisia kk numeroita
        kk-alku (t/date-time vuosi kk 1)
        ;;_ (log "kk-alku on: " kk-alku)
        viikon-alku (loop [pvm (t/date-time vuosi kk 1)]
                      (if (= 1 (t/day-of-week pvm))
                        pvm
                        (recur (t/minus pvm (t/days 1)))))
        kk-loppu (loop [pvm (t/date-time vuosi kk 28)]
                   (if (not= (t/month pvm) kk)
                     ;; mentiin yli seuraavaan kuukauten, palauta edellinen päivä
                     (t/minus pvm (t/days 1))

                     ;; vielä samassa kuussa, kelataan huomiseen
                     (recur (t/plus pvm (t/days 1)))))
        viikon-loppu (loop [pvm kk-loppu]
                       ;; Kelataan päivää sunnuntaihin asti eteenpäin, että saadaan täysi viikko
                       (if (= 7 (t/day-of-week pvm))
                         pvm
                         (recur (t/plus pvm (t/days 1)))))]
    (loop [paivat []
           p viikon-alku]
      (if (t/after? p viikon-loppu)
        (vec (partition 7 7 [] paivat))
        (recur (conj paivat p)
               (t/plus p (t/days 1)))))))

(defn pvm-valintakalenteri
  "Luo uuden päivämäärävalinnan.

   Seuraavat optiot ovat mahdollisia:

   :pvm      tämänhetkinen päivämäärä (goog.date.Date)
   :vuosi    näytettävä vuosi, oletus nykyinen
   :kuukausi näytettävä kuukausi (0 - 11)
   :valitse  funktio, jota kutsutaan kun päivämäärä valitaan
   :flowissa?  position absoluten sijaan position static, jolloin voi käyttää normiflowissa
   :valittava?-fn funktio, jolle annetaan pvm. Paluuarvo truthy, jota
                  jonka perusteella tarkastetaan voiko päivää valita."
  [optiot]
  (let [pakota-suunta (:pakota-suunta optiot)
        sijainti-atom (atom (or pakota-suunta nil))
        nyt (or (:pvm optiot) (pvm/nyt))
        nayta (atom [(.getYear nyt) (.getMonth nyt)])
        scroll-kuuntelija (fn [this _]
                            (selvita-kalenterin-suunta this sijainti-atom))
        flowissa? (:flowissa? optiot)
        {vayla-tyyli? :vayla-tyyli?} optiot
        tyyli-otsikkorivi (if vayla-tyyli? "calendar-header-row " "pvm-viikonpaivat ")
        tyyli-paivasolu (if vayla-tyyli? "calendar-cell " "pvm-paiva ")
        tyyli-kalenteri (if vayla-tyyli? "calendar-default " "pvm-valinta ")]
    (komp/luo
      {:component-will-receive-props
       (fn [this & [_ optiot]]
         (when-let [pvm (:pvm optiot)]
           ;; päivitetään näytä vuosi ja kk
           (reset! nayta [(.getYear pvm) (.getMonth pvm)])))
       :component-did-mount
       (fn [this _]
         (when (not pakota-suunta)
           (selvita-kalenterin-suunta this sijainti-atom)))}

      (komp/dom-kuuntelija js/window
                           EventType/SCROLL scroll-kuuntelija)

      (fn [{:keys [pvm valitse style valittava?-fn vayla-tyyli? sulje-kalenteri input-id] :as optiot}]
        (let [[vuosi kk] @nayta
              naytettava-kk (t/date-time vuosi (inc kk) 1)
              naytettava-kk-paiva? #(pvm/sama-kuukausi? naytettava-kk %)
              fokus-paiva (if pvm (t/day pvm) 1)
              nayta-edellinen-kk #(swap! nayta
                                    (fn [[vuosi kk]]
                                      (if (= kk 0)
                                        [(dec vuosi) 11]
                                        [vuosi (dec kk)])))
              nayta-seuraava-kk #(swap! nayta
                                   (fn [[vuosi kk]]
                                     (if (= kk 11)
                                       [(inc vuosi) 0]
                                       [vuosi (inc kk)])))]
          [:table {:class (str tyyli-kalenteri)
                   :style (merge
                            {:display (if @sijainti-atom "table" "none")
                             ;; Etenkin jos kalenteri avataan ylöspäin, on tärkeää, että korkeus pysyy vakiona
                             ;; Muuten otsikkorivi hyppii sen mukaan paljonko kuussa on päiviä.
                             :height  "200px"}
                            (if flowissa? {:position "static"} {})
                            (case @sijainti-atom
                              :ylos-oikea {:bottom "100%" :left 0}
                              :ylos-vasen {:bottom "100%" :right 0}
                              :alas-oikea {:top "100%" :left 0}
                              :alas-vasen {:top "100%" :right 0}
                              {}))}
           [:thead.pvm-kontrollit
            [:tr
             [:td.pvm-edellinen-kuukausi.klikattava
              {:on-click #(do (.preventDefault %)
                              (nayta-edellinen-kk)
                              nil)
               :tabIndex "0"
               :on-key-down  #(do
                                (when (dom/enter-nappain? %)
                                  (.preventDefault %)
                                  (nayta-edellinen-kk))
                                (when (dom/tab+shift-nappaimet? %)
                                  (.preventDefault %)
                                  (sulje-kalenteri))
                                nil)}
              (ikonit/livicon-chevron-left)]
             [:td {:col-span 5} [:span.pvm-kuukausi (nth pvm/+kuukaudet+ kk)] " " [:span.pvm-vuosi vuosi]]
             [:td.pvm-seuraava-kuukausi.klikattava
              {:on-click #(do (.preventDefault %)
                              (nayta-seuraava-kk)
                              nil)
               :tabIndex "0"
               :id "seuraava-kk"
               :on-key-down #(do
                               (when (dom/enter-nappain? %)
                                 (.preventDefault %)
                                 (nayta-seuraava-kk))
                               (when (dom/tab-nappain-ilman-shiftia? %)
                                 (.preventDefault %)
                                 (r/after-render (fn [] (some-> js/document (.getElementById (str "paiva_" fokus-paiva)) .focus))))
                               nil)}
              (ikonit/livicon-chevron-right)]]
            [:tr {:class tyyli-otsikkorivi}
             (for [paiva +paivat+]
               ^{:key paiva}
               [:td paiva])]]

           [:tbody.pvm-paivat
            (for [paivat (pilko-viikoiksi vuosi kk)]
              ^{:key (pvm/millisekunteina (first paivat))}
              [:tr
               (for [paiva paivat
                     :let [valittava? (or (not (some? valittava?-fn))
                                          (valittava?-fn paiva))]]
                 ^{:key (pvm/millisekunteina paiva)}
                 [:td {:class    (str tyyli-paivasolu
                                      (if valittava?
                                        "klikattava "
                                        "pvm-disabloitu ")
                                      (when (pvm/sama-pvm? (pvm/nyt) paiva) "pvm-tanaan ")
                                      (when (and pvm
                                                 (= (t/day paiva) (t/day pvm))
                                                 (= (t/month paiva) (t/month pvm))
                                                 (= (t/year paiva) (t/year pvm)))
                                        "pvm-valittu ")
                                      (if (naytettava-kk-paiva? paiva)
                                        "pvm-naytettava-kk-paiva" "pvm-muu-kk-paiva"))

                       :on-click #(do (.stopPropagation %)
                                      (when valittava?
                                        (valitse paiva))
                                      nil)
                       :tabIndex (if (naytettava-kk-paiva? paiva)
                                   "0")
                       :id (when (naytettava-kk-paiva? paiva) (str "paiva_" (t/day paiva)))
                       :on-key-down #(do
                                       (.preventDefault %)

                                       (cond
                                         (and (dom/enter-nappain? %) valittava?)
                                         (do (valitse paiva) (sulje-kalenteri))

                                         (dom/tab+shift-nappaimet? %)
                                         (r/after-render (fn [] (some-> js/document (.getElementById "seuraava-kk") .focus)))

                                         (dom/tab-nappain-ilman-shiftia? %)
                                         (r/after-render (fn [] (some-> js/document (.getElementById "tanaan") .focus)))

                                         (dom/nuoli-oikealle? %)
                                         (r/after-render (fn [] (some-> js/document (.getElementById (str "paiva_" (inc (t/day paiva)))) .focus)))

                                         (dom/nuoli-vasemmalle? %)
                                         (r/after-render (fn [] (some-> js/document (.getElementById (str "paiva_" (dec (t/day paiva)))) .focus)))

                                         (dom/nuoli-ylos? %)
                                         (r/after-render (fn [] (some-> js/document (.getElementById (str "paiva_" (- (t/day paiva) 7))) .focus)))

                                         (dom/nuoli-alas? %)
                                         (r/after-render (fn [] (some-> js/document (.getElementById (str "paiva_" (+ (t/day paiva) 7))) .focus)))

                                         :else nil)

                                       nil)}
                  (t/day paiva)])])]
           [:tfoot.pvm-tanaan-text
            [:tr [:td {:colSpan 7}
                  [:a {:on-click #(do
                                    (.preventDefault %)
                                    (.stopPropagation %)
                                    (when (or (not (some? valittava?-fn))
                                            (valittava?-fn (pvm/nyt)))
                                      (valitse (pvm/nyt))))
                       :tabIndex "0"
                       :id "tanaan"
                       :on-key-down #(do
                                       (.preventDefault %)
                                       (when (and (dom/enter-nappain? %)
                                               (or (not (some? valittava?-fn))
                                                 (valittava?-fn (pvm/nyt))))
                                         (valitse (pvm/nyt))
                                         (sulje-kalenteri))
                                       (when (dom/tab+shift-nappaimet? %)
                                         (r/after-render (fn []
                                                           (some-> js/document (.getElementById (if pvm
                                                                                                  (str "paiva_" (t/day pvm))
                                                                                                  (str "paiva_1"))) .focus))))
                                       (when (dom/tab-nappain-ilman-shiftia? %)
                                         (sulje-kalenteri))
                                       nil)}
                   "Tänään"]]]]])))))

(defn pvm-valintakalenteri-inputilla
  [_]
  (let [auki? (r/atom false)
        suora-syotto-sisalto (r/atom "")]
    (fn [{:keys [paivamaara valitse luokat valittava?-fn disabled sumeutus-fn placeholder]}]
      (let [kiinni #(reset! % false)
            elementin-id (str (gensym "pvm-pakollinen-input"))]
        [:div.kalenteri-kontti
         [:input {:disabled disabled
                  :type :text
                  :class (apply conj #{} (filter #(not (nil? %)) (conj luokat (when @auki? "auki"))))
                  :value (cond
                           (seq @suora-syotto-sisalto) @suora-syotto-sisalto
                           (not (nil? paivamaara)) (pvm/pvm paivamaara)
                           :else "")
                  :placeholder placeholder
                  :on-change #(reset! suora-syotto-sisalto (-> % .-target .-value))
                  :on-click #(reset! auki? true)
                  :on-focus    #(reset! auki? true)
                  :on-key-down #(do
                                  (when (or (dom/tab+shift-nappaimet? %) (dom/esc-nappain? %))
                                    (kiinni auki?))
                                  (when (dom/enter-nappain? %)
                                    (reset! auki? (not @auki?))))
                  :on-blur     (fn []
                                 (when (not paivamaara) (kiinni auki?)) ; jos ei ole päivämäärää määritelty, niin valintoja ei voi tehdä. droppari jää auki, kunnes koontilaskun kuukausi klikataan. tällä estetään se tilanne.
                                 (when sumeutus-fn (sumeutus-fn))
                                 (when (seq @suora-syotto-sisalto)
                                   (let [pvm-sisalto (pvm/->pvm @suora-syotto-sisalto)]
                                     (when (valittava?-fn pvm-sisalto)
                                       (valitse (pvm/->pvm @suora-syotto-sisalto))))
                                   (reset! suora-syotto-sisalto "")))
                  :id elementin-id}]
         (when @auki?
           [pvm-valintakalenteri {:valitse       #(do
                                                    (kiinni auki?)
                                                    (valitse %))
                                  :valittava?-fn valittava?-fn
                                  :pvm           paivamaara
                                  :sulje-kalenteri #(do
                                                      (some-> js/document (.getElementById elementin-id) .focus)
                                                      (reset! auki? false))
                                  :input-id elementin-id}])]))))
