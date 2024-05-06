(ns harja.views.urakka.yllapitokohteet.mpu-apurit
  "MPU kustannusten apufunktiot"
  (:require [harja.tiedot.urakka.mpu-kustannukset :as tiedot]
            [harja.fmt :as fmt]
            [harja.ui.lomake :as lomake]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.napit :as napit]))


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


(defn sanktiot-ja-bonukset-grid [{:keys [haku-kaynnissa? sanktiot-ja-bonukset kustannukset-yhteensa]}]

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
                                 [{:teksti "Kustannukset yhteensä" :luokka "lihavoitu"}
                                  {:teksti (str (fmt/euro-opt false kustannukset-yhteensa) " €") :tasaa :oikea :luokka "lihavoitu"}])}

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
   sanktiot-ja-bonukset])
