(ns harja.palvelin.komponentit.liitteet
  (:require [clojure.string :as str]
            [harja.kyselyt.liitteet :as liitteet-q]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [harja.domain.liite :as t-liitteet]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.komponentit.virustarkistus :as virustarkistus]
            [harja.palvelin.komponentit.tiedostopesula :as tiedostopesula]
            [fileyard.client :as fileyard-client]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [clojure.java.jdbc :as jdbc]
            [org.httpkit.client :as http]
            [clojure.core.async :as async])
  (:import (java.io InputStream ByteArrayOutputStream)
           (org.postgresql.largeobject LargeObjectManager)
           (com.mchange.v2.c3p0 C3P0ProxyConnection)
           (net.coobird.thumbnailator Thumbnailator)
           (net.coobird.thumbnailator.tasks UnsupportedFormatException)
           (java.util UUID))
  (:use [slingshot.slingshot :only [try+ throw+]]))

(declare lue-s3-tiedosto)

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

(def s3-virustarkistusvastaus (atom nil))
(def s3-virustarkistus-maara (atom 0))
(def virustarkistus-max-maara 8)

(defn- odota-s3-virustarkistus [db s3-url s3hash]
  (async/go-loop []
    (async/<! (async/timeout 15000))
    (let [_ (reset! s3-virustarkistusvastaus (lue-s3-tiedosto s3-url s3hash db))
          _ (swap! s3-virustarkistus-maara inc)
          _ (log/info "odota-s3-virustarkistus :: tulos:" (pr-str @s3-virustarkistusvastaus))]
      (cond
        (and (not (nil? @s3-virustarkistusvastaus)) (< @s3-virustarkistus-maara virustarkistus-max-maara) )
        (do
          (log/info "Liite on virustarkastettu.")
          ;; Merkitään se tarkastetuksi
          (liitteet-q/merkitse-liite-virustarkistetuksi! db {:s3hash s3hash}))
        (and (nil? @s3-virustarkistusvastaus) (< @s3-virustarkistus-maara virustarkistus-max-maara) )
        (do
          (log/info "Tiedosto tarkastamatta, odotetaan 15 sekuntia...")
          (recur))
        (and (nil? @s3-virustarkistusvastaus) (>= @s3-virustarkistus-maara virustarkistus-max-maara) )
        (log/error "Virustarkastus epäonnistui. Tarkistuskertojen maksimi ylittyi.")))))

(defn- tallenna-s3
  "Anna lähetettävä tiedosto java.io.inputstreaminä.
  1. Luo ensin urlin POST komennolla, johon liite voidaan lähettää.
  2. Lähettää PUT komennolla liitteen S3 ämpäriin.
  3. S3 tarkistaa onko tiedostossa viruksia ja tagittaa tiedoston"
  [s3-url input-stream-sisalto tiedostonimi]
  (try
    (let [_ (log/info "tallenna-s3 :: tiedostonimi: " tiedostonimi)
          ;; Siirretään tiedostot S3:lle nimettynä uusiksi. Uusi nimi on tallennettu kantaan, jota kautta
          ;; tiedostot saadaan sitten haettua
          s3hash (str/trim (str (pvm/iso8601 (pvm/nyt)) "-" (UUID/randomUUID)))
          ;; Generoi presignedurl, johon varsinainen liite lähetetään
          vastaus @(http/post s3-url {:body (cheshire.core/encode {"key" s3hash "operation" "put"})
                                      :timeout 50000})
          _ (log/info "Generoi presignedurl :: vastaus " vastaus)
          ;; Vastauksesta parsitaan varsinainen url, johon liite lähetetään
          varsinainen-put-url (when (= 200 (:status vastaus))
                                 (str/trim (get (cheshire.core/decode (:body vastaus)) "url")))
          _ (log/info "Lähetetään tiedosto urliin: " varsinainen-put-url)
          liite-vastaus (when varsinainen-put-url
                          @(http/put varsinainen-put-url {:body input-stream-sisalto}))
          _ (if varsinainen-put-url
              (log/info "Liitteen tallennuksen vastaus: " liite-vastaus)
              (log/error "Ei saatu yhteyttä S3:seen. Liitetiedosto jää lähettämättä "))]

      ;; Jos lataus osoitetta ei saatu, palautetaan nil
      (when varsinainen-put-url
        s3hash))
    (catch Exception e
      (do
        (log/error "Liitetiedoston tallennus S3:Selle epäonnistui: " e)
        ;; Palautetaan nil
        nil))))

