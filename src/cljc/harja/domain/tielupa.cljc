(ns harja.domain.tielupa
  (:require
    [clojure.spec.alpha :as s]
    [harja.domain.muokkaustiedot :as muokkaustiedot]

    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
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
    ::katselmus-url
    ::ely
    ::urakka
    ::urakan-nimi
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
   :tilapainen-myyntilupa "tilapäinen-myyntilupa"
   :tilapainen-liikennemerkkijarjestely "tilapäinen-liikennemerkkijärjestely"
   :tietyolupa "tietyölupa"
   :vesihuoltolupa "vesihuoltolupa"})

(defn tyyppi-fmt [tyyppi]
  (when-let [tyyppi (get lupatyypit* tyyppi)]
    (str/capitalize tyyppi)))

(def lupatyyppi-vaihtoehdot (keys lupatyypit*))

(s/def ::hae-tieluvat-kysely (s/keys :opt [::hakija-nimi
                                           ::tyyppi
                                           ::ulkoinen-tunniste
                                           ::voimassaolon-alkupvm
                                           ::voimassaolon-loppupvm
                                           ::myontamispvm
                                           ::sijainnit]))
#?(:clj (s/def ::hae-tieluvat-vastaus (s/keys :opt [::liittymalupa-kyla
                                                    ::muokkaustiedot/muokattu
                                                    ::liittymalupa-liittymaohje-rummun-halkaisija-millimetreissa
                                                    ::liikennemerkkijarjestely-lisatiedot-nopeusrajoituksesta
                                                    ::tienpitoviranomainen-sahkopostiosoite
                                                    ::liittymalupa-haettu-kayttotarkoitus
                                                    ::opastelupa-palvelukohteen-osoiteviitta
                                                    ::mainoslupa-asemakaava-alueella
                                                    ::tyolupa-tieliikennekusksen-sahkopostiosoite
                                                    ::liittymalupa-liittymaohje-liittymakaari
                                                    ::tyolupa-ohjeet-tyon-suorittamiseen
                                                    ::myyntilupa-opastusmerkit
                                                    ::liittymalupa-liittymaohje-rummun-etaisyys-metreissa
                                                    ::liittymalupa-nykyisen-liittyman-numero
                                                    ::johtolupa-silta-asennuksia
                                                    ::opastelupa-osoiteviitan-tunnus
                                                    ::hakija-osasto
                                                    ::myyntilupa-aihe
                                                    ::opastelupa-alkuperainen-lupanro
                                                    ::liittymalupa-valmistumisen-takaraja
                                                    ::liikennemerkkijarjestely-nopeusrajoituksen-syy
                                                    ::suoja-aluerakentamislupa-rakennettava-asia
                                                    ::liittymalupa-liittymaohje-odotustila-metreissa
                                                    ::liittymalupa-liittyman-siirto
                                                    ::liittymalupa-liittymaohje-liikennemerkit
                                                    ::tyolupa-los-puuttuu
                                                    ::valmistumisilmoitus
                                                    ::vesihuoltolupa-silta-asennuksia
                                                    ::liittymalupa-tarkoituksen-kuvaus
                                                    ::tienpitoviranomainen-lupapaallikko
                                                    ::liittymalupa-sijainnin-kuvaus
                                                    ::liittymalupa-liittymaohje-liittymisnakema
                                                    ::liittymalupa-arvioitu-kokonaisliikenne
                                                    ::opastelupa-lisatiedot
                                                    ::kohde-postitoimipaikka
                                                    ::opastelupa-kohteen-nimi
                                                    ::valmistumisilmoitus-palautettu
                                                    ::mainoslupa-lisatiedot
                                                    ::johtoasennukset
                                                    ::opastelupa-osoiteviitta
                                                    ::liikenneohjaajan-sahkopostiosoite
                                                    ::liittymalupa-liittymaohje-lisaohjeet
                                                    ::mainoslupa-tiedoksi-elykeskukselle
                                                    ::myyntilupa-aikaisempi-myyntilupa
                                                    ::liikenneohjaajan-yhteyshenkilo
                                                    ::mainoslupa-suoja-alueen-leveys
                                                    ::suoja-aluerakentamislupa-suoja-alueen-leveys
                                                    ::valmistumisilmoitus-vaaditaan
                                                    ::tienpitoviranomainen-puhelinnumero
                                                    ::voimassaolon-alkupvm
                                                    ::muokkaustiedot/luotu
                                                    ::liittymalupa-tilapainen
                                                    ::tienpitoviranomainen-yhteyshenkilo
                                                    ::suoja-aluerakentamislupa-vahimmaisetaisyys-tien-keskilinjasta
                                                    ::opastelupa-ennakkomerkki
                                                    ::kunta
                                                    ::kohde-lahiosoite
                                                    ::opastelupa-alkuperaisen-luvan-loppupvm
                                                    ::liikenneohjaajan-nimi
                                                    ::paatoksen-diaarinumero
                                                    ::hakija-tyyppi
                                                    ::tyolupa-viimeistely-oltava
                                                    ::liikennemerkkijarjestelyt
                                                    ::urakka
                                                    ::kaapeliasennukset
                                                    ::mainoslupa-korvaava-paatos
                                                    ::opastelupa-kohteen-url-osoite
                                                    ::liikennemerkkijarjestely-tapahtuman-tiedot
                                                    ::urakoitsija-sahkopostiosoite
                                                    ::opastelupa-opasteen-teksti
                                                    ::hakija-postinumero
                                                    ::tyolupa-tyon-sisalto
                                                    ::sijainnit
                                                    ::liikennemerkkijarjestely-aihe
                                                    ::mainokset
                                                    ::tyolupa-tyon-saa-aloittaa
                                                    ::johtolupa-ilmakaapelia-yhteensa
                                                    ::vesihuoltolupa-tienalituksia
                                                    ::urakoitsija-puhelinnumero
                                                    ::liittymalupa-liittymaohje-leveys-metreissa
                                                    ::mainoslupa-sijainnin-kuvaus
                                                    ::liikennemerkkijarjestely-muut-liikennemerkit
                                                    ::suoja-aluerakentamislupa-esitetty-etaisyys-tien-keskilinjaan
                                                    ::otsikko
                                                    ::hakija-postinosoite
                                                    ::suoja-aluerakentamislupa-kiinteisto-rn
                                                    ::opastelupa-alkuperaisen-luvan-alkupvm
                                                    ::urakan-nimi
                                                    ::ely
                                                    ::johtolupa-maakaapelia-yhteensa
                                                    ::kohde-postinumero
                                                    ::id
                                                    ::ulkoinen-tunniste
                                                    ::hakija-maakoodi
                                                    ::saapumispvm
                                                    ::liittymalupa-kiinteisto-rn
                                                    ::liikennemerkkijarjestely-sijainnin-kuvaus
                                                    ::liikenneohjaajan-puhelinnumero
                                                    ::opastelupa-nykyinen-opastus
                                                    ::liittymalupa-nykyisen-liittyman-paivays
                                                    ::katselmus-url
                                                    ::liittymalupa-muut-kulkuyhteydet
                                                    ::suoja-aluerakentamislupa-lisatiedot
                                                    ::vesihuoltolupa-tienylityksia
                                                    ::voimassaolon-loppupvm
                                                    ::hakija-nimi
                                                    ::myontamispvm
                                                    ::opasteet
                                                    ::tyolupa-los-lisatiedot
                                                    ::tienpitoviranomainen-kasittelija
                                                    ::tyyppi
                                                    ::opastelupa-jatkolupa
                                                    ::hakija-sahkopostiosoite
                                                    ::hakija-puhelinnumero
                                                    ::johtolupa-tienylityksia
                                                    ::liittymalupa-liittymaohje-nakemapisteen-etaisyys
                                                    ::tyolupa-ilmoitus-tieliikennekeskukseen
                                                    ::tien-nimi
                                                    ::tyolupa-tilapainen-nopeusrajoitus
                                                    ::johtolupa-tienalituksia
                                                    ::mainoslupa-mainostettava-asia
                                                    ::liittymalupa-liittymaohje-rumpu
                                                    ::urakoitsija-yhteyshenkilo
                                                    ::liittymalupa-myonnetty-kayttotarkoitus
                                                    ::urakoitsija-nimi
                                                    ::suoja-aluerakentamislupa-sijoitus
                                                    ::opastelupa-palvelukohteen-opastaulu
                                                    ::liittymalupa-arvioitu-kuorma-autoliikenne
                                                    ::myyntilupa-alueen-nimi
                                                    ::liitteet])))

(s/def ::hae-tielupien-hakijat-kysely (s/keys :req [::hakija-nimi]))
(s/def ::hae-tielupien-hakijat-vastaus (s/coll-of ::hakija-nimi))

