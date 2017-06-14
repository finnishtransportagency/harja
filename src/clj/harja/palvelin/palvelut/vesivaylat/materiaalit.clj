(ns harja.palvelin.palvelut.vesivaylat.materiaalit
  "Vesiväylien materiaaliseurannan palvelut"
  (:require [specql.core :as specql]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [com.stuartsierra.component :as component]
            [harja.domain.vesivaylat.materiaali :as materiaali]
            [harja.domain.oikeudet :as oikeudet]))

(defn- hae-materiaalilistaus [db user params]
  ;; FIXME: lukuoikeuden tarkistus
  #_(oikeudet/vaadi-lukuoikeus )
  (specql/fetch db ::materiaalilistaus (specql/columns ::materiaalilistaus) params))

(defrecord Materiaalit []
  component/Lifecycle
  (start [{db :db
           http :http-palvelin :as this}]
    (http-palvelin/julkaise-palvelu
     http :hae-vesivayla-materiaalilistaus
     {:kysely-spec ::materiaali/materiaalilistauksen-haku
      :vastaus-spec ::materiaali/materiaalilistauksen-vastaus})))
