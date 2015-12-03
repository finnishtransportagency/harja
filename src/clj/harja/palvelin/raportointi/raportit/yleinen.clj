(ns harja.palvelin.raportointi.raportit.yleinen
  (:require [yesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [clj-time.local :as l]))

(defn raportin-otsikko
  [konteksti nimi alkupvm loppupvm]
  (let [kk-vali? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        kkna-ja-vuonna (pvm/kuukautena-ja-vuonna (l/to-local-date-time alkupvm))]
    (if kk-vali?
      (str konteksti ", " nimi " " kkna-ja-vuonna)
      (str konteksti ", " nimi " ajalta "
           (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm)))))