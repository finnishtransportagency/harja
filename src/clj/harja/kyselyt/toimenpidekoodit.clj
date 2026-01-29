(ns harja.kyselyt.toimenpidekoodit
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/toimenpidekoodit.sql"
  {:positional? true})

(declare hae-tehtava-tunnisteella listaa-tehtavat listaa-tehtavaryhmat listaa-toimenpiteet-analytiikalle
  onko-kaytossa? onko-olemassa? hae-tehtavan-nopeusrajoitus hae-tehtava-apitunnisteella)
