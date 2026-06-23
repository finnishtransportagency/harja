(ns harja.views.urakka.suunnittelu.tehtavat-maarat-nakyma
  (:require [reagent.core :as r]
            [harja.pvm :as pvm]
            [harja.ui.dom :as dom]
            [tuck.core :as tuck]
            [harja.fmt :as fmt]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :as kentat]

            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.tehtavat-maarat-tiedot :as tiedot]

            [harja.views.urakka.valinnat :as urakka-valinnat]))

(defn- tallennus-painikkeet [e! tallennus-kesken? tallennustila? viimeisin-muokkaus viimeisin-muokkaaja
                             tehtavat-ja-maarat kopioi-tuleville-vuosille? onko-muutoksia?]
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

   (if tallennustila?
     [:div.painikkeet.text-right.grid-status-viestit
      (when kopioi-tuleville-vuosille?
        [:span {:style {:margin-left "1rem"}}
         [napit/yleinen-toissijainen "Kopioi tuleville hoitovuosille"
          #(e! (tiedot/->TallennaTehtavat tehtavat-ja-maarat true))
          {:disabled (or tallennus-kesken? false)
           :luokka "ikoni-16"
           :vayla-tyyli? false
           :ikoni (ikonit/action-copy)
           :data-cy "btn-kopioi-tuleville-hoitovuosille"}]])
      [:span {:style {:margin-left "1rem"}}
       [napit/yleinen-ensisijainen "Tallenna"
        #(e! (tiedot/->TallennaTehtavat tehtavat-ja-maarat false))
        {:disabled (or tallennus-kesken? false)}]]
      [:span
       [napit/yleinen-toissijainen "Peruuta"
        #(e! (tiedot/->PeruutaTallennus))
        {:disabled (or tallennus-kesken? false)}]]]

     [:div.painikkeet.text-right.grid-status-viestit
      [:span {:style {:margin-left "1rem"}}
       [napit/muokkaa
         "Muokkaa alkuperäisen sopimuksen määriä"
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
    (pos? arvo) (str "+" (fmt/desimaaliluku-opt-ilman-nollia arvo))
    :else (fmt/desimaaliluku-opt-ilman-nollia arvo)))

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
         :fmt #(fmt/desimaaliluku-opt-ilman-nollia %)}
        {:otsikko "Pysyvät muutokset (+/-)" :nimi :maaramuutos :leveys "10%" :tyyppi :numero :tasaa :oikea
         :fmt (fn [arvo] (muutoksen-vaikutus-fn arvo))}
        {:otsikko "Muuttunut määrä" :nimi :uusi_maara :leveys "10%" :tyyppi :numero :desimaalien-maara 2 :tasaa :oikea
         :fmt #(fmt/desimaaliluku-opt-ilman-nollia %)}
        {:otsikko "Lisätieto" :nimi :syy :leveys "60%" :tyyppi :string :tasaa :vasen}]
       muutokset]]]))


(defn tehtava-taulukko [e! haku-kaynnissa? tallennustila? tehtavat-ja-maarat avatut-tehtavaryhmat]
  (let [;; Filtteröidään listasta pois ne rivit, joita ei ole aukaistu
        ;; eli ne rivit, joiden valiotsikko ei ole avatut-riveissä
        tehtavat-ja-maarat (filter (fn [rivi] (or
                                                (not (nil? (:valiotsikko rivi)))
                                                (contains? avatut-tehtavaryhmat (:tehtavaryhmaotsikko rivi))))
                             tehtavat-ja-maarat)
        rivit-joilla-muutos (filter #(nil? (first (:valiotsikko %))) tehtavat-ja-maarat)
        solun-luokka-fn (fn [_arvo rivi]
                          (when (or haku-kaynnissa? (some? (:valiotsikko rivi)))
                          "valiotsikko-tausta korkea"))
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
                      :fmt #(fmt/desimaaliluku-opt-ilman-nollia %)
                      :nimi :tarjous_maara
                      :tyyppi :positiivinen-numero
                      :desimaalien-maara 2
                      :tasaa :oikea
                      :muokattava? #(and
                                    tallennustila?
                                    ;; Älä anna muokata väliotsikkorivejä
                                    (nil? (:valiotsikko %)))
                    :solun-luokka solun-luokka-fn}
                     {:otsikko "Pysyvät muutokset (+/-)" :leveys "12.5%"
                      :nimi :muutos_maaramuutos
                      :solun-luokka solun-luokka-fn
                      :tasaa :oikea
                      :tyyppi :numero
                      :desimaalien-maara 2
                      :muokattava? (constantly false)
                      :fmt muutoksen-vaikutus-fn}
                     {:otsikko "Muuttunut määrä" :leveys "12.5%" :nimi :yhteensa
                      :tyyppi :numero
                      :desimaalien-maara 2
                      :muokattava? (constantly false)
                      :solun-luokka solun-luokka-fn
                        :tasaa :oikea
                        :fmt #(fmt/desimaaliluku-opt-ilman-nollia %)}
                     {:otsikko "Yksikkö" :leveys "12.5%" :nimi :suunnitteluyksikko :tyyppi :teksti :tasaa :vasen :muokattava? (constantly false) :solun-luokka solun-luokka-fn}]]
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

(defn nakyma [e! {:keys [haku-kaynnissa? tallennus-kesken? tallennustila? viimeisin-muokkaus viimeisin-muokkaaja
                         tehtavat-ja-maarat avatut-tehtavaryhmat tallentamattomia-muutoksia? haku] :as app}]
  (let [urakan-loppuvuoden-alkuvuosi (dec (pvm/vuosi (:loppupvm (-> @tila/tila :yleiset :urakka))))
        valitun-hoitokauden-alkuvuosi (pvm/vuosi (first @u/valittu-hoitokausi))
        onko-viimeinen-vuosi? (= valitun-hoitokauden-alkuvuosi urakan-loppuvuoden-alkuvuosi)]
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

     [tallennus-painikkeet e! tallennus-kesken? tallennustila? viimeisin-muokkaus viimeisin-muokkaaja tehtavat-ja-maarat
      (not onko-viimeinen-vuosi?) tallentamattomia-muutoksia?]

     (when tallennustila?
       [:span "Syötä alle urakan tehtävä- ja määräluettelon mukaiset hoitoluokkatiedot ja tehtävämäärät."])

     [tehtava-taulukko e! haku-kaynnissa? tallennustila? tehtavat-ja-maarat avatut-tehtavaryhmat]
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
