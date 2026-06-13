(ns harja.views.urakka.tiemerkinta-kustannukset.muut-kustannukset
  "Tiemerkintöjen muut kustannukset välilehti"
  (:require [reagent.core :as r]
            [tuck.core :refer [tuck]]

            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.urakka :as urakka-tila]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.views.urakka.tiemerkinta-kustannukset.yhteiset :as yhteiset]
            [harja.tiedot.urakka.tiemerkinta-kustannukset.muut-kustannukset-tiedot :as tiedot]))


(defn- muut-kustannukset-grid
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
              :rivi-klikattu #(e! (tiedot/->AvaaKustannusModal %))
              :rivi-jalkeen-fn (fn [rivit]
                                 (let [rivien-maara (count rivit)
                                       yhteensa-hinta (reduce + (map :hinta rivit))]
                                   [[{:teksti "Yhteensä" :luokka "yhteensa"}
                                     {:teksti (str rivien-maara " kpl") :luokka "yhteensa"}
                                     {:luokka "yhteensa"}
                                     {:luokka "yhteensa"}
                                     {:teksti (str (fmt/euro-opt false yhteensa-hinta) " €") :tasaa :oikea :luokka "yhteensa"}]]))}

   [{:otsikko-komp (fn [_ _]
                     [:div.pvm "Päivämäärä"
                      [:div [ikonit/action-sort-descending]]])
     :tyyppi :komponentti
     :komponentti (fn [arvo _] (str (pvm/pvm (:pvm arvo))))
     :luokka "caption text-nowrap"
     :leveys 0.2}

    {:otsikko "Tyyppi"
     :tyyppi :komponentti
     :komponentti (comp #(yhteiset/tyyppi-valinnat %) :tyyppi)
     :luokka "text-nowrap"
     :leveys 0.2}

    {:otsikko "Selite"
     :tyyppi :string
     :nimi :selite
     :leveys 0.2}

    {:otsikko "Pk-luokka"
     :tyyppi :komponentti
     :komponentti (comp str :nimi :yllapitoluokka)
     :luokka "text-nowrap"
     :leveys 0.2}

    {:otsikko "Kustannus"
     :tyyppi :euro
     :tasaa :oikea
     :nimi :hinta
     :luokka "text-nowrap"
     :leveys 0.2}]
   rivit])


(defn- muut-kustannukset-muokkauspaneeli
  "Toteumien luonti / muokkaus"
  [e! {:keys [pvm virheita?] :as valittu-rivi} voi-kirjoittaa? voi-tallentaa? tyypit]
  [:div.overlay-oikealla
   [lomake/lomake
    {:ei-borderia? true
     :voi-muokata? voi-kirjoittaa?
     :tarkkaile-ulkopuolisia-muutoksia? true
     :muokkaa! #(e! (tiedot/->MuokkaaRivia %))
     :header (let [muokataan? (-> valittu-rivi :id some?)
                   otsikko (if muokataan? "Muokkaa kustannusta" "Lisää uusi kustannus")]
               [:div.col-md-12
                [:h2.header-yhteiset otsikko]
                [:hr]])
     :footer (let [peruuta-fn #(e! (tiedot/->SuljeMuokkaus))
                   tallenna-fn #(e! (tiedot/->TallennaRivi valittu-rivi (lomake/virheita? valittu-rivi)))]
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
        :nimi :pvm
        :pakollinen? true
        :tyyppi :komponentti
        :validoi [#(when (and virheita? (nil? %)) "Anna päivämäärä")]
        :komponentti (fn []
                       [:span
                        [kentat/tee-kentta {:tyyppi :pvm :vayla-tyyli? true}
                         (r/wrap
                           pvm
                           #(e! (tiedot/->AsetaToteumanPvm %)))]])
        ::lomake/col-luokka "col-xs-6"})

     (lomake/rivi
       {:otsikko "Tyyppi"
        :nimi :tyyppi
        :tyyppi :valinta
        :pakollinen? true
        :vayla-tyyli? true
        :valinnat (map :tyyppi tyypit)
        :valinta-nayta #(get yhteiset/tyyppi-valinnat (keyword %))
        :validoi [#(when (and virheita? (nil? %)) "Valitse tyyppi")]
        ::lomake/col-luokka "col-xs-6"})

     (lomake/rivi
       {:nimi :selite
        :tyyppi :text
        :otsikko "Selite"
        :pakollinen? true
        :salli-kirjoitus? true
        :piilota-checkbox? true
        :piilota-dropdown? true
        :validoi [#(when (and virheita? (nil? (seq %))) "Kirjoita kustannuksen selite")]
        ::lomake/col-luokka "col-xs-6"})

     (lomake/rivi
       {:otsikko "PK-luokka"
        :pakollinen? true
        :vayla-tyyli? true
        :tyyppi :radio-group
        :nimi :yllapitoluokka
        :vaihtoehto-nayta :nimi
        :vaihtoehdot yllapitokohteet-domain/paallysteen-korjausluokat
        :validoi [#(when (and virheita? (nil? %)) "Syötä jokin luokka, tai 'Ei PK-luokkaa'")]})

     (lomake/rivi
       {:otsikko "Summa"
        :nimi :hinta
        :tyyppi :euro
        :pakollinen? true
        :vayla-tyyli? true
        :teksti-oikealla "EUR"
        :validoi [#(when (and virheita? (nil? %)) "Syötä kustannusarvo")
                  [:rajattu-numero -999999999 999999999 "Anna arvo väliltä 0 - 999 999 999"]]
        ::lomake/col-luokka "col-xs-6 summa-valinta"})]
    valittu-rivi]])


(defn muut-kustannukset-listaus [e! {:keys [rivit valinnat muokataan
                                            valittu-rivi haku-kaynnissa? tyypit] :as _app}]
  (let [voi-tallentaa? true ;; Valitoidaan tallennettaessa (saavutettavuus)
        voi-kirjoittaa? (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteutus-muutkustannukset @nav/valittu-urakka-id)
        lisaa-uusi-fn #(e! (tiedot/->AvaaKustannusModal {:yllapitoluokka (first (filter
                                                                                  (fn [d] (= "-" (:lyhyt-nimi d))) yllapitokohteet-domain/paallysteen-korjausluokat))}))

        grid (muut-kustannukset-grid e! rivit haku-kaynnissa?)
        aikavali (yhteiset/paivittava-urakkavuosi-suodatin valinnat #(e! (tiedot/->HaeTiedot)) haku-kaynnissa? false)
        muokkauspaneeli (muut-kustannukset-muokkauspaneeli e! valittu-rivi voi-kirjoittaa? voi-tallentaa? tyypit)]

    (yhteiset/nakyma-body "Muut kustannukset" lisaa-uusi-fn aikavali valinnat muokataan muokkauspaneeli grid haku-kaynnissa? nil false)))


(defn muut-kustannukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan
      #(do
         (when (urakka-tiedot/koko-urakkakausi-valittuna?) (urakka-tiedot/valitse-kuluva-hk!))
         (e! (tiedot/->HaeTiedot))))
    (fn [e! app] [muut-kustannukset-listaus e! app])))


(defn muut-kustannukset []
  [tuck urakka-tila/tiemerkinta-muut-kustannukset muut-kustannukset*])
