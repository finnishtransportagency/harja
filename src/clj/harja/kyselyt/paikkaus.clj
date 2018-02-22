(ns harja.kyselyt.paikkaus
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch update! insert! upsert! delete!]]
            [harja.domain.paikkaus :as paikkaus]
            [harja.pvm :as pvm]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.id :refer [id-olemassa?]]))

(defqueries "harja/kyselyt/paikkaus.sql"
            {:positional? true})

(defn hae-paikkaukset [db hakuehdot]
  (fetch db
         ::paikkaus/paikkaus
         paikkaus/paikkauksen-perustiedot
         hakuehdot))

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

(defn onko-paikkaus-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja-id]
  (and
    (number? ulkoinen-id)
    (number? luoja-id)
    (not (empty? (hae-paikkaukset db {::paikkaus/ulkoinen-id ulkoinen-id
                                      ::muokkaustiedot/luoja-id luoja-id})))))

(defn onko-paikkaustoteuma-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja-id]
  (and
    (number? ulkoinen-id)
    (number? luoja-id)
    (not (empty? (hae-paikkaustoteumat db {::paikkaus/ulkoinen-id ulkoinen-id
                                           ::muokkaustiedot/luoja-id luoja-id})))))

(defn onko-kohde-olemassa-ulkoisella-idlla? [db ulkoinen-id]
  (and
    (number? ulkoinen-id)
    (not (empty? (hae-paikkauskohteet db {::paikkaus/ulkoinen-id ulkoinen-id})))))

(defn tallenna-paikkauskohde [db kayttaja-id kohde]
  (let [id (::paikkaus/id kohde)
        ulkoinen-tunniste (::paikkaus/ulkoinen-id kohde)]
    (if (id-olemassa? id)
      (update! db ::paikkaus/paikkauskohde kohde {::paikkaus/id id})
      (if (onko-kohde-olemassa-ulkoisella-idlla? db ulkoinen-tunniste)
        (update! db ::paikkaus/paikkauskohde kohde {::paikkaus/ulkoinen-id ulkoinen-tunniste})
        (insert! db ::paikkaus/paikkauskohde (assoc kohde ::muokkaustiedot/luoja-id kayttaja-id))))
    (first (hae-paikkauskohteet db {::paikkaus/ulkoinen-id ulkoinen-tunniste}))))

(defn hae-tai-tee-paikkauskohde [db kayttaja-id paikkauskohde]
  (when-let [ulkoinen-id (::paikkaus/ulkoinen-id paikkauskohde)]
    (or (::paikkaus/id paikkauskohde)
        (::paikkaus/id (hae-paikkauskohteet db {::paikkaus/ulkoinen-id ulkoinen-id}))
        (::paikkaus/id (tallenna-paikkauskohde db kayttaja-id paikkauskohde)))))

(defn- paivita-paikkaus [db paikkaus]
  (let [id (::paikkaus/id paikkaus)
        luoja-id (::muokkaustiedot/luoja-id paikkaus)
        ulkoinen-id (::paikkaus/ulkoinen-id paikkaus)
        ehdot (if (id-olemassa? id)
                {::paikkaus/id id}
                {::paikkaus/ulkoinen-id ulkoinen-id
                 ::muokkaustiedot/luoja-id luoja-id})]
    (update! db ::paikkaus/paikkaus paikkaus ehdot)
    (first (hae-paikkaukset db ehdot))))

(defn- luo-paikkaus [db paikkaus]
  (insert! db ::paikkaus/paikkaus paikkaus))

(defn paivita-paikkaustoteuma [db toteuma]
  (let [id (::paikkaus/id toteuma)
        luoja-id (::muokkaustiedot/luoja-id toteuma)
        ulkoinen-id (::paikkaus/ulkoinen-id toteuma)
        ehdot (if (id-olemassa? id)
                {::paikkaus/id id}
                {::paikkaus/ulkoinen-id ulkoinen-id
                 ::muokkaustiedot/luoja-id luoja-id})]
    (update! db ::paikkaus/paikkaustoteuma toteuma ehdot)
    (first (hae-paikkaustoteumat db ehdot))))

