(ns harja.kyselyt.kustannusten-kirjaus
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kustannusten_kirjaus.sql"
  {:positional? true})

(declare hae-tiemerkinta-kustannuskirjaukset hae-tiemerkinta-kustannuskirjaus-kustannusvuodella
  lisaa-tiemerkinta-kustannuskirjaus! paivita-tiemerkinta-kustannuskirjaus!
  hae-tiemerkinta-kustannustyypit hae-urakan-yllapitokohteiden-kustannukset
  hae-urakan-paikkauskohteiden-kustannukset hae-yllapitokustannus
  lisaa-tiemerkinta-yllapitokohde-kustannuskirjaus! paivita-tiemerkinta-yllapitokohde-kustannuskirjaus!
  hae-paikkauskustannus lisaa-tiemerkinta-paikkauskohde-kustannuskirjaus!
  paivita-tiemerkinta-paikkauskohde-kustannuskirjaus!
  hae-analytiikalle-tiemerkinta-korjauskustannukset hae-analytiikalle-tiemerkinta-yllapitokohde-kustannukset
  hae-analytiikalle-tiemerkinta-paikkauskohde-kustannukset  analytiikalle-tiemerkintaurakat-kannasta)
