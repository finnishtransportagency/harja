(ns harja.palvelin.palvelut.vesivaylat.toimenpiteet
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.async :as async]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.urakka :as ur]
            [harja.domain.roolit :as roolit]
            [harja.geo :as geo]
            [harja.transit :as transit]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.vesivaylat.toimenpiteet :as q]))

(defn hae-yksikkohintaiset-toimenpiteet [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::to/urakka-id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset user urakka-id)
      (q/hae-toimenpiteet db (assoc tiedot :tyyppi :yksikkohintainen)))))

(defn siirra-toimenpiteet-kokonaishintaisiin [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::to/urakka-id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                      user urakka-id)
      (jdbc/with-db-transaction [db db]
        (q/paivita-toimenpiteiden-tyyppi db (::to/idt tiedot) :kokonaishintainen)))
    (::to/idt tiedot)))

(defn hae-kokonaishintaiset-toimenpiteet [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::to/urakka-id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-vesivaylatoimenpiteet-kokonaishintaiset user urakka-id)
      (q/hae-toimenpiteet db (assoc tiedot :tyyppi :kokonaishintainen)))))

(defn siirra-toimenpiteet-yksikkohintaisiin [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::to/urakka-id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylatoimenpiteet-kokonaishintaiset
                                      user urakka-id)
      (jdbc/with-db-transaction [db db]
        (q/paivita-toimenpiteiden-tyyppi db (::to/idt tiedot) :yksikkohintainen)))
    (::to/idt tiedot)))

(defn lisaa-toimenpiteelle-liite [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::to/urakka-id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                      user urakka-id)
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylatoimenpiteet-kokonaishintaiset
                                      user urakka-id)
      (jdbc/with-db-transaction [db db]
        (q/lisaa-toimenpiteelle-liite db (::to/id tiedot) (::to/liite-id tiedot))))
    {:ok? true}))

(defrecord Toimenpiteet []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-yksikkohintaiset-toimenpiteet
      (fn [user tiedot]
        (hae-yksikkohintaiset-toimenpiteet db user tiedot))
      {:kysely-spec ::to/hae-vesivaylien-toimenpiteet-kysely
       :vastaus-spec ::to/hae-vesivayilien-yksikkohintaiset-toimenpiteet-vastaus})
    (julkaise-palvelu
      http
      :siirra-toimenpiteet-kokonaishintaisiin
      (fn [user tiedot]
        (siirra-toimenpiteet-kokonaishintaisiin db user tiedot))
      {:kysely-spec ::to/siirra-toimenpiteet-kokonaishintaisiin-kysely})
    (julkaise-palvelu
      http
      :hae-kokonaishintaiset-toimenpiteet
      (fn [user tiedot]
        (hae-kokonaishintaiset-toimenpiteet db user tiedot))
      {:kysely-spec ::to/hae-vesivaylien-toimenpiteet-kysely
       :vastaus-spec ::to/hae-vesivayilien-kokonaishintaiset-toimenpiteet-vastaus})
    (julkaise-palvelu
      http
      :siirra-toimenpiteet-yksikkohintaisiin
      (fn [user tiedot]
        (siirra-toimenpiteet-yksikkohintaisiin db user tiedot))
      {:kysely-spec ::to/siirra-toimenpiteet-yksikkohintaisiin-kysely})
    (julkaise-palvelu
      http
      :lisaa-toimenpiteelle-liite
      (fn [user tiedot]
        (lisaa-toimenpiteelle-liite db user tiedot))
      {:kysely-spec ::to/lisaa-toimenpiteelle-liite-kysely})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-yksikkohintaiset-toimenpiteet
      :siirra-toimenpiteet-kokonaishintaisiin
      :hae-kokonaishintaiset-toimenpiteet
      :siirra-toimenpiteet-yksikkohintaisiin)
    this))