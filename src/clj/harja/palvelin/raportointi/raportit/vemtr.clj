(ns harja.palvelin.raportointi.raportit.vemtr
  "Valtakunnallinen ja ELY-kohtainen määrätoteumaraportti"
  (:require [harja.kyselyt
             [urakat :as urakat-q]
             [tehtavamaarat :as tm-q]
             [toteumat :as toteuma-q]]
            [harja.palvelin.raportointi.raportit.tehtavamaarat :as tm-r]
            [harja.pvm :as pvm])
  (:import (java.math RoundingMode)))

;; toteutusta vaille valmis


(defn suorita
  [db user params]
  (let [{:keys [otsikot rivit debug]} (tm-r/muodosta-taulukko db user params)]
    [:raportti
     {:nimi "Tehtävämäärät"}
     [:taulukko
      {:otsikko    "Määrätoteumat ajalta "
       :sheet-nimi "Määrätoteumat"}
      otsikot
      rivit]]))
