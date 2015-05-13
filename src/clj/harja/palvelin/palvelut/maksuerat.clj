(ns harja.palvelin.palvelut.maksuerat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.maksuerat :as q]
            [harja.palvelin.integraatiot.sampo :as sampo]
            [harja.kyselyt.konversio :as konversio]))

(declare hae-urakan-maksuerat)
(declare laheta-maksuera-sampoon)

(defrecord Maksuerat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakan-maksuerat (fn [user urakka-id]
                                              (hae-urakan-maksuerat (:db this) user urakka-id)))
    (julkaise-palvelu (:http-palvelin this)
                      :laheta-maksuera-sampooon (fn [user maksueranumero]
                                                  (laheta-maksuera-sampoon (:sampo this) user maksueranumero)))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-urakan-maksuerat)
    (poista-palvelu (:http-palvelin this) :laheta-maksuera-sampooon)
    this))

(defn hae-urakan-maksuerat
  "Palvelu, joka palauttaa urakan maksuerät."
  [db user urakka-id]
  (into []
        ;; FIXME: Oikeustarkistukset?
        (map konversio/alaviiva->rakenne (q/hae-urakan-maksuerat db urakka-id))))

(defn laheta-maksuera-sampoon
  "Palvelu, joka lähettää annetun maksuerän Sampoon."
  [sampo user maksueranumero]
  (into []
        ;; FIXME: Oikeustarkistukset?
        (sampo/laheta-maksuera-sampoon sampo maksueranumero)))

  
  
  
