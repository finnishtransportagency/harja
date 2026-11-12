(ns harja.kyselyt.lampotilat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/lampotilat.sql"
  {:positional? true})

(declare hae-urakan-lampotilat hae-urakoiden-talvisuolarajat hae-urakan-talvisuojarajat-yhteensa
  luo-suolasakko<! paivita-suolasakko! paivita-lampotila<! uusi-lampotila<!
  hae-suolasakko-id hae-urakan-suolasakot
  tallenna-pohjavesialue-talvisuola<! paivita-pohjavesialue-talvisuola!
  hae-urakan-pohjavesialue-talvisuolarajat-teittain hae-teiden-hoitourakoiden-lampotilat)
