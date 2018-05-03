(ns harja.palvelin.raportointi.raportit.kanavien-liikennetapahtumat
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.kyselyt.urakat :as urakat-q]))


(defn suorita [db user {:keys [parametrit sarakkeet rivit] :as kaikki-parametrit}]

  (let [{:keys [urakoiden-nimet alkupvm loppupvm]} parametrit
        raportin-nimi "Liikennetapahtumat"
        raportin-otsikko (raportin-otsikko urakoiden-nimet raportin-nimi alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi raportin-otsikko}

     [:taulukko {:otsikko raportin-nimi
                 :tyhja (when (empty? rivit) "Ei raportoitavaa.")
                 :sheet-nimi "Yhteensä"}
      sarakkeet
      rivit]]))
