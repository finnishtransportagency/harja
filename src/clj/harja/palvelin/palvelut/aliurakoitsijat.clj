(ns harja.palvelin.palvelut.aliurakoitsijat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.aliurakoitsijat :as q]
            [harja.domain.oikeudet :as oikeudet]))

(defn- hae-aliurakoitsijat
  [db user]
  (into []
        (q/hae-aliurakoitsijat db)))

(defn- hae-aliurakoitsija-nimella
  [db user nimi]
  (q/hae-aliurakoitsija-nimella db nimi))

(defrecord Aliurakoitsijat
  []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu :aliurakoitsijat (fn [user]
                                           (hae-aliurakoitsijat (:db this) user )))
      (julkaise-palvelu :aliurakoitsija (fn [user nimi]
                                          (hae-aliurakoitsija-nimella (:db this) user nimi))))
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :aliurakoitsijat)
    (poista-palvelu (:http-palvelin this) :aliurakoitsija)
    this))