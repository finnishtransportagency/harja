(ns harja.palvelin.integraatiot.api.tyokalut.liitteet
  (:require [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.kyselyt.havainnot :as havainnot])
  (:import (java.util Base64)))

(defn dekoodaa-base64 [data]
  (.decode (Base64/getDecoder) data))

(defn tallenna-liitteet-havainnolle [db liitteiden-hallinta urakan-id havainto-id kirjaaja liitteet]
  (doseq [liitteen-data liitteet]
    (when (:sisalto (:liite liitteen-data))
      (let [liite (:liite liitteen-data)
            tyyppi (:tyyppi liite)
            tiedostonimi (:nimi liite)
            data (dekoodaa-base64 (:sisalto liite))
            koko (alength data)
            liite-id (:id (liitteet/luo-liite liitteiden-hallinta (:id kirjaaja) urakan-id tiedostonimi tyyppi koko data))]
        (havainnot/liita-havainto<! db havainto-id liite-id)))))