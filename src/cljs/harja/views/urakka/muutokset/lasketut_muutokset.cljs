(ns harja.views.urakka.muutokset.lasketut-muutokset
  "Muutokset välilehden 'Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset'"
  (:require [harja.fmt :as fmt]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.modal :as modal]
            [harja.ui.lomake :as lomake]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]
            [harja.tiedot.urakka.muutokset.lasketut-muutokset-tiedot :as t-lasketut]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]))


(defn- aseta-yksikkohinta-modal [e!
                                 {:keys [yksikkohinta-modal-auki?] :as _app}
                                 {:keys [tehtava aikaisemmat-yksikkohinnat] :as valittu-rivi}]
  (let [voi-kirjoittaa? true
        voi-tallentaa? (some? (:yksikkohinta valittu-rivi))]

    [modal/modal
     {:otsikko ""
      :nakyvissa? yksikkohinta-modal-auki?
      :sulje-fn #(e! (t-lasketut/->SuljeYksikkohintaModal))}

     ;; Moodalin sisältö 
     [lomake/lomake
      {:ei-borderia? true
       :voi-muokata? voi-kirjoittaa?
       :tarkkaile-ulkopuolisia-muutoksia? true
       :muokkaa! #(e! (t-lasketut/->MuokkaaYksikkohintaa (lomake/ilman-lomaketietoja %) aikaisemmat-yksikkohinnat))

       :header [:div.col-md-12
                [:h2.header-yhteiset "Aseta tehtävän yksikköhinta"]
                [:hr]
                [:div.body-caption.lihavoitu "Tehtävä"]
                [:div.body tehtava]]

       :footer (let [peruuta-fn #(e! (t-lasketut/->SuljeYksikkohintaModal))
                     tallenna-fn #(e! (t-lasketut/->TallennaYksikkohinta valittu-rivi))]
                 [:<>
                  [:hr]
                  [:div.muokkaus-modal-napit
                   [napit/tallenna "Tallenna" #(tallenna-fn) {:disabled (not voi-tallentaa?)}]
                   [napit/yleinen-toissijainen "Peruuta" #(peruuta-fn)]]])}

      [(lomake/rivi
         {:otsikko "Yksikköhinta"
          :nimi :yksikkohinta
          :tyyppi :valinta
          :pakollinen? true
          :vayla-tyyli? true
          ;; Vektori jossa mappeja,  rakenne -> hae-hoitovuosien-yksikkohinnat
          :valinnat (into [{:valinta "Ei yksikköhintaa" :arvo 0.0M}] aikaisemmat-yksikkohinnat)
          ;; Näytä :valinta -> aikaisemmat-yksikkohinnat
          :valinta-nayta #(:valinta %)
          ;; Täsmää :yksikkohinta valintojen kentän :arvo avaimeen 
          :valinta-arvo #(:arvo %)
          :validoi [#(when (nil? %) "Valitse yksikköhinta")]
          ::lomake/col-luokka "col-xs-6"})]
      valittu-rivi]

     ;; Hyrrän kuvaus: 
     ;; Näytetään Modal / dropdown, jos tehtävätoteumia (urakan.tehtavat.maara) ei oo tehty ollenkaan 
     ;; Dropdownissa pitäs tarjota tilanteen mukaan edellisten vuosien laskettu yksikköhinta
     ;; Jos niitäkään ei ole, modalia ei näytetä, ja tavoitehintamuutos pitää syöttää käsin

     ;; Jätetään "Aseta yksikköhinta"" valinta riville jos se on asetettu  
     ;; -> eli mahdollisuus päivittää 
     ;; Haetaan näkymään tullessa aina nykytilanne (mikäli data muuttunut) 
     ;; Jos kirjattu tavoitehinta manuaalisesti, ja viimevuoden dataa tulee, anna "aseta yksikköhinta" valinta 
     ]))


(defn lasketut-muutokset [e!
                          {:keys [tehtava-maaramuutokset
                                  valittu-modal-tehtava haku-kaynnissa?] :as app}]

  [yhteiset/kehystetty-avattava-grid e! app
   {:taulukon-avain :lasketut-muutokset
    :taulukon-nakyvyys-event #(e! (t-yhteiset/->ToggleTaulukonNakyvyys :lasketut-muutokset))
    :otsikko "Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset"
    :summa (reduce + 0 (map :tavoitehinnan_muutos tehtava-maaramuutokset))
    :toiminnot (fn [_e! _app]
                 [:span
                  [yleiset/vihje (str
                                   "Tavoitehintamuutosten laskennassa käytetään Harjan suunniteltuja ja toteutuneita määriä sekä palvelusopimuksen mukaisia kaavoja. "
                                   "Kirjatun määrän puuttuessa yksikköhinnan voi asettaa aikaisempien hoitovuosien perusteella. "
                                   "Yksikköhintatietojen puuttuessa tulee tavoitehinnan muutos asettaa käsin.")]])

    :taulukko
    (fn [e! _app]
      (let [;; Värjätään tällä väliotsikot design mukaiseksi 
            ;; Väliotsikot asetetaan backend 
            solun-luokka-fn (fn [_arvo rivi]
                              (when (or
                                      haku-kaynnissa?
                                      (some? (:valiotsikko rivi))) "vaalen-tumma-tausta"))]

        [:<>
         ;; "Aseta yksikköhinta" modal joka aukeaa kun rivin nappia painetaan
         ;; Tälle passataan valittu rivi / valittu tehtävä 
         [aseta-yksikkohinta-modal e! app valittu-modal-tehtava]

         ;; Tehtävä ja määrämuutos taulukko 
         [grid/grid
          {:tunniste :id
           :voi-kumota? false
           :voi-lisata? false
           :piilota-toiminnot? true
           :tallenna-vain-muokatut true
           :piilota-sivutus-footer? true
           :voi-poistaa? (constantly false)
           :voi-muokata? (not haku-kaynnissa?)
           :luokat ["lasketut-muutokset-grid"]
           ;; Tietoja ladataan ensimmäistä kertaa, näytä loaderi 
           :tyhja (if haku-kaynnissa?
                    [ajax-loader-pieni "Haku käynnissä..."]
                    "Aikavälille ei löytynyt tuloksia.")
           :tallenna (fn [sisalto]
                       (tuck-apurit/e-kanavalla! e! t-lasketut/->TallennaTehtavaMaaramuutokset sisalto))
           :rivi-jalkeen-fn (fn [rivit]
                              (let [tavoitehinnan-muutokset (map :tavoitehinnan_muutos rivit)
                                    tavoitehinnan-muutokset-yhteensa (apply + tavoitehinnan-muutokset)]
                                [{:teksti "Tavoitehinnan muutokset yhteensä" :luokka "yhteensa" :sarakkeita 7}
                                 {:teksti "" :luokka "yhteensa" :leveys 8 :tasaa :oikea}
                                 {:teksti (fmt/euro-opt false true tavoitehinnan-muutokset-yhteensa) :luokka "yhteensa" :leveys 8 :tasaa :oikea}
                                 {:teksti "" :luokka "yhteensa" :leveys 8 :tasaa :oikea}]))}

          [{:otsikko "Tehtävä"
            :nimi :tehtava
            :solun-luokka solun-luokka-fn
            :muokattava? (constantly true)
            :tyyppi :komponentti
            :komponentti (fn [{:keys [tehtava valiotsikko]}]
                           (if tehtava
                             [:<> tehtava]
                             [:div.body-text.strong valiotsikko]))
            :leveys 35}

           {:otsikko "Yksikkö"
            :nimi :yksikko
            :tyyppi :string
            :solun-luokka solun-luokka-fn
            :muokattava? (constantly false)
            :leveys 13}

           {:otsikko "Muutoksen syy / lisätieto"
            :nimi :syy
            :tyyppi :text
            :solun-luokka solun-luokka-fn
            :muokattava? #(and
                            (not haku-kaynnissa?)
                            ;; Älä anna muokata väliotsikkoja 
                            (nil? (:valiotsikko %)))
            :leveys 25}

           {:otsikko "Suunniteltu määrä"
            :nimi :suunniteltu_maara
            :tyyppi :numero
            ;; Näyttää desimaalit vain tarvittaessa
            :fmt #(fmt/desimaaliluku-opt % 0 2 true)
            :solun-luokka solun-luokka-fn
            :muokattava? (constantly false)
            :leveys 15}

           {:otsikko "Toteutunut määrä"
            :nimi :maara
            :tyyppi :numero
            :fmt #(fmt/desimaaliluku-opt % 0 3 true)
            :solun-luokka solun-luokka-fn
            :muokattava? (constantly false)
            :leveys 15}

           {:otsikko "Määrämuutos (+/-)"
            :nimi :maaramuutos
            :tyyppi :numero
            :fmt (fn [maaramuutos]
                   (if (> maaramuutos 0)
                     (str "+" (fmt/desimaaliluku-opt maaramuutos 0 2 true))
                     (fmt/desimaaliluku-opt maaramuutos 0 2 true)))
            :solun-luokka solun-luokka-fn
            :muokattava? (constantly false)
            :leveys 15}

           {:otsikko "Kohdistetut kulut (€)"
            :nimi :kirjatut_kulut_summa
            :tyyppi :numero
            :fmt fmt/euro-opt
            :solun-luokka solun-luokka-fn
            :muokattava? (constantly false)
            :leveys 15}

           {:otsikko "Yksikköhinnan keskiarvo (€)"
            :nimi :yksikkohinta
            :tyyppi :numero
            :fmt fmt/euro-opt
            :solun-luokka solun-luokka-fn
            :muokattava? (constantly false)
            :leveys 15}

           {:otsikko "Tavoitehinnan muutos (€)"
            :nimi :tavoitehinnan_muutos
            :tyyppi :numero
            :fmt (fn [v r]
                   (if (:valiotsikko r)
                     v
                     (fmt/euro-opt v)))
            :tasaa :oikea
            :solun-luokka solun-luokka-fn
            ;; Annetaanko kirjata tavoitehinta päätellään takapäässä
            :muokattava? #(and
                            (not haku-kaynnissa?)
                            (true? (:anna-kirjata-tavoitehinta? %)))
            :leveys 22}

           ;; Aseta yksikköhinta
           {:otsikko ""
            :tyyppi :komponentti
            :solun-luokka solun-luokka-fn
            :komponentti (fn [{:keys [maara tehtava_id] :as valittu-rivi}
                              {:keys [muokataan?] :as _grid}]
                           [:<>
                            ;; Näytä valinta mikäli toteumia ei ole 
                            ;; sekä aikaisemman vuoden yksikköhinta on saatavilla (anna-kirjata-tavoitehinta? kertoo tämän)
                            (when (and
                                    maara
                                    (= maara 0)
                                    (not muokataan?)
                                    (not (:anna-kirjata-tavoitehinta? valittu-rivi)))
                              [:div.nappi-toissijainen
                               {:on-click #(e! (t-lasketut/->AvaaYksikkohintaModal valittu-rivi tehtava_id))} "Aseta yksikköhinta"])])
            :leveys 22}]

          tehtava-maaramuutokset]]))}])
