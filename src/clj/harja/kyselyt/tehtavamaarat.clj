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

(define-tables 
  ["sopimuksen_tehtavamaarat_tallennettu" ::sopimuksen-tehtavamaarat-tallennettu
   {"id" ::sopimuksen-tehtavamaara-tilan-id
    "urakka" ::urakka/id
    "tallennettu" ::tallennettu}])

(defn hae-sopimuksen-tila
  [db {:keys [urakka-id sopimus-id]}]
  (fetch db ::sopimuksen-tehtavamaarat-tallennettu
    #{::sopimuksen-tehtavamaara-tilan-id ::tallennettu ::urakka/id}
    (if sopimus-id 
      {::sopimuksen-tehtavamaara-tilan-id sopimus-id}
      {::urakka/id urakka-id})))

(defn tallenna-sopimuksen-tila
  [db {:keys [urakka-id sopimus-id]} tallennettu]
  (upsert! db ::sopimuksen-tehtavamaarat-tallennettu
    #{::urakka/id}
    {::tallennettu tallennettu
     ::urakka/id urakka-id}
    (if sopimus-id 
      {::sopimuksen-tehtavamaara-tilan-id sopimus-id}
      {::urakka/id urakka-id})))

(defn tallenna-sopimuksen-tehtavamaara [db user urakka-id tehtava maara]
  (upsert! db ::sopimus-tehtavamaara
    #{::urakka/id ::toimenpidekoodi/id}
    {::urakka/id urakka-id
     ::toimenpidekoodi/id tehtava
     ::maara maara
     ::muokkaustiedot/muokattu (pvm/nyt)
     ::muokkaustiedot/muokkaaja-id (:id user)}))
