(ns harja.palvelin.raportointi.raportit.yleinen
  (:require [yesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]))

(defn raportin-otsikko
  [konteksti nimi alkupvm loppupvm]
  (str konteksti ", " nimi " ajalta "
       (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm)))