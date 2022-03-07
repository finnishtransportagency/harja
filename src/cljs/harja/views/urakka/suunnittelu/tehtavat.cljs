
(ns harja.views.urakka.suunnittelu.tehtavat
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-tehtavat :as t]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]   
            [harja.ui.kentat :as kentat]
            [harja.ui.napit :as napit]
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
    (fn [e! {{:keys [toimenpide-valikko-valinnat samat-tuleville toimenpide hoitokausi noudetaan]} :valinnat :keys [sopimukset-syotetty?] :as _app}]
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
                                        :vayla-tyyli? true
                                        :disabled     (or
                                                        (not sopimukset-syotetty?)
                                                        (disabloitu-alasveto? toimenpide-valikko-valinnat))}
           toimenpide-valikko-valinnat]]
         [:div
          {:style {:width        "220px"
                   :margin-right "15px"}}
          [:label.alasvedon-otsikko "Hoitokausi"]
          [yleiset/livi-pudotusvalikko {:valinta      hoitokausi
                                        :valitse-fn   #(e! (t/->ValitseTaso % :hoitokausi))
                                        :format-fn    #(str "1.10." % "-30.9." (inc %))
                                        :disabled     (or
                                                        (not sopimukset-syotetty?)
                                                        (disabloitu-alasveto? hoitokaudet))
                                        :vayla-tyyli? true}
           hoitokaudet]]
         [:div
          [:input#kopioi-tuleville-vuosille.vayla-checkbox
           {:type      "checkbox"
            :checked   samat-tuleville
            :on-change #(e! (t/->SamatTulevilleMoodi (not samat-tuleville)))
            :disabled  (not sopimukset-syotetty?)}]
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

(defn vetolaatikko-komponentti
  [_ _ {:keys [vanhempi id] :as rivi}]
  (let [disabloitu? (r/atom (:joka-vuosi-erikseen? rivi))]
    (fn [e! app {:keys [vanhempi id] :as rivi}]
      [:div 
       [:div 
        [kentat/tee-kentta {:tyyppi :checkbox 
                            :teksti "Haluan syöttää joka vuoden erikseen"
                            :valitse!
                            (fn [v]
                                (let [ruksittu? (.. v -target -checked)]
                                  (reset! disabloitu? ruksittu?)
                                  (swap! t/taulukko-tila assoc-in [vanhempi id :joka-vuosi-erikseen?] ruksittu?))) }
         (r/cursor t/taulukko-tila [vanhempi id :joka-vuosi-erikseen?])]]
       [:div
        (doall 
          (for [vuosi (range
                        (-> @tila/yleiset
                          :urakka
                          :alkupvm
                          pvm/vuosi)
                        (-> @tila/yleiset
                          :urakka
                          :loppupvm
                          pvm/vuosi))]
            ^{:key (gensym "vetolaatikko-input")}
            [kentat/tee-kentta {:tyyppi :numero
                                :disabled? (not @disabloitu?)                            
                                :on-blur #(tallenna! e! 
                                            (:sopimukset-syotetty? app) 
                                            (assoc rivi 
                                              :sopimuksen-tehtavamaara (.. % -target -value) 
                                              :hoitokausi vuosi))}
             (r/cursor t/taulukko-tila [vanhempi id :sopimuksen-tehtavamaarat vuosi])]))]])))

(defn- vetolaatikot
  [e! app rivi] 
  [vetolaatikko-komponentti e! app rivi])

