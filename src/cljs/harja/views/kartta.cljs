(ns harja.views.kartta
  "Harjan kartta."
  (:require [reagent.core :refer [atom] :as reagent]

            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.tyokoneseuranta :as tyokoneenseuranta]
            [harja.ui.openlayers :refer [openlayers] :as openlayers]
            [harja.asiakas.tapahtumat :as t]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.views.kartta.tasot :as tasot]

            [cljs.core.async :refer [timeout <!]]
            [harja.asiakas.kommunikaatio :as k]
            )

  (:require-macros [reagent.ratom :refer [run!]]
                   [cljs.core.async.macros :refer [go]]))



(def kartta-ch "Karttakomponentin käskyttämisen komentokanava" (atom nil))
;; PENDING: suurin piirtien hyvä kohta "koko suomen" sijainniksi ja zoom-tasoksi, saa tarkentaa
(def +koko-suomi-sijainti+ [431704.1 7211111])
(def +koko-suomi-zoom-taso+ 6)

(defonce kartta-sijainti (atom +koko-suomi-sijainti+))
(defonce zoom-taso (atom +koko-suomi-zoom-taso+))

(defonce kartta-kuuntelija
         (t/kuuntele! :hallintayksikkovalinta-poistettu
                      #(do (reset! kartta-sijainti +koko-suomi-sijainti+)
                           (reset! zoom-taso +koko-suomi-zoom-taso+))))

#_(defonce kartan-alueen-asetus
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
                   (openlayers/fit-bounds! urakka)))

             (not (nil? hal))
             (do
               (log "HAL vaihtui, zoomataan siihen: " (pr-str (dissoc hal :alue)))
               (go (<! (timeout 500)) ;; leaflet vaatii hieman armon aikaa ennen kuin zoomataan
                   (openlayers/fit-bounds! (assoc hal :valittu true)))))))))

;; Joitain värejä... voi keksiä paremmat tai "oikeat", jos sellaiset on tiedossa
(def +varit+ ["#E04836" "#F39D41" "#8D5924" "#5696BC" "#2F5168" "wheat" "teal"])

(defonce kartan-koon-paivitys
         (run! (do @nav/kartan-koko
                   @yleiset/ikkunan-koko
                   (openlayers/invalidate-size!))))

(defn kartan-koko-kontrollit
  []
  (let [koko @nav/kartan-koko
        sivu @nav/sivu]
    [:span.kartan-koko-kontrollit {:class (when (or @nav/tarvitaanko-tai-onko-pakotettu-nakyviin?
                                                    (= sivu :tilannekuva)) "hide")}
     [:span.livicon-compress.kartta-kontrolli {:class    (when (= koko :S) "hide")
                                               :on-click #(nav/vaihda-kartan-koko! (case koko
                                                                                     :S :S
                                                                                     :M :S
                                                                                     :L :M))}]
     [:span.livicon-expand.kartta-kontrolli {:class    (case koko
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
  (openlayers/show-popup! sijainti sisalto-hiccup))


(defn kartta-openlayers []
  (let [hals @hal/hallintayksikot
        v-hal @nav/valittu-hallintayksikko
        koko @nav/kartan-koko
        kork @yleiset/korkeus
        lev @yleiset/leveys
        koko (if-not (empty? @nav/tarvitsen-karttaa)
               :M
               koko)]
    [openlayers

     {:id          "kartta"
      :width       (if (= koko :S) "160px" "100%")
      :height      (if (= koko :S) "150px"
                                   (max (int (* 0.90 (- kork 150))) 350)) ;;"100%" ;; set width/height as CSS units, must set height as pixels!
      :style       (when (= koko :S)
                     {:display "none"})
      :view        kartta-sijainti
      :zoom        zoom-taso
      :selection   nav/valittu-hallintayksikko
      :on-drag     (fn [_ newextent]
                     ; (log "Move, uusi extent: " newextent)
                     (reset! tyokoneenseuranta/valittu-alue {:xmin (aget newextent 0)
                                                             :ymin (aget newextent 1)
                                                             :xmax (aget newextent 2)
                                                             :ymax (aget newextent 3)}))

      ;:on-click    (fn [at] (.log js/console "CLICK: " (pr-str at)))
      :on-select   (fn [item event]
                     (let [item (assoc item :klikkaus-koordinaatit (js->clj (.-coordinate event)))]
                       #_(log "TÄLLAISEN VALITSIT :: " (pr-str (dissoc item :alue)))
                       (condp = (:type item)
                         :hy (when-not (= (:id item) (:id @nav/valittu-hallintayksikko))
                               (nav/valitse-hallintayksikko item))
                         :ur (when-not (= (:id item) (:id @nav/valittu-urakka))
                               (t/julkaise! (assoc item :aihe :urakka-klikattu)))
                         (t/julkaise! (assoc item :aihe (keyword (str (name (:type item)) "-klikattu")))))))
      :tooltip-fn  (fn [geom]
                     (and geom
                          [:div {:class (name (:type geom))} (or (:nimi geom) (:siltanimi geom))]))
      :geometries
                   (concat (cond
                             ;; Ei valittua hallintayksikköä, näytetään hallintayksiköt
                             (nil? v-hal)
                             hals

                             ;; Ei valittua urakkaa, näytetään valittu hallintayksikkö ja sen urakat
                             (nil? @nav/valittu-urakka)
                             (vec (concat [(assoc v-hal
                                             :valittu true)]
                                          @nav/urakat-kartalla))

                             ;; Valittu urakka, mitä näytetään?
                             :default [(assoc @nav/valittu-urakka
                                         :valittu true
                                         :harja.ui.openlayers/fit-bounds true)])
                           @tasot/geometriat)

      :geometry-fn (fn [piirrettava]
                     (when-let [alue (:alue piirrettava)]
                       (when (map? alue)
                         (assoc alue
                           :fill (if (:valittu piirrettava) false true)
                           :stroke (when (or (:valittu piirrettava)
                                             (= :silta (:type piirrettava)))
                                     {:width 3})
                           :harja.ui.openlayers/fit-bounds (:valittu piirrettava) ;; kerro kartalle, että siirtyy valittuun
                           :color (or (:color alue)
                                      (nth +varit+ (mod (hash (:nimi piirrettava)) (count +varit+))))
                           :zindex (or (:zindex alue) (case (:type piirrettava)
                                                        :hy 0
                                                        :ur 1
                                                        :pohjavesialueet 2
                                                        :sillat 3
                                                        4))
                           ;;:marker (= :silta (:type hy))
                           ))))

      :layers      [{:type  :mml
                     :url   (str (k/wmts-polku) "maasto/wmts")
                     :layer "taustakartta"}]

      }]))

(defn kartta []
  [:span
   [kartan-koko-kontrollit]
   [kartta-openlayers]])
