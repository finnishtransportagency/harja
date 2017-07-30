(ns harja.palvelin.palvelut.vesivaylat.materiaalit
  "Vesiväylien materiaaliseurannan palvelut"
  (:require [specql.core :as specql]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [com.stuartsierra.component :as component]
            [harja.domain.vesivaylat.materiaali :as m]
            [harja.domain.muokkaustiedot :as muok]
            [harja.domain.oikeudet :as oikeudet]
            [clj-time.coerce :as c]
            [clj-time.core :as t]))

(defn- hae-materiaalilistaus [db user params]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-vesivayla-materiaalit user (::m/urakka-id params))
  (specql/fetch db ::m/materiaalilistaus (specql/columns ::m/materiaalilistaus) params))

(defn- kirjaa-materiaali [db user materiaali]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivayla-materiaalit user
                                  (::m/urakka-id materiaali))
  (specql/insert! db ::m/materiaali (assoc materiaali ::muok/luoja-id (:id user)
                                                      ::muok/luotu (c/to-sql-date (t/now))))
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
                                    {:kysely-spec ::m/materiaalikirjaus
                                     :vastaus-spec ::m/materiaalilistauksen-vastaus})
    this)

  (stop [this]
    (http-palvelin/poista-palvelut
      (:http-palvelin this)
      :hae-vesivayla-materiaalilistaus
      :kirjaa-vesivayla-materiaali)
    this))