(defn- itse-taulukko 
  [e! {:keys [sopimukset-syotetty? taso-4-tehtavat] :as app} toimenpiteen-tiedot]
  (let [{:keys [nimi sisainen-id]} toimenpiteen-tiedot
        tila (r/cursor t/taulukko-tila [sisainen-id])]
    (when t/taulukko-tila
      ^{:key (gensym "tehtavat-")}
      [grid/muokkaus-grid
       (merge 
         {:otsikko nimi
          :id (keyword (str "tehtavat-maarat-" nimi))
          :tyhja "Ladataan tietoja"
          :voi-poistaa? (constantly false)
          :jarjesta :jarjestys 
          :ulkoinen-validointi? true
          :voi-muokata? true
          :voi-lisata? false
          :voi-kumota? false
          :virheet t/taulukko-virheet
          :piilota-toiminnot? true
          :on-rivi-blur (r/partial tallenna! e! sopimukset-syotetty?)}
         (when (not sopimukset-syotetty?) 
           {:vetolaatikot
            (into {}
              (map (juxt :id (r/partial vetolaatikot e! app)))
              taso-4-tehtavat)
            :vetolaatikot-auki t/taulukko-avatut-vetolaatikot}))
       [(when (not sopimukset-syotetty?) 
          {:tyyppi :vetolaatikon-tila :leveys 1})
        {:otsikko "Tehtävä" :nimi :nimi :tyyppi :string :muokattava? (constantly false) :leveys 8}
        (when (not sopimukset-syotetty?)
          {:otsikko "Sopimuksen määrä koko urakka yhteensä" :nimi :sopimuksen-tehtavamaara :tyyppi :numero :muokattava? kun-yksikko :leveys 3})
        (when sopimukset-syotetty? 
          {:otsikko "Sovittu koko urakka yhteensä" :nimi :sopimuksen-tehtavamaara :tyyppi :numero :muokattava? (constantly false) :leveys 3})
        (when sopimukset-syotetty? 
          {:otsikko "Sovittu koko urakka jäljellä" :nimi :sovittuja-jaljella :tyyppi :string :muokattava? (constantly false) :leveys 3})
        (when sopimukset-syotetty? 
          {:otsikko [:div 
                     [:div "Suunniteltu määrä"] 
                     [:div "hoitokausi"]] :nimi :maara :tyyppi :numero :muokattava? kun-yksikko :leveys 3})
        {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys 2}]
       tila])))

(defn tehtava-maarat-taulukko
  [e! {:keys [valinnat taulukko] :as app}]
  [:div
   [debug/debug valinnat]
   [debug/debug taulukko]
   #_[debug/debug @t/taulukko-tila]
   #_[debug/debug @t/taulukko-avatut-vetolaatikot]
   [:div 
    (doall
      (for [t (filter :nayta-toimenpide? taulukko)]        
        ^{:key (gensym "tehtavat-")}
        [itse-taulukko e! app t]))]])

(defn sopimuksen-tallennus-boksi
  [e! virhe-sopimuksia-syottaessa?]
  [:div.table-default-even.col-xs-12
   [:div.flex-row 
    [:h3 "Syötä sopimuksen määrät"]
    [napit/yleinen-ensisijainen "Tallenna" #(e! (t/->TallennaSopimus true))]]
   (when virhe-sopimuksia-syottaessa?  
     [yleiset/info-laatikko :varoitus "Syötä kaikkiin tehtäviin tiedot. Jos sopimuksessa ei ole määriä kyseiselle tehtävälle, syötä '0'" "" "100%" {:luokka "ala-margin-16"}])])

(defn tehtavat*
  [e! _]
  (komp/luo
    (komp/piirretty (fn [_]
                      (e! (t/->AsetaOletusHoitokausi))
                      (e! (t/->HaeSopimuksenTila))
                      (e! (t/->HaeTehtavat
                            {:hoitokausi :kaikki}))))
    (fn [e! {:keys [sopimukset-syotetty? virhe-sopimuksia-syottaessa?] :as app}]
      [:div#vayla
       [:h1 "Tehtävät ja määrät"]
       [debug/debug app]
       [:div "Tehtävät ja määrät suunnitellaan urakan alussa ja tarkennetaan urakan kuluessa. Osalle tehtävistä kertyy toteuneita määriä automaattisesti urakoitsijajärjestelmistä. Osa toteutuneista määristä täytyy kuitenkin kirjata manuaalisesti Toteuma-puolelle."]
       [:div "Yksiköttömiin tehtäviin ei tehdä kirjauksia."]
       [:div.table-default-even "DEBUG: Resetoi sopimuksen tila testataksesi uudelleen."
        [:button {:on-click #(e! (t/->TallennaSopimus false))} (str "TILA: Vahvistettu? " sopimukset-syotetty?)]]
       (when (not sopimukset-syotetty?)
         [yleiset/keltainen-vihjelaatikko "Urakan aluksi syötä sopimuksen tehtävä- ja määräluettelosta sovitut määrät kerrottuna koko urakalle yhteensä. Tätä tietoa voidaan käyttää määrien suunnitteluun ja seurantaan." :info])
       (when (not sopimukset-syotetty?) 
         [sopimuksen-tallennus-boksi e! virhe-sopimuksia-syottaessa?])
       [valitaso-filtteri e! app]
       [tehtava-maarat-taulukko e! app]
       (when (not sopimukset-syotetty?) 
         [sopimuksen-tallennus-boksi e! virhe-sopimuksia-syottaessa?])])))

(defn tehtavat []
  (tuck/tuck tila/suunnittelu-tehtavat tehtavat*))
