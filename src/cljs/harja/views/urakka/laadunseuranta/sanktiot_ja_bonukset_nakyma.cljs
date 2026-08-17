(ns harja.views.urakka.laadunseuranta.sanktiot-ja-bonukset-nakyma
  "Sanktioiden ja bonusten välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.loki :refer [log]]
            [harja.transit :as t]

            [harja.asiakas.kommunikaatio :as k]

            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.urakka :as uu-tiedot]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot]
            [harja.tiedot.urakka.laadunseuranta.bonukset :as bonukset-tiedot]
            [harja.tiedot.urakka.laadunseuranta.arvonvahennys-tiedot :as arvonvahennys-tiedot]

            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.sivupalkki :as sivupalkki]
            [harja.ui.viesti :as viesti]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.kentat :as kentat]
            [harja.ui.debug :as debug]

            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]

            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.views.urakka.laadunseuranta.sanktiot-lomake :as sanktiot-lomake]
            [harja.views.urakka.laadunseuranta.bonukset-lomake :as bonukset-lomake]
            [harja.views.urakka.laadunseuranta.arvonvahennys-lomake :as arvonvahennys-lomake]
            [harja.ui.ikonit :as ikonit]))

;; --- Sivupaneeli sanktio- ja bonuslomakkeille ---

(defn- sivupaneelityypit [arvonvahennyslomake-kaytossa?]
  (cond-> []
    true (conj :sanktiot)
    arvonvahennyslomake-kaytossa? (conj :arvonvahennys)
    true (conj :bonukset)))

(defn bonus-sanktio-arvonvahennykset-valikko
  [tila mhu25? arvonvahennyslomake-kaytossa?]
  [:<>
   [kentat/tee-kentta {:tyyppi :radio-group
                       :vaihtoehdot (sivupaneelityypit arvonvahennyslomake-kaytossa?)
                       :vayla-tyyli? true
                       :nayta-rivina? true
                       :vaihtoehto-nayta {:sanktiot "Sanktio"
                                          :arvonvahennys "Arvonvähennys"
                                          :bonukset "Bonus"}
                       :valitse-fn (fn [arvo]
                                     ;; Alusta sanktio/bonus/arvonvähennys joka kerta kun valinta vaihdetaan, jotta uudelle tyhjälle
                                     ;; lomakkeelle ei jää aiemman lomakkeen dataa.
                                     (cond
                                       (or (= arvo :sanktiot) (and (= arvo :arvonvahennys) (not arvonvahennyslomake-kaytossa?)))
                                       (reset! tiedot/valittu-sanktio (tiedot/uusi-sanktio (:tyyppi @nav/valittu-urakka) (pvm/vuosi (first @tiedot-urakka/valittu-hoitokausi))))

                                       (and (= arvo :arvonvahennys) arvonvahennyslomake-kaytossa?)
                                       (reset! tiedot/valittu-sanktio (arvonvahennys-tiedot/uusi-arvonvahennys mhu25? (pvm/vuosi (first @tiedot-urakka/valittu-hoitokausi))))

                                       (= arvo :bonukset)
                                       (reset! tiedot/valittu-sanktio (bonukset-tiedot/uusi-bonus))))}
    tila]
   [:hr]])


