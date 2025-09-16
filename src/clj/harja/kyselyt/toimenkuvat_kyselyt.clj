(ns harja.kyselyt.toimenkuvat-kyselyt
  (:require [clojure.string :as str]
            [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/toimenkuvat_kyselyt.sql"
  {:positional? true})

(declare hae-toimenkuva hae-toimenkuva-idlla
  hae-2025-urakoiden-toimenkuvat hae-toimenkuvat hae-urakan-toimenkuva hae-urakan-toimenkuvat
  onko-toimenkuva-olemassa? onko-toimenkuva-kaytossa? poista-toimenkuva-urakoilta! poista-toimenkuva!
  lisaa-uusi-toimenkuva<! lisaa-urakan-toimenkuva<! paivita-urakan-toimenkuva<! poista-urakan-toimenkuva<!
  hae-urakan-toimenkuvat-alkuvuoden-perusteella)

(defn paattele-toimenkuvan-jarjestys
  "Pakotetaan toimenkuvat oikeaan järjestykseen kovakoodauksen avulla."
  [toimenkuva-nimike]
  (case (str/lower-case toimenkuva-nimike)
    "valmistelukausi ennen urakka-ajan alkua" 0
    "sopimusvastaava" 1
    "vastuunalainen työnjohtaja" 2
    "2. työnjohtaja" 3
    "3. työnjohtaja" 4
    "päätoiminen apulainen" 5
    "päätoiminen apulainen (talvikausi)" 5
    "päätoiminen apulainen (kesäkausi)" 6
    "apulainen/työnjohtaja" 7
    "apulainen/työnjohtaja (talvikausi)" 7
    "apulainen/työnjohtaja (kesäkausi)" 8
    "viherhoidosta vastaava henkilö" 9
    "hankintavastaava" 10
    "harjoittelija" 11
    99)) ;; Muu toimenkuva, joka ei ole listassa
