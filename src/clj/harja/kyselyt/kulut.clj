(ns harja.kyselyt.kulut
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kulut.sql"
  {:positional? false})

(declare luo-kulu<! hae-urakan-kulut hae-kulu hae-kulun-kohdistukset
         paivita-kulu<! linkita-kulu-ja-liite<! tarkista-kohdistuksen-yhteensopivuus
         hae-liitteet poista-kulun-ja-liitteen-linkitys! poista-kulu!
         poista-kulun-kohdistukset! hae-kulut-kohdistuksineen-tietoineen-vientiin
  hae-urakan-kulut-kohdistuksineen hae-tehtavan-nimi hae-tehtavaryhman-nimi luo-kulun-kohdistus<!
  paivita-kulun-kohdistus<! hae-pvm-laskun-numerolla poista-kulun-kohdistus!)

