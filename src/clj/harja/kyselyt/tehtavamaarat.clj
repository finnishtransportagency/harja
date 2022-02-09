(ns harja.kyselyt.tehtavamaarat
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch upsert!]]
            [harja.kyselyt.specql-db :refer [define-tables]]
            [harja.domain.urakka :as urakka]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.pvm :as pvm]))

(defqueries "harja/kyselyt/tehtavamaarat.sql"
  {:positional? true})

(define-tables
  ["sopimus_tehtavamaara" ::sopimus-tehtavamaara
   {"id" ::sopimus-tehtavamaara-id
    "urakka" ::urakka/id
    "tehtava" ::toimenpidekoodi/id
    "maara" ::maara
    "muokattu" ::muokkaustiedot/muokattu
    "muokkaaja" ::muokkaustiedot/muokkaaja-id}])

(defn tallenna-sopimuksen-tehtavamaara [db user urakka-id tehtava maara]
  (upsert! db ::sopimus-tehtavamaara
    #{::urakka/id ::toimenpidekoodi/id}
    {::urakka/id urakka-id
     ::toimenpidekoodi/id tehtava
     ::maara maara
     ::muokkaustiedot/muokattu (pvm/nyt)
     ::muokkaustiedot/muokkaaja-id (:id user)}))