(defn sivupaneeli
  [e! app sivupaneeli-auki?-atom]
  (let [tila (atom {:lukutila true :lomake :sanktiot})]
    (komp/luo
      (fn [e! app sivupaneeli-auki?-atom]
        (let [muokattu (atom @tiedot/valittu-sanktio)
              _ (when (and
                        (true? (:bonus @muokattu))
                        (not= :bonukset (:lomake @tila)))
                  (swap! tila assoc :lomake :bonukset))
              _ (when (and
                        (= :arvonvahennyssanktio (:laji @muokattu))
                        (not= :arvonvahennys (:lomake @tila)))
                  (swap! tila assoc :lomake :arvonvahennys))
              oikeus-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot
                                (:id @nav/valittu-urakka))
              muokataan-vanhaa? (some? (:id @muokattu))
              suorasanktio? (:suorasanktio @muokattu)
              lupaus? (some #{:lupaussanktio :lupausbonus} #{(:laji @muokattu)})
              lukutila? (if (not muokataan-vanhaa?) false (:lukutila @tila))
              bonusten-syotto? (= :bonukset (:lomake @tila))
              arvonvahennys-syotto? (= :arvonvahennys (:lomake @tila))
              mhu25? (and (= :teiden-hoito (:tyyppi @nav/valittu-urakka))
                       (>= (pvm/vuosi (:alkupvm @nav/valittu-urakka)) 2025))
              arvonvahennyslomake-kaytossa? (sanktio-domain/arvonvahennykset-kaytossa? @nav/valittu-urakka @tiedot-urakka/valittu-hoitokausi)]
          [:div.padding-16.ei-sulje-sivupaneelia
           [:h2 (cond
                  (and lukutila? muokataan-vanhaa?)
                  (str (sanktio-domain/sanktiolaji->teksti (:laji @muokattu)))

                  (and muokataan-vanhaa? (not bonusten-syotto?))
                  "Muokkaa sanktiota"

                  (and muokataan-vanhaa? bonusten-syotto?)
                  "Muokkaa bonusta"

                  :else
                  "Lisää uusi")]

           (when-not muokataan-vanhaa?
             [bonus-sanktio-arvonvahennykset-valikko (r/cursor tila [:lomake]) mhu25? arvonvahennyslomake-kaytossa?])

           (when (and lukutila? muokataan-vanhaa?)
             [:div.flex-row.alkuun.valistys16
              [napit/yleinen-reunaton "Muokkaa" #(swap! tila update :lukutila not)
               ;; Estä muokkaus-nappulan käyttö laatupoikkeaman kautta tehdyille sanktioille
               ;; ja urakan_paatos-taulusta haetuille sanktioille ja bonuksille
               ;; TODO: Jos/kun lupaussanktio ja lupausbonus sanktio/bonus lajeille tehdään muokkausmahdollisuus tälle lomakkeelle
               ;;       niin, poista "lupaus?" ehto. Lupausbonus ja lupaussanktio tehdään välikatselmuksessa, kun lupauspäätöstä tehdään.
               {:disabled (or (not suorasanktio?) lupaus?)}]
              (cond
                (not suorasanktio?)
                [yleiset/vihje "Lukitun laatupoikkeaman sanktiota ei voi enää muokata." nil 18]
                lupaus?
                [yleiset/vihje "Lupaussanktiota tai lupausbonusta ei voi muokata tällä lomakkeella" nil 18])])

           ; Avaa oikea lomake - arvonvahennyslomake-kaytossa?
           (cond
             bonusten-syotto?
             [bonukset-lomake/bonus-lomake sivupaneeli-auki?-atom @muokattu
              ;; Kun bonuksen tallennus tai poisto onnistuu, niin haetaan S&B-listauksen tiedot uudelleen.
              #(tiedot/paivita-sanktiot-ja-bonukset!)
              lukutila? oikeus-muokata?]
             (and arvonvahennyslomake-kaytossa? arvonvahennys-syotto?)
             [arvonvahennys-lomake/arvonvahennys-lomake e! app sivupaneeli-auki?-atom lukutila? oikeus-muokata? mhu25?]

             :else
             [sanktiot-lomake/sanktio-lomake sivupaneeli-auki?-atom lukutila? oikeus-muokata?])])))))

;; --- Sanktioiden listaus ---

(defn- lajisuodatin-valinnat [lajisuodattimet]
  [:div.lajisuodattimet
   [kentat/tee-otsikollinen-kentta
    {:otsikko "Näytä lajit"
     :otsikon-tag :div
     :luokka ""
     :kentta-params {:tyyppi :checkbox-group
                     :vaihtoehdot lajisuodattimet
                     :vaihtoehto-nayta #(:teksti (tiedot/lajisuodatin-tiedot %))
                     :label-luokka "margin-right-16"
                     :nayta-rivina? true}
     :arvo-atom tiedot/sanktio-bonus-suodattimet}]])

