(ns harja.palvelin.palvelut.vesivaylat.toimenpiteet
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.async :as async]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.urakka :as ur]
            [harja.domain.roolit :as roolit]
            [harja.geo :as geo]
            [harja.transit :as transit]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.vesivaylat.toimenpiteet :as q]
            [harja.kyselyt.vesivaylat.hinnoittelut :as hinnoittelut-q]
            [harja.kyselyt.vesivaylat.kiintiot :as kiintiot-q]))

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
      (oikeudet/vaadi-oikeus "siirrä-kokonaishintaisiin" oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset user urakka-id)
      (q/vaadi-toimenpiteet-kuuluvat-urakkaan db (::to/idt tiedot) urakka-id)
      (jdbc/with-db-transaction [db db]
        (q/paivita-toimenpiteiden-tyyppi db (::to/idt tiedot) :kokonaishintainen)
        (hinnoittelut-q/poista-toimenpiteet-hintaryhmistaan! db user (::to/idt tiedot))
        (hinnoittelut-q/poista-toimenpiteet-omista-hinnoitteluista! db user (::to/idt tiedot))))
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
      (oikeudet/vaadi-oikeus "siirrä-yksikköhintaisiin" oikeudet/urakat-vesivaylatoimenpiteet-kokonaishintaiset user urakka-id)
      (q/vaadi-toimenpiteet-kuuluvat-urakkaan db (::to/idt tiedot) urakka-id)
      (jdbc/with-db-transaction [db db]
        (q/paivita-toimenpiteiden-tyyppi db (::to/idt tiedot) :yksikkohintainen)))
    (::to/idt tiedot)))

(defn lisaa-toimenpiteelle-liite [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::to/urakka-id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-oikeus "lisää-liite" oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset user urakka-id)
      (oikeudet/vaadi-oikeus "lisää-liite" oikeudet/urakat-vesivaylatoimenpiteet-kokonaishintaiset user urakka-id)
      (q/vaadi-toimenpiteet-kuuluvat-urakkaan db #{(::to/id tiedot)} urakka-id)
      (jdbc/with-db-transaction [db db]
        (q/lisaa-toimenpiteelle-liite db (::to/id tiedot) (::to/liite-id tiedot))))
    {:ok? true}))

(defn poista-toimenpiteen-liite [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::to/urakka-id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-oikeus "lisää-liite" oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset user urakka-id)
      (oikeudet/vaadi-oikeus "lisää-liite" oikeudet/urakat-vesivaylatoimenpiteet-kokonaishintaiset user urakka-id)
      (q/vaadi-toimenpiteet-kuuluvat-urakkaan db #{(::to/id tiedot)} urakka-id)
      (jdbc/with-db-transaction [db db]
        (q/poista-toimenpiteen-liite db (::to/id tiedot) (::to/liite-id tiedot))))
    {:ok? true}))

(defn tallenna-toimenpide! [db user {:keys [hakuehdot tallennettava]}]
  (q/tallenna-toimenpide! db user tallennettava)
  (if (= :yksikkohintainen (::to/hintatyyppi tallennettava))
    (hae-yksikkohintaiset-toimenpiteet db user hakuehdot)

    (hae-kokonaishintaiset-toimenpiteet db user hakuehdot)))

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
    (julkaise-palvelu
      http
      :poista-toimenpiteen-liite
      (fn [user tiedot]
        (poista-toimenpiteen-liite db user tiedot))
      {:kysely-spec ::to/poista-toimenpiteen-liite-kysely})
    (julkaise-palvelu
      http
      :tallenna-toimenpide
      (fn [user tiedot]
        (tallenna-toimenpide! db user tiedot))
      {:kysely-spec ::to/tallenna-toimenpide-kysely
       :vastaus-sepc ::to/tallenna-toimenpide-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-yksikkohintaiset-toimenpiteet
      :siirra-toimenpiteet-kokonaishintaisiin
      :hae-kokonaishintaiset-toimenpiteet
      :siirra-toimenpiteet-yksikkohintaisiin)
    this))
