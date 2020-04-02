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
  (q/hae-aliurakoitsija-nimella db {:nimi nimi}))

(defn- tallenna-aliurakoitsija
  [db user {:keys [nimi ytunnus]}]
  (q/tallenna-aliurakoitsija<! db {:kayttaja (:id user)
                                   :nimi     nimi
                                   :ytunnus  ytunnus}))

(defn- paivita-aliurakoitsija
  [db
   {kayttaja-id :id :as _user}
   {:keys [id nimi ytunnus] :as _aliurakoitsija}]
  (q/paivita-aliurakoitsija<! db {:kayttaja kayttaja-id
                                  :nimi nimi
                                  :ytunnus ytunnus
                                  :id id})
  (first (q/hae-aliurakoitsija-nimella db {:nimi nimi})))

(defrecord Aliurakoitsijat
  []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu :paivita-aliurakoitsija (fn [user aliurakoitsija]
                                                  (paivita-aliurakoitsija (:db this) user aliurakoitsija)))
      (julkaise-palvelu :tallenna-aliurakoitsija (fn [user aliurakoitsija]
                                                   (tallenna-aliurakoitsija (:db this) user aliurakoitsija)))
      (julkaise-palvelu :aliurakoitsijat (fn [user]
                                           (hae-aliurakoitsijat (:db this) user)))
      (julkaise-palvelu :aliurakoitsija (fn [user nimi]
                                          (hae-aliurakoitsija-nimella (:db this) user nimi))))
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :tallenna-aliurakoitsija)
    (poista-palvelu (:http-palvelin this) :aliurakoitsijat)
    (poista-palvelu (:http-palvelin this) :aliurakoitsija)
    (poista-palvelu (:http-palvelin this) :paivita-aliurakoitsija)
    this))