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
            [harja.math :as math]
            [cljs-time.local :as l]
            [cljs-time.core :as t])
  (:require-macros [reagent.ratom :refer [run!]]
                   [devcards.core :refer [defcard]]))

(defn lisaa-kirjausikoni [teksti]
  (swap! s/kirjauspisteet
         conj (assoc (select-keys (:nykyinen @s/sijainti) [:lat :lon])
                :label teksti)))

(defn- wmts-source [layer url]
  (ol.source.WMTS. #js {:attributions [(ol.Attribution. #js {:html "MML"})]
                        :url url
                        :layer layer
                        :matrixSet "ETRS-TM35FIN"
                        :format (if (= "ortokuva" layer) "image/jpeg" "image/png")
                        :projection projektiot/projektio
                        :tileGrid (ol.tilegrid.WMTS. (clj->js (projektiot/tilegrid 16)))
                        :style "default"
                        :wrapX true}))

;; Karttakuvatasot kytketään indeksillä, joten merkitään ne tänne
(def ^:const
  taustakarttatyypit
  [{:nimi :taustakartta
    :luokka "taustakartta"
    :napin-sprite "kartta-24"
    :tason-indeksi 0}

   {:nimi :ortokuva
    :luokka "ortokuva"
    :napin-sprite "satelliitti-24"
    :tason-indeksi 1}

   {:nimi :maastokartta
    :luokka "maastokartta"
    :napin-sprite "maasto-24"
    :tason-indeksi 2}])

(def ^:const taso-kiinteistorajat 3)

;; Funktiot, jotka palauttavat WMTS sourcen URLille
(def wmts-source-taustakartta (partial wmts-source "taustakartta"))
(def wmts-source-kiinteistojaotus (partial wmts-source "kiinteistojaotus"))
(def wmts-source-ortokuva (partial wmts-source "ortokuva"))
(def wmts-source-maastokartta (partial wmts-source "maastokartta"))

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
  {:layers [;; Karttakuvatasojen järjestyksellä on merkitystä, niitä
            ;; käytetään suoraan indekseillä
            (tile-layer (wmts-source-taustakartta wmts-url))
            (tile-layer (wmts-source-ortokuva wmts-url-ortokuva))
            (tile-layer (wmts-source-maastokartta wmts-url))
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
    (.setStyle (nuoli-ikoni-tyyli (- (/ Math/PI 2)))))
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

(defn- paivita-kartan-zoom [kartta zoom-taso]
  (let [view (.getView kartta)]
    (doto view
      (.setZoom zoom-taso)
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


(defn- kytke-taso [taso-idx kartta enable]
  (-> kartta
      .getLayers .getArray
      (aget taso-idx)
      (.setVisible enable)))

(def kytke-kiinteistorajat (partial kytke-taso taso-kiinteistorajat))

(defn- aseta-taustakartta
  "Asettaa karttatasojen näkyvyydet käyttäjän valitseman taustakartan perusteella"
  [kartta valinta]
  (doseq [{:keys [nimi tason-indeksi]} taustakarttatyypit]
    (kytke-taso tason-indeksi kartta (= valinta nimi))))

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

(defn- maarita-kartan-rotaatio-ajosuunnan-mukaan [kartta sijainti-edellinen sijainti-nykyinen]
  ;; Rotatoi kartta ajosuuntaan, mutta vain jos nopeus on riittävä, muuten
  ;; paikallaolo ja siitä aiheutuva GPS-kohina saa kartan levottomaksi
  (when (>= (math/pisteiden-etaisyys sijainti-edellinen sijainti-nykyinen) 1)
    (paivita-kartan-rotaatio kartta (- (math/pisteiden-kulma-radiaaneina
                                         sijainti-edellinen
                                         sijainti-nykyinen)
                                       (/ Math/PI 2)))))

(defn- maarita-kartan-zoom-taso-ajonopeuden-mukaan [{:keys [kartta nopeus kayttaja-muutti-zoomausta-aikaleima]}]
  (when (or
          (nil? kayttaja-muutti-zoomausta-aikaleima)
          (and kayttaja-muutti-zoomausta-aikaleima
               (> (t/in-seconds (t/interval kayttaja-muutti-zoomausta-aikaleima (l/local-now)))
                  asetukset/+kunnioita-kayttajan-zoomia-s+)))
    (let [nopeus-tiedossa? (not (or (js/isNaN nopeus) ;; GPS-API palauttaa nopeuden JS NaN -muodossa, jos ei saada
                                    (nil? nopeus)))
          min-zoom asetukset/+min-zoom+
          max-zoom asetukset/+max-zoom+
          max-nopeus-min-zoomaus 30 ;; m/s, jolla kartta zoomautuu minimiarvoonsa eli niin kauas kuin sallittu
          uusi-zoom-taso (if nopeus-tiedossa?
                           ;; Zoomataan karttaa kauemmas sopivalle tasolle GPS:stä saadun nopeustiedon perusteella
                           (- max-zoom (float (* (/ nopeus max-nopeus-min-zoomaus) (- max-zoom min-zoom))))
                           max-zoom)
          uusi-tarkastettu-zoom-taso (cond
                                       (< uusi-zoom-taso min-zoom)
                                       min-zoom

                                       (> uusi-zoom-taso max-zoom)
                                       max-zoom

                                       :default
                                       uusi-zoom-taso)]
      (paivita-kartan-zoom kartta uusi-tarkastettu-zoom-taso))))

(defn kartta-did-mount [this {:keys [wmts-url wmts-url-kiinteistorajat wmts-url-ortokuva keskipiste-atomi
                                     ajoneuvon-sijainti-atomi reittipisteet-atomi kirjatut-pisteet-atomi optiot
                                     kayttaja-muutti-zoomausta-aikaleima-atom keskita-ajoneuvoon-atom]}]
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
            (maarita-kartan-zoom-taso-ajonopeuden-mukaan
              {:kartta kartta
               :keskita-ajoneuvoon? @keskita-ajoneuvoon-atom
               :kayttaja-muutti-zoomausta-aikaleima @kayttaja-muutti-zoomausta-aikaleima-atom
               :nopeus (:speed (:nykyinen @ajoneuvon-sijainti-atomi))})
            (maarita-kartan-rotaatio-ajosuunnan-mukaan kartta sijainti-edellinen sijainti-nykyinen)))
        (kytke-dragpan kartta true)))

    (run!
      (kytke-kiinteistorajat kartta (:nayta-kiinteistorajat? @optiot)))

    (run!
     ;; Aseta taustakarttatasojen näkyvyydet kun käyttäjän valinta muuttuu
     (aseta-taustakartta kartta (:taustakartta @optiot)))

    ;; reagoidaan ajoneuvon sijainnin muutokseen
    (run! (paivita-ajoneuvon-sijainti kartta ajoneuvo ajoneuvokerros (:nykyinen @ajoneuvon-sijainti-atomi)))

    ;; reagoidaan reittipisteiden muutokseen
    (run! (paivita-ajettu-reitti kartta ajettu-reitti reittikerros @reittipisteet-atomi))

    ;; reagoidaan merkintojen muutokseen
    (run! (paivita-kirjausikonit kartta kirjatut-pisteet ikonikerros @kirjatut-pisteet-atomi))))


