(ns harja.kyselyt.raportit
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/raportit.sql")

(declare paivita_raportti_toteutuneet_materiaalit paivita-suorituksen-kesto<! luo-suoritustieto<!
  paivita_raportti_pohjavesialueiden_suolatoteumat paivita_raportti_toteuma_maarat)