(defn luo-paikkaustoteuma [db toteuma]
  (insert! db ::paikkaus/paikkaustoteuma toteuma))

(defn- tallenna-materiaalit [db toteuma-id materiaalit]
  (delete! db ::paikkaus/paikkauksen_materiaali {::paikkaus/paikkaus-id toteuma-id})
  (doseq [materiaali materiaalit]
    (insert! db ::paikkaus/paikkauksen_materiaali (assoc materiaali ::paikkaus/paikkaus-id toteuma-id))))

(defn tallenna-tienkohdat [db toteuma-id tienkohdat]
  (delete! db ::paikkaus/paikkauksen-tienkohta {::paikkaus/paikkaus-id toteuma-id})
  (doseq [tienkohta tienkohdat]
    (insert! db ::paikkaus/paikkauksen-tienkohta (assoc tienkohta ::paikkaus/paikkaus-id toteuma-id))))

(defn tallenna-paikkaus [db kayttaja-id paikkaus]
  (let [id (::paikkaus/id paikkaus)
        ulkoinen-id (::paikkaus/ulkoinen-id paikkaus)
        paikkauskohde-id (hae-tai-tee-paikkauskohde db kayttaja-id (::paikkaus/paikkauskohde paikkaus))
        materiaalit (::paikkaus/materiaalit paikkaus)
        tienkohdat (::paikkaus/tienkohdat paikkaus)
        uusi-paikkaus (dissoc (assoc paikkaus ::paikkaus/paikkauskohde-id paikkauskohde-id
                                              ::muokkaustiedot/luoja-id kayttaja-id)
                              ::paikkaus/materiaalit
                              ::paikkaus/tienkohdat
                              ::paikkaus/paikkauskohde)
        muokattu-paikkaus (assoc uusi-paikkaus ::muokkaustiedot/muokkaaja-id kayttaja-id
                                               ::muokkaustiedot/muokattu (pvm/nyt))
        paivita? (or (id-olemassa? id) (onko-paikkaus-olemassa-ulkoisella-idlla? db ulkoinen-id kayttaja-id))
        id (::paikkaus/id (if paivita?
                            (paivita-paikkaus db muokattu-paikkaus)
                            (luo-paikkaus db uusi-paikkaus)))]
    (tallenna-materiaalit db id materiaalit)
    (tallenna-tienkohdat db id tienkohdat)))

(defn tallenna-paikkaustoteuma [db kayttaja-id toteuma]
  (let [id (::paikkaus/id toteuma)
        ulkoinen-id (::paikkaus/ulkoinen-id toteuma)
        paikkauskohde-id (hae-tai-tee-paikkauskohde db kayttaja-id (::paikkaus/paikkauskohde toteuma))
        uusi-toteuma (dissoc (assoc toteuma ::paikkaus/paikkauskohde-id paikkauskohde-id
                                            ::muokkaustiedot/luoja-id kayttaja-id)
                             ::paikkaus/materiaalit
                             ::paikkaus/tienkohdat
                             ::paikkaus/paikkauskohde)
        muokattu-toteuma (assoc uusi-toteuma ::muokkaustiedot/muokkaaja-id kayttaja-id
                                             ::muokkaustiedot/muokattu (pvm/nyt))
        paivita? (or (id-olemassa? id) (onko-paikkaustoteuma-olemassa-ulkoisella-idlla? db ulkoinen-id kayttaja-id))]

    (println "--->> " paivita?)
    (::paikkaus/id (if paivita?
                     (paivita-paikkaustoteuma db muokattu-toteuma)
                     (luo-paikkaustoteuma db uusi-toteuma)))))

(defn hae-urakan-paikkaukset [db urakka-id]
  (hae-paikkaukset db {::paikkaus/urakka-id urakka-id}))

(defn hae-urakan-paikkaustoteumat [db urakka-id]
  (hae-paikkaustoteumat db {::paikkaus/urakka-id urakka-id}))

