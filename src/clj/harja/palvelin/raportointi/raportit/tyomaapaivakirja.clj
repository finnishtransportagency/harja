(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja
  "Työmaapäiväkirja -näkymän raportti"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.domain.ely :as ely]
   [harja.domain.tierekisteri :as tr-domain]
   [clojure.string :as str]
   [harja.pvm :as pvm]
   [taoensso.timbre :as log]))

(defn- taulukon-rivi
  [test lihavoi?]
  (let []
    (rivi
      [:varillinen-teksti {:arvo "AaAa"}]
      [:varillinen-teksti {:arvo (str test) :lihavoi? lihavoi?}])))


(defn- taulukko [{:keys [otsikot tiedot]}]
  (let [rivit (into []
                (remove nil?
                  [(taulukon-rivi "Test 1" true)
                   (taulukon-rivi "Test 2" true)]))]

    [:taulukko {:piilota-border? true
                :viimeinen-rivi-yhteenveto? false}

     (rivi
       {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 12 :tyyppi :varillinen-teksti}
       {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 48 :tyyppi :varillinen-teksti})

     rivit]))


(defn suorita [_ _ {:keys [valittu-rivi] :as parametrit}]
  (let [_ (println "\n \n Params T: " parametrit)
        otsikko "Test"

        rivit (into []
                (remove nil?
                  [(taulukon-rivi "Rivi 1" true)
                   (taulukon-rivi "Rivi 2" true)]))
        taulukon-optiot {:piilota-border? false
                         :viimeinen-rivi-yhteenveto? false}
        taulukon-otsikot (rivi
                           {:otsikko "Otsikko 1" :leveys 12 :tyyppi :varillinen-teksti}
                           {:otsikko "Otsikko 2" :leveys 48 :tyyppi :varillinen-teksti})

        taulukko-1 {:otsikko-vasen "Päivystäjät" :optiot-vasen taulukon-optiot :otsikot-vasen taulukon-otsikot :rivit-vasen rivit}
        taulukko-2 {:otsikko-oikea "Työnjohtajat" :optiot-oikea taulukon-optiot :otsikot-oikea taulukon-otsikot :rivit-oikea rivit}]


    [:raportti {:nimi otsikko
                :piilota-otsikko? true}

     [:tyomaapaivakirja-header valittu-rivi]

     ;; Päivystäjät, Työnjohtajat
     [:otsikko-heading "Vahvuus"]
     [:gridit-vastakkain taulukko-1 taulukko-2]]))
