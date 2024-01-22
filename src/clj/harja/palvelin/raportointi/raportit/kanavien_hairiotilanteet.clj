(ns harja.palvelin.raportointi.raportit.kanavien-hairiotilanteet
  "Häiriötilanne raportti"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [clojure.string :as str]
   [harja.pvm :as pvm]
   [taoensso.timbre :as log]))


(defn suorita [_ _ {:keys [sarakkeet rivit parametrit]}]
  
  (let [{:keys [tiedot]} parametrit
        otsikko "Häiriötilanteet"]

    [:raportti {:nimi otsikko
                :piilota-otsikko? true}

     [:taulukko {:otsikko otsikko
                 :tyhja (when (empty? rivit) "Ei raportoitavaa.")
                 :sheet-nimi otsikko}
      sarakkeet
      rivit]]))