(defn lue-s3-tiedosto
  "Anna ladattavan tiedoston s3hash.
  1. Luo ensin urlin POST komennolla, josta liite voidaan ladata.
  2. Hae GET komennolla liite S3 ämpäristä.
  3. S3 palauttaa vain virstarkistetut tiedostot, joissa on tag 'viruscan=clean'
  4. Jos tiedosto saadaan palautettua, merkataan se automaattisesti virustarkistetuksi tietokantaan."
  [s3-url s3hash db]
  (try
    (let [_ (log/info "lue-s3-tiedosto")
          ;; Generoi presignedurl, josta liite haetaan
          vastaus @(http/post s3-url {:body (cheshire.core/encode {"key" (str s3hash) "operation" "get"})
                                      :timeout 50000})
          _ (log/info "lue-s3-tiedosto :: vastaus:" vastaus)
          _ (when (not= 200 (:status vastaus))
              (log/error "File: " s3hash " got download error: " (:body vastaus)))

          ;; Vastauksesta parsitaan varsinainen url, josta liite ladataan
          varsinainen-get-url (when (= 200 (:status vastaus)) (str/trim (get (cheshire.core/decode (:body vastaus)) "url")))
          _ (log/info "lue-s3-tiedosto :: varsinainen-get-url:" varsinainen-get-url)

          liite (when varsinainen-get-url @(http/get varsinainen-get-url))
          _ (log/info "lue-s3-tiedosto :: liite:" liite)

          ;; Jos liite saatiin, merkitään kantaan, että se on virustarkastettu
          _ (when (= 200 (:status liite)) (liitteet-q/merkitse-liite-virustarkistetuksi! db {:s3hash s3hash}))]
      ;; Jos osoitetta ei saatu, palautetaan liitteen sijasta nil
      (when varsinainen-get-url
        (with-open [out (ByteArrayOutputStream.)]
          (io/copy (:body liite) out)
          (.toByteArray out))))
    (catch Exception e
      (do
        (log/error "Liitetiedoston lataus epäonnistui: " e)
        ;; Palautetaan nil
        nil))))

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

(defn- tallenna-liitteen-data [db alusta s3-url fileyard-client lahde tiedostonimi]
  (cond
    ;; Jos fileyard tallennus on käytössä, tallennetaan ulkoiseen palveluun
    (and (ominaisuus-kaytossa? :fileyard) fileyard-client)
    (let [hash @(fileyard-client/save fileyard-client lahde)]
      (if (string? hash)
        {:liite_oid nil
         :s3hash nil
         :fileyard-hash hash
         :virustarkastettu? true}
        (do
          (log/error "Uuden liitteen tallennus fileyard epäonnistui, tallennetaan tietokantaan. "
            {:liite_oid nil
             :s3hash nil
             :fileyard-hash hash
             :virustarkastettu? true})
          (tallenna-liitteen-data db alusta nil nil lahde tiedostonimi))))

    ;; Jos S3 tallennus käytössä
    (and (= :aws alusta) s3-url)
    (let [s3hash (tallenna-s3 s3-url (io/input-stream lahde) tiedostonimi)]
      (if (string? s3hash)
        {:liite_oid nil
         :s3hash s3hash
         :fileyard-hash nil
         :virustarkastettu? false}
        (do
          (log/error "Uuden liitteen tallennus s3 epäonnistui, tallennetaan vain tietokantaan. ")
          (tallenna-liitteen-data db alusta s3-url nil lahde tiedostonimi))))

    ;; Muuten tallennetaan paikalliseen tietokantaan
    :else {:liite_oid (tallenna-lob db (io/input-stream lahde))
           :fileyard-hash nil
           :s3hash nil
           :virustarkastettu? true}))

(defn- kahdenna-stream [alkuperainen]
  (let [temp-file (java.io.File/createTempFile "harja-liite-tmp" ".bin")]
    (.deleteOnExit temp-file)
    (try
      (io/copy alkuperainen temp-file)
      [(io/input-stream temp-file) (io/input-stream temp-file)])))

