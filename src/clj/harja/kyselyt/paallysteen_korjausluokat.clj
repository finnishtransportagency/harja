(ns harja.kyselyt.paallysteen-korjausluokat
  "Päällysteen korjausluokat ja niihin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/paallysteen-korjausluokat.sql"
  {:positional? true})
