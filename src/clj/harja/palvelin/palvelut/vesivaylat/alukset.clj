(ns harja.palvelin.palvelut.vesivaylat.alukset
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.async :as async]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.geo :as geo]
            [harja.transit :as transit]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.vesivaylat.alukset :as alukset-q]
            [harja.domain.vesivaylat.alus :as alus]
            [harja.domain.urakka :as urakka]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.organisaatio :as organisaatio]
            [namespacefy.core :refer [namespacefy]]
            [specql.core :as specql]
            [specql.op :as op]
            [namespacefy.core :as namespacefy]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.set :as set]
            [harja.palvelin.palvelut.urakoitsijat :as urakoitsijat]))

(defn vaadi-alus-kuuluu-urakoitsijalle [db alus-mmsi vaitetty-urakoitsija-id]
  (log/debug "Tarkikistetaan, että alus " alus-mmsi " kuuluu väitetylle urakoitsijalle " vaitetty-urakoitsija-id)
  (assert vaitetty-urakoitsija-id "Urakoitsija-id puuttuu!")
  (let [alus-kannassa (first (alukset-q/hae-alus-mmsilla db {:mmsi alus-mmsi}))]
    (when (and (some? alus-kannassa)
               (not= (:urakoitsija-id alus-kannassa) vaitetty-urakoitsija-id))
      (throw (SecurityException. (str "Alus ei kuulu urakoitsijalle " vaitetty-urakoitsija-id
                                      " vaan urakoitisjalle " (:urakoitsija-id alus-kannassa)))))))

(defn hae-urakoitsijan-alukset [db user tiedot]
  (let [urakka-id (::urakka/id tiedot)]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-yleiset user urakka-id)
    (namespacefy/namespacefy
      (into []
            (map #(konv/array->set % :kaytossa-urakoissa))
            (alukset-q/hae-urakoitsijan-alukset db {:urakka urakka-id
                                                    :urakoitsija (::alus/urakoitsija-id tiedot)}))
      {:ns :harja.domain.vesivaylat.alus})))

(defn- tallenna-urakoitsijan-alus
  "Luo uuden tai päivittää olemassa olevan aluksen MMSI:n perusteella."
  [db user urakoitsija-id alus]
  (if (first (alukset-q/hae-urakoitsijan-alus-mmsilla db {:mmsi (::alus/mmsi alus)
                                                          :urakoitsija urakoitsija-id}))
    (specql/update!
      db
      ::alus/alus
      {::alus/nimi (::alus/nimi alus)
       ::alus/lisatiedot (::alus/lisatiedot alus)
       ::m/muokattu (c/to-sql-time (t/now))
       ::m/muokkaaja-id (:id user)
       ::m/poistettu? (or (:poistettu alus) false)
       ::m/poistaja-id (when (:poistettu alus) (:id user))}
      {::alus/mmsi (::alus/mmsi alus)})
    (specql/insert!
      db
      ::alus/alus
      {::alus/mmsi (::alus/mmsi alus)
       ::alus/nimi (::alus/nimi alus)
       ::alus/lisatiedot (::alus/lisatiedot alus)
       ::alus/urakoitsija-id urakoitsija-id
       ::m/luotu (c/to-sql-time (t/now))
       ::m/luoja-id (:id user)})))

