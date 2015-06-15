(ns harja.ui.pvm
  "Päivämäärän valintakomponentti"
  (:require [reagent.core :refer [atom] :as r]
            [cljs-time.core :as t]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.ikonit :as ikonit]))

(def +paivat+ ["Ma" "Ti" "Ke" "To" "Pe" "La" "Su"])
(def +kuukaudet+ ["Tammi" "Helmi" "Maalis" "Huhti"
                  "Touko" "Kesä" "Heinä" "Elo"
                  "Syys" "Loka" "Marras" "Joulu"])

(defn- pilko-viikoiksi [vuosi kk]
  ;;(.log js/console "vuosi: " vuosi ", kk: " kk)
  (let [kk (inc kk) ;; cljs-time käyttää luonnollisia kk numeroita
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
    
    
(defn pvm
  "Luo uuden päivämäärävalinnan.
Seuraavat optiot ovat mahdollisia:

:pvm      tämänhetkinen päivämäärä (goog.date.Date)
:vuosi    näytettävä vuosi, oletus nykyinen
:kuukausi näytettävä kuukausi (0 - 11)
:valitse  funktio, jota kutsutaan kun päivämäärä valitaan
:sijainti optionaalinen [x y w] vasen ylänurkka 

  ...muita tarpeen mukaan..."
  [optiot]
  (let [nyt (or (:pvm optiot) (t/now))
        nayta (atom [(.getYear nyt) (.getMonth nyt)])]
    (r/create-class
      {:component-will-receive-props
       (fn [this new-argv]
         (let [optiot (second new-argv)
               pvm (:pvm optiot)]
           (when pvm
             ;; päivitetään näytä vuosi ja kk
             (reset! nayta [(.getYear pvm) (.getMonth pvm)]))))


       :reagent-render
       (fn [{:keys [pvm valitse sijainti] :as optiot}]
         (let [[vuosi kk] @nayta
               naytettava-kk (t/date-time vuosi (inc kk) 1)
               naytettava-kk-paiva? #(pvm/sama-kuukausi? naytettava-kk %)]
           [:table.pvm-valinta (when sijainti
                                 (let [[x y w] sijainti]
                                   {:style {:left x :top y
                                            :width w
                                            :position "absolute"}}))
            [:tbody.pvm-kontrollit
             [:tr
              [:td.pvm-edellinen-kuukausi.klikattava
               {:on-click #(do (.preventDefault %)
                               (swap! nayta
                                      (fn [[vuosi kk]]
                                        (if (= kk 0)
                                          [(dec vuosi) 11]
                                          [vuosi (dec kk)])))
                               nil)}
               (ikonit/chevron-left)]
              [:td {:col-span 5} [:span.pvm-kuukausi (nth +kuukaudet+ kk)] " " [:span.pvm-vuosi vuosi]]
              [:td.pvm-seuraava-kuukausi.klikattava
               {:on-click #(do (.preventDefault %)
                               (swap! nayta
                                      (fn [[vuosi kk]]
                                        (if (= kk 11)
                                          [(inc vuosi) 0]
                                          [vuosi (inc kk)])))
                               nil)}
               (ikonit/chevron-right)]]
             [:tr.pvm-viikonpaivat
              (for [paiva +paivat+]
                ^{:key paiva}
                [:td paiva])]]

            [:tbody.pvm-paivat
             (for [paivat (pilko-viikoiksi vuosi kk)]
               ^{:key (pvm/millisekunteina (first paivat))}
               [:tr
                (for [paiva paivat]
                  ^{:key (pvm/millisekunteina paiva)}
                  [:td.pvm-paiva.klikattava {:class (str
                                                     (when (and pvm
                                                                (= (t/day paiva) (t/day pvm))
                                                                (= (t/month paiva) (t/month pvm))
                                                                (= (t/year paiva) (t/year pvm)))
                                                       "pvm-valittu ")
                                                     (if (naytettava-kk-paiva? paiva)
                                                       "pvm-naytettava-kk-paiva" "pvm-muu-kk-paiva"))

                                  :on-click #(do (.stopPropagation %) (valitse paiva) nil)}
                   (t/day paiva)])])]]))})))


