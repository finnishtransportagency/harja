(ns harja.views.urakka.yllapitokohteet.kustannukset-nakyma
  "MPU ja PPU sopimustyyppisten urakoiden kustannukset"
  (:require [tuck.core :refer [tuck]]
            [cljs-time.core :as t]

            [harja.ui.grid :as grid]
            [harja.ui.debug :as debug]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
            [harja.tiedot.urakka.yllapitokohteet.kustannukset-tiedot :as tiedot]
            [harja.views.urakka.yllapitokohteet.kustannukset-apurit :as apurit]))


(defn kustannukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(e! (tiedot/->HaeKustannustiedot)))

    (fn [e! {:keys [haku-kaynnissa? tallennus-kaynnissa? lomake-valinnat muokataan tyomenetelmittain] :as app}]
      (let [urakka @nav/valittu-urakka
            voi-kirjoittaa? (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-paikkauskohteet @nav/valittu-urakka-id @istunto/kayttaja)
            voi-tallentaa? (and
                             voi-kirjoittaa?
                             (tiedot/voi-tallentaa? lomake-valinnat))]

        (if tallennus-kaynnissa?
          [ajax-loader-pieni "Haetaan tietoja..."]

          [:div.kustannukset
           (when muokataan
             (apurit/kustannuksen-lisays-lomake e! app voi-tallentaa?))

           [:h1 "Paikkauskustannusten yhteenveto"]
           [debug/debug app]
           [:div.kalenterivalinta
            ;; Vuosi valinta
            [valinnat/vuosi
             {:disabled false
              :kaanteinen-jarjestys? true
              :otsikko-teksti "Kalenterivuosi"}
             (t/year (:alkupvm urakka))
             (t/year (:loppupvm urakka))
             urakka/valittu-urakan-vuosi
             #(do
                (urakka/valitse-urakan-vuosi! %)
                (e! (tiedot/->HaeKustannustiedot)))]]

           [:h3.header-yhteiset.ei-marginia "Työmenetelmittäin"]

           ;; Kustannus taulukko työmenetelmittäin
           [grid/grid {:tyhja (if haku-kaynnissa?
                                [ajax-loader "Haku käynnissä..."]
                                "Valitulle aikavälille ei löytynyt mitään.")
                       :tunniste :id
                       :sivuta grid/vakiosivutus
                       :voi-kumota? false
                       :piilota-toiminnot? true
                       :piilota-otsikot? true}

            [{:tyyppi :string
              :nimi :tyomenetelma
              :luokka "text-nowrap"
              :leveys 1}

             {:tyyppi :euro
              :desimaalien-maara 2
              :nimi :kokonaiskustannus
              :tasaa :oikea
              :luokka "text-nowrap"
              :leveys 1}]
            tyomenetelmittain]

           [:div.valitetty-rivi
            [:h3.header-yhteiset.ei-marginia "Muut kustannukset"]

            [:span
             [napit/yleinen-ensisijainen
              "Lisää kustannus"
              #(e! (tiedot/->AvaaLomake))
              {:ikoni [ikonit/harja-icon-action-add] :vayla-tyyli? true}]]]

           [:div
            "Sopimuksen mukaiset sanktiot ja bonukset tulee syöttää "
            [:a.klikattava.alleviivaa {:on-click #(siirtymat/siirry-annettuun-valilehteen
                                                    @nav/valittu-hallintayksikko-id (:id @nav/valittu-urakka)
                                                    {:taso1 :urakat
                                                     :taso2 :laadunseuranta
                                                     :taso3 :sanktiot})} "Sanktiot ja bonukset"]
            [:span " -osiossa tai laatupoikkeaman kautta."]]

           [:div
            "Urakkaa koskevat muut kulut voi lisätä tämän osion “Lisää kustannus”-toiminnon kautta."]

           ;; Muut kustannukset & Sanktiot ja bonukset
           (apurit/muut-kustannukset-grid e! app @urakka/valittu-urakan-vuosi)])))))


(defn kustannukset []
  [tuck tiedot/tila kustannukset*])
