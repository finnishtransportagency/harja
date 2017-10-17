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
            [namespacefy.core :as namespacefy]))

(defn hae-urakan-alukset [db user tiedot]
  ;; TODO Oikeustarkistus + testi sille
  (namespacefy
    (alukset-q/hae-urakan-alukset db {:urakka (::urakka/id tiedot)})
    {:ns :harja.domain.vesivaylat.alus
     :custom {:lisatiedot ::alus/urakan-aluksen-kayton-lisatiedot}}))

(defn hae-urakoitsijan-alukset [db user tiedot]
  ;; TODO Oikeustarkistus + testi sille
  (namespacefy/namespacefy
    (alukset-q/hae-urakoitsijan-alukset db {:urakoitsija (::organisaatio/id tiedot)})
    {:ns :harja.domain.vesivaylat.alus}))

(defn hae-kaikki-alukset [db user tiedot]
  ;; TODO Oikeustarkistus + testi sille
  (sort-by ::alus/mmsi (specql/fetch
                         db
                         ::alus/alus
                         alus/perustiedot
                         {})))

(defn tallenna-urakan-alukset [db user tiedot]
  ;; TODO Oikeustarkistus + testi sille
  (let [urakka-id (::urakka/id tiedot)
        alukset (::alus/urakan-tallennettavat-alukset tiedot)]
    (jdbc/with-db-transaction [db db]
      (doseq [alus alukset]
        (if (::alus/mmsi alus)
          (specql/update!
            db
            ::alus/urakan-aluksen-kaytto
            {::alus/urakan-alus-mmsi (::alus/mmsi alus)
             ::alus/urakan-aluksen-kayton-lisatiedot (::alus/urakan-aluksen-kayton-lisatiedot alus)
             ::m/poistettu? (:poistettu alus)}
            {::alus/urakan-alus-mmsi (::alus/mmsi alus)})
          (specql/insert!
            db
            ::alus/urakan-aluksen-kaytto
            {::alus/urakan-alus-mmsi (::alus/mmsi alus)
             ::urakka/id urakka-id
             ::alus/urakan-aluksen-kayton-lisatiedot (::alus/urakan-aluksen-kayton-lisatiedot alus)})))
      (hae-urakan-alukset db user tiedot))))

(defrecord Alukset []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-urakan-alukset
      (fn [user tiedot]
        (hae-urakan-alukset db user tiedot))
      {:kysely-spec ::alus/hae-urakan-alukset-kysely
       :vastaus-spec ::alus/hae-urakan-alukset-vastaus})
    (julkaise-palvelu
      http
      :hae-urakoitsijan-alukset
      (fn [user tiedot]
        (hae-urakoitsijan-alukset db user tiedot))
      {:kysely-spec ::alus/hae-urakoitsijan-alukset-kysely
       :vastaus-spec ::alus/hae-urakoitsijan-alukset-vastaus})
    (julkaise-palvelu
      http
      :hae-kaikki-alukset
      (fn [user tiedot]
        (hae-kaikki-alukset db user tiedot))
      {:kysely-spec ::alus/hae-kaikki-alukset-kysely
       :vastaus-spec ::alus/hae-kaikki-alukset-vastaus})
    (julkaise-palvelu
      http
      :tallenna-urakan-alukset
      (fn [user tiedot]
        (tallenna-urakan-alukset db user tiedot))
      {:kysely-spec ::alus/tallenna-urakan-alukset
       :vastaus-spec ::alus/hae-kaikki-alukset-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-urakan-alukset
      :hae-urakoitsijan-alukset
      :hae-kaikki-alukset)
    this))