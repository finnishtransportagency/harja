(ns harja.kyselyt.api-jarjestelmatunnukset
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/api_jarjestelmatunnukset.sql")

(declare
  luo-jarjestelmatunnukselle-lisaoikeus-urakkaan<!
  lisaa-jarjestelmatunnukselle-kirjoitusoikeus!
  paivita-jarjestelmatunnuksen-lisaoikeus-urakkaan!
  poista-jarjestelmatunnuksen-lisaoikeus-urakkaan!
  luo-jarjestelmatunnus<! paivita-jarjestelmatunnus! poista-jarjestelmatunnus!
  hae-mahdolliset-api-oikeudet hae-jarjestelmatunnukset hae-urakat-lisaoikeusvalintaan
  hae-jarjestelmatunnuksen-lisaoikeudet)
