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
            [harja.domain.organisaatio :as organisaatio]
            [namespacefy.core :refer [namespacefy]]
            [specql.core :as specql]
            [specql.op :as op]
            [namespacefy.core :as namespacefy]))

(defn hae-urakan-alukset [db user tiedot]
  ;; TODO Oikeustarkistus + testi sille
  (namespacefy
    (alukset-q/hae-urakan-alukset db {:urakka (::urakka/id tiedot)})
    {:ns :harja.domain.vesivaylat.alus}))

(defn hae-urakoitsijan-alukset [db user tiedot]
  ;; TODO Oikeustarkistus + testi sille
  (namespacefy/namespacefy
    (alukset-q/hae-urakoitsijan-alukset db {:urakoitsija (::organisaatio/id tiedot)})
    {:ns :harja.domain.vesivaylat.alus}))

(defn hae-kaikki-alukset [db user tiedot]
  ;; TODO Oikeustarkistus + testi sille
  (specql/fetch
    db
    ::alus/alus
    alus/perustiedot
    {}))

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
       :vastaus-spec ::alus/hae-urakan-alukset-vastaus}
      )
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
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-urakan-alukset
      :hae-urakoitsijan-alukset
      :hae-kaikki-alukset)
    this))