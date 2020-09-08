(ns harja.kyselyt.toteumat
  "Toteumien ja toteuman reittien kyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.geo :as geo]
            [specql.core :refer [upsert! delete!]]
            [harja.domain.reittipiste :as rp]
            [harja.kyselyt.sopimukset :as sopimukset]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]])
  (:use [slingshot.slingshot :only [throw+]]))

(defn muunna-reitti [{reitti :reitti :as rivi}]
  (assoc rivi
         :reitti (geo/pg->clj reitti)))

(defqueries "harja/kyselyt/toteumat.sql"
  {:positional? true})

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja urakka-id]
  (log/debug "Tarkistetaan onko olemassa toteuma ulkoisella id:llä " ulkoinen-id " ja luojalla " luoja " sekä urakka id:llä: " urakka-id)
  (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id luoja urakka-id))))

(defn pisteen-hoitoluokat [db piste]
  (first (hae-pisteen-hoitoluokat db piste)))

(defn tallenna-toteuman-reittipisteet! [db toteuman-reittipisteet]
  (upsert! db ::rp/toteuman-reittipisteet
           toteuman-reittipisteet))

(defn poista-reittipiste-toteuma-idlla! [db toteuma-id]
  (delete! db ::rp/toteuman-reittipisteet
           {::rp/toteuma-id toteuma-id}))

;; Partitiointimuutoksen jälkeen toteumataulusta pitää hakea uusin id aina INSERT:n
;; jälkeen. Käytetään tätä funktiota sovelluksen puolella, API-puolella on omansa.
(defn luo-uusi-toteuma
  "Luo uuden toteuman ja palauttaa sen id:n"
  [db toteuma]
  (do
    (luo-toteuma<! db toteuma)
    (luodun-toteuman-id db)))