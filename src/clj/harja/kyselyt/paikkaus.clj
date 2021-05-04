(ns harja.kyselyt.paikkaus
  (:require [clojure.set :as set]
            [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch update! insert! upsert! delete!]]
            [specql.op :as op]
            [harja.geo :as geo]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [clojure.string :as str]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.pvm :as pvm]
            [harja.kyselyt.tieverkko :as q-tr]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
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
               ::paikkaus/urakka-id
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

(defn onko-kohde-olemassa-nimella? [db nimi urakka-id]
  (fetch db
         ::paikkaus/paikkauskohde
         paikkaus/paikkauskohteen-perustiedot
         {::paikkaus/nimi nimi
          ::paikkaus/urakka-id urakka-id
          ::muokkaustiedot/poistettu? false}))

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

(defn poista-kasin-syotetty-paikkaus
  "Poistaa paikkaukset tietokannasta, jos ulkoinen-id, urakka-id ja käyttäjä täsmäävät."
  [db kayttaja-id urakka-id paikkaus-id]
  (update! db ::paikkaus/paikkaus
           {::muokkaustiedot/poistettu? true
            ::muokkaustiedot/muokkaaja-id kayttaja-id
            ::muokkaustiedot/muokattu (pvm/nyt)}
           {::muokkaustiedot/luoja-id kayttaja-id
            ::paikkaus/urakka-id urakka-id
            ::paikkaus/id paikkaus-id}))

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
  "Tallentaa paikkauksen tienkohdat.
  Päivitys tapahtuu poistamalla ensin kaikki paikkauksen tienkohdat ja tallentamalla sitten kaikki tienkohdat uudelleen."
  [db toteuma-id tienkohdat]
  (let [_ (println "tallenna-tienkohdat" (pr-str tienkohdat))]
    (delete! db ::paikkaus/paikkauksen-tienkohta {::paikkaus/paikkaus-id toteuma-id})
    (doseq [tienkohta tienkohdat]
      (insert! db ::paikkaus/paikkauksen-tienkohta (assoc tienkohta ::paikkaus/paikkaus-id toteuma-id)))))

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

