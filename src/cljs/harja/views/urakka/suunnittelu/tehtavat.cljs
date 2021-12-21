(ns harja.views.urakka.suunnittelu.tehtavat
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<! chan]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-tehtavat :as t]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.pvm :as pvm]
            [harja.loki :as loki])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn sarakkeiden-leveys [sarake]
  (case sarake
    :tehtava "col-xs-12 col-sm-8 col-md-8 col-lg-8"
    :maara "leveys-70"
    :maara-input "leveys-15"
    :maara-yksikko "leveys-15"))

(defn valitaso-filtteri
  [_ app]
  (let [{:keys [alkupvm]} (-> @tila/tila :yleiset :urakka)
        toimenpide-xform (filter
                           (fn [data]
                             (= 3 (:taso data))))
        disabloitu-alasveto? (fn [koll]
                               (= 0 (count koll)))]
    (fn [e! {:keys [tehtavat-ja-toimenpiteet valinnat] :as app}]
      (let [vuosi (pvm/vuosi alkupvm)
            toimenpiteet (sort-by :nimi (into [] toimenpide-xform tehtavat-ja-toimenpiteet))
            hoitokaudet (into [] (range vuosi (+ 5 vuosi)))]
        [:div.flex-row
         {:style {:justify-content "flex-start"
                  :align-items     "flex-end"}}
         [:div
          {:style {:width        "840px"
                   :margin-right "15px"}}
          [:label.alasvedon-otsikko "Toimenpide"]
          [yleiset/livi-pudotusvalikko {:valinta      (:toimenpide valinnat)
                                        :valitse-fn   #(e! (t/->ValitseTaso % :toimenpide))
                                        :format-fn    #(:nimi %)
                                        :disabled     (disabloitu-alasveto? toimenpiteet)
                                        :vayla-tyyli? true}
           toimenpiteet]]
         [:div
          {:style {:width        "220px"
                   :margin-right "15px"}}
          [:label.alasvedon-otsikko "Hoitokausi"]
          [yleiset/livi-pudotusvalikko {:valinta      (:hoitokausi valinnat)
                                        :valitse-fn   #(e! (t/->HaeMaarat {:hoitokausi %}))
                                        :format-fn    #(str "1.10." % "-30.9." (inc %))
                                        :disabled     (disabloitu-alasveto? hoitokaudet)
                                        :vayla-tyyli? true}
           hoitokaudet]]
         [:div
          [:input#kopioi-tuleville-vuosille.vayla-checkbox
           {:type      "checkbox"
            :checked   (:samat-tuleville valinnat)
            :on-change #(e! (t/->SamatTulevilleMoodi (not (:samat-tuleville valinnat))))
            :disabled  (:noudetaan valinnat)}]
          [:label
           {:for "kopioi-tuleville-vuosille"}
           "Samat suunnitellut määrät tuleville hoitokausille"]]]))))

(defn- kun-yksikko
  [rivi] 
  (let [{:keys [yksikko]} rivi] 
    (not (or (nil? yksikko)
           (= "" yksikko)
           (= "-" yksikko)))))

(defn tallenna! 
  [e! rivi]                          
  (e! (t/->TallennaTehtavamaara rivi)))

(defn tehtava-maarat-taulukko
  [e! {:keys [taulukon-atomit] {:keys [urakan-alku?] :as valinnat} :valinnat}]
  (let [tulevat-ominaisuudet? true]
    [:<>
     [debug/debug valinnat]
     [debug/debug taulukon-atomit]
     (for [atomi taulukon-atomit] 
         ^{:key (gensym "tehtavat-")}
         [grid/muokkaus-grid
          {:otsikko (:nimi atomi)
           :id (keyword (str "tehtavat-maarat-" (:nimi atomi)))
           :tyhja "Ladataan tietoja"
           :voi-poistaa? (constantly false)
           :voi-muokata? true
           :voi-lisata? false
           :voi-kumota? false
           :piilota-muokkaus? true
           :piilota-toiminnot? false
           :on-rivi-blur (r/partial tallenna! e!)}
          [{:otsikko "Tehtävä" :nimi :nimi :tyyppi :string :muokattava? (constantly false) :leveys 8}
           (when (and urakan-alku? tulevat-ominaisuudet?)
             {:otsikko "Sopimuksen määrä koko urakka yhteensä" :nimi :maara-sopimus :tyyppi :numero :muokattava? (constantly true) :leveys 3})
           (when (and (not urakan-alku?) tulevat-ominaisuudet?) 
             {:otsikko "Sovittu koko urakka yhteensä" :nimi :maara-sovittu-koko-urakka :tyyppi :numero :muokattava? (constantly false) :leveys 3})
           (when (and (not urakan-alku?) tulevat-ominaisuudet?) 
             {:otsikko "Sovittu koko urakka jäljellä" :nimi :maara-jaljella-koko-urakka :tyyppi :numero :muokattava? (constantly false) :leveys 3})
           (when-not urakan-alku? 
             {:otsikko "Suunniteltu määrä hoitokausi" :nimi :maara :tyyppi :numero :muokattava? kun-yksikko :leveys 3})
           {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys 2}]
          (:atomi atomi)])]))

(defn tehtavat*
  [e! _]
  (komp/luo
    (komp/piirretty (fn [_]
                      (e! (t/->HaeTehtavat
                            {:hoitokausi :kaikki}))))
    (fn [e! app]
      [:div#vayla
       [debug/debug app]
       [:div "Tehtävät ja määrät suunnitellaan urakan alussa ja tarkennetaan urakan kuluessa. Osalle tehtävistä kertyy toteuneita määriä automaattisesti urakoitsijajärjestelmistä. Osa toteutuneista määristä täytyy kuitenkin kirjata manuaalisesti Toteuma-puolelle."]
       [:div "Yksiköttömiin tehtäviin ei tehdä kirjauksia."]
       [valitaso-filtteri e! app]
       [tehtava-maarat-taulukko e! app]])))

(defn tehtavat []
  (tuck/tuck tila/suunnittelu-tehtavat tehtavat*))
