(ns harja.views.urakka.kustannusten-kirjaus.sakot-ja-bonukset
  "Tiemerkintöjen sakot ja bonukset välilehti"
  (:require [harja.views.urakka.kustannusten-kirjaus.sakot-bonukset-tiedot :as tiedot]
            [harja.views.urakka.kustannusten-kirjaus.yhteiset :as yhteiset]
            [tuck.core :refer [tuck]]
            [harja.asiakas.kommunikaatio :as komm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka :as urakka-tiedot]
            [reagent.core :as r]
            [harja.fmt :as fmt]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :as kentat]
            [harja.pvm :as pvm]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.transit :as transit]
            [harja.ui.napit :as napit]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.tierekisteri :as tr-domain]))


(defn- sakot-bonukset-grid [e! rivit liitteet haku-kaynnissa?]
  [grid/grid {:tyhja (if haku-kaynnissa?
                       [ajax-loader-pieni "Haku käynnissä..."]
                       "Valitulle aikavälille ei löytynyt mitään.")
              :tunniste :id
              :sivuta grid/vakiosivutus
              :voi-kumota? false
              :piilota-toiminnot? true
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
     :komponentti (comp str :perustelu :paatos :laatupoikkeama)
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
   rivit])


(defn- sakot-bonukset-muokkauspaneeli
  "Toteumien luonti / muokkaus"
  [e! {:keys [lajit uusi-liite] :as valinnat} valittu-rivi kohteet liitteet voi-kirjoittaa? voi-tallentaa? alkuaika tyypit]
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
               [napit/tallenna "Tallenna" #(e! (tiedot/->TallennaRivi %)) {:disabled (not voi-tallentaa?)}]
               [napit/yleinen-toissijainen "Peruuta" #(e! (tiedot/->SuljeMuokkaus))]]]}

    [(lomake/rivi
       {:otsikko "Päivämäärä"
        :pakollinen? true
        :tyyppi :komponentti
        :komponentti (fn [a]
                       [:span
                        [kentat/tee-kentta {:tyyppi :pvm :vayla-tyyli? true}
                         (r/wrap
                           (-> a :data :kasittelyaika)
                           #(println "test5"))]])
        ::lomake/col-luokka "col-xs-6"})

     (lomake/rivi
       {:otsikko "Laji"
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
        :hae #(or (:lisatieto %) (get-in % [:laatupoikkeama :paatos :perustelu]))
        :tyyppi :text
        :pakollinen? true
        :piilota-checkbox? true
        :piilota-dropdown? true
        :salli-kirjoitus? true
        :validoi [[:ei-tyhja "Kirjoita kustannuksen selite"]]
        ::lomake/col-luokka "leveys-kokonainen"})

     (lomake/rivi
       {:otsikko "Kulun kohdistus"
        :pakollinen? true
        :validoi [[:ei-tyhja "Valitse toimenpide"]]
        :nimi :toimenpideinstanssi
        :tyyppi :valinta
        :hae (fn [rivi]
               (first
                 (filter #(= (:tpi_id %) (-> rivi :toimenpideinstanssi))
                   @urakka-tiedot/urakan-toimenpideinstanssit)))
        :valinnat (into [] @urakka-tiedot/urakan-toimenpideinstanssit)
        :valinta-nayta #(if %
                          (:tpi_nimi %)
                          "Valitse toimenpide")
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

     (lomake/rivi
       {:otsikko "Liitteet"
        :nimi :liitteet
        :tyyppi :komponentti
        :komponentti (fn [rivi]
                       [liitteet/liitteet-ja-lisays
                        @nav/valittu-urakka-id
                        (vec (filter #(= (:laatupoikkeama %) (-> rivi :data :laatupoikkeama :id)) liitteet))
                        {:uusi-liite-atom uusi-liite
                         :uusi-liite-teksti "Lisää liite"
                         :salli-poistaa-lisatty-liite? true
                         ;:poista-lisatty-liite-fn #(e! (tiedot/->PoistaLiite r))
                         :salli-poistaa-tallennettu-liite? false}])})]
    valittu-rivi]])

(defn sakot-bonukset-listaus [e! {:keys [rivit valinnat muokataan valittu-rivi
                                         haku-kaynnissa? kustannukset tyypit valittu-laji liitteet kohteet] :as app}]

  (let [alkuaika (:alkuaika valittu-rivi)
        ;; TODO 
        voi-kirjoittaa? true
        voi-tallentaa? true
        grid (sakot-bonukset-grid e! rivit liitteet haku-kaynnissa?)
        lisaa-uusi-fn #(e! (tiedot/->AvaaModal nil))
        muokkauspaneeli (sakot-bonukset-muokkauspaneeli e! valinnat valittu-rivi kohteet liitteet voi-kirjoittaa? voi-tallentaa? alkuaika tyypit)
        laji-suodatin [kentat/tee-kentta {:vayla-tyyli? true
                                          :nayta-rivina? true
                                          :space-valissa? true
                                          :tyyppi :radio-group
                                          :vaihtoehdot [:kaikki :yllapidon_sakko :yllapidon_bonus]
                                          :vaihtoehto-nayta tiedot/laji-valinnat
                                          :valitse-fn #(e! (tiedot/->ValitseLaji %))}
                       (atom valittu-laji)]]

    (yhteiset/nakyma-body "Sakot ja bonukset"
      e! lisaa-uusi-fn
      rivit valinnat muokataan valittu-rivi
      haku-kaynnissa? kustannukset tyypit muokkauspaneeli grid laji-suodatin)))


(defn sakot-ja-bonukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(e! (tiedot/->HaeTiedot)))
    (fn [e! app] [sakot-bonukset-listaus e! app])))


(defn sakot-ja-bonukset []
  [tuck tiedot/tila sakot-ja-bonukset*])
