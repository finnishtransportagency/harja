(ns harja.views.urakka.muutokset.rahavarausten-muutokset
  "Muutokset välilehden 'Rahavarausten muutokset' -osio"
  (:require [harja.fmt :as fmt]
            [harja.ui.grid :as grid]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]
            [harja.tiedot.urakka.muutokset.rahavarausten-muutokset-tiedot :as t-rahavaraukset]
            [harja.ui.yleiset :as yleiset]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset]))


(defn rahavarausten-muutokset
  "Näyttää rahavarausten muutokset taulukossa sekä yhteenvedon.
  Taulukko on avattava ja suljettava. Sisältö automaattisesti laskettu muista tauluista."
  [e! {:keys [rahavarausten-muutokset] :as app}]
  ;; Prosessoi rivit vasta kun saatavilla on sekvenssimuotoista dataa, jotta gridin load-spinneri näkyy nil-arvolla
  (let [rivit (when (seq rahavarausten-muutokset)
                (or (butlast rahavarausten-muutokset) []))
        yhteenveto (last rahavarausten-muutokset)
        suunnittelutiedot-puuttuvat (every? #(or
                                               (nil? (:summa-indeksikorjattu %))
                                               (zero? (:summa-indeksikorjattu %))) rivit)]
    [yhteiset/kehystetty-avattava-grid e! app
     {:taulukon-avain :rahavarausten-muutokset
      :taulukon-nakyvyys-event #(e! (t-yhteiset/->ToggleTaulukonNakyvyys :rahavarausten-muutokset))
      :otsikko "Rahavarausten muutokset"
      :summa (:tavoitehinnan-muutos yhteenveto)
      :toiminnot (fn [e! app]
                   [::span
                    [yleiset/vihje (str
                                     "Harja laskee rahavarausten tavoitehintamuutokset automaattisesti "
                                     "kustannussuunnitelman ja kulukirjausten perusteella.")]
                    (when suunnittelutiedot-puuttuvat
                      [yleiset/toast-viesti "Suunnittelutiedot puuttuvat tarjouksen tiedoista."])])
      :taulukko
      (fn [e! app]
        [grid/grid
         {:tunniste :id
          :luokat ["rahavarausten-muutokset-grid"]
          :tyhja "Ei rahavarausten muutoksia."
          :voi-lisata? false
          :voi-kumota? false
          :voi-poistaa? (constantly false)
          :voi-muokata? true
          :piilota-toiminnot? true
          :tallenna #(tuck-apurit/e-kanavalla! e! t-rahavaraukset/->TallennaRahavarausmuutostenSyyt %)
          :rivi-jalkeen-fn (fn []
                             [{:teksti "Tavoitehinnan muutokset yhteensä" :luokka "yhteensa" :yhteenveto-vayla true}
                              {:teksti "" :sarakkeita 1 :luokka "yhteensa"}
                              {:teksti "" :tasaa :oikea :luokka "yhteensa"}
                              {:teksti "" :tasaa :oikea :luokka "yhteensa"}
                              {:teksti (fmt/euro-opt false true (:tavoitehinnan-muutos yhteenveto)) :tasaa :oikea :luokka "yhteensa"}
                              {:teksti "" :luokka "yhteensa" :leveys 8 :tasaa :oikea}])}

         ;; Taulukon kentät
         [{:otsikko "Rahavaraus"
           :nimi :nimi
           :tyyppi :string
           :leveys 15
           :muokattava? (constantly false)}

          {:otsikko "Muutoksen syy"
           :nimi :syy
           :tyyppi :text
           :pituus-max 1000
           :koko [50 3]
           :leveys 25}

          {:otsikko "Suunniteltu määrä"
           :nimi :summa-indeksikorjattu
           :tyyppi :numero
           :tasaa :oikea
           :leveys 10
           :muokattava? (constantly false)
           :fmt (fn [arvo]
                  (if arvo
                    (fmt/euro-opt arvo)
                    "Ei indeksikorjattua summaa"))}

          {:otsikko "Toteutunut määrä"
           :nimi :toteumat
           :tyyppi :numero
           :fmt #(if %
                   (fmt/euro-opt %)
                   (fmt/euro-opt 0))
           :tasaa :oikea
           :leveys 10
           :muokattava? (constantly false)}

          {:otsikko "Tavoitehinnan muutos (€)"
           :nimi :tavoitehinnan-muutos
           :tyyppi :numero
           :fmt #(if %
                   (fmt/euro-opt %)
                   (fmt/euro-opt 0))
           :tasaa :oikea
           :leveys 10
           :muokattava? (constantly false)}
          ;; Tyhjä sarake, jotta "Tavoitehinnan muutos (€)" -sarake asettuun samaan kohtaan kuin muissa muutostauluissa
          ;; Muissa tauluissa tässä sarakkeessa on toimintopainikkeet, mutta tässä ei ole toimintoja
          {:otsikko ""
           :nimi :filleri
           :tyyppi :komponentti
           :komponentti (constantly nil)
           :tasaa :oikea
           :leveys 10}]
         rivit])}]))
