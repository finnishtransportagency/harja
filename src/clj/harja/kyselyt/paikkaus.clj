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
            [taoensso.timbre :as log]
            [specql.core :as specql]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [slingshot.slingshot :refer [throw+]]
            [harja.domain.tierekisteri.validointi :as tr-validointi]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yllapitokohteet-yleiset]
            [harja.domain.tierekisteri :as tr-domain]))

(def merkitse-paikkauskohde-tarkistetuksi!
  "Päivittää paikkauskohteen tarkistaja-idn ja aikaleiman.")

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
    (-> paikkaus/paikkauksen-perustiedot
      (disj ::paikkaus/pinta-ala ::paikkaus/massamaara)
      (conj [::paikkaus/materiaalit paikkaus/materiaalit-perustiedot]))
    hakuehdot))

(defn hae-paikkaukset-paikkauskohde [db hakuehdot]
  (fetch db
         ::paikkaus/paikkaus
         (conj paikkaus/paikkauksen-perustiedot
               [::paikkaus/paikkauskohde (conj paikkaus/paikkauskohteen-perustiedot
                                               ::muokkaustiedot/luotu
                                               ::muokkaustiedot/muokattu)])
         (merge
           {::muokkaustiedot/poistettu? false}
           hakuehdot)))

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

(defn hae-paikkauskohteiden-tyomenetelmat
  ([db]
   (hae-paikkauskohteiden-tyomenetelmat db nil nil))
  ([db _ {:keys [tyomenetelma]}]
   (oikeudet/ei-oikeustarkistusta!)
   (fetch db
          ::paikkaus/paikkauskohde-tyomenetelma
          (specql/columns ::paikkaus/paikkauskohde-tyomenetelma)
          (when tyomenetelma
            (op/or {::paikkaus/tyomenetelma-nimi tyomenetelma}
                   {::paikkaus/tyomenetelma-lyhenne tyomenetelma})))))

(defn hae-tyomenetelman-id [db tyomenetelma]
  (::paikkaus/tyomenetelma-id
    (first (fetch db
                  ::paikkaus/paikkauskohde-tyomenetelma
                  #{::paikkaus/tyomenetelma-id}
                  (op/or {::paikkaus/tyomenetelma-nimi tyomenetelma}
                         {::paikkaus/tyomenetelma-lyhenne tyomenetelma})))))

(defn onko-paikkaus-olemassa-ulkoisella-idlla?
      "Paikkaus tunnistetaan urakan ja ulkoisen-id:n perusteella.
      Paikkausta saa muokata urakan käyttäjät ja urakoitsijajärjestelmä."
      [db urakka-id ulkoinen-id]
  (and
    (number? ulkoinen-id)
    (not (empty? (hae-paikkaukset db {::paikkaus/ulkoinen-id ulkoinen-id
                                      ::paikkaus/urakka-id urakka-id})))))

(defn onko-paikkaustoteuma-olemassa-ulkoisella-idlla? [db urakka-id ulkoinen-id]
      "Paikkaustoteuma tunnistetaan urakan ja ulkoisen-id:n perusteella.
      Paikkaustoteumaa saa muokata urakan käyttäjät ja urakoitsijajärjestelmä."
  (and
    (number? ulkoinen-id)
    (not (empty? (hae-paikkaustoteumat db {::paikkaus/ulkoinen-id ulkoinen-id
                                           ::paikkaus/urakka-id urakka-id})))))

(defn onko-kohde-olemassa-ulkoisella-idlla?
      "Paikkauskohde tunnistetaan urakan ja ulkoisen-id:n perusteella.
      Paikkauskohdetta saa muokata urakan käyttäjät ja urakoitsijajärjestelmä."
      [db urakka-id ulkoinen-id]
  (and
    (number? ulkoinen-id)
    (not (empty? (hae-paikkauskohteet db {::paikkaus/ulkoinen-id ulkoinen-id
                                          ::paikkaus/urakka-id urakka-id})))))

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
        ehdot {::paikkaus/id id}]
    (when (id-olemassa? id)
      (update! db ::paikkaus/paikkauskohde paikkauskohde ehdot))
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
  (delete! db ::paikkaus/paikkauksen-tienkohta {::paikkaus/paikkaus-id toteuma-id})
  (doseq [tienkohta tienkohdat]
    (insert! db ::paikkaus/paikkauksen-tienkohta (assoc tienkohta ::paikkaus/paikkaus-id toteuma-id))))

