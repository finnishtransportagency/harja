(ns harja.kyselyt.toteumat
  "Toteumien ja toteuman reittien kyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.geo :as geo]
            [specql.core :refer [upsert! delete!]]
            [harja.domain.reittipiste :as rp]))

(defn muunna-reitti [{reitti :reitti :as rivi}]
  (assoc rivi
         :reitti (geo/pg->clj reitti)))

(defqueries "harja/kyselyt/toteumat.sql"
  {:positional? true})

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja]
  (log/debug "Tarkistetaan onko olemassa toteuma ulkoisella id:llä " ulkoinen-id " ja luojalla " luoja)
  (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id luoja))))

(defn pisteen-hoitoluokat [db piste]
  (first (hae-pisteen-hoitoluokat db piste)))

(defn tallenna-toteuman-reittipisteet! [db toteuman-reittipisteet]
  (upsert! db ::rp/toteuman-reittipisteet
           toteuman-reittipisteet))

(defn poista-reittipiste-toteuma-idlla! [db toteuma-id]
  (delete! db ::rp/toteuman-reittipisteet
           {::rp/toteuma-id toteuma-id}))
