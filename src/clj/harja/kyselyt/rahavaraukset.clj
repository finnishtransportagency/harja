(ns harja.kyselyt.rahavaraukset
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/rahavaraukset.sql"
  {:positional? true})

(declare hae-urakan-rahavaraukset-ja-tehtavaryhmat hae-rahavarauksen-tehtavaryhmat hae-urakan-rahavaraukset
  hae-rahavarauksen-toimenpideinstanssi hae-rahavaraukset hae-urakoiden-rahavaraukset hae-rahavaraukset-tehtavineen
  kuuluuko-tehtava-rahavaraukselle? onko-tehtava-olemassa? onko-rahavaraus-olemassa?
  hae-rahavaraukselle-mahdolliset-tehtavat hae-urakan-rahavaraus paivita-urakan-rahavaraus<!
  lisaa-urakan-rahavaraus<! poista-urakan-rahavaraus<! lisaa-uusi-rahavaraus<! lisaa-rahavaraukselle-tehtava<!
  poista-rahavaraukselta-tehtava! onko-rahavaraus-kaytossa? poista-rahavaraus-urakoilta!
  poista-rahavarauksen-tehtavat! poista-rahavaraus!)
