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
   [harja.pvm :as pvm]
   [harja.ui.valinnat :as valinnat]
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
    ;; Joillain urakoilla näemmä satoja kohteita 
    ;; Pidetään sivutus, käyttökokemus muuten tosi surkea
    :sivuta 10
    :mahdollista-rivin-valinta? false
    :piilota-toiminnot? true
    :voi-poistaa? (constantly false)
    :rivi-jalkeen-fn (fn [kustannukset]
                       (let [linjamerkinnat-summa (tiedot/kustannusten-summa kustannukset :linjamerkinnat)
                             pienmerkinnat-summa (tiedot/kustannusten-summa kustannukset :pienmerkinnat)
                             jyrsinnat-summa (tiedot/kustannusten-summa kustannukset :jyrsinnat)
                             muut-kustannukset-summa (tiedot/kustannusten-summa kustannukset :muut-kustannukset)]
                         [{:teksti "Yhteensä" :sarakkeita (if (= taulukon-tyyppi :paallystys) 4 3) :luokka "yhteensa"}
                          {:teksti (fmt/euro-opt linjamerkinnat-summa) :luokka "yhteensa" :tasaa :oikea}
                          {:teksti (fmt/euro-opt pienmerkinnat-summa) :luokka "yhteensa" :tasaa :oikea}
                          {:teksti (fmt/euro-opt jyrsinnat-summa) :luokka "yhteensa" :tasaa :oikea}
                          {:teksti (fmt/euro-opt muut-kustannukset-summa) :luokka "yhteensa" :tasaa :oikea}
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
    {:otsikko "Muut kustannukset (EUR)"
     :nimi :muut-kustannukset
     :muokattava? (constantly true)
     :tyyppi :euro
     :tasaa :oikea
     :leveys 2}
    {:otsikko "Yhteensä"
     :nimi :yhteensa
     :luokka "yhteensa"
     :hae (fn [kohde] (tiedot/rivin-kustannusten-summa kohde [:linjamerkinnat :pienmerkinnat :jyrsinnat :muut-kustannukset]))
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

(defn uusien-kustannusten-merkinnat* [e! _app] 
    (komp/luo
      (komp/sisaan #(do 
                      (when (u/koko-urakkakausi-valittuna?) (u/valitse-kuluva-hk!))
                      (e! (tiedot/->HoitokausiVaihdettu @u/valittu-aikavali)))) 
      
      (fn [e! {:keys [kustannukset paikkaus-kustannukset 
                      haku-kaynnissa? urakan-hoitokaudet valittu-hoitokausi] :as _app}]
        
        [:div.livi-grid.tiemerkinta-kustannusten-kirjaus
         [:h1 "Uusien päällysteiden tiemerkinnät"]
         ;; [debug/debug app]

         ;; Aikaväli 
         [:div.flex-row.margin-bottom-16
          [valinnat/urakan-hoitokausi-tuck valittu-hoitokausi urakan-hoitokaudet
          #(e! (tiedot/->HoitokausiVaihdettu %))
          {:wrapper-luokka "label-ja-alasveto hoitokausi"}]]
         
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
