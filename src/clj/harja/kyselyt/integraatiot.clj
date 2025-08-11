(ns harja.kyselyt.integraatiot
  (:require [jeesql.core :refer [defqueries]]
            [slingshot.slingshot :refer [throw+]]))

(declare hae-integraatiotapahtuman-tila)
(declare hae-integraation-id)

(defqueries "harja/kyselyt/integraatiot.sql"
  {:positional? true})

(defn integraation-id
  "Palauttaa integraation id:n annetulle järjestelmälle ja nimelle.
  Jos integraatiota ei ole, heitetään poikkeus :tuntematon-integraatio."
  [db jarjestelma nimi]
  (if-let [id (first (hae-integraation-id db jarjestelma nimi))]
    (:id id)
    (throw+ {:type :tunematon-integraatio
             :jarjestelma jarjestelma
             :nimi nimi})))
