(ns harja.views.urakka.suunnittelu.tehtavat
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [clojure.string :as str]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-tehtavat :as t]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
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
        [:div.flex-row.sticky-ylos.valkoinen-tausta
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
                                        :format-fn pvm/hoitokausi-str-alkuvuodesta
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
  (if-not (boolean? rivi)
    (let [{:keys [yksikko]} rivi] 
      (not (or 
             (nil? yksikko)
             (= "" yksikko)
             (= "-" yksikko))))
    rivi))

(defn vertaile-onko-samat 
  [vertailumaara [_ maara]] 
  (= vertailumaara maara))

(defn- samat-sopimusmaarat? 
  [rivi]
  (let [vertailumaara (-> rivi :sopimuksen-tehtavamaarat first second)]
    (every? (r/partial vertaile-onko-samat vertailumaara) (:sopimuksen-tehtavamaarat rivi))))

(defn- kun-kaikki-samat
  [rivi]
  (if (:joka-vuosi-erikseen? rivi)
    false
    rivi))

(defn tallenna!
  [e! sopimukset-syotetty? alueet-vai-maarat rivi]
  (let [tuck-event (cond
                     (and (= :alueet alueet-vai-maarat)
                       sopimukset-syotetty?)
                     t/->TallennaMuuttunutAluemaara
                     
                     (and (= :maarat alueet-vai-maarat)
                       sopimukset-syotetty?)
                     t/->TallennaTehtavamaara

                     (and (= :alueet alueet-vai-maarat)
                       (not sopimukset-syotetty?))
                     t/->TallennaSopimuksenAluemaara
                     
                     (and (= :maarat alueet-vai-maarat)
                       (not sopimukset-syotetty?))
                     t/->TallennaSopimuksenTehtavamaara)] 
    (e! (tuck-event rivi))))

(defn vali->viiva [nimi] (str/replace (str/lower-case nimi) " " "-"))

(defn vetolaatikko-komponentti-
  [_ _ {:keys [vanhempi id yksikko nimi] :as _rivi}]
  (let [joka-vuosi-erikseen? (r/cursor t/taulukko-tila [:maarat vanhempi id :joka-vuosi-erikseen?])]
    (fn [e! app {:keys [vanhempi id] :as rivi}]
      [:div.tehtavat-maarat-vetolaatikko 
       [:div.vetolaatikko-ruksi 
        [kentat/tee-kentta {:tyyppi :checkbox 
                            :teksti "Haluan syöttää joka vuoden erikseen"
                            :valitse!
                            (fn [v]
                              (let [ruksittu? (.. v -target -checked)]
                                (swap! t/taulukko-tila assoc-in [:maarat vanhempi id :joka-vuosi-erikseen?] ruksittu?)
                                (e! (t/->JokaVuosiErikseenKlikattu ruksittu? vanhempi id))))}
         joka-vuosi-erikseen?]]
       [:div.flex-row.tasaa-alas.loppuun
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
            ^{:key (str "vetolaatikko-input-" nimi "-" vuosi)}
            [:div.vetolaatikko-kentat
             [:label (str "Vuosi " vuosi "-" (inc vuosi))]
             [kentat/tee-kentta {:tyyppi :numero
                                 :elementin-id (str "vetolaatikko-input-" (vali->viiva nimi) "-" vuosi)
                                 :disabled? (not @joka-vuosi-erikseen?)                            
                                 :on-blur #(tallenna! e! 
                                             (:sopimukset-syotetty? app)
                                             :maarat
                                             (assoc rivi 
                                               :joka-vuosi-erikseen? @joka-vuosi-erikseen?
                                               :sopimuksen-tehtavamaara (.. % -target -value) 
                                               :hoitokausi vuosi))}
              (r/cursor t/taulukko-tila [:maarat vanhempi id :sopimuksen-tehtavamaarat vuosi])]]))
        [:div.vetolaatikko-kentat [:span (str yksikko)]]]])))

(defn vetolaatikko-komponentti
  [e! app rivi]
  [vetolaatikko-komponentti- e! app rivi])

(defn- sarake-disabloitu-arvo 
  [{:keys [rivi]}] 
  (if (and 
        (not (kun-kaikki-samat rivi))
        (not (samat-sopimusmaarat? rivi))
        (kun-yksikko rivi)) 
    "vaihtelua/vuosi"
    ""))

