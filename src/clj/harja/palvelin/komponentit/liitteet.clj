(ns harja.palvelin.komponentit.liitteet
  (:require [harja.kyselyt.liitteet :as liitteet]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [harja.domain.liitteet :as t-liitteet]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.komponentit.virustarkistus :as virustarkistus]
            [fileyard.client :as fileyard-client])
  (:import (java.io InputStream ByteArrayOutputStream)
           (org.postgresql.largeobject LargeObjectManager)
           (com.mchange.v2.c3p0 C3P0ProxyConnection)
           (net.coobird.thumbnailator Thumbnailator)
           (net.coobird.thumbnailator.tasks UnsupportedFormatException))
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def get-large-object-api (->> (Class/forName "org.postgresql.PGConnection")
                               .getMethods
                               (filter #(= (.getName %) "getLargeObjectAPI"))
                               first))

(defn- large-object-api [c]
  (.rawConnectionOperation c
                           get-large-object-api
                           C3P0ProxyConnection/RAW_CONNECTION
                           (into-array Object [])))

(defn- lue-lob [db oid]
  (with-open [c (doto (.getConnection (:datasource db))
                  (.setAutoCommit false))]
    (let [lom (large-object-api c)]
      (with-open [obj (.open lom oid LargeObjectManager/READ)
                  in (.getInputStream obj)
                  out (ByteArrayOutputStream.)]
        (io/copy in out)
        (.toByteArray out)))))

(defn- lue-fileyard-tiedosto [client uuid]
  (with-open [out (ByteArrayOutputStream.)]
    (io/copy (fileyard-client/fetch client uuid) out)
    (.toByteArray out)))

(defn- tallenna-lob [db ^InputStream in]
  (with-open [c (doto (.getConnection (:datasource db))
                  (.setAutoCommit false))]
    (let [lom (large-object-api c)
          oid (.create lom LargeObjectManager/READWRITE)]
      (try
        (with-open [obj (.open lom oid LargeObjectManager/WRITE)
                    out (.getOutputStream obj)]
          (io/copy in out)
          oid)
        (finally
          (.commit c))))))

(defn- muodosta-pikkukuva
  "Ottaa ison kuvan input streamin ja palauttaa pikkukuvan byte[] muodossa. Pikkukuva on aina png.
  Jos thumbnalaitor ei pysty tekemään pikkukuvaa palautetaan nil."
  [isokuva]
  (try (with-open [out (ByteArrayOutputStream.)]
         (Thumbnailator/createThumbnail isokuva out "png" 64 64)
         (.toByteArray out))
       (catch UnsupportedFormatException _ nil)))

(defprotocol LiitteidenHallinta
  (luo-liite [this luoja urakka tiedostonimi tyyppi koko lahde kuvaus lahdejarjestelma])
  (lataa-liite [this liitteen-id])
  (lataa-pikkukuva [this liitteen-id]))

(defn- tallenna-liite [db fileyard-client virustarkistus luoja urakka tiedostonimi tyyppi koko lahde kuvaus lahde-jarjestelma]
  (log/debug "Vastaanotettu pyyntö tallentaa liite kantaan.")
  (log/debug "Tyyppi: " (pr-str tyyppi))
  (log/debug "Koko: " (pr-str koko))
  (let [liitetarkistus (t-liitteet/tarkista-liite {:tyyppi tyyppi :koko koko})]
    (if (:hyvaksytty liitetarkistus)
      (do
        (virustarkistus/tarkista virustarkistus tiedostonimi (io/input-stream lahde))
        (let [pikkukuva (muodosta-pikkukuva (io/input-stream lahde))
              uuid @(fileyard-client/save fileyard-client lahde)
              liite (liitteet/tallenna-liite<! db tiedostonimi tyyppi koko uuid pikkukuva luoja urakka kuvaus lahde-jarjestelma)]
            (log/debug "Liite tallennettu.")
            liite))
      (do
        (log/debug "Liite hylätty: " (:viesti liitetarkistus))
        (throw+ {:type virheet/+virheellinen-liite+ :virheet
                 [{:koodi  virheet/+virheellinen-liite-koodi+
                   :viesti (str "Virheellinen liite: " (:viesti liitetarkistus))}]})))))

(defn- hae-liite [db fileyard-client liitteen-id]
  (let [{:keys [fileyard-hash] :as liite}
        (first (liitteet/hae-liite-lataukseen db liitteen-id))]

    (dissoc
     (if fileyard-hash
       (assoc liite :data (lue-fileyard-tiedosto fileyard-client fileyard-hash))
       (assoc liite :data (lue-lob db (:liite_oid liite))))
     :liite_oid :fileyard-hash)))

(defn- hae-pikkukuva [db liitteen-id]
  (first (liitteet/hae-pikkukuva-lataukseen db liitteen-id)))

(defrecord Liitteet [fileyard-url]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this)

  LiitteidenHallinta
  (luo-liite [{db :db virustarkistus :virustarkistus}
              luoja urakka tiedostonimi tyyppi koko lahde kuvaus lahdejarjestelma]
    (tallenna-liite db
                    (fileyard-client/new-client fileyard-url)
                    virustarkistus luoja urakka tiedostonimi tyyppi koko lahde kuvaus lahdejarjestelma))
  (lataa-liite [{db :db} liitteen-id]
    (hae-liite db (fileyard-client/new-client fileyard-url) liitteen-id))
  (lataa-pikkukuva [{db :db} liitteen-id]
    (hae-pikkukuva db liitteen-id)))
