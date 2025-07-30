(ns harja.kyselyt.toimenkuvat-kyselyt
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/toimenkuvat_kyselyt.sql"
  {:positional? true})

(declare hae-urakoiden-toimenkuvat hae-toimenkuvat hae-urakan-toimenkuva hae-urakan-toimenkuvat
  onko-toimenkuva-olemassa? onko-toimenkuva-kaytossa? poista-toimenkuva-urakoilta! poista-toimenkuva!
  lisaa-uusi-toimenkuva<! lisaa-urakan-toimenkuva<! paivita-urakan-toimenkuva<! poista-urakan-toimenkuva<!)
