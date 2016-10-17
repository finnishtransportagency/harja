(ns harja-laadunseuranta.core
  (:require [taoensso.timbre :as log]
            [compojure.core :refer [GET]]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [ring.util.response :refer [redirect]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [harja-laadunseuranta.tietokanta :as tietokanta]
            [harja-laadunseuranta.kyselyt :as q]
            [harja-laadunseuranta.tarkastukset :as tarkastukset]
            [harja-laadunseuranta.schemas :as schemas]
            [harja-laadunseuranta.utils :as utils]
            [schema.core :as s]
            [clojure.core.match :refer [match]]
            [clojure.java.jdbc :as jdbc]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [clojure.data.codec.base64 :as b64]
            [com.stuartsierra.component :as component]
            [clojure.walk :as walk]
            [harja.domain.oikeudet :as oikeudet])
  (:import (org.postgis PGgeometry))
  (:gen-class))

(defn- tallenna-merkinta! [tx vakiohavainto-idt merkinta]
  (q/tallenna-reittimerkinta! tx {:id            (:id merkinta)
                                  :tarkastusajo  (:tarkastusajo merkinta)
                                  :aikaleima     (:aikaleima merkinta)
                                  :x             (:lon (:sijainti merkinta))
                                  :y             (:lat (:sijainti merkinta))
                                  :lampotila     (get-in merkinta [:mittaukset :lampotila])
                                  :lumisuus      (get-in merkinta [:mittaukset :lumisuus])
                                  :tasaisuus     (get-in merkinta [:mittaukset :tasaisuus])
                                  :kitkamittaus  (get-in merkinta [:mittaukset :kitkamittaus])
                                  :kiinteys      (get-in merkinta [:mittaukset :kiinteys])
                                  :polyavyys     (get-in merkinta [:mittaukset :polyavyys])
                                  :sivukaltevuus (get-in merkinta [:mittaukset :sivukaltevuus])
                                  :havainnot     (mapv vakiohavainto-idt (:havainnot merkinta))
                                  :kuvaus        (get-in merkinta [:kuvaus])
                                  :laadunalitus  (get-in merkinta [:laadunalitus])
                                  :kuva          (get-in merkinta [:kuva])}))

(defn- tallenna-multipart-kuva! [db {:keys [tempfile content-type size]} kayttaja-id]
  (let [oid (tietokanta/tallenna-lob db (io/input-stream tempfile))]
    (:id (q/tallenna-kuva<! db {:lahde "harja-ls-mobiili"
                                :tyyppi content-type
                                :koko size
                                :pikkukuva (tietokanta/tee-thumbnail tempfile)
                                :oid oid
                                :luoja kayttaja-id}))))

(defn- tallenna-merkinnat! [db kirjaukset kayttaja-id]
  (jdbc/with-db-transaction [tx db]
    (let [vakiohavainto-idt (q/hae-vakiohavaintoavaimet tx)]
      (doseq [merkinta (:kirjaukset kirjaukset)]
        (tallenna-merkinta! tx vakiohavainto-idt merkinta)))))

(defn merkitse-ajo-paattyneeksi! [tx tarkastusajo-id kayttaja]
  (q/paata-tarkastusajo! tx {:id tarkastusajo-id
                             :kayttaja (:id kayttaja)}))

(defn- paata-tarkastusajo! [db tarkastusajo kayttaja]
  (jdbc/with-db-transaction [tx db]
    (let [tarkastusajo-id (-> tarkastusajo :tarkastusajo :id)
          urakka-id (or
                     (:urakka tarkastusajo)
                     (:id (first (q/paattele-urakka tx {:tarkastusajo tarkastusajo-id}))))
          merkinnat (q/hae-reitin-merkinnat tx {:tarkastusajo tarkastusajo-id
                                                :treshold 100})
          merkinnat-tr-osoitteilla (tarkastukset/lisaa-reittimerkinnoille-tieosoite merkinnat)
          tarkastukset (-> (tarkastukset/reittimerkinnat-tarkastuksiksi merkinnat-tr-osoitteilla)
                           (tarkastukset/lisaa-tarkastuksille-urakka-id urakka-id))]
      (log/debug "Tallennetaan tarkastus urakkaan " urakka-id)
      (tarkastukset/tallenna-tarkastukset! tx tarkastukset kayttaja)
      (merkitse-ajo-paattyneeksi! tx tarkastusajo-id kayttaja))))

(defn- tarkastustyypiksi [tyyppi]
  (condp = tyyppi
    :kelitarkastus 1
    :soratietarkastus 2
    :paallystys 3
    :tiemerkinta 4
    0))

(defn- luo-uusi-tarkastusajo! [db tiedot kayttaja]
  (q/luo-uusi-tarkastusajo<! db {:ulkoinen_id 0
                                 :kayttaja (:id kayttaja)
                                 :tyyppi (tarkastustyypiksi (-> tiedot :tyyppi))}))

(defn- hae-tr-osoite [db lat lon treshold]
  (try
    (first (q/hae-tr-osoite db {:y lat
                                :x lon
                                :treshold treshold}))
    (catch Exception e
      nil)))

(defn- hae-tr-tiedot [db lat lon treshold]
  (let [pos {:y lat
             :x lon
             :treshold treshold}
        talvihoitoluokka (q/hae-pisteen-hoitoluokka db (assoc pos :tietolaji "talvihoito"))
        soratiehoitoluokka (q/hae-pisteen-hoitoluokka db (assoc pos :tietolaji "soratie"))]
    {:talvihoitoluokka (:hoitoluokka_pisteelle (first talvihoitoluokka))
     :soratiehoitoluokka (:hoitoluokka_pisteelle (first soratiehoitoluokka))
     :tr-osoite (hae-tr-osoite db lat lon treshold)}))

(defn- hae-urakkatyypin-urakat [db urakkatyyppi kayttaja]
  (let [urakat (map
                 #(assoc % :urakkaroolissa? (if ((set
                                                   (keys (:urakkaroolit kayttaja)))
                                                  (:id %))
                                              true
                                              false))
                 (q/hae-urakkatyypin-urakat db
                                            {:tyyppi urakkatyyppi}
                                            ))]
    urakat))

