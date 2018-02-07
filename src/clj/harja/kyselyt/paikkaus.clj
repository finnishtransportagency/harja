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
         paikkaus/paikkaustoteuman-perustiedot
         hakuehdot))

(defn hae-paikkauskohteet [db hakuehdot]
  (fetch db
         ::paikkaus/paikkauskohde
         paikkaus/paikkauskohteen-perustiedot
         hakuehdot))

(defn onko-olemassa-ulkoisella-idlla? [db haku ulkoinen-id]
  (and
    (number? ulkoinen-id)
    (not (empty? (haku db {::paikkaus/ulkoinen-id ulkoinen-id})))))

(defn hae-urakan-paikkaustoteumat [db urakka-id]
  (first (hae-paikkaustoteumat db {::paikkaus/urakka-id urakka-id})))

(defn tallenna-paikkauskohde [db kohde]
  (let [id (::paikkaus/id kohde)
        ulkoinen-tunniste (::paikkaus/ulkoinen-id kohde)]
    (if (id-olemassa? id)
      (update! db ::paikkaus/paikkauskohde kohde {::paikkaus/id id})
      (if (onko-olemassa-ulkoisella-idlla? db hae-paikkauskohteet ulkoinen-tunniste)
        (update! db ::paikkaus/paikkauskohde kohde {::paikkaus/ulkoinen-id ulkoinen-tunniste})
        (insert! db ::paikkaus/paikkauskohde kohde)))
    (first (hae-paikkauskohteet db {::paikkaus/ulkoinen-id ulkoinen-tunniste}))))

(defn hae-tai-tee-paikkauskohde [db paikkauskohde]
  (when-let [ulkoinen-id (::paikkaus/ulkoinen-id paikkauskohde)]
    (or (::paikkaus/id paikkauskohde)
        (::paikkaus/id (hae-paikkauskohteet db {::paikkaus/ulkoinen-id ulkoinen-id}))
        (::paikkaus/id (tallenna-paikkauskohde db paikkauskohde)))))

(defn tallenna-paikkaustoteuma [db toteuma]
  (let [id (::paikkaus/id toteuma)
        ulkoinen-id (::paikkaus/ulkoinen-id toteuma)
        paikkauskohde-id (hae-tai-tee-paikkauskohde db (::paikkaus/paikkauskohde toteuma))
        materiaalit (::paikkaus/materiaalit toteuma)
        tienkohdat (::paikkaus/tienkohdat toteuma)
        toteuma (dissoc (assoc toteuma ::paikkaus/paikkauskohde paikkauskohde-id)
                        ::paikkaus/materiaalit
                        ::paikkaus/tienkohdat
                        ::paikkaus/paikkauskohde)
        uusi (assoc toteuma ::muokkaustiedot/luotu (pvm/nyt))
        muokattu (assoc toteuma ::muokkaustiedot/muokattu (pvm/nyt))]

    (if (id-olemassa? id)
      (update! db ::paikkaus/paikkaustoteuma muokattu {::paikkaus/id id})
      (if (onko-olemassa-ulkoisella-idlla? db hae-paikkaustoteumat ulkoinen-id)
        (update! db ::paikkaus/paikkaustoteuma muokattu {::paikkaus/ulkoinen-id ulkoinen-id})
        (insert! db ::paikkaus/paikkaustoteuma uusi)))

    (let [kohde-id (hae-paikkaustoteumat db {::paikkaus/ulkoinen-id ulkoinen-id})])

    ;; tallenna tienkohdat ja materiaalit
    )

  )
