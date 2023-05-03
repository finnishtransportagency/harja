(ns harja.views.hallinta.jarjestelma-asetukset
  (:require [harja.ui.komponentti :as komp]
            [tuck.core :refer [tuck]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.hallinta.jarjestelma-asetukset :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [harja.domain.geometriaaineistot :as geometria-aineistot]
            [harja.ui.debug :refer [debug]]
            [harja.loki :refer [log]]
            [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [chan <!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn aineiston-voimassaolot-epavalidit? [geometria-aineistot]
  (boolean
    (some
      (fn [ryhma]
        (some (fn [rivi]
                (some
                  (fn [kasiteltava]
                    (pvm/aikavalit-leikkaavat? (::geometria-aineistot/voimassaolo-alkaa rivi)
                                               (::geometria-aineistot/voimassaolo-paattyy rivi)
                                               (::geometria-aineistot/voimassaolo-alkaa kasiteltava)
                                               (::geometria-aineistot/voimassaolo-paattyy kasiteltava)))
                  (filter #(not= % rivi) ryhma)))
              ryhma))
      (map second (group-by ::geometria-aineistot/nimi geometria-aineistot)))))

(defn geometria-aineistot [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeGeometria-aineistot)))
    (fn [e! {:keys [geometria-aineistot haku-kaynnissa?] :as app}]
      [:div
       [grid/grid
        {:otsikko "Geometria-aineistot"
         :voi-lisata? (constantly true)
         :voi-muokata? (constantly true)
         :voi-poistaa? (constantly true)
         :voi-kumota? false
         :piilota-toiminnot? false
         :tyhja "Ei geometria-aineistoja"
         :jarjesta ::geometria-aineistot/nimi
         :tunniste ::geometria-aineistot/id
         :tallenna (fn [aineistot]
                     (let [ch (chan)
                           aineistot (map #(if (= -1 (::geometria-aineistot/id %))
                                             (dissoc % ::geometria-aineistot/id)
                                             %) aineistot)]
                       (e! (tiedot/->TallennaGeometria-ainestot aineistot ch))
                       (go (<! ch))))}
        [{:otsikko "Nimi"
          :nimi ::geometria-aineistot/nimi
          :tyyppi :string
          :leveys 1
          :validoi [[:ei-tyhja "Anna aineiston nimi"]]}
         {:otsikko "Tiedostonimi"
          :nimi ::geometria-aineistot/tiedostonimi
          :tyyppi :string
          :leveys 1
          :validoi [[:ei-tyhja "Anna aineiston tiedostonimi"]]}
         {:otsikko "Voimassaolo alkaa"
          :nimi ::geometria-aineistot/voimassaolo-alkaa
          :tyyppi :pvm-aika
          :fmt pvm/pvm-opt
          :leveys 1
          :validoi [#(when (aineiston-voimassaolot-epavalidit? (vals %3))
                      "Aineiston voimassaolo ei saa olla päällekkäin saman aineiston toisen voimassaolon kanssa")]}
         {:otsikko "Voimassaolo päättyy"
          :nimi ::geometria-aineistot/voimassaolo-paattyy
          :tyyppi :pvm-aika
          :fmt pvm/pvm-opt
          :leveys 1
          :validoi [#(when (aineiston-voimassaolot-epavalidit? (vals %3))
                      "Aineiston voimassaolo ei saa olla päällekkäin saman aineiston toisen voimassaolon kanssa")
                    [:pvm-kentan-jalkeen ::geometria-aineistot/voimassaolo-alkaa "Lopun on oltava alun jälkeen"]]}]
        geometria-aineistot]])))

(defn geometriapaivitykset [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeGeometriapaivitykset)))
    (fn [e! {:keys [geometriapaivitykset haku-kaynnissa?] :as app}]
      [:div
       [grid/grid
        {:otsikko "Geometriapaivitykset"
         :voi-lisata? (constantly true)
         :voi-muokata? (constantly true)
         :voi-poistaa? (constantly true)
         :voi-kumota? false
         :piilota-toiminnot? false
         :tyhja "Ei geometriapäivityksia"
         :jarjesta ::geometria-aineistot/nimi
         :tunniste ::geometria-aineistot/id}
        [{:otsikko "Nimi"
          :nimi ::geometria-aineistot/nimi
          :tyyppi :string
          :leveys 1}
         {:otsikko "Viimeisen päivitys"
          :nimi ::geometria-aineistot/viimeisen_paivitys
          :tyyppi :pvm-aika
          :fmt pvm/pvm-opt
          :leveys 1}
         {:otsikko "Seuraava päivitys"
          :nimi ::geometria-aineistot/seuraava_paivitys
          :tyyppi :pvm-aika
          :fmt pvm/pvm-opt
          :leveys 1}
         {:otsikko "Edellinen päivitys"
          :nimi ::geometria-aineistot/edellinen_paivitysyritys
          :tyyppi :pvm-aika
          :fmt pvm/pvm-opt
          :leveys 1}
         {:otsikko "Käytössä"
          :nimi ::geometria-aineistot/kaytossa
          :tyyppi :string
          :leveys 1}
         {:otsikko "Lisätiedot"
          :nimi ::geometria-aineistot/lisatieto
          :tyyppi :string
          :leveys 1}]
        geometriapaivitykset]])))

(defn jarjestelma-asetukset* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! app]
      [:div
       [debug app]
       [geometria-aineistot e! app]
       [geometriapaivitykset e! app]])))

(defn jarjestelma-asetukset []
  [tuck tiedot/tila jarjestelma-asetukset*])
