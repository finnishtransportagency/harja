(ns harja.views.urakka.suunnittelu.tehtavat-maarat-nakyma
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [harja.pvm :as pvm]
            [harja.ui.dom :as dom]
            [tuck.core :as tuck]
            [harja.fmt :as fmt]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :as kentat]
            [harja.ui.varmista-kayttajalta :as varmista]

            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.tehtavat-maarat-tiedot :as tiedot]

            [harja.views.urakka.valinnat :as urakka-valinnat]))

(defn- tallennus-status [viimeisin-muokkaus viimeisin-muokkaaja onko-muutoksia?]
  (cond
    (and onko-muutoksia? viimeisin-muokkaus)
    [:<>
     [:div.status-viesti.tallennettu
      (str "Viimeksi tallennettu: " (pvm/pvm-aika-klo viimeisin-muokkaus) " (" viimeisin-muokkaaja ")")]
     [:div.status-viesti.tallentamatta
      "Tallentamattomia muutoksia"]]

    onko-muutoksia?
    [:div.status-viesti.tallentamatta
     "Tallentamattomia muutoksia"]

    viimeisin-muokkaus
    [:div.status-viesti.tallennettu
     (str "Viimeksi tallennettu: " (pvm/pvm-aika-klo viimeisin-muokkaus) " (" viimeisin-muokkaaja ")")]

    :else
    [:div.status-viesti.ei-muutoksia
     "Ei tallennettuja muutoksia"]))

(defn- tallennus-painikkeet
  [e! tallennus-kesken? tallennustila? kaikki-tehtavat viimeisin-muokkaus viimeisin-muokkaaja tallentamattomia-muutoksia?]
  [:div.flex-row {:style {:justify-content "right"}}
   [:div.painikkeet.text-right
    [:div.grid-status-viestit
     (tallennus-status viimeisin-muokkaus viimeisin-muokkaaja tallentamattomia-muutoksia?)]
    (if tallennustila?
      [:<>
       [:span {:style {:margin-left "1rem"}}
        [napit/hyvaksy "Tallenna ja merkitse valmiiksi"
         #(e! (tiedot/->TallennaTehtavatJaMerkitseValmiiksi kaikki-tehtavat false))
         {:disabled (or tallennus-kesken? false)
          :vayla-tyyli? true
          :data-cy "btn-tallenna-ja-merkitse-valmiiksi"}]]
      [:span {:style {:margin-left "1rem"}}
       [napit/yleinen-ensisijainen "Tallenna keskeneräisenä"
         #(e! (tiedot/->TallennaTehtavat kaikki-tehtavat false))
         {:disabled (or tallennus-kesken? false)
          :data-cy "btn-tallenna-tehtavat-ja-maarat"}]]
       [:span
        [napit/yleinen-toissijainen "Peruuta"
         #(e! (tiedot/->PeruutaTallennus))
         {:disabled (or tallennus-kesken? false)
          :data-cy "btn-peruuta-tehtavat-ja-maarat"}]]]
      [:span {:style {:margin-left "1rem"}}
       [napit/muokkaa
        "Muokkaa alkuperäisen sopimuksen määriä"
        #(e! (tiedot/->ToggleTallennusTila))
        {:data-cy "btn-muokkaa-sopimuksen-maaria"}]])]])


