(ns harja.palvelin.palvelut.vesivaylat.kiintiot
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.async :as async]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.domain.oikeudet :as oikeudet]

            [harja.domain.urakka :as ur]
            [harja.domain.roolit :as roolit]
            [harja.domain.vesivaylat.kiintio :as kiintio]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.geo :as geo]
            [harja.transit :as transit]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.vesivaylat.kiintiot :as q]
            [harja.kyselyt.vesivaylat.toimenpiteet :as q-toimenpiteet]))

(defn- hae-kiintiot [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::kiintio/urakka-id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-vesivaylasuunnittelu-kiintiot user urakka-id)
      (q/hae-kiintiot db tiedot))))

(defn- tallenna-kiintiot [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::kiintio/urakka-id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylasuunnittelu-kiintiot user urakka-id)
      (q/vaadi-kiintiot-kuuluvat-urakkaan! db
                                           (keep ::kiintio/id (::kiintio/tallennettavat-kiintiot tiedot))
                                           urakka-id)
      (jdbc/with-db-transaction [db db]
        (q/tallenna-kiintiot! db user tiedot)
        (hae-kiintiot db user (assoc tiedot :toimenpiteet? true))))))

(defn- liita-toimenpiteet-kiintioon [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::kiintio/urakka-id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-oikeus "liitä-kiintiöön" oikeudet/urakat-vesivaylatoimenpiteet-kokonaishintaiset user urakka-id)
      (q/vaadi-kiintiot-kuuluvat-urakkaan! db
                                           #{(::kiintio/id tiedot)}
                                           urakka-id)
      (q-toimenpiteet/vaadi-toimenpiteet-kuuluvat-urakkaan db
                                                           (::to/idt tiedot)
                                                           urakka-id)
      (jdbc/with-db-transaction [db db]
        (q/liita-toimenpiteet-kiintioon db user tiedot))
      {::to/idt (::to/idt tiedot)})))

(defn- irrota-toimenpiteet-kiintiosta [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::to/urakka-id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-oikeus "irrota" oikeudet/urakat-vesivaylasuunnittelu-kiintiot user urakka-id)
      (q-toimenpiteet/vaadi-toimenpiteet-kuuluvat-urakkaan db
                                                           (::to/idt tiedot)
                                                           urakka-id)
      (jdbc/with-db-transaction [db db]
        (q/irrota-toimenpiteet-kiintiosta db user tiedot))
      {::to/idt (::to/idt tiedot)})))

(defrecord Kiintiot []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-kiintiot-ja-toimenpiteet
      (fn [user tiedot]
        (hae-kiintiot db user (assoc tiedot :toimenpiteet? true)))
      {:kysely-spec ::kiintio/hae-kiintiot-kysely
       :vastaus-spec ::kiintio/hae-kiintiot-ja-toimenpiteet-vastaus})

    (julkaise-palvelu
      http
      :hae-kiintiot
      (fn [user tiedot]
        (hae-kiintiot db user tiedot))
      {:kysely-spec ::kiintio/hae-kiintiot-kysely
       :vastaus-spec ::kiintio/hae-kiintiot-vastaus})

    (julkaise-palvelu
      http
      :tallenna-kiintiot
      (fn [user tiedot]
        (tallenna-kiintiot db user tiedot))
      {:kysely-spec ::kiintio/tallenna-kiintiot-kysely
       :vastaus-spec ::kiintio/tallenna-kiintiot-vastaus})

    (julkaise-palvelu
      http
      :liita-toimenpiteet-kiintioon
      (fn [user tiedot]
        (liita-toimenpiteet-kiintioon db user tiedot))
      {:kysely-spec ::kiintio/liita-toimenpiteet-kiintioon-kysely
       :vastaus-spec ::kiintio/liita-toimenpiteet-kiintioon-vastaus})

    (julkaise-palvelu
      http
      :irrota-toimenpiteet-kiintiosta
      (fn [user tiedot]
        (irrota-toimenpiteet-kiintiosta db user tiedot))
      {:kysely-spec ::kiintio/irrota-toimenpiteet-kiintiosta-kysely
       :vastaus-spec ::kiintio/irrota-toimenpiteet-kiintiosta-vastaus})

    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-kiintiot-ja-toimenpiteet
      :hae-kiintiot
      :tallenna-kiintiot)
    this))
