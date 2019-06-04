(ns harja.domain.tielupa
  (:require
    [clojure.spec.alpha :as s]
    [harja.domain.muokkaustiedot :as muokkaustiedot]

    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]
        :cljs [[specql.impl.registry]])
    [clojure.string :as str])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]]))
  #?(:clj
     (:import (org.postgis PGgeometry))))

(define-tables
  ["tielupatyyppi" ::tielupatyyppi (specql.transform/transform (specql.transform/to-keyword))]
  ["tieluvan_kaapeliasennus" ::kaapeliasennustyyppi]
  ["tieluvan_johtoasennus" ::johtoasennustyyppi]
  ["tieluvan_opaste" ::opastetyyppi]
  ["tieluvan_liikennemerkkijarjestely" ::liikennemerkkijarjestelytyyppi]
  ["tr_osoite_laajennettu" ::tr-osoite-laajennettu]
  ["suoja_alue_rakenteen_sijoitus" ::suoja-alue-rakenteen-sijoitus]
  ["tielupa" ::tielupa
   {"luotu" ::muokkaustiedot/luotu
    "muokattu" ::muokkaustiedot/muokattu}])

#?(:clj
   (def kaikki-kentat (specql.core/columns ::tielupa)))

(def perustiedot
  #{::id
    ::ulkoinen-tunniste
    ::tyyppi
    ::paatoksen-diaarinumero
    ::saapumispvm
    ::myontamispvm
    ::voimassaolon-alkupvm
    ::voimassaolon-loppupvm
    ::otsikko
    ::liite-url
    ::katselmus-url
    ::ely
    ::urakat
    ::urakoiden-nimet
    ::kunta
    ::kohde-lahiosoite
    ::kohde-postinumero
    ::kohde-postitoimipaikka
    ::tien-nimi
    ::sijainnit
    ::muokkaustiedot/luotu
    ::muokkaustiedot/muokattu})

(def hakijan-tiedot
  #{::hakija-nimi
    ::hakija-osasto
    ::hakija-postinosoite
    ::hakija-postinumero
    ::hakija-puhelinnumero
    ::hakija-sahkopostiosoite
    ::hakija-tyyppi
    ::hakija-maakoodi})

(def urakoitsijan-tiedot
  #{::urakoitsija-nimi
    ::urakoitsija-yhteyshenkilo
    ::urakoitsija-puhelinnumero
    ::urakoitsija-sahkopostiosoite})

(def liikenneohjaajan-tiedot
  #{::liikenneohjaajan-nimi
    ::liikenneohjaajan-yhteyshenkilo
    ::liikenneohjaajan-puhelinnumero
    ::liikenneohjaajan-sahkopostiosoite})

(def tienpitoviranomaisen-tiedot
  #{::tienpitoviranomainen-yhteyshenkilo
    ::tienpitoviranomainen-puhelinnumero
    ::tienpitoviranomainen-sahkopostiosoite
    ::tienpitoviranomainen-lupapaallikko
    ::tienpitoviranomainen-kasittelija})

(def valmistumisilmoiksen-tiedot
  #{::valmistumisilmoitus-vaaditaan
    ::valmistumisilmoitus-palautettu
    ::valmistumisilmoitus})

(def johto-ja-kaapeliluvan-tiedot
  #{::johtolupa-maakaapelia-yhteensa
    ::johtolupa-ilmakaapelia-yhteensa
    ::johtolupa-tienylityksia
    ::johtolupa-silta-asennuksia
    ::kaapeliasennukset})

(def liittymaluvan-tiedot
  #{::liittymalupa-myonnetty-kayttotarkoitus
    ::liittymalupa-haettu-kayttotarkoitus
    ::liittymalupa-liittyman-siirto
    ::liittymalupa-tarkoituksen-kuvaus
    ::liittymalupa-tilapainen
    ::liittymalupa-sijainnin-kuvaus
    ::liittymalupa-arvioitu-kokonaisliikenne
    ::liittymalupa-arvioitu-kuorma-autoliikenne
    ::liittymalupa-nykyisen-liittyman-numero
    ::liittymalupa-nykyisen-liittyman-paivays
    ::liittymalupa-kiinteisto-rn
    ::liittymalupa-muut-kulkuyhteydet
    ::liittymalupa-valmistumisen-takaraja
    ::liittymalupa-kyla
    ::liittymalupa-liittymaohje-liittymakaari
    ::liittymalupa-liittymaohje-leveys-metreissa
    ::liittymalupa-liittymaohje-rumpu
    ::liittymalupa-liittymaohje-rummun-halkaisija-millimetreissa
    ::liittymalupa-liittymaohje-rummun-etaisyys-metreissa
    ::liittymalupa-liittymaohje-odotustila-metreissa
    ::liittymalupa-liittymaohje-nakemapisteen-etaisyys
    ::liittymalupa-liittymaohje-liikennemerkit
    ::liittymalupa-liittymaohje-lisaohjeet})

