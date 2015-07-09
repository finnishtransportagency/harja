(ns harja.kyselyt.integraatioloki
  "Integraatiotapahtumiin liittyvät tietokantakyselyt"
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/integraatioloki.sql")