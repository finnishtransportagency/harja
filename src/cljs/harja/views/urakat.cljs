(ns harja.views.urakat
  "Harjan näkymä, jossa näytetään karttaa sekä kontekstisidonnaiset asiat."
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :as async :refer [chan <! >!]]
             
            [harja.ui.listings :refer [filtered-listing]]
            [harja.ui.leaflet :refer [leaflet]]
            
            [harja.tiedot.hallintayksikot :as hal]

            [harja.asiakas.tapahtumat :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(def valittu-hallintayksikko "Tällä hetkellä valittu hallintayksikkö (tai nil)" (atom nil))

(def urakkalista "Hallintayksikön urakat" (atom []))

(def valittu-urakka "Tällä hetkellä valittu urakka (hoidon alueurakka / ylläpidon urakka) tai nil" (atom nil))

(def kartta-ch "Karttakomponentin käskyttämisen komentokanava" (atom nil))

(t/kuuntele! :hallintayksikko-valittu
             (fn [{:keys [nimi]}]
               (.log js/console "Hallintayksikkö vaihdettu!")
               ;; PENDING: hae palvelimelta urakat
               (reset! urakkalista
                       [{:nimi (str nimi " alueurakka 05-10")}
                        {:nimi (str nimi " alueurakka 10-15")}])))

(defn valitse-hallintayksikko [yks]
  (reset! valittu-hallintayksikko yks)
  (reset! valittu-urakka nil)
  (if yks
    (t/julkaise! (assoc yks :aihe :hallintayksikko-valittu))
    (t/julkaise! {:aihe :hallintayksikkovalinta-poistettu})))

(defn murupolku
  "Näyttää tämänhetkiset valinnat murupolkuna"
  []
  (let [hallintayksikon-valinta (atom false)]
    (fn []
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
             [:li [:a {:href "#" :on-click #(do (reset! hallintayksikon-valinta false)
                                                (valitse-hallintayksikko muu-yksikko))} (:nimi muu-yksikko)]])]
          
          ])
       (when @valittu-urakka
         [:li [:a {:href "#"}
               (:nimi @valittu-urakka)]])])))



(def view-position (atom [65.1 25.2]))
(def zoom-level (atom 8))

;; Joitain värejä... voi keksiä paremmat tai "oikeat", jos sellaiset on tiedossa
(def +varit+ ["#E04836" "#F39D41" "#8D5924" "#5696BC" "#2F5168" "wheat" "teal"])


(defn kartta []
  (let [hals @hal/hallintayksikot]
    [leaflet {:id "kartta"
              :width "100%" :height "750px" ;; set width/height as CSS units, must set height as pixels!
              :view view-position           ;; map center position
              :zoom zoom-level              ;; map zoom level
              :selection valittu-hallintayksikko
              :on-select valitse-hallintayksikko
              :geometries (if-let [valittu @valittu-hallintayksikko]
                            [(assoc valittu :valittu true)]
                            hals)
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
    (when-not @valittu-hallintayksikko
      [:span
       [:h5.haku-otsikko "Hae alueurakka kartalta tai listasta"]
       [:div [filtered-listing {:format :nimi :haku :nimi
                                :selection valittu-hallintayksikko
                                :on-select valitse-hallintayksikko}
              hal/hallintayksikot]]])]
   [:div#kartta-container.col-sm-8
    [kartta]]
   ])
           
