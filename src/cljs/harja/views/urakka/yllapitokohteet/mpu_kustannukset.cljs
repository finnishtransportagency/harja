(ns harja.views.urakka.yllapitokohteet.mpu-kustannukset
  "MPU sopimustyyppisten urakoiden kustannukset"
  (:require [tuck.core :refer [tuck]]
            [harja.tiedot.urakka.mpu-kustannukset :as tiedot]
            [reagent.core :as r]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka :as urakka]
            [harja.ui.liitteet :as liitteet]
            [harja.fmt :as fmt]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.lomake :as lomake]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.kentat :as kentat]
            [cljs-time.core :as t]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.napit :as napit]
            [harja.views.kartta :as kartta]
            [harja.tiedot.istunto :as istunto])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))


(defn kustannuksen-lisays-lomake [e! {:keys [voi-kirjoittaa? lomake-valinnat kustannusten-tyypit]} voi-tallentaa?]
  [:div.overlay-oikealla
   [lomake/lomake
    {:ei-borderia? true
     :voi-muokata? voi-kirjoittaa?
     :tarkkaile-ulkopuolisia-muutoksia? true
     :muokkaa! #(e! (tiedot/->MuokkaaLomaketta %))
     ;; Header
     :header [:div.col-md-12
              [:h2.header-yhteiset {:data-cy "mpu-kustannus-lisays"} "Lisää kustannus"]
              [:hr]]
     ;; Footer
     :footer [:<>
              [:div.muokkaus-modal-napit
               ;; Tallenna
               [napit/tallenna "Tallenna" #(e! (tiedot/->TallennaKustannus lomake-valinnat))  {:disabled (not voi-tallentaa?)
                                                                                               :data-attributes {:data-cy "tallena-mpu-kustannus"}}]
               ;; Peruuta 
               [napit/yleinen-toissijainen "Peruuta" #(e! (tiedot/->SuljeLomake)) {:data-attributes {:data-cy "mpu-kustannus-peruuta"}}]]]}

    [;; Tyyppi
     (lomake/rivi
       {:otsikko "Kustannuksen tyyppi"
        :pakollinen? true
        :rivi-luokka "lomakeryhman-rivi-tausta"
        :validoi [[:ei-tyhja "Valitse tyyppi"]]
        :nimi :kustannus-tyyppi
        :tyyppi :valinta
        :valinnat kustannusten-tyypit
        ::lomake/col-luokka "leveys-kokonainen"})

     ;; Selite 
     (when (= (:kustannus-tyyppi lomake-valinnat) "Muut kustannukset")
       (lomake/rivi
         {:otsikko "Selite"
          :pakollinen? true
          :rivi-luokka "lomakeryhman-rivi-tausta"
          :validoi [[:ei-tyhja "Kirjoita kustannuksen selite"]]
          :nimi :kustannus-selite
          :tyyppi :string
          ::lomake/col-luokka "leveys-kokonainen"}))

     ;; Määrä 
     (lomake/rivi
       {:otsikko "Kustannus"
        :pakollinen? true
        :rivi-luokka "lomakeryhman-rivi-tausta"
        :nimi :kustannus
        :tyyppi :euro
        :teksti-oikealla "EUR"
        :vayla-tyyli? true
        :validoi [[:ei-tyhja "Syötä kustannusarvo"]]
        ::lomake/col-luokka "maara-valinnat"})]
    lomake-valinnat]])


(defn kustannukset-listaus [e! {:keys [haku-kaynnissa? lomake-valinnat
                                       muokataan rivit kustannukset-yhteensa] :as app} urakka]

  (let [voi-kirjoittaa? (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-paikkauskohteet @nav/valittu-urakka-id @istunto/kayttaja)
        voi-tallentaa? (and
                         voi-kirjoittaa?
                         (tiedot/voi-tallentaa? lomake-valinnat))]
    [:div.mpu-kustannukset
     ;; Lomake
     (when muokataan
       (kustannuksen-lisays-lomake e! app voi-tallentaa?))

     [:div.header-valinnat
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
          (e! (tiedot/->HaeTiedot)))]

      ;; Lisää kustannus
      [:span
       [napit/yleinen-ensisijainen
        "Lisää kustannus"
        #(do
           (println "Uusi Kustannus ->")
           (e! (tiedot/->AvaaLomake)))
        {:ikoni [ikonit/harja-icon-action-add] :vayla-tyyli? true}]]]

     ;; Taulukko
     [grid/grid {:tyhja (if haku-kaynnissa?
                          [ajax-loader "Haku käynnissä..."]
                          "Valitulle aikavälille ei löytynyt mitään.")
                 :tunniste :id
                 :sivuta grid/vakiosivutus
                 :voi-kumota? false
                 :piilota-toiminnot? true
                 :piilota-otsikot? true
                 ;; Yhteenveto 
                 :rivi-jalkeen-fn (fn [_rivit]
                                    ^{:luokka "kustannukset-yhteenveto"}
                                    [{:teksti "Yhteensä" :luokka "lihavoitu"}
                                     {:teksti (str (fmt/euro-opt false kustannukset-yhteensa) " €") :tasaa :oikea :luokka "lihavoitu"}])}

      ;; Työmenetelmä
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
      rivit]]))


(defn mpu-kustannukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(do
                    (e! (tiedot/->HaeTiedot))))

    (fn [e! app]
      [:div
       [kustannukset-listaus e! app @nav/valittu-urakka]])))


(defn mpu-kustannukset []
  [tuck tiedot/tila mpu-kustannukset*])
 