(defn- tallenna-liite-tietokantaan [db lahdetiedosto alusta s3-url fileyard-client liite-perustiedot]
  (let [pikkukuva (muodosta-pikkukuva (io/input-stream lahdetiedosto))
        data (tallenna-liitteen-data db alusta s3-url fileyard-client lahdetiedosto (:tiedostonimi liite-perustiedot))
        liite (when (or (some? (:liite_oid data)) (some? (:s3hash data)) (some? (:fileyard-hash data)))
                (liitteet-q/tallenna-liite<!
                  db
                  (merge {:nimi (:tiedostonimi liite-perustiedot)
                          :tyyppi (:tyyppi liite-perustiedot)
                          :koko (:koko liite-perustiedot)
                          :pikkukuva pikkukuva
                          :luoja (:luoja liite-perustiedot)
                          :urakka (:urakka liite-perustiedot)
                          :kuvaus (:kuvaus liite-perustiedot)
                          :lahdejarjestelma (:lahdejarjestelma liite-perustiedot)}
                    data)))]
    (if liite
      (do
        (log/info "Liite tallennettu.")
        liite)
      (throw+ {:type virheet/+ominaisuus-ei-kaytossa+ :virheet
               [{:koodi virheet/+ominaisuus-ei-kaytossa+
                 :viesti (str "Liitteen tallennus ei tällä hetkellä onnistu. Kokeile myöhemmin uudestaan.")}]}))))

(defn- tallenna-liite-s3 [db lahdetiedosto s3-url {:keys [tyyppi koko] :as liite-perustiedot}]
  (log/debug "Vastaanotettu pyyntö tallentaa liite s3:seen.")
  (log/debug "Tyyppi: " (pr-str tyyppi))
  (log/debug "Koko väitetty / havaittu: " (pr-str koko) (and (instance? java.io.File lahdetiedosto) (.length lahdetiedosto)))
  (log/debug "lahde:" lahdetiedosto)
  ;; Resetoidaan atomit jokaisen latauksen yhteydessä
  (reset! s3-virustarkistusvastaus nil)
  (reset! s3-virustarkistus-maara 0)
  (let [koko (if (instance? java.io.File lahdetiedosto)
               (.length lahdetiedosto)
               koko)
        liitetarkistus (t-liitteet/tarkista-liite {:tyyppi tyyppi :koko koko})]

    (if (:hyvaksytty liitetarkistus)
      (do
        (let [liite (tallenna-liite-tietokantaan db lahdetiedosto :aws s3-url nil liite-perustiedot)
              ;; S3 tallennuksessa käynnistetään virustarkastus
              _ (when (:s3hash liite) (async/thread (odota-s3-virustarkistus db s3-url (:s3hash liite))))]
          liite))
      (do
        (log/debug "Liite hylätty: " (:viesti liitetarkistus))
        (throw+ {:type virheet/+virheellinen-liite+ :virheet
                 [{:koodi  virheet/+virheellinen-liite-koodi+
                   :viesti (str "Virheellinen liite: " (:viesti liitetarkistus))}]})))))

(defn- tallenna-liite [db lahdetiedosto fileyard-client tiedostopesula virustarkistus {:keys [tiedostonimi tyyppi koko] :as liite-perustiedot}]
  (log/debug "Vastaanotettu pyyntö tallentaa liite kantaan.")
  (log/debug "Tyyppi: " (pr-str tyyppi))

  (log/debug "Koko väitetty / havaittu: " (pr-str koko) (and (instance? java.io.File lahdetiedosto) (.length lahdetiedosto)))
  (log/debug "lahde:" lahdetiedosto)
  (let [koko (if (instance? java.io.File lahdetiedosto)
               (.length lahdetiedosto)
               koko)
        liitetarkistus (t-liitteet/tarkista-liite {:tyyppi tyyppi :koko koko})
        pesty-lahdetiedosto (when (and
                                    (ominaisuus-kaytossa? :tiedostopesula) tiedostopesula (= tyyppi "application/pdf"))
                              (do (log/info "PDF-tiedosto -> tiedostopesula")
                                (tiedostopesula/pdfa-muunna-file->file! tiedostopesula lahdetiedosto)))
        tallennettava-lahdetiedosto (or pesty-lahdetiedosto lahdetiedosto)
        tallennettava-koko (if pesty-lahdetiedosto
                             (.length tallennettava-lahdetiedosto)
                             koko)
        liite-perustiedot (assoc liite-perustiedot :koko tallennettava-koko)]
    (if pesty-lahdetiedosto
      (log/info "Tallennetaan tiedostopesulassa käsitelty liitetiedosto")
      (log/info "Tallennetaan alkuperäinen liitetiedosto"))
    (if (:hyvaksytty liitetarkistus)
      (do
        (virustarkistus/tarkista virustarkistus tiedostonimi (io/input-stream lahdetiedosto))
        (tallenna-liite-tietokantaan db lahdetiedosto nil nil fileyard-client liite-perustiedot))
      (do
        (log/debug "Liite hylätty: " (:viesti liitetarkistus))
        (throw+ {:type virheet/+virheellinen-liite+ :virheet
                 [{:koodi  virheet/+virheellinen-liite-koodi+
                   :viesti (str "Virheellinen liite: " (:viesti liitetarkistus))}]})))))

