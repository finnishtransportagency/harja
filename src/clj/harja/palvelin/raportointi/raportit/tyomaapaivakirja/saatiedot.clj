(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.saatiedot
  "Työmaapäiväkirja -näkymän säätietojen gridit"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.domain.ely :as ely]
   [harja.domain.tierekisteri :as tr-domain]
   [clojure.string :as str]
   [harja.pvm :as pvm]
   [taoensso.timbre :as log]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn saatiedot-taulukkojen-parametrit []
  (let [taulukon-optiot {:piilota-border? false
                         :viimeinen-rivi-yhteenveto? false}
        taulukon-otsikot (rivi
                           {:otsikko "Klo" :leveys 12 :tyyppi :varillinen-teksti}
                           {:otsikko "Ilma" :leveys 12 :tyyppi :varillinen-teksti}
                           {:otsikko "Tie" :leveys 12 :tyyppi :varillinen-teksti}
                           {:otsikko "S-olom" :leveys 12 :tyyppi :varillinen-teksti}
                           {:otsikko "K-tuuli" :leveys 12 :tyyppi :varillinen-teksti}
                           {:otsikko "S-Sum" :leveys 12 :tyyppi :varillinen-teksti})
        rivit (into []
                (remove nil?
                  [(yhteiset/taulukon-rivi "Rivi 1" true)
                   (yhteiset/taulukon-rivi "Rivi 2" true)
                   (yhteiset/taulukon-rivi "Rivi 3" true)]))

        taulukko-1 {:otsikko-vasen "Tie 4, Oulu, Ritaharju" :optiot-vasen taulukon-optiot :otsikot-vasen taulukon-otsikot :rivit-vasen rivit}
        taulukko-2 {:otsikko-oikea "Tie 20, Oulu, Hönttämäki" :optiot-oikea taulukon-optiot :otsikot-oikea taulukon-otsikot :rivit-oikea rivit}]
    [taulukko-1 taulukko-2]))

(defn saatietojen-taulukot []
  [:otsikko-heading "Sääasemien tiedot"]
  [:gridit-vastakkain (first (saatiedot-taulukkojen-parametrit)) (second (saatiedot-taulukkojen-parametrit))])
