(ns harja.views.urakka.suunnittelu.tehtavat-maarat-nakyma
  (:require [harja.pvm :as pvm]
            [harja.ui.dom :as dom]
            [tuck.core :as tuck]
            [harja.fmt :as fmt]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.ui.napit :as napit]

            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.tehtavat-maarat-tiedot :as tiedot]

            [harja.views.urakka.valinnat :as urakka-valinnat]))

(defn- tallennus-painikkeet [e! tallennus-kesken? tallennustila? viimeisin-muokkaus viimeisin-muokkaaja
                             tehtavat-ja-maarat kopioi-tuleville-vuosille?]
  [:div.painikkeet.text-right
   [:div.grid-status-viestit
    (cond
      (and (tiedot/onko-muutoksia?) viimeisin-muokkaus)
      [:<>
       [:div.status-viesti.tallennettu
        (str "Viimeksi tallennettu: " (pvm/pvm-aika-klo viimeisin-muokkaus) " (" viimeisin-muokkaaja ")")]
       [:div.status-viesti.tallentamatta
        "Tallentamattomia muutoksia"]]

      (tiedot/onko-muutoksia?)
      [:div.status-viesti.tallentamatta
       "Tallentamattomia muutoksia"]

      viimeisin-muokkaus
      [:div.status-viesti.tallennettu
       (str "Viimeksi tallennettu: " (pvm/pvm-aika-klo viimeisin-muokkaus) " (" viimeisin-muokkaaja ")")]

      :else
      [:div.status-viesti.ei-muutoksia
       "Ei tallennettuja muutoksia"])]

   (if tallennustila?
     [:div
      (when kopioi-tuleville-vuosille?
        [:span {:style {:margin-left "1rem"}}
         [napit/yleinen-toissijainen "Kopioi tuleville hoitovuosille"
          #(e! (tiedot/->TallennaTehtavat tehtavat-ja-maarat true))
          {:disabled (or tallennus-kesken? false)
           :luokka "ikoni-16"
           :ikoni (ikonit/action-copy)}]])
      [:span {:style {:margin-left "1rem"}}
       [napit/yleinen-ensisijainen "Tallenna"
        #(e! (tiedot/->TallennaTehtavat tehtavat-ja-maarat false))
        {:disabled (or tallennus-kesken? false)}]]
      [:span {:style {:margin-left "1rem"}}
       [napit/yleinen-toissijainen "Peruuta"
        #(e! (tiedot/->PeruutaTallennus))
        {:disabled (or tallennus-kesken? false)}]]]

     [:span {:style {:margin-left "1rem"}}
      [napit/yleinen-toissijainen "Muokkaa sopimuksen määriä" #(e! (tiedot/->ToggleTallennusTila))
       {:vayla-tyyli? true}]])])

(defn- avaa-tai-sulje-haitari [event e! valiotsikko]
  (when (dom/enter-nappain? event)
    (e! (tiedot/->AvaaRivi valiotsikko))))

(defn- piirra-valiotsikko-caret [e! valiotsikko avatut-rivit]
  (if (contains? avatut-rivit valiotsikko)
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

(defn tehtava-vetolaatikko
  "Näyttää tehtävän muutokset vetolatikossa"
  [tehtava muutokset]
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first @u/valittu-hoitokausi))
        urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        hoitovuodet (into [] (range urakan-alkuvuosi urakan-loppuvuosi))]
    [:div
     [:h2 "Muutokset"]
     [:div.body-text {:style {:margin-top "-15px"}} (str tehtava ", " (fmt/hoitokauden-jarjestysluku-ja-vuodet hoitokauden-alkuvuosi hoitovuodet "Hoitovuosi"))]


     [:div.vetolaatikko-border {:style {:border-left "4px solid lightblue" :padding-left "18px"}}
      [grid/grid
       {:otsikko ""
        :voi-poistaa? (constantly false)
        :voi-lisata? false
        :piilota-toiminnot? true
        :muokkauspaneeli? false
        :jarjesta :voimassa_alkaen
        :tunniste :id
        :voi-kumota? false}
       [{:otsikko "Voimassa alkaen" :nimi :voimassa_alkaen :tyyppi :string :fmt pvm/pvm :leveys "15%"}
        {:otsikko "Edellinen määrä" :nimi :edellinen_maara :leveys "15%" :tyyppi :numero :tasaa :oikea}
        {:otsikko "Muutoksen vaikutus" :nimi :maaramuutos :leveys "15%" :tyyppi :numero :tasaa :oikea}
        {:otsikko "Muuttunut määrä" :nimi :uusi_maara :leveys "15%" :tyyppi :numero :tasaa :oikea}
        {:otsikko "Lisätieto" :nimi :syy :leveys "40%" :tyyppi :string :tasaa :vasen}]
       muutokset]]]))