(defn- tehtava-maarat-taulukko 
  [e! {:keys [sopimukset-syotetty? taso-4-tehtavat valinnat] :as app} toimenpiteen-tiedot]
  (let [{:keys [nimi sisainen-id alue-tehtavia maara-tehtavia]} toimenpiteen-tiedot
        aluetiedot-tila (r/cursor t/taulukko-tila [:alueet sisainen-id])
        maarat-tila (r/cursor t/taulukko-tila [:maarat sisainen-id])]
    [:<>
     (when (= :kaikki (-> valinnat :toimenpide :id))
       [:h2 nimi])
     (when (> alue-tehtavia 0)
       [:<>        
        [:div.tm-otsikko {:class (str (when sopimukset-syotetty? "marginilla"))} "Hoitoluokkatiedot"]
        (when sopimukset-syotetty?
          [:div "Urakka-alueen tietoja ei tarvitse syöttää, ellei määrä ole muuttunut"])
        [grid/muokkaus-grid
         {:id (keyword (str "tehtavat-alueet-" (vali->viiva nimi)))
          :tyhja "Ladataan tietoja"
          :voi-poistaa? (constantly false)
          :jarjesta :jarjestys 
          :ulkoinen-validointi? true
          :voi-muokata? true
          :voi-lisata? false
          :disabloi-autocomplete? true
          :voi-kumota? false
          :virheet t/taulukko-virheet
          :piilota-toiminnot? true
          :on-rivi-blur (r/partial tallenna! e! sopimukset-syotetty? :alueet)}
         [{:otsikko "Tehtävä" :nimi :nimi :tyyppi :string :muokattava? (constantly false) :leveys 
           (if sopimukset-syotetty? 
             "60%"
             "70%")}
          ;; ennen urakkaa -moodi         
          {:otsikko "Tarjouksen määrä" :nimi :sopimuksen-aluetieto-maara :tyyppi :numero :leveys "180px"
           :muokattava? (constantly (if sopimukset-syotetty? false true)) :tasaa :oikea :veda-oikealle? true}
          ;; urakan ajan suunnittelu -moodi         
          (when sopimukset-syotetty? 
            {:otsikko "Muuttunut määrä" :nimi :muuttunut-aluetieto-maara :tyyppi :numero :muokattava? kun-yksikko :leveys "180px" :tasaa :oikea :veda-oikealle? true})
          {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "140px"}]
         aluetiedot-tila]])
     (when (> maara-tehtavia 0)
       [:<>
        [:div.tm-otsikko "Määrät"]
        [grid/muokkaus-grid
         (merge 
           {:id (keyword (str "tehtavat-maarat-" (vali->viiva nimi)))
            :tyhja "Ladataan tietoja"
            :voi-poistaa? (constantly false)
            :jarjesta :jarjestys 
            :ulkoinen-validointi? true
            :voi-muokata? true
            :voi-lisata? false
            :disabloi-autocomplete? true
            :voi-kumota? false
            :virheet t/taulukko-virheet
            :piilota-toiminnot? true
            :on-rivi-blur (r/partial tallenna! e! sopimukset-syotetty? :maarat)}
           (when (not sopimukset-syotetty?) 
             {:vetolaatikot
              (into {}
                (map (juxt :id (r/partial vetolaatikko-komponentti e! app)))
                taso-4-tehtavat)
              :vetolaatikot-auki t/taulukko-avatut-vetolaatikot
              :vetolaatikko-optiot {:ei-paddingia true}}))
         [(when (not sopimukset-syotetty?) 
            {:tyyppi :vetolaatikon-tila :leveys "5%"})
          {:otsikko "Tehtävä" :nimi :nimi :tyyppi :string :muokattava? (constantly false) :leveys 
           (if sopimukset-syotetty? 
             "60%"
             "70%")}
          ;; ennen urakkaa -moodi
          (when (not sopimukset-syotetty?)
            {:otsikko "Tarjouksen määrä vuodessa" :nimi :sopimuksen-tehtavamaara :tyyppi :numero :leveys "180px" 
             :muokattava? (comp kun-yksikko kun-kaikki-samat) :sarake-disabloitu-arvo-fn sarake-disabloitu-arvo
             :veda-oikealle? true :tasaa :oikea})
          ;; urakan ajan suunnittelu -moodi
          (when sopimukset-syotetty? 
            {:otsikko "Koko urakka-ajan määrä tarjouksessa" :nimi :sopimuksen-tehtavamaarat-yhteensa 
             :tyyppi :numero :muokattava? (constantly false) :leveys "160px" :tasaa :oikea :veda-oikealle? true})
          (when sopimukset-syotetty? 
            {:otsikko "Koko urakka-ajan määrää jäljellä" :nimi :sovittuja-jaljella :tyyppi :string 
             :muokattava? (constantly false) :leveys "160px" :tasaa :oikea :veda-oikealle? true})
          (when sopimukset-syotetty? 
            {:otsikko "Hoitovuoden suunniteltu määrä" :nimi :maara :tyyppi :numero :tasaa :oikea :muokattava? kun-yksikko :leveys "180px" :veda-oikealle? true})
          {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "140px"}]
         maarat-tila]])]))

