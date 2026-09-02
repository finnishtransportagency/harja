(ns harja.kyselyt.sopimukset
  "Sopimuksiin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/sopimukset.sql"
  {:positional? true})

(declare hae-urakan-paasopimus onko-olemassa)

(defn onko-olemassa? [db urakka-id sopimus-id]
  (:exists (first (onko-olemassa db urakka-id sopimus-id))))

(declare hae-urakan-sopimus-idt paivita-urakka-sampoidlla! poista-kaikki-sopimukset-urakasta!
  aseta-sopimuksien-paasopimus! aseta-sopimus-paasopimukseksi! liita-sopimukset-urakkaan!)
