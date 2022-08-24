(ns harja.kyselyt.toteumat
  "Toteumien ja toteuman reittien kyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.geo :as geo]
            [specql.core :refer [upsert! delete!]]
            [harja.domain.reittipiste :as rp]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]))

(defn muunna-reitti [{reitti :reitti :as rivi}]
  (assoc rivi
         :reitti (geo/pg->clj reitti)))

(defqueries "harja/kyselyt/toteumat.sql"
  {:positional? true})

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja urakka-id]
  (log/debug "Tarkistetaan onko olemassa toteuma ulkoisella id:llä " ulkoinen-id " ja luojalla " luoja " sekä urakka id:llä: " urakka-id)
  (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id luoja urakka-id))))

;; Talvihoitoluokat niille kevyen liikenteen väylille, joita ei suolata, eli K1, K2 ja K (Ei talvihoitoa)
(def kelvien-talvihoitoluokat [9 10 11])

(defn pisteen-hoitoluokat [db piste tehtavat materiaalit]
  (let [suolausta? (when (or (seq tehtavat) (seq materiaalit))
                     (onko-toteumalla-suolausta db {:materiaalit materiaalit :tehtavat tehtavat}))]
    (first (hae-pisteen-hoitoluokat db (assoc piste :kielletyt_hoitoluokat (when suolausta? kelvien-talvihoitoluokat))))))

(defn tallenna-toteuman-reittipisteet! [db toteuman-reittipisteet]
  (upsert! db ::rp/toteuman-reittipisteet
           toteuman-reittipisteet))

(defn poista-reittipiste-toteuma-idlla! [db toteuma-id]
  (delete! db ::rp/toteuman-reittipisteet
           {::rp/toteuma-id toteuma-id}))

(defn hae-uusimmat-varustetoteuma-ulkoiset
  [db {:keys [urakka-id hoitokauden-alkuvuosi hoitovuoden-kuukausi tie aosa aeta losa leta kuntoluokat tietolajit toteuma]}]
  (let [hoitokauden-alkupvm (pvm/luo-pvm-dec-kk hoitokauden-alkuvuosi 10 01)
        hoitokauden-loppupvm (pvm/luo-pvm-dec-kk (+ 1 hoitokauden-alkuvuosi) 9 30)
        toteumat (hae-urakan-uusimmat-varustetoteuma-ulkoiset db {:urakka urakka-id
                                                                  :hoitokauden_alkupvm (konv/sql-date hoitokauden-alkupvm)
                                                                  :hoitokauden_loppupvm (konv/sql-date hoitokauden-loppupvm)
                                                                  :kuukausi hoitovuoden-kuukausi
                                                                  :tie tie
                                                                  :aosa aosa
                                                                  :aeta aeta
                                                                  :losa losa
                                                                  :leta leta
                                                                  :tietolajit (or tietolajit [])
                                                                  :kuntoluokat (or kuntoluokat [])
                                                                  :toteuma toteuma})
        toteumat-clj-sijainneilla (map #(update % :sijainti geo/pg->clj) toteumat)]
    toteumat-clj-sijainneilla))

;; Partitiointimuutoksen jälkeen toteumataulusta pitää hakea uusin id aina INSERT:n
;; jälkeen. Käytetään tätä funktiota sovelluksen puolella, API-puolella on omansa.
(defn luo-uusi-toteuma
  "Luo uuden toteuman ja palauttaa sen id:n"
  [db toteuma]
  (do
    (luo-toteuma<! db toteuma)
    (luodun-toteuman-id db)))