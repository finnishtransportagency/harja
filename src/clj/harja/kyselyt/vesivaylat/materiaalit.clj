(ns harja.kyselyt.vesivaylat.materiaalit
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch insert! update!] :as specql]
            [harja.domain.vesivaylat.materiaali :as m]
            [harja.domain.muokkaustiedot :as muok]))

(defqueries "harja/kyselyt/vesivaylat/materiaalit.sql")

(defn hae-materiaalilistaus [db hakuehdot]
  (fetch db ::m/materiaalilistaus (specql/columns ::m/materiaalilistaus) hakuehdot))

(defn kirjaa-materiaali [db user materiaalikirjaus]
  "Kirjaa materiaalin käytön tai lisäyksen"
  (if (::m/id materiaalikirjaus)
    (update! db ::m/materiaali
             (muok/lisaa-muokkaustiedot materiaalikirjaus ::m/id user)
             {::m/id (::m/id materiaalikirjaus)})
    (insert! db ::m/materiaali
             (muok/lisaa-muokkaustiedot materiaalikirjaus ::m/id user))))

(defn poista-materiaalikirjaus [db user materiaali-id]
  (update! db ::m/materiaali
           (muok/poistotiedot user)
           {::m/id (::m/id materiaali-id)}))