(defn- hae-liite [db alusta s3-url fileyard-client liitteen-id]
  (let [_ (log/info "hae-liite :: alusta: " alusta)
        _ (log/info "hae-liite :: s3-url: " s3-url)
        _ (log/info "hae-liite :: fileyard-client: " fileyard-client)
        {:keys [fileyard-hash s3hash] :as liite}
        (first (liitteet-q/hae-liite-lataukseen db liitteen-id))
        _ (log/info "hae-liite :: ehto: " (and fileyard-client fileyard-hash))]

    (dissoc
      (cond
        ;; Jos fileyard käytössä
        (and (ominaisuus-kaytossa? :fileyard) fileyard-client fileyard-hash) (assoc liite :data (lue-fileyard-tiedosto fileyard-client fileyard-hash))
        ;; Jos S3 tallennus käytössä
        (= :aws alusta)
        (assoc liite :data (lue-s3-tiedosto s3-url (str s3hash) db))
        :else (assoc liite :data (lue-lob db (:liite_oid liite))))
      :liite_oid :fileyard-hash)))

(defn- hae-pikkukuva [db liitteen-id]
  (first (liitteet-q/hae-pikkukuva-lataukseen db liitteen-id)))

(defn- siirra-liite-fileyard [db client {:keys [id nimi liite_oid]}]
  (try
    (let [result @(fileyard-client/save client (lue-lob db liite_oid))]
      (if (not (string? result))
        (log/error "Virhe siirrettäessä liitettä " nimi "(id: " id ") fileyardiin: " result)
        (do
          (jdbc/with-db-transaction [db db]
            (poista-lob db liite_oid)
            (liitteet-q/merkitse-liite-siirretyksi! db {:id id :fileyard-hash result}))
          (log/info "Siirretty liite: " nimi " (id: " id ")"))))
    (catch Exception e
      (log/error e "Poikkeus siirrettäessä liitettä fileyardiin " nimi " (id: " id ")"))))

(def liitteiden-ajastusvali-min 5)
(def lukon-vanhenemisaika-s (* (dec liitteiden-ajastusvali-min) 60))

(defn- siirra-liitteet-fileyard [db fileyard-url]
  (when (and (ominaisuus-kaytossa? :fileyard)
             fileyard-url)
    (lukot/yrita-ajaa-lukon-kanssa
      db "fileyard-liitesiirto"
      #(let [client (fileyard-client/new-client fileyard-url)]
        (doseq [liite (liitteet-q/hae-siirrettavat-liitteet db)]
          (siirra-liite-fileyard db client liite)))
      lukon-vanhenemisaika-s)))

(defrecord Liitteet [fileyard-url s3-url alusta]
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this ::lopeta-ajastettu-tehtava
                (ajastettu-tehtava/ajasta-minuutin-valein
                  liitteiden-ajastusvali-min 11                                      ;; 5 min välein alkaen 11s käynnistyksestä
                  (fn [_]
                    (siirra-liitteet-fileyard db fileyard-url)))))
  (stop [this]
    ((get this ::lopeta-ajastettu-tehtava))
    this)

  LiitteidenHallinta
  (luo-liite [{db :db virustarkistus :virustarkistus tiedostopesula :tiedostopesula}
              luoja urakka tiedostonimi tyyppi koko lahde kuvaus lahdejarjestelma]
    (let [liite-perustiedot {:luoja luoja
                             :urakka urakka
                             :tiedostonimi tiedostonimi
                             :tyyppi tyyppi
                             :koko koko
                             :kuvaus kuvaus
                             :lahdejarjestelma lahdejarjestelma}]
      ; Tallennetaan liitteet s3:een vähemmillä vaiheilla
      (if (and (= :aws alusta) s3-url)
        (tallenna-liite-s3 db lahde s3-url liite-perustiedot)
        (tallenna-liite db lahde
          (when fileyard-url
            (fileyard-client/new-client fileyard-url))
          tiedostopesula virustarkistus liite-perustiedot))))
  (lataa-liite [{db :db :as this} liitteen-id]
    (hae-liite db (:alusta this) s3-url (fileyard-client/new-client fileyard-url) liitteen-id))
  (lataa-pikkukuva [{db :db} liitteen-id]
    (hae-pikkukuva db liitteen-id)))
