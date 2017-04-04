(ns harja.kyselyt.tietyoilmoitukset
  (:require [jeesql.core :refer [defqueries]]
            [harja.domain.tietyoilmoitukset :as t]
            [harja.domain.tierekisteri :as tr]
            [specql.core :refer [define-tables fetch]]
            [specql.op :as op]
            [specql.rel :as rel]
            [clojure.spec :as s]
            [harja.kyselyt.specql :refer [db]]
            [harja.domain.muokkaustiedot :as m]
            [clojure.future :refer :all]))


(defqueries "harja/kyselyt/tietyoilmoitukset.sql")

(define-tables db
  ["tietyon_henkilo" ::t/henkilo]
  ["tietyon_ajoneuvorajoitukset" ::t/ajoneuvorajoitukset*]
  ["tietyotyyppi" ::t/tietyotyyppi]
  ["tietyon_tyypin_kuvaus" ::t/tietyon-tyyppi]
  ["viikonpaiva" ::t/viikonpaiva]
  ["tietyon_tyoaika" ::t/tyoaika]
  ["tietyon_vaikutussuunta" ::t/vaikutussuunta*]
  ["tietyon_kaistajarjestelytyyppi" ::t/kaistajarjestelytyyppi]
  ["tietyon_kaistajarjestelyt" ::t/kaistajarjestelyt*]
  ["nopeusrajoitus" ::t/nopeusrajoitusvalinta]
  ["tietyon_nopeusrajoitus" ::t/nopeusrajoitus]
  ["tietyon_pintamateriaalit" ::t/pintamateriaalit]
  ["tietyon_tienpinta" ::t/tien-pinta]
  ["tietyon_mutkat" ::t/mutkat]
  ["tietyon_liikenteenohjaaja" ::t/liikenteenohjaaja*]
  ["tietyon_liikenteenohjaus" ::t/liikenteenohjaus*]
  ["tietyon_huomautukset" ::t/huomautustyypit]
  ["tietyoilmoitus" ::t/ilmoitus
   {::t/paailmoitus (rel/has-one ::t/paatietyoilmoitus
                                 ::t/ilmoitus
                                 ::t/id)
    ::t/tyovaiheet (rel/has-many ::t/id
                                 ::t/ilmoitus
                                 ::t/paatietyoilmoitus)
    "luoja" ::m/luoja-id
    "luotu" ::m/luotu
    "muokkaaja" ::m/muokkaaja-id
    "muokattu" ::m/muokattu
    "poistaja" ::m/poistaja-id
    "poistettu" ::m/poistettu}]
  ["tietyoilmoitus_pituus" ::t/ilmoitus+pituus
   {::t/paailmoitus (rel/has-one ::t/paatietyoilmoitus
                                 ::t/ilmoitus+pituus
                                 ::t/id)
    "luoja" ::m/luoja-id
    "luotu" ::m/luotu
    "muokkaaja" ::m/muokkaaja-id
    "muokattu" ::m/muokattu
    "poistaja" ::m/poistaja-id
    "poistettu" ::m/poistettu}])

;; Löysennetään tyyppejä numeroiksi, koska kokonaisluvut tulevat
;; transitin läpi longeina.
(s/def ::t/max-korkeus number?)
(s/def ::t/max-paino number?)
(s/def ::t/max-pituus number?)
(s/def ::t/max-leveys number?)

(def kaikki-ilmoituksen-kentat
  #{::t/id
    ::t/tloik-id
    ::t/paatietyoilmoitus
    ::t/tloik-paatietyoilmoitus-id
    ::m/luotu
    ::m/luoja-id
    ::m/muokattu
    ::m/muokkaaja-id
    ::m/poistettu
    ::m/poistaja-id
    ::t/ilmoittaja-id
    ::t/ilmoittaja
    ::t/urakka-id
    ::t/urakan-nimi
    ::t/urakkatyyppi
    ::t/urakoitsijan-nimi
    ::t/urakoitsijayhteyshenkilo-id
    ::t/urakoitsijayhteyshenkilo
    ::t/tilaaja-id
    ::t/tilaajan-nimi
    ::t/tilaajayhteyshenkilo-id
    ::t/tilaajayhteyshenkilo
    ::t/tyotyypit
    ::t/luvan-diaarinumero
    ::t/osoite
    ::t/tien-nimi
    ::t/kunnat
    ::t/alkusijainnin-kuvaus
    ::t/loppusijainnin-kuvaus
    ::t/alku
    ::t/loppu
    ::t/tyoajat
    ::t/vaikutussuunta
    ::t/kaistajarjestelyt
    ::t/nopeusrajoitukset
    ::t/tienpinnat
    ::t/kiertotien-pituus
    ::t/kiertotien-mutkaisuus
    ::t/kiertotienpinnat
    ::t/liikenteenohjaus
    ::t/liikenteenohjaaja
    ::t/viivastys-normaali-liikenteessa
    ::t/viivastys-ruuhka-aikana
    ::t/ajoneuvorajoitukset
    ::t/huomautukset
    ::t/ajoittaiset-pysaytykset
    ::t/ajoittain-suljettu-tie
    ::t/pysaytysten-alku
    ::t/pysaytysten-loppu
    ::t/lisatietoja})

;; Hakee pääilmoituksen kaikki kentät, sekä siihen liittyvät työvaiheet
(def kaikki-ilmoituksen-kentat-ja-tyovaiheet
  (conj kaikki-ilmoituksen-kentat
        [::t/tyovaiheet kaikki-ilmoituksen-kentat]))

