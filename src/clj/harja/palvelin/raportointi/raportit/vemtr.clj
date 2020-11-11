(ns harja.palvelin.raportointi.raportit.vemtr
  "Valtakunnallinen ja ELY-kohtainen määrätoteumaraportti"
  (:require [harja.kyselyt
             [urakat :as urakat-q]
             [tehtavamaarat :as tm-q]
             [toteumat :as toteuma-q]]
            [harja.pvm :as pvm])
  (:import (java.math RoundingMode)))

;; toteutusta vaille valmis
