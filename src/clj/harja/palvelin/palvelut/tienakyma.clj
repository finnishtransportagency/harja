(ns harja.palvelin.palvelut.tienakyma
  "Tienäkymän backend palvelu.

  Tienäkymässä haetaan TR osoitevälillä tietyllä aikavälillä tapahtuneita
  asioita. Palvelu on vain tilaajan käyttäjille, eikä hauissa ole mitään
  urakkarajauksia näkyvyyteen.

  Tienäkymän kaikki löydökset renderöidään frontilla."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin
             :refer [julkaise-palvelut poista-palvelut]]))

(defn- hae-tienakymaan [db user {:keys [tr alku loppu]}]
  ;; FIXME: dummy service
  [{:foo :bar}])

(defrecord Tienakyma []
  component/Lifecycle
  (start [{db :db http :http-palvelin :as this}]
    (julkaise-palvelut
     http
     :hae-tienakymaan (partial #'hae-tienakymaan db))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :hae-tienakymaan)
    this))
