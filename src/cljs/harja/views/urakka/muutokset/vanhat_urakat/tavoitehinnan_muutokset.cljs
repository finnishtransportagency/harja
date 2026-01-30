(ns harja.views.urakka.muutokset.vanhat-urakat.tavoitehinnan-muutokset
  "Tavoitehinnan muutokset, vanhat urakat"
  (:require [harja.fmt :as fmt]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset :refer [kehystetty-avattava-grid]]))


(defn tavoitehinnan-muutokset [e! {:keys [tavoitehinnan-muutokset] :as app}]
  [kehystetty-avattava-grid e! app
   {:taulukon-avain :tavoitehinnan-muutokset
    :taulukon-nakyvyys-event #(e! (t-yhteiset/->ToggleTaulukonNakyvyys :tavoitehinnan-muutokset))
    :otsikko "Tavoitehinnan muutokset"
    :summa (reduce + 0 (map :tavoitehinnan-muutos tavoitehinnan-muutokset)) ;; todo
    :toiminnot (fn [e! app]
                 [::span
                  [napit/uusi "Lisää muutos" #(e! (t-yhteiset/->LisaaTavoitehintojenMuutos))]])
    :taulukko
    (fn [e! app]
      [grid/grid
       {:tunniste :id
        :luokat ["tavoitehinnan-muutokset-grid"]
        :tyhja "Ei tavoitehinnan muutoksia."
        :voi-lisata? false
        :voi-kumota? false
        :voi-poistaa? (constantly false)
        :voi-muokata? true}

       ;; Taulukon kentät
       [{:otsikko "Muutos"
         :nimi :muutos
         :tyyppi :string
         :leveys 15}

        {:otsikko "Perustelu"
         :nimi :perustelu
         :tyyppi :string
         :leveys 35}

        {:otsikko "Vaikutus € (+/-)"
         :nimi :tavoitehinnan-muutos
         :tyyppi :numero
         :fmt fmt/euro-opt
         :tasaa :oikea
         :leveys 15}]
       tavoitehinnan-muutokset])}])
