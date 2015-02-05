(ns harja.views.urakat
  "Harjan näkymä, jossa näytetään karttaa sekä kontekstisidonnaiset asiat."
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :as async :refer [chan <! >!]]
            [bootstrap :as bs]
            [harja.ui.listings :refer [filtered-listing]]
            [harja.ui.leaflet :refer [leaflet] :as leaflet]
            [harja.ui.yleiset :as yleiset]
            
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.urakat :as ur]
            
            [harja.asiakas.tapahtumat :as t]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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

;; Joitain värejä... voi keksiä paremmat tai "oikeat", jos sellaiset on tiedossa
(def +varit+ ["#E04836" "#F39D41" "#8D5924" "#5696BC" "#2F5168" "wheat" "teal"])

(defn wms-url
  "Määrittelee WMS osoitteen, suhteellisena sovelluksen osoitteeseen."
  []
  (let [l (.-location js/document)
        port (.-port l)]
    (str (.-protocol l)
         "//"
         (.-hostname l)
         (when-not (or (= port "80")
                       (= port "443"))
           (str ":" port))
         "/wms/rasteriaineistot/image?")))

(defn kartta []
  (let [hals @hal/hallintayksikot
        v-hal @nav/valittu-hallintayksikko]
    [leaflet {:id "kartta"
              :width "100%" :height "750px" ;; set width/height as CSS units, must set height as pixels!
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



(defn urakat
  "Harjan karttasivu."
  []
  [:span
   [:div#sidebar-left.col-sm-6
    (let [v-hal @nav/valittu-hallintayksikko
          v-ur @nav/valittu-urakka]
      (if-not v-hal
        ;; Hallintayksikköä ei ole valittu: näytetään lista hallintayksiköistä
        [:span
         [:h5.haku-otsikko "Hae hallintayksikkö kartalta tai listasta"]
         [:div
          ^{:key "hy-lista"}
          [filtered-listing {:format :nimi :haku :nimi
                             :selection nav/valittu-hallintayksikko
                             :on-select nav/valitse-hallintayksikko}
           hal/hallintayksikot]]]
        
        ;; Hallintayksikko on valittu, mutta urakkaa ei: näytetään luettelossa urakat
        (if-not v-ur
          ;;(let [urakat (ur/hallintayksikon-urakat v-hal)]
            (if (nil? @nav/urakkalista)
              [yleiset/ajax-loader "Urakoita haetaan..."]
              [:span
               [:h5.haku-otsikko "Hae urakka kartalta tai listasta"]
               [:div
                ^{:key "ur-lista"}
                [filtered-listing {:format :nimi :haku :nimi
                                   :selection nav/valittu-urakka
                                   :on-select nav/valitse-urakka}
                 nav/urakkalista]]])
          
            ;; Urakka valittu, tähän kaikki urakan komponentit
              [:span
   [:h3 "Raportit"]
   [bs/dropdown-panel {} "Työ- ja toteumaraportit" 
    [:div "hei, ihan urakkakin valittuna? hieno juttu! ei täällä vielä mitään tosin ole"]]
   [bs/dropdown-panel {} "Laadunseuranta" "ei ole mitään"]
   [bs/dropdown-panel {} "Poikkeamaraportit" "ei poikkeamia"]
   
   
   [:h3 "Suunnittelu"]
   [bs/dropdown-panel {} "Kustannussuunnitelma: kokonaishintaiset työt" "ei vielä"]
   [bs/dropdown-panel {} "Kustannussuunnitelma: yksikköhintaiset työt" "ei vielä"]
   [bs/dropdown-panel {} "Materiaalisuunnitelma" "ei vielä"]
   
   [:h3 "Toteumat"]
   [bs/dropdown-panel {} "Toteutuneet työt" "ei vielä"]
   [bs/dropdown-panel {} "Toteutuneet materiaalit" "ei vielä"]
   
   ]
            
             
               
            )))]
    [:div#kartta-container.col-sm-6
     [kartta]]
    ])
           
(comment
  [bs/tabs {}
             "Raportit"
             ^{:key "raportit"}
             [:div 
              [bs/dropdown-panel {} "Työ- ja toteumaraportit" 
               [:div "hei, ihan urakkakin valittuna? hieno juttu! ei täällä vielä mitään tosin ole"]]
              [bs/dropdown-panel {} "Laadunseuranta" "ei ole mitään"]
              [bs/dropdown-panel {} "Poikkeamaraportit" "ei poikkeamia"]]
             "Suunnittelu"
             ^{:key "suunnittelu"}
             [:div 
              [bs/dropdown-panel {} "Kustannussuunnitelma: kokonaishintaiset työt" "ei vielä"]
              [bs/dropdown-panel {} "Kustannussuunnitelma: yksikköhintaiset työt" "ei vielä"]
              [bs/dropdown-panel {} "Materiaalisuunnitelma" "ei vielä"]]

             "Toteumat"
             ^{:key "toteumat"}
             [:div
              [bs/dropdown-panel {} "Toteutuneet työt" "ei vielä"]
              [bs/dropdown-panel {} "Toteutuneet materiaalit" "ei vielä"]]]
)