(defn tallenna-paikkauskohde
  "Käsittelee paikkauskohteen. Päivittää olemassa olevan tai lisää uuden."
  ([db urakka-id kayttaja-id kohde]
   (tallenna-paikkauskohde db urakka-id kayttaja-id kohde nil))
  ([db urakka-id kayttaja-id kohde alkuaika]
  (let [id (::paikkaus/id kohde)
        ulkoinen-tunniste (::paikkaus/ulkoinen-id kohde)
        ;; nollataan mahdollinen ilmoitettu virhe
        kohde (assoc kohde ::paikkaus/ilmoitettu-virhe nil)
        ;; Muutetaan työmenetelmä tarvittaessa ID:ksi
        tyomenetelma (::paikkaus/tyomenetelma kohde)
        tyomenetelma (if (string? tyomenetelma)
                       (hae-tyomenetelman-id db tyomenetelma)
                       tyomenetelma)
        kohde (assoc kohde ::paikkaus/tyomenetelma tyomenetelma)]
    (if (id-olemassa? id)
      (update! db ::paikkaus/paikkauskohde kohde {::paikkaus/id id})
      (if (onko-kohde-olemassa-ulkoisella-idlla? db urakka-id ulkoinen-tunniste)
        (update! db ::paikkaus/paikkauskohde
                 (assoc kohde ::muokkaustiedot/poistettu? false
                              ::muokkaustiedot/muokkaaja-id kayttaja-id
                              ::muokkaustiedot/muokattu (pvm/nyt))
                 {::paikkaus/urakka-id urakka-id
                  ::paikkaus/ulkoinen-id ulkoinen-tunniste
                  ::muokkaustiedot/luoja-id kayttaja-id})
        (insert! db ::paikkaus/paikkauskohde
                 (assoc kohde ::paikkaus/urakka-id urakka-id
                              ;; Jos lisätään uusi paikkauskohde, niin annetaan sille pari pakollista
                              ;; tietoa
                              ::paikkaus/paikkauskohteen-tila "tilattu"
                              ::paikkaus/tilattupvm alkuaika
                              ::muokkaustiedot/luoja-id kayttaja-id
                              ::muokkaustiedot/luotu (pvm/nyt)))))
    (first (hae-paikkauskohteet db {::paikkaus/ulkoinen-id ulkoinen-tunniste
                                    ::paikkaus/urakka-id urakka-id})))))

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
  (let [_ (log/debug "tallenna-paikkaus :: paikkaus:" (pr-str paikkaus))
        paikkauskohde-id (::paikkaus/id
                           (tallenna-paikkauskohde db urakka-id kayttaja-id
                             (::paikkaus/paikkauskohde paikkaus)
                             (::paikkaus/alkuaika paikkaus)))
        {id ::paikkaus/id
         ulkoinen-id ::paikkaus/ulkoinen-id
         materiaalit ::paikkaus/materiaalit
         tr-osoite ::paikkaus/tierekisteriosoite
         tyomenetelma ::paikkaus/tyomenetelma
         tienkohdat ::paikkaus/tienkohdat
         leveys ::paikkaus/leveys
         massamenekki ::paikkaus/massamenekki
         massamaara ::paikkaus/massamaara } paikkaus
        paikkaus (cond-> paikkaus
                         (not (nil? massamenekki)) (update ::paikkaus/massamenekki bigdec))
        tr-osoite-tr-muodossa (tr-domain/tr-alkuiseksi tr-osoite)
        osien-pituudet-tielle (yllapitokohteet-yleiset/laske-osien-pituudet db [tr-osoite-tr-muodossa])
        pituus (tr-domain/laske-tien-pituus (osien-pituudet-tielle (::tierekisteri/tie tr-osoite)) tr-osoite-tr-muodossa)

        pinta-ala (when (and leveys pituus)
                    (* leveys pituus))
        ;; lisätään paikkauksiin pinta-ala ja massamäärä, jos ne luvut saatavilla mistä pystytään johtamaan
        paikkaus (cond-> paikkaus
                   true (assoc
                          ::paikkaus/pinta-ala pinta-ala)

                   ;; Vanha tapa kirjata paikkaus massamenekillä. Voidaan poistaa myöhemmin, kun massamäärällä kirjaus
                   ;; on saatu otettua käyttöön, pitäisi tapahtua 24.8.2023.
                   (and pinta-ala (some? massamenekki) (nil? massamaara))
                   ;; massamenekki on kg/m2, kokonaismassamäärä puolestaan aina tonneja -->
                   ;; kokonaismassamäärä tonneina = massamenekki tonneina / m2 * pinta-ala m2
                   (assoc
                     ::paikkaus/massamaara (* (/ massamenekki 1000) pinta-ala))

                   ;; 24.8.2023 jälkeen rajapinnasta pitäisi saada massamäärä kilogrammoina massamenekin sijaan.
                   (and (some? massamaara) (nil? massamenekki))
                   (assoc
                     ;; Rajapinnasta saadaan massamäärä kilogrammoina. -->
                     ;; massamenekki kg/m^2 = massamäärä / pinta-ala.
                     ;; Massamenekin pitäisi olla kymmenissä, joten with-precision 5 pitäisi olla riittävä.
                     ::paikkaus/massamenekki (bigdec (with-precision 5 (/ massamaara pinta-ala)))
                     ;; Muutetaan massamäärä vielä tonneiksi ennen kantaan tallennusta.
                     ;; Massaa on maksimissaan kymmenissä tonneissa. Varalta kuitenkin with-precision 10.
                     ::paikkaus/massamaara (bigdec (with-precision 10 (/ massamaara 1000)))))
        tyomenetelma (if (string? tyomenetelma)
                       (hae-tyomenetelman-id db tyomenetelma)
                       tyomenetelma)
        sijainti (q-tr/tierekisteriosoite-viivaksi db {:tie (::tierekisteri/tie tr-osoite) :aosa (::tierekisteri/aosa tr-osoite)
                                                       :aet (::tierekisteri/aet tr-osoite) :losa (::tierekisteri/losa tr-osoite)
                                                       :loppuet (::tierekisteri/let tr-osoite)})
        uusi-paikkaus (dissoc (assoc paikkaus ::paikkaus/paikkauskohde-id paikkauskohde-id
                                              ::muokkaustiedot/luoja-id kayttaja-id
                                              ::paikkaus/sijainti sijainti
                                              ::paikkaus/lahde "harja-api"
                                              ::paikkaus/tyomenetelma tyomenetelma)
                              ::paikkaus/materiaalit
                              ::paikkaus/tienkohdat
                              ::paikkaus/paikkauskohde)
        muokattu-paikkaus (assoc uusi-paikkaus ::muokkaustiedot/muokkaaja-id kayttaja-id
                                               ::muokkaustiedot/muokattu (pvm/nyt)
                                               ::muokkaustiedot/poistettu? false)
        paivita? (or (id-olemassa? id) (onko-paikkaus-olemassa-ulkoisella-idlla? db urakka-id ulkoinen-id))
        id (::paikkaus/id (if paivita?
                            (paivita-paikkaus db urakka-id muokattu-paikkaus)
                            (luo-paikkaus db uusi-paikkaus)))]
    (tallenna-materiaalit db id materiaalit)
    (tallenna-tienkohdat db id tienkohdat)))

