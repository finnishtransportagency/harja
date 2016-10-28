(ns harja.domain.laadunseuranta.sanktiot)

(defn sakko? [sanktio]
  (not= :muistutus (:sakkoryhma sanktio)))

(defn paatos-on-sanktio? [sanktio]
  (= :sanktio (get-in sanktio [:paatos :paatos])))