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
            [harja.tyokalut.vieritys :as vieritys]
            [harja.pvm :as pvm]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.fmt :as fmt]))

(defn disabloitu-alasveto?
  [koll]
  (= 0 (count koll)))

(defn valitaso-filtteri
  [_ _]
  (let [{:keys [alkupvm loppupvm]} (-> @tila/tila :yleiset :urakka)]
    (fn [e! {{:keys [toimenpide-valikko-valinnat samat-tuleville toimenpide hoitokausi noudetaan nayta-aluetehtavat? nayta-suunniteltavat-tehtavat?]} :valinnat :keys [sopimukset-syotetty?] :as _app}]
      (let [vuosi (pvm/vuosi alkupvm)
            loppuvuosi (pvm/vuosi loppupvm)
            hoitokaudet (into [] (range vuosi loppuvuosi))]
        [:<>
         [:div.flex-row.sticky-ylos.valkoinen-tausta
          {:style {:justify-content "flex-start"
                   :align-items "flex-end"}}
          [:div
           {:style {:width "840px"
                    :margin-right "15px"}}
           [:label.alasvedon-otsikko "Toimenpide"]
           [yleiset/livi-pudotusvalikko {:valinta toimenpide
                                         :valitse-fn #(e! (t/->ValitseTaso % :toimenpide))
                                         :format-fn #(:nimi %)
                                         :vayla-tyyli? true
                                         :disabled (or
                                                     (not sopimukset-syotetty?)
                                                     (disabloitu-alasveto? toimenpide-valikko-valinnat))}
            toimenpide-valikko-valinnat]]
          [:div
           {:style {:width "220px"
                    :margin-right "15px"}}
           [:label.alasvedon-otsikko "Hoitokausi"]
           [yleiset/livi-pudotusvalikko {:valinta hoitokausi
                                         :valitse-fn #(e! (t/->ValitseTaso % :hoitokausi))
                                         :format-fn #(fmt/hoitokauden-jarjestysluku-ja-vuodet % hoitokaudet "Hoitovuosi")
                                         :disabled (or
                                                     (not sopimukset-syotetty?)
                                                     (disabloitu-alasveto? hoitokaudet))
                                         :vayla-tyyli? true}
            hoitokaudet]]
          [:div
           [:input#kopioi-tuleville-vuosille.vayla-checkbox
            {:type "checkbox"
             :checked samat-tuleville
             :on-change #(e! (t/->SamatTulevilleMoodi (not samat-tuleville)))
             :disabled (not sopimukset-syotetty?)}]
           [:label
            {:for "kopioi-tuleville-vuosille"}
            "Samat suunnitellut määrät tuleville hoitokausille"]]]
         [:div.flex-row.alkuun
          [:div {:style {:margin-right "15px"}} "Näytä:"]          
          [kentat/raksiboksi {:teksti "Hoitoluokkatiedot"
                              :tiivis? true
                              :toiminto #(e! (t/->NaytaAluetehtavat
                                               (not nayta-aluetehtavat?)))}
           nayta-aluetehtavat?]
          [kentat/raksiboksi {:teksti "Suunniteltavat määrät"
                              :tiivis? true
                              :toiminto #(e! (t/->NaytaSuunniteltavatTehtavat
                                               (not nayta-suunniteltavat-tehtavat?)))}
           nayta-suunniteltavat-tehtavat?]]]))))

