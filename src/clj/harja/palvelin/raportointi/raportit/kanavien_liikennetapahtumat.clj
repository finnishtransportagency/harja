(ns harja.palvelin.raportointi.raportit.kanavien-liikennetapahtumat
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.kyselyt.urakat :as urakat-q]))


(defn suorita [db user {:keys [urakoiden-nimet alkupvm loppupvm
                               sarakkeet rivit] :as parametrit}]
  (println " SUORITA LIIKENNETAPAHTUMAT " parametrit)
  (let [raportin-nimi "Liikennetapahtumat"
        raportin-otsikko (raportin-otsikko urakoiden-nimet raportin-nimi alkupvm loppupvm)
        _ (println " sarakkeet " sarakkeet)
        _ (println " rivit " rivit)]
    (log/debug "Kanavien Liikennetapahtumat, suorita: " parametrit)

    [:raportti {:orientaatio :landscape
                :nimi raportin-otsikko}

     [:taulukko {:otsikko raportin-nimi
                 :tyhja (when (empty? rivit) "Ei raportoitavaa.")
                 :sheet-nimi "Yhteensä"}
      sarakkeet
      rivit]]))
