(ns harja.palvelin.palvelut.valitavoitteet
  "Palvelu välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.oikeudet :as oik]
            [harja.kyselyt.valitavoitteet :as q]
            [harja.kyselyt.konversio :as konv]))

(defn hae-valitavoitteet [db user urakka-id]
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)

  (into []
        (map konv/alaviiva->rakenne)
        (q/hae-urakan-valitavoitteet db urakka-id)))

(defrecord Valitavoitteet []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this) :hae-urakan-valitavoitteet
                      (fn [user urakka-id]
                        (hae-valitavoitteet (:db this) user urakka-id)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this) :hae-urakan-valitavoitteet)
    this))
