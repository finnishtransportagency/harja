(ns harja.kyselyt.toteutuneet-kustannukset
  "Kustannusarvioitu_tyo taulusta toteutuneet_kustannukset tauluun siirretyt asiat."
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/toteutuneet_kustannukset.sql"
  {:positional? true})

(declare siirra-budjetoidut-tyot-toteutumiin hae-siirtamattomat-kustannukset)
