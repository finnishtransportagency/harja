(ns harja.palvelin.palvelut.lupaukset
  "Palvelu välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt
             [lupaukset :as lupaukset-q]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(defn- hae-urakan-lupaustiedot [db user urakka-id]
  (lupaukset-q/hae-urakan-lupaustiedot db {:urakkaid urakka-id}))


(defrecord Lupaukset []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this) :hae-urakan-lupaustiedot
                      (fn [user urakka-id]
                        (hae-urakan-lupaustiedot (:db this) user urakka-id)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-urakan-lupaustiedot)
    this))