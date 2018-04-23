(ns harja.kyselyt.paikkaus
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch update! insert! upsert! delete!]]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.pvm :as pvm]
            [harja.kyselyt.tieverkko :as q-tr]
            [harja.id :refer [id-olemassa?]]))

(defqueries "harja/kyselyt/paikkaus.sql"
            {:positional? true})

(defn hae-paikkaukset [db hakuehdot]
  (let [paikkaus-materiaalit (fetch db
                                    ::paikkaus/paikkaus
                                    (conj paikkaus/paikkauksen-perustiedot
                                          [::paikkaus/materiaalit paikkaus/materiaalit-perustiedot])
                                    (dissoc hakuehdot ::paikkaus/paikkauskohde ::paikkaus/tienkohdat))
        paikkaus-paikkauskohde (fetch db
                                      ::paikkaus/paikkaus
                                      (conj paikkaus/paikkauksen-perustiedot
                                            [::paikkaus/paikkauskohde paikkaus/paikkauskohteen-perustiedot])
                                      (dissoc hakuehdot ::paikkaus/tienkohdat ::paikkaus/materiaalit))
        paikkaus-tienkohta (fetch db
                                  ::paikkaus/paikkaus
                                  (conj paikkaus/paikkauksen-perustiedot
                                        [::paikkaus/tienkohdat paikkaus/tienkohta-perustiedot])
                                  (dissoc hakuehdot ::paikkaus/paikkauskohde ::paikkaus/materiaalit))
        yhdistetty (reduce (fn [kayty paikkaus]
                             ;; jos kaikista hauista löytyy kyseinen id, se kuuluu silloin palauttaa
                             (let [materiaali (some #(when (= (::paikkaus/id paikkaus) (::paikkaus/id %))
                                                       %)
                                                    paikkaus-materiaalit)
                                   paikkauskohde (some #(when (= (::paikkaus/id paikkaus) (::paikkaus/id %))
                                                          %)
                                                       paikkaus-paikkauskohde)
                                   tienkohta (some #(when (= (::paikkaus/id paikkaus) (::paikkaus/id %))
                                                      %)
                                                   paikkaus-tienkohta)]
                               (if (and materiaali paikkauskohde tienkohta)
                                 (conj kayty (merge materiaali paikkauskohde tienkohta))
                                 kayty)))
                           [] (:paikkaukset (max-key :count
                                              {:count (count paikkaus-materiaalit)
                                               :paikkaukset paikkaus-materiaalit}
                                              {:count (count paikkaus-paikkauskohde)
                                               :paikkaukset paikkaus-paikkauskohde}
                                              {:count (count paikkaus-tienkohta)
                                               :paikkaukset paikkaus-tienkohta})))]
    yhdistetty))

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
        tr-osoite (::paikkaus/tierekisteriosoite paikkaus)
        sijainti (q-tr/tierekisteriosoite-viivaksi db {:tie (::tierekisteri/tie tr-osoite) :aosa (::tierekisteri/aosa tr-osoite)
                                                       :aet (::tierekisteri/aet tr-osoite) :losa (::tierekisteri/losa tr-osoite)
                                                       :loppuet (::tierekisteri/let tr-osoite)})
        uusi-paikkaus (dissoc (assoc paikkaus ::paikkaus/paikkauskohde-id paikkauskohde-id
                                              ::muokkaustiedot/luoja-id kayttaja-id
                                              ::paikkaus/sijainti sijainti)
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
  (let [paikkauskohde-id (hae-tai-tee-paikkauskohde db kayttaja-id (::paikkaus/paikkauskohde toteuma))
        uusi-toteuma (dissoc (assoc toteuma ::paikkaus/paikkauskohde-id paikkauskohde-id
                                            ::muokkaustiedot/luoja-id kayttaja-id)
                             ::paikkaus/materiaalit
                             ::paikkaus/tienkohdat
                             ::paikkaus/paikkauskohde)]
    (::paikkaus/id (luo-paikkaustoteuma db uusi-toteuma))))

(defn hae-urakan-paikkaukset [db urakka-id]
  (hae-paikkaukset db {::paikkaus/urakka-id urakka-id}))

(defn hae-urakan-paikkaustoteumat [db urakka-id]
  (hae-paikkaustoteumat db {::paikkaus/urakka-id urakka-id}))

(defn poista-paikkaustoteumat [db kayttaja-id ulkoinen-id]
  (delete! db ::paikkaus/paikkaustoteuma {::muokkaustiedot/luoja-id kayttaja-id
                                          ::paikkaus/ulkoinen-id ulkoinen-id}))

(defn hae-urakan-paikkauskohteet [db urakka-id]
  (let [paikkauskohteet (fetch db
                               ::paikkaus/paikkauskohde
                               (conj paikkaus/paikkauskohteen-perustiedot
                                     [::paikkaus/paikkaukset #{::paikkaus/urakka-id}])
                               {::paikkaus/paikkaukset {::paikkaus/urakka-id urakka-id}})]
    (mapv #(dissoc % ::paikkaus/paikkaukset) paikkauskohteet)))

