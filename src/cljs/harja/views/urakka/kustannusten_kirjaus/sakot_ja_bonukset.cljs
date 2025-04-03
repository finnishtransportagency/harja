(ns harja.views.urakka.kustannusten-kirjaus.sakot-ja-bonukset
  "Tiemerkintöjen sakot ja bonukset välilehti"
  (:require [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [reagent.core :as r]
            [harja.ui.grid :as grid]
            [tuck.core :refer [tuck]]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.views.urakka.kustannusten-kirjaus.yhteiset :as yhteiset]
            [harja.views.urakka.kustannusten-kirjaus.sakot-bonukset-tiedot :as tiedot]))


(defn- sakot-bonukset-grid 
  "Taulukko"
  [e! rivit liitteet haku-kaynnissa?]
  [grid/grid {:tyhja (if haku-kaynnissa?
                       [ajax-loader-pieni "Haku käynnissä..."]
                       "Aikavälille ei löytynyt tuloksia.")
              :tunniste :id
              :voi-kumota? false
              :piilota-toiminnot? true
              :sivuta grid/vakiosivutus
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
     :komponentti (comp #(tiedot/laji-valinnat %) :laji)
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
     :luokka "text-nowrap"
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
     :komponentti (fn [{:keys [laatupoikkeama id]}]
                    (let [liite-linkki (or (-> laatupoikkeama :id) id)
                          rivin-liite (vec (filter #(or
                                                      (= (:laatupoikkeama %) liite-linkki)
                                                      (= (:sanktio_id %) liite-linkki)) liitteet))]
                      [liitteet/liitteet-ikoneina
                       rivin-liite
                       {:ikoni [:div.nappi-toissijainen
                                [ikonit/ikoni-ja-teksti (ikonit/link) "Avaa liite"]]}]))}]
   ;; Sorttaa gridi pvm mukaan 
   (->> rivit
     (sort-by :kasittelyaika) reverse)])


