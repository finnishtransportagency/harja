(ns harja.palvelin.palvelut.vesivaylat.toimenpiteet.kokonaishintaiset
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.async :as async]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.toteuma :as tot]
            [harja.domain.roolit :as roolit]
            [harja.geo :as geo]
            [harja.transit :as transit]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.vesivaylat.toimenpiteet :as q]))

(defn hae-kokonaishintaiset-toimenpiteet [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::tot/urakka-id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-vesivaylatoimenpiteet-kokonaishintaiset user urakka-id)
      (q/hae-toimenpiteet db tiedot))))

(defn siirra-toimenpiteet-yksikkohintaisiin [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::tot/urakka-id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylatoimenpiteet-kokonaishintaiset
                                      user (::tot/urakka-id tiedot))
      (q/paivita-toimenpiteiden-tyyppi db (::to/toimenpide-idt tiedot) :vv-yksikkohintainen))
    (::to/toimenpide-idt tiedot)))

(defrecord KokonaishintaisetToimenpiteet []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-kokonaishintaiset-toimenpiteet
      (fn [user tiedot]
        (hae-kokonaishintaiset-toimenpiteet db user tiedot))
      {:kysely-spec ::to/hae-vesivaylien-toimenpiteet-kysely
       :vastaus-spec ::to/hae-vesivayilien-toimenpiteet-vastaus})
    (julkaise-palvelu
      http
      :siirra-toimenpiteet-yksikkohintaisiin
      (fn [user tiedot]
        (siirra-toimenpiteet-yksikkohintaisiin db user tiedot))
      {:kysely-spec ::to/siirra-toimenpiteet-yksikkohintaisiin-kysely})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-kokonaishintaiset-toimenpiteet)
    this))