(defn- hae-paikkauskohteen-tila [db kohteen-id]
  (-> db
      (fetch ::paikkaus/paikkauskohde
             #{::paikkaus/paikkauskohteen-tila}
             {::paikkaus/id kohteen-id})
      first
      ::paikkaus/paikkauskohteen-tila))

(defn tallenna-kasinsyotetty-paikkaus
  "Olettaa saavansa paikkauksena mäpin, joka ei sisällä paikkaus domainin namespacea. Joten ne lisätään,
  jotta voidaan hyödyntää specql:n toimintaa."
  [db user paikkaus]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-paikkauskohteet user (:urakka-id paikkaus))
  ;; Voidaan tallentaa vain, jos tila on tilattu
  (when (not= "tilattu" (hae-paikkauskohteen-tila db (:paikkauskohde-id paikkaus)))
      (log/error (str "Yritettiin luoda kohteelle, jonka tila ei ole 'tilattu', toteumaa :: kohteen-id " (:paikkauskohde-id paikkaus)))
      (throw+ {:type "Validaatiovirhe"
               :virheet [{:koodi :puuttelliset-parametrit
                          :viesti (str "Yritettiin luoda kohteelle, jonka tila ei ole 'tilattu', toteumaa :: kohteen-id " (:paikkauskohde-id paikkaus))}]}))
  ;; Valitoidaan tierekisteriosoite
  (when-not (empty? (tr-validointi/validoi-tieosoite #{} (:tie paikkaus) (:aosa paikkaus) (:losa paikkaus) (:aet paikkaus) (:let paikkaus)))
      (log/error (str "Yritettiin luoda paikkaus epävalidilla tierekisteriosoitteella :: kohteen-id " (:paikkauskohde-id paikkaus)))
      (throw+ {:type "Validaatiovirhe"
               :virheet [{:koodi :viallinen-tierekisteriosoite
                          :viesti (str "Yritettiin luoda paikkaus epävalidilla tierekisteriosoitteella :: kohteen-id " (:paikkauskohde-id paikkaus))}]}))
  (let [_ (log/info "tallenna-kasinsyotetty-paikkaus :: urakka-id" (:urakka-id paikkaus) "paikkaus:" paikkaus)
        paikkaus-id (:id paikkaus)
        paikkauskohde-id (:paikkauskohde-id paikkaus)
        sijainti (q-tr/tierekisteriosoite-viivaksi db {:tie (:tie paikkaus) :aosa (:aosa paikkaus)
                                                       :aet (:aet paikkaus) :losa (:losa paikkaus)
                                                       :loppuet (:let paikkaus)})
        tyomenetelmat (hae-paikkauskohteiden-tyomenetelmat db)
        tienkohdat (if (and (paikkaus/levittimella-tehty? paikkaus tyomenetelmat)
                              (:kaista paikkaus))
                     {::paikkaus/ajorata (konversio/konvertoi->int (:ajorata paikkaus))
                      ::paikkaus/kaista (konversio/konvertoi->int (:kaista paikkaus))}
                     ;; Muut kuin levittimellä tehdyt voi käyttää yksinkertaisempaa tienkohtaa
                     {::paikkaus/ajorata (konversio/konvertoi->int (:ajorata paikkaus))})
        ;; Muutetaan työmenetelmä tarvittaessa ID:ksi
        tyomenetelma (:tyomenetelma paikkaus)
        paikkaus (if (string? tyomenetelma)
                   (assoc paikkaus :tyomenetelma (paikkaus/tyomenetelma-id tyomenetelma tyomenetelmat))
                   paikkaus)
        paikkaus (-> paikkaus
                     (assoc ::paikkaus/tierekisteriosoite {::tierekisteri/tie (:tie paikkaus)
                                                           ::tierekisteri/aosa (:aosa paikkaus)
                                                           ::tierekisteri/aet (:aet paikkaus)
                                                           ::tierekisteri/losa (:losa paikkaus)
                                                           ::tierekisteri/let (:let paikkaus)
                                                           ::tierekisteri/ajorata (:ajorata paikkaus)})
                     (dissoc :maara :tie :aosa :aet :let :losa :ajorata :kaista :ajouravalit :ajourat :reunat
                             :harja.domain.paikkaus/tienkohdat :keskisaumat)
                     (assoc :ulkoinen-id 0)
                     (update :massatyyppi #(or % ""))
                     (assoc :lahde "harja-ui"))
        paikkaus (cond-> paikkaus
                         (not (nil? (:leveys paikkaus))) (update :leveys bigdec)
                         (not (nil? (:massamaara paikkaus))) (update :massamaara bigdec)
                         (not (nil? (:pinta-ala paikkaus))) (update :pinta-ala bigdec)
                         (not (nil? (:juoksumetri paikkaus))) (update :juoksumetri bigdec)
                         (not (nil? (:kpl paikkaus))) (update :kpl bigdec)
                         (not (nil? (:massamenekki paikkaus))) (update :massamenekki bigdec))
        paikkaus (set/rename-keys paikkaus paikkaus/paikkaus->speqcl-avaimet)

        uusi-paikkaus (assoc paikkaus ::paikkaus/paikkauskohde-id paikkauskohde-id
                                      ::muokkaustiedot/luoja-id (:id user)
                                      ::paikkaus/sijainti sijainti)
        muokattu-paikkaus (assoc uusi-paikkaus ::muokkaustiedot/muokkaaja-id (:id user)
                                               ::muokkaustiedot/muokattu (pvm/nyt)
                                               ::muokkaustiedot/poistettu? false)
        paikkaus (if paikkaus-id
                   (paivita-paikkaus db (:urakka-id paikkaus) muokattu-paikkaus)
                   (luo-paikkaus db uusi-paikkaus))
        _ (tallenna-tienkohdat db (::paikkaus/id paikkaus) [tienkohdat])
        ]
    paikkaus))

(defn tallenna-urem-paikkaus-excelista [db paikkaus]
  (let [tienkohdat (::paikkaus/tienkohdat paikkaus)
        paikkaus (luo-paikkaus db (dissoc paikkaus ::paikkaus/tienkohdat))]
    (tallenna-tienkohdat db (::paikkaus/id paikkaus) [tienkohdat])
    paikkaus))

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
                               ;{::paikkaus/paikkaukset {::paikkaus/urakka-id urakka-id}}
                               {::paikkaus/urakka-id urakka-id})
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
                                          {::paikkaus/urakka-id urakka-id})
        paikkauskohteiden-tyomenetelmat (fetch db
                                               ::paikkaus/paikkauskohde
                                               #{::paikkaus/paikkauskohteen-tila
                                                 ::paikkaus/tyomenetelma}
                                               {::paikkaus/urakka-id urakka-id})
        paikkauskohteiden-tyomenetelmat (filter #(case (::paikkaus/paikkauskohteen-tila %)
                                                   ("tilattu", "valmis", nil) true
                                                   false) paikkauskohteiden-tyomenetelmat)]
    (into #{} (distinct
               (map ::paikkaus/tyomenetelma (concat
                                             paikkauksien-tyomenetelmat
                                             paikkauskohteiden-tyomenetelmat))))))

