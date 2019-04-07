(ns harja.palvelin.integraatiot.api.tyokalut.liitteet
  (:require [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.kyselyt.laatupoikkeamat :as laatupoikkeamat]
            [harja.kyselyt.tarkastukset :as tarkastukset]
            [harja.kyselyt.turvallisuuspoikkeamat :as turvallisuuspoikkeamat]
            [harja.kyselyt.siltatarkastukset :as siltatarkastukset]
            [harja.kyselyt.tielupa :as tielupa])
  (:import (java.util Base64)))

(defn dekoodaa-base64 [data]
  (.decode (Base64/getDecoder) data))

(defn enkoodaa-base64 [data]
  (.encode (Base64/getEncoder) data))

(defn- luo-liitteet [db liitteiden-hallinta urakan-id kirjaaja liitteet liite-luotu-fn]
  (doseq [liitteen-data liitteet]
    (when (:sisalto (:liite liitteen-data))
      (let [liite (:liite liitteen-data)
            tyyppi (:tyyppi liite)
            tiedostonimi (:nimi liite)
            data (dekoodaa-base64 (:sisalto liite))
            koko (alength data)
            kuvaus (:kuvaus liite)
            liite-id (:id (liitteet/luo-liite liitteiden-hallinta (:id kirjaaja) urakan-id tiedostonimi tyyppi koko data kuvaus "harja-api"))]
        (liite-luotu-fn liite-id)))))

(defn tallenna-liitteet-laatupoikkeamalle [db liitteiden-hallinta urakan-id laatupoikkeama-id kirjaaja liitteet]
  (luo-liitteet db liitteiden-hallinta urakan-id kirjaaja liitteet
                #(laatupoikkeamat/liita-laatupoikkeama<! db laatupoikkeama-id %)))

(defn tallenna-liitteet-tarkastukselle [db liitteiden-hallinta urakan-id tarkastus-id kirjaaja liitteet]
  (luo-liitteet db liitteiden-hallinta urakan-id kirjaaja liitteet
                #(tarkastukset/luo-liite<! db tarkastus-id %)))

(defn tallenna-liitteet-turvallisuuspoikkeamalle [db liitteiden-hallinta urakan-id tp-id kirjaaja liitteet]
  (luo-liitteet db liitteiden-hallinta urakan-id kirjaaja liitteet
                #(turvallisuuspoikkeamat/liita-liite<! db tp-id %)))

(defn tallenna-liitteet-siltatarkastuskohteelle [db liitteiden-hallinta kirjaaja urakan-id siltatarkastus-id tarkastuskohde-id liitteet]
  (luo-liitteet db liitteiden-hallinta urakan-id kirjaaja liitteet
                #(siltatarkastukset/lisaa-liite-siltatarkastuskohteelle<! db siltatarkastus-id tarkastuskohde-id %)))

(defn tallenna-liitteet-tieluvalle [db liitteiden-hallinta urakan-id tielupa-id kirjaaja liitteet]
  (luo-liitteet db liitteiden-hallinta urakan-id kirjaaja liitteet
                #(tielupa/liita-liite-tieluvalle<! db tielupa-id %)))
