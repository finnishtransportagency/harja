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

(defn- tallennus-painikkeet [e! tallennus-kesken? tallennustila? viimeisin-muokkaus viimeisin-muokkaaja
         tehtavat kopioi-tuleville-vuosille? onko-muutoksia?
         {:keys [puuttuvat-lkm puuttuvat-tehtava-idt puuttuvat-tekstit nayta-puuttuvat-lista? toggle-nayta-puuttuvat-lista!
           nayta-vain-puuttuvat? kopiointi-tuleville-tehty?]}]
  [:div.flex-row {:style {:justify-content "right"}}
   [:div.painikkeet.text-right
    [:div.grid-status-viestit
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
        "Ei tallennettuja muutoksia"])]]

   (when (or (pos? (or puuttuvat-lkm 0))
             (and tallennustila? kopioi-tuleville-vuosille?))
     [:div.painikkeet.text-right.grid-status-viestit {:style {:margin-left "1rem"}}
      [:div.flex-row {:style {:gap "0.75rem" :align-items "center" :justify-content "flex-end" :flex-wrap "wrap"}}
       (when (pos? (or puuttuvat-lkm 0))
         [:<> 
          [:span {:style {:white-space "nowrap"}} (str "Puuttuvat: " puuttuvat-lkm " kpl")]
          [:span
           [napit/yleinen-toissijainen (if nayta-vain-puuttuvat? "Näytä kaikki" "Näytä vain puuttuvat")
            #(e! (tiedot/->ToggleNaytaVainPuuttuvat))
            {:disabled (or tallennus-kesken? false)
             :vayla-tyyli? false
             :data-cy "btn-nayta-vain-puuttuvat"}]]
          [:span
           [napit/yleinen-ensisijainen (str "Aseta puuttuvat 0:ksi (" puuttuvat-lkm ")")
            #(e! (tiedot/->AsetaPuuttuvatNollaksi puuttuvat-tehtava-idt))
            {:disabled (or tallennus-kesken? false (not tallennustila?))
             :vayla-tyyli? false
             :data-cy "btn-aseta-puuttuvat-nollaksi"}]]
          [:span
           [yleiset/linkki (if nayta-puuttuvat-lista? "Piilota lista" "Näytä lista")
            toggle-nayta-puuttuvat-lista!
            {:luokka "klikattava alleviivaa"
             :data-cy "linkki-puuttuvat-nayta-lista"}]]])

       (when (and tallennustila? kopioi-tuleville-vuosille?)
         [:<> 
          [:span {:style {:white-space "nowrap"}}
           (str "Kopiointi tuleville hoitovuosille: " (if kopiointi-tuleville-tehty? "tehty" "ei tehty"))]
          [:span
           [napit/yleinen-toissijainen "Kopioi nyt"
            #(varmista/varmista-kayttajalta
               {:otsikko "Kopioidaanko tuleville hoitovuosille?"
                :sisalto [:div
                          [:div "Kopioidaan valitun hoitovuoden sopimuksen määrät kaikille tuleville hoitovuosille."]]
                :hyvaksy "Kopioi ja tallenna"
                :peruuta-txt "Peruuta"
                :napit [:tallenna :peruuta]
             :toiminto-fn (fn [] (e! (tiedot/->TallennaTehtavat tehtavat true)))})
            {:disabled (or tallennus-kesken? false)
             :vayla-tyyli? false
             :data-cy "btn-kopioi-nyt"}]]])]

      (when (and (pos? (or puuttuvat-lkm 0)) (true? nayta-puuttuvat-lista?))
        (let [esimerkit (take 5 puuttuvat-tekstit)
              loput (- (count puuttuvat-tekstit) (count esimerkit))]
          [:div {:style {:margin-top "0.5rem" :text-align "left"}}
           [:div "Asetetaan 0 näille tehtäville:"]
           [:ul {:style {:margin "0.25rem 0 0.25rem 1.25rem"}}
            (for [t esimerkit]
              ^{:key t} [:li t])]
           (when (pos? loput)
             [:div (str "ja " loput " muuta")])]))])

   (if tallennustila?
     [:div.painikkeet.text-right.grid-status-viestit
      [:span {:style {:margin-left "1rem"}}
       [napit/yleinen-ensisijainen "Tallenna"
        #(e! (tiedot/->TallennaTehtavat tehtavat false))
        {:disabled (or tallennus-kesken? false)}]]
      [:span
       [napit/yleinen-toissijainen "Peruuta"
        #(e! (tiedot/->PeruutaTallennus))
        {:disabled (or tallennus-kesken? false)}]]]

     [:div.painikkeet.text-right.grid-status-viestit
      [:span {:style {:margin-left "1rem"}}
       [napit/muokkaa
       "Muokkaa sopimuksen määriä"
        #(e! (tiedot/->ToggleTallennusTila))
        {:data-cy "btn-muokkaa-sopimuksen-maaria"}]]])])

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
    (pos? arvo) (str "+" arvo)
    (neg? arvo) (str arvo)
    :else arvo))

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
        {:otsikko "Edellinen määrä" :nimi :edellinen_maara :leveys "10%" :tyyppi :numero :tasaa :oikea}
        {:otsikko "Muutoksen vaikutus" :nimi :maaramuutos :leveys "10%" :tyyppi :numero :tasaa :oikea
         :fmt (fn [arvo] (muutoksen-vaikutus-fn arvo))}
        {:otsikko "Muuttunut määrä" :nimi :uusi_maara :leveys "10%" :tyyppi :numero :tasaa :oikea}
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
                   {:otsikko "Sopimuksen määrä" :leveys "12.5%" :nimi :tarjous_maara :tyyppi :positiivinen-numero :tasaa :oikea
                    :jos-tyhja "—"
                    :validoi (when tallennus-yritetty?
                              [[:ei-tyhja "Syötä määrä. Jos tehtävälle ei ole määrää, syötä 0"]])
                    :muokattava? #(and
                                    tallennustila?
                                    ;; Älä anna muokata väliotsikkorivejä
                                    (nil? (:valiotsikko %)))
                    :solun-luokka tarjousmaara-solun-luokka}
                   {:otsikko "Muutoksen vaikutus" :leveys "12.5%"
                    :nimi :muutos_maaramuutos
                    :solun-luokka solun-luokka-fn
                    :tasaa :oikea
                    :tyyppi :komponentti
                    :komponentti (fn [{:keys [tehtava_id muutos_maaramuutos]}]
                                   (if tehtava_id
                                     [:span (muutoksen-vaikutus-fn muutos_maaramuutos)]
                                     [:span]))}
                   {:otsikko "Muuttunut määrä" :leveys "12.5%" :nimi :yhteensa
                    :tyyppi :komponentti
                    :solun-luokka solun-luokka-fn
                    :tasaa :oikea
                    :komponentti (fn [{:keys [tehtava_id yhteensa]}]
                                   (if tehtava_id
                                     [:span yhteensa]
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

(defn nakyma [e! {:keys [haku-kaynnissa? tallennus-kaynnissa? tallennustila? tallennus-yritetty? viimeisin-muokkaus viimeisin-muokkaaja
                         tehtavat-ja-maarat avatut-tehtavaryhmat tallentamattomia-muutoksia? haku] :as app}]
  (r/with-let [nayta-puuttuvat-lista? (r/atom false)]
    (let [urakan-loppuvuoden-alkuvuosi (dec (pvm/vuosi (:loppupvm (-> @tila/tila :yleiset :urakka))))
          valitun-hoitokauden-alkuvuosi (pvm/vuosi (first @u/valittu-hoitokausi))
          onko-viimeinen-vuosi? (= valitun-hoitokauden-alkuvuosi urakan-loppuvuoden-alkuvuosi)
          nayta-vain-puuttuvat? (boolean (:nayta-vain-puuttuvat? app))
          puuttuvat-rivit (filter tiedot/puuttuuko-tarjous-maara? tehtavat-ja-maarat)
          puuttuvat-tehtava-idt (tiedot/puuttuvat-tarjous-maarat tehtavat-ja-maarat)
          puuttuvat-lkm (count puuttuvat-tehtava-idt)
          puuttuvat-tekstit (->> puuttuvat-rivit
                              (map (fn [{:keys [nimi tehtavaryhmaotsikko]}]
                                     (if tehtavaryhmaotsikko
                                       (str nimi " (" tehtavaryhmaotsikko ")")
                                       (str nimi))))
                              distinct
                              vec)
          kaikki-tehtavat (:kaikki-tehtavat app)]
      [:div#vayla
       [:div.row
        [:h1 "Tehtävät ja määrät"]]

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
      [:span "Sovitut muutokset alkuperäisiin sopimuksen tehtävämääriin kirjataan muutokset-sivulla. "
       [yleiset/linkki "Siirry muutokset-sivulle"
        #(siirtymat/siirry-annettuun-valilehteen
           @nav/valittu-hallintayksikko-id (:id @nav/valittu-urakka)
           {:taso1 :urakat
            :taso2 :mhu-muutokset
            :taso3 nil})
        {:luokka "klikattava alleviivaa"}]]]

       [tallennus-painikkeet e! tallennus-kaynnissa? tallennustila? viimeisin-muokkaus viimeisin-muokkaaja kaikki-tehtavat
      (not onko-viimeinen-vuosi?) tallentamattomia-muutoksia?
      {:puuttuvat-lkm puuttuvat-lkm
       :puuttuvat-tehtava-idt puuttuvat-tehtava-idt
       :puuttuvat-tekstit puuttuvat-tekstit
       :nayta-puuttuvat-lista? @nayta-puuttuvat-lista?
       :toggle-nayta-puuttuvat-lista! #(swap! nayta-puuttuvat-lista? not)
         :nayta-vain-puuttuvat? nayta-vain-puuttuvat?
         :kopiointi-tuleville-tehty? (boolean (:kopiointi-tuleville-tehty? app))}]

     (when tallennustila?
       [yleiset/info-laatikko :neutraali
        "Syötä kaikkiin tehtäviin määrät. Jos tehtävälle ei ole määrää, syötä 0"
        "" "100%" {:luokka "ala-margin-16"}])

       [tehtava-taulukko e! haku-kaynnissa? tallennustila? tallennus-yritetty? tehtavat-ja-maarat avatut-tehtavaryhmat]
       [debug/debug app]])))

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