(defn tehtava-maarat-taulukko-kontti
  [e! {:keys [valinnat taulukko] :as app}]
  [:div
   [debug/debug valinnat]
   [debug/debug taulukko]
   [:div 
    (doall
      (for [t (filter :nayta-toimenpide? taulukko)]        
        ^{:key (str "tehtavat" (:sisainen-id t))}
        [tehtava-maarat-taulukko e! app t]))]])

(defn sopimuksen-tallennus-boksi
  [e! virhe-sopimuksia-syottaessa?]
  [:div.table-default-even.col-xs-12
   [:div.flex-row 
    [:h3 "Syötä tarjouksen määrät"]
    [napit/yleinen-ensisijainen "Tallenna" #(e! (t/->TallennaSopimus true))]]
   (when virhe-sopimuksia-syottaessa?  
     [yleiset/info-laatikko :varoitus "Syötä kaikkiin tehtäviin määrät. Jos sopimuksessa ei ole määriä kyseiselle tehtävälle, syötä '0'" "" "100%" {:luokka "ala-margin-16"}])])

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
       [:div.flex-row
        [:h1 "Tehtävät ja määrät"]
        (when sopimukset-syotetty?
          [napit/yleinen-reunaton "Muokkaa tarjouksien määriä" #(e! (t/->TallennaSopimus false)) {:ikoni [ikonit/pencil]}])]
       [debug/debug app]
       [debug/debug @t/taulukko-tila]
       [debug/debug @t/taulukko-virheet]
       [:div "Tehtävät ja määrät suunnitellaan urakan alussa ja tarkennetaan urakan kuluessa. Osalle tehtävistä kertyy toteuneita määriä automaattisesti urakoitsijajärjestelmistä. Osa toteutuneista määristä täytyy kuitenkin kirjata manuaalisesti Toteuma-puolelle."]
       [:div "Yksiköttömiin tehtäviin ei tehdä kirjauksia."]       
       (when (not sopimukset-syotetty?)
         [yleiset/keltainen-vihjelaatikko    
          [:<>
           [:div "Urakan aluksi syötä tehtäville  tarjouksen tehtävä- ja määräluettelosta määrät. “Aluetiedot” syötetään sellaisenaan tarjouksesta. Määrät kerrotaan suunnittelua varten oletuksena hoitovuosien määrän mukaan. Jos haluat suunnitella vuosikohtaisesti niin aukaise rivi ja valitse “Haluan syöttää joka vuoden erikseen”."]
           [:div "Syötä kaikkiin tehtäviin määrät. Jos sopimuksessa ei ole määriä kyseiselle tehtävälle, syötä ‘0’.  Tarjouksien määriä käytetään apuna urakan määrien suunnitteluun ja seurantaan."]]
          :info])
       (when (not sopimukset-syotetty?) 
         [sopimuksen-tallennus-boksi e! virhe-sopimuksia-syottaessa?])
       (when sopimukset-syotetty?
         [valitaso-filtteri e! app])
       [tehtava-maarat-taulukko-kontti e! app]
       (when (not sopimukset-syotetty?) 
         [sopimuksen-tallennus-boksi e! virhe-sopimuksia-syottaessa?])])))

(defn tehtavat []
  (tuck/tuck tila/suunnittelu-tehtavat tehtavat*))
