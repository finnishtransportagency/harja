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

(declare luo-erilliskustannus<! onko-olemassa-ulkoisella-idlla onko-toteumalla-suolausta hae-pisteen-hoitoluokat
  luo-toteuma<! poista-toteuma! luo-toteuma_tehtava<! luodun-toteuman-id hae-toteuman-hash siirra-toteumat-analytiikalle
  hae-reitittomat-mutta-reittipisteelliset-toteumat hae-reitittomat-mutta-osoitteelliset-toteumat
  hae-reittitoteumat-analytiikalle luo-toteuma-materiaali<! hae-toteuman-alkanut-pvm-idlla paivita-toteuma<!
  paivita-toteuman-reittigeometria<! paivita-toteuma-materiaali! paivita-palautettu-analytiikalle-aikaleima!
  lisaa-toteumalle-jsonhash! hae-toteuman-reittipisteet paivita-toteuma-ulkoisella-idlla<! toteuman-id-ulkoisella-idlla
  hae-poistettavien-toteumien-paivat-ja-aikavali-ulkoisella-idlla
  poista-toteumat-ulkoisilla-idlla-ja-luojalla!
  hae-toteumat-ilman-reittipisteita-analytiikalle
  hae-toteuman-perustiedot-ulkoisella-idlla poista-toteuma_tehtava-toteuma-idlla!
  poista-toteuma-materiaali-toteuma-idlla! paivita-toteuman-muokattu!)

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id urakka-id]
  (log/debug "Tarkistetaan onko olemassa toteuma ulkoisella id:llä " ulkoinen-id " ja urakka id:llä: " urakka-id)
  (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id urakka-id))))

;; Talvihoitoluokat niille kevyen liikenteen väylille, joita ei suolata, eli K1, K2 ja K (Ei talvihoitoa)
(def kelvien-talvihoitoluokat [9 10 11])

(defn pisteen-hoitoluokat [db piste tehtavat materiaalit]
  (let [suolausta? (when (or (seq tehtavat) (seq materiaalit))
                     (onko-toteumalla-suolausta db {:materiaalit materiaalit :tehtavat tehtavat}))]
    (first (hae-pisteen-hoitoluokat db (assoc piste :kielletyt_hoitoluokat (when suolausta? kelvien-talvihoitoluokat))))))

(defn tallenna-toteuman-reittipisteet! [db toteuman-reittipisteet]
  (upsert! db ::rp/toteuman-reittipisteet
           toteuman-reittipisteet))

(defn poista-toteuman-reittipisteet-toteuma-idlla! [db toteuma-id]
  (delete! db ::rp/toteuman-reittipisteet
           {::rp/toteuma-id toteuma-id}))

;; Partitiointimuutoksen jälkeen toteumataulusta pitää hakea uusin id aina INSERT:n
;; jälkeen. Käytetään tätä funktiota sovelluksen puolella, API-puolella on omansa.
(defn luo-uusi-toteuma
  "Luo uuden toteuman ja palauttaa sen id:n"
  [db toteuma]
  (luo-toteuma<! db toteuma)
  (luodun-toteuman-id db))

(defn ei-ole-lahetetty-aiemmin? [db-replica jsonhash ulkoinen-id]
  ;; Jos hashia ei löydy, ei ole lähetetty aiemmin
  (if-not (:exists (first (hae-toteuman-hash db-replica {:hash jsonhash
                                                         :ulkoinen-id ulkoinen-id})))
    true
    (do
      (log/info (format "Toteuma ulkoisella id:llä: %s on lähetetty aiemmin. Ei tallenneta uudestaan." ulkoinen-id))
      false)))