(defn- suodattimet-ja-toiminnot [sivupaneeli-auki?-atom lajisuodattimet]
  (let [urakan-alkuvuosi (pvm/vuosi (:alkupvm @nav/valittu-urakka))
        mahdolliset-kulun-kohdistukset (tiedot/mahdolliset-kulun-kohdistukset true urakan-alkuvuosi tiedot/valittu-sanktio)
        tpi (when (= 1 (count mahdolliset-kulun-kohdistukset))
              (:tpi_id (first mahdolliset-kulun-kohdistukset)))]
    [:div.flex-row
        [:div
         [valinnat/urakkavalinnat {:urakka @nav/valittu-urakka}
          ^{:key "urakkavalinnat"}
          [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]]]

        [:div {:style {:flex-grow 2}}
         [lajisuodatin-valinnat lajisuodattimet]]
        [:div {:style {:flex-grow 1}}
         (let [oikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot
                         (:id @nav/valittu-urakka))
               uusi-sanktio (merge
                              (tiedot/uusi-sanktio (:tyyppi @nav/valittu-urakka) (pvm/vuosi (first @tiedot-urakka/valittu-hoitokausi)))
                               {:toimenpideinstanssi tpi})
               ;; Vanhemmilla urakoilla ei ole välttämättä käsittelytapana välikatselmus.
               uusi-sanktio (if (>= urakan-alkuvuosi 2025)
                              (assoc-in uusi-sanktio [:laatupoikkeama :paatos :kasittelytapa] :valikatselmus)
                              uusi-sanktio)]
           (yleiset/wrap-if
             (not oikeus?)
             [yleiset/tooltip {} :%
              (oikeudet/oikeuden-puute-kuvaus :kirjoitus
                oikeudet/urakat-laadunseuranta-sanktiot)]
             ^{:key "Lisää uusi"}
             [:div.lisaa-nappi {:style {:float "right"}}
              [napit/uusi "Lisää uusi"
               #(do
                  (reset! sivupaneeli-auki?-atom true)
                  (reset! tiedot/valittu-sanktio uusi-sanktio))
               {:disabled (not oikeus?)}]]))]]))


(defn valitse-sanktio-tai-bonus! [rivi sanktio-atom]
  (reset! sanktio-atom rivi)
  ;; TODO: Tässä on jotakin sanktioiden liitteiden hakua valinnan yhteydessä?
  ;;       Pitääkö tunnistaa lisäksi onko bonus valittu ja hakea myös bonuksen liitteet?
  (if (= :virhe (tiedot/hae-sanktion-liitteet! (or 
                                                 (get-in rivi [:laatupoikkeama :urakka]) 
                                                 (get-in rivi [:urakka :id]))
                                               (get-in rivi [:laatupoikkeama :id])
                                               sanktio-atom))
    (viesti/nayta-toast! "Sanktion liitteiden hakeminen epäonnistui" :warning)
    (log "Liitteet haettiin onnistuneesti.")))

(defn- sanktion-tai-bonuksen-perustelu [{:keys [bonus] :as sanktio-tai-bonus}]
  ;; Bonuksille näytetään pelkästään lisätieto
  (if bonus
    [:span (:lisatieto sanktio-tai-bonus)]

    ;; Sanktioilla on kaksi vaihtoehtoista tekstiä:
    ;; Jos sanktio on ns. suorasanktio, eli tehty suoraan sanktiolomakkeella, näytetään perustelu laatupoikkeamasta.
    ;; Jos sanktio on tehty laatupoikkeamat-välilehden kautta, niin näytetään perustelun lisäksi kuvaus.
    (let [perustelu (get-in sanktio-tai-bonus [:laatupoikkeama :paatos :perustelu])
          kuvaus (get-in sanktio-tai-bonus [:laatupoikkeama :kuvaus])]
      (if (:suorasanktio sanktio-tai-bonus)
        [:span
         perustelu]

        [:<>
         (str "Laatupoikkeaman kuvaus: " kuvaus)
         [:br]
         [:br]
         (str "Päätöksen selitys: " perustelu)]))))

