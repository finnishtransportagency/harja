(ns harja.views.urakka.muutokset.kirjatut-muutokset
  "Muutokset välilehden 'Kirjatut muutokset' -osio"
  (:require [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.muutos-domain :as muutos-domain]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]))


(defn hoitovuoden-kirjatut-muutokset-grid [e! {:keys [kirjatut-muutokset valittu-hoitokausi urakan-hoitokaudet] :as app}]
  [grid/grid
   {:tunniste :id
    :otsikko (str (fmt/hoitokauden-jarjestysluku-ja-alku-ja-loppupvm
                    (pvm/vuosi (first valittu-hoitokausi)) (map (comp pvm/vuosi first) urakan-hoitokaudet) "hoitovuoden")
               " kirjatut muutokset")
    :luokat ["kirjatut-muutokset-grid"]
    :tyhja "Ei kirjattuja muutoksia."
    :voi-lisata? false
    :voi-kumota? false
    :voi-poistaa? (constantly false)
    :voi-muokata? false
    :rivin-luokka (fn [arvo _]
                    (let [rivin-id (:id arvo)
                          viimeksi-klikattu-id (-> app :viimeksi-valittu :id)]
                      (when (= viimeksi-klikattu-id rivin-id) "viimeksi-valittu-tausta")))
    :rivi-jalkeen-fn (fn [rivit]
                       (let [tavoitehinnan-muutokset (map :tavoitehinnan-muutos rivit)
                             tavoitehinnan-muutokset-yhteensa (apply + tavoitehinnan-muutokset)]
                         [{:teksti "Hoitovuoden lopun tavoitehinnan muutokset yhteensä" :luokka "yhteensa" :sarakkeita 2}
                          {:teksti "" :luokka "yhteensa" :leveys 8 :tasaa :oikea}
                          {:teksti (fmt/euro-opt false true tavoitehinnan-muutokset-yhteensa) :luokka "yhteensa" :leveys 8 :tasaa :oikea}
                          {:teksti "" :luokka "yhteensa" :leveys 8 :tasaa :oikea}]))}

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
                     #(e! (t-yhteiset/->MuokkaaMuutosta rivi))])}]
   kirjatut-muutokset])

;; TODO: Toteuta loppuun
(defn aiemmilta-hoitovuosilta-jatkuvat-pysyvat-muutokset-grid [e! {:keys [kirjatut-muutokset] :as app}]
  [grid/grid
   {:tunniste :id
    :otsikko "Aiemmilta hoitovuosilta jatkuvat pysyvät muutokset"
    :luokat ["kirjatut-muutokset-grid"]
    :tyhja "Ei muutoksia aiemmilta hoitovuosilta."
    :voi-lisata? false
    :voi-kumota? false
    :voi-poistaa? (constantly false)
    :voi-muokata? false
    :rivin-luokka (fn [arvo _]
                    (let [rivin-id (:id arvo)
                          viimeksi-klikattu-id (-> app :viimeksi-valittu :id)]
                      (when (= viimeksi-klikattu-id rivin-id) "viimeksi-valittu-tausta")))
    :rivi-jalkeen-fn (fn [rivit]
                       (let [tavoitehinnan-muutokset (map :tavoitehinnan-muutos rivit)
                             tavoitehinnan-muutokset-yhteensa (apply + tavoitehinnan-muutokset)]
                         [{:teksti "Hoitovuoden alun tavoitehinnan muutokset yhteensä" :luokka "yhteensa" :sarakkeita 2}
                          ;; TODO:
                          {:teksti (fmt/euro-opt false true tavoitehinnan-muutokset-yhteensa) :luokka "yhteensa" :leveys 8 :tasaa :oikea}
                          {:teksti (fmt/euro-opt false true tavoitehinnan-muutokset-yhteensa) :luokka "yhteensa" :leveys 8 :tasaa :oikea}
                          {:teksti "" :luokka "yhteensa" :leveys 8 :tasaa :oikea}]))}

   ;; Taulukon kentät
   [{:otsikko "Muutoksen syy"
     :nimi :syy
     :tyyppi :string
     :leveys 40}

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

    {:otsikko "Indeksikorjattu"
     :nimi :tavoitehinnan-muutos-indeksikorjattu
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
                     #(e! (t-yhteiset/->MuokkaaMuutosta rivi))])}]
   ;; TODO: Data
   []])

(defn kirjatut-muutokset [e! {:keys [kirjatut-muutokset] :as app}]
  [yhteiset/kehystetty-avattava-grid e! app
   {:taulukon-avain :kirjatut-muutokset
    :taulukon-nakyvyys-event #(e! (t-yhteiset/->ToggleTaulukonNakyvyys :kirjatut-muutokset))
    :otsikko "Kirjatut muutokset"
    :summa (reduce + 0 (map :tavoitehinnan-muutos kirjatut-muutokset))
    :toiminnot-asetukset {:tasaa :oikea}
    :toiminnot (fn [e! app]
                 [napit/uusi "Lisää uusi" #(e! (t-yhteiset/->MuokkaaMuutosta {}))])
    :taulukko
    (fn [e! app]
      [:<>
       [hoitovuoden-kirjatut-muutokset-grid e! app]
       ;; TODO: Toteuta pysyvien muutosten haku näkymään aiemmilta vuosilta
       [aiemmilta-hoitovuosilta-jatkuvat-pysyvat-muutokset-grid e! app]])}])