(defn- tilanne-kortti
  [e! {:keys [tallennustila? tallennus-kaynnissa?]
       :as app}
   _tehtavat-ja-maarat kaikki-tehtavat kopiointi-relevantti?]
  (let [nayta-vain-puuttuvat? (boolean (:nayta-vain-puuttuvat? app))
        ;; Puuttuvat lasketaan aina koko datasta, jotta suodatin ei "tyhjennä" näkymää.
        puuttuvat-tehtava-idt (tiedot/puuttuvat-tarjous-maarat kaikki-tehtavat)
        puuttuvat-lkm (count puuttuvat-tehtava-idt)
        valmiiksi-yritetty? (and (true? (:tallennus-yritetty? app)) (pos? puuttuvat-lkm))
        {:keys [tulevia-vuosia-yhteensa tulevia-vuosia-joissa-syotettyja tulevia-vuosia-valmiina]
         :as tulevat-yhteenveto}
        (:tulevat-hoitovuodet-yhteenveto app)
        {:keys [menneita-vuosia-yhteensa menneita-vuosia-valmiina] :as _menneet-yhteenveto}
        (:menneet-hoitovuodet-yhteenveto app)
        tulevia-vuosia-yhteensa (or tulevia-vuosia-yhteensa 0)
        tulevia-vuosia-joissa-syotettyja (or tulevia-vuosia-joissa-syotettyja 0)
        tulevia-vuosia-valmiina (or tulevia-vuosia-valmiina 0)
        menneita-vuosia-yhteensa (or menneita-vuosia-yhteensa 0)
        menneita-vuosia-valmiina (or menneita-vuosia-valmiina 0)
        tuleville-on-jo-syotettyja? (pos? tulevia-vuosia-joissa-syotettyja)
        kopioi-napin-teksti (if tuleville-on-jo-syotettyja? "Kopioi (korvaa)" "Kopioi nyt")
        taman-hoitovuosi-valmis? (zero? puuttuvat-lkm)
        taman-hoitovuoden-tila (if taman-hoitovuosi-valmis? "valmis" "kesken")
        taman-hoitovuoden-tila-teksti (if taman-hoitovuosi-valmis? "Määrät syötetty" "Määriä puuttuu")
        menneet-valmiina? (= menneita-vuosia-valmiina menneita-vuosia-yhteensa)
        tulevat-valmiina? (= tulevia-vuosia-valmiina tulevia-vuosia-yhteensa)
        kaikki-hoitovuodet-valmiina? (and taman-hoitovuosi-valmis? menneet-valmiina? tulevat-valmiina?)
        kaikki-hoitovuodet-tila (if kaikki-hoitovuodet-valmiina? "valmis" "kesken")
        kaikki-hoitovuodet-tila-teksti (if kaikki-hoitovuodet-valmiina? "Määrät syötetty" "Määriä puuttuu")
        menneet-teksti (when (pos? menneita-vuosia-yhteensa)
                         (str "Menneet: " menneita-vuosia-valmiina "/" menneita-vuosia-yhteensa " valmiina"))
        tulevat-teksti (when (some? tulevat-yhteenveto)
                         (if (zero? tulevia-vuosia-yhteensa)
                           "Tulevat: ei tulevia"
                           (str "Tulevat: " tulevia-vuosia-valmiina "/" tulevia-vuosia-yhteensa " valmiina")))
        ;; Laatikon tyyppi: vihreä vain kun kaikki hoitovuodet ovat kunnossa
        laatikon-tyyppi (if kaikki-hoitovuodet-valmiina? :onnistunut :neutraali)]
    [yleiset/info-laatikko laatikon-tyyppi
     [:div
      [:div.flex-row {:style {:justify-content "space-between" :align-items "flex-start" :gap "1rem"}}
       [:div
        [:div.body-text.strong "Tilanne"]]

       (when tallennus-kaynnissa?
         [:div [ajax-loader-pieni]])]

      [:div {:style {:margin-top "0.5rem"}}
       (when (pos? puuttuvat-lkm)
         [:div.body-text.strong {:style {:margin-bottom "0.25rem"}}
          (if (= 1 puuttuvat-lkm)
            "Puuttuu 1 alkuperäisen sopimuksen määrä"
            (str "Puuttuu " puuttuvat-lkm " alkuperäisen sopimuksen määrää"))])

       [:div.flex-row.alkuun {:style {:gap "0.5rem" :flex-wrap "wrap" :align-items "center"}}
        [:span "Tämä hoitovuosi:"]
        [yleiset/tila-indikaattori taman-hoitovuoden-tila {:fmt-fn (constantly taman-hoitovuoden-tila-teksti)}]
        ]

        [:div.flex-row.alkuun {:style {:gap "0.5rem" :flex-wrap "wrap" :align-items "center" :margin-top "0.25rem"}}
         [:span "Kaikki hoitovuodet:"]
         [yleiset/tila-indikaattori kaikki-hoitovuodet-tila {:fmt-fn (constantly kaikki-hoitovuodet-tila-teksti)}]]

       ;; Selkeä tekstierottelu: tämä hoitovuosi vs kaikki hoitovuodet
       (when taman-hoitovuosi-valmis?
         [:div.body-text {:style {:margin-top "0.5rem"}}
          (cond
            kaikki-hoitovuodet-valmiina?
            "Kaikkien hoitovuosien alkuperäisen sopimuksen määrät on syötetty."

            tallennustila?
            [:<>
             [:span "Tämän hoitovuoden alkuperäisen sopimuksen määrät on syötetty. Voit nyt "]
             [:strong "tallentaa ja merkitä valmiiksi"]
             [:span "."]]

            :else
            "Tämän hoitovuoden alkuperäisen sopimuksen määrät on syötetty, mutta kaikkien hoitovuosien määrät eivät ole vielä syötetty.")])

       (when (and nayta-vain-puuttuvat? (zero? puuttuvat-lkm))
         [:div.body-text {:style {:margin-top "0.25rem"}}
          "Suodatin on päällä, mutta puuttuvia määriä ei ole – näkymä voi näyttää tyhjältä. Valitse Näytä kaikki nähdäksesi koko listan."])

       (when (and taman-hoitovuosi-valmis?
                  (not kaikki-hoitovuodet-valmiina?)
                  (or menneet-teksti tulevat-teksti))
         [:div {:style {:margin-top "0.75rem"}}
          [:div.body-text.strong "Muut hoitovuodet"]
          (when menneet-teksti
            [:div.body-text {:style {:margin-top "0.25rem"}} menneet-teksti])
          (when tulevat-teksti
            [:div.body-text {:style {:margin-top "0.25rem"}} tulevat-teksti])])

       (when (and tallennustila? (or (pos? puuttuvat-lkm) nayta-vain-puuttuvat?))
         [:div.body-text.strong {:style {:margin-top "0.75rem"}}
          "Puuttuvat määrät"])

       (when (and tallennustila? valmiiksi-yritetty?)
         [:div {:style {:margin-top "0.5rem"}}
          [:div.body-text.strong
           (str "Et voi merkitä valmiiksi – "
             (if (= 1 puuttuvat-lkm)
               "puuttuu vielä 1 sopimuksen määrä"
               (str "puuttuu vielä " puuttuvat-lkm " sopimuksen määrää")))]
          [:div.body-text {:style {:margin-top "0.25rem"}}
           "Täytä alkuperäisen sopimuksen puuttuvat määrät tai aseta 0. Voit tallentaa muutokset myös keskeneräisenä."]])

       (when (and tallennustila? (pos? puuttuvat-lkm))
         [:div.body-text {:style {:margin-top "0.25rem"}}
          "Jos tehtävälle ei ole määrää, syötä 0."])

       (when (and tallennustila? (or (pos? puuttuvat-lkm) nayta-vain-puuttuvat?))
         [:div.flex-row.alkuun {:style {:margin-top "0.5rem" :gap "0.75rem" :flex-wrap "wrap"}}
          [:span
           [napit/yleinen-toissijainen (if nayta-vain-puuttuvat? "Näytä kaikki" "Näytä vain puuttuvat")
            #(e! (tiedot/->ToggleNaytaVainPuuttuvat))
            {:disabled (or tallennus-kaynnissa?
                         (and (not nayta-vain-puuttuvat?) (zero? puuttuvat-lkm)))
             :vayla-tyyli? false
             :data-cy "btn-nayta-vain-puuttuvat"}]]
          (when (pos? puuttuvat-lkm)
            [:span
             [napit/yleinen-ensisijainen (str "Aseta puuttuvat 0:ksi (" puuttuvat-lkm ")")
              #(e! (tiedot/->AsetaPuuttuvatNollaksi puuttuvat-tehtava-idt))
              {:disabled (or tallennus-kaynnissa? (zero? puuttuvat-lkm))
               :vayla-tyyli? false
               :data-cy "btn-aseta-puuttuvat-nollaksi"}]])])

       (when (and (not tallennustila?) (pos? puuttuvat-lkm))
         [:div.body-text {:style {:margin-top "0.5rem"}}
          [:div
           "Täytä alkuperäisen sopimuksen määrät muokkaustilassa."
           [:div {:style {:margin-top "0.5rem"}}
            [napit/muokkaa
             "Muokkaa alkuperäisen sopimuksen määriä"
             #(e! (tiedot/->ToggleTallennusTila))
             {:data-cy "btn-muokkaa-sopimuksen-maaria-tilannekortti"}]]]])

       (when (and (not tallennustila?) nayta-vain-puuttuvat?)
         [:div {:style {:margin-top "0.75rem"}}
          [:div.body-text.strong
           (str "Suodatin aktiivinen: Näytetään vain puuttuvat (" puuttuvat-lkm ")")]
          [:div {:style {:margin-top "0.25rem"}}
           [napit/yleinen-toissijainen "Näytä kaikki"
            #(e! (tiedot/->ToggleNaytaVainPuuttuvat))
            {:disabled (or tallennus-kaynnissa? false)
             :vayla-tyyli? false
             :data-cy "btn-nayta-kaikki-ei-muokkaustilassa"}]]])

       (when (and tallennustila? kopiointi-relevantti?)
         [:div {:style {:margin-top "0.75rem"}}
          [:div.body-text.strong "Kopiointi tuleville hoitovuosille"]
          [:div {:style {:margin-top "0.5rem"}}
           [napit/yleinen-toissijainen kopioi-napin-teksti
            #(varmista/varmista-kayttajalta
               {:otsikko "Kopioidaanko tuleville hoitovuosille?"
                :sisalto [:div
                          [:div "Kopioidaan valitun hoitovuoden sopimuksen määrät kaikille tuleville hoitovuosille ja merkitään tämä hoitovuosi valmiiksi."]
                          (when tuleville-on-jo-syotettyja?
                            [:div {:style {:margin-top "0.5rem"}}
                             "Huom: kopiointi korvaa tulevien hoitovuosien nykyiset määrät."])]
                :hyvaksy "Kopioi ja merkitse valmiiksi"
                :peruuta-txt "Peruuta"
                :napit [:tallenna :peruuta]
                :toiminto-fn (fn [] (e! (tiedot/->TallennaTehtavatJaMerkitseValmiiksi kaikki-tehtavat true)))})
            {:disabled (or tallennus-kaynnissa? (pos? puuttuvat-lkm))
             :vayla-tyyli? false
             :data-cy "btn-kopioi-nyt"}]

           [:div.body-text {:style {:margin-top "0.25rem"}}
            "Kopioi tämän hoitovuoden määrät tuleville hoitovuosille."]

           (when (pos? puuttuvat-lkm)
             [:div.body-text {:style {:margin-top "0.25rem"}}
              "Täytä alkuperäisen sopimuksen puuttuvat määrät (tai aseta 0) ennen kopiointia."])

           (when tuleville-on-jo-syotettyja?
             [:div.body-text {:style {:margin-top "0.25rem"}}
              "Huom: korvaa tulevien hoitovuosien nykyiset määrät."])]])]]
     nil "100%" {:luokka "ala-margin-16"}]))


