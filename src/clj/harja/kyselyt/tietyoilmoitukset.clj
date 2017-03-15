(ns harja.kyselyt.tietyoilmoitukset
  (:require [jeesql.core :refer [defqueries]]
            [harja.domain.tietyoilmoitukset :as t]
            [specql.core :refer [define-tables]]
            [clojure.spec :as s]
            [harja.kyselyt.specql :refer [db]]))

(defqueries "harja/kyselyt/tietyoilmoitukset.sql")

(define-tables db
  ["tietyon_henkilo" ::t/henkilo]
  ["tietyon_ajoneuvorajoitukset" ::t/ajoneuvorajoitukset*]
  ["tietyotyyppi" ::t/tietyotyyppi]
  ["tietyon_tyypin_kuvaus" ::t/tietyon-tyyppi]
  ["viikonpaiva" ::t/viikonpaiva]
  ["tietyon_tyoaika" ::t/tyoaika]
  ["tietyon_vaikutussuunta" ::t/vaikutussuunta]
  ["tietyon_kaistajarjestelyt" ::t/kaistajarjestelyt]
  ["nopeusrajoitus" ::t/nopeusrajoitusvalinta]
  ["tietyon_nopeusrajoitus" ::t/nopeusrajoitus]
  ["tietyon_pintamateriaalit" ::t/pintamateriaalit]
  ["tietyon_tienpinta" ::t/tien-pinta]
  ["tietyon_mutkat" ::t/mutkat]
  ["tietyon_liikenteenohjaaja" ::t/liikenteenohjaaja]
  ["tietyon_huomautukset" ::t/huomautustyypit]
  ["tietyoilmoitus" ::t/ilmoitus])

(def kaikki-ilmoituksen-kentat
  #{::t/id
    ::t/tloik_id
    ::t/paatietyoilmoitus
    ::t/tloik_paatietyoilmoitus_id
    ::t/luotu
    ::t/luoja
    ::t/muokattu
    ::t/muokkaaja
    ::t/poistettu
    ::t/poistaja
    ::t/ilmoittaja-id
    ::t/ilmoittaja
    ::t/urakka
    ::t/urakka_nimi
    ::t/urakkatyyppi
    ::t/urakoitsijayhteyshenkilo-id
    ::t/urakoitsijayhteyshenkilo
    ::t/tilaaja
    ::t/tilaajan_nimi
    ::t/tilaajayhteyshenkilo-id
    ::t/tilaajayhteyshenkilo
    ::t/tyotyypit
    ::t/luvan_diaarinumero
    ::t/sijainti
    ::t/tr_numero
    ::t/tr_alkuosa
    ::t/tr_alkuetaisyys
    ::t/tr_loppuosa
    ::t/tr_loppuetaisyys
    ::t/tien_nimi
    ::t/kunnat
    ::t/alkusijainnin_kuvaus
    ::t/loppusijainnin_kuvaus
    ::t/alku
    ::t/loppu
    ::t/tyoajat
    ::t/vaikutussuunta
    ::t/kaistajarjestelyt
    ::t/nopeusrajoitukset
    ::t/tienpinnat
    ::t/kiertotien_mutkaisuus
    ::t/kiertotienpinnat
    ::t/liikenteenohjaus
    ::t/liikenteenohjaaja
    ::t/viivastys_normaali_liikenteessa
    ::t/viivastys_ruuhka_aikana
    ::t/ajoneuvorajoitukset
    ::t/huomautukset
    ::t/ajoittaiset_pysatykset
    ::t/ajoittain_suljettu_tie
    ::t/pysaytysten_alku
    ::t/pysaytysten_loppu
    ::t/lisatietoja})