(defn- hae-urakkatyyppi [db urakka-id]
  (keyword (:tyyppi (first (q-yllapitokohteet/hae-urakan-tyyppi db {:urakka urakka-id})))))

(defn- kasittele-paikkauskohteiden-sijainti
  "Poistetaan käyttämättömät avaimet ja lasketaan pituus"
  [db paikkauskohteet]
  (map (fn [p]
         (let [sijainti (geo/pg->clj (:geometria p))
               sijainti (if (and sijainti (= :multipoint (:type sijainti)))
                          {:type :multiline
                           :lines [{:type :line
                                    :points [(:coordinates (first (:coordinates sijainti)))]}]}
                          sijainti)]
           (-> p
               (assoc :pituus (:pituus (q-tr/laske-tierekisteriosoitteen-pituus db p)))
               (assoc :sijainti sijainti)
               (dissoc :geometria))))
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
        urakan-paikkauskohteet (kasittele-paikkauskohteiden-sijainti db urakan-paikkauskohteet)
        ;; Tarkistetaan käyttäjän käyttöoikeudet suhteessa kustannuksiin.
        ;; Mikäli käyttäjälle ei ole nimenomaan annettu oikeuksia nähdä summia, niin poistetaan ne
        urakan-paikkauskohteet (map (fn [kohde]
                                      (let [kohde (if (oikeudet/voi-lukea? oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (:urakka-id kohde) user)
                                                    ;; True - on oikeudet kustannuksiin
                                                    kohde
                                                    ;; False - ei ole oikeuksia kustannuksiin, joten poistetaan ne
                                                    (dissoc kohde :suunniteltu-hinta :toteutunut-hinta))
                                            kohde (if (:valmistumispvm kohde)
                                                    (assoc kohde :paikkaustyo-valmis? true)
                                                    (assoc kohde :paikkaustyo-valmis? false))]
                                        kohde))
                                    urakan-paikkauskohteet)
        ;_ (println "urakan-paikkauskohteet: " (pr-str (into (sorted-map) (dissoc (first urakan-paikkauskohteet) :sijainti))))
        ]
    urakan-paikkauskohteet))

