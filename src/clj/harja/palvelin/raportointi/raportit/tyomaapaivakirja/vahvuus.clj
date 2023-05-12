(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.vahvuus
  "Työmaapäiväkirja -näkymän urakan henkilöstö gridit"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.domain.ely :as ely]
   [harja.domain.tierekisteri :as tr-domain]
   [clojure.string :as str]
   [harja.pvm :as pvm]
   [taoensso.timbre :as log]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn vahvuus-taulukkojen-parametrit []
  (let [taulukon-optiot {:piilota-border? false
                         :viimeinen-rivi-yhteenveto? false}
        taulukon-otsikot (rivi
                           {:otsikko "Otsikko 1" :leveys 12 :tyyppi :varillinen-teksti}
                           {:otsikko "Otsikko 2" :leveys 48 :tyyppi :varillinen-teksti})
        rivit (into []
                (remove nil?
                  [(yhteiset/taulukon-rivi "Rivi 1" true)
                   (yhteiset/taulukon-rivi "Rivi 2" true)
                   (yhteiset/taulukon-rivi "Rivi 3" true)]))

        taulukko-1 {:otsikko-vasen "Päivystäjät" :optiot-vasen taulukon-optiot :otsikot-vasen taulukon-otsikot :rivit-vasen rivit}
        taulukko-2 {:otsikko-oikea "Työnjohtajat" :optiot-oikea taulukon-optiot :otsikot-oikea taulukon-otsikot :rivit-oikea rivit}]
    [taulukko-1 taulukko-2]))

(defn vahvuus-gridit []
  [:otsikko-heading "Vahvuus"]
  [:gridit-vastakkain (first (vahvuus-taulukkojen-parametrit)) (second (vahvuus-taulukkojen-parametrit))])
