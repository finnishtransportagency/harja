(ns harja.kyselyt.vesivaylat.materiaalit
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch insert! update!] :as specql]
            [specql.op :as op]
            [harja.domain.vesivaylat.materiaali :as m]
            [harja.domain.muokkaustiedot :as muok]
            [namespacefy.core :refer [unnamespacefy]]))

(defqueries "harja/kyselyt/vesivaylat/materiaalit.sql")

(defn hae-materiaalilistaus [db hakuehdot]
  (fetch db ::m/materiaalilistaus (specql/columns ::m/materiaalilistaus) hakuehdot))

(defn poiston-jalkeiset-materiaalit [db idt toimenpide-id]
  (when (and idt (not (empty? idt)))
    (fetch db ::m/materiaali #{::m/nimi} {::m/id (op/not (op/in idt))
                                          ::muok/poistettu? false
                                          ::m/toimenpide toimenpide-id})))

(defn kirjaa-materiaali
  "Kirjaa materiaalin käytön tai lisäyksen"
  [db user materiaalikirjaus]
  (let [halytysraja-kirjauksessa? (not (nil? (::m/halytysraja materiaalikirjaus)))
        materiaalin-halytysraja (when-not halytysraja-kirjauksessa?
                                  (materiaalin-halytysraja db (unnamespacefy (select-keys materiaalikirjaus [::m/nimi ::m/urakka-id]))))
        materiaalikirjaus (if (not (empty? materiaalin-halytysraja))
                            (assoc materiaalikirjaus ::m/halytysraja (:halytysraja (first materiaalin-halytysraja)))
                            materiaalikirjaus)]
    (if (::m/id materiaalikirjaus)
      (update! db ::m/materiaali
               (muok/lisaa-muokkaustiedot materiaalikirjaus ::m/id user)
               {::m/id (::m/id materiaalikirjaus)})
      (insert! db ::m/materiaali
               (muok/lisaa-muokkaustiedot materiaalikirjaus ::m/id user)))))

(defn poista-materiaalikirjaus [db user materiaali-id]
  (update! db ::m/materiaali
           (muok/poistotiedot user)
           {::m/id materiaali-id}))

(defn poista-toimenpiteen-kaikki-materiaalikirjaukset [db user toimenpide-id]
  (update! db ::m/materiaali
           (muok/poistotiedot user)
           {::m/toimenpide toimenpide-id
            ::muok/poistettu? false}))
