(ns harja.views.urakka.suunnittelu.tehtavat
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-tehtavat :as t]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.pvm :as pvm]))

(defn sarakkeiden-leveys [sarake]
  (case sarake
    :tehtava "col-xs-12 col-sm-8 col-md-8 col-lg-8"
    :maara "leveys-70"
    :maara-input "leveys-15"
    :maara-yksikko "leveys-15"))

(defn disabloitu-alasveto?
  [koll]
  (= 0 (count koll)))

(defn valitaso-filtteri
  [_ _]
  (let [{:keys [alkupvm loppupvm]} (-> @tila/tila :yleiset :urakka)]
    (fn [e! {{:keys [toimenpide-valikko-valinnat samat-tuleville toimenpide hoitokausi noudetaan]} :valinnat :as _app}]
      (let [vuosi (pvm/vuosi alkupvm)
            loppuvuosi (pvm/vuosi loppupvm)
            hoitokaudet (into [] (range vuosi loppuvuosi))]
        [:div.flex-row
         {:style {:justify-content "flex-start"
                  :align-items     "flex-end"}}
         [:div
          {:style {:width        "840px"
                   :margin-right "15px"}}
          [:label.alasvedon-otsikko "Toimenpide"]
          [yleiset/livi-pudotusvalikko {:valinta      toimenpide
                                        :valitse-fn   #(e! (t/->ValitseTaso % :toimenpide))
                                        :format-fn    #(:nimi %)
                                        :disabled     (disabloitu-alasveto? toimenpide-valikko-valinnat)
                                        :vayla-tyyli? true}
           toimenpide-valikko-valinnat]]
         [:div
          {:style {:width        "220px"
                   :margin-right "15px"}}
          [:label.alasvedon-otsikko "Hoitokausi"]
          [yleiset/livi-pudotusvalikko {:valinta      hoitokausi
                                        :valitse-fn   #(e! (t/->ValitseTaso % :hoitokausi))
                                        :format-fn    #(str "1.10." % "-30.9." (inc %))
                                        :disabled     (disabloitu-alasveto? hoitokaudet)
                                        :vayla-tyyli? true}
           hoitokaudet]]
         [:div
          [:input#kopioi-tuleville-vuosille.vayla-checkbox
           {:type      "checkbox"
            :checked   samat-tuleville
            :on-change #(e! (t/->SamatTulevilleMoodi (not samat-tuleville)))
            :disabled  noudetaan}]
          [:label
           {:for "kopioi-tuleville-vuosille"}
           "Samat suunnitellut määrät tuleville hoitokausille"]]]))))

(defn- kun-yksikko
  [rivi] 
  (let [{:keys [yksikko]} rivi] 
    (not (or 
           (nil? yksikko)
           (= "" yksikko)
           (= "-" yksikko)))))

(defn tallenna! 
  [e! sopimukset-syotetty? rivi]   
  (let [tuck-event (if sopimukset-syotetty?
                     t/->TallennaTehtavamaara
                     t/->TallennaSopimuksenTehtavamaara)] 
    (e! (tuck-event rivi))))

(defn tehtava-maarat-taulukko
  [e! {:keys [taulukon-atomit sopimukset-syotetty? valinnat]}]
  [:<>
   [debug/debug valinnat]
   [debug/debug taulukon-atomit]
   (let [sopimukset-syotetty? true])
   (for [atomi (filter :nayta-toimenpide? taulukon-atomit)]
     ^{:key (gensym "tehtavat-")}
     [grid/muokkaus-grid
      {:otsikko (:nimi atomi)
       :id (keyword (str "tehtavat-maarat-" (:nimi atomi)))
       :tyhja "Ladataan tietoja"
       :voi-poistaa? (constantly false)
       :jarjesta :jarjestys 
       :voi-muokata? true
       :voi-lisata? false
       :voi-kumota? false
       :piilota-toiminnot? true
       :on-rivi-blur (r/partial tallenna! e! sopimukset-syotetty?)}
      [{:otsikko "Tehtävä" :nimi :nimi :tyyppi :string :muokattava? (constantly false) :leveys 8}
                                        ; disabloitu toistaiseksi, osa tulevia featureita jotka sommittelun vuoksi olleet mukana
       (when (and t/sopimuksen-tehtavamaarat-kaytossa? (not sopimukset-syotetty?))
         {:otsikko "Sopimuksen määrä koko urakka yhteensä" :nimi :sopimuksen-tehtavamaara :tyyppi :numero :muokattava? kun-yksikko :leveys 3})
       (when (and t/sopimuksen-tehtavamaarat-kaytossa? sopimukset-syotetty?) 
         {:otsikko "Sovittu koko urakka yhteensä" :nimi :sopimuksen-tehtavamaara :tyyppi :numero :muokattava? (constantly false) :leveys 3})
       (when (and t/sopimuksen-tehtavamaarat-kaytossa? sopimukset-syotetty?) 
         {:otsikko "Sovittu koko urakka jäljellä" :nimi :sovittuja-jaljella :tyyppi :string :muokattava? (constantly false) :leveys 3})
       (when (or (not t/sopimuksen-tehtavamaarat-kaytossa?) sopimukset-syotetty?) 
         {:otsikko [:<> 
                    [:div "Suunniteltu määrä"] 
                    [:div "hoitokausi"]] :nimi :maara :tyyppi :numero :muokattava? kun-yksikko :leveys 3})
       {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys 2}]
      (:atomi atomi)])])

(defn sopimuksen-tallennus-boksi
  [e! sopimukset-syotetty?]
  [:div "I'm a good deal"
   [:button {:on-click #(e! (t/->TallennaSopimus (not sopimukset-syotetty?)))} (str "Currently vahvistettu? " sopimukset-syotetty?)]])

(defn tehtavat*
  [e! _]
  (komp/luo
    (komp/piirretty (fn [_]
                      (e! (t/->HaeSopimuksenTila))
                      (e! (t/->HaeTehtavat
                            {:hoitokausi :kaikki}))))
    (fn [e! {sopimukset-syotetty? :sopimukset-syotetty? :as app}]
      [:div#vayla
       [debug/debug app]
       [:div "Tehtävät ja määrät suunnitellaan urakan alussa ja tarkennetaan urakan kuluessa. Osalle tehtävistä kertyy toteuneita määriä automaattisesti urakoitsijajärjestelmistä. Osa toteutuneista määristä täytyy kuitenkin kirjata manuaalisesti Toteuma-puolelle."]
       [:div "Yksiköttömiin tehtäviin ei tehdä kirjauksia."]
       (when (and t/sopimuksen-tehtavamaarat-kaytossa? #_(not sopimukset-syotetty?)) 
         [sopimuksen-tallennus-boksi e! sopimukset-syotetty?])
       [valitaso-filtteri e! app]
       [tehtava-maarat-taulukko e! app]
       (when (and t/sopimuksen-tehtavamaarat-kaytossa? #_(not sopimukset-syotetty?)) 
         [sopimuksen-tallennus-boksi e! sopimukset-syotetty?])])))

(defn tehtavat []
  (tuck/tuck tila/suunnittelu-tehtavat tehtavat*))
