(ns harja.palvelin.palvelut.vesivaylat.toimenpiteet.yksikkohintaiset
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.async :as async]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.roolit :as roolit]
            [harja.geo :as geo]
            [harja.transit :as transit]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.vesivaylat.toimenpiteet :as q]))

(defn hae-yksikkohintaiset-toimenpiteet [db user {:keys [urakka-id] :as tiedot}]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset user urakka-id)
    (q/hae-toimenpiteet db tiedot)))

(defrecord YksikkohintaisetToimenpiteet []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-yksikkohintaiset-toimenpiteet
      (fn [user tiedot]
        (hae-yksikkohintaiset-toimenpiteet db user tiedot))
      {:kysely-spec ::to/hae-vesivaylien-toimenpiteet-kyselyt
       :vastaus-spec ::to/hae-vesivayilien-toimenpiteet-vastaukset})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-yksikkohintaiset-toimenpiteet)
    this))