(ns harja.kyselyt.paikkaus
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch update! insert! upsert! delete!]]
            [specql.op :as op]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.pvm :as pvm]
            [harja.kyselyt.tieverkko :as q-tr]
            [harja.id :refer [id-olemassa?]]
            [taoensso.timbre :as log]))

(def merkitse-paikkauskohde-tarkistetuksi!
  "Päivittää paikkauskohteen tarkistaja-idn ja aikaleiman."
)

(defqueries "harja/kyselyt/paikkaus.sql"
            {:positional? true})

(defn hae-paikkaukset [db hakuehdot]
  (fetch db
         ::paikkaus/paikkaus
         paikkaus/paikkauksen-perustiedot
         hakuehdot))

(defn hae-paikkaukset-materiaalit [db hakuehdot]
  (fetch db
         ::paikkaus/paikkaus
         (conj paikkaus/paikkauksen-perustiedot
               [::paikkaus/materiaalit paikkaus/materiaalit-perustiedot])
         hakuehdot))

(defn hae-paikkaukset-paikkauskohde [db hakuehdot]
  (fetch db
         ::paikkaus/paikkaus
         (conj paikkaus/paikkauksen-perustiedot
               [::paikkaus/paikkauskohde (conj paikkaus/paikkauskohteen-perustiedot
                                               ::muokkaustiedot/luotu
                                               ::muokkaustiedot/muokattu)])
         hakuehdot))

(defn hae-paikkaukset-tienkohta [db hakuehdot]
  (fetch db
         ::paikkaus/paikkaus
         (conj paikkaus/paikkauksen-perustiedot
               [::paikkaus/tienkohdat paikkaus/tienkohta-perustiedot])
         hakuehdot))

(defn hae-paikkauksen-tienkohdat [db hakuehdot]
  (fetch db
         ::paikkaus/paikkauksen-tienkohta
         paikkaus/tienkohta-perustiedot
         hakuehdot))

(defn hae-paikkaustoteumat [db hakuehdot]
  (fetch db
         ::paikkaus/paikkaustoteuma
         paikkaus/paikkaustoteuman-perustiedot
         hakuehdot))

(defn hae-paikkauskohteet [db hakuehdot]
  (fetch db
         ::paikkaus/paikkauskohde
         (conj paikkaus/paikkauskohteen-perustiedot
               ::muokkaustiedot/luotu
               ::muokkaustiedot/muokattu)
         hakuehdot))

(defn onko-paikkaus-olemassa-ulkoisella-idlla? [db urakka-id ulkoinen-id luoja-id]
  (and
    (number? ulkoinen-id)
    (number? luoja-id)
    (not (empty? (hae-paikkaukset db {::paikkaus/ulkoinen-id ulkoinen-id
                                      ::paikkaus/urakka-id urakka-id
                                      ::muokkaustiedot/luoja-id luoja-id})))))

(defn onko-paikkaustoteuma-olemassa-ulkoisella-idlla? [db urakka-id ulkoinen-id luoja-id]
  (and
    (number? ulkoinen-id)
    (number? luoja-id)
    (not (empty? (hae-paikkaustoteumat db {::paikkaus/ulkoinen-id ulkoinen-id
                                           ::paikkaus/urakka-id urakka-id
                                           ::muokkaustiedot/luoja-id luoja-id})))))

(defn onko-kohde-olemassa-ulkoisella-idlla? [db urakka-id ulkoinen-id luoja-id]
  (and
    (number? ulkoinen-id)
    (number? luoja-id)
    (not (empty? (hae-paikkauskohteet db {::paikkaus/ulkoinen-id ulkoinen-id
                                          ::paikkaus/urakka-id urakka-id
                                          ::muokkaustiedot/luoja-id luoja-id})))))

