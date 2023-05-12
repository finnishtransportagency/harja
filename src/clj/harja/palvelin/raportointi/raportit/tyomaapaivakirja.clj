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
      [:varillinen-teksti {:arvo ""}]
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
  (let [ _ (println "\n \n Params T: " parametrit)
        otsikko "Test"]

    [:raportti {:nimi otsikko
                :piilota-otsikko? true}
     
     [:tyomaapaivakirja-header valittu-rivi]

     (taulukko {:tiedot parametrit
                :sheet-nimi "ilmoitukset"})]))
