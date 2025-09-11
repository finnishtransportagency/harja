(ns harja.views.urakka.muutokset.kirjatut-muutokset
  "Muutokset välilehden 'Kirjatut muutokset' -osio"
  (:require [harja.fmt :as fmt]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.muutos-domain :as muutos-domain]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset]
            [harja.tiedot.urakka.muutos-tiedot :as muutos-tiedot]))


(defn kirjatut-muutokset [e! {:keys [kirjatut-muutokset] :as app}]
  [yhteiset/kehystetty-avattava-grid e! app
   {:taulukon-avain :kirjatut-muutokset
    :taulukon-nakyvyys-event #(e! (muutos-tiedot/->ToggleTaulukonNakyvyys :kirjatut-muutokset))
    :otsikko "Kirjatut muutokset"
    :summa (reduce + 0 (map :tavoitehinnan-muutos kirjatut-muutokset))
    :toiminnot (fn [e! app]
                 [napit/uusi "Lisää uusi" #(e! (muutos-tiedot/->MuokkaaMuutosta {}))])
    :taulukko
    (fn [e! app]
      [grid/grid
       {:tunniste :id
        :luokat ["kirjatut-muutokset-grid"]
        :tyhja "Ei kirjattuja muutoksia."
        :voi-lisata? false
        :voi-kumota? false
        :voi-poistaa? (constantly false)
        :voi-muokata? false
        :rivin-luokka (fn [arvo _]
                        (let [rivin-id (:id arvo)
                              viimeksi-klikattu-id (-> app :viimeksi-valittu :id)]
                          (when (= viimeksi-klikattu-id rivin-id) "viimeksi-valittu-tausta")))}

       ;; Taulukon kentät
       [{:otsikko "Tyyppi"
         :nimi :tyyppi
         :tyyppi :string
         :leveys 15
         :fmt (fn [arvo]
                (muutos-domain/tyyppi-fmt arvo (:sopimustyyppi @nav/valittu-urakka)))}

        {:otsikko "Muutoksen syy"
         :nimi :syy
         :tyyppi :string
         :leveys 35}

        {:otsikko "Voimassa alkaen"
         :nimi :voimassa_alkaen
         :tyyppi :pvm
         :leveys 15}

        {:otsikko "Tavoitehinnan muutos (€)"
         :nimi :tavoitehinnan-muutos
         :tyyppi :numero
         :fmt fmt/euro-opt
         :tasaa :oikea
         :leveys 15}

        {:otsikko ""
         :nimi :toiminnot
         :tyyppi :komponentti
         :leveys 10
         :tasaa :oikea
         :komponentti (fn [rivi]
                        [napit/muokkaa "Muokkaa"
                         #(e! (muutos-tiedot/->MuokkaaMuutosta rivi))])}]
       kirjatut-muutokset])}])
