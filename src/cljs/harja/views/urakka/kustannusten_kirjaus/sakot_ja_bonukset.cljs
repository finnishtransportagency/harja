(ns harja.views.urakka.kustannusten-kirjaus.sakot-ja-bonukset
  "Tiemerkintöjen sakot ja bonukset välilehti"
  (:require [harja.pvm :as pvm]
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
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.views.urakka.kustannusten-kirjaus.yhteiset :as yhteiset]
            [harja.views.urakka.kustannusten-kirjaus.sakot-bonukset-tiedot :as tiedot]))


(defn- sakot-bonukset-grid 
  "Taulukko"
  [e! rivit liitteet haku-kaynnissa?]
  [grid/grid {:tyhja (if haku-kaynnissa?
                       [ajax-loader-pieni "Haku käynnissä..."]
                       "Valitulle aikavälille ei löytynyt mitään.")
              :tunniste :id
              :voi-kumota? false
              :piilota-toiminnot? true
              :sivuta grid/vakiosivutus
              :mahdollista-rivin-valinta? true
              :rivi-klikattu #(e! (tiedot/->AvaaModal %))}

   [{:otsikko-komp (fn [_ _]
                     [:div.pvm "Päivämäärä"
                      [:div [ikonit/action-sort-descending]]])
     :tyyppi :komponentti
     :komponentti (comp #(pvm/pvm %) :kasittelyaika)

     :luokka "semibold text-nowrap"
     :leveys 0.15}

    {:otsikko "Laji"
     :tyyppi :komponentti
     :komponentti (comp #(tiedot/laji-valinnat %) :laji)
     :luokka "text-nowrap"
     :leveys 0.1}

    {:otsikko "Kohde"
     :tyyppi :komponentti
     :komponentti (comp str :nimi :yllapitokohde)
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
     :komponentti (fn [{:keys [laatupoikkeama]}]
                    (let [rivin-liite (vec (filter #(= (:laatupoikkeama %) (-> laatupoikkeama :id)) liitteet))]
                      [liitteet/liitteet-ikoneina
                       rivin-liite
                       {:ikoni [:div.nappi-toissijainen
                                [ikonit/ikoni-ja-teksti (ikonit/link) "Avaa liite"]]}]))}]
   (->> rivit
     (sort-by :kasittelyaika) reverse)])


(defn- sakot-bonukset-muokkauspaneeli
  "Toteumien luonti / muokkaus"
  [e! {:keys [lajit] :as valinnat} {:keys [kasittelyaika] :as valittu-rivi} kohteet liitteet voi-kirjoittaa? voi-tallentaa? alkuaika tyypit]
  [:div.overlay-oikealla
   [lomake/lomake
    {:ei-borderia? true
     :voi-muokata? voi-kirjoittaa?
     :tarkkaile-ulkopuolisia-muutoksia? true
     :muokkaa! #(e! (tiedot/->MuokkaaRivia %))
     :header [:div.col-md-12
              [:h2.header-yhteiset "Lisää uusi sakko tai bonus"]
              [:hr]]
     :footer [:<>
              [:hr]
              [:div.muokkaus-modal-napit
               [napit/tallenna "Tallenna" #(e! (tiedot/->TallennaRivi valittu-rivi)) {:disabled (not voi-tallentaa?)}]
               [napit/yleinen-toissijainen "Peruuta" #(e! (tiedot/->SuljeMuokkaus))]]]}

    [(lomake/rivi
       {:otsikko "Päivämäärä"
        :pakollinen? true
        :tyyppi :komponentti
        :komponentti (fn []
                       [:span
                        [kentat/tee-kentta {:tyyppi :pvm :vayla-tyyli? true}
                         (r/wrap
                           kasittelyaika
                           #(e! (tiedot/->AsetaToteumanPvm %)))]])
        ::lomake/col-luokka "col-xs-6"})

     (lomake/rivi
       {:otsikko "Laji"
        :valitse-fn #(do
                       (println "ww: " %)
                       (e! (tiedot/->UusiSanktio %)))
        :nimi :laji
        :pakollinen? true
        :vayla-tyyli? true
        :tyyppi :radio-group
        :vaihtoehto-nayta lajit
        :vaihtoehdot (keys lajit)
        :validoi [#(when (nil? %) "Anna kustannuksen tyyppi")]})

     (lomake/rivi
       {:otsikko "Päällystys- tai paikkauskohde"
        :pakollinen? true
        :validoi [[:ei-tyhja "Valitse kohde"]]
        :nimi :yllapitokohde
        :tyyppi :valinta
        :valinnat (into [] kohteet)
        :valinta-nayta :nimi
        ::lomake/col-luokka "leveys-kokonainen"})

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
        :validoi [[:ei-tyhja "Syötä kustannusarvo"]]
        ::lomake/col-luokka "col-xs-6 summa-valinta"})

     (let [tyyppi (-> valittu-rivi :laji)
           poikkeama (-> valittu-rivi :laatupoikkeama :id)
           rivin-liite (vec (filter #(= (:laatupoikkeama %) poikkeama) liitteet))]

       ;; Sallitaan liitteen lisäys vain sakoille
       (when (= tyyppi :yllapidon_sakko)
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
                                                               :domain-id poikkeama
                                                               :domain :laatupoikkeama
                                                               :urakka-id @nav/valittu-urakka-id
                                                               :poistettu-fn #(e! (tiedot/->HaeTiedot))}))}])})))]
    valittu-rivi]])


(defn- suodattimet-aikavali
  "Urakkavuosi valinta, triggeröi haun"
  [e! {:keys [aikavali]}]
  (let [fn-aikavali-muuttunut? (fn [aika]
                                 (let [alku (-> @urakka-tiedot/valittu-hoitokausi first)
                                       loppu (-> @urakka-tiedot/valittu-hoitokausi second)
                                       valinnat-alku (-> aika first)
                                       valinnat-loppu (-> aika second)]
                                   (boolean (or
                                              (not= alku valinnat-alku)
                                              (not= loppu valinnat-loppu)))))]

    [:div {:on-click #(when (fn-aikavali-muuttunut? aikavali) (e! (tiedot/->HaeTiedot)))}
     [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]]))


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
  [e! {:keys [rivit valinnat muokataan valittu-rivi
              haku-kaynnissa? kustannukset tyypit liitteet kohteet] :as app}]

  (let [alkuaika (:alkuaika valittu-rivi)
        voi-tallentaa? (tiedot/voi-tallentaa? valittu-rivi kohteet)
        voi-kirjoittaa? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot @nav/valittu-urakka-id)

        aikavali (suodattimet-aikavali e! valinnat)
        lisaa-uusi-fn #(e! (tiedot/->AvaaModal nil))
        laji-suodatin (suodattimet-lajit e! valinnat)
        grid (sakot-bonukset-grid e! rivit liitteet haku-kaynnissa?)
        muokkauspaneeli (sakot-bonukset-muokkauspaneeli e! valinnat valittu-rivi kohteet liitteet voi-kirjoittaa? voi-tallentaa? alkuaika tyypit)]

    (yhteiset/nakyma-body "Sakot ja bonukset"
      e! lisaa-uusi-fn aikavali
      rivit valinnat muokataan valittu-rivi
      haku-kaynnissa? kustannukset tyypit muokkauspaneeli grid laji-suodatin)))


(defn sakot-ja-bonukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(e! (tiedot/->HaeTiedot)))
    (fn [e! app] [sakot-bonukset-listaus e! app])))


(defn sakot-ja-bonukset []
  [tuck tiedot/tila sakot-ja-bonukset*])
