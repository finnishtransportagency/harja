(ns harja.kyselyt.tietyoilmoitukset
  (:require [jeesql.core :refer [defqueries]]
            [harja.domain.tietyoilmoitukset :as t]
            [specql.core :refer [define-tables]]
            [specql.rel :as rel]
            [clojure.spec :as s]
            [harja.kyselyt.specql :refer [db]]))

(defqueries "harja/kyselyt/tietyoilmoitukset.sql")

(define-tables db
  ["tr_osoite" ::t/tr-osoite]
  ["tietyon_henkilo" ::t/henkilo]
  ["tietyon_ajoneuvorajoitukset" ::t/ajoneuvorajoitukset*]
  ["tietyotyyppi" ::t/tietyotyyppi]
  ["tietyon_tyypin_kuvaus" ::t/tietyon-tyyppi]
  ["viikonpaiva" ::t/viikonpaiva]
  ["tietyon_tyoaika" ::t/tyoaika]
  ["tietyon_vaikutussuunta" ::t/vaikutussuunta]
  ["tietyon_kaistajarjestelytyyppi" ::t/kaistajarjestelytyyppi]
  ["tietyon_kaistajarjestelyt" ::t/kaistajarjestelyt]
  ["nopeusrajoitus" ::t/nopeusrajoitusvalinta]
  ["tietyon_nopeusrajoitus" ::t/nopeusrajoitus]
  ["tietyon_pintamateriaalit" ::t/pintamateriaalit]
  ["tietyon_tienpinta" ::t/tien-pinta]
  ["tietyon_mutkat" ::t/mutkat]
  ["tietyon_liikenteenohjaaja" ::t/liikenteenohjaaja]
  ["tietyon_huomautukset" ::t/huomautustyypit]
  ["tietyoilmoitus" ::t/ilmoitus
   {::t/paailmoitus (rel/has-one ::t/paatietyoilmoitus
                                 ::t/ilmoitus
                                 ::t/id)}])

(def kaikki-ilmoituksen-kentat
  #{::t/id
    ::t/tloik-id
    ::t/paatietyoilmoitus
    ::t/tloik-paatietyoilmoitus-id
    ::t/luotu
    ::t/luoja
    ::t/muokattu
    ::t/muokkaaja
    ::t/poistettu
    ::t/poistaja
    ::t/ilmoittaja-id
    ::t/ilmoittaja
    ::t/urakka-id
    ::t/urakan-nimi
    ::t/urakkatyyppi
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
    ::t/kiertotien-mutkaisuus
    ::t/kiertotienpinnat
    ::t/liikenteenohjaus
    ::t/liikenteenohjaaja
    ::t/viivastys-normaali-liikenteessa
    ::t/viivastys-ruuhka-aikana
    ::t/ajoneuvorajoitukset
    ::t/huomautukset
    ::t/ajoittaiset-pysatykset
    ::t/ajoittain-suljettu-tie
    ::t/pysaytysten-alku
    ::t/pysaytysten-loppu
    ::t/lisatietoja})

(def ilmoituslomakkeen-kentat
  #{[::t/paailmoitus #{::t/urakan-nimi ::t/urakoitsijayhteyshenkilo ::t/tilaajayhteyshenkilo
                       ::t/ilmoittaja
                       ::t/osoite ::t/tien-nimi ::t/kunnat
                       ::t/alkusijainnin-kuvaus ::t/loppusijainnin-kuvaus
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
    ::t/ajoittaiset-pysatykset
    ::t/ajoittain-suljettu-tie
    ::t/pysaytysten-alku
    ::t/pysaytysten-loppu
    ::t/lisatietoja})
