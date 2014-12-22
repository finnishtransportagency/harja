(ns harja.views.urakat
  "Harjan näkymä, jossa näytetään karttaa sekä kontekstisidonnaiset asiat."
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :as async :refer [chan <! >!]]
             
            [harja.ui.listings :refer [filtered-listing]]
            [harja.ui.leaflet :refer [leaflet]]
            [harja.ui.yleiset :refer [ajax-loader]]
            
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.urakat :as ur]
            
            [harja.asiakas.tapahtumat :as t]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(def valittu-hallintayksikko "Tällä hetkellä valittu hallintayksikkö (tai nil)" (atom nil))

(def valittu-urakka "Tällä hetkellä valittu urakka (hoidon alueurakka / ylläpidon urakka) tai nil" (atom nil))

(def kartta-ch "Karttakomponentin käskyttämisen komentokanava" (atom nil))

(def urakkalista "Hallintayksikon urakat" (atom nil))

(defn valitse-hallintayksikko [yks]
  (reset! valittu-hallintayksikko yks)
  (reset! urakkalista nil)
  (reset! valittu-urakka nil)
  (if yks
    (do
      (go (reset! urakkalista (<! (ur/hae-hallintayksikon-urakat yks))))
      (t/julkaise! (assoc yks :aihe :hallintayksikko-valittu)))
        
    (t/julkaise! {:aihe :hallintayksikkovalinta-poistettu})))

(defn valitse-urakka [ur]
  (reset! valittu-urakka ur))

(defn murupolku
  "Näyttää tämänhetkiset valinnat murupolkuna"
  []
  (let [hallintayksikon-valinta (atom false)]
    (reagent/create-class
     {:get-initial-state (fn [this] {:hallintayksikon-valinta hallintayksikon-valinta})
      :render (fn [this]
                (let [hallintayksikon-valinta (-> this reagent/state :hallintayksikon-valinta)]
                  [:ol.breadcrumb
                   [:li [:a {:href "#" :on-click #(valitse-hallintayksikko nil)}
                         "Koko Suomi"]]
                   (when-let [valittu @valittu-hallintayksikko]
                     [:li.dropdown {:class (when @hallintayksikon-valinta "open")}
                      [:a {:href "#" 
                           :on-click #(swap! hallintayksikon-valinta not)}
                       (:nimi valittu)
                       [:span.caret]]

                      ;; Alasvetovalikko yksikön nopeaa vaihtamista varten
                      [:ul.dropdown-menu {:role "menu"}
                       (for [muu-yksikko (filter #(not= % valittu) @hal/hallintayksikot)]
                         ^{:key (str "hy-" (:id muu-yksikko))}
                         [:li [:a {:href "#" :on-click #(do (reset! hallintayksikon-valinta false)
                                                            (valitse-hallintayksikko muu-yksikko))} (:nimi muu-yksikko)]])]
          
                      ])
                   (when @valittu-urakka
                     [:li [:a {:href "#"}
                           (:nimi @valittu-urakka)]])]))             
      :component-did-mount (fn [this]
                             (.log js/console "murupolku: mounted!")
                             (reagent/set-state this {::lopeta-kuuntelu (t/kuuntele! :hallintayksikkovalinta-poistettu
                                                                                     (fn [_]
                                                                                       (.log js/console "piilota dropdown")
                                                                                       (reset! hallintayksikon-valinta false)))}))
      :component-will-unmount (fn [this]
                                ((-> this reagent/state ::lopeta-kuuntelu)))
      })))



(def view-position (atom [65.1 25.2]))
(def zoom-level (atom 8))

;; Joitain värejä... voi keksiä paremmat tai "oikeat", jos sellaiset on tiedossa
(def +varit+ ["#E04836" "#F39D41" "#8D5924" "#5696BC" "#2F5168" "wheat" "teal"])


(defn kartta []
  (let [hals @hal/hallintayksikot
        v-hal @valittu-hallintayksikko]
    [leaflet {:id "kartta"
              :width "100%" :height "750px" ;; set width/height as CSS units, must set height as pixels!
              :view view-position           ;; map center position
              :zoom zoom-level              ;; map zoom level
              :selection valittu-hallintayksikko
              :on-select (fn [item]
                           (condp = (:type item)
                             :hy (valitse-hallintayksikko item)
                             :ur (valitse-urakka item)))
              :geometries (cond
                           ;; Ei valittua hallintayksikköä, näytetään hallintayksiköt
                           (nil? v-hal)
                           hals

                           ;; Ei valittua urakkaa, näytetään valittu hallintayksikkö ja sen urakat
                           (nil? @valittu-urakka)
                           (vec (concat [(assoc v-hal :valittu true)]
                                        @urakkalista))
                           
                           ;; Valittu urakka, mitä näytetään?
                           :default [])
              
              :geometry-fn (fn [hy]
                             (when-let [alue (:alue hy)]
                               {:type (if (:valittu hy) :line :polygon)
                                :coordinates alue
                                :color (nth +varit+ (mod (hash (:nimi hy)) (count +varit+)))}))

              ;; PENDING: tilalle MML kartat, kunhan ne saadaan 
              :layers [{:type :tile
                        :url "http://{s}.tile.osm.org/{z}/{x}/{y}.png"
                        :attribution "&copy; <a href=\"http://osm.org/copyright\">OpenStreetMap</a> contributors"}]

              }
     ]))



(defn urakat
  "Harjan karttasivu."
  []
  [:span
   [murupolku]
   [:div#sidebar-left.col-sm-4
    (let [v-hal @valittu-hallintayksikko
          v-ur @valittu-urakka]
      (if-not v-hal
        ;; Hallintayksikköä ei ole valittu: näytetään lista hallintayksiköistä
        [:span
         [:h5.haku-otsikko "Hae alueurakka kartalta tai listasta"]
         [:div
          ^{:key "hy-lista"}
          [filtered-listing {:format :nimi :haku :nimi
                             :selection valittu-hallintayksikko
                             :on-select valitse-hallintayksikko}
           hal/hallintayksikot]]]
        
        ;; Hallintayksikko on valittu, mutta urakkaa ei: näytetään luettelossa urakat
        (if-not v-ur
          ;;(let [urakat (ur/hallintayksikon-urakat v-hal)]
            (if (nil? @urakkalista)
              [ajax-loader "Urakoita haetaan..."]
              [:span
               [:h5.haku-otsikko "Hae urakka kartalta tai listasta"]
               [:div
                ^{:key "ur-lista"}
                [filtered-listing {:format :nimi :haku :nimi
                                   :selection valittu-urakka
                                   :on-select valitse-urakka}
                 urakkalista]]])
          
          ;; Urakka valittu, tähän kaikki urakan komponentit
          [:div "hei, ihan urakkakin valittuna? hieno juttu! ei täällä vielä mitään tosin ole"])))]
    [:div#kartta-container.col-sm-8
     [kartta]]
    ])
           