(defn tehtava-taulukko [e! haku-kaynnissa? tallennustila? tehtavat-ja-maarat avatut-rivit viimeksi-klikattu]
  (let [;; Filtteröidään listasta pois ne rivit, joita ei ole aukaistu
        ;; eli ne rivit, joiden valiotsikko ei ole avatut-riveissä
        tehtavat-ja-maarat (filter (fn [rivi] (or
                                                (not (nil? (:valiotsikko rivi)))
                                                (contains? avatut-rivit (:tehtavaryhmaotsikko rivi))))
                             tehtavat-ja-maarat)
        rivit-joilla-muutos (filter #(nil? (first (:valiotsikko %))) tehtavat-ja-maarat)
        solun-luokka-fn (fn [_arvo rivi]
                          (when (or haku-kaynnissa? (some? (:valiotsikko rivi)))
                          "vaalen-tumma-tausta"))
        sarakkeet [{:otsikko "nro" :leveys "5%"
                    :tyyppi :komponentti
                    :komponentti (fn [rivi]
                                   (if (:valiotsikko rivi)
                                     (piirra-valiotsikko-caret e! (:valiotsikko rivi) avatut-rivit)
                                     [:span]))
                    :solun-luokka solun-luokka-fn}
                   {:otsikko "" :tyyppi :vetolaatikon-tila :leveys "5%" :solun-luokka solun-luokka-fn :luokka "muokattava"}
                   {:otsikko "Tehtävä"
                    :leveys "30%"
                    :nimi :tehtava
                    :solun-luokka solun-luokka-fn
                    :muokattava? (constantly true)
                    :tyyppi :komponentti
                    :komponentti (fn [{:keys [tehtava_id nimi valiotsikko]}]
                                   (if tehtava_id
                                     [:<> nimi]
                                     [:div.body-text.strong valiotsikko]))}
                   {:otsikko "Sopimuksen määrä" :leveys "15%" :nimi :tarjous_maara :tyyppi :positiivinen-numero :tasaa :oikea
                    :muokattava? #(and
                                    tallennustila?
                                    ;; Älä anna muokata väliotsikkorivejä
                                    (nil? (:valiotsikko %)))
                    :solun-luokka solun-luokka-fn}
                   {:otsikko "Muutos Muutokset" :leveys "15%" :nimi :muutos_maaramuutos :tyyppi :numero :tasaa :oikea
                    :muokattava? (constantly false) :solun-luokka solun-luokka-fn
                    :fmt (fn [arvo]
                           (if (nil? arvo) "-" arvo))}
                   {:otsikko "Muuttunut määrä" :leveys "20%" :nimi :yhteensa :tyyppi :numero :tasaa :oikea
                    :muokattava? (constantly false) :solun-luokka solun-luokka-fn
                    :fmt (fn [arvo]
                           (if (nil? arvo) "-" arvo))}
                   {:otsikko "Yksikkö" :leveys "20%" :nimi :yksikko :tyyppi :teksti :tasaa :vasen :muokattava? (constantly false) :solun-luokka solun-luokka-fn}]]
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
                         tehtavat-ja-maarat avatut-rivit] :as app}]
  (let [urakan-loppuvuoden-alkuvuosi (dec (pvm/vuosi (:loppupvm (-> @tila/tila :yleiset :urakka))))
        valitun-hoitokauden-alkuvuosi (pvm/vuosi (first @u/valittu-hoitokausi))
        onko-viimeinen-vuosi? (= valitun-hoitokauden-alkuvuosi urakan-loppuvuoden-alkuvuosi)]
    [:div#vayla
     [:div.row
      [:div.col-xs-12
       [:h1 "Tehtävät ja määrät"]]]

     [:div.flex-row {:style {:justify-content "flex-start"}}
      [:div.filtteri {:style {:width "200px"}}
       [urakka-valinnat/paivittava-urakkavuosi-tuck
        @u/valittu-aikavali
        #(e! (tiedot/->HaeTehtavatJaMaarat nil)) haku-kaynnissa? false]]]
     [:div.flex-row
      [:span "Sovitut muutokset alkuperäisiin sopimuksen tehtävämääriin kirjataan muutokset-sivulla. "
       [yleiset/linkki "Siirry muutokset-sivulle"
        #(siirtymat/siirry-annettuun-valilehteen
           @nav/valittu-hallintayksikko-id (:id @nav/valittu-urakka)
           {:taso1 :urakat
            :taso2 :mhu-muutokset
            :taso3 nil})]]]

     [tallennus-painikkeet e! tallennus-kesken? tallennustila? viimeisin-muokkaus viimeisin-muokkaaja tehtavat-ja-maarat
      (not onko-viimeinen-vuosi?)]
     [tehtava-taulukko e! haku-kaynnissa? tallennustila? tehtavat-ja-maarat avatut-rivit]
     [debug/debug app]]))

(defn tehtavat-maarat*
  [e! _]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(e! (tiedot/->HaeTehtavatJaMaarat nil)))
    (fn [e! app]
      [:div
       [nakyma e! app]])))

(defn tehtavat-maarat []
  (tuck/tuck tila/suunnittelu-tehtavat-maarat tehtavat-maarat*))
