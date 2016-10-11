(ns harja.domain.laadunseuranta.sanktiot)

(defn sakko? [sanktio]
  (not (= :muistutus (:laji sanktio))))