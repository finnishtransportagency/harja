(ns harja.palvelin.palvelut.tyomaapaivakirja
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.tyomaapaivakirja :as q]
            [harja.domain.oikeudet :as oikeudet]))

(defn tyomaapaivakirja-hae [db user]
  (oikeudet/ei-oikeustarkistusta!)
  (into [] (q/hae-tiedot db)))

(defrecord Tyomaapaivakirja []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :tyomaapaivakirja-hae
      (fn [user _]
        (tyomaapaivakirja-hae (:db this) user)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this) :tyomaapaivakirja-hae)
    this))
