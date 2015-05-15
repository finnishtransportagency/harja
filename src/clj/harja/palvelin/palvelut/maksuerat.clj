(ns harja.palvelin.palvelut.maksuerat
    (:require [com.stuartsierra.component :as component]
              [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
              [harja.kyselyt.maksuerat :as q]
              [harja.palvelin.integraatiot.sampo :as sampo]
              [harja.kyselyt.konversio :as konversio]))

(declare hae-urakan-maksuerat)
(declare laheta-maksuerat-sampoon)

(defrecord Maksuerat []
    component/Lifecycle
    (start [this]
        (julkaise-palvelu (:http-palvelin this)
                          :hae-urakan-maksuerat (fn [user urakka-id]
                                                    (hae-urakan-maksuerat (:db this) user urakka-id)))

        (julkaise-palvelu (:http-palvelin this)
                          :laheta-maksuerat-sampoon (fn [user maksueranumerot]
                                                        (laheta-maksuerat-sampoon (:sampo this) user maksueranumerot)))
        this)

    (stop [this]
        (poista-palvelu (:http-palvelin this) :hae-urakan-maksuerat)
        (poista-palvelu (:http-palvelin this) :laheta-maksuerat-sampoon)
        this))

(defn hae-urakan-maksuerat
    "Palvelu, joka palauttaa urakan maksuerät."
    [db user urakka-id]
    (let [maksuerat (into []
          ;; FIXME: Oikeustarkistukset?
          (map konversio/alaviiva->rakenne (q/hae-urakan-maksuerat db urakka-id)))]
        (map #(assoc % :nimi (:nimi (:toimenpideinstanssi %))) maksuerat)))

(defn laheta-maksuerat-sampoon
    "Palvelu, joka lähettää annetut maksuerät Sampoon."
    [sampo user maksueranumerot]
    (into []
          ;; FIXME: Oikeustarkistukset?
          (mapv (fn [maksueranumero] (laheta-maksuera-sampoon sampo user maksueranumero)) maksueranumerot)))

