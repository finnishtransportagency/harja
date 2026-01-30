(ns harja.views.urakka.muutokset.vanhat-urakat.tavoitehinnan-muutokset
  "Tavoitehinnan muutokset, vanhat urakat"
  (:require [harja.fmt :as fmt]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.grid :as grid]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset :refer [kehystetty-avattava-grid]]))


(defn tavoitehinnan-muutokset [e! {:keys [tavoitehinnan-muutokset] :as app}]

  [kehystetty-avattava-grid e! app
   {:taulukon-avain :tavoitehinnan-muutokset
    :taulukon-nakyvyys-event #(e! (t-yhteiset/->ToggleTaulukonNakyvyys :tavoitehinnan-muutokset))
    :otsikko "Tavoitehinnan muutokset"
    :summa (reduce + 0 (map :tavoitehinnan-muutos tavoitehinnan-muutokset))
    :toiminnot (fn [e! app]
                 [:div {:style {:display "flex" :column-gap "10px"}}
                  [:div.col-xs-12.body-text "Tavoitehinnan muutokset ovat saatavilla myös Välikatselmuksessa. "
                   [:a.klikattava "Siirry välikatselmukseen."]]])
    :taulukko
    (fn [e! app]
      [grid/grid
       {:tunniste ::valikatselmus/oikaisun-id
        :tyhja "Ei tavoitehinnan muutoksia."
        :luokat ["tavoitehinnan-muutokset-grid"]
        :voi-lisata? true
        :voi-kumota? false
        :tallenna-vain-muokatut true
        :voi-poistaa? (constantly true)
        :mahdollista-rivin-valinta? false
        :tallenna (fn [a]
                    (println "rivi:: " a))}

       [{:otsikko "Muutos"
         :nimi ::valikatselmus/otsikko
         :tyyppi :valinta
         :valinnat (into [] (valikatselmus/luokat @nav/valittu-urakka))
         :validoi [[:ei-tyhja "Valitse arvo"]]
         :leveys 20
         :elementin-id (str "luokka-" (gensym))
         :aria-label "Muutos"}

        {:otsikko "Perustelu"
         :nimi ::valikatselmus/selite
         :tyyppi :text
         :leveys 35}

        {:otsikko "Vaikutus € (+/-)"
         :nimi ::valikatselmus/summa
         :tyyppi :numero
         :fmt fmt/euro-opt
         :tasaa :oikea
         :leveys 15}]
       tavoitehinnan-muutokset])}])