(def mainosluvan-tiedot
  #{::mainoslupa-mainostettava-asia
    ::mainoslupa-sijainnin-kuvaus
    ::mainoslupa-korvaava-paatos
    ::mainoslupa-tiedoksi-elykeskukselle
    ::mainoslupa-asemakaava-alueella
    ::mainoslupa-suoja-alueen-leveys
    ::mainoslupa-lisatiedot
    ::mainokset})

(def opasteluvan-tiedot
  #{::opastelupa-kohteen-nimi
    ::opastelupa-palvelukohteen-opastaulu
    ::opastelupa-palvelukohteen-osoiteviitta
    ::opastelupa-osoiteviitta
    ::opastelupa-ennakkomerkki
    ::opastelupa-opasteen-teksti
    ::opastelupa-osoiteviitan-tunnus
    ::opastelupa-lisatiedot
    ::opastelupa-kohteen-url-osoite
    ::opastelupa-jatkolupa
    ::opastelupa-alkuperainen-lupanro
    ::opastelupa-alkuperaisen-luvan-alkupvm
    ::opastelupa-alkuperaisen-luvan-loppupvm
    ::opastelupa-nykyinen-opastus
    ::opasteet})

(def suoja-alueen-rakentamisluvan-tiedot
  #{::suoja-aluerakentamislupa-rakennettava-asia
    ::suoja-aluerakentamislupa-lisatiedot
    ::suoja-aluerakentamislupa-esitetty-etaisyys-tien-keskilinjaan
    ::suoja-aluerakentamislupa-vahimmaisetaisyys-tien-keskilinjasta
    ::suoja-aluerakentamislupa-valitoimenpiteet
    ::suoja-aluerakentamislupa-suoja-alueen-leveys
    ::suoja-aluerakentamislupa-suoja-alue
    ::suoja-aluerakentamislupa-nakema-alue
    ::suoja-aluerakentamislupa-kiinteisto-rn})

(def tilapaisen-myyntiluvan-tiedot
  #{::myyntilupa-aihe
    ::myyntilupa-alueen-nimi
    ::myyntilupa-aikaisempi-myyntilupa
    ::myyntilupa-opastusmerkit})

(def tilapaisen-liikennemerkkijarjestelyn-tiedot
  #{::liikennemerkkijarjestely-aihe
    ::liikennemerkkijarjestely-sijainnin-kuvaus
    ::liikennemerkkijarjestely-tapahtuman-tiedot
    ::liikennemerkkijarjestely-nopeusrajoituksen-syy
    ::liikennemerkkijarjestely-lisatiedot-nopeusrajoituksesta
    ::liikennemerkkijarjestely-muut-liikennemerkit
    ::liikennemerkkijarjestelyt})

(def tyoluvan-tiedot
  #{::tyolupa-tyon-sisalto
    ::tyolupa-tyon-saa-aloittaa
    ::tyolupa-viimeistely-oltava
    ::tyolupa-ohjeet-tyon-suorittamiseen
    ::tyolupa-los-puuttuu
    ::tyolupa-ilmoitus-tieliikennekeskukseen
    ::tyolupa-tilapainen-nopeusrajoitus
    ::tyolupa-los-lisatiedot
    ::tyolupa-tieliikennekusksen-sahkopostiosoite})

(def vesihuoltoluvan-tiedot
  #{::vesihuoltolupa-tienylityksia
    ::vesihuoltolupa-silta-asennuksia
    ::johtoasennukset})

(def lupatyypit*
  ^{:private true}
  {:johto-ja-kaapelilupa "johto- ja kaapelilupa"
   :liittymalupa "liittymälupa"
   :mainoslupa "mainoslupa"
   :mainosilmoitus "mainosilmoitus"
   :opastelupa "opastelupa"
   :suoja-aluerakentamislupa "suoja-aluerakentamislupa"
   :tilapainen-myyntilupa "tilapäinen myyntilupa"
   :tilapainen-liikennemerkkijarjestely "tilapäinen liikennemerkkijärjestely"
   :tietyolupa "tietyölupa"
   :vesihuoltolupa "vesihuoltolupa"})

(defn tyyppi-fmt [tyyppi]
  (when-let [tyyppi (get lupatyypit* tyyppi)]
    (str/capitalize tyyppi)))

(def lupatyyppi-vaihtoehdot (keys lupatyypit*))

(s/def ::haettava-tr-osoite (s/keys :opt [::tie ::aet ::aosa ::losa ::let]))
(s/def ::hae-tieluvat-kysely (s/keys :opt [::hakija-nimi
                                           ::tyyppi
                                           ::paatoksen-diaarinumero
                                           ::voimassaolon-alkupvm
                                           ::voimassaolon-loppupvm
                                           ::myontamispvm
                                           ::haettava-tr-osoite]))
(s/def ::hae-tieluvat-vastaus (s/coll-of ::tielupa))

(s/def ::hakuteksti ::hakija-nimi)
(s/def ::hae-tielupien-hakijat-kysely (s/keys :req-un [::hakuteksti]))
(s/def ::hae-tielupien-hakijat-vastaus (s/coll-of ::hakija-nimi))

