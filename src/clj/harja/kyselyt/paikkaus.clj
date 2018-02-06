(ns harja.kyselyt.paikkaus
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch update! insert! upsert!]]
            [harja.domain.paikkaus :as paikkaus]
            [harja.pvm :as pvm]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.id :refer [id-olemassa?]]))

(defqueries "harja/kyselyt/paikkaus.sql"
  {:positional? true})

(defn onko-olemassa-paikkausilmioitus? [db yllapitokohde-id]
  (:exists (first (harja.kyselyt.paikkaus/yllapitokohteella-paikkausilmoitus
                    db
                    {:yllapitokohde yllapitokohde-id}))))

(defn hae-paikkaustoteumat [db hakuehdot]
  (fetch db
         ::paikkaus/paikkaustoteuma
         paikkaus/perustiedot
         hakuehdot))

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id]
  (and
    (number? ulkoinen-id)
    (not (empty? (hae-paikkaustoteumat db {::paikkaus/ulkoinen-id ulkoinen-id})))))

(defn hae-urakan-paikkaustoteumat [db urakka-id]
  (first (hae-paikkaustoteumat db {::paikkaus/urakka-id urakka-id})))

(defn tallenna-paikkaustoteuma [db toteuma]
  (let [id (::paikkaus/id toteuma)
        ulkoinen-tunniste (::paikkaus/ulkoinen-id toteuma)
        uusi (assoc toteuma ::muokkaustiedot/luotu (pvm/nyt))
        muokattu (assoc toteuma ::muokkaustiedot/muokattu (pvm/nyt))]
    (if (id-olemassa? id)
      (update! db ::paikkaus/paikkaustoteuma muokattu {::paikkaus/id id})
      (if (onko-olemassa-ulkoisella-idlla? db ulkoinen-tunniste)
        (update! db ::paikkaus/paikkaustoteuma muokattu {::paikkaus/ulkoinen-id ulkoinen-tunniste})
        (insert! db ::paikkaus/paikkaustoteuma uusi)))))
