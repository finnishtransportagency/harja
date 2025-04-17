(ns harja.views.urakka.tiemerkinta-kustannukset.uusien-paallysteiden-merkinnat-nakyma
  (:require
   [harja.fmt :as fmt]
   [harja.tiedot.navigaatio :as nav]
   [harja.tiedot.urakka :as u]
   [harja.tiedot.urakka.urakka :as tila]
   [harja.tiedot.urakka.uusien-paallysteiden-merkinnat-tiedot :as tiedot]
   [harja.tyokalut.tuck :as tuck-apurit]
   [harja.ui.debug :as debug]
   [harja.ui.grid :as grid]
   [harja.ui.komponentti :as komp]
   [harja.ui.yleiset :as yleiset]
   [harja.views.urakka.valinnat :as urakka-valinnat]
   [tuck.core :refer [tuck]]))

(defn uusien-kustannusten-merkinnat-taulukko [e! otsikko kustannukset tallenna-fn taulukon-tyyppi tyhja haku-kaynnissa?]
  [grid/grid
   {:otsikko otsikko
    :tyhja  (if haku-kaynnissa?
              [yleiset/ajax-loader-pieni "Haku käynnissä..."]
              (or tyhja "Aikavälille ei löytynyt tuloksia."))
    :voi-lisata? false
    :muokattava? true
    :tunniste :id
    :mahdollista-rivin-valinta? false
    :piilota-toiminnot? true
    :voi-poistaa? (constantly false)
    :rivi-jalkeen-fn (fn [kustannukset]
                       (let [linjamerkinnat-summa (tiedot/kustannusten-summa kustannukset :linjamerkinnat)
                             pienmerkinnat-summa (tiedot/kustannusten-summa kustannukset :pienmerkinnat)
                             jyrsinnat-summa (tiedot/kustannusten-summa kustannukset :jyrsinnat)]
                         [{:teksti "Yhteensä" :sarakkeita 5 :luokka "yhteensa"}
                          {:teksti (fmt/euro-opt linjamerkinnat-summa) :luokka "yhteensa" :tasaa :oikea}
                          {:teksti (fmt/euro-opt pienmerkinnat-summa) :luokka "yhteensa" :tasaa :oikea}
                          {:teksti (fmt/euro-opt jyrsinnat-summa) :luokka "yhteensa" :tasaa :oikea}
                          {:teksti "" :sarakkeita 2 :luokka "yhteensa"}]))
    :tallenna #(tuck-apurit/e-kanavalla! e! tallenna-fn %)}
   [{:otsikko "Kohdenro"
     :nimi :kohdenumero
     :muokattava? (constantly false)
     :tyyppi :string
     :leveys 2}
    (when (= taulukon-tyyppi :paallystys)
      {:otsikko "YHA-ID"
       :nimi :yha-id
       :muokattava? (constantly false)
       :tyyppi :string
       :leveys 2})
    {:otsikko "Kohteen nimi"
     :nimi :nimi
     :muokattava? (constantly false)
     :tyyppi :string
     :leveys 3}
    {:otsikko "Tieosoite"
     :nimi :tieosoite
     :hae (fn [rivi] (fmt/tieosoite-lyhyt-muoto rivi))
     :muokattava? (constantly false)
     :tyyppi :string
     :leveys 4}
    {:otsikko "Linjamerkinnät (EUR)"
     :nimi :linjamerkinnat
     :muokattava? (constantly true)
     :tyyppi :euro
     :tasaa :oikea
     :leveys 2}
    {:otsikko "Pienmerkinnät (EUR)"
     :nimi :pienmerkinnat
     :muokattava? (constantly true)
     :tyyppi :euro
     :tasaa :oikea
     :leveys 2}
    {:otsikko "Jyrsinnät (EUR)"
     :nimi :jyrsinnat
     :muokattava? (constantly true)
     :tyyppi :euro
     :tasaa :oikea
     :leveys 2}
    {:otsikko "Yhteensä"
     :nimi :yhteensa
     :luokka "yhteensa"
     :hae (fn [kohde] (tiedot/rivin-kustannusten-summa kohde [:linjamerkinnat :pienmerkinnat :jyrsinnat]))
     :fmt fmt/euro-opt
     :muokattava? (constantly false)
     :tasaa :oikea
     :tyyppi :positiivinen-numero
     :leveys 2}
    {:otsikko "Pk-lk"
     :nimi :pk-luokka
     :muokattava? (constantly false)
     :tyyppi :string
     :tasaa :oikea
     :leveys 2}]
   kustannukset])

(defn uusien-kustannusten-merkinnat* [e!] 
    (komp/luo
      (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                      (e! (tiedot/->PaivitaValinnat uusi))))
      (komp/sisaan #(do (e! (tiedot/->HaePaallystysKustannukset))
                      (e! (tiedot/->HaePaikkausKustannukset))
                      (e! (tiedot/->PaivitaValinnat
                            {:urakka @nav/valittu-urakka
                             :aikavali @u/valittu-aikavali
                             :valittu-hoitokausi @u/valittu-hoitokausi}))))
      (fn [e! {:keys [kustannukset paikkaus-kustannukset haku-kaynnissa?] :as app}]
        @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity. 
        [:div.livi-grid.tiemerkinta-kustannusten-kirjaus
         [:h1 "Uusien päällysteiden tiemerkinnät"] 
         ;; [debug/debug app] 
         [:div
          [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]]
         [uusien-kustannusten-merkinnat-taulukko e! 
          "Päällystyskohteiden tiemerkintäkustannukset" 
          kustannukset 
          tiedot/->TallennaPaallystysKustannukset 
          :paallystys
          nil
          haku-kaynnissa?]
         [uusien-kustannusten-merkinnat-taulukko e! 
          "Paikkauskohteiden tiemerkintäkustannukset"
          paikkaus-kustannukset 
          tiedot/->TallennaPaikkausKustannukset 
          :paikkaus
          "Ei kohteita valitulla vuodella. Paikkauskohteet listataan kun tiemerkintä on merkitty tehdyksi."
          haku-kaynnissa?]])))

(defn uusien-paallysteiden-merkinnat []
  [tuck tila/tiemerkinta-uusien-paallysteiden-merkkinnat uusien-kustannusten-merkinnat*])