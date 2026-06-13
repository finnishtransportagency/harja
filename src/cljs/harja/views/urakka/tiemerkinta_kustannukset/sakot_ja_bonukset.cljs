(ns harja.views.urakka.tiemerkinta-kustannukset.sakot-ja-bonukset
  "Tiemerkintöjen sakot ja bonukset välilehti"
  (:require [reagent.core :as r]
            [tuck.core :refer [tuck]]

            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.urakka :as urakka-tila]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.views.urakka.tiemerkinta-kustannukset.yhteiset :as yhteiset]
            [harja.tiedot.urakka.tiemerkinta-kustannukset.sakot-ja-bonukset-tiedot :as tiedot]))


(defn- sakot-bonukset-grid
  "Taulukko"
  [e! rivit haku-kaynnissa?]
  [grid/grid {:tyhja (if haku-kaynnissa?
                       [ajax-loader-pieni "Haku käynnissä..."]
                       "Aikavälille ei löytynyt tuloksia.")
              :tunniste :id
              :voi-kumota? false
              :piilota-toiminnot? true
              :sivuta 25
              :mahdollista-rivin-valinta? true
              :rivi-klikattu #(e! (tiedot/->AvaaModal %))
              :rivi-jalkeen-fn (fn [rivit]
                                 (let [rivien-maara (count rivit)
                                       yhteensa-hinta (reduce + (map :summa rivit))]
                                   [[{:teksti "Yhteensä" :luokka "yhteensa"}
                                     {:teksti (str rivien-maara " kpl") :luokka "yhteensa"}
                                     {:luokka "yhteensa"}
                                     {:luokka "yhteensa"}
                                     {:teksti (str (fmt/euro-opt false yhteensa-hinta) " €") :tasaa :oikea :luokka "yhteensa"}
                                     {:luokka "yhteensa"}]]))}

   [{:otsikko-komp (fn [_ _]
                     [:div.pvm "Päivämäärä"
                      [:div [ikonit/action-sort-descending]]])
     :tyyppi :komponentti
     :komponentti (comp #(pvm/pvm %) :kasittelyaika)

     :luokka "caption text-nowrap"
     :leveys 0.08}

    {:otsikko "Laji"
     :tyyppi :komponentti
     :komponentti (comp #(yhteiset/laji-valinnat %) :laji)
     :luokka "text-nowrap"
     :leveys 0.05}

    {:otsikko "Kohde"
     :tyyppi :komponentti
     :komponentti #(or (-> % :yllapitokohde :nimi) tiedot/ei-kohdetta-teksti)
     :luokka "text-nowrap"
     :leveys 0.15}

    {:otsikko "Selite"
     :tyyppi :komponentti
     :komponentti #(or (:lisatieto %) (-> % :laatupoikkeama :paatos :perustelu))
     :leveys 0.15}

    {:otsikko "Määrä"
     :tyyppi :euro
     :tasaa :oikea
     :nimi :summa
     :luokka "text-nowrap"
     :leveys 0.1}

    {:otsikko "Liite"
     :tyyppi :komponentti
     :leveys 0.1
     :komponentti (fn [{:keys [liitteet] :as _rivi}]
                    [liitteet/liitteet-ikoneina
                     liitteet
                     {:ikoni [:div.nappi-toissijainen
                              [ikonit/ikoni-ja-teksti (ikonit/link) "Avaa liite"]]}])}]
   ;; Sorttaa gridi pvm mukaan 
   (->> rivit
     (sort-by :kasittelyaika) reverse)])


(defn- sakot-bonukset-muokkauspaneeli
  "Toteumien luonti / muokkaus"
  [e! {:keys [kasittelyaika virheita? liitteet uudet-liitteet] :as valittu-rivi} kohteet voi-kirjoittaa? voi-tallentaa?]
  [:div.overlay-oikealla
   [lomake/lomake
    {:ei-borderia? true
     :voi-muokata? voi-kirjoittaa?
     :tarkkaile-ulkopuolisia-muutoksia? true
     :muokkaa! #(e! (tiedot/->MuokkaaRivia %))
     :header (let [muokataan? (-> valittu-rivi :id some?)
                   otsikko (if muokataan? "Muokkaa kustannusta" "Lisää uusi sakko tai bonus")]
               [:div.col-md-12
                [:h2.header-yhteiset otsikko]
                [:hr]])

     :footer (let [peruuta-fn #(e! (tiedot/->SuljeMuokkaus))
                   tallenna-fn #(e! (tiedot/->TallennaRivi valittu-rivi (lomake/virheita? valittu-rivi) liitteet))]
               [:<>
                [:hr]
                [:div.muokkaus-modal-napit
                 [napit/tallenna "Tallenna" #(tallenna-fn) {:disabled (not voi-tallentaa?)}]
                 [napit/yleinen-toissijainen "Peruuta" #(peruuta-fn)]]

                (when virheita?
                  ;; Virheet on saatavilla (-> valittu-rivi ::lomake/virheet vals), mutta ei tarvi tässä näyttää toistaseen
                  [yleiset/info-laatikko :varoitus yhteiset/lomake-validointi-virhe-viesti])])}

    [(lomake/rivi
       {:otsikko "Päivämäärä"
        :pakollinen? true
        :nimi :kasittelyaika
        :tyyppi :komponentti
        :validoi [#(when (and virheita? (nil? %)) "Anna päivämäärä")]
        :komponentti (fn []
                       [:span
                        [kentat/tee-kentta {:tyyppi :pvm :vayla-tyyli? true}
                         (r/wrap
                           kasittelyaika
                           #(e! (tiedot/->AsetaToteumanPvm %)))]])
        ::lomake/col-luokka "col-xs-6"})

     (lomake/rivi
       {:otsikko "Laji"
        :valitse-fn #(e! (tiedot/->UusiSanktio %))
        :nimi :laji
        :pakollinen? true
        :vayla-tyyli? true
        :tyyppi :radio-group
        :vaihtoehto-nayta (dissoc yhteiset/laji-valinnat :kaikki)
        :vaihtoehdot (keys (dissoc yhteiset/laji-valinnat :kaikki))
        :validoi [#(when (and virheita? (nil? %)) "Anna kustannuksen tyyppi")]})

     (lomake/rivi
       {:otsikko "Päällystys- tai paikkauskohde"
        :tyyppi :valinta
        :pakollinen? false
        :nimi :yllapitokohde
        ::lomake/col-luokka "col-xs-6"
        :valinnat (into [{:nimi tiedot/ei-kohdetta-teksti}] kohteet)
        :valinta-nayta #(if (:id %) (:nimi %) tiedot/ei-kohdetta-teksti)})

     (lomake/rivi
       {:otsikko "Selite"
        :nimi :lomake-selite
        :tyyppi :text
        :pakollinen? true
        :salli-kirjoitus? true
        :piilota-checkbox? true
        :piilota-dropdown? true
        :validoi [#(when (and virheita? (nil? (seq %))) "Kirjoita kustannuksen selite")]
        ::lomake/col-luokka "col-xs-6"})

     (lomake/rivi
       {:otsikko "Kulun kohdistus"
        :pakollinen? true
        :tyyppi :komponentti
        :nimi :toimenpideinstanssi
        :validoi [#(when (and virheita? (nil? %)) "Valitse toimenpide")]
        :komponentti (fn [{:keys [muokkaa-lomaketta data]}]
                       (let [toimenpideinstanssit @urakka-tiedot/urakan-toimenpideinstanssit]
                         [:<>
                          [yleiset/livi-pudotusvalikko
                           {:pakollinen? true
                            :vayla-tyyli? true
                            :valitse-oletus? true
                            :format-fn :tpi_nimi
                            :valinta (first toimenpideinstanssit)
                            :valitse-fn #(muokkaa-lomaketta (assoc data :toimenpideinstanssi (:tpi_id %)))}
                           toimenpideinstanssit]]))
        ::lomake/col-luokka "col-xs-6"})

     (lomake/rivi
       {:otsikko "Summa"
        :pakollinen? true
        :vayla-tyyli? true
        :nimi :summa
        :tyyppi :euro
        :teksti-oikealla "EUR"
        :validoi [#(when (and virheita? (nil? %)) "Syötä kustannusarvo")
                  [:rajattu-numero -999999999 999999999 "Anna arvo väliltä 0 - 999 999 999"]]
        ::lomake/col-luokka "col-xs-6 summa-valinta"})

     (lomake/rivi
       {:otsikko "Liitteet"
        :nimi :liitteet
        :tyyppi :komponentti
        :komponentti (fn [_rivi]
                       [liitteet/liitteet-ja-lisays
                        @nav/valittu-urakka-id
                        liitteet
                        {:uusi-liite-atom (r/wrap
                                            uudet-liitteet
                                            (fn [data] (e! (tiedot/->UusiLiite data))))
                         :uusi-liite-teksti "Lisää liite"
                         :salli-poistaa-lisatty-liite? true
                         :salli-poistaa-tallennettu-liite? true
                         :poista-tallennettu-liite-fn (fn [liite-id]
                                                        (liitteet/poista-liite-kannasta
                                                          {:liite-id liite-id
                                                           :domain-id (-> liitteet first :laatupoikkeama)
                                                           :domain :laatupoikkeama
                                                           :urakka-id @nav/valittu-urakka-id
                                                           :poistettu-fn #(e! (tiedot/->HaeTiedot))}))}])})]
    valittu-rivi]])


(defn- suodattimet-lajit
  "Kaikki / Sakko / Bonus, triggeröi haun"
  [e! {:keys [valittu-laji]} haku-kaynnissa?]
  [kentat/tee-kentta {:vayla-tyyli? true
                      :nayta-rivina? true
                      :space-valissa? true
                      :tyyppi :radio-group
                      :disabloitu? haku-kaynnissa?
                      :vaihtoehdot [:kaikki :yllapidon_sakko :yllapidon_bonus]
                      :vaihtoehto-nayta yhteiset/laji-valinnat
                      :valitse-fn #(do
                                     (e! (tiedot/->ValitseLaji %))
                                     (e! (tiedot/->HaeTiedot)))}
   (atom valittu-laji)])


(defn sakot-bonukset-listaus
  "Luodaan komponentit, ja kutsutaan yhteistä nakyma-body funktiota joka rakentaa näkymän"
  [e! {:keys [rivit valinnat muokataan
              valittu-rivi haku-kaynnissa? kohteet] :as _app}]

  (let [voi-tallentaa? true ;; Valitoidaan tallennettaessa (saavutettavuus)
        voi-kirjoittaa? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot @nav/valittu-urakka-id)

        grid (sakot-bonukset-grid e! rivit haku-kaynnissa?)
        laji-suodatin (suodattimet-lajit e! valinnat haku-kaynnissa?)
        lisaa-uusi-fn #(e! (tiedot/->AvaaModal {:yllapitokohde {:nimi tiedot/ei-kohdetta-teksti}}))
        aikavali (yhteiset/paivittava-urakkavuosi-suodatin valinnat #(e! (tiedot/->HaeTiedot)) haku-kaynnissa? false)
        muokkauspaneeli (sakot-bonukset-muokkauspaneeli e! valittu-rivi kohteet voi-kirjoittaa? voi-tallentaa?)]

    (yhteiset/nakyma-body "Sakot ja bonukset" lisaa-uusi-fn aikavali valinnat muokataan muokkauspaneeli grid haku-kaynnissa? laji-suodatin false)))


(defn sakot-ja-bonukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(do
                    (when (urakka-tiedot/koko-urakkakausi-valittuna?) (urakka-tiedot/valitse-kuluva-hk!))
                    (e! (tiedot/->ValitseLaji :kaikki))
                    (e! (tiedot/->HaeTiedot))))
    (fn [e! app] [sakot-bonukset-listaus e! app])))


(defn sakot-ja-bonukset []
  [tuck urakka-tila/tiemerkinta-sanktiot-ja-bonukset sakot-ja-bonukset*])
