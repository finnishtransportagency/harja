(ns harja.views.urakka.yksikkohinnat
  (:require [tuck.core :as tuck]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.yksikkohinnat :as tiedot]))

(defn- piirra-valiotsikko-caret [e! valiotsikko avatut-tehtavaryhmat]
  [:img {:alt "Expander"
         :src (if (contains? avatut-tehtavaryhmat valiotsikko)
                "images/expander-down.svg"
                "images/expander.svg")
         :tabIndex "0"
         :on-click #(e! (tiedot/->AvaaRivi valiotsikko))}])

(defn- tallennus-painikkeet [e! tallennus-kesken? tallennustila? yksikkohintaiset-tyot
                             kopioi-tuleville-vuosille? tallentamattomia-muutoksia?]
  [:div.flex-row {:style {:justify-content "right"}}
   (if tallennustila?
     [:div.painikkeet.text-right.grid-status-viestit
      [:span {:style {:margin-left "1rem"}}
       [napit/yleinen-toissijainen "Aseta kaikki tehtävät käyttämään oletusindeksiä"
        #(e! (tiedot/->AsetaKaikkiIndeksille yksikkohintaiset-tyot))
        {:ikoni (ikonit/list)
         :disabled (or tallennus-kesken? false)}]]
      [:span {:style {:margin-left "1rem"}}
       [napit/yleinen-toissijainen "Kopioi tuleville hoitovuosille"
        #(e! (tiedot/->TallennaYksikkohinnat yksikkohintaiset-tyot true))
        {:ikoni (ikonit/harja-icon-action-copy)
         :disabled (or tallennus-kesken? false)}]]
      [:span {:style {:margin-left "1rem"}}
       [napit/yleinen-ensisijainen "Tallenna"
        #(e! (tiedot/->TallennaYksikkohinnat yksikkohintaiset-tyot false))
        {:disabled (or tallennus-kesken? false)}]]
      [:span
       [napit/yleinen-toissijainen "Peruuta"
        #(e! (tiedot/->PeruutaTallennus))
        {:disabled (or tallennus-kesken? false)}]]]

     [:div.painikkeet.text-right.grid-status-viestit
      [:span {:style {:margin-left "1rem"}}
       [napit/muokkaa
        "Muokkaa oletushintoja"
        #(e! (tiedot/->ToggleTallennusTila))
        {:data-cy "btn-muokkaa-oletushintoja"}]]])])

(defn tehtavat-grid-riveiksi
  [toimenpiteet-ja-tehtavat yksikkohintaiset-tyot]
  (let [yksikkohinnat-tehtavittain (group-by :tehtava yksikkohintaiset-tyot)
        tehtavarivit (map
                        (fn [[_ _ toimenpide tehtava]]
                          (merge
                            {:tehtava (:id tehtava)
                             :tehtavan_nimi (:nimi tehtava)
                             :toimenpide-nimi (:nimi toimenpide)
                             :tehtavaryhma-id (:tehtavaryhma-id tehtava)
                             :tehtavaryhma-nimi (:tehtavaryhma-nimi tehtava)
                             :tehtavaryhmaotsikko-id (:tehtavaryhmaotsikko-id tehtava)
                             :tehtavaryhmaotsikko (:tehtavaryhmaotsikko tehtava)}
                            (first
                              (get-in yksikkohinnat-tehtavittain
                                [(:id tehtava)]))))
                       toimenpiteet-ja-tehtavat)
        ryhmat (group-by (juxt :tehtavaryhmaotsikko-id :tehtavaryhma-id) tehtavarivit)]

    (->> ryhmat
      (sort-by (comp :tehtavaryhmaotsikko first val))
      (mapcat
        (fn [[ryhma-id rivit]]
          (let [rivi (first rivit)
                otsikko (str (:tehtavaryhmaotsikko rivi)
                          " / "
                          (:tehtavaryhma-nimi rivi))]
            (cons
              {:nimi (str "ryhma-" (hash ryhma-id))
               :valiotsikko otsikko
               :tehtavaryhmaotsikko (:tehtavaryhmaotsikko rivi)
               :tehtavaryhma-id (:tehtavaryhma-id rivi)
               :tehtavaryhmaotsikko-id (:tehtavaryhmaotsikko-id rivi)
               :tehtavan_nimi otsikko
               :jarjestys (:jarjestys rivi)}
              (sort-by :jarjestys rivit))))
        ryhmat))))

(defn tehtava-taulukko
  [e! haku-kaynnissa? tallennustila? grid-rivit avatut-tehtavaryhmat]
  (let [_ (prn "yksikkohinnat-nakyman tehtava-taulukko, parametrina tulleet grid-rivit: " (pr-str grid-rivit))
        _ (prn "yksikkohinnat-nakyman tehtava-taulukko, avatut-tehtavaryhmat: " (pr-str avatut-tehtavaryhmat))
        grid-rivit
        (filter (fn [rivi]
                  (or (nil? (:tehtava rivi))
                    (contains? avatut-tehtavaryhmat
                      (:tehtavaryhmaotsikko-id rivi))))
          grid-rivit)
        solun-luokka-fn
        (fn [_ rivi]
          (when (:valiotsikko rivi)
            "valiotsikko-tausta korkea"))
        sarakkeet
        [{:otsikko ""
          :leveys "2%"
          :tyyppi :komponentti
          :komponentti
          (fn [rivi]
            (if (:valiotsikko rivi)
              (piirra-valiotsikko-caret e!
                (:tehtavaryhmaotsikko-id rivi)
                avatut-tehtavaryhmat)
              [:span]))
          :solun-luokka solun-luokka-fn}
         {:otsikko "Tehtävä"
          :leveys "50%"
          :nimi :tehtavan_nimi
          :tyyppi :komponentti
          :solun-luokka solun-luokka-fn
          :komponentti
          (fn [rivi]
            (if (:valiotsikko rivi)
              [:div.body-text.strong
               (:valiotsikko rivi)]
              [:span
               (:tehtavan_nimi rivi)]))
          :muokattava? (constantly false)}
         {:otsikko "Indeksi"
          :leveys "18%"
          :fmt #(fmt/desimaaliluku-opt-ilman-nollia %)
          :nimi :indeksi
          :tyyppi :string
          :tasaa :oikea
          :solun-luokka solun-luokka-fn
          :muokattava? (constantly false)}
         {:otsikko "Hinta ilman indeksikorjausta\u2028€ / yksikkö" :leveys "15%"
          :nimi :yksikkohinta
          :tasaa :oikea
          :tyyppi :numero
          :desimaalien-maara 2
          :solun-luokka solun-luokka-fn
          :muokattava? (fn [rivi]
                         (and tallennustila?
                           (:tehtava rivi)))}
         {:otsikko "Indeksikorjattu hinta\u2028€ / yksikkö" :leveys "15%" :nimi :indeksikorjattu-yksikkohinta
          :tyyppi :numero
          :desimaalien-maara 2
          :solun-luokka solun-luokka-fn
          :muokattava? (constantly false)
          :tasaa :oikea}]]
    [grid/grid
     {:otsikko ""
      :tyhja "Ei tietoja."
      :luokat ["matala-panel"]
      :data-cy "tehtavat-ja-maarat-grid"
      :muokkaa-aina true
      :voi-muokata? tallennustila?
      :voi-poistaa? (constantly false)
      :peruuta false
      :voi-lisata? false
      :voi-kumota? false
      :piilota-toiminnot? true
      :piilota-muokkaus? true
      :tunniste (fn [rivi]
                  (or (:tehtava rivi)
                    (:nimi rivi)))
      :jarjesta :jarjestys
      :nayta-toimintosarake? false
      :muutos #(e!
                 (tiedot/->PaivitaYksikkohinnatGrid
                   (vals (grid/hae-muokkaustila %))))}
     sarakkeet
     grid-rivit]))

(defn yksikkohinnat-nakyma
  [e! {:keys [urakka urakan-hoitokaudet valittu-hoitokausi yksikkohintaiset-tyot
              toimenpiteet-ja-tehtavat haku-kaynnissa? virhe
              tallennus-kesken? tallennustila? viimeisin-muokkaus viimeisin-muokkaaja onko-muutoksia?
              avatut-tehtavaryhmat tallentamattomia-muutoksia? haku] :as app}]
  (let [grid-rivit (tehtavat-grid-riveiksi toimenpiteet-ja-tehtavat yksikkohintaiset-tyot)
        urakan-loppuvuoden-alkuvuosi (pvm/vuosi (:loppupvm (-> @tila/tila :yleiset :urakka)))
        valitun-hoitokauden-alkuvuosi (some-> valittu-hoitokausi first pvm/vuosi)
        onko-viimeinen-vuosi? (and valitun-hoitokauden-alkuvuosi
                                (= valitun-hoitokauden-alkuvuosi
                                  urakan-loppuvuoden-alkuvuosi))]
    [:div.yksikkohinnat
     [:div.row
      [:h1 "Yksikköhinnat"]]
     [:div.flex-row
      [:div.filtteri
       [:div {:style {:width "300px"}}
        [valinnat/urakan-hoitokausi-tuck
         valittu-hoitokausi
         urakan-hoitokaudet
         #(e! (tiedot/->HoitokausiVaihdettu %))]]]
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
           "Ei tallennettuja muutoksia"])]]]

     [tallennus-painikkeet e! tallennus-kesken? tallennustila? yksikkohintaiset-tyot
      (not onko-viimeinen-vuosi?) tallentamattomia-muutoksia?]

     [tehtava-taulukko e! haku-kaynnissa? tallennustila? grid-rivit avatut-tehtavaryhmat]
     [debug/debug app]]))

(defn- yksikkohinnat*
  [e! app urakka]
  (komp/luo
    (komp/sisaan
      #(do
         (e! (tiedot/->Alusta urakka))
         (e! (tiedot/->HaeYksikkohintaisetTyot (:id urakka)))
         (e! (tiedot/->HaeToimenpiteetJaTehtavat (:id urakka)))))

    (fn [e! app]
      [yksikkohinnat-nakyma
       e!
       (assoc app :urakka urakka)])))

(defn yksikkohinnat
  [urakka]
  [tuck/tuck
   tiedot/yksikkohinnat-app-tila
   (fn [e! app]
     [yksikkohinnat* e! app urakka])])
