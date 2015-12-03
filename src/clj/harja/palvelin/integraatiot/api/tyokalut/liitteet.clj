(ns harja.palvelin.integraatiot.api.tyokalut.liitteet
  (:require [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.kyselyt.laatupoikkeamat :as laatupoikkeamat]
            [harja.kyselyt.turvallisuuspoikkeamat :as turvallisuuspoikkeamat])
  (:import (java.util Base64)))

(defn dekoodaa-base64 [data]
  (.decode (Base64/getDecoder) data))

(defn tallenna-liitteet-laatupoikkeamalle [db liitteiden-hallinta urakan-id laatupoikkeama-id kirjaaja liitteet]
  (doseq [liitteen-data liitteet]
    (when (:sisalto (:liite liitteen-data))
      (let [liite (:liite liitteen-data)
            tyyppi (:tyyppi liite)
            tiedostonimi (:nimi liite)
            data (dekoodaa-base64 (:sisalto liite))
            koko (alength data)
            liite-id (:id (liitteet/luo-liite liitteiden-hallinta (:id kirjaaja) urakan-id tiedostonimi tyyppi koko data))]
        (laatupoikkeamat/liita-laatupoikkeama<! db laatupoikkeama-id liite-id)))))

(defn tallenna-liitteet-turvallisuuspoikkeamalle [db liitteiden-hallinta urakan-id tp-id kirjaaja liitteet]
  (doseq [liitteen-data liitteet]
    (when (:sisalto (:liite liitteen-data))
      (let [liite (:liite liitteen-data)
            tyyppi (:tyyppi liite)
            tiedostonimi (:nimi liite)
            data (dekoodaa-base64 (:sisalto liite))
            koko (alength data)
            liite-id (:id (liitteet/luo-liite liitteiden-hallinta (:id kirjaaja) urakan-id tiedostonimi tyyppi koko data))]
        (turvallisuuspoikkeamat/liita-liite<! db tp-id liite-id)))))
