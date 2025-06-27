(ns harja.views.urakka.tiemerkinta-kustannukset.yhteenveto
  "Tiemerkintöjen kustannusten yhteenveto välilehti"
  (:require  [tuck.core :refer [tuck]]

             [harja.fmt :as fmt]
             [harja.pvm :as pvm]
             [harja.ui.grid :as grid]
             [harja.tiedot.urakka :as u]
             [harja.ui.komponentti :as komp]
             [harja.tiedot.navigaatio :as nav]
             [harja.tiedot.urakka.urakka :as urakka-tila]
             [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
             [harja.views.urakka.tiemerkinta-kustannukset.yhteiset :as yhteiset]
             [harja.tiedot.urakka.tiemerkinta-kustannukset.yhteenveto-tiedot :as tiedot]))


(defn- yhteenveto-grid
  "Yhteenveto taulukko, kaikki lasketaan bäkkärissä"
  [_e! rivit haku-kaynnissa?]
  (let [urakka (-> @nav/valittu-urakka :nimi)
        valittu-vuosi (when (first @u/valittu-aikavali)
                        (-> @u/valittu-aikavali first (pvm/vuosi)))

        vuosi-termi (if (u/koko-urakkakausi-valittuna?)
                      "Kaikki toteutuneet kustannukset"
                      (str "Toteutuneet kustannukset " valittu-vuosi))

        hinta-sarake (fn [hinta prosentti tekstina?]
                       (let [hinta (or hinta 0.0)
                             hinta (fmt/formatoi-numero-tuhansittain (or hinta 0))
                             prosentti (or prosentti 0.0)
                             prosentti (fmt/prosentti prosentti 2)]

                         (if tekstina?
                           (str hinta " € (" prosentti ")")
                           [:span
                            [:span (str hinta " €")]
                            [:span.caption (str " (" prosentti ")")]])))
        ;; Eritä viimeinen yhteenveto rivi datasta 
        ;; Se näytetään erikseen rivi-jalkeen- äf än 
        yhteenveto (first (filter #(= (:tyyppi %) :yhteensa) rivit))
        rivit (remove #(= (:tyyppi %) :yhteensa) rivit)]

    [:<>
     [:h2 vuosi-termi]
     [:h3 urakka]

     [grid/grid {:tyhja (if haku-kaynnissa?
                          [ajax-loader-pieni "Haku käynnissä..."]
                          "Aikavälille ei löytynyt tuloksia.")
                 :tunniste :id
                 :voi-kumota? false
                 :piilota-toiminnot? true
                 :sivuta grid/vakiosivutus
                 :mahdollista-rivin-valinta? false
                 :rivi-jalkeen-fn (fn [_rivit]
                                    (let [{:keys [kustannus
                                                  pk1-hinta pk1-prosentti
                                                  pk2-hinta pk2-prosentti
                                                  pk3-hinta pk3-prosentti
                                                  ei-luokkaa-hinta ei-luokkaa-prosentti]} yhteenveto]
                                      [[{:teksti "Yhteensä" :luokka "yhteensa"}
                                        {:teksti (str (fmt/euro-opt false kustannus) " €") :tasaa :oikea :luokka "yhteensa"}
                                        ;; PK 1
                                        {:teksti (hinta-sarake pk1-hinta pk1-prosentti true) :tasaa :oikea :luokka "yhteensa"}
                                        ;; PK 2 
                                        {:teksti (hinta-sarake pk2-hinta pk2-prosentti true) :tasaa :oikea :luokka "yhteensa"}
                                        ;; PK 3
                                        {:teksti (hinta-sarake pk3-hinta pk3-prosentti true) :tasaa :oikea :luokka "yhteensa"}
                                        ;; Ei pk luokkaa
                                        {:teksti (hinta-sarake ei-luokkaa-hinta ei-luokkaa-prosentti true) :tasaa :oikea :luokka "yhteensa"}]]))}

      [{:otsikko "Kustannuslaji"
        :tyyppi :komponentti
        :komponentti (fn [rivi]
                       ((:tyyppi rivi) yhteiset/yhteenveto-tyypit))
        :luokka "text-nowrap"
        :leveys 0.5}

       {:otsikko "Kustannus"
        :nimi :kustannus
        :tyyppi :euro
        :tasaa :oikea
        :luokka "text-nowrap"
        :leveys 0.25}

       {:otsikko "Pk1-osuus"
        :nimi :hinta
        :tyyppi :komponentti
        :komponentti #(hinta-sarake (:pk1-hinta %) (:pk1-prosentti %) false)
        :tasaa :oikea
        :luokka "text-nowrap"
        :leveys 0.25}

       {:otsikko "Pk2-osuus"
        :nimi :hinta
        :tyyppi :komponentti
        :komponentti #(hinta-sarake (:pk2-hinta %) (:pk2-prosentti %) false)
        :tasaa :oikea
        :luokka "text-nowrap"
        :leveys 0.25}

       {:otsikko "Pk3-osuus"
        :nimi :hinta
        :tyyppi :komponentti
        :komponentti #(hinta-sarake (:pk3-hinta %) (:pk3-prosentti %) false)
        :tasaa :oikea
        :luokka "text-nowrap"
        :leveys 0.25}

       {:otsikko "Ei pk-luokkaa"
        :nimi :hinta
        :tyyppi :komponentti
        :komponentti #(hinta-sarake (:ei-luokkaa-hinta %) (:ei-luokkaa-prosentti %) false)
        :tasaa :oikea
        :luokka "text-nowrap"
        :leveys 0.25}]
      rivit]]))


(defn yhteenveto-listaus [e! {:keys [rivit valinnat muokataan haku-kaynnissa?] :as _app}]
  (let [lisaa-uusi-fn nil
        grid (yhteenveto-grid e! rivit haku-kaynnissa?)
        aikavali (yhteiset/paivittava-urakkavuosi-suodatin valinnat #(e! (tiedot/->HaeTiedot)) haku-kaynnissa? true)]

    (yhteiset/nakyma-body "Kustannusten yhteenveto" lisaa-uusi-fn aikavali valinnat muokataan nil grid haku-kaynnissa? nil true)))


(defn kustannusten-yhteenveto* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan
      #(e! (tiedot/->HaeTiedot)))
    (fn [e! app] [yhteenveto-listaus e! app])))


(defn kustannusten-yhteenveto []
  [tuck urakka-tila/tiemerkinta-yhteenveto kustannusten-yhteenveto*])
