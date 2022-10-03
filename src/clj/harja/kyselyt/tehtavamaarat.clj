(ns harja.kyselyt.tehtavamaarat
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch upsert!]]
            [harja.kyselyt.specql-db :refer [define-tables]]
            [harja.domain.urakka :as urakka]
            [harja.domain.tehtavamaarat :as tehtavamaarat]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.pvm :as pvm]))

(defqueries "harja/kyselyt/tehtavamaarat.sql"
  {:positional? true})

(defn hae-sopimuksen-tila
  [db {:keys [urakka-id sopimus-id]}]
  (fetch db ::tehtavamaarat/sopimuksen-tehtavamaarat-tallennettu
    #{::tehtavamaarat/sopimuksen-tehtavamaara-tilan-id ::tehtavamaarat/tallennettu ::urakka/id}
    (if sopimus-id 
      {::tehtavamaarat/sopimuksen-tehtavamaara-tilan-id sopimus-id}
      {::urakka/id urakka-id})))

(defn tallenna-sopimuksen-tila
  [db {:keys [urakka-id sopimus-id]} tallennettu]
  (upsert! db ::tehtavamaarat/sopimuksen-tehtavamaarat-tallennettu
    #{::urakka/id}
    {::tehtavamaarat/tallennettu tallennettu
     ::urakka/id urakka-id}
    (if sopimus-id 
      {::tehtavamaarat/sopimuksen-tehtavamaara-tilan-id sopimus-id}
      {::urakka/id urakka-id})))

(defn tallenna-sopimuksen-tehtavamaara [db user urakka-id tehtava maara hoitovuosi]
  (upsert! db ::tehtavamaarat/sopimus-tehtavamaara
    #{::urakka/id ::toimenpidekoodi/id ::tehtavamaarat/hoitovuosi}
    {::urakka/id urakka-id
     ::toimenpidekoodi/id tehtava
     ::tehtavamaarat/maara maara
     ::tehtavamaarat/hoitovuosi hoitovuosi
     ::muokkaustiedot/muokattu (pvm/nyt)
     ::muokkaustiedot/muokkaaja-id (:id user)}))

