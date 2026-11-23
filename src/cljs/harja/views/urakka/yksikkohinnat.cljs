(ns harja.views.urakka.yksikkohinnat
  (:require [tuck.core :as tuck]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.ui.varmista-kayttajalta :as varmista]
            [harja.tiedot.urakka.yksikkohinnat :as tiedot]))

(defn- piirra-valiotsikko-caret [e! valiotsikko avatut-tehtavaryhmat]
  [:img {:alt "Expander"
         :src (if (contains? avatut-tehtavaryhmat valiotsikko)
                "images/expander-down.svg"
                "images/expander.svg")
         :tabIndex "0"
         :on-click #(e! (tiedot/->AvaaRivi valiotsikko))}])

(defn- tulevilla-vuosilla-tietoja? [app]
  (let [nykyinen-vuosi (some-> (:valittu-hoitokausi app) first pvm/vuosi)
        sopimus (first @urakka-tiedot/valittu-sopimusnumero)]
    (some
      (fn [rivi]
        (and
          (= sopimus (:sopimus rivi))
          (> (pvm/vuosi (:alkupvm rivi)) nykyinen-vuosi)
          (or (:yksikkohinta rivi)
            (:indeksi_kaytossa rivi)
            (:indeksikorjattu_yksikkohinta rivi))))
      (:kaikki-yksikkohintaiset-tyot app))))

(defn- kopioi-tuleville-vuosille! [e! app yksikkohintaiset-tyot]
  (let [tallenna! (fn []
                    (e! (tiedot/->TallennaYksikkohinnat
                          yksikkohintaiset-tyot
                          true)))]
    (if (tulevilla-vuosilla-tietoja? app)
      (varmista/varmista-kayttajalta
        {:otsikko "Tulevilla urakkavuosilla on jo tietoja"
         :sisalto [:p "Tulevilla urakkavuosilla on jo tietoja. Ylikirjoitetaanko tiedot? Ylikirjoitetut tiedot menetetään pysyvästi"]
         :hyvaksy "Ylikirjoita"
         :modal-luokka "harja-modal-keskitetty"
         :luokka "modal-dialog-keskitetty"
         :toiminto-fn tallenna!})
      (tallenna!))))

(defn- nayta-tallennusvalinta! [e! app yksikkohintaiset-tyot]
  (modal/nayta!
    {:otsikko "Haluatko tallentaa tiedot vain yhdelle urakkavuodelle?"
     :modal-luokka "harja-modal-keskitetty"
     :luokka "modal-dialog-keskitetty-leveampi yksikkohinnat-tallennus-modal"
     :footer
     [:div.yksikkohinnat-tallennus-napit
      [napit/yleinen-ensisijainen
       "Kopioi ja tallenna tiedot myös tuleville urakkavuosille"
       #(do
          (modal/piilota!)
          (kopioi-tuleville-vuosille! e! app yksikkohintaiset-tyot))]
      [napit/yleinen-toissijainen
       "Tallenna tiedot vain valitulle vuodelle"
       #(do
          (modal/piilota!)
          (e! (tiedot/->TallennaYksikkohinnat
                yksikkohintaiset-tyot
                false)))]
      [napit/peruuta
       "Peruuta"
       #(modal/piilota!)]]}
    [:p
     "Olet tallentamassa tietoja vain yhdelle vuodelle: "
     [:strong
      (fmt/hoitokauden-jarjestysluku-ja-vuodet
        (:valittu-hoitokausi app)
        (:urakan-hoitokaudet app)
        "Urakkavuosi")]
     "."]))

(defn- tallennus-painikkeet [e! app tallennus-kesken? tallennustila? yksikkohintaiset-tyot grid-rivit
                             kopioi-tuleville-vuosille? tallentamattomia-muutoksia?]
  [:div.flex-row {:style {:justify-content "right"}}
   (if tallennustila?
     [:div.painikkeet.text-right.grid-status-viestit
      [:span {:style {:margin-left "1rem"}}
       [napit/yleinen-toissijainen "Aseta kaikki tehtävät käyttämään oletusindeksiä"
        #(e! (tiedot/->AsetaKaikkiIndeksille (filterv :tehtava grid-rivit)))
        {:ikoni (ikonit/list)
         :disabled (or tallennus-kesken? false)}]]
      (when kopioi-tuleville-vuosille?
        [:span {:style {:margin-left "1rem"}}
         [napit/yleinen-toissijainen "Kopioi tuleville urakkavuosille"
          #(kopioi-tuleville-vuosille! e! app yksikkohintaiset-tyot)
          {:ikoni (ikonit/harja-icon-action-copy)
           :disabled (or tallennus-kesken? false)}]])
      [:span {:style {:margin-left "1rem"}}
       [napit/yleinen-ensisijainen "Tallenna"
        #(nayta-tallennusvalinta! e! app yksikkohintaiset-tyot)
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
                        (fn [[_ _ _ tehtava]]
                          (merge
                            {:tehtava (:id tehtava)
                             :tehtavan_nimi (:nimi tehtava)
                             :tehtavaryhma-id (:tehtavaryhma-id tehtava)
                             :tehtavaryhma-nimi (:tehtavaryhma-nimi tehtava)
                             :tehtavaryhma-jarjestys (:tehtavaryhma-jarjestys tehtava)
                             :tehtavaryhmaotsikko-id (:tehtavaryhmaotsikko-id tehtava)
                             :tehtavaryhmaotsikko (:tehtavaryhmaotsikko tehtava)}
                            (first
                              (get-in yksikkohinnat-tehtavittain
                                [(:id tehtava)]))
                            {:tehtavaryhma-avain [(:tehtavaryhmaotsikko-id tehtava)
                                                  (:tehtavaryhma-id tehtava)]}))
                       toimenpiteet-ja-tehtavat)
        ryhmat (group-by (juxt :tehtavaryhmaotsikko-id :tehtavaryhma-id) tehtavarivit)]

    (->> ryhmat
      (sort-by (fn [[_ rivit]]
                 (:tehtavaryhma-jarjestys (first rivit))))
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
               :tehtavaryhma-avain (:tehtavaryhma-avain rivi)
               :tehtavan_nimi otsikko
               :jarjestys (:jarjestys rivi)}
              (sort-by :jarjestys rivit))))))))

(defn tehtava-taulukko
  [e! haku-kaynnissa? tallennustila? grid-rivit avatut-tehtavaryhmat urakan-indeksi urakan-perusluku]
  (let [grid-rivit (filter (fn [rivi]
                            (or (nil? (:tehtava rivi))
                              (contains? avatut-tehtavaryhmat
                                (:tehtavaryhma-avain rivi))))
                     grid-rivit)
        solun-luokka-fn (fn [_ rivi]
                          (when (:valiotsikko rivi)
                            "valiotsikko-tausta korkea"))
        indeksivaihtoehdot [{:arvo true  :nimi "Kyllä"}
                            {:arvo false :nimi "Ei"}]
        sarakkeet [{:otsikko ""
                    :leveys "2%"
                    :tyyppi :komponentti
                    :komponentti
                    (fn [rivi]
                      (if (:valiotsikko rivi)
                        (piirra-valiotsikko-caret e!
                          (:tehtavaryhma-avain rivi)
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
                    :nimi :indeksi_kaytossa
                    :tyyppi :valinta
                    :valinnat indeksivaihtoehdot
                    :valinta-arvo :arvo
                    :valinta-nayta (fn [valinta]
                                     (if valinta
                                       (:nimi valinta)
                                       [:span
                                        {:title "Valinta puuttuu"}
                                        (ikonit/livicon-warning-sign)
                                        " Valitse"]))
                    :fmt (fn [indeksi-kaytossa?]
                           (cond
                             (true? indeksi-kaytossa?) (str urakan-indeksi
                                                         (when urakan-perusluku
                                                           (str ", Perusluku: " urakan-perusluku)))
                             (false? indeksi-kaytossa?) "-"
                             :else ""))
                    :tasaa :vasen
                    :solun-luokka solun-luokka-fn
                    :muokattava? (fn [rivi]
                                   (and tallennustila?
                                     (:tehtava rivi)))}
                   {:otsikko "Hinta ilman indeksikorjausta\u2028€ / yksikkö"
                    :leveys "15%"
                    :nimi :yksikkohinta
                    :tasaa :oikea
                    :tyyppi :numero
                    :fmt (partial fmt/euro-opt false)
                    :desimaalien-maara 2
                    :solun-luokka solun-luokka-fn
                    :muokattava? (fn [rivi]
                                   (and tallennustila?
                                     (:tehtava rivi)))}
                   {:otsikko-komp
                    (fn []
                      [:div.indeksikorjattu-yksikkohinta
                       [:span.indeksikorjattu-yksikkohinta-otsikko
                        "Indeksikorjattu hinta\u2028€ / yksikkö"]
                       [:button.indeksikorjattu-yksikkohinta-ikoni
                        {:type "button"
                         :title "Indeksikorjatun hinnan laskeminen"
                         :aria-label "Indeksikorjatun hinnan laskeminen"
                         :on-click
                         (fn [_]
                           (modal/nayta!
                             {:otsikko "Indeksikorjatun hinnan laskeminen"
                              :footer [napit/sulje #(modal/piilota!)]
                              :modal-luokka "harja-modal-keskitetty-leveampi"
                              :luokka "modal-dialog-keskitetty-leveampi"}
                             [:div
                              [:p "Ensimmäisenä urakkavuotena ei tehdä indeksikorjausta."]
                              [:p "Indeksikorjaus tehdään aina 2. urakkavuodesta lähtien urakkavuoden ensimmäisenä päivänä 1.10. käyttäen seuraavaa laskukaavaa:"]
                              [:p "Indeksikorjattuhinta = alkuperäinen hinta × (vertailukuukauden indeksiluku / perusluku)"]
                              [:p "Tässä urakassa on käytössä " urakan-indeksi (when urakan-perusluku (str ", jonka perusluku on " urakan-perusluku)) "."]]))}
                        (ikonit/nelio-info)]])
                    :leveys "15%"
                    :nimi :indeksikorjattu_yksikkohinta
                    :tyyppi :numero
                    :solun-luokka solun-luokka-fn
                    :muokattava? (constantly false)
                    :tasaa :oikea
                    :hae (fn [rivi]
                           (if (and (some? (:tehtava rivi))
                                 tallennustila?
                                 (nil? (:indeksi_kaytossa rivi))
                                 (nil? (:yksikkohinta rivi)))
                             "Ei saatavilla"
                             (:indeksikorjattu_yksikkohinta rivi)))
                    :fmt (fn [arvo]
                           (cond
                             (= arvo "Ei saatavilla") arvo
                             (nil? arvo) ""
                             :else (fmt/euro-opt false arvo)))}]]
    [grid/grid
     {:otsikko ""
      :tyhja (if haku-kaynnissa?
               [yleiset/ajax-loader "Haku käynnissä..."]
               "Tehtäviä ei löytynyt.")
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
      :nayta-toimintosarake? false
      :muutos #(e!
                 (tiedot/->PaivitaYksikkohinnatGrid
                   (vals (grid/hae-muokkaustila %))))}
     sarakkeet
     grid-rivit]))

(defn yksikkohinnat-nakyma
  [e! {:keys [urakka urakan-hoitokaudet valittu-hoitokausi yksikkohintaiset-tyot
              toimenpiteet-ja-tehtavat haku-kaynnissa? virhe
              tallennus-kesken? tallennustila? viimeisin-muokkaus viimeisin-muokkaaja
              avatut-tehtavaryhmat tallentamattomia-muutoksia? haku] :as app}]
  (let [grid-rivit (tehtavat-grid-riveiksi toimenpiteet-ja-tehtavat yksikkohintaiset-tyot)
        urakan-loppuvuoden-alkuvuosi (some-> urakan-hoitokaudet last first pvm/vuosi)
        valitun-hoitokauden-alkuvuosi (some-> valittu-hoitokausi first pvm/vuosi)
        onko-viimeinen-vuosi? (and valitun-hoitokauden-alkuvuosi
                                (= valitun-hoitokauden-alkuvuosi
                                  urakan-loppuvuoden-alkuvuosi))
        urakan-indeksi (:indeksi urakka)
        urakan-perusluku (:indeksilaskennan_perusluku urakka)]
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
          (and tallentamattomia-muutoksia? viimeisin-muokkaus)
          [:<>
           [:div.status-viesti.tallennettu
            (str "Viimeksi tallennettu: " (pvm/pvm-aika-klo viimeisin-muokkaus) " (" viimeisin-muokkaaja ")")]
           [:div.status-viesti.tallentamatta
            "Tallentamattomia muutoksia"]]

          tallentamattomia-muutoksia?
          [:div.status-viesti.tallentamatta
           "Tallentamattomia muutoksia"]

          viimeisin-muokkaus
          [:div.status-viesti.tallennettu
           (str "Viimeksi tallennettu: " (pvm/pvm-aika-klo viimeisin-muokkaus) " (" viimeisin-muokkaaja ")")]

          :else
          [:div.status-viesti.ei-muutoksia
           "Ei tallennettuja muutoksia"])]]]

     [tallennus-painikkeet e! app tallennus-kesken? tallennustila? yksikkohintaiset-tyot grid-rivit
      (not onko-viimeinen-vuosi?) tallentamattomia-muutoksia?]

     [tehtava-taulukko e! haku-kaynnissa? tallennustila? grid-rivit avatut-tehtavaryhmat urakan-indeksi urakan-perusluku]
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