(defn- kun-yksikko
  [rivi]
  (if-not (boolean? rivi)
    (let [{:keys [yksikko]} rivi] 
      (not (or 
             (nil? yksikko)
             (= "" yksikko)
             (= "-" yksikko)
             (= "--" yksikko))))
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

(defn- kun-ei-rahavaraus [rivi]
  (if (:rahavaraus? rivi)
    false
    rivi))

(defn- rahavarausperustainen-muokattava? [rivi]
  (if (:rahavaraus? rivi)
    false
    true))

(defn- yksikkoperustainen-muokattava? [rivi]
  (if (not= "--" (:yksikko rivi))
    true
    false))

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
                        (-> @tila/yleiset :urakka :alkupvm pvm/vuosi)
                        (-> @tila/yleiset :urakka :loppupvm pvm/vuosi))]
            ^{:key (str "vetolaatikko-input-" nimi "-" vuosi)}
            [:div.vetolaatikko-kentat
             [:label (str "Vuosi " vuosi "-" (inc vuosi))]
             [kentat/tee-kentta {:tyyppi :numero
                                 :elementin-id (str "vetolaatikko-input-" (vali->viiva nimi) "-" vuosi)
                                 :disabled? (or (not @joka-vuosi-erikseen?) (:rahavaraus? _rivi))
                                 :on-blur #(tallenna! e!
                                             (:sopimukset-syotetty? app)
                                             :maarat
                                             (assoc rivi
                                               :joka-vuosi-erikseen? @joka-vuosi-erikseen?
                                               :sopimus-maara (let [maara (.. % -target -value)]
                                                                (if-not (str/blank? maara)
                                                                  (js/parseInt maara)
                                                                  0))
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

(defn- paivita-yksikko [rivit]
  (r/atom (apply array-map (flatten (mapv
                                      ;rivit on muotoa: {<id> {<key> <value> mäppinä}}
                                      (fn [rivi]
                                        (let [[key val] rivi
                                              {:keys [yksikko rahavaraus?]} val
                                              val (assoc val :yksikko (if (or (nil? yksikko) (str/blank? yksikko) rahavaraus?) "--" yksikko))]
                                          [key val]))
                                      rivit)))))

(defn- tehtava-maarat-taulukko 
  [e! {:keys [sopimukset-syotetty? taso-4-tehtavat valinnat] :as app} toimenpiteen-tiedot]
  (let [{:keys [nimi sisainen-id alue-tehtavia maara-tehtavia]} toimenpiteen-tiedot
        {:keys [nayta-aluetehtavat? nayta-suunniteltavat-tehtavat?]} valinnat
        aluetiedot-tila (r/cursor t/taulukko-tila [:alueet sisainen-id])
        ;; Merkitse yksiköksi -- jos sitä ei ole
        aluetiedot @aluetiedot-tila
        aluetiedot-tila (if (> (count aluetiedot) 0) (paivita-yksikko aluetiedot) aluetiedot-tila)
        vetolaatikkotehtavat (filter #(and (= (:taso %) 4) (= false (:rahavaraus? %)) (= false (:aluetieto? %))) taso-4-tehtavat)
        maarat-tila (r/cursor t/taulukko-tila [:maarat sisainen-id])
        maarat @maarat-tila
        maarat-tila (if (> (count maarat) 0) (paivita-yksikko maarat) maarat-tila)
        onko-tehtavia? (cond
                         (and nayta-aluetehtavat? nayta-suunniteltavat-tehtavat?
                           (= 0 alue-tehtavia maara-tehtavia))
                         false

                         (and nayta-aluetehtavat? (not nayta-suunniteltavat-tehtavat?)
                           (= 0 alue-tehtavia))
                         false

                         (and nayta-suunniteltavat-tehtavat? (not nayta-aluetehtavat?)
                           (= 0 maara-tehtavia))
                         false

                         (and (not nayta-aluetehtavat?) (not nayta-suunniteltavat-tehtavat?))
                         false
                         
                         :else true)]
    (when onko-tehtavia?
      [:<>
       (when (= :kaikki (-> valinnat :toimenpide :id))
         [:h2 nimi])
       (when-not (or nayta-aluetehtavat? nayta-suunniteltavat-tehtavat?)
         [yleiset/keltainen-vihjelaatikko "Näytettäviä tietoja/määriä ei valittu, tarkista valinnat"])
       (when (= 0 alue-tehtavia maara-tehtavia)
         [yleiset/keltainen-vihjelaatikko "Rahavaraukset suunnitellaan kustannussuunnitelmassa"])
       (when (and (> alue-tehtavia 0) nayta-aluetehtavat?)
         [:<>        
          [:div.tm-otsikko {:class (str (when sopimukset-syotetty? "marginilla"))} "Hoitoluokkatiedot"]
          (when sopimukset-syotetty?
            [:div "Hoitoluokkatietoja ei tarvitse syöttää, ellei määrä ole muuttunut"])
          [grid/muokkaus-grid
           {:id (keyword (str "tehtavat-alueet-" (vali->viiva nimi)))
            :tyhja "Ladataan tietoja"
            :voi-poistaa? (constantly false)
            :jarjesta :jarjestys
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
            (if sopimukset-syotetty?
              {:otsikko "Tarjouksen määrä" :nimi :sopimus-maara :tyyppi :numero :leveys "180px"
               :validoi [[:ei-tyhja "Anna määrä"]]
               :muokattava? (constantly false) :tasaa :oikea :veda-oikealle? true}
              {:otsikko "Tarjouksen määrä" :nimi :sopimus-maara :tyyppi :numero :leveys "180px"
               :validoi [[:ei-tyhja "Anna määrä"]]
               :muokattava? (or (partial rahavarausperustainen-muokattava?) (partial yksikkoperustainen-muokattava?))
               :tasaa :oikea :veda-oikealle? true})
            ;; urakan ajan suunnittelu -moodi         
            (when sopimukset-syotetty?
              {:otsikko "Muuttunut määrä" :nimi :maara-muuttunut-tarjouksesta :tyyppi :numero :muokattava? (comp kun-yksikko kun-ei-rahavaraus) :leveys "180px" :tasaa :oikea :veda-oikealle? true})
            {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "140px"}]
           aluetiedot-tila]])
       (when (and (> maara-tehtavia 0) nayta-suunniteltavat-tehtavat?)
         [:<>
          [:div.tm-otsikko.suunniteltavat
           "Suunniteltavat määrät"]
          [grid/muokkaus-grid
           (merge 
             {:id (keyword (str "tehtavat-maarat-" (vali->viiva nimi)))
              :tyhja "Ladataan tietoja"
              :voi-poistaa? (constantly false)
              :jarjesta :jarjestys
              :voi-muokata? true
              :voi-lisata? false
              :disabloi-autocomplete? true
              :voi-kumota? false
              :virheet t/taulukko-virheet
              :piilota-toiminnot? true
              :korostusrajaus? true
              :on-rivi-blur (r/partial tallenna! e! sopimukset-syotetty? :maarat)}
             (when (not sopimukset-syotetty?) 
               {:vetolaatikot
                (into {}
                  (map (juxt :id (r/partial vetolaatikko-komponentti e! app)))
                  vetolaatikkotehtavat)
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
              {:otsikko "Tarjouksen määrä vuodessa" :nimi :sopimus-maara :tyyppi :numero :leveys "180px"
               :validoi [[:ei-tyhja]]
               :muokattava? (comp kun-yksikko kun-kaikki-samat kun-ei-rahavaraus) :sarake-disabloitu-arvo-fn sarake-disabloitu-arvo
               :veda-oikealle? true :tasaa :oikea})
            ;; urakan ajan suunnittelu -moodi
            (when sopimukset-syotetty? 
              {:otsikko "Koko urakka-ajan määrä tarjouksessa" :nimi :sopimuksen-tehtavamaarat-yhteensa
               :tyyppi :numero :muokattava? (constantly false) :leveys "160px" :tasaa :oikea :veda-oikealle? true})
            (when sopimukset-syotetty? 
              {:otsikko "Koko urakka-ajan määrää jäljellä" :nimi :sovittuja-jaljella :tyyppi :string 
               :muokattava? (constantly false) :leveys "160px" :tasaa :oikea :veda-oikealle? true})
            (when sopimukset-syotetty? 
              {:otsikko "Hoitovuoden suunniteltu määrä" :nimi :maara-muuttunut-tarjouksesta :tyyppi :numero :tasaa :oikea :muokattava? kun-yksikko :leveys "180px" :veda-oikealle? true})
            {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "140px"}]
           maarat-tila]])])))

(defn tehtava-maarat-taulukko-kontti
  [e! {:keys [valinnat taulukko] :as app}]
  (let [{:keys [nayta-aluetehtavat? nayta-suunniteltavat-tehtavat?]} valinnat]
    [:div
     ;[debug/debug valinnat]
     ;[debug/debug taulukko]
     (if (or nayta-aluetehtavat? nayta-suunniteltavat-tehtavat?)
       [:div 
        (doall
          (for [t (filter :nayta-toimenpide? taulukko)]        
            ^{:key (str "tehtavat" (:sisainen-id t))}
            [tehtava-maarat-taulukko e! app t]))]
       [yleiset/keltainen-vihjelaatikko "Näytettäviä tietoja/määriä ei valittu, tarkista valinnat"])]))

(defn sopimuksen-tallennus-boksi
  [e!]
  (let [aluetietoja-puuttuu? (t/aluetietoja-puuttuu?)
        maaratietoja-puuttuu? (t/maaratietoja-puuttuu?)]
    [:div.table-default-even.col-xs-12
     [:div.flex-row
      [:h3 "Syötä tarjouksen määrät"]
      [napit/yleinen-ensisijainen "Tallenna" (comp (vieritys/vierita ::top) #(e! (t/->TallennaSopimus true)))
       {:disabled (or aluetietoja-puuttuu? maaratietoja-puuttuu?)}]]
     (when (or aluetietoja-puuttuu? maaratietoja-puuttuu?)
       [yleiset/info-laatikko :neutraali
        "Jotta voit tallentaa, syötä kaikkiin tehtäviin ensin määrät. Jos sopimuksessa ei ole määriä kyseiselle tehtävälle, syötä '0'"
        "" "100%" {:luokka "ala-margin-16"}])]))

(defn kaikille-tehtaville-arvon-tallennus
  [e!]
  (when (and (k/kehitysymparistossa?)
             (roolit/roolissa? @istunto/kayttaja roolit/jarjestelmavastaava))
    [:div.table-default-even.col-xs-12
     [:div.flex-row
      [:h3 "Pikatäytä arvot kaikille tehtäville kerralla (vain testikäytössä)"]
      [napit/tallenna "Pikatäytä arvot kaikille tehtäville"
       (comp (vieritys/vierita ::top) #(when (and (k/kehitysymparistossa?)
                                                  (roolit/roolissa? @istunto/kayttaja roolit/jarjestelmavastaava))
                                         (e! (t/->TestiTallennaKaikkiinTehtaviinArvo {:hoitokausi :kaikki}))))
       {:luokka "nappi-ensisijainen"}]]]))

(defn tehtavat*
  [e! _]
  (komp/luo
    (komp/piirretty (fn [_]
                      (e! (t/->AsetaOletusHoitokausi))
                      (e! (t/->HaeSopimuksenTila))
                      (e! (t/->HaeTehtavat
                            {:hoitokausi :kaikki}))))
    (fn [e! {:keys [sopimukset-syotetty?] :as app}]
      [:div#vayla
       [vieritys/majakka ::top]
       [:div.flex-row
        [:h1 "Tehtävät ja määrät"]
        (when sopimukset-syotetty?
          [napit/yleinen-reunaton "Muokkaa tarjouksien määriä"
           (comp
             (vieritys/vierita ::top)
             #(e! (t/->TallennaSopimus false))) {:ikoni [ikonit/pencil]}])]
       ;[debug/debug app]
       ;[debug/debug @t/taulukko-tila]
       ;[debug/debug @t/taulukko-virheet]
       [:div "Tehtävät ja määrät suunnitellaan urakan alussa ja tarkennetaan urakan kuluessa. Osalle tehtävistä kertyy toteuneita määriä automaattisesti urakoitsijajärjestelmistä. Osa toteutuneista määristä täytyy kuitenkin kirjata manuaalisesti Toteuma-puolelle."]
       [:div "Yksiköttömiin tehtäviin ei tehdä kirjauksia."]
       (when (not sopimukset-syotetty?)
         [yleiset/keltainen-vihjelaatikko
          [:<>
           [:div "Urakan aluksi syötä tehtäville tarjouksen tehtävä- ja määräluettelosta määrät. Hoitoluokkatiedot syötetään sellaisenaan tarjouksesta. Suunnitellut määrät kerrotaan suunnittelua varten oletuksena hoitovuosien määrän mukaan. Jos haluat suunnitella vuosikohtaisesti niin aukaise rivi ja valitse “Haluan syöttää joka vuoden erikseen”."]
           [:div "Syötä kaikkiin tehtäviin määrät. Jos sopimuksessa ei ole määriä kyseiselle tehtävälle, syötä ‘0’. Tarjouksien määriä käytetään apuna urakan määrien suunnitteluun ja seurantaan."]]
          :info])
       [yleiset/keltainen-vihjelaatikko
        [:<>
         [:div "Määrät, joita ei voi syöttää, kuuluvat rahavaraukseen."]]
        :info]

       (when (not sopimukset-syotetty?)
         [sopimuksen-tallennus-boksi e!])
       ;; Vain pääkäyttäjille testiympäristössä mahdollisuus luoda nopeasti arvot kaikille tehtäville
       (when (and (k/kehitysymparistossa?)
               (roolit/roolissa? @istunto/kayttaja roolit/jarjestelmavastaava)
               (not sopimukset-syotetty?))
         [kaikille-tehtaville-arvon-tallennus e!])
       (when sopimukset-syotetty?
         [valitaso-filtteri e! app])
       [tehtava-maarat-taulukko-kontti e! app]
       (when (not sopimukset-syotetty?)
         [sopimuksen-tallennus-boksi e!])])))

(defn tehtavat []
  (tuck/tuck tila/suunnittelu-tehtavat tehtavat*))
