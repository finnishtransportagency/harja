(ns harja.palvelin.palvelut.suunnittelu.suunnittelu-apurit
  (:require [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakat-q]))

(defn jasenna-tallennettavat-vuodet
  "Jäsentää talletettavat hoitovuodet listaksi.
  Jos kopioi-tuleville-vuosille? on true, niin palauttaa kaikki hoitovuodet urakan alkamisvuodesta urakan loppumisvuoteen asti.
  Muuten palauttaa vain hoitovuoden-alkuvuoden."
  [db urakka-id hoitovuoden-alkuvuosi kopioi-tuleville-vuosille?]
  (if kopioi-tuleville-vuosille?
    (let [urakan-tiedot (first (urakat-q/hae-urakan-tiedot db urakka-id))
          urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
          urakan-loppuvuosi (pvm/vuosi (:loppupvm urakan-tiedot))
          ;; Varmista, että hoitovuoden-alkuvuosi on urakan sisällä
          hoitovuoden-alkuvuosi (cond
                                  (< hoitovuoden-alkuvuosi urakan-alkuvuosi) urakan-alkuvuosi
                                  (>= hoitovuoden-alkuvuosi urakan-loppuvuosi) urakan-loppuvuosi
                                  :else hoitovuoden-alkuvuosi)
          vuodet (range hoitovuoden-alkuvuosi urakan-loppuvuosi)]
      vuodet)
    [hoitovuoden-alkuvuosi]))
