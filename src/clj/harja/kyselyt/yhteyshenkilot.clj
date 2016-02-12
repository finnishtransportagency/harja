(ns harja.kyselyt.yhteyshenkilot
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/yhteyshenkilot.sql")

(defn onko-olemassa-yhteyshenkilo-ulkoisella-idlla? [db ulkoinen-id]
  (:exists (first (onko-olemassa-yhteyshenkilo-ulkoisella-idlla db ulkoinen-id))))

(defn onko-olemassa-paivystys-jossa-yhteyshenkilona-id? [db paivystaja-id]
  (:exists (first (onko-olemassa-paivystys-jossa-yhteyshenkilona-id db paivystaja-id))))

(defn hae-urakan-tamanhetkinen-paivystaja [db urakkaid]
  (let [paivystajat (hae-urakan-taman-hetkiset-paivystajat db urakkaid)]
    (if (= 1 (count paivystajat))
      (first paivystajat)
      (when (< 0 (count paivystajat))
        (if (some :vastuuhenkilo paivystajat)
          (first (filter :vastuuhenkilo paivystajat))
          (first paivystajat))))))