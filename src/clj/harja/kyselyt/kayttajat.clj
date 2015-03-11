(ns harja.kyselyt.kayttajat
  (:require [yesql.core :refer [defqueries]]))

;; TODO: mieti kayttaja_organisaatio_rooli tarve uudelleen, se pitänee poistaa
;; käyttäjät on suoraan linkattu omiin organisaaioihinsa, joten ei tarvita sitä.

(defqueries "harja/kyselyt/kayttajat.sql")
