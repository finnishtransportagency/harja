(ns harja.kyselyt.tehtavamaarat
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch upsert!]]
            [harja.kyselyt.specql-db :refer [define-tables]]
            [harja.domain.urakka :as urakka]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]))

(defqueries "harja/kyselyt/tehtavamaarat.sql"
  {:positional? true})

(define-tables
  ["suunniteltu_tehtavamaara" ::suunniteltu-tehtavamaara
   {"id" ::suunniteltu-tehtavamara-id
    "urakka" ::urakka/id
    "tehtava" ::toimenpidekoodi/id
    "maara" ::maara
    "muokattu" ::muokattu
    }]
  )

(defn tallenna-suunnitellut-tehtavamaarat [db st]
  (upsert! db ::suunniteltu-tehtavamaara st))
