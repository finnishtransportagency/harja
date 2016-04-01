(ns harja.kyselyt.integraatioloki
  "Integraatiotapahtumiin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/integraatioloki.sql"
  {:positional? true})
