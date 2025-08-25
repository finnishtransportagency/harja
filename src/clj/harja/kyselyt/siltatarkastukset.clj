(ns harja.kyselyt.siltatarkastukset
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/siltatarkastukset.sql"
  {:positional? true})

(declare hae-urakan-sillat hae-urakan-sillat-korjattavat hae-urakan-sillat-ohjelmoitavat hae-urakan-sillat-korjatut
  hae-siltatarkastus hae-sillan-tarkastukset paivita-siltatarkastuksen-kohteet! paivita-siltatarkastus!
  luo-siltatarkastus<! luo-siltatarkastuksen-kohde<! hae-sillan-urakat lisaa-liite-siltatarkastuskohteelle<!)
