(ns harja.views.kartta
  "Harjan kartta."
  (:require [reagent.core :refer [atom] :as reagent]
            
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.leaflet :refer [leaflet] :as leaflet]
            [harja.asiakas.tapahtumat :as t]))
            

(def kartta-ch "Karttakomponentin käskyttämisen komentokanava" (atom nil))
;; PENDING: suurin piirtien hyvä kohta "koko suomen" sijainniksi ja zoom-tasoksi, saa tarkentaa
(def +koko-suomi-sijainti+ [65.1 25.2])
(def +koko-suomi-zoom-taso+ 5)

(defonce kartta-sijainti (atom +koko-suomi-sijainti+))
(defonce zoom-taso (atom +koko-suomi-zoom-taso+))

(defonce kartta-kuuntelija 
  (t/kuuntele! :hallintayksikkovalinta-poistettu
               #(do (reset! kartta-sijainti +koko-suomi-sijainti+)
                    (reset! zoom-taso +koko-suomi-zoom-taso+))))
                        
(defn kartta []
  (let [hals @hal/hallintayksikot
        v-hal @nav/valittu-hallintayksikko]
    [leaflet {:id "kartta"
              :width "100%" :height "100%" ;; set width/height as CSS units, must set height as pixels!
              :view kartta-sijainti
              :zoom zoom-taso
              :selection nav/valittu-hallintayksikko
              :on-click (fn [at] (.log js/console "CLICK: " (pr-str at)))
              :on-select (fn [item]
                           (condp = (:type item)
                             :hy (nav/valitse-hallintayksikko item)
                             :ur (nav/valitse-urakka item)))
              :geometries (cond
                           ;; Ei valittua hallintayksikköä, näytetään hallintayksiköt
                           (nil? v-hal)
                           hals

                           ;; Ei valittua urakkaa, näytetään valittu hallintayksikkö ja sen urakat
                           (nil? @nav/valittu-urakka)
                           (vec (concat [(assoc v-hal
                                           :valittu true
                                           :leaflet/fit-bounds true)]
                                        @nav/urakkalista))
                           
                           ;; Valittu urakka, mitä näytetään?
                           :default [(assoc @nav/valittu-urakka
                                       :valittu true
                                       :leaflet/fit-bounds true)])
              
              :geometry-fn (fn [hy]
                             (when-let [alue (:alue hy)]
                               (assoc alue
                                 :type (if (:valitty hy) :line (:type alue)) ;;{:type (if (:valittu hy) :line :polygon)
                                 :harja.ui.leaflet/fit-bounds (:valittu hy) ;; kerro leafletille, että siirtyy valittuun
                                 :color (nth +varit+ (mod (hash (:nimi hy)) (count +varit+))))))

              ;; PENDING: tilalle MML kartat, kunhan ne saadaan 
              :layers [;;{:type :wms
                        ;;:url (wms-url)
                        ;;:layers ["yleiskartta_1m"]
                        ;;:format "image/png"
                        ;;:transparent true
                        ;;:srs "EPSG:3067"
                        ;;:attribution "Maanmittauslaitoksen Karttakuvapalvelu (WMS)"}]
                        
                       {:type :tile
                        :url "http://{s}.tile.osm.org/{z}/{x}/{y}.png"
                        :attribution "&copy; <a href=\"http://osm.org/copyright\">OpenStreetMap</a> contributors"}]

              }
     ]))