(defn sanktiot-ja-bonukset-listaus
  [sivupaneeli-auki?-atom]
  (let [;; TODO: Onko tämä käytännössä sama asia kuin alempi "yllapitokohdeurakka?". Ylläpitourakakka?:ssa on mukana lisäksi :valaistus-urakkatyypi
        ;;       Jos yllapitourakka? on OK, niin "yllapitokohdeurakka?" voi poistaa ja korvata viittaukset siihen "yllapitourakka?"-symbolilla.
        yllapitourakka? @tiedot-urakka/yllapitourakka?
        yllapitokohdeurakka? @tiedot-urakka/yllapitokohdeurakka?

        sanktiot (->> @tiedot/haetut-sanktiot-ja-bonukset
                   tiedot/suodata-sanktiot-ja-bonukset
                   (sort-by (if (uu-tiedot/mhu25-urakka? @nav/valittu-urakka) :maarattypvm :kasittelyaika))
                   reverse)
        hoitokauden-alku (first @tiedot-urakka/valittu-hoitokausi)
        hoitokauden-loppu (second @tiedot-urakka/valittu-hoitokausi)
        urakka-id (when @nav/valittu-urakka (:id @nav/valittu-urakka))
        urakka-nimi (when @nav/valittu-urakka (:nimi @nav/valittu-urakka))]

    [:div.sanktiot
     #_[harja.ui.debug/debug sanktiot]

     [:div.header-rivi
      [:div.laadunseuranta-otsikko
       [:h1 {:style {:width "545px"}} (if yllapitourakka? "Sakot ja bonukset" "Sanktiot, bonukset ja arvonvähennykset")]]
      [:div.header-export
       ;; Excel
       [:div
        ^{:key "raporttixls"}
        [:form {:style {:margin-left "auto"}
                :target "_blank" :method "POST"
                :action (k/excel-url :bonukset-ja-sanktiot)}
         [:input {:type "hidden" :name "parametrit"
                  :value (t/clj->transit {:urakka-id urakka-id
                                          :urakka-nimi urakka-nimi
                                          :alku hoitokauden-alku
                                          :loppu hoitokauden-loppu
                                          :suodattimet @tiedot/sanktio-bonus-suodattimet})}]
         [:button {:type "submit"
                   :class #{"nappi-toissijainen"}}
          [ikonit/ikoni-ja-teksti (ikonit/livicon-download) "Tallenna Excel"]]]]

       ;; PDF 
       [:div
        ^{:key "raporttipdf"}
        [:form {:style {:margin-left "16px"}
                :target "_blank" :method "POST"
                :action (k/pdf-url :bonukset-ja-sanktiot)}
         [:input {:type "hidden" :name "parametrit"
                  :value (t/clj->transit {:urakka-id urakka-id
                                          :urakka-nimi urakka-nimi
                                          :alku hoitokauden-alku
                                          :loppu hoitokauden-loppu
                                          :suodattimet @tiedot/sanktio-bonus-suodattimet})}] ;#{:muistutukset :sanktiot :bonukset :arvonvahennykset}
         [:button {:type "submit"
                   :class #{"nappi-toissijainen"}}
          [ikonit/ikoni-ja-teksti (ikonit/livicon-download) "Tallenna PDF"]]]]]]

     (when (uu-tiedot/mhu25-urakka? @nav/valittu-urakka)
       [:div
        [yleiset/info-laatikko :neutraali
         [:span "Tässä urakassa sanktiot ja arvonvähennykset määrätään työmaakokouksissa, mutta käsitellään vasta
          välikatselmuksissa ja vastaanottotarkastuksessa. Bonukset käsitellään välikatselmuksissa ja vastaanottotarkastuksessa."]]])

     [suodattimet-ja-toiminnot sivupaneeli-auki?-atom @tiedot/urakan-lajisuodattimet]

     [grid/grid
      {:tyhja (if @tiedot/haetut-sanktiot-ja-bonukset "Ei löytyneitä tietoja" [ajax-loader "Haetaan sanktioita."])
       :rivi-klikattu #(do
                         (reset! sivupaneeli-auki?-atom true)
                         (valitse-sanktio-tai-bonus! % tiedot/valittu-sanktio))
       :rivi-jalkeen-fn #(let [yhteensa-summat (reduce + 0 (map :summa %))]
                           [{:teksti "Yht." :luokka "lihavoitu"}
                            {:teksti (str (count %) " kpl") :sarakkeita 4 :luokka "lihavoitu"}
                            {:teksti (str (fmt/euro-opt false yhteensa-summat)) :tasaa :oikea :luokka "lihavoitu"}])}

      [(if (uu-tiedot/mhu25-urakka? @nav/valittu-urakka)
         {:otsikko "Määrätty" :nimi :maarattypvm :fmt pvm/pvm-opt :leveys 1.3}
         {:otsikko "Käsitelty" :nimi :kasittelyaika :fmt pvm/pvm-opt :leveys 1.3})
       {:otsikko "Laji" :nimi :laji :hae :laji :leveys 2.5 :fmt sanktio-domain/sanktiolaji->teksti}
       (when yllapitokohdeurakka?
         {:otsikko "Kohde" :nimi :kohde :leveys 2
          :hae (fn [rivi]
                 (if (get-in rivi [:yllapitokohde :id])
                   (yllapitokohde-domain/yllapitokohde-tekstina {:kohdenumero (get-in rivi [:yllapitokohde :numero])
                                                                 :nimi (get-in rivi [:yllapitokohde :nimi])})
                   "Ei liity kohteeseen"))})
       (if yllapitourakka?
         {:otsikko "Kuvaus" :nimi :vakiofraasi
          :hae #(sanktio-domain/yllapidon-sanktiofraasin-nimi (:vakiofraasi %)) :leveys 3}
         {:otsikko "Tyyppi" :nimi :sanktiotyyppi :hae (comp :nimi :tyyppi)
          :leveys 2.5 :fmt #(cond
                              (and % (= "Ei tarvita sanktiotyyppiä" %)) "–"
                              (and % (not= "Ei tarvita sanktiotyyppiä" %)) %
                              :else "–")})
       (when (not yllapitourakka?)
         {:otsikko "Tapah\u00ADtuma\u00ADpaik\u00ADka/kuvaus" :nimi :tapahtumapaikka
          :tyyppi :komponentti :komponentti tiedot/sanktion-tai-bonuksen-kuvaus :leveys 3})
       {:otsikko "Perustelu" :nimi :perustelu :leveys 3
        :tyyppi :komponentti :komponentti sanktion-tai-bonuksen-perustelu}
       {:otsikko "Määrä (€)" :nimi :summa :leveys 1.2 :tyyppi :numero :tasaa :oikea
        :hae #(or (fmt/euro-opt false (:summa %))
                "Muistutus")}]
      sanktiot]
     (when yllapitourakka?
       (yleiset/vihje "Huom! Sakot ovat miinusmerkkisiä ja bonukset plusmerkkisiä."))]))

(defn sanktiot-ja-bonukset [e! app]
  (let [sivupaneeli-auki? (r/atom false)]
    (komp/luo
      (komp/lippu tiedot/nakymassa?)
      (komp/sisaan-ulos #(do
                           (e! (arvonvahennys-tiedot/->HaeKaikkiTehtavaryhmat))
                           (reset! tiedot-urakka/default-hoitokausi {:ylikirjoita? true
                                                                     :default nil}))
        #(reset! tiedot-urakka/default-hoitokausi {:ylikirjoita? false}))
      (fn [e! app]
        [:div.laadunseuranta
         (when @sivupaneeli-auki?
           [sivupalkki/oikea
            {:leveys "600px" :sulku-fn #(do
                                          (reset! sivupaneeli-auki? false)
                                          (reset! tiedot/valittu-sanktio nil))}
            [sivupaneeli e! app sivupaneeli-auki?]])
         [sanktiot-ja-bonukset-listaus sivupaneeli-auki?]
         [debug/debug app]]))))
