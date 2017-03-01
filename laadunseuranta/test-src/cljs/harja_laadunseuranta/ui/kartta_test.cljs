(ns harja-laadunseuranta.ui.kartta-test
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [reagent.core :as reagent :refer [atom]]
            [dommy.core :as dommy]
            [harja.testutils.shared-testutils :refer [sel sel1]]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.ui.kartta :as kartta])
  (:require-macros [harja-laadunseuranta.test-macros :refer [with-component prepare-component-tests]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

(prepare-component-tests)

(defn- kerroksen-geometria [kerros feature-n]
  (-> kerros
      .getSource
      .getFeatures
      (aget feature-n)
      .getGeometry
      .getCoordinates))

(defn- kartan-kerrokset [ol]
  (.getArray (.getLayers ol)))

(defn- kartan-kerros [ol nth]
  (aget (kartan-kerrokset ol) nth))

(deftest kartan-konfiguraatio
  (testing "Kartan konfiguraatio"
    (let [sijainti (atom {:lat 100
                          :lon 200})
          ajoneuvon-sijainti (atom {:nykyinen {:lat 400
                                               :lon 500
                                               :heading 45}})
          reittipisteet (atom [{:segmentti [[1 1] [2 2]]
                                :vari "black"}
                               {:segmentti [[2 2] [3 3]]
                                :vari "black"}])
          kirjauspisteet (atom [{:lat 1 :lon 1}])]

      (with-component [kartta/karttakomponentti
                       {:wmts-url asetukset/+wmts-url+
                        :wmts-url-kiinteistorajat asetukset/+wmts-url-kiinteistojaotus+
                        :wmts-url-ortokuva asetukset/+wmts-url-ortokuva+
                        :sijainti-atomi sijainti
                        :keskita-ajoneuvoon-atom (atom false)
                        :kayttaja-muutti-zoomausta-aikaleima-atom (atom nil)
                        :ajoneuvon-sijainti-atomi ajoneuvon-sijainti
                        :reittipisteet-atomi reittipisteet
                        :kirjauspisteet-atomi kirjauspisteet
                        :optiot (atom {:seuraa-sijaintia? true})}]
        (let [kartta-div (sel1 [:div.map])]
          (is (not (nil? kartta-div)))

          (let [ol (.-openlayers kartta-div)
                view (.getView ol)]

            (testing "Viewin asetukset"
              (is (= [200 100] (js->clj (.getCenter view))))
              ;; FIXME: miksi tämä ei enää toimi? proj4 määrityksen latausjärjestys?
              ;;(is (= "m" (.getUnits (.getProjection view))))
              (is (= "EPSG:3067" (.getCode (.getProjection view)))))

            (testing "Kerrokset"
              (let [kerrokset (kartan-kerrokset ol)]
                (is (= 7 (.-length kerrokset)))

                (let [ajoneuvokerros (aget kerrokset 4)
                      ajoneuvon-geometria (kerroksen-geometria ajoneuvokerros 0)]
                  (is (= [500 400] (js->clj ajoneuvon-geometria))))

                (let [reittipistekerros (aget kerrokset 5)
                      reittipistegeometriat (kerroksen-geometria reittipistekerros 0)
                      reittipistegeometriat2 (kerroksen-geometria reittipistekerros 1)]
                  (is (= [[1 1] [2 2]] (js->clj reittipistegeometriat)))
                  (is (= [[2 2] [3 3]] (js->clj reittipistegeometriat2))))))

            (reset! sijainti {:lat 200
                              :lon 300})
            (reset! ajoneuvon-sijainti {:nykyinen {:lat 1000
                                                   :lon 1000
                                                   :heading 90}})
            (reagent/flush)
            (testing "Kartan keskipisteen siirtyminen"
              (is (= [300 200] (js->clj (.getCenter view)))))

            (testing "Ajoneuvon geometrioiden muuttuminen"
              (let [ajoneuvokerros (kartan-kerros ol 4)
                    ajoneuvon-geometria (kerroksen-geometria ajoneuvokerros 0)]
                (is (= [1000 1000] (js->clj ajoneuvon-geometria)))))))))))
