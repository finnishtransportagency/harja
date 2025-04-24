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
  "Taulukko"
  [_e! rivit haku-kaynnissa?]
  [:<>
   [:h2 (str 
          (-> @nav/valittu-urakka :nimi) 
          ", toteutuneet kustannukset " 
          (-> @u/valittu-aikavali first (pvm/vuosi)))]

   [grid/grid {:tyhja (if haku-kaynnissa?
                        [ajax-loader-pieni "Haku käynnissä..."]
                        "Aikavälille ei löytynyt tuloksia.")
               :tunniste :id
               :voi-kumota? false
               :piilota-toiminnot? true
               :sivuta grid/vakiosivutus
               :mahdollista-rivin-valinta? false
               :rivi-jalkeen-fn (fn [rivit]
                                  (let [yhteensa-hinta (reduce + (map :hinta rivit))]
                                    [[{:teksti "Yhteensä" :luokka "yhteensa"}
                                      {:teksti (str (fmt/euro-opt false yhteensa-hinta) " €") :tasaa :oikea :luokka "yhteensa"}]]))}

    [{:otsikko "Kustannuslaji"
      :tyyppi :komponentti
      :komponentti (fn [rivi]
                     (or
                       ((:tyyppi rivi) yhteiset/yhteenveto-tyypit)
                       ((:tyyppi rivi) yhteiset/tyyppi-valinnat)
                       ((:tyyppi rivi) yhteiset/laji-valinnat)))
      :luokka "text-nowrap"
      :leveys 0.2}

     {:otsikko "Kustannus"
      :nimi :hinta
      :tyyppi :euro
      :tasaa :oikea
      :luokka "text-nowrap"
      :leveys 0.2}]
    rivit]])


(defn yhteenveto-listaus [e! {:keys [rivit valinnat muokataan haku-kaynnissa?] :as _app}]
  (let [lisaa-uusi-fn nil
        grid (yhteenveto-grid e! rivit haku-kaynnissa?)
        aikavali (yhteiset/paivittava-urakkavuosi-suodatin valinnat #(e! (tiedot/->HaeTiedot)) haku-kaynnissa?)]

    (yhteiset/nakyma-body "Kustannusten yhteenveto" lisaa-uusi-fn aikavali valinnat muokataan nil grid nil true)))


(defn kustannusten-yhteenveto* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan
      #(e! (tiedot/->HaeTiedot)))
    (fn [e! app] [yhteenveto-listaus e! app])))


(defn kustannusten-yhteenveto []
  [tuck urakka-tila/tiemerkinta-yhteenveto kustannusten-yhteenveto*])
