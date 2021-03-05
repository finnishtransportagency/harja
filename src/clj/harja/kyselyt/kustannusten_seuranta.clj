(ns harja.kyselyt.kustannusten-seuranta
  "Toteumien ja toteuman reittien kyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [upsert! delete!]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]])
  (:use [slingshot.slingshot :only [throw+]]))

(defqueries "harja/kyselyt/kustannusten_seuranta.sql"
            {:positional? true})

