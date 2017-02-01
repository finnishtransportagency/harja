(ns harja-laadunseuranta.ui.kartta
  (:require [reagent.core :as reagent :refer [atom]]
            [ol]
            [ol.Map]
            [ol.Feature]
            [ol.layer.Tile]
            [ol.layer.Vector]
            [ol.geom.Point]
            [ol.geom.LineString]
            [ol.source.WMTS]
            [ol.style.Style]
            [ol.style.Stroke]
            [ol.style.Icon]
            [ol.style.Text]
            [ol.animation]
            [ol.control :as ol-control]
            [ol.interaction :as ol-interaction]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.tiedot.projektiot :as projektiot]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.math :as math])
  (:require-macros [reagent.ratom :refer [run!]]
                   [devcards.core :refer [defcard]]))

(defn- lisaa-kirjausikoni [teksti]
  (swap! s/kirjauspisteet
         conj (assoc (select-keys (:nykyinen @s/sijainti) [:lat :lon])
                :label teksti)))

(defn- wmts-source [url layer]
  (ol.source.WMTS. #js {:attributions [(ol.Attribution. #js {:html "MML"})]
                        :url url
                        :layer layer
                        :matrixSet "ETRS-TM35FIN"
                        :format (if (= "ortokuva" layer) "image/jpeg" "image/png")
                        :projection projektiot/projektio
                        :tileGrid (ol.tilegrid.WMTS. (clj->js (projektiot/tilegrid 16)))
                        :style "default"
                        :wrapX true}))

(defn- wmts-source-taustakartta [url]
  (wmts-source url "taustakartta"))

(defn- wmts-source-kiinteistojaotus [url]
  (wmts-source url "kiinteistojaotus"))

(defn- wmts-source-ortokuva [url]
  (wmts-source url "ortokuva"))

(defn- tile-layer [source]
  (ol.layer.Tile.
    #js {;:preload asetukset/+preload-taso+
         :source source}))

(defn- tee-ikoni [suunta kuva]
  (ol.style.Icon. #js {:anchor #js [0.5 0.5]
                       :anchorXUnits "fraction"
                       :anchorYUnits "fraction"
                       :opacity 1
                       :rotation suunta
                       :src kuva}))

(defn- tee-offset-ikoni [offset kuva]
  (ol.style.Icon. #js {:anchor (clj->js offset)
                       :anchorXUnits "pixels"
                       :anchorYUnits "pixels"
                       :opacity 1
                       :rotation 0
                       :src kuva}))

(defn- nuoli-ikoni-tyyli [suunta]
  (ol.style.Style. #js {:image (tee-ikoni suunta kuvat/+autonuoli+)}))

(defn- reittiviivan-tyyli [leveys vari]
  (ol.style.Style. #js {:stroke (ol.style.Stroke. #js {:width leveys
                                                       :color vari})}))

(defn- havaintopisteen-teksti [havainto]
  (ol.style.Text. #js {:text (:label havainto)
                       :offsetX 0
                       :offsetY -30
                       :scale 2}))

(defn- kirjauspiste-ikoni-tyyli [havainto]
  (ol.style.Style. #js {:image (tee-offset-ikoni [24 48] kuvat/+havaintopiste+)
                        :text (havaintopisteen-teksti havainto)}))

(defn- tee-piste [piste]
  (ol.geom.Point. (clj->js (projektiot/latlon-vektoriksi piste))))

(defn- tee-viiva [pisteet]
  (ol.geom.LineString. (clj->js pisteet)))

(defn- laske-suunta [heading]
  (* (/ (+ heading asetukset/+heading-ikonikorjaus+) 360) (* 2 Math/PI)))

