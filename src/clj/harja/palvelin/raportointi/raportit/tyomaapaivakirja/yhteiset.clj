(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset
  "Työmaapäiväkirja raportin yhteiset funktiot"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.domain.ely :as ely]
   [harja.domain.tierekisteri :as tr-domain]
   [clojure.string :as str]
   [harja.pvm :as pvm]
   [taoensso.timbre :as log]))

(defn taulukon-rivi
  [test lihavoi?]
  (let []
    (rivi
      [:varillinen-teksti {:arvo "Test"}]
      [:varillinen-teksti {:arvo (str test) :lihavoi? lihavoi?}])))

(defn taulukko [{:keys [otsikot tiedot]}]
  (let [rivit (into []
                (remove nil?
                  [(taulukon-rivi "Test 1" true)
                   (taulukon-rivi "Test 2" true)]))]

    [:taulukko {:piilota-border? false
                :viimeinen-rivi-yhteenveto? false}
     
     ;; :otsikkorivi-luokka
     (rivi
       {:otsikko "Test" :leveys 12 :tyyppi :varillinen-teksti}
       {:otsikko "Test" :leveys 48 :tyyppi :varillinen-teksti})

     rivit]))
