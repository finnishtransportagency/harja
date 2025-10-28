(ns harja.palvelin.palvelut.suunnittelu.suunnittelu-apurit
  "Apureita uuden kustannussuunnitelman ja tehtävät ja määrät -palveluille."
  (:require [harja.domain.mhu :as mhu]
            [harja.kyselyt.indeksit :as indeksi-kyselyt]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as suunnitelma-q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.suunnittelu.uusi-kustannussuunnitelma-domain :as k-domain]
            [harja.palvelin.palvelut.budjettisuunnittelu :as budjettisuunnittelu]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]))

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