(defn- tallenna-aluksen-kaytto-urakassa
  "Linkittää aluksen urakkaan tai merkitsee linkityksen poistetuksi."
  [db user urakka-id alus]
  (if (first (alukset-q/hae-urakan-alus-mmsilla db {:mmsi (::alus/mmsi alus)
                                                    :urakka urakka-id}))
    (let [kaytossa-urakassa? (::alus/kaytossa-urakassa? alus)
          alus-poistettu? (:poistettu alus)]
      (specql/update!
        db
        ::alus/urakan-aluksen-kaytto
        {::alus/urakan-aluksen-kayton-lisatiedot (::alus/urakan-aluksen-kayton-lisatiedot alus)
         ::m/muokattu (c/to-sql-time (t/now))
         ::m/muokkaaja-id (:id user)
         ;; Poista linkitys jos ei enää kuulu urakkaan
         ::m/poistettu? (or alus-poistettu?
                            (not kaytossa-urakassa?))
         ::m/poistaja-id (when (or alus-poistettu?
                                   (not kaytossa-urakassa?))
                           (:id user))}
        {::alus/urakan-alus-mmsi (::alus/mmsi alus)
         ::alus/urakka-id urakka-id}))
    ;; Alukselle ei ole linkitystä urakkaan kannassa, lisää jos ilmoitettu kuuluvaksi urakkaan
    (when (::alus/kaytossa-urakassa? alus)
      (specql/insert!
        db
        ::alus/urakan-aluksen-kaytto
        {::alus/urakan-alus-mmsi (::alus/mmsi alus)
         ::alus/urakka-id urakka-id
         ::alus/urakan-aluksen-kayton-lisatiedot (::alus/urakan-aluksen-kayton-lisatiedot alus)
         ::m/luotu (c/to-sql-time (t/now))
         ::m/luoja-id (:id user)}))))

(defn tallenna-urakoitsijan-alukset [db user tiedot]
  (let [urakka-id (::urakka/id tiedot)
        urakoitsija-id (::alus/urakoitsija-id tiedot)
        alukset (::alus/tallennettavat-alukset tiedot)]
    (oikeudet/vaadi-oikeus "alusten-muokkaus" oikeudet/urakat-yleiset user urakka-id)
    (urakoitsijat/vaadi-urakoitsija-kuuluu-urakkaan db urakoitsija-id urakka-id)
    (doseq [alus alukset]
      (vaadi-alus-kuuluu-urakoitsijalle db (::alus/mmsi alus) urakoitsija-id))

    (jdbc/with-db-transaction [db db]
      (doseq [alus alukset]
        (tallenna-urakoitsijan-alus db user urakoitsija-id alus)
        (tallenna-aluksen-kaytto-urakassa db user urakka-id alus))
      (hae-urakoitsijan-alukset db user tiedot))))

(defn hae-alusten-reitit
  ([db user tiedot] (hae-alusten-reitit db user tiedot false))
  ([db _ tiedot pisteet?]
   (oikeudet/ei-oikeustarkistusta!)
   (if pisteet?
     (alukset-q/alusten-reitit-pisteineen db tiedot)
     (alukset-q/alusten-reitit db tiedot))))

(defrecord Alukset []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-urakoitsijan-alukset
      (fn [user tiedot]
        (hae-urakoitsijan-alukset db user tiedot))
      {:kysely-spec ::alus/hae-urakoitsijan-alukset-kysely
       :vastaus-spec ::alus/hae-urakoitsijan-alukset-vastaus})
    (julkaise-palvelu
      http
      :tallenna-urakoitsijan-alukset
      (fn [user tiedot]
        (tallenna-urakoitsijan-alukset db user tiedot))
      {:kysely-spec ::alus/tallenna-urakoitsijan-alukset-kysely
       :vastaus-spec ::alus/hae-urakoitsijan-alukset-vastaus})
    (julkaise-palvelu
      http
      :hae-alusten-reitit-pisteineen
      (fn [user tiedot]
        (hae-alusten-reitit db user tiedot true))
      {:kysely-spec ::alus/hae-alusten-reitit-pisteineen-kysely
       :vastaus-spec ::alus/hae-alusten-reitit-pisteineen-vastaus})
    (julkaise-palvelu
      http
      :hae-alusten-reitit
      (fn [user tiedot]
        (hae-alusten-reitit db user tiedot))
      {:kysely-spec ::alus/hae-alusten-reitit-kysely
       :vastaus-spec ::alus/hae-alusten-reitit-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-urakan-alukset
      :hae-urakoitsijan-alukset
      :hae-kaikki-alukset
      :tallenna-urakan-alukset
      :tallenna-alukset
      :hae-alusten-reitit-pisteineen
      :hae-alusten-reitit)
    this))
