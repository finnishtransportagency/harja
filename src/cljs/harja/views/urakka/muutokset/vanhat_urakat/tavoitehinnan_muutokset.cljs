(ns harja.views.urakka.muutokset.vanhat-urakat.tavoitehinnan-muutokset
  "Tavoitehinnan muutokset, vanhat urakat"
  (:require [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.ui.grid.protokollat :as grid-protokollat]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset :refer [kehystetty-avattava-grid]]))


(defn tavoitehinnan-muutokset [e! {:keys [tavoitehinnan-muutokset haku-kaynnissa? tavoitehinnan-muutokset-yhteensa] :as app}]

  (let [hy (-> @tila/yleiset :urakka :hallintayksikko :id)
        urakka-id (-> @tila/yleiset :urakka :id)
        valittu-alkuvuosi (some->> @u/valittu-hoitokausi first pvm/vuosi)]

    [kehystetty-avattava-grid e! app
     {:taulukon-avain :tavoitehinnan-muutokset
      :taulukon-nakyvyys-event #(e! (t-yhteiset/->ToggleTaulukonNakyvyys :tavoitehinnan-muutokset))
      :otsikko "Tavoitehinnan muutokset"
      :summa (or tavoitehinnan-muutokset-yhteensa 0)
      :toiminnot (fn [_ _]
                   [:div.tavoitehinnan-muutokset-valinnat
                    [:div.col-xs-12.body-text "Tavoitehinnan muutokset ovat saatavilla myös Välikatselmuksessa. "
                     [:a.klikattava.alleviivaa
                      {:href (str "/#urakat/valikatselmus?&hy=" hy "&u=" urakka-id)
                       :on-click #(siirtymat/avaa-valikatselmus hy urakka-id
                                    [(pvm/hoitokauden-alkupvm valittu-alkuvuosi) (pvm/hoitokauden-loppupvm (inc valittu-alkuvuosi))])}
                      "Siirry välikatselmukseen."]]])
      :taulukko
      (fn [e! app]
        [grid/grid
         {:tunniste ::valikatselmus/oikaisun-id
          :tyhja (if haku-kaynnissa?
                   [ajax-loader-pieni "Haku käynnissä..."]
                   "Aikavälille ei löytynyt tuloksia.")
          :luokat ["tavoitehinnan-muutokset-grid"]
          :voi-lisata? true
          :voi-kumota? false
          :tallenna-vain-muokatut true
          :voi-poistaa? (constantly true)
          :mahdollista-rivin-valinta? false
          :tallenna (fn [rivit]
                      (tuck-apurit/e-kanavalla! e! t-yhteiset/->TallennaOikaisut
                        (keep (fn [r]
                                (let [id (::valikatselmus/oikaisun-id r)
                                      uusi? (neg-int? id)
                                      poistettu? (:poistettu r)]
                                  (when (and (not (and uusi? poistettu?))
                                          (::valikatselmus/summa r)
                                          (::valikatselmus/selite r))
                                    (cond-> r
                                      uusi? (assoc
                                              ::valikatselmus/oikaisun-id 0
                                              :harja.domain.urakka/id urakka-id
                                              ::valikatselmus/hoitokauden-alkuvuosi valittu-alkuvuosi)
                                      poistettu? (assoc ::muokkaustiedot/poistettu? true)))))
                          rivit)))}

         [{:otsikko "Muutos"
           :nimi ::valikatselmus/otsikko
           :tyyppi :valinta
           :valinnat (into [] (valikatselmus/luokat @nav/valittu-urakka))
           :validoi [[:ei-tyhja "Valitse arvo"]]
           :leveys 20
           :elementin-id #(str (::valikatselmus/oikaisun-id % (gensym)))
           :aria-label "Muutos"}

          {:otsikko "Perustelu"
           :nimi ::valikatselmus/selite
           :validoi [[:ei-tyhja "Kirjoita oikaisun perustelu"]]
           :tyyppi :text
           :leveys 35}

          {:otsikko "Vaikutus € (+/-)"
           :nimi ::valikatselmus/summa
           :validoi [[:ei-tyhja "Anna oikaisulle vaikutus"]]
           :tyyppi :numero
           :fmt fmt/euro-opt
           :tasaa :oikea
           :leveys 15}]
         tavoitehinnan-muutokset])}]))