(defn- sakot-bonukset-muokkauspaneeli
  "Toteumien luonti / muokkaus"
  [e! 
   {:keys [lajit] :as _valinnat} 
   {:keys [kasittelyaika virheita?] :as valittu-rivi} kohteet liitteet voi-kirjoittaa? voi-tallentaa?]
  [:div.overlay-oikealla
   [lomake/lomake
    {:ei-borderia? true
     :voi-muokata? voi-kirjoittaa?
     :tarkkaile-ulkopuolisia-muutoksia? true
     :muokkaa! #(e! (tiedot/->MuokkaaRivia %))
     :header (let [muokataan?  (-> valittu-rivi :id some?)
                   otsikko (if muokataan? "Muokkaa kustannusta" "Lisää uusi sakko tai bonus")]
               [:div.col-md-12
                [:h2.header-yhteiset otsikko]
                [:hr]])
     :footer [:<>
              [:hr]
              [:div.muokkaus-modal-napit
               [napit/tallenna "Tallenna" #(e! (tiedot/->TallennaRivi valittu-rivi (lomake/virheita? valittu-rivi)))
                {:disabled (not voi-tallentaa?)}]
               [napit/yleinen-toissijainen "Peruuta" #(e! (tiedot/->SuljeMuokkaus))]]

              (when virheita?
                ;; Virheet on saatavilla (-> valittu-rivi ::lomake/virheet vals), mutta ei tarvi tässä näyttää toistaseen
                [yleiset/info-laatikko :varoitus yhteiset/lomake-validointi-virhe-viesti])]}

    [(lomake/rivi
       {:otsikko "Päivämäärä"
        :pakollinen? true
        :nimi :kasittelyaika
        :tyyppi :komponentti
        :validoi [[:ei-tyhja "Anna päivämäärä"]]
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
        :vaihtoehto-nayta lajit
        :vaihtoehdot (keys lajit)
        :validoi [#(when (nil? %) "Anna kustannuksen tyyppi")]})

     (lomake/rivi
       {:otsikko "Päällystys- tai paikkauskohde"
        :tyyppi :valinta
        :pakollinen? true
        :nimi :yllapitokohde
        :validoi [[:ei-tyhja "Valitse kohde"]]
        ::lomake/col-luokka "leveys-kokonainen"
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
        :validoi [[:ei-tyhja "Kirjoita kustannuksen selite"]]
        ::lomake/col-luokka "leveys-kokonainen"})

     (lomake/rivi
       {:otsikko "Kulun kohdistus"
        :pakollinen? true
        :tyyppi :komponentti
        :nimi :toimenpideinstanssi
        :validoi [[:ei-tyhja "Valitse toimenpide"]]
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
        ::lomake/col-luokka "leveys-kokonainen"})

     (lomake/rivi
       {:otsikko "Summa"
        :pakollinen? true
        :vayla-tyyli? true
        :nimi :summa
        :tyyppi :euro
        :teksti-oikealla "EUR"
        :validoi [[:ei-tyhja "Syötä kustannusarvo"]
                  [:rajattu-numero -999999999 999999999 "Anna arvo väliltä 0 - 999 999 999"]]
        ::lomake/col-luokka "col-xs-6 summa-valinta"})

     (let [liite-linkki (or
                          ;; Bonuksen liite linkittyy sanktio id:llä
                          (-> valittu-rivi :laatupoikkeama :id)
                          (-> valittu-rivi :id))
           rivin-liite (vec (filter #(or
                                       (= (:laatupoikkeama %) liite-linkki)
                                       (= (:sanktio_id %) liite-linkki)) liitteet))

           laatupoikkeama-id (-> rivin-liite first :laatupoikkeama)]

       (lomake/rivi
         {:otsikko "Liitteet"
          :nimi :liitteet
          :tyyppi :komponentti
          :komponentti (fn [_rivi]
                         [liitteet/liitteet-ja-lisays
                          @nav/valittu-urakka-id
                          rivin-liite
                          {:uusi-liite-atom (r/wrap valittu-rivi
                                              (fn [data]
                                                (e! (tiedot/->UusiLiite data))))
                           :uusi-liite-teksti "Lisää liite"
                           :salli-poistaa-lisatty-liite? true
                           :salli-poistaa-tallennettu-liite? true
                           :poista-tallennettu-liite-fn (fn [liite-id]
                                                          (liitteet/poista-liite-kannasta
                                                            {:liite-id liite-id
                                                             :domain-id laatupoikkeama-id
                                                             :domain :laatupoikkeama
                                                             :urakka-id @nav/valittu-urakka-id
                                                             :poistettu-fn #(e! (tiedot/->HaeTiedot))}))}])}))]
    valittu-rivi]])


(defn- suodattimet-lajit
  "Kaikki / Sakko / Bonus, triggeröi haun"
  [e! {:keys [valittu-laji]}]
  [kentat/tee-kentta {:vayla-tyyli? true
                      :nayta-rivina? true
                      :space-valissa? true
                      :tyyppi :radio-group
                      :vaihtoehdot [:kaikki :yllapidon_sakko :yllapidon_bonus]
                      :vaihtoehto-nayta tiedot/laji-valinnat
                      :valitse-fn #(do
                                     (e! (tiedot/->ValitseLaji %))
                                     (e! (tiedot/->HaeTiedot)))}
   (atom valittu-laji)])


(defn sakot-bonukset-listaus
  "Luodaan komponentit, ja kutsutaan yhteistä nakyma-body funktiota joka rakentaa näkymän"
  [e! {:keys [rivit valinnat muokataan
              valittu-rivi haku-kaynnissa? liitteet kohteet] :as _app}]

  (let [voi-tallentaa? true ;; Valitoidaan tallennettaessa (saavutettavuus)
        voi-kirjoittaa? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot @nav/valittu-urakka-id)

        lisaa-uusi-fn #(e! (tiedot/->AvaaModal nil))
        laji-suodatin (suodattimet-lajit e! valinnat)
        grid (sakot-bonukset-grid e! rivit liitteet haku-kaynnissa?)
        aikavali (yhteiset/paivittava-urakkavuosi-suodatin valinnat #(e! (tiedot/->HaeTiedot)))
        muokkauspaneeli (sakot-bonukset-muokkauspaneeli e! valinnat valittu-rivi kohteet liitteet voi-kirjoittaa? voi-tallentaa?)]

    (yhteiset/nakyma-body "Sakot ja bonukset" lisaa-uusi-fn aikavali valinnat muokataan muokkauspaneeli grid laji-suodatin)))


(defn sakot-ja-bonukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(e! (tiedot/->HaeTiedot)))
    (fn [e! app] [sakot-bonukset-listaus e! app])))


(defn sakot-ja-bonukset []
  [tuck tiedot/tila sakot-ja-bonukset*])
