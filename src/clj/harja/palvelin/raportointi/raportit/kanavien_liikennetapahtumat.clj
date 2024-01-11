(ns harja.palvelin.raportointi.raportit.kanavien-liikennetapahtumat
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.kanavat.liikennetapahtumat :as q]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko rivi]]
            [harja.kyselyt.urakat :as urakat-q]))


(defn suorita [db user {:keys [urakoiden-nimet sarakkeet rivit parametrit]
                        :as kaikki-parametrit}]

  (let [{:keys [alkupvm loppupvm valitut-urakat]} parametrit
        raportin-nimi "Liikennetapahtumat"
        ;; Urakoiden-nimet on nil kun 1 urakka valittuna, haetaan tällöin valitun urakan nimi toisesta muuttujasta
        urakoiden-nimet (or urakoiden-nimet (first valitut-urakat))
        raportin-otsikko (raportin-otsikko urakoiden-nimet raportin-nimi alkupvm loppupvm)
        
        _ (println "\n Kutsutaan liikenneraportti, yhteenveto: " yhteenveto)

        
        ]
    [:raportti {:orientaatio :landscape
                :nimi raportin-otsikko}
     


     #_ [:taulukko {:otsikko raportin-nimi
                 :tyhja (when (empty? rivit) "Ei raportoitavaa.")
                 :sheet-nimi "Yhteensä"}
      sarakkeet
      rivit]
     
     [:liikenneyhteenveto yhteenveto]]))
