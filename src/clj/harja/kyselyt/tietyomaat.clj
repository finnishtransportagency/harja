(ns harja.kyselyt.tietyomaat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tietyomaat.sql")

(declare onko-olemassa? merkitse-tietyomaa-poistetuksi! paivita-tietyomaa! luo-tietyomaa<!)
