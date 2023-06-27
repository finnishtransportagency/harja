(ns harja.palvelin.raportointi.raportit.kanavien-liikennetapahtumat
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.kyselyt.urakat :as urakat-q]))


(defn suorita [db user {:keys [urakoiden-nimet sarakkeet rivit parametrit]
                        :as kaikki-parametrit}]

  (let [{:keys [alkupvm loppupvm valitut-urakat]} parametrit
        raportin-nimi "Liikennetapahtumat"
        ;; Urakoiden-nimet on nil kun 1 urakka valittuna, haetaan tällöin valitun urakan nimi toisesta muuttujasta
        urakoiden-nimet (or urakoiden-nimet (first valitut-urakat))
        raportin-otsikko (raportin-otsikko urakoiden-nimet raportin-nimi alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi raportin-otsikko}

     [:taulukko {:otsikko raportin-nimi
                 :tyhja (when (empty? rivit) "Ei raportoitavaa.")
                 :sheet-nimi "Yhteensä"}
      sarakkeet
      rivit]
     
     [:liikenneyhteenveto (:yhteenveto parametrit)]]))
