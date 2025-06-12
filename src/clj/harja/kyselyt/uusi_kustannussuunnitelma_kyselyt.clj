(ns harja.kyselyt.uusi-kustannussuunnitelma-kyselyt
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/uusi_kustannussuunnitelma_kyselyt.sql"
  {:positional? true})

(declare hae-urakan-toimenpiteet hae-kuukausittainen-kiintea-kustannus
  hae-kiinteat-kustannukset-kuukausittain poista-kiinteat-kustannukset-kuukausittain!
  tallenna-kiinteat-kustannukset-kuukausittain<! paivita-kiinteat-kustannukset-kuukausittain<!)
