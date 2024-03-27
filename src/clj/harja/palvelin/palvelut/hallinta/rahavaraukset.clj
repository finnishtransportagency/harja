(ns harja.palvelin.palvelut.hallinta.rahavaraukset
  (:require [com.stuartsierra.component :as component]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(defn- hae-urakoiden-rahavaraukset [db kayttaja tiedot]

  )

(defrecord RahavarauksetHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-urakoiden-rahavaraukset
      (fn [kayttaja tiedot]
        (hae-urakoiden-rahavaraukset db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :paivita-urakan-rahavaraukset
      (fn [kayttaja tiedot]
        (paivita-urakan-rahavaraukset db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-tarjoushinnat
      :paivita-tarjoushinnat)
    this))