(defn tallenna-paikkaus
  "APIa varten tehty paikkauksen tallennus. Olettaa saavansa ulkoisen id:n"
  [db urakka-id kayttaja-id paikkaus]
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

(def paikkaus->speqcl-avaimet
  {:id ::paikkaus/id
   :luotu ::paikkaus/luotu
   :urakka-id ::paikkaus/urakka-id
   :paikkauskohde-id ::paikkaus/paikkauskohde-id
   :ulkoinen-id ::paikkaus/ulkoinen-id
   :alkuaika ::paikkaus/alkuaika
   :loppuaika ::paikkaus/loppuaika
   :tierekisteriosoite ::paikkaus/tierekisteriosoite
   :tyomenetelma ::paikkaus/tyomenetelma
   :massatyyppi ::paikkaus/massatyyppi
   :leveys ::paikkaus/leveys
   :massamenekki ::paikkaus/massamenekki
   :raekoko ::paikkaus/raekoko
   :kuulamylly ::paikkaus/kuulamylly
   :massamaara ::paikkaus/massamaara
   :pinta-ala ::paikkaus/pinta-ala
   :sijainti ::paikkaus/sijainti})

(def speqcl-avaimet->paikkaus
  {::paikkaus/id :id
   ::paikkaus/luotu :luotu
   ::paikkaus/urakka-id :urakka-id
   ::paikkaus/paikkauskohde-id :paikkauskohde-id
   ::paikkaus/ulkoinen-id :ulkoinen-id
   ::paikkaus/alkuaika :alkuaika
   ::paikkaus/loppuaika :loppuaika
   ::paikkaus/tierekisteriosoite :tierekisteriosoite
   ::paikkaus/tyomenetelma :tyomenetelma
   ::paikkaus/massatyyppi :massatyyppi
   ::paikkaus/leveys :leveys
   ::paikkaus/massamenekki :massamenekki
   ::paikkaus/raekoko :raekoko
   ::paikkaus/kuulamylly :kuulamylly
   ::paikkaus/massamaara :massamaara
   ::paikkaus/pinta-ala :pinta-ala
   ::paikkaus/sijainti :sijainti })


(defn tallenna-kasinsyotetty-paikkaus
  "Olettaa saavansa paikkauksena mäpin, joka ei sisällä paikkaus domainin namespacea. Joten ne lisätään,
  jotta voidaan hyödyntää specql:n toimintaa."
  [db user paikkaus]
  ;; TODO: Tarkista käyttöoikeudet jotenkin
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-paikkauskohteet user (:urakka-id paikkaus))
  (let [_ (println "tallenna-kasinsyotetty-paikkaus" (pr-str (:urakka-id paikkaus)) (pr-str paikkaus))
        paikkaus-id (:id paikkaus)
        paikkauskohde-id (:paikkauskohde-id paikkaus)
        sijainti (q-tr/tierekisteriosoite-viivaksi db {:tie (:tie paikkaus) :aosa (:aosa paikkaus)
                                                       :aet (:aet paikkaus) :losa (:losa paikkaus)
                                                       :loppuet (:let paikkaus)})
        ;; Otetaan mahdollinen tienkohta talteen
        tienkohdat (when (and (paikkaus/levittimella-tehty? paikkaus)
                              (:kaista paikkaus))
                     {::paikkaus/ajorata (:ajorata paikkaus)
                      ::paikkaus/toteuma-id (:id paikkaus)
                      ::paikkaus/ajourat [(:kaista paikkaus)]})
        paikkaus (-> paikkaus
                     (assoc ::paikkaus/tierekisteriosoite {::tierekisteri/tie (:tie paikkaus)
                                                           ::tierekisteri/aosa (:aosa paikkaus)
                                                           ::tierekisteri/aet (:aet paikkaus)
                                                           ::tierekisteri/losa (:losa paikkaus)
                                                           ::tierekisteri/let (:let paikkaus)})
                     (dissoc :maara ;; TODO: tätä ei oikeasti saa poistaa, vaan pinta-alat yms pitää tallenta ajohonkin
                             :tie :aosa :aet :let :losa :ajorata :kaista)
                     (assoc :ulkoinen-id 0)
                     (assoc :massatyyppi ""))
        paikkaus (cond-> paikkaus
                         (not (nil? (:leveys paikkaus))) (update :leveys bigdec)
                         (not (nil? (:massamaara paikkaus))) (update :massamaara bigdec)
                         (not (nil? (:pinta-ala paikkaus))) (update :pinta-ala bigdec))
        paikkaus (set/rename-keys paikkaus paikkaus->speqcl-avaimet)

        uusi-paikkaus (assoc paikkaus ::paikkaus/paikkauskohde-id paikkauskohde-id
                                      ::muokkaustiedot/luoja-id (:id user)
                                      ::paikkaus/sijainti sijainti)
        muokattu-paikkaus (assoc uusi-paikkaus ::muokkaustiedot/muokkaaja-id (:id user)
                                               ::muokkaustiedot/muokattu (pvm/nyt)
                                               ::muokkaustiedot/poistettu? false)
        paikkaus (if paikkaus-id
                   (paivita-paikkaus db (:urakka-id paikkaus) muokattu-paikkaus)
                   (luo-paikkaus db uusi-paikkaus))
        ;; Onko tarvetta palauttaa paikkauksen tietoja tallennusvaiheessa? Muokataan siis kentät takaisin
        ;paikkaus (set/rename-keys paikkaus speqcl-avaimet->paikkaus)
        ;; Koitetaan tallentaa paikkauksen tienkohta. Se voidaan tehdä vain levittimellä tehdyille paikkauksille
        _ (if (and (paikkaus/levittimella-tehty? paikkaus)
                   tienkohdat)
            (tallenna-tienkohdat db (::paikkaus/id paikkaus) tienkohdat))
        ]
    paikkaus)
  )

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

