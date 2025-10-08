(ns harja.kyselyt.raportit
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/raportit.sql")

(declare paivita_raportti_toteutuneet_materiaalit)