(defn- avaa-tai-sulje-haitari [event e! valiotsikko]
  (when (dom/enter-nappain? event)
    (e! (tiedot/->AvaaRivi valiotsikko))))

(defn- piirra-valiotsikko-caret [e! valiotsikko avatut-tehtavaryhmat]
  (if (contains? avatut-tehtavaryhmat valiotsikko)
    [:img {:alt "Expander"
           :src "images/expander-down.svg"
           :tabIndex "0"
           :on-click #(e! (tiedot/->AvaaRivi valiotsikko))
           :on-key-down #(avaa-tai-sulje-haitari % e! valiotsikko)}]
    [:img {:alt "Expander"
           :src "images/expander.svg"
           :tabIndex "0"
           :on-click #(e! (tiedot/->AvaaRivi valiotsikko))
           :on-key-down #(avaa-tai-sulje-haitari % e! valiotsikko)}]))

(defn muutoksen-vaikutus-fn [arvo]
  (cond
    (nil? arvo) "-"
    (pos? arvo) (str "+" (fmt/desimaaliluku-opt arvo 2))
    :else (fmt/desimaaliluku-opt arvo 2)))

(defn tehtava-vetolaatikko
  "Näyttää tehtävän muutokset vetolatikossa"
  [tehtava muutokset]
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first @u/valittu-hoitokausi))
        urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        hoitovuodet (into [] (range urakan-alkuvuosi urakan-loppuvuosi))]
    [:div
     [:h2 "Muutokset"]
     [:div.body-text {:style {:margin-top "-15px" :margin-bottom "1rem"}} (str tehtava ", " (fmt/hoitokauden-jarjestysluku-ja-vuodet hoitokauden-alkuvuosi hoitovuodet "Hoitovuosi"))]
     [:div.vetolaatikko-border {:style {:border-left "4px solid lightblue" :padding-left "18px"}}
      [grid/grid
       {:otsikko ""
        :tyhja "Ei muutoksia."
        :voi-poistaa? (constantly false)
        :voi-lisata? false
        :piilota-toiminnot? true
        :muokkauspaneeli? false
        :jarjesta :voimassa_alkaen
        :tunniste :id
        :voi-kumota? false}
       [{:otsikko "Voimassa alkaen" :nimi :voimassa_alkaen :tyyppi :string :fmt pvm/pvm :leveys "10%"}
        {:otsikko "Edellinen määrä" :nimi :edellinen_maara :leveys "10%" :tyyppi :numero :desimaalien-maara 2 :tasaa :oikea
         :fmt #(fmt/desimaaliluku-opt % 2)}
        {:otsikko "Pysyvät muutokset (+/-)" :nimi :maaramuutos :leveys "10%" :tyyppi :numero :tasaa :oikea
         :fmt (fn [arvo] (muutoksen-vaikutus-fn arvo))}
        {:otsikko "Muuttunut määrä" :nimi :uusi_maara :leveys "10%" :tyyppi :numero :desimaalien-maara 2 :tasaa :oikea
         :fmt #(fmt/desimaaliluku-opt % 2)}
        {:otsikko "Lisätieto" :nimi :syy :leveys "60%" :tyyppi :string :tasaa :vasen}]
       muutokset]]]))

(defn tehtava-taulukko [e! haku-kaynnissa? tallennustila? tallennus-yritetty? tehtavat-ja-maarat avatut-tehtavaryhmat]
  (let [;; Filtteröidään listasta pois ne rivit, joita ei ole aukaistu
        ;; eli ne rivit, joiden valiotsikko ei ole avatut-riveissä
        tehtavat-ja-maarat (filter (fn [rivi] (or
                                                (not (nil? (:valiotsikko rivi)))
                                                (contains? avatut-tehtavaryhmat (:tehtavaryhmaotsikko rivi))))
                             tehtavat-ja-maarat)
        puuttuvat-tarjous-maarat (if tallennus-yritetty?
                                   (tiedot/puuttuvat-tarjous-maarat tehtavat-ja-maarat)
                                   #{})
        rivit-joilla-muutos (filter #(nil? (first (:valiotsikko %))) tehtavat-ja-maarat)
        solun-luokka-fn (fn [_arvo rivi]
                          (when (or haku-kaynnissa? (some? (:valiotsikko rivi)))
                          "valiotsikko-tausta korkea"))
        tarjousmaara-solun-luokka (fn [arvo rivi]
                                   (let [perus (solun-luokka-fn arvo rivi)
                                         puuttuu? (and tallennus-yritetty?
                                                    (nil? (:valiotsikko rivi))
                                                    (some? (:tehtava_id rivi))
                                                    (contains? puuttuvat-tarjous-maarat (:tehtava_id rivi)))
                                         luokat (remove nil? [perus (when puuttuu? "sisaltaa-virheen")])]
                                     (when-not (empty? luokat)
                                       (str/join " " luokat))))
        sarakkeet [{:otsikko "" :leveys "1%"
                    :tyyppi :komponentti
                    :komponentti (fn [rivi]
                                   (if (:valiotsikko rivi)
                                     (piirra-valiotsikko-caret e! (:valiotsikko rivi) avatut-tehtavaryhmat)
                                     [:span]))
                    :solun-luokka solun-luokka-fn
                    :luokka "korkea"}
                   {:otsikko "" :tyyppi :vetolaatikon-tila :leveys "5%" :solun-luokka solun-luokka-fn :luokka "muokattava korkea"}
                   {:otsikko "Tehtävä"
                    :leveys "44%"
                    :nimi :tehtava
                    :solun-luokka solun-luokka-fn
                    :tyyppi :komponentti
                    :komponentti (fn [{:keys [tehtava_id nimi valiotsikko]}]
                                   (if tehtava_id
                                     [:<> nimi]
                                     [:div.body-text.strong valiotsikko]))}
                   {:otsikko "Alkuperäisen sopimuksen määrä"
                    :leveys "12.5%"
                    :fmt #(fmt/desimaaliluku-opt % 2)
                    :nimi :tarjous_maara
                    :tyyppi :positiivinen-numero
                    :desimaalien-maara 2
                    :tasaa :oikea
                    :jos-tyhja "—"
                    :validoi (when tallennus-yritetty?
                              [[:ei-tyhja "Syötä määrä. Jos tehtävälle ei ole määrää, syötä 0"]])
                    :muokattava? #(and
                                    tallennustila?
                                    ;; Älä anna muokata väliotsikkorivejä
                                    (nil? (:valiotsikko %)))
                    :solun-luokka tarjousmaara-solun-luokka}
                   {:otsikko "Pysyvät muutokset (+/-)"
                    :leveys "12.5%"
                    :nimi :muutos_maaramuutos
                    :solun-luokka solun-luokka-fn
                    :tasaa :oikea
                    :tyyppi :komponentti
                    :komponentti (fn [{:keys [tehtava_id muutos_maaramuutos]}]
                                   (if tehtava_id
                                     [:span (muutoksen-vaikutus-fn muutos_maaramuutos)]
                                     [:span]))}
                   {:otsikko "Muuttunut määrä"
                    :leveys "12.5%"
                    :nimi :yhteensa
                    :tyyppi :komponentti
                    :solun-luokka solun-luokka-fn
                    :tasaa :oikea
                    :komponentti (fn [{:keys [tehtava_id yhteensa]}]
                                   (if tehtava_id
                                     [:span (fmt/desimaaliluku-opt yhteensa 2)]
                                     [:span]))}
                   {:otsikko "Yksikkö" :leveys "12.5%" :nimi :yksikko :tyyppi :teksti :tasaa :vasen :muokattava? (constantly false) :solun-luokka solun-luokka-fn}]]
    (if haku-kaynnissa?
      [ajax-loader-pieni]
      [grid/grid
       {:otsikko ""
        :tyhja "Ei tietoja."
        :luokat ["matala-panel"]
        :data-cy "tehtavat-ja-maarat-grid"
        :muokkaa-aina true
        :voi-muokata? (or tallennustila? false)
        :voi-poistaa? (constantly false)
        :peruuta false
        :voi-lisata? false
        :voi-kumota? false
        :piilota-toiminnot? true
        :piilota-muokkaus? true
        :tunniste :nimi
        :jarjesta :jarjestys
        :nayta-toimintosarake? false
        :muutos #(do
                   (e! (tiedot/->PaivitaTehtavatGrid (vals (grid/hae-muokkaustila %)))))
        :vetolaatikot (into {}
                        (map (juxt :nimi (fn [rivi]
                                           [tehtava-vetolaatikko (:nimi rivi)
                                            (:muutokset rivi)]))
                          rivit-joilla-muutos))

        :vetolaatikko-optiot {:ei-paddingia true}}
       sarakkeet
       tehtavat-ja-maarat])))

(defn nakyma [e! {:keys [haku-kaynnissa? tallennus-kaynnissa? tallennustila? tallennus-yritetty?
                         tehtavat-ja-maarat avatut-tehtavaryhmat haku] :as app}]
  (let [urakan-loppuvuoden-alkuvuosi (dec (pvm/vuosi (:loppupvm (-> @tila/tila :yleiset :urakka))))
        valitun-hoitokauden-alkuvuosi (pvm/vuosi (first @u/valittu-hoitokausi))
        onko-viimeinen-vuosi? (= valitun-hoitokauden-alkuvuosi urakan-loppuvuoden-alkuvuosi)
        kaikki-tehtavat (:kaikki-tehtavat app)]
    [:div#vayla
     [:div.row
      [:h1 "Tehtävä ja määräluettelo"]]

     [:div.flex-row {:style {:justify-content "space-between"}}
      [:div.filtteri
       [:div {:style {:width "300px"}}
        [urakka-valinnat/paivittava-urakkavuosi-tuck
         @u/valittu-aikavali
         #(e! (tiedot/->HaeTehtavatJaMaarat nil)) haku-kaynnissa? false]]]
      [:div.label-ja-alasveto
       [:span.alasvedon-otsikko "Haku"]
       [:div.kentta {:style {:width "300px"}}
             [kentat/tee-kentta {:tyyppi :string
                                 :nimi :haku
                                 :placeholder "Hae tehtävää..."
                                 :vayla-tyyli? true
                                 :on-blur  #(e! (tiedot/->FiltteroiTehtavat (.. % -target -value)))
                                 :toiminta-f #(e! (tiedot/->FiltteroiTehtavat %))}
              (r/atom haku)]]]]
     [:div.flex-row
                [:span "Pysyvät muutokset sopimuksen määriin kirjataan muutokset-sivulla. "
       [yleiset/linkki "Siirry muutokset-sivulle"
        #(siirtymat/siirry-annettuun-valilehteen
           @nav/valittu-hallintayksikko-id (:id @nav/valittu-urakka)
           {:taso1 :urakat
            :taso2 :mhu-muutokset
            :taso3 nil})
        {:luokka "klikattava alleviivaa"}]]]

     [tilanne-kortti e! app tehtavat-ja-maarat kaikki-tehtavat (not onko-viimeinen-vuosi?)]

  [tallennus-painikkeet e! tallennus-kaynnissa? tallennustila? kaikki-tehtavat
   (:viimeisin-muokkaus app) (:viimeisin-muokkaaja app) (:tallentamattomia-muutoksia? app)]

     [tehtava-taulukko e! haku-kaynnissa? tallennustila? tallennus-yritetty? tehtavat-ja-maarat avatut-tehtavaryhmat]
     [debug/debug app]]))

(defn tehtavat-maarat* [e! _]
  (let [{:keys [sisaan ulos]}
        (nav/luo-muutosten-hallinta
          :tehtavat-maarat-nakyma/muutokset
          #(get @tila/suunnittelu-tehtavat-maarat :tallentamattomia-muutoksia?)
          :beforeunload-viesti "Tehtävä- ja määräluettelo -lomakkeella on tallentamattomia muutoksia! Jos poistut, menetät tekemäsi muutokset.")]
    (komp/luo
         (komp/lippu tiedot/nakymassa?)
         (komp/sisaan #(do
                         (e! (tiedot/->HaeTehtavatJaMaarat nil))
                         (sisaan)))
      (komp/ulos
        #(do
           (e! (tiedot/->NollaaTehtavatJaMaaratMuutokset))
           (ulos)))
         (fn [e! app]
           [:div
            [nakyma e! app]]))))

(defn tehtavat-maarat []
  (tuck/tuck tila/suunnittelu-tehtavat-maarat tehtavat-maarat*))
