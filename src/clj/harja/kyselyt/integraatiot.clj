(ns harja.kyselyt.integraatiot
  (:require [jeesql.core :refer [defqueries]]
            [slingshot.slingshot :refer [throw+]]
            [taoensso.truss :refer [have]]))

(defqueries "harja/kyselyt/integraatiot.sql"
  {:positional? true})

(defn integraatiotapahtuman-tila
  "Hakee integraatiotapahtuman tilan: alkanut, päättynyt ja onnistunut."
  [db integraatio ulkoinen-id]
  (let [tila (first (hae-integraatiotapahtuman-tila db integraatio ulkoinen-id))]
    (if tila
      tila
      (throw+ {:type :tuntematon-integraatiotapahtuma
               :integraatio integraatio
               :ulkoinen-id ulkoinen-id}))))

(defn integraatiotapahtuma-paattynyt?
  "Tarkistaa onko integraatiotapahtuma päättynyt annetulla integraatio id:llä ja ulkoise "
  [db integraatio ulkoinen-id]
  (some? (:paattynyt (integraatiotapahtuman-tila db
                                                 (have some? integraatio)
                                                 (have some? ulkoinen-id)))))

(defn integraatiotapahtuma-onnistunut?
  "Palauttaa true, jos integraatiotapahtuma on onnistunut"
  [db integraatio ulkoinen-id]
  (:onnistunut (integraatiotapahtuman-tila db integraatio ulkoinen-id)))

(defn integraation-id
  "Palauttaa integraation id:n annetulle järjestelmälle ja nimelle.
  Jos integraatiota ei ole, heitetään poikkeus :tuntematon-integraatio."
  [db jarjestelma nimi]
  (if-let [id (first (hae-integraation-id db jarjestelma nimi))]
    (:id id)
    (throw+ {:type :tunematon-integraatio
             :jarjestelma jarjestelma
             :nimi nimi})))