(defn- tee-piste-feature [coords]
  (doto (ol.Feature. #js {:geometry (tee-piste coords)})
    (.setStyle (nuoli-ikoni-tyyli (laske-suunta (:heading coords))))))

(defn- tee-viiva-feature [piste]
  (doto (ol.Feature. #js {:geometry (tee-viiva (:segmentti piste))})
    (.setStyle (reittiviivan-tyyli asetukset/+reittiviivan-leveys+ (:vari piste)))))

(defn- tee-viiva-featuret [pisteet]
  (mapv tee-viiva-feature pisteet))

(defn- tee-ikoni-feature [ikoni]
  (doto (ol.Feature. #js {:geometry (tee-piste ikoni)})
    (.setStyle (kirjauspiste-ikoni-tyyli ikoni))))

(defn- tee-ikoni-featuret [ikonit]
  (mapv tee-ikoni-feature ikonit))

(defn nakyma [{:keys [lat lon zoom]}]
  (ol/View. (clj->js {:center [lon lat]
                      :projection projektiot/projektio
                      :zoom (or zoom asetukset/+oletuszoom+)})))

(defn- luo-interaktiot []
  (ol-interaction/defaults #js {:mouseWheelZoom false
                                :dragPan false
                                :pinchRotate false
                                :pinchZoom false}))

(defn- tee-vektorilahde [features]
  (ol.source.Vector. #js {:features (clj->js features)}))

(defn- vector-layer [lahde]
  (ol.layer.Vector. #js {:source lahde}))

(defn- luo-kontrollit []
  (ol-control/defaults #js {:zoom true
                            :rotate false
                            :attribution false}))

(defn- luo-optiot [wmts-url wmts-url-kiinteistorajat wmts-url-ortokuva
                   sijainti kohde-elementti ajoneuvokerros reittikerros ikonikerros]
  {:layers [(tile-layer (wmts-source-taustakartta wmts-url))
            (tile-layer (wmts-source-ortokuva wmts-url-ortokuva))
            (tile-layer (wmts-source-kiinteistojaotus wmts-url-kiinteistorajat))
            ajoneuvokerros
            reittikerros
            ikonikerros]
   :view (nakyma {:lat 0
                  :lon 0
                  :zoom asetukset/+oletuszoom+})
   :interactions (luo-interaktiot)
   :controls (luo-kontrollit)
   :target kohde-elementti})

;; karttakomponentti

(defn kartta-render []
  [:div.map])

(defn- paivita-ajoneuvon-sijainti [kartta ajoneuvo ajoneuvokerros ajoneuvon-sijainti]
  (doto ajoneuvo
    (.setGeometry (tee-piste ajoneuvon-sijainti))
    (.setStyle (nuoli-ikoni-tyyli (laske-suunta (:heading ajoneuvon-sijainti)))))
  (.changed ajoneuvokerros)
  (.changed (.getView kartta)))

(defn- smooth-pan [old-center duration]
  (ol.animation.pan #js {:duration duration
                         :source old-center}))

(defn- paivita-kartan-keskipiste [kartta keskipiste]
  (let [view (.getView kartta)]
    ;; smooth panning aiheuttaa vektorilayereiden piirron viivästymisen pahimmillaan minuutteja
    ;; disabloidaan.
    ;;(.beforeRender kartta (smooth-pan (.getCenter view) 1000))
    (doto view
      (.setCenter (clj->js (projektiot/latlon-vektoriksi keskipiste)))
      (.changed))))

(defn- paivita-kartan-rotaatio [kartta rad]
  (let [view (.getView kartta)]
    (doto view
      (.setRotation rad)
      (.changed))))

(defn- paivita-ajettu-reitti [kartta ajettu-reitti reittikerros reittipisteet]
  (let [src (.getSource reittikerros)]
    (.clear src)
    (.addFeatures src (clj->js (tee-viiva-featuret reittipisteet))))
  ;(.setGeometry ajettu-reitti (tee-viiva reittipisteet))
  (.changed reittikerros)
  (.changed (.getView kartta)))

(defn- paivita-kirjausikonit [kartta kirjatut-pisteet ikonikerros pisteet]
  (let [src (.getSource ikonikerros)]
    (.clear src)
    (.addFeatures src (clj->js (tee-ikoni-featuret pisteet))))
  (.changed ikonikerros)
  (.changed (.getView kartta)))

(defn- etsi-dragpan [kartta]
  (first (filter #(instance? ol.interaction.DragPan %)
                 (-> kartta .getInteractions .getArray))))

(defn- kytke-dragpan [kartta enable]
  (if enable
    (when-not (etsi-dragpan kartta)
      (.addInteraction kartta (ol.interaction.DragPan.)))
    (when-let [dragpan (etsi-dragpan kartta)]
      (.removeInteraction kartta dragpan))))

(defn- kytke-kiinteistorajat [kartta enable]
  (.setVisible (aget (.getArray (.getLayers kartta)) 2) enable))

(defn- kytke-ortokuva [kartta enable]
  (.setVisible (aget (.getArray (.getLayers kartta)) 1) enable))

(defn- sijainti-ok? [{:keys [lat lon]}]
  (and (not= 0 lon)
       (not= 0 lat)))

(defn- siirra-kontrollit-ylapalkkiin
  "OpenLayers osaa mountata kontrollit vain karttaan, joten
   siirretään ne käsin yläpalkkiin"
  []
  (let [kontrollien-paikka (.getElementById js/document "karttakontrollit")
        kontrollit (-> (.getElementsByClassName js/document "ol-zoom")
                       (.item 0))]
    (when (and kontrollien-paikka kontrollit)
      (.appendChild kontrollien-paikka kontrollit))))

(defn kartta-did-mount [this wmts-url wmts-url-kiinteistorajat wmts-url-ortokuva keskipiste-atomi
                        ajoneuvon-sijainti-atomi reittipisteet-atomi kirjatut-pisteet-atomi optiot]
  (let [alustava-sijainti-saatu? (cljs.core/atom false)
        map-element (reagent/dom-node this)

        ajoneuvo (tee-piste-feature (:nykyinen @ajoneuvon-sijainti-atomi))
        ajettu-reitti (tee-viiva-featuret @reittipisteet-atomi)
        kirjatut-pisteet (tee-ikoni-featuret @kirjatut-pisteet-atomi)

        ajoneuvokerros (vector-layer (tee-vektorilahde [ajoneuvo]))
        reittikerros (vector-layer (tee-vektorilahde ajettu-reitti))
        ikonikerros (vector-layer (tee-vektorilahde kirjatut-pisteet))

        kartta (ol/Map. (clj->js (luo-optiot wmts-url wmts-url-kiinteistorajat wmts-url-ortokuva @keskipiste-atomi
                                             map-element ajoneuvokerros reittikerros ikonikerros)))]

    ;; instanssi talteen DOMiin testausta varten
    (set! (.-openlayers map-element) kartta)
    (siirra-kontrollit-ylapalkkiin)

    (run!
      (when (and (not @alustava-sijainti-saatu?) (sijainti-ok? @keskipiste-atomi))
        (paivita-kartan-keskipiste kartta @keskipiste-atomi)
        (reset! alustava-sijainti-saatu? true)))

    ;; reagoidaan kartan ja ajoneuvon sijainnin muutokseen
    (run!
      (if (:seuraa-sijaintia? @optiot)
        (do
          (kytke-dragpan kartta false)
          (paivita-kartan-keskipiste kartta @keskipiste-atomi)
          (let [sijainti-edellinen (projektiot/latlon-vektoriksi
                                     (:edellinen @ajoneuvon-sijainti-atomi))
                sijainti-nykyinen (projektiot/latlon-vektoriksi
                                    (:nykyinen @ajoneuvon-sijainti-atomi))]
            ;; Rotatoi kartta ajosuuntaan, mutta vain jos nopeus on riittävä, muuten
            ;; paikallaolo ja siitä aiheutuva GPS-kohina saa kartan levottomaksi
            (when (>= (math/pisteiden-etaisyys sijainti-edellinen sijainti-nykyinen) 8)
              (paivita-kartan-rotaatio kartta (- (math/pisteiden-kulma-radiaaneina
                                                   sijainti-edellinen
                                                   sijainti-nykyinen)
                                                 (/ Math/PI 2))))))
        (kytke-dragpan kartta true)))

    (run!
      (kytke-kiinteistorajat kartta (:nayta-kiinteistorajat? @optiot)))

    (run!
      (kytke-ortokuva kartta (:nayta-ortokuva? @optiot)))

    ;; reagoidaan ajoneuvon sijainnin muutokseen
    (run! (paivita-ajoneuvon-sijainti kartta ajoneuvo ajoneuvokerros (:nykyinen @ajoneuvon-sijainti-atomi)))

    ;; reagoidaan reittipisteiden muutokseen
    (run! (paivita-ajettu-reitti kartta ajettu-reitti reittikerros @reittipisteet-atomi))

    ;; reagoidaan merkintojen muutokseen
    (run! (paivita-kirjausikonit kartta kirjatut-pisteet ikonikerros @kirjatut-pisteet-atomi))))


(defn karttakomponentti [{:keys [wmts-url wmts-url-kiinteistorajat wmts-url-ortokuva sijainti-atomi
                                 ajoneuvon-sijainti-atomi reittipisteet-atomi kirjauspisteet-atomi optiot]}]
  (reagent/create-class {:reagent-render kartta-render
                         :component-did-mount
                         #(kartta-did-mount
                            %
                            wmts-url
                            wmts-url-kiinteistorajat
                            wmts-url-ortokuva
                            sijainti-atomi
                            ajoneuvon-sijainti-atomi
                            reittipisteet-atomi
                            kirjauspisteet-atomi
                            optiot)}))

(defn kartta []
  [:div
   [karttakomponentti
    {:wmts-url asetukset/+wmts-url+
     :wmts-url-kiinteistorajat asetukset/+wmts-url-kiinteistojaotus+
     :wmts-url-ortokuva asetukset/+wmts-url-ortokuva+
     :sijainti-atomi s/kartan-keskipiste
     :ajoneuvon-sijainti-atomi s/ajoneuvon-sijainti
     :reittipisteet-atomi s/reittipisteet
     :kirjauspisteet-atomi s/kirjauspisteet
     :optiot s/karttaoptiot}]
   [:div.kartan-kontrollit {:style (when @s/havaintolomake-auki?
                                     {:display "none"})}
    [:div#karttakontrollit] ;; OpenLayersin ikonit asetetaan tähän elementtiin erikseen
    [:div.kontrollinappi.ortokuva {:on-click #(swap! s/nayta-ortokuva? not)}
     [kuvat/svg-sprite "maasto-24"]]
    [:div.kontrollinappi.kiinteistorajat {:on-click #(swap! s/nayta-kiinteistorajat? not)}
     [kuvat/svg-sprite "kiinteistoraja-24"]]
    [:div.kontrollinappi.keskityspainike {:on-click #(do (swap! s/keskita-ajoneuvoon? not)
                                                         (swap! s/keskita-ajoneuvoon? not))}
     [kuvat/svg-sprite "tahtain-24"]]]])

;; devcards

(def test-sijainti (atom {:lon 428147
                          :lat 7208956
                          :heading 45}))

(def test-ikonit (atom [{:lat 7208942 :lon 428131
                         :label "0.45"}]))
(def testioptiot (atom {:seuraa-sijaintia? true}))

(def test-reittipisteet (atom [[[428131 7208942] [428131 7208942]]
                               [[428141 7208952] [428147 7208956]]]))

(defn- paikallinen [url]
  (str "http://localhost:8000" url))

(defcard kartta-card
  "Karttakomponentti"
  (fn [sijainti _]
    (reagent/as-element
      [:div {:style {:width "100%"
                     :height "800px"}}
       [karttakomponentti {:wmts-url (paikallinen asetukset/+wmts-url+)
                           :wmts-url-kiinteistorajat (paikallinen asetukset/+wmts-url-kiinteistojaotus+)
                           :wmts-url-ortokuva (paikallinen asetukset/+wmts-url-ortokuva+)
                           :sijainti-atomi sijainti
                           :ajoneuvon-sijainti-atomi sijainti
                           :reittipisteet-atomi test-reittipisteet
                           :kirjauspisteet-atomi test-ikonit
                           :optiot testioptiot}]]))
  test-sijainti
  {:inspect-data true
   :watch-atom true})

(defcard kartan-ohjaus
  "Siirrä karttaa muuttamalla sijaintiatomia. Autonuolen pitäisi liikkua kartalla, ei jäädä paikalleen"
  (fn [sijainti _]
    (reagent/as-element
      [:div
       [:button {:on-click #(swap! sijainti update-in [:lat] (fn [x] (+ x 100)))}
        "Siirrä"]
       [:button {:on-click #(swap! sijainti update-in [:heading] (fn [suunta] (+ suunta 10)))}
        "Suuntaa"]]))
  test-sijainti
  {:watch-atom true})
