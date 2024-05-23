(ns harja.palvelin.komponentit.liitteet
  (:require [clojure.string :as str]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.liitteet :as liitteet-q]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [harja.domain.liite :as t-liitteet]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.komponentit.virustarkistus :as virustarkistus]
            [harja.palvelin.komponentit.tiedostopesula :as tiedostopesula]
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
  (lataa-liite [this liitteen-id optiot])
  (lataa-pikkukuva [this liitteen-id]))

(defn- tallenna-liitteen-data [db alusta s3-url lahde tiedostonimi]
  (cond
    ;; Jos S3 tallennus käytössä
    (and (= :aws alusta) s3-url)
    (let [s3hash (tallenna-s3 s3-url (io/input-stream lahde) tiedostonimi)]
      (if (string? s3hash)
        {:liite_oid nil
         :s3hash s3hash
         :virustarkastettu? false}
        (do
          (log/error "Uuden liitteen tallennus s3 epäonnistui, tallennetaan vain tietokantaan. ")
          (tallenna-liitteen-data db alusta s3-url lahde tiedostonimi))))

    ;; Muuten tallennetaan paikalliseen tietokantaan
    :else {:liite_oid (tallenna-lob db (io/input-stream lahde))
           :s3hash nil
           :virustarkastettu? true}))

(defn- kahdenna-stream [alkuperainen]
  (let [temp-file (java.io.File/createTempFile "harja-liite-tmp" ".bin")]
    (.deleteOnExit temp-file)
    (try
      (io/copy alkuperainen temp-file)
      [(io/input-stream temp-file) (io/input-stream temp-file)])))

(defn- tallenna-liite-tietokantaan [db lahdetiedosto alusta s3-url liite-perustiedot]
  (let [pikkukuva (muodosta-pikkukuva (io/input-stream lahdetiedosto))
        data (tallenna-liitteen-data db alusta s3-url lahdetiedosto (:tiedostonimi liite-perustiedot))
        liite (when (or (some? (:liite_oid data)) (some? (:s3hash data)))
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
        (log/info "Liite tallennettu vain tietokantaan.")
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
        (let [liite (tallenna-liite-tietokantaan db lahdetiedosto :aws s3-url liite-perustiedot)
              ;; S3 tallennuksessa käynnistetään virustarkastus
              _ (when (:s3hash liite) (async/thread (odota-s3-virustarkistus db s3-url (:s3hash liite))))]
          liite))
      (do
        (log/debug "Liite hylätty: " (:viesti liitetarkistus))
        (throw+ {:type virheet/+virheellinen-liite+ :virheet
                 [{:koodi  virheet/+virheellinen-liite-koodi+
                   :viesti (str "Virheellinen liite: " (:viesti liitetarkistus))}]})))))

(defn- tallenna-liite [db lahdetiedosto tiedostopesula virustarkistus {:keys [tiedostonimi tyyppi koko] :as liite-perustiedot}]
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
        (tallenna-liite-tietokantaan db lahdetiedosto nil nil liite-perustiedot))
      (do
        (log/debug "Liite hylätty: " (:viesti liitetarkistus))
        (throw+ {:type virheet/+virheellinen-liite+ :virheet
                 [{:koodi  virheet/+virheellinen-liite-koodi+
                   :viesti (str "Virheellinen liite: " (:viesti liitetarkistus))}]})))))

(defn- hae-liite
  "Optiot sisältää seuraavat avaimet:
  siltatarkastusliite? Boolean, joka on true jos halutaan hakea siltatarkastuksia. Näihin vaaditaan erikoiskäsittely oikeuksien takia."
  [db alusta s3-url liitteen-id {:keys [siltatarkastusliite?] :as _optiot}]
  (let [{:keys [s3hash urakat] :as liite}
        (if siltatarkastusliite?
          ;; Siltatarkastukset eroaa muista liitteistä oikeuksien suhteen. Niille tarvitaan urakan sijaan lista urakoista, joiden perusteella päätellään, saako käyttäjä ladata liitettä.
          ;; Mikäli tulevaisuudessa on tarve muiden poikkeusten käsittelyyn, siirretään poikkeusten käsittely tämän komponentin ulkopuolelle.
          ;; Yhden poikkeuksen käsittelyn vuoksi refaktorointi todettiin ylilyönniksi, mutta jos poikkeuksia tulee lisää, koodin selkeys vähenee.
          (first (liitteet-q/hae-siltatarkastusliite-lataukseen db liitteen-id))
          (first (liitteet-q/hae-liite-lataukseen db liitteen-id)))
        liite (assoc liite :urakat (konversio/pgarray->vector urakat))]
    (dissoc
      (cond
        ;; Jos S3 tallennus käytössä
        (= :aws alusta)
        (assoc liite :data (lue-s3-tiedosto s3-url (str s3hash) db))
        :else (assoc liite :data (lue-lob db (:liite_oid liite))))
      :liite_oid)))

(defn- hae-pikkukuva [db liitteen-id]
  (first (liitteet-q/hae-pikkukuva-lataukseen db liitteen-id)))

(defrecord Liitteet [s3-url alusta]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
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
        (tallenna-liite db lahde tiedostopesula virustarkistus liite-perustiedot))))
  (lataa-liite [{db :db :as this} liitteen-id optiot]
    (hae-liite db (:alusta this) s3-url liitteen-id optiot))
  (lataa-pikkukuva [{db :db} liitteen-id]
    (hae-pikkukuva db liitteen-id)))
