(ns harja.views.urakat
  "Harjan näkymä, jossa näytetään karttaa sekä kontekstisidonnaiset asiat."
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :as async :refer [chan <! >!]]
            [bootstrap :as bs]
            [harja.ui.listings :refer [filtered-listing]]
            [harja.ui.leaflet :refer [leaflet] :as leaflet]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija]]
            
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
  (reset! valittu-urakka ur)
  (if ur
    (t/julkaise! (assoc ur :aihe :urakka-valittu))
    (t/julkaise! {:aihe :urakkavalinta-poistettu})))


(defn murupolku
  "Näyttää tämänhetkiset valinnat murupolkuna"
  []
  (kuuntelija

   {:hallintayksikon-valinta (atom false)
    :urakan-valinta (atom false)}
   

   (fn [this]
     (let [{:keys [hallintayksikon-valinta urakan-valinta]} (reagent/state this)]
       [:ol.breadcrumb
        [:li [:a {:href "#" :on-click #(valitse-hallintayksikko nil)}
              "Koko Suomi"]]
        (when-let [valittu @valittu-hallintayksikko]
          [:li.dropdown {:class (when @hallintayksikon-valinta "open")}
           [:a {:href "#" 
                :on-click #(valitse-hallintayksikko valittu)}
            (:nimi valittu) " "]
           [:button.btn.btn-default.btn-xs.dropdown-toggle {:href "#" :on-click #(swap! hallintayksikon-valinta not)}
            [:span.caret]]
                      
           ;; Alasvetovalikko yksikön nopeaa vaihtamista varten
           [:ul.dropdown-menu {:role "menu"}
            (for [muu-yksikko (filter #(not= % valittu) @hal/hallintayksikot)]
              ^{:key (str "hy-" (:id muu-yksikko))}
              [:li [:a {:href "#" :on-click #(do (reset! hallintayksikon-valinta false)
                                                 (valitse-hallintayksikko muu-yksikko))} (:nimi muu-yksikko)]])]])
        (when-let [valittu @valittu-urakka]
          [:li.dropdown {:class (when @urakan-valinta "open")}
           [:a {:href "#"
                :on-click #(valitse-urakka valittu)}
            (:nimi valittu) " "]
           [:button.btn.btn-default.btn-xs.dropdown-toggle {:on-click #(swap! urakan-valinta not)}
            [:span.caret]]

           ;; Alasvetovalikko urakan nopeaa vaihtamista varten
           [:ul.dropdown-menu {:role "menu"}
            (for [muu-urakka (filter #(not= % valittu) @urakkalista)]
              ^{:key (str "ur-" (:id muu-urakka))}
              [:li [:a {:href "#" :on-click #(valitse-urakka muu-urakka)} (:nimi muu-urakka)]])]])]))

   ;; Jos hallintayksikkö valitaan, piilota hallintayksikkö dropdown
   [:hallintayksikko-valittu :hallintayksikkovalinta-poistettu]
   #(reset! (-> % reagent/state :hallintayksikon-valinta) false)

   ;; Jos urakanvalinta tapahtuu, piilota urakka dropdown
   [:urakka-valittu :urakkavalinta-poistettu]
   #(reset! (-> % reagent/state :urakan-valinta) false)))


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


(defn kartta []
  (let [hals @hal/hallintayksikot
        v-hal @valittu-hallintayksikko]
    [leaflet {:id "kartta"
              :width "100%" :height "750px" ;; set width/height as CSS units, must set height as pixels!
              :view kartta-sijainti
              :zoom zoom-taso
              :selection valittu-hallintayksikko
              :on-click (fn [at] (.log js/console "CLICK: " (pr-str at)))
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
                           (vec (concat [(assoc v-hal
                                           :valittu true
                                           :leaflet/fit-bounds true)]
                                        @urakkalista))
                           
                           ;; Valittu urakka, mitä näytetään?
                           :default [(assoc @valittu-urakka
                                       :valittu true
                                       :leaflet/fit-bounds true)])
              
              :geometry-fn (fn [hy]
                             (when-let [alue (:alue hy)]
                               (assoc alue
                                 :type (if (:valitty hy) :line (:type alue)) ;;{:type (if (:valittu hy) :line :polygon)
                                 :harja.ui.leaflet/fit-bounds (:valittu hy) ;; kerro leafletille, että siirtyy valittuun
                                 :color (nth +varit+ (mod (hash (:nimi hy)) (count +varit+))))))

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
   [:div#sidebar-left.col-sm-6
    (let [v-hal @valittu-hallintayksikko
          v-ur @valittu-urakka]
      (if-not v-hal
        ;; Hallintayksikköä ei ole valittu: näytetään lista hallintayksiköistä
        [:span
         [:h5.haku-otsikko "Hae hallintayksikkö kartalta tai listasta"]
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
