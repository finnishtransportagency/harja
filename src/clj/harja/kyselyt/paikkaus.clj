(ns harja.kyselyt.paikkaus
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch update! insert! upsert! delete!]]
            [harja.domain.paikkaus :as paikkaus]
            [harja.pvm :as pvm]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.id :refer [id-olemassa?]]))

(defqueries "harja/kyselyt/paikkaus.sql"
            {:positional? true})

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

(defn onko-toteuma-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja-id]
  (and
    (number? ulkoinen-id)
    (number? luoja-id)
    (not (empty? (hae-paikkaustoteumat db {::paikkaus/ulkoinen-id ulkoinen-id
                                           ::muokkaustiedot/luoja-id luoja-id})))))

(defn onko-kohde-olemassa-ulkoisella-idlla? [db ulkoinen-id]
  (and
    (number? ulkoinen-id)
    (not (empty? (hae-paikkauskohteet db {::paikkaus/ulkoinen-id ulkoinen-id})))))

(defn hae-urakan-paikkaustoteumat [db urakka-id]
  (first (hae-paikkaustoteumat db {::paikkaus/urakka-id urakka-id})))

(defn tallenna-paikkauskohde [db kohde]
  (let [id (::paikkaus/id kohde)
        ulkoinen-tunniste (::paikkaus/ulkoinen-id kohde)]
    (if (id-olemassa? id)
      (update! db ::paikkaus/paikkauskohde kohde {::paikkaus/id id})
      (if (onko-kohde-olemassa-ulkoisella-idlla? db ulkoinen-tunniste)
        (update! db ::paikkaus/paikkauskohde kohde {::paikkaus/ulkoinen-id ulkoinen-tunniste})
        (insert! db ::paikkaus/paikkauskohde kohde)))
    (first (hae-paikkauskohteet db {::paikkaus/ulkoinen-id ulkoinen-tunniste}))))

(defn hae-tai-tee-paikkauskohde [db paikkauskohde]
  (when-let [ulkoinen-id (::paikkaus/ulkoinen-id paikkauskohde)]
    (or (::paikkaus/id paikkauskohde)
        (::paikkaus/id (hae-paikkauskohteet db {::paikkaus/ulkoinen-id ulkoinen-id}))
        (::paikkaus/id (tallenna-paikkauskohde db paikkauskohde)))))

(defn- paivita-toteuma [db toteuma]
  (let [id (::paikkaus/id toteuma)
        luoja-id (::muokkaustiedot/luoja-id toteuma)
        ulkoinen-id (::paikkaus/ulkoinen-id toteuma)
        ehdot (if (id-olemassa? id)
                {::paikkaus/id id}
                {::paikkaus/ulkoinen-id ulkoinen-id
                 ::paikkaus/luoja-id luoja-id})]
    (update! db ::paikkaus/paikkaustoteuma toteuma ehdot)
    toteuma))

(defn- luo-toteuma [db toteuma]
  (insert! db ::paikkaus/paikkaustoteuma toteuma))

(defn- tallenna-materiaalit [db toteuma-id materiaalit]
  (delete! db ::paikkaus/paikkauksen-materiaalit {::paikkaus/paikkaustoteuma-id toteuma-id})
  (doseq [materiaali materiaalit]
    (insert! db ::paikkaus/paikkauksen-materiaalit (assoc materiaali ::paikkaus/paikkaustoteuma-id toteuma-id))))

(defn tallenna-tienkohdat [db toteuma-id tienkohdat]
  (delete! db ::paikkaus/paikkauksen-tienkohta {::paikkaus/paikkaustoteuma-id toteuma-id})
  (doseq [tienkohta tienkohdat]
    (insert! db ::paikkaus/paikkauksen-tienkohta (assoc tienkohta ::paikkaus/paikkaustoteuma-id toteuma-id))))

(defn tallenna-paikkaustoteuma [db toteuma]
  (let [id (::paikkaus/id toteuma)
        luoja-id (::muokkaustiedot/luoja-id toteuma)
        ulkoinen-id (::paikkaus/ulkoinen-id toteuma)
        paikkauskohde-id (hae-tai-tee-paikkauskohde db (::paikkaus/paikkauskohde toteuma))
        materiaalit (::paikkaus/materiaalit toteuma)
        tienkohdat (::paikkaus/tienkohdat toteuma)
        toteuma (dissoc (assoc toteuma ::paikkaus/paikkauskohde paikkauskohde-id)
                        ::paikkaus/materiaalit
                        ::paikkaus/tienkohdat
                        ::paikkaus/paikkauskohde)
        id (::paikkaus/id (if (or (id-olemassa? id) (onko-toteuma-olemassa-ulkoisella-idlla? db ulkoinen-id luoja-id))
                            (paivita-toteuma db (assoc toteuma ::muokkaustiedot/muokattu (pvm/nyt)))
                            (luo-toteuma db (assoc toteuma ::muokkaustiedot/luotu (pvm/nyt)))))]

    (tallenna-materiaalit db id materiaalit)
    (tallenna-tienkohdat db id tienkohdat)))
