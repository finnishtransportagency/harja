(ns harja.palvelin.komponentit.liitteet
  (:require [harja.kyselyt.liitteet :as liitteet]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [harja.domain.liite :as t-liitteet]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.komponentit.virustarkistus :as virustarkistus]
            [harja.palvelin.komponentit.tiedostopesula :as tiedostopesula]
            [fileyard.client :as fileyard-client]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [clojure.java.jdbc :as jdbc])
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

(defn- poista-lob [db oid]
  (with-open [c (.getConnection (:datasource db))]
    (.unlink (large-object-api c) oid)))

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

(defn- tallenna-liitteen-data [db fileyard-client lahde]
  (if (and (ominaisuus-kaytossa? :fileyard) fileyard-client)
    ;; Jos fileyard tallennus on käytössä, tallennetaan ulkoiseen palveluun
    (let [hash @(fileyard-client/save fileyard-client lahde)]
      (if (string? hash)
        {:liite_oid nil :fileyard-hash hash}
        (do
          (log/error "Uuden liitteen tallennus fileyard epäonnistui, tallennetaan tietokantaan. "
                     hash)
          (tallenna-liitteen-data db nil lahde))))

    ;; Muuten tallennetaan paikalliseen tietokantaan
    {:liite_oid (tallenna-lob db (io/input-stream lahde)) :fileyard-hash nil}))


(defn- kahdenna-stream [alkuperainen]
  (let [temp-file (java.io.File/createTempFile "harja-liite-tmp" ".bin")]
    (.deleteOnExit temp-file)
    (try
      (io/copy alkuperainen temp-file)
      [(io/input-stream temp-file) (io/input-stream temp-file)])))

(defn- tallenna-liite [db fileyard-client tiedostopesula virustarkistus luoja urakka tiedostonimi tyyppi uskoteltu-koko lahdetiedosto kuvaus lahde-jarjestelma]
  (log/debug "Vastaanotettu pyyntö tallentaa liite kantaan.")
  (log/debug "Tyyppi: " (pr-str tyyppi))

  (log/debug "Koko väitetty / havaittu: " (pr-str uskoteltu-koko) (and (instance? java.io.File lahdetiedosto) (.length lahdetiedosto)))
  (log/debug "lahde:" lahdetiedosto)
  (let [koko (if (instance? java.io.File lahdetiedosto)
               (.length lahdetiedosto)
               uskoteltu-koko)
        liitetarkistus (t-liitteet/tarkista-liite {:tyyppi tyyppi :koko koko})
        pesty-lahdetiedosto (when (and (ominaisuus-kaytossa? :tiedostopesula) tiedostopesula (= tyyppi "application/pdf"))
                              (do (log/info "PDF-tiedosto -> tiedostopesula")
                                  (tiedostopesula/pdfa-muunna-file->file! tiedostopesula lahdetiedosto)))
        tallennettava-lahdetiedosto (or pesty-lahdetiedosto lahdetiedosto)
        tallennettava-koko (if pesty-lahdetiedosto
                             (.length tallennettava-lahdetiedosto)
                             koko)]
    (if pesty-lahdetiedosto
      (log/info "Tallennetaan tiedostopesulassa käsitelty liitetiedosto")
      (log/info "Tallennetaan alkuperäinen liitetiedosto"))
    (if (:hyvaksytty liitetarkistus)
      (do
        (virustarkistus/tarkista virustarkistus tiedostonimi (io/input-stream lahdetiedosto))
        (let [pikkukuva (muodosta-pikkukuva (io/input-stream tallennettava-lahdetiedosto))
              liite (liitteet/tallenna-liite<!
                     db
                     (merge {:nimi tiedostonimi
                             :tyyppi tyyppi
                             :koko tallennettava-koko
                             :pikkukuva pikkukuva
                             :luoja luoja
                             :urakka urakka
                             :kuvaus kuvaus
                             :lahdejarjestelma lahde-jarjestelma}
                            (tallenna-liitteen-data db fileyard-client tallennettava-lahdetiedosto)))]
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

(defn- siirra-liite-fileyard [db client {:keys [id nimi liite_oid]}]
  (try
    (let [result @(fileyard-client/save client (lue-lob db liite_oid))]
      (if (not (string? result))
        (log/error "Virhe siirrettäessä liitettä " nimi "(id: " id ") fileyardiin: " result)
        (do
          (jdbc/with-db-transaction [db db]
            (poista-lob db liite_oid)
            (liitteet/merkitse-liite-siirretyksi! db {:id id :fileyard-hash result}))
          (log/info "Siirretty liite: " nimi " (id: " id ")"))))
    (catch Exception e
      (log/error e "Poikkeus siirrettäessä liitettä fileyardiin " nimi " (id: " id ")"))))

(defn- siirra-liitteet-fileyard [db fileyard-url]
  (when (and (ominaisuus-kaytossa? :fileyard)
             fileyard-url)
    (lukot/aja-lukon-kanssa
     db "fileyard-liitesiirto"
     #(let [client (fileyard-client/new-client fileyard-url)]
        (doseq [liite (liitteet/hae-siirrettavat-liitteet db)]
          (siirra-liite-fileyard db client liite))))))

(defrecord Liitteet [fileyard-url]
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this ::lopeta-ajastettu-tehtava
                (ajastettu-tehtava/ajasta-minuutin-valein
                  5 11                                      ;; 5 min välein alkaen 11s käynnistyksestä
                  (fn [_]
                    (siirra-liitteet-fileyard db fileyard-url)))))
  (stop [this]
    ((get this ::lopeta-ajastettu-tehtava))
    this)

  LiitteidenHallinta
  (luo-liite [{db :db virustarkistus :virustarkistus tiedostopesula :tiedostopesula}
              luoja urakka tiedostonimi tyyppi koko lahde kuvaus lahdejarjestelma]
    (tallenna-liite db
                    (when fileyard-url
                      (fileyard-client/new-client fileyard-url))
                    tiedostopesula virustarkistus luoja urakka tiedostonimi tyyppi koko lahde kuvaus lahdejarjestelma))
  (lataa-liite [{db :db} liitteen-id]
    (hae-liite db (fileyard-client/new-client fileyard-url) liitteen-id))
  (lataa-pikkukuva [{db :db} liitteen-id]
    (hae-pikkukuva db liitteen-id)))
