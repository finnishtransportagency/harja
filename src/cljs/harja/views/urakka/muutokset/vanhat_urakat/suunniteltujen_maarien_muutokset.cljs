(ns harja.views.urakka.muutokset.vanhat-urakat.suunniteltujen-maarien-muutokset
  "Suunniteltujen määrien muutokset, vanhat urakat"
  (:require [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset :refer [kehystetty-avattava-grid]]))


(defn suunniteltujen-maarien-muutokset
  "Ei toteutusta vielä, tässä pohjakoodia"
  [e! {:keys [suunniteltujen-maarien-muutokset haku-kaynnissa?] :as app}]
  [kehystetty-avattava-grid e! app
   {:taulukon-avain :suunniteltujen-maarien-muutokset
    :taulukon-nakyvyys-event #(e! (t-yhteiset/->ToggleTaulukonNakyvyys :suunniteltujen-maarien-muutokset))
    :otsikko "Suunniteltujen määrien muutokset"
    :summa 0
    :toiminnot (fn [e! app]
                 #_[:span
                    [napit/uusi "Lisää muutos" #(e! (t-yhteiset/->LisaaSuunniteltujenMaarienMuutos))]])
    :taulukko
    (fn [e! app]
      [grid/grid
       {:tunniste :id
        :luokat ["suunniteltujen-maarien-muutokset-grid"]
        :tyhja (if haku-kaynnissa?
                 [ajax-loader-pieni "Haku käynnissä..."]
                 "Aikavälille ei löytynyt tuloksia.")
        :voi-lisata? false
        :voi-kumota? false
        :voi-poistaa? (constantly false)
        :voi-muokata? true}

       ;; Taulukon kentät
       [{:otsikko "Muutoksen syy"
         :nimi :syy
         :tyyppi :string
         :leveys 15}

        {:otsikko "Muutokset"
         :nimi :muutokset
         :tyyppi :string
         :leveys 35}

        {:otsikko "Lisätieto"
         :nimi :lisatieto
         :tyyppi :string
         :leveys 15}

        {:otsikko "" :nimi
         :toiminnot :tyyppi
         :komponentti
         :leveys 10
         :tasaa :oikea
         :komponentti (fn [rivi]
                        [napit/muokkaa "Muokkaa"
                         #(e! (t-yhteiset/->MuokkaaMuutosta rivi))])}]
       []])}])
