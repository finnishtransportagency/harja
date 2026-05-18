(ns harja.views.urakka.muutokset.kirjatut-muutokset
  "Muutokset välilehden 'Kirjatut muutokset' -osio"
  (:require [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.muutos-domain :as muutos-domain]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]))


(defn hoitovuoden-kirjatut-muutokset-grid [e! {:keys [kirjatut-muutokset valittu-hoitokausi urakan-hoitokaudet] :as app}]
  [grid/grid
   {:tunniste :id
    ;; Näytetään otsikko tyyliin: "2. hoitovuoden (01.10.2025 − 30.09.2026) kirjatut muutokset"
    :otsikko (str (fmt/hoitokauden-jarjestysluku-ja-alku-ja-loppupvm
                    (some-> valittu-hoitokausi (first) (pvm/vuosi))
                    (map #(some-> % (first) (pvm/vuosi)) urakan-hoitokaudet) "hoitovuoden")
               " kirjallisesti sovitut muutokset")
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
                       (let [tavoitehinnan-muutokset-yhteensa (reduce + (map :tavoitehinnan-muutos rivit))
                             kustannusvaikutukset-yhteensa
                             (reduce + 0
                               (for [rivi rivit
                                     :let [summa (reduce + 0 (map :summa (:kustannusvaikutukset rivi)))]
                                     :when (or (= (:tyyppi rivi) "muutostyo")
                                             (and (= (:tyyppi rivi) "pysyva") (pos? summa)))]
                                 summa))]
                         [{:teksti "Yhteensä" :luokka "yhteensa" :sarakkeita 2}
                          {:teksti "" :luokka "yhteensa" :leveys 8 :tasaa :oikea}
                          {:teksti (fmt/euro-opt false false kustannusvaikutukset-yhteensa) :luokka "yhteensa" :leveys 8 :tasaa :oikea}
                          {:teksti (fmt/euro-opt false true tavoitehinnan-muutokset-yhteensa) :luokka "yhteensa" :leveys 8 :tasaa :oikea}
                          {:teksti "" :luokka "yhteensa" :leveys 8 :tasaa :oikea}]))}

   ;; Taulukon kentät
   [{:otsikko "Voimassa alkaen"
     :nimi :voimassa_alkaen
     :tyyppi :pvm
     :leveys 12}

    {:otsikko "Tyyppi"
     :nimi :tyyppi
     :tyyppi :string
     :leveys 23
     :fmt (fn [arvo]
            (muutos-domain/tyyppi-fmt arvo (:sopimustyyppi @nav/valittu-urakka)))}

    {:otsikko "Muutoksen syy"
     :nimi :syy
     :tyyppi :string
     :leveys 35}

    {:otsikko "Muutostyötilaus (€)"
     :nimi :kustannusvaikutukset
     :tyyppi :komponentti
     :fmt #(fmt/euro-opt false false (reduce + 0 (map :summa %)))
     :tasaa :oikea
     :leveys 15
     :komponentti (fn [{:keys [tyyppi kustannusvaikutukset] :as _rivi}]
                    (let [summa (reduce + 0 (map :summa kustannusvaikutukset))
                          ;; Summa näytetään jos tyyppi on muutostyo, tai ei-negatiivinen pysyvä muutos
                          nayta-summa? (or
                                         (= tyyppi "muutostyo")
                                         (and (= tyyppi "pysyva") (pos? summa)))]
                      [:span
                       (if nayta-summa?
                         (fmt/euro-opt false false summa)
                         "-")]))}

    {:otsikko "Tavoitehinnan muutos (€)"
     :nimi :tavoitehinnan-muutos
     :tyyppi :numero
     :fmt (partial fmt/euro-opt false true)
     :tasaa :oikea
     :leveys 15}

    {:otsikko ""
     :nimi :toiminnot
     :tyyppi :komponentti
     :leveys 10
     :tasaa :oikea
     :komponentti (fn [rivi]
                    (let [poikkeama? (= (:alityyppi rivi) :poikkeama)
                          nimi (if poikkeama? "TODO (poikkeama)" "Muokkaa")]

                      #_[napit/muokkaa "Muokkaa"
                         #(e! (t-yhteiset/->MuokkaaMuutosta rivi))]

                      ;; Voi poistaa kun poikkeama lomake toteutettu
                      [napit/muokkaa nimi
                       #(e! (t-yhteiset/->MuokkaaMuutosta rivi)) {:disabled poikkeama?}]))}]
   kirjatut-muutokset])

(defn aiemmilta-hoitovuosilta-jatkuvat-pysyvat-muutokset-grid [e! {:keys [aiempien-hoitovuosien-pysyvat-muutokset] :as app}]
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
                       (let [tavoitehinnan-muutokset-yhteensa (reduce + (map :tavoitehinnan-muutos rivit))
                             tavoitehinnan-muutokset-indeksikorjattu-yht (reduce + (map :tavoitehinnan-muutos-indeksikorjattu rivit))]
                         [{:teksti "Yhteensä" :luokka "yhteensa" :sarakkeita 2}
                          {:teksti (fmt/euro-opt false true tavoitehinnan-muutokset-yhteensa) :luokka "yhteensa" :leveys 8 :tasaa :oikea}
                          {:teksti (fmt/euro-opt false true tavoitehinnan-muutokset-indeksikorjattu-yht) :luokka "yhteensa" :leveys 8 :tasaa :oikea}
                          {:teksti "" :luokka "yhteensa" :leveys 8 :tasaa :oikea}]))}

   ;; Taulukon kentät
   [{:otsikko "Voimassa alkaen"
     :nimi :voimassa_alkaen
     :tyyppi :pvm
     :leveys 12}

    {:otsikko "Muutoksen syy"
     :nimi :syy
     :tyyppi :string
     :leveys 60}

    {:otsikko "Tavoitehinnan muutos (€)"
     :nimi :tavoitehinnan-muutos
     :tyyppi :numero
     :fmt (partial fmt/euro-opt false true)
     :tasaa :oikea
     :leveys 15}

    {:otsikko "Indeksikorjattu"
     :nimi :tavoitehinnan-muutos-indeksikorjattu
     :tyyppi :numero
     :fmt (partial fmt/euro-opt false true)
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
   aiempien-hoitovuosien-pysyvat-muutokset])

(defn kirjatut-muutokset [e! {:keys [kirjatut-muutokset aiempien-hoitovuosien-pysyvat-muutokset] :as app}]
  [yhteiset/kehystetty-avattava-grid e! app
   {:taulukon-avain :kirjatut-muutokset
    :taulukon-nakyvyys-event #(e! (t-yhteiset/->ToggleTaulukonNakyvyys :kirjatut-muutokset))
    :otsikko "Kirjallisesti sovitut muutokset"
    :summa (reduce + 0 (map :tavoitehinnan-muutos kirjatut-muutokset))
    :toiminnot-asetukset {:tasaa :oikea}
    :toiminnot (fn [e! app]
                 [napit/uusi "Lisää uusi" #(e! (t-yhteiset/->MuokkaaMuutosta {}))])
    :taulukko
    (fn [e! app]
      [:<>
       [hoitovuoden-kirjatut-muutokset-grid e! app]
       [aiemmilta-hoitovuosilta-jatkuvat-pysyvat-muutokset-grid e! app]])}])
