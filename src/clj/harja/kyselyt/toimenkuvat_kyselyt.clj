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
    "sopimusvastaava (ennen urakka-ajan alkua)" 1
    "sopimusvastaava" 2
    "vastuunalainen työnjohtaja (ennen urakka-ajan alkua)" 3
    "vastuunalainen työnjohtaja" 4
    "2. työnjohtaja" 5
    "3. työnjohtaja" 6
    "päätoiminen apulainen" 7
    "päätoiminen apulainen (talvikausi)" 7
    "päätoiminen apulainen (kesäkausi)" 8
    "apulainen/työnjohtaja" 9
    "apulainen/työnjohtaja (talvikausi)" 9
    "apulainen/työnjohtaja (kesäkausi)" 10
    "viherhoidosta vastaava henkilö" 11
    "hankintavastaava" 12
    "työnjohtoharjoittelija" 13
    "harjoittelija" 14
    99)) ;; Muu toimenkuva, joka ei ole listassa
