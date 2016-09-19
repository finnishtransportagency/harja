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
            [harja-laadunseuranta.config :as c]
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

(def db tietokanta/db)

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

(defn- tallenna-kuva! [tx {:keys [data mime-type]} kayttaja-id]
  (let [decoded-data (b64/decode (.getBytes data "UTF-8"))
        oid (tietokanta/tallenna-lob (io/input-stream decoded-data))]
    (:id (q/tallenna-kuva<! tx {:lahde "harja-ls-mobiili"
                                :tyyppi mime-type
                                :koko (count decoded-data)
                                :pikkukuva (tietokanta/tee-thumbnail decoded-data)
                                :oid oid
                                :luoja kayttaja-id}))))

(defn- tallenna-multipart-kuva! [tx {:keys [tempfile content-type size]} kayttaja-id]
  (let [oid (tietokanta/tallenna-lob tx (io/input-stream tempfile))]
    (:id (q/tallenna-kuva<! tx {:lahde "harja-ls-mobiili"
                                :tyyppi content-type
                                :koko size
                                :pikkukuva (tietokanta/tee-thumbnail tempfile)
                                :oid oid
                                :luoja kayttaja-id}))))

(defn- tallenna-merkinnat! [kirjaukset kayttaja-id]
  (jdbc/with-db-transaction [tx @db]
    (let [vakiohavainto-idt (q/hae-vakiohavaintoavaimet tx)]
      (doseq [merkinta (:kirjaukset kirjaukset)]
        (tallenna-merkinta! tx vakiohavainto-idt merkinta)))))

(defn merkitse-ajo-paattyneeksi! [tx tarkastusajo-id kayttaja]
  (q/paata-tarkastusajo! tx {:id tarkastusajo-id
                             :kayttaja (:id kayttaja)}))

(defn- paata-tarkastusajo! [tarkastusajo kayttaja]
  (jdbc/with-db-transaction [tx @db]
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

(defn- luo-uusi-tarkastusajo! [tiedot kayttaja]
  (q/luo-uusi-tarkastusajo<! @db {:ulkoinen_id 0
                                  :kayttaja (:id kayttaja)
                                  :tyyppi (tarkastustyypiksi (-> tiedot :tyyppi))}))

(defn- hae-tr-osoite [lat lon treshold]
  (try
    (first (q/hae-tr-osoite @db {:y lat
                                 :x lon
                                 :treshold treshold}))
    (catch Exception e
      nil)))

(defn- hae-tr-tiedot [lat lon treshold]
  (let [pos {:y lat
             :x lon
             :treshold treshold}
        talvihoitoluokka (q/hae-pisteen-hoitoluokka @db (assoc pos :tietolaji "talvihoito")
                                                    )
        soratiehoitoluokka (q/hae-pisteen-hoitoluokka @db (assoc pos :tietolaji "soratie")
                                                      )]
    {:talvihoitoluokka (:hoitoluokka_pisteelle (first talvihoitoluokka))
     :soratiehoitoluokka (:hoitoluokka_pisteelle (first soratiehoitoluokka))
     :tr-osoite (hae-tr-osoite lat lon treshold)}))

(defn- hae-urakkatyypin-urakat [urakkatyyppi kayttaja]
  (println "hae ur tyyypin urakat, kayttaja " kayttaja)
  (let [urakat (q/hae-urakkatyypin-urakat @db {:tyyppi urakkatyyppi})]
    urakat))

(defn- muunna-havainnot [{kirjaukset :kirjaukset :as tiedot}]
  (println "tiedot: " tiedot)
  (if kirjaukset
    (assoc tiedot :kirjaukset
           (mapv (fn [kirjaus]
                   (if-let [havainnot (:havainnot kirjaus)]
                     (assoc kirjaus :havainnot (mapv keyword havainnot))
                     kirjaus)) kirjaukset))
    tiedot))

(defn kasittele-api-kutsu [skeema-sisaan skeema-ulos kasittelija]
  (fn [user tiedot]
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/laadunseuranta-kirjaus user)
    (let [tiedot (->> tiedot
                      walk/keywordize-keys
                      muunna-havainnot
                      (s/validate skeema-sisaan))]
      (->> {:ok (kasittelija user tiedot)}
           (s/validate skeema-ulos)))))

(defn- laadunseuranta-api [http]
  (http-palvelin/julkaise-palvelut
   http

   :ls-reittimerkinta
   (kasittele-api-kutsu
    schemas/Havaintokirjaukset {:ok s/Str}
    (fn [user kirjaukset]
      (tallenna-merkinnat! (:id user) kirjaukset)
      "Reittimerkinta tallennettu"))


   :ls-paata-tarkastusajo
   (kasittele-api-kutsu
    schemas/TarkastuksenPaattaminen
    {:ok s/Str}
    (fn [user tarkastusajo]
      (log/debug "Päätetään tarkastusajo " tarkastusajo)
      (paata-tarkastusajo! tarkastusajo user)
      "Tarkastusajo päätetty"))

   :ls-uusi-tarkastusajo
   (kasittele-api-kutsu
    s/Any {:ok s/Any}
    (fn [user tiedot]
      (log/debug "Luodaan uusi tarkastusajo " tiedot)
      (luo-uusi-tarkastusajo! tiedot user)))

   :ls-hae-tr-tiedot
   (kasittele-api-kutsu
    s/Any {:ok s/Any}
    (fn [user koordinaatit]
      (log/debug "Haetaan tierekisteritietoja pisteelle " koordinaatit)
      (let [{:keys [lat lon treshold]} koordinaatit]
        (hae-tr-tiedot lat lon treshold))))

   :ls-urakkatyypin-urakat
   (kasittele-api-kutsu
    s/Str {:ok s/Any}
    (fn [kayttaja urakkatyyppi]
      (log/debug "Haetaan urakkatyypin urakat " urakkatyyppi)
      (hae-urakkatyypin-urakat urakkatyyppi kayttaja)))

   :ls-hae-kayttajatiedot
   (fn [kayttaja]
     (log/debug "Käyttäjän tietojen haku")
     {:ok {:kayttajanimi (:kayttajanimi kayttaja)
           :nimi (str (:etunimi kayttaja) " " (:sukunimi kayttaja))
           :vakiohavaintojen-kuvaukset (q/hae-vakiohavaintojen-kuvaukset @db)}})))


(defn- tallenna-liite [req]
  (jdbc/with-db-transaction [tx @db]
    (let [id (tallenna-multipart-kuva! tx (get-in req [:multipart-params "liite"]) (get-in req [:kayttaja :id]))]
      {:status 200
       :headers {"Content-Type" "text/plain"}
       :body (str id)})))

(defn luo-routet [http]
  (http-palvelin/julkaise-reitti
   http :ls-juuri
   (GET "/laadunseuranta" [] (redirect (utils/polku "/index.html"))))
  (http-palvelin/julkaise-palvelu
   http :ls-tallenna-liite
   (wrap-multipart-params
    (fn [req]
      (tallenna-liite req)))
   {:ring-kasittelija? true})
  (laadunseuranta-api http))


(defrecord Laadunseuranta [asetukset]
  component/Lifecycle
  (start [{db :db
           http :http-palvelin
           :as this}]
    (c/aseta-config! asetukset)
    (tietokanta/aseta-tietokanta! db)
    (log/info "Harja laadunseuranta käynnistyy")
    (luo-routet http)
    this)

  (stop [this]
    ;; FIXME: poista routet
    this))
