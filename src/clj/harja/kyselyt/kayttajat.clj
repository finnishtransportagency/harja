(ns harja.kyselyt.kayttajat
  (:require [jeesql.core :refer [defqueries]]))

;; TODO: mieti kayttaja_organisaatio_rooli tarve uudelleen, se pitänee poistaa
;; käyttäjät on suoraan linkattu omiin organisaaioihinsa, joten ei tarvita sitä.

(defqueries "harja/kyselyt/kayttajat.sql"
  {:positional? true})

(defn onko-kayttaja-urakan-organisaatiossa? [db urakka-id kayttaja-id]
  (:exists (first (harja.kyselyt.kayttajat/onko-kayttaja-urakan-organisaatiossa db urakka-id kayttaja-id))))

(defn onko-kayttaja-organisaatiossa? [db ytunnus kayttaja-id]
  (:exists (first (harja.kyselyt.kayttajat/onko-kayttaja-organisaatiossa db ytunnus kayttaja-id))))
