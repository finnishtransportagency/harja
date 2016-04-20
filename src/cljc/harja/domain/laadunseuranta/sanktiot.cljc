(ns harja.domain.laadunseuranta.sanktiot)

(defn sakko? [sanktio]
  (not (nil? (:summa sanktio))))