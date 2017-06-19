(ns harja.palvelin.palvelut.vesivaylat.materiaalit
  "Vesiväylien materiaaliseurannan palvelut"
  (:require [specql.core :as specql]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [com.stuartsierra.component :as component]
            [harja.domain.vesivaylat.materiaali :as m]
            [harja.domain.oikeudet :as oikeudet]))

(defn- hae-materiaalilistaus [db user params]
  ;; FIXME: lukuoikeuden tarkistus
  #_(oikeudet/vaadi-lukuoikeus )
  (specql/fetch db ::m/materiaalilistaus (specql/columns ::m/materiaalilistaus) params))

(defn- kirjaa-materiaali [db user materiaali]
  ;; TARKISTA OIKEUS
  (specql/insert! db ::m/materiaali materiaali)
  (hae-materiaalilistaus db user (select-keys materiaali #{::m/urakka-id})))

(defrecord Materiaalit []
  component/Lifecycle
  (start [{db :db
           http :http-palvelin :as this}]
    (http-palvelin/julkaise-palvelu http :hae-vesivayla-materiaalilistaus
                                    (fn [user haku]
                                      (hae-materiaalilistaus db user haku))
                                    {:kysely-spec ::m/materiaalilistauksen-haku
                                     :vastaus-spec ::m/materiaalilistauksen-vastaus})
    (http-palvelin/julkaise-palvelu http :kirjaa-vesivayla-materiaali
                                    (fn [user materiaali]
                                      (kirjaa-materiaali db user materiaali))
                                    {:kysely-spec ::m/materiaali-insert
                                     :vastaus-spec ::m/materiaalilistauksen-vastaus})
    this)

  (stop [{http :http-palvelin :as this}]
    (http-palvelin/poista-palvelu http :hae-vesivayla-materiaalilistaus)
    this))
