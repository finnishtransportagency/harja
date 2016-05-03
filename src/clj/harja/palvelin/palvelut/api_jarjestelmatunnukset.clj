(ns harja.palvelin.palvelut.api-jarjestelmatunnukset
  (:require [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.api-jarjestelmatunnukset :as q]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-jarjestelmatunnukset [db user]
  (oikeudet/lue oikeudet/hallinta-api-jarjestelmatunnukset user)
  (q/hae-jarjestelmatunnukset db))

(defrecord APIJarjestelmatunnukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]
    (julkaise-palvelu http :hae-jarjestelmatunnukset
                      (fn [user payload]
                        (hae-jarjestelmatunnukset db user)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :hae-jarjestelmatunnukset)
    this))