(defn- hae-urakkatyyppi [db urakka-id]
  (keyword (:tyyppi (first (q-yllapitokohteet/hae-urakan-tyyppi db {:urakka urakka-id})))))

(defn laske-tien-osien-pituudet
  "Pätkitään funkkari osiin, jotta se on helpommin testattavissa. Tämä laskee siis
  tien pätkälle pituudet riippuen siitä, miten osan-pituudet listassa on annettu"
  [osan-pituudet kohde]
  ;; Pieni validointi kohteen arvoille
  (when (<= (:aosa kohde) (:losa kohde))
    (reduce (fn [k rivi]
              (cond
                ;; Kun alkuosa ja loppuosa ovat erit
                ;; Alkuosa täsmää, joten ei oteta koko pituutta, vaan pelkästään jäljelle jäävä pituus
                (and (not= (:aosa k) (:losa k))
                     (= (:aosa k) (:osa rivi)))
                (assoc k :pituus (+
                                   (:pituus k) ;; Nykyinen pituus
                                   (- (:pituus rivi) (:aet k)) ;; Osamäpin osan pituudesta vähennetään alkuosan etäisyys
                                   ))
                ;; Kun alkuosa ja loppuosa ovat erit
                ;; Jos loppuosa täsmää osalistan osaan, niin otetaan vain loppuosan etäisyys
                (and (not= (:aosa k) (:losa k))
                     (= (:losa k) (:osa rivi)))
                (assoc k :pituus (+
                                   (:pituus k) ;; Nykyinen pituus
                                   (:let k) ;; Lopposan pituus, eli alusta tähän asti, ei siis koko osan pituutta
                                   ))
                ;; Kun alkuosa on sama kuin loppuosa
                ;; Ja osa on olemassa. Eli jos tiepätkään ei kuulu se osa mitä mitataan, niin ei myöskään
                ;; lasketa sitä mukaaj
                ;; Otetaan vain osien väliin jäävä pätkä mukaan
                (and (= (:osa rivi) (:aosa k))
                     (= (:aosa k) (:losa k)))
                (assoc k :pituus (+
                                   (:pituus k) ;; Nykyinen pituus
                                   (- (:let k) (:aet k)) ;; Osamäpin osan pituudesta vähennetään alkuosan etäisyys
                                   ))

                ;; alkuosa tai loppuosa ei täsmää, joten otetaan koko osan pituus
                :else
                (if
                  ;; Varmistetaan, että tietokannasta on saatu validi osa
                  ;; Ja että tietokannasta saatu osa pitää käsitellä vielä tälle kohteelle.
                  ;; Jos :osa on suurimpi kuin :losa, niin mitään käsittelyitä ei tarvita enää
                  ;; Ja jos :osa on pienempi kuin :aosa, niin käsittelyitä ei tarvita
                  (and (:pituus rivi)
                       (< (:osa rivi) (:losa k))
                       (> (:osa rivi) (:aosa k)))
                  (assoc k :pituus (+
                                     (:pituus k) ;; Nykyinen pituus
                                     (:pituus rivi) ;; Osamäpin osan pituus
                                     ))
                  k)))
            ;; Annetaan reducelle mäppi, jossa :pituus avaimeen lasketaan annetun tien kohdan pituus.
            {:pituus 0 :aosa (:aosa kohde) :aet (:aet kohde) :losa (:losa kohde) :let (:let kohde)}
            osan-pituudet)))

