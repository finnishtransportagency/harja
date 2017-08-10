(ns harja.palvelin.palvelut.vesivaylat.materiaalit
  "Vesiväylien materiaaliseurannan palvelut"
  (:require [specql.core :as specql]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [com.stuartsierra.component :as component]
            [harja.domain.vesivaylat.materiaali :as m]
            [harja.domain.muokkaustiedot :as muok]
            [harja.domain.oikeudet :as oikeudet]))

(defn- hae-materiaalilistaus [db user params]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-vesivayla-materiaalit user (::m/urakka-id params))
  (specql/fetch db ::m/materiaalilistaus (specql/columns ::m/materiaalilistaus) params))

(defn- kirjaa-materiaali [db user materiaali]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivayla-materiaalit user
                                  (::m/urakka-id materiaali))
  (specql/insert! db ::m/materiaali
                  (muok/lisaa-muokkaustiedot materiaali ::m/id user))
  (hae-materiaalilistaus db user (select-keys materiaali #{::m/urakka-id})))

(defn- poista-materiaalikirjaus [db user materiaali]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivayla-materiaalit user
                                  (::m/urakka-id materiaali))
  ;; TODO Vaadi materiaali kuuluu urakkaan
  (specql/update! db ::m/materiaali
                  (muok/poistotiedot user)
                  {::m/id (::m/id materiaali)})
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
    (http-palvelin/julkaise-palvelu http :poista-materiaalikirjaus
                                    (fn [user tiedot]
                                      (poista-materiaalikirjaus db user tiedot))
                                    {:kysely-spec ::m/poista-materiaalikirjaus
                                     :vastaus-spec ::m/materiaalilistauksen-vastaus})
    this)

  (stop [this]
    (http-palvelin/poista-palvelut
      (:http-palvelin this)
      :hae-vesivayla-materiaalilistaus
      :kirjaa-vesivayla-materiaali)
    this))