(def ilmoitus-pdf-kentat
  (conj kaikki-ilmoituksen-kentat
        ::t/pituus
        [::t/paailmoitus (conj kaikki-ilmoituksen-kentat ::t/pituus)]))

(def ilmoituslomakkeen-kentat
  #{[::t/paailmoitus #{::t/urakan-nimi ::t/urakoitsijayhteyshenkilo ::t/tilaajayhteyshenkilo
                       ::t/ilmoittaja
                       ::t/osoite ::t/tien-nimi ::t/kunnat
                       ::t/alkusijainnin-kuvaus ::t/loppusijainnin-kuvaus
                       ::t/tyoajat
                       ::t/alku ::t/loppu}]
    ::t/urakka-id
    ::t/urakan-nimi
    ::t/ilmoittaja
    ::t/urakoitsijayhteyshenkilo
    ::t/tilaajayhteyshenkilo
    ::t/tyotyypit
    ::t/luvan-diaarinumero
    ::t/osoite
    ::t/tien-nimi
    ::t/kunnat
    ::t/alkusijainnin-kuvaus
    ::t/loppusijainnin-kuvaus
    ::t/alku
    ::t/loppu
    ::t/tyoajat
    ::t/vaikutussuunta
    ::t/kaistajarjestelyt
    ::t/nopeusrajoitukset
    ::t/tienpinnat
    ::t/kiertotien-mutkaisuus
    ::t/kiertotienpinnat
    ::t/liikenteenohjaus
    ::t/liikenteenohjaaja
    ::t/viivastys-normaali-liikenteessa
    ::t/viivastys-ruuhka-aikana
    ::t/ajoneuvorajoitukset
    ::t/huomautukset
    ::t/ajoittaiset-pysaytykset
    ::t/ajoittain-suljettu-tie
    ::t/pysaytysten-alku
    ::t/pysaytysten-loppu
    ::t/lisatietoja})

(defn intersects? [threshold geometry]
  (reify op/Op
    (to-sql [this value-accessor]
      [(str "ST_Intersects(ST_Buffer(?,?), " value-accessor ")")
       [geometry threshold]])))

(defn intersects-envelope? [{:keys [xmin ymin xmax ymax]}]
  (reify op/Op
    (to-sql [this val]
      [(str "ST_Intersects("val ", ST_MakeEnvelope(?,?,?,?))")
       [xmin ymin xmax ymax]])))

(defn overlaps? [rivi-alku rivi-loppu alku loppu]
  (op/or {rivi-alku (op/between alku loppu)}
         {rivi-loppu (op/between alku loppu)}
         {rivi-alku (op/<= alku) rivi-loppu (op/>= loppu)}))

(defn interval? [start interval]
  (reify op/Op
    (to-sql [this value]
      [(str "(? - "value" < ?::INTERVAL)")
       [start interval]])))

(defn hae-ilmoitukset [db {:keys [luotu-alku
                                  luotu-loppu
                                  kaynnissa-alku
                                  kaynnissa-loppu
                                  urakat
                                  organisaatio
                                  kayttaja-id
                                  sijainti]}]
  (let [ilmoitukset (fetch db ::t/ilmoitus kaikki-ilmoituksen-kentat-ja-tyovaiheet
                           (op/and
                             (merge {::t/paatietyoilmoitus op/null?}
                                    (when (and luotu-alku luotu-loppu)
                                      {::m/luotu (op/between luotu-alku luotu-loppu)})
                                    (when kayttaja-id
                                      {::m/luoja-id kayttaja-id})
                                    (when sijainti
                                      {::t/osoite {::tr/geometria (intersects? 100 sijainti)}}))
                             (if (and kaynnissa-alku kaynnissa-loppu)
                               (overlaps? ::t/alku ::t/loppu kaynnissa-loppu kaynnissa-loppu)
                               {::t/id op/not-null?})
                             (if (empty? urakat)
                               (if organisaatio
                                 {::t/urakoitsija-id organisaatio}
                                 {::t/id op/not-null?})
                               {::t/urakka-id (op/or op/null? (op/in urakat))})))]
    ilmoitukset))

(defn hae-ilmoitukset-tilannekuvaan [db {:keys [nykytilanne?
                                                tilaaja?
                                                urakat
                                                alku
                                                loppu
                                                alue]}]
  (fetch db ::t/ilmoitus kaikki-ilmoituksen-kentat-ja-tyovaiheet
         (op/and
           (if nykytilanne?
               {::t/loppu (interval? loppu "7 days")}
               (overlaps? ::t/alku ::t/loppu alku loppu))
           {::t/osoite {::tr/geometria (intersects-envelope? alue)}}
           (when-not tilaaja?
             (when-not (empty? urakat)
               {::t/urakka-id (op/or op/null? (op/in urakat))})))))

(defn hae-ilmoitukset-tienakymaan [db {:keys [alku
                                              loppu
                                              sijainti] :as tiedot}]
  (fetch db ::t/ilmoitus kaikki-ilmoituksen-kentat
         (op/and
           (overlaps? ::t/alku ::t/loppu alku loppu)
           {::t/osoite {::tr/geometria (intersects? 100 sijainti)}})))

(defn hae-ilmoitus [db tietyoilmoitus-id]
  (first (fetch db ::t/ilmoitus kaikki-ilmoituksen-kentat-ja-tyovaiheet
          {::t/id tietyoilmoitus-id})))
