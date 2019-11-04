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
            [harja.ui.komponentti :as komp]))

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
   :valittava?-fn funktio, jolle annetaan pvm. Paluuarvo truthy, jota
                  jonka perusteella tarkastetaan voiko päivää valita."
  [optiot]
  (let [pakota-suunta (:pakota-suunta optiot)
        sijainti-atom (atom (or pakota-suunta nil))
        nyt (or (:pvm optiot) (pvm/nyt))
        nayta (atom [(.getYear nyt) (.getMonth nyt)])
        scroll-kuuntelija (fn [this _]
                            (selvita-kalenterin-suunta this sijainti-atom))]
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

      (fn [{:keys [pvm valitse style valittava?-fn] :as optiot}]
        (let [[vuosi kk] @nayta
              naytettava-kk (t/date-time vuosi (inc kk) 1)
              naytettava-kk-paiva? #(pvm/sama-kuukausi? naytettava-kk %)]
          [:table.pvm-valinta {:style (merge
                                        {:display (if @sijainti-atom "table" "none")
                                         ;; Etenkin jos kalenteri avataan ylöspäin, on tärkeää, että korkeus pysyy vakiona
                                         ;; Muuten otsikkorivi hyppii sen mukaan paljonko kuussa on päiviä.
                                         :height  "200px"}
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
                              (swap! nayta
                                     (fn [[vuosi kk]]
                                       (if (= kk 0)
                                         [(dec vuosi) 11]
                                         [vuosi (dec kk)])))
                              nil)}
              (ikonit/livicon-chevron-left)]
             [:td {:col-span 5} [:span.pvm-kuukausi (nth pvm/+kuukaudet+ kk)] " " [:span.pvm-vuosi vuosi]]
             [:td.pvm-seuraava-kuukausi.klikattava
              {:on-click #(do (.preventDefault %)
                              (swap! nayta
                                     (fn [[vuosi kk]]
                                       (if (= kk 11)
                                         [(inc vuosi) 0]
                                         [vuosi (inc kk)])))
                              nil)}
              (ikonit/livicon-chevron-right)]]
            [:tr.pvm-viikonpaivat
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
                 [:td.pvm-paiva {:class    (str
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
                                                nil)}
                  (t/day paiva)])])]
           [:tfoot.pvm-tanaan-text
            [:tr [:td {:colSpan 7}
                  [:a {:on-click #(do
                                    (.preventDefault %)
                                    (.stopPropagation %)
                                    (valitse (pvm/nyt)))}
                   "Tänään"]]]]])))))

(defn pvm-valintakalenteri-inputilla
  [optiot]
  (let [auki? (atom false)]
    (fn [{:keys [pvm valitse valittava?-fn]}]
      [:div
       [:input.form-control {:type     :text
                             :value    (when-not (nil? pvm) (str (.getDate pvm) "." (.getMonth pvm) "." (.getYear pvm)))
                             :on-focus #(reset! auki? true)
                             :on-blur  (fn [] (js/setTimeout #(reset! auki? false) 100))}]
       (when @auki? [pvm-valintakalenteri {:valitse       valitse
                                           :valittava?-fn #(true? true)
                                           :pvm pvm}])])))