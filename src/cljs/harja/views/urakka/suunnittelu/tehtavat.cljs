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
  (let [{:keys [alkupvm]} (-> @tila/tila :yleiset :urakka)]
    (fn [e! {:keys [tehtavat-ja-toimenpiteet valinnat] :as app}]
      (let [vuosi (pvm/vuosi alkupvm)
            toimenpide-xform (comp #_(map
                                     (fn [[_ data]] data))
                                   (filter
                                     (fn [data]
                                       (= 3 (:taso data)))))
            toimenpiteet (sort-by :nimi (into [] toimenpide-xform tehtavat-ja-toimenpiteet))
            hoitokaudet (into [] (range vuosi (+ 5 vuosi)))
            disabloitu-alasveto? (fn [koll]
                                   (= 0 (count koll)))]

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
                                        :valitse-fn   #(e! (t/->HaeMaarat {:hoitokausi        %
                                                                           ;:prosessori        (partial luo-tehtava-taulukko e!)
                                                                           :tilan-paivitys-fn (fn [tila] (assoc-in tila [:valinnat :hoitokausi] %))}))
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

(defn- map->id-map-maaralla
  [maarat hoitokausi rivi]
  [(:id rivi) (assoc rivi 
                :hoitokausi hoitokausi
                :maara (get-in maarat [(:id rivi) hoitokausi]))])

(defn- kun-yksikko
  [rivi] 
  (let [{:keys [yksikko]} rivi] 
    (not (or (nil? yksikko)
           (= "" yksikko)
           (= "-" yksikko)))))

(defn tehtava-maarat-taulukko
  [e! {:keys [tehtavat-ja-toimenpiteet maarat] {:keys [toimenpide urakan-alku? hoitokausi] :as valinnat} :valinnat}]
  (let [valitut (if-not (= :kaikki toimenpide) 
                  (filter (fn [{:keys [id]}] (= id (:id toimenpide))) tehtavat-ja-toimenpiteet)
                  tehtavat-ja-toimenpiteet)
        tulevat-ominaisuudet? true]
    [:<>
     [debug/debug valinnat]
     (for [toimenpide valitut] 
       [:<>
        [:div (str (pr-str (:tehtavat toimenpide)))]
        [grid/muokkaus-grid
         {:otsikko (:nimi toimenpide)
          :id (keyword (str "tehtavat-maarat-" (:nimi toimenpide)))
          :tyhja "Ladataan tietoja"
          :voi-poistaa? (constantly false)
          :voi-muokata? true
          :voi-lisata? false
          :voi-kumota? false
          :piilota-muokkaus? true
          :piilota-toiminnot? false
          :on-rivi-blur (fn [& params]                          
                          (println (str "blurraus tapahtui" params))
                          (e! (t/->TallennaTehtavamaara params)))}
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
         (r/wrap (into {} (map (r/partial map->id-map-maaralla maarat hoitokausi)) (:tehtavat toimenpide)) 
           (constantly nil))]])]))

(defn tehtavat*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [this]
                      (e! (t/->HaeTehtavat
                            {:hoitokausi         :kaikki
                             #_:tehtavat->taulukko #_(partial luo-tehtava-taulukko e!)}))))
    (fn [e! app]
      [:div#vayla
       [debug/debug app]
       [:div "Tehtävät ja määrät suunnitellaan urakan alussa ja tarkennetaan urakan kuluessa. Osalle tehtävistä kertyy toteuneita määriä automaattisesti urakoitsijajärjestelmistä. Osa toteutuneista määristä täytyy kuitenkin kirjata manuaalisesti Toteuma-puolelle."]
       [:div "Yksiköttömiin tehtäviin ei tehdä kirjauksia."]
       [valitaso-filtteri e! app]
       [tehtava-maarat-taulukko e! app]])))

(defn tehtavat []
  (tuck/tuck tila/suunnittelu-tehtavat tehtavat*))
