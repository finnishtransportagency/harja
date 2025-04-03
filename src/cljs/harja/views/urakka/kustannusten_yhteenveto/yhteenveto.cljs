(ns harja.views.urakka.kustannusten-yhteenveto.yhteenveto
  "Tiemerkintöjen kustannusten yhteenveto välilehti"
  (:require
   [harja.pvm :as pvm]
   [harja.fmt :as fmt]
   [reagent.core :as r]
   [harja.ui.grid :as grid]
   [tuck.core :refer [tuck]]
   [harja.ui.napit :as napit]
   [harja.ui.lomake :as lomake]
   [harja.ui.kentat :as kentat]
   [harja.ui.ikonit :as ikonit]
   [harja.ui.komponentti :as komp]
   [harja.tiedot.navigaatio :as nav]
   [harja.domain.oikeudet :as oikeudet]
   [harja.tiedot.urakka :as urakka-tiedot]
   [harja.views.urakka.valinnat :as urakka-valinnat]
   [harja.domain.yllapitokohde :as yllapitokohteet-domain]
   [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
   [harja.views.urakka.kustannusten-kirjaus.yhteiset :as yhteiset]
   [harja.views.urakka.kustannusten-yhteenveto.yhteenveto-tiedot :as tiedot]))


(defn- yhteenveto-grid
  "Taulukko"
  [e! rivit haku-kaynnissa?]
  [grid/grid {:tyhja (if haku-kaynnissa?
                       [ajax-loader-pieni "Haku käynnissä..."]
                       "Aikavälille ei löytynyt tuloksia.")
              :tunniste :id
              :voi-kumota? false
              :piilota-toiminnot? true
              :sivuta grid/vakiosivutus
              :mahdollista-rivin-valinta? false
              :rivi-jalkeen-fn (fn [rivit]
                                 (let [rivien-maara (count rivit)
                                       yhteensa-hinta (reduce + (map :hinta rivit))]
                                   [[{:teksti "Yhteensä" :luokka "yhteensa"}
                                     {:teksti (str rivien-maara " kpl") :luokka "yhteensa"}
                                     {:teksti (str (fmt/euro-opt false yhteensa-hinta) " €") :tasaa :oikea :luokka "yhteensa"}]]))}

   [{:otsikko-komp (fn [_ _]
                     [:div.pvm "Päivämäärä"
                      [:div [ikonit/action-sort-descending]]])
     :tyyppi :komponentti
     :komponentti (fn [arvo _] (str (pvm/pvm (:pvm arvo))))
     :luokka "caption text-nowrap"
     :leveys 0.2}

    {:otsikko "Kustannuslaji"
     :tyyppi :string
     :nimi :selite
     :luokka "text-nowrap"
     :leveys 0.2}

    {:otsikko "Kustannus"
     :tyyppi :euro
     :tasaa :oikea
     :nimi :hinta
     :luokka "text-nowrap"
     :leveys 0.2}]
   rivit])


(defn- suodattimet-aikavali
  "Urakkavuosi valinta, triggeröi haun"
  [e! {:keys [aikavali]}]
  (let [fn-aikavali-muuttunut? (fn [aika]
                                 (let [alku (-> @urakka-tiedot/valittu-hoitokausi first)
                                       loppu (-> @urakka-tiedot/valittu-hoitokausi second)
                                       valinnat-alku (-> aika first)
                                       valinnat-loppu (-> aika second)]
                                   (boolean (or
                                              (not= alku valinnat-alku)
                                              (not= loppu valinnat-loppu)))))]

    [:div {:on-click #(when (fn-aikavali-muuttunut? aikavali) (e! (tiedot/->HaeTiedot)))}
     [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]]))


(defn yhteenveto-listaus [e! {:keys [rivit valinnat muokataan valittu-rivi
                                     haku-kaynnissa? kustannukset tyypit] :as app}]
  (let [lisaa-uusi-fn nil
        voi-tallentaa? nil
        voi-kirjoittaa? nil
        muokkauspaneeli nil
        aikavali (suodattimet-aikavali e! valinnat)
        grid (yhteenveto-grid e! rivit haku-kaynnissa?)]
    (yhteiset/nakyma-body "Kustannusten yhteenveto" lisaa-uusi-fn aikavali valinnat muokataan nil grid nil)))


(defn kustannusten-yhteenveto* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan
      #(e! (tiedot/->HaeTiedot)))
    (fn [e! app] [yhteenveto-listaus e! app])))


(defn kustannusten-yhteenveto []
  [tuck tiedot/tila kustannusten-yhteenveto*])