(defn paivita-paikkaukset-poistetuksi
  "Poistaa paikkaukset tietokannasta, jos ulkoinen-id, urakka-id ja käyttäjä täsmäävät."
  [db kayttaja-id urakka-id paikkaus-idt]
  (update! db ::paikkaus/paikkaus
           {::muokkaustiedot/poistettu? true
            ::muokkaustiedot/muokkaaja-id kayttaja-id
            ::muokkaustiedot/muokattu (pvm/nyt)}
           {::muokkaustiedot/luoja-id kayttaja-id
            ::paikkaus/urakka-id urakka-id
            ::paikkaus/ulkoinen-id (op/in (into #{} paikkaus-idt))}))

(defn paivita-paikauskohteiden-paikkaukset-poistetuksi
  "Poistaa paikkaukset tietokannasta, jos ulkoinen-id, urakka-id, käyttäjä ja sisäinen paikkauskohde-id täsmäävät."
  [db kayttaja-id urakka-id paikkauskohde-idt]
  (update! db ::paikkaus/paikkaus
           {::muokkaustiedot/poistettu? true
            ::muokkaustiedot/muokkaaja-id kayttaja-id
            ::muokkaustiedot/muokattu (pvm/nyt)}
           {::muokkaustiedot/luoja-id kayttaja-id
            ::paikkaus/urakka-id urakka-id
            ::paikkaus/paikkauskohde-id (op/in (into #{} paikkauskohde-idt))}))

(defn paivita-paikkaustoteumat-poistetuksi
  "Poistaa paikkauskustannukset tietokannasta, jos ulkoinen-id, urakka-id ja käyttäjä täsmäävät."
  [db kayttaja-id urakka-id paikkaustoteuma-idt]
  (update! db ::paikkaus/paikkaustoteuma
           {::muokkaustiedot/poistettu? true
            ::muokkaustiedot/muokkaaja-id kayttaja-id
            ::muokkaustiedot/muokattu (pvm/nyt)}
           {::muokkaustiedot/luoja-id kayttaja-id
            ::paikkaus/urakka-id urakka-id
            ::paikkaus/ulkoinen-id (op/in (into #{} paikkaustoteuma-idt))}))

(defn paivita-paikauskohteiden-toteumat-poistetuksi
  "Poistaa paikkaukustannukset tietokannasta, jos ulkoinen-id, urakka-id, käyttäjä ja sisäinen paikkauskohde-id täsmäävät."
  [db kayttaja-id urakka-id paikkauskohde-idt]
  (update! db ::paikkaus/paikkaustoteuma
           {::muokkaustiedot/poistettu? true
            ::muokkaustiedot/muokkaaja-id kayttaja-id
            ::muokkaustiedot/muokattu (pvm/nyt)}
           {::muokkaustiedot/luoja-id kayttaja-id
            ::paikkaus/urakka-id urakka-id
            ::paikkaus/paikkauskohde-id (op/in (into #{} paikkauskohde-idt))}))

(defn paivita-paikkauskohteet-poistetuksi
  "Poistaa paikkauskohteet sekä niihin liittyvät paikkaukset ja paikkauskustannukset tietokannasta, jos ulkoinen-id, urakka-id ja käyttäjä täsmäävät."
  [db kayttaja-id urakka-id paikkauskohde-idt]
  (let [sisaiset-idt (fetch db ::paikkaus/paikkauskohde
                            #{::paikkaus/id}
                            {::muokkaustiedot/luoja-id kayttaja-id
                             ::paikkaus/urakka-id urakka-id
                             ::paikkaus/ulkoinen-id (op/in (into #{} paikkauskohde-idt))})]
    (paivita-paikauskohteiden-paikkaukset-poistetuksi db kayttaja-id urakka-id (map ::paikkaus/id sisaiset-idt))
    (paivita-paikauskohteiden-toteumat-poistetuksi db kayttaja-id urakka-id (map ::paikkaus/id sisaiset-idt)))
  (update! db ::paikkaus/paikkauskohde
           {::muokkaustiedot/poistettu? true
            ::muokkaustiedot/muokkaaja-id kayttaja-id
            ::muokkaustiedot/muokattu (pvm/nyt)}
           {::muokkaustiedot/luoja-id kayttaja-id
            ::paikkaus/urakka-id urakka-id
            ::paikkaus/ulkoinen-id (op/in (into #{} paikkauskohde-idt))}))

(defn paivita-paikkauskohteen-tila
  "Päivittää paikkauskohteen tilan harjan sisäisen id:n perusteella (lähetetty, virhe)."
    [db paikkauskohde]
    (let [id (::paikkaus/id paikkauskohde)
          ehdot (if (id-olemassa? id)
                  {::paikkaus/id id})]
      (update! db ::paikkaus/paikkauskohde paikkauskohde ehdot)
      (first (hae-paikkaukset db ehdot))))

(defn- paivita-paikkaus
  "Päivittää paikkauksen tiedot, jos ulkoinen-id, urakka-id ja käyttäjä täsmäävät."
  [db urakka-id paikkaus]
  (let [id (::paikkaus/id paikkaus)
        luoja-id (::muokkaustiedot/luoja-id paikkaus)
        ulkoinen-id (::paikkaus/ulkoinen-id paikkaus)
        ehdot (if (id-olemassa? id)
                {::paikkaus/id id}
                {::paikkaus/ulkoinen-id ulkoinen-id
                 ::paikkaus/urakka-id urakka-id
                 ::muokkaustiedot/luoja-id luoja-id})]
    (update! db ::paikkaus/paikkaus paikkaus ehdot)
    (first (hae-paikkaukset db ehdot))))

(defn- paivita-paikkaustoteuma [db urakka-id paikkaustoteuma]
  "Päivittää paikkauskustannuksen tiedot, jos ulkoinen-id, urakka-id ja käyttäjä täsmäävät."
  (let [id (::paikkaus/id paikkaustoteuma)
        luoja-id (::muokkaustiedot/luoja-id paikkaustoteuma)
        ulkoinen-id (::paikkaus/ulkoinen-id paikkaustoteuma)
        ehdot (if (id-olemassa? id)
                {::paikkaus/id id}
                {::paikkaus/ulkoinen-id ulkoinen-id
                 ::paikkaus/urakka-id urakka-id
                 ::muokkaustiedot/luoja-id luoja-id})]
    (update! db ::paikkaus/paikkaustoteuma paikkaustoteuma ehdot)
    (first (hae-paikkaustoteumat db ehdot))))

(defn- paivita-paikkauskohde
  "Päivittää paikkauskohteen tiedot, jos ulkoinen-id, urakka-id ja käyttäjä täsmäävät."
  [db urakka-id paikkauskohde]
  (let [id (::paikkaus/id paikkauskohde)
        luoja-id (::muokkaustiedot/luoja-id paikkauskohde)
        ulkoinen-id (::paikkaus/ulkoinen-id paikkauskohde)
        ehdot (if (id-olemassa? id)
                {::paikkaus/id id}
                {::paikkaus/ulkoinen-id ulkoinen-id
                 ::paikkaus/urakka-id urakka-id
                 ::muokkaustiedot/luoja-id luoja-id})]
    (update! db ::paikkaus/paikkausohde paikkauskohde ehdot)
    (first (hae-paikkaukset db ehdot))))

(defn- luo-paikkaus
  "Tallentaa tietokantaan uuden paikkauksen."
  [db paikkaus]
  (insert! db ::paikkaus/paikkaus paikkaus))

(defn luo-paikkaustoteuma
  "Tallentaa tietokantaan uuden paikkauskustannuksen."
  [db toteuma]
  (insert! db ::paikkaus/paikkaustoteuma toteuma))

(defn- tallenna-materiaalit
  "Tallentaa paikkauksen materiaalit. Päivitys tapahtuu poistamalla ensin kaikki paikkauksen materiaalit ja tallentamalla sitten kaikki materiaalit uudelleen."
  [db toteuma-id materiaalit]
  (delete! db ::paikkaus/paikkauksen_materiaali {::paikkaus/paikkaus-id toteuma-id})
  (doseq [materiaali materiaalit]
    (insert! db ::paikkaus/paikkauksen_materiaali (assoc materiaali ::paikkaus/paikkaus-id toteuma-id))))

(defn tallenna-tienkohdat
  "Tallentaa paikkauksen tienkohdat. Päivitys tapahtuu poistamalla ensin kaikki paikkauksen materiaalit ja tallentamalla sitten kaikki materiaalit uudelleen."
  [db toteuma-id tienkohdat]
  (delete! db ::paikkaus/paikkauksen-tienkohta {::paikkaus/paikkaus-id toteuma-id})
  (doseq [tienkohta tienkohdat]
    (insert! db ::paikkaus/paikkauksen-tienkohta (assoc tienkohta ::paikkaus/paikkaus-id toteuma-id))))

(defn tallenna-paikkauskohde
  "Käsittelee paikkauskohteen. Päivittää olemassa olevan tai lisää uuden."
  [db urakka-id kayttaja-id kohde]
  (let [id (::paikkaus/id kohde)
        ulkoinen-tunniste (::paikkaus/ulkoinen-id kohde)
        ;; nollataan mahdollinen ilmoitettu virhe
        kohde (assoc kohde ::paikkaus/ilmoitettu-virhe nil)]
    (if (id-olemassa? id)
      (update! db ::paikkaus/paikkauskohde kohde {::paikkaus/id id})
      (if (onko-kohde-olemassa-ulkoisella-idlla? db urakka-id ulkoinen-tunniste kayttaja-id)
        (update! db ::paikkaus/paikkauskohde
                    (assoc kohde ::muokkaustiedot/poistettu? false
                                 ::muokkaustiedot/muokkaaja-id kayttaja-id
                                 ::muokkaustiedot/muokattu (pvm/nyt))
                    {::paikkaus/urakka-id urakka-id
                     ::paikkaus/ulkoinen-id ulkoinen-tunniste
                     ::muokkaustiedot/luoja-id kayttaja-id})
        (insert! db ::paikkaus/paikkauskohde
                 (assoc kohde ::paikkaus/urakka-id urakka-id
                              ::muokkaustiedot/luoja-id kayttaja-id
                              ::muokkaustiedot/luotu (pvm/nyt)))))
    (first (hae-paikkauskohteet db {::paikkaus/ulkoinen-id ulkoinen-tunniste
                                    ::paikkaus/urakka-id urakka-id}))))

(defn hae-tai-tee-paikkauskohde [db urakka-id kayttaja-id paikkauskohde]
  (when-let [ulkoinen-id (::paikkaus/ulkoinen-id paikkauskohde)]
    (or (::paikkaus/id paikkauskohde)
        (::paikkaus/id (hae-paikkauskohteet db {::paikkaus/urakka-id urakka-id
                                                ::paikkaus/ulkoinen-id ulkoinen-id
                                                ::muokkaustiedot/luoja-id kayttaja-id}))
        (::paikkaus/id (tallenna-paikkauskohde db urakka-id kayttaja-id paikkauskohde)))))

(defn poista-paikkaustoteuma [db kayttaja-id urakka-id ulkoinen-id]
  (delete! db ::paikkaus/paikkaustoteuma {::muokkaustiedot/luoja-id kayttaja-id
                                          ::paikkaus/urakka-id urakka-id
                                          ::paikkaus/ulkoinen-id ulkoinen-id}))

(defn tallenna-paikkaus [db urakka-id kayttaja-id paikkaus]
  (let [id (::paikkaus/id paikkaus)
        ulkoinen-id (::paikkaus/ulkoinen-id paikkaus)
        paikkauskohde-id (::paikkaus/id (tallenna-paikkauskohde db urakka-id kayttaja-id (::paikkaus/paikkauskohde paikkaus)))
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
                                               ::muokkaustiedot/muokattu (pvm/nyt)
                                               ::muokkaustiedot/poistettu? false)
        paivita? (or (id-olemassa? id) (onko-paikkaus-olemassa-ulkoisella-idlla? db urakka-id ulkoinen-id kayttaja-id))
        id (::paikkaus/id (if paivita?
                            (paivita-paikkaus db urakka-id muokattu-paikkaus)
                            (luo-paikkaus db uusi-paikkaus)))]
    (tallenna-materiaalit db id materiaalit)
    (tallenna-tienkohdat db id tienkohdat)))

(defn tallenna-paikkaustoteuma
  "Tallentaa paikkauskustannuksiin liittyvän yksittäisen rivin tiedot."
  [db urakka-id kayttaja-id toteuma]
  (let [ulkoinen-id (::paikkaus/ulkoinen-id toteuma)
        paikkauskohde-id (::paikkaus/id (tallenna-paikkauskohde db urakka-id kayttaja-id (::paikkaus/paikkauskohde toteuma)))
        tallennettava-toteuma (dissoc (assoc toteuma ::paikkaus/paikkauskohde-id paikkauskohde-id
                                            ::muokkaustiedot/luoja-id kayttaja-id)
                             ::paikkaus/materiaalit
                             ::paikkaus/tienkohdat
                             ::paikkaus/paikkauskohde)]
        (luo-paikkaustoteuma db tallennettava-toteuma)))

(defn hae-urakan-paikkaukset [db urakka-id]
  (hae-paikkaukset db {::paikkaus/urakka-id urakka-id
                       ::muokkaustiedot/poistettu? false}))

(defn hae-urakan-paikkauskohteet [db urakka-id]
  (let [paikkauskohteet (fetch db
                               ::paikkaus/paikkauskohde
                               (conj paikkaus/paikkauskohteen-perustiedot
                                     ::muokkaustiedot/muokattu
                                     ::muokkaustiedot/luotu
                                     [::paikkaus/paikkaukset #{::paikkaus/urakka-id}])
                               {::paikkaus/paikkaukset {::paikkaus/urakka-id urakka-id}})
        paikkauskohteet (into []
                              (comp
                                (map #(assoc % ::paikkaus/tierekisteriosoite
                                               (first (hae-paikkauskohteen-tierekisteriosoite db {:kohde (::paikkaus/id %)}))))
                                (map #(dissoc % ::paikkaus/paikkaukset)))
                              paikkauskohteet)]
paikkauskohteet))

(defn hae-urakan-tyomenetelmat [db urakka-id]
  (let [paikkauksien-tyomenetelmat (fetch db
                                          ::paikkaus/paikkaus
                                          #{::paikkaus/tyomenetelma}
                                          {::paikkaus/urakka-id urakka-id})]
    (into #{} (distinct (map ::paikkaus/tyomenetelma paikkauksien-tyomenetelmat)))))

