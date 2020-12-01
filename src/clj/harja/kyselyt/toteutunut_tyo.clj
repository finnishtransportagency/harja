(ns harja.kyselyt.toteutunut-tyo
  "Kustannusarvioitu_tyo taulusta toteutunut_tyo tauluun siirretyt asiat."
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [upsert! delete!]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]])
  (:use [slingshot.slingshot :only [throw+]]))

(defqueries "harja/kyselyt/toteutunut_tyo.sql"
            {:positional? true})

