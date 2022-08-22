(ns harja.kyselyt.toteutuneet_kustannukset
  "Kustannusarvioitu_tyo taulusta toteutuneet_kustannukset tauluun siirretyt asiat."
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [upsert! delete!]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]])
  (:use [slingshot.slingshot :only [throw+]]))

(defqueries "harja/kyselyt/toteutuneet_kustannukset.sql"
            {:positional? true})