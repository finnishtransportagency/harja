(ns harja.views.kartta
  "Harjan kartta."
  (:require [reagent.core :refer [atom] :as reagent]

            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.openlayers :refer [openlayers] :as openlayers]
            [harja.asiakas.tapahtumat :as t]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.views.kartta.tasot :as tasot]
            ;[harja.]
            [cljs.core.async :refer [timeout <! >!] :as async]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as tapahtumat]
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

(defonce urakka-kuuntelija
  (t/kuuntele! :urakka-valittu
               #(openlayers/hide-popup!)))

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
    [:span.kartan-kontrollit.kartan-koko-kontrollit {:class (when (or @nav/tarvitaanko-tai-onko-pakotettu-nakyviin?
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

(def kartan-yleiset-kontrollit-sisalto (atom nil))

(def keskita-kartta-pisteeseen openlayers/keskita-kartta-pisteeseen!)
(def keskita-kartta-alueeseen! openlayers/keskita-kartta-alueeseen!)


(defn kartan-yleiset-kontrollit
  "Kartan yleiset kontrollit -komponentti, johon voidaan antaa mitä tahansa sisältöä, jota tietyssä näkymässä tarvitaan"
  []
  (let [sisalto @kartan-yleiset-kontrollit-sisalto]
    [:span.kartan-kontrollit.kartan-yleiset-kontrollit sisalto]))

(defn aseta-yleiset-kontrollit [uusi-sisalto]
  (reset! kartan-yleiset-kontrollit-sisalto uusi-sisalto))

(defn tyhjenna-yleiset-kontrollit []
  (reset! kartan-yleiset-kontrollit-sisalto nil))

(defn nayta-popup!
  "Näyttää popup sisällön kartalla tietyssä sijainnissa. Sijainti on vektori [lat lng], 
joka kertoo karttakoordinaatit. Sisältö annetaan sisalto-hiccup muodossa ja se renderöidään
HTML merkkijonoksi reagent render-to-string funktiolla (eikä siis ole täysiverinen komponentti)"
  [sijainti sisalto-hiccup]
  (openlayers/show-popup! sijainti sisalto-hiccup))

(defn poista-popup! []
  (openlayers/hide-popup!))

(defonce poista-popup-kun-tasot-muuttuvat
  (tapahtumat/kuuntele! :karttatasot-muuttuneet
                        (fn [_]
                          (poista-popup!))))
   

(def aseta-klik-kasittelija! openlayers/aseta-klik-kasittelija!)
(def poista-klik-kasittelija! openlayers/poista-klik-kasittelija!)
(def aseta-hover-kasittelija! openlayers/aseta-hover-kasittelija!)
(def poista-hover-kasittelija! openlayers/poista-hover-kasittelija!)
(def aseta-kursori! openlayers/aseta-kursori!)

(defn kaappaa-hiiri
  "Muuttaa kartan toiminnallisuutta siten, että hover ja click eventit annetaan datana annettuun kanavaan.
Palauttaa funktion, jolla kaappaamisen voi lopettaa. Tapahtumat ovat vektori, jossa on kaksi elementtiä:
tyyppi ja sijainti. Kun kaappaaminen lopetetaan, suljetaan myös annettu kanava."
  [kanava]
  (let [kasittelija #(go (>! kanava %))]
    (aseta-klik-kasittelija! kasittelija)
    (aseta-hover-kasittelija! kasittelija)

    #(do (poista-klik-kasittelija!)
         (poista-hover-kasittelija!)
         (async/close! kanava))))

  
(defn- paivita-extent [_ newextent]
  (reset! nav/kartalla-nakyva-alue {:xmin (aget newextent 0)
                                    :ymin (aget newextent 1)
                                    :xmax (aget newextent 2)
                                    :ymax (aget newextent 3)}))

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
     {:id "kartta"
      :width (if (= koko :S) "160px" "100%")
      :height (if (= koko :S) "150px"
                              (max (int (* 0.90 (- kork 150))) 350)) ;;"100%" ;; set width/height as CSS units, must set height as pixels!
      :style (when (= koko :S)
               {:display "none"})
      :view kartta-sijainti
      :zoom zoom-taso
      :selection nav/valittu-hallintayksikko
      :on-zoom paivita-extent
      :on-drag (fn [item event]
                 (paivita-extent item event)
                 (t/julkaise! {:aihe :karttaa-vedetty}))
      :on-mount (fn [initialextent] (paivita-extent nil initialextent))
      :on-click (fn [at] (t/julkaise! {:aihe :tyhja-click :klikkaus-koordinaatit at}))
      :on-select (fn [item event]
                   (let [item (assoc item :klikkaus-koordinaatit (js->clj (.-coordinate event)))]
                     (condp = (:type item)
                       :hy (when-not (= (:id item) (:id @nav/valittu-hallintayksikko))
                             (nav/valitse-hallintayksikko item))
                       :ur (when-not (= (:id item) (:id @nav/valittu-urakka))
                             (t/julkaise! (assoc item :aihe :urakka-klikattu)))
                       (t/julkaise! (assoc item :aihe (keyword (str (name (:type item)) "-klikattu")))))))
      :tooltip-fn (fn [geom]
                    (and geom
                         [:div {:class (name (:type geom))} (or (:nimi geom) (:siltanimi geom))]))
      :geometries
                   (concat (cond
                             (and (= :tilannekuva @nav/sivu) (nil? v-hal))
                             nil

                             (and (= :tilannekuva @nav/sivu) (nil? @nav/valittu-urakka))
                             [(assoc v-hal :valittu true)]

                             (and (= :tilannekuva @nav/sivu) @nav/valittu-urakka)
                             [(assoc @nav/valittu-urakka :valittu true)]

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
                     (when-let [{:keys [stroke] :as alue} (:alue piirrettava)]
                       (when (map? alue)
                         (assoc alue
                           :fill (if (:valittu piirrettava) false true)
                           :stroke (if stroke
                                     stroke
                                     (when (or (:valittu piirrettava)
                                               (= :silta (:type piirrettava)))
                                       {:width 3}))
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
   [kartan-yleiset-kontrollit]
   [kartta-openlayers]])
