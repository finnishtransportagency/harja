(ns harja.views.kartta
  "Harjan kartta."
  (:require [reagent.core :refer [atom] :as reagent]
            
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.leaflet :refer [leaflet] :as leaflet]
            [harja.asiakas.tapahtumat :as t]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.views.kartta.tasot :as tasot]

            [cljs.core.async :refer [timeout <!]]
            )

  (:require-macros [reagent.ratom :refer [run!]]
                   [cljs.core.async.macros :refer [go]]))
            


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

(defonce kartan-alueen-asetus
  (run! (let [hal @nav/valittu-hallintayksikko
              urakka @nav/valittu-urakka
              koko @nav/kartan-koko]
          (log "kartan koko, hallintayksikkö tai urakka vaihtui, koko:" koko)
          (when (not= :S koko)
            (log "Kartan koko: " koko)
            (cond
             (not (nil? urakka))
             (do
               (log "Urakka olemassa, zoomataan siihen: " (pr-str (dissoc urakka :alue)))
               (go (<! (timeout 500))
                   (leaflet/fit-bounds! urakka)))

             (not (nil? hal))
             (do
               (log "HAL vaihtui, zoomataan siihen: " (pr-str (dissoc hal :alue)))
               (go (<! (timeout 500)) ;; leaflet vaatii hieman armon aikaa ennen kuin zoomataan
                   (leaflet/fit-bounds! (assoc hal :valittu true)))))))))

;; Joitain värejä... voi keksiä paremmat tai "oikeat", jos sellaiset on tiedossa
(def +varit+ ["#E04836" "#F39D41" "#8D5924" "#5696BC" "#2F5168" "wheat" "teal"])


(defn kartan-koko-kontrollit
  []
  (let [koko @nav/kartan-koko
        sivu @nav/sivu]
    [:span.kartan-koko-kontrollit {:class (when (or (not (empty? @nav/tarvitsen-karttaa))
                                                    (= sivu :tilannekuva)) "hide")}
     [:div.ikoni-pienenna {:class (when (= koko :S) "hide")
                           :on-click #(nav/vaihda-kartan-koko! (case koko
                                                                 :S :S
                                                                 :M :S
                                                                 :L :M))}]
     [:div.ikoni-suurenna {:class (case koko
                                    :L "hide"
                                    :M ""
                                    :S "kulmassa-kelluva"
                                    :hidden "")
                           :on-click #(nav/vaihda-kartan-koko!
                                       (case koko
                                         :hidden :S
                                         :S :M
                                         :M :L
                                         :L :L))}]]))

(defn nayta-popup!
  "Näyttää popup sisällön kartalla tietyssä sijainnissa. Sijainti on vektori [lat lng], 
joka kertoo karttakoordinaatit. Sisältö annetaan sisalto-hiccup muodossa ja se renderöidään
HTML merkkijonoksi reagent render-to-string funktiolla (eikä siis ole täysiverinen komponentti)"
  [sijainti sisalto-hiccup]
  (leaflet/show-popup! sijainti sisalto-hiccup))

  
(defn kartta-leaflet []
  (let [hals @hal/hallintayksikot
        v-hal @nav/valittu-hallintayksikko
        koko @nav/kartan-koko
        kork @yleiset/korkeus
        lev @yleiset/leveys
        koko (if-not (empty? @nav/tarvitsen-karttaa)
               :M
               koko)]
    [leaflet {:id "kartta"
              :width (if (= koko :S) "160px" "100%")
              :height (if (= koko :S) "150px"
                          (max (int (* 0.90 (- kork 150))) 350)) ;;"100%" ;; set width/height as CSS units, must set height as pixels!
              :style (when (= koko :S)
                       {:display "none"})
              :view kartta-sijainti
              :zoom zoom-taso
              :selection nav/valittu-hallintayksikko
              :on-click (fn [at] (.log js/console "CLICK: " (pr-str at)))
              :on-select (fn [item event]
                           (.log js/console "kartan valinta event: " event)
                           (let [latlng (.-latlng event)
                                 item (assoc item :klikkaus-koordinaatit [(.-lat latlng) (.-lng latlng)])]
                             (condp = (:type item)
                               :hy (nav/valitse-hallintayksikko item)
                               :ur (t/julkaise! (assoc item :aihe :urakka-klikattu))
                               (t/julkaise! (assoc item :aihe (keyword (str (name (:type item)) "-klikattu"))))
                               )))
              :tooltip-fn (fn [geom]
                            [:div {:class (name (:type geom))} (:nimi geom)])
              :geometries
              (concat (cond
                       ;; Ei valittua hallintayksikköä, näytetään hallintayksiköt
                       (nil? v-hal)
                       hals

                       ;; Ei valittua urakkaa, näytetään valittu hallintayksikkö ja sen urakat
                       (nil? @nav/valittu-urakka)
                       (vec (concat [(assoc v-hal
                                       :valittu true)]
                                    @nav/suodatettu-urakkalista))
                           
                       ;; Valittu urakka, mitä näytetään?
                       :default [(assoc @nav/valittu-urakka
                                   :valittu true
                                   :leaflet/fit-bounds true)])
                      @tasot/geometriat)
              
              :geometry-fn (fn [hy]
                             (when-let [alue (:alue hy)]
                               (when (map? alue)
                                 (assoc alue
                                   :fill (if (:valittu hy) false true)
                                   :harja.ui.leaflet/fit-bounds (:valittu hy) ;; kerro leafletille, että siirtyy valittuun
                                   :color (or (:color alue)
                                              (nth +varit+ (mod (hash (:nimi hy)) (count +varit+))))))))

              ;; PENDING: tilalle MML kartat, kunhan ne saadaan 
              :layers [ ;;{:type :wms
                       ;;:url (wms-url)
                       ;;:layers ["yleiskartta_1m"]
                       ;;:format "image/png"
                       ;;:transparent true
                       ;;:srs "EPSG:3067"
                       ;;:attribution "Maanmittauslaitoksen Karttakuvapalvelu (WMS)"}]
                        
                       {:type :tile
                        :url "http://{s}.tile.osm.org/{z}/{x}/{y}.png"
                        :attribution "&copy; <a href=\"http://osm.org/copyright\">OpenStreetMap</a> contributors"}]

              }]))

(defn kartta []
  [:span
   [kartan-koko-kontrollit]
   [kartta-leaflet]])