(defn karttakomponentti [{:keys [wmts-url wmts-url-kiinteistorajat wmts-url-ortokuva sijainti-atomi
                                 ajoneuvon-sijainti-atomi reittipisteet-atomi kirjauspisteet-atomi optiot
                                 kayttaja-muutti-zoomausta-aikaleima-atom keskita-ajoneuvoon-atom]}]
  (reagent/create-class {:reagent-render kartta-render
                         :component-did-mount
                         #(kartta-did-mount
                            %
                            {:wmts-url wmts-url
                             :wmts-url-kiinteistorajat wmts-url-kiinteistorajat
                             :wmts-url-ortokuva wmts-url-ortokuva
                             :keskipiste-atomi sijainti-atomi
                             :ajoneuvon-sijainti-atomi ajoneuvon-sijainti-atomi
                             :reittipisteet-atomi reittipisteet-atomi
                             :kirjatut-pisteet-atomi kirjauspisteet-atomi
                             :optiot optiot
                             :keskita-ajoneuvoon-atom keskita-ajoneuvoon-atom
                             :kayttaja-muutti-zoomausta-aikaleima-atom kayttaja-muutti-zoomausta-aikaleima-atom})}))

(defn- taustakartan-valinta
  "Valinnat, jolla voi vaihtaa näytettävä taustakartta: normaali, ortokuva tai maastokartta."
  [valittu-tyyppi aseta-tyyppi!]
  [:div.taustakartan-valinta
   (for [{:keys [luokka napin-sprite nimi]} taustakarttatyypit]
     ^{:key (str nimi)}
     [:div.kontrollinappi
      {:class (str luokka
                   (when (= nimi valittu-tyyppi)
                     " kontrollinappi-aktiivinen"))
       :on-click #(aseta-tyyppi! nimi)}
      [kuvat/svg-sprite napin-sprite]])])

(defn kartta []
  [:div
   [karttakomponentti
    {:wmts-url asetukset/+wmts-url+
     :wmts-url-kiinteistorajat asetukset/+wmts-url-kiinteistojaotus+
     :wmts-url-ortokuva asetukset/+wmts-url-ortokuva+
     :sijainti-atomi s/kartan-keskipiste
     :ajoneuvon-sijainti-atomi s/ajoneuvon-sijainti
     :reittipisteet-atomi s/reittipisteet
     :keskita-ajoneuvoon-atom s/keskita-ajoneuvoon?
     :kayttaja-muutti-zoomausta-aikaleima-atom s/kayttaja-muutti-zoomausta-aikaleima
     :kirjauspisteet-atomi s/kirjauspisteet
     :optiot s/karttaoptiot}]
   [:div.kartan-kontrollit {:style (when @s/havaintolomake-auki?
                                     {:display "none"})}
    [:div#karttakontrollit ;; OpenLayersin ikonit asetetaan tähän elementtiin erikseen
     {:on-click #(reset! s/kayttaja-muutti-zoomausta-aikaleima (l/local-now))}]
    [:div
     {:class (str "kontrollinappi kiinteistorajat "
                  (when @s/nayta-kiinteistorajat? "kontrollinappi-aktiivinen"))
      :on-click #(swap! s/nayta-kiinteistorajat? not)}
     [kuvat/svg-sprite "kiinteistoraja-24"]]
    [:div
     {:class (str "kontrollinappi keskityspainike "
                  (when @s/keskita-ajoneuvoon? "kontrollinappi-aktiivinen"))
      :on-click #(do (swap! s/keskita-ajoneuvoon? not))}
     [kuvat/svg-sprite "tahtain-24"]]
    [taustakartan-valinta @s/taustakartta #(reset! s/taustakartta %)]]])

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