(defn laske-paikkauskohteen-pituus [db kohde]
  (let [;; Jos osan hae-osien-pituudet kyselyn tulos muuttuu, tämän funktion toiminta loppuu
        ;; Alla oleva reduce olettaa, että sille annetaan osien pituudet desc järjestyksessä ja muodossa
        ;; ({:osa 1 :pituus 3000} {:osa 2 :pituus 3000})
        osan-pituudet (harja.kyselyt.tieverkko/hae-osien-pituudet db {:tie (:tie kohde)
                                                                      :aosa (:aosa kohde)
                                                                      :losa (:losa kohde)})]
    (laske-tien-osien-pituudet osan-pituudet kohde)))

(defn- siivoa-paikkauskohteet
  "Poistetaan käyttämättömät avaimet ja lasketaan pituus"
  [db paikkauskohteet]
  (map (fn [p]
         (-> p
             (assoc :pituus (:pituus (laske-paikkauskohteen-pituus db p)))
             (assoc :sijainti (geo/pg->clj (:geometria p)))
             (dissoc :geometria)))
       paikkauskohteet))

(defn paikkauskohteet [db user {:keys [elyt tilat alkupvm loppupvm tyomenetelmat urakka-id hae-alueen-kohteet?] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-paikkauskohteet user (:urakka-id tiedot))
  (let [_ (log/debug "paikkauskohteet :: tiedot" (pr-str tiedot))
        ;; Paikkauskohteiden hakeminen eri urakkatyypeille vaihtelee.
        ;; Paikkaus ja Päällystys urakoille haetaan normaalisti vain paikkauskohteet, mutta
        ;; Jos alueurakalle (jolla siis tarkoitetaan hoito ja teiden-hoito tyyppinen urakka) sekä tiemerkintä urakalle
        ;; haetaan paikkauskohteita, niin silloin turvaudutaan paikkauskohteen maantieteelliseen sijaintiin eikä urakan-id:seen.
        ;; Hoitourakoille voidaan myös hakea kohteet id:n perusteella käyttäen hae-urakan-kohteet?-lippua.
        urakan-tyyppi (hae-urakkatyyppi db (:urakka-id tiedot))
        tilat (disj tilat "kaikki")
        tilat (when (> (count tilat) 0)
                (vec tilat))
        menetelmat (disj tyomenetelmat "Kaikki")
        menetelmat (when (> (count menetelmat) 0)
                     menetelmat)
        ;; Valitut elykeskukset
        elyt (disj elyt 0) ;; Poistetaan potentiaalinen "kaikki" valinta
        elyt (when (> (count elyt) 0)
               (vec elyt))
        urakan-paikkauskohteet (cond
                                 hae-alueen-kohteet?
                                 (paikkauskohteet-urakan-alueella db urakka-id tilat alkupvm loppupvm menetelmat)
                                 (= :tiemerkinta urakan-tyyppi)
                                 (paikkauskohteet-elyn-alueella db urakka-id tilat alkupvm loppupvm menetelmat)
                                 :else
                                 (paikkauskohteet-urakalle db {:urakka-id urakka-id
                                                               :tilat tilat
                                                               :alkupvm alkupvm
                                                               :loppupvm loppupvm
                                                               :tyomenetelmat menetelmat
                                                               :elyt elyt}))
        urakan-paikkauskohteet (siivoa-paikkauskohteet db urakan-paikkauskohteet)
        ;_ (println "paikkauskohteet :: urakan-paikkauskohteet" (pr-str urakan-paikkauskohteet))
        ;; Tarkistetaan käyttäjän käyttöoikeudet suhteessa kustannuksiin.
        ;; Mikäli käyttäjälle ei ole nimenomaan annettu oikeuksia nähdä summia, niin poistetaan ne
        urakan-paikkauskohteet (map (fn [kohde]
                                      (if (oikeudet/voi-lukea? oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (:urakka-id kohde) user)
                                        ;; True - on oikeudet kustannuksiin
                                        kohde
                                        ;; False - ei ole oikeuksia kustannuksiin, joten poistetaan ne
                                        (dissoc kohde :suunniteltu-hinta :toteutunut-hinta)))
                                    urakan-paikkauskohteet)]
    urakan-paikkauskohteet))

