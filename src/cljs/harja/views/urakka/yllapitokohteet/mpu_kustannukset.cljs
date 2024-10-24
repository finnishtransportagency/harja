(ns harja.views.urakka.yllapitokohteet.mpu-kustannukset
  "MPU sopimustyyppisten urakoiden kustannukset"
  (:require [tuck.core :refer [tuck]]
            [harja.tiedot.urakka.mpu-kustannukset :as tiedot]
            [harja.tiedot.urakka :as urakka]
            [harja.ui.valinnat :as valinnat]
            [cljs-time.core :as t]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.napit :as napit]
            [harja.views.urakka.yllapitokohteet.mpu-apurit :as apurit]
            [harja.tiedot.istunto :as istunto]))


(defn mpu-kustannukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(e! (tiedot/->HaeKustannustiedot)))

    ;; Näkymä
    (fn [e! {:keys [haku-kaynnissa? lomake-valinnat muokataan tyomenetelmittain] :as app}]
      
      (let [urakka @nav/valittu-urakka
            voi-kirjoittaa? (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-paikkauskohteet @nav/valittu-urakka-id @istunto/kayttaja)
            voi-tallentaa? (and
                             voi-kirjoittaa?
                             (tiedot/voi-tallentaa? lomake-valinnat))]
        [:div.mpu-kustannukset
         ;; Lomake
         (when muokataan
           (apurit/kustannuksen-lisays-lomake e! app voi-tallentaa?))

         ;; Pääotsikko
         [:h2.header-yhteiset "Kustannukset"]

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

         ;; Väliotsikko
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

          ;; Työmenetelmä / kustannus selite
          [{:tyyppi :string
            :nimi :tyomenetelma
            :luokka "text-nowrap"
            :leveys 1}

           ;; Kustannus
           {:tyyppi :euro
            :desimaalien-maara 2
            :nimi :kokonaiskustannus
            :tasaa :oikea
            :luokka "text-nowrap"
            :leveys 1}]
          tyomenetelmittain]

         [:div.valitetty-rivi
          ;; Väliotsikko
          [:h3.header-yhteiset.ei-marginia "Muut kustannukset"]

          ;; Lisää kustannus
          [:span
           [napit/yleinen-ensisijainen
            "Lisää kustannus"
            #(e! (tiedot/->AvaaLomake))
            {:ikoni [ikonit/harja-icon-action-add] :vayla-tyyli? true}]]]

         ;; Muut kustannukset & Sanktiot ja bonukset
         (apurit/muut-kustannukset-grid app @urakka/valittu-urakan-vuosi)]))))


(defn mpu-kustannukset []
  [tuck tiedot/tila mpu-kustannukset*])
 