(defn- muunna-havainnot [{kirjaukset :kirjaukset :as tiedot}]
  (if kirjaukset
    (assoc tiedot :kirjaukset
           (mapv (fn [kirjaus]
                   (if-let [havainnot (:havainnot kirjaus)]
                     (assoc kirjaus :havainnot (mapv keyword havainnot))
                     kirjaus)) kirjaukset))
    tiedot))

(defn kasittele-api-kutsu [skeema-sisaan ok-skeema kasittelija]
  (let [skeema-ulos (schemas/api-vastaus ok-skeema)]
    (fn [user tiedot]
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/laadunseuranta-kirjaus user)
      (let [tiedot (->> tiedot
                        walk/keywordize-keys
                        muunna-havainnot
                        (s/validate skeema-sisaan))
            tulos (try {:ok (kasittelija user tiedot)}
                       (catch Throwable t
                         (log/warn t "Virhe LS API-kutsussa, tiedot: " (pr-str tiedot))
                         {:error (.getMessage t)}))]
        (->> tulos
             (s/validate skeema-ulos))))))

(defn- laadunseuranta-api [db http]
  (http-palvelin/julkaise-palvelut
   http

   :ls-reittimerkinta
   (kasittele-api-kutsu
    schemas/Havaintokirjaukset s/Str
    (fn [user kirjaukset]
      (tallenna-merkinnat! db kirjaukset (:id user))
      "Reittimerkinta tallennettu"))


   :ls-paata-tarkastusajo
   (kasittele-api-kutsu
    schemas/TarkastuksenPaattaminen s/Str
    (fn [user tarkastusajo]
      (log/debug "Päätetään tarkastusajo " tarkastusajo)
      (paata-tarkastusajo! db tarkastusajo user)
      "Tarkastusajo päätetty"))

   :ls-uusi-tarkastusajo
   (kasittele-api-kutsu
    s/Any s/Any
    (fn [user tiedot]
      (log/debug "Luodaan uusi tarkastusajo " tiedot)
      (luo-uusi-tarkastusajo! db tiedot user)))

   :ls-hae-tr-tiedot
   (kasittele-api-kutsu
    s/Any s/Any
    (fn [user koordinaatit]
      (log/debug "Haetaan tierekisteritietoja pisteelle " koordinaatit)
      (let [{:keys [lat lon treshold]} koordinaatit]
        (hae-tr-tiedot db lat lon treshold))))

   :ls-urakkatyypin-urakat
   (kasittele-api-kutsu
    s/Str s/Any
    (fn [kayttaja urakkatyyppi]
      (log/debug "Haetaan urakkatyypin urakat " urakkatyyppi)
      (hae-urakkatyypin-urakat db urakkatyyppi kayttaja)))

   :ls-hae-kayttajatiedot
   (fn [kayttaja]
     (log/debug "Käyttäjän tietojen haku")
     {:ok {:kayttajanimi (:kayttajanimi kayttaja)
           :nimi (str (:etunimi kayttaja) " " (:sukunimi kayttaja))
           :vakiohavaintojen-kuvaukset (q/hae-vakiohavaintojen-kuvaukset db)}})))


(defn- tallenna-liite [db req]
  (let [id (tallenna-multipart-kuva! db (get-in req [:multipart-params "liite"]) (get-in req [:kayttaja :id]))]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body (str id)}))

(defn luo-routet [db http]
  ;; Reitti liitteen tallennukseen
  (http-palvelin/julkaise-palvelu
   http :ls-tallenna-liite
   (wrap-multipart-params
    (fn [req]
      (println "SAATIIN UPLOAD: " req)
      (tallenna-liite db req)))
   {:ring-kasittelija? true})

  ;; Laadunseurannan API kutsut
  (laadunseuranta-api db http))


(defrecord Laadunseuranta [asetukset]
  component/Lifecycle
  (start [{db :db
           http :http-palvelin
           :as this}]
    (tietokanta/aseta-tietokanta! db)
    (log/info "Harja laadunseuranta käynnistyy")
    (luo-routet db http)
    this)

  (stop [{http :http-palvelin :as this}]
    (http-palvelin/poista-palvelut http
                                   :ls-juuri-1 :ls-juuri-2
                                   :ls-tallenna-liite
                                   :ls-reittimerkinta
                                   :ls-paata-tarkastusajo
                                   :ls-uusi-tarkastusajo
                                   :ls-hae-tr-tiedot
                                   :ls-urakkatyypin-urakat
                                   :ls-hae-kayttajatiedot)
    this))
