(ns harja.domain.infopaneeli
  "Infopaneelin tuloksien spec määrittelyt"
  (:require [clojure.spec :as s]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.tietyoilmoitukset :as t]))

(defmulti infopaneeli-skeema :tyyppi-kartalla)

(defmethod infopaneeli-skeema :tyokone [_]
  (s/keys :req-un [::ensimmainen-havainto ::viimeisin-havainto ::tyokonetyyppi
                   ::organisaationimi ::urakkanimi ::tehtavat]))

(s/def ::ilmoitus
  (s/keys :req-un [::ilmoitusid ::ilmoitettu ::otsikko ::paikankuvaus ::lisatieto ::kuittaukset]))

(defmethod infopaneeli-skeema :toimenpidepyynto [_]
  ::ilmoitus)
(defmethod infopaneeli-skeema :tiedoitus [_]
  ::ilmoitus)
(defmethod infopaneeli-skeema :kysely [_]
  ::ilmoitus)

(defmethod infopaneeli-skeema :varustetoteuma [_]
  (s/keys :req-un [::alkupvm ::tunniste ::tietolaji ::toimenpide ::kuntoluokka]))

(s/def ::yllapitokohde-skeema
  (s/keys :opt-un [::tr/tierekisteriosoite]
          :req-un [::yllapitokohde ::nimi]))
(defmethod infopaneeli-skeema :paallystys [_]
  ::yllapitokohde-skeema)
(defmethod infopaneeli-skeema :paikkaus [_]
  ::yllapitokohde-skeema)

(defmethod infopaneeli-skeema :turvallisuuspoikkeama [_]
  (s/keys :opt-un [::tapahtunut ::paattynyt ::kasitelty
                   ::tyontekijanammattimuu]
          :req-un [::sairaalavuorokaudet ::vakavuusaste ::vammat
                   ::tyontekijanammatti ::kuvaus
                   ::korjaavattoimenpiteet]))

(defmethod infopaneeli-skeema :tarkastus [_]
  (s/keys :opt-un [::vakiohavainnot]
          :req-un [::havainnot ::aika
                   ::tr/tierekisteriosoite]))

(defmethod infopaneeli-skeema :laatupoikkeama [_]
  (s/and
   ;; Joko TR-osoite suoraan tai ylläpitokohde
   (s/or :tr-osoite (s/keys :req-un [::tr/tierekisteriosoite])
         :yllapitokohde (s/keys :req-un [::yllapitokohde]))

   (s/keys :req-un [::aika ::kuvaus ::tekija ::tekijanimi])))

(s/def ::tr ::tr/tierekisteriosoite)

(defmethod infopaneeli-skeema :suljettu-tieosuus [_]
  (s/keys :req-un [::yllapitokohteen-nimi ::yllapitokohteen-numero
                   ::aika ::tr ::kaistat ::ajoradat]))

(defmethod infopaneeli-skeema :toteuma [_]
  (s/keys :opt-un [::aika-pisteessa ::lisatieto ::materiaalit]
          :req-un [::alkanut ::paattynyt ::suorittaja
                   ::tehtavat]))

(defmethod infopaneeli-skeema :silta [_]
  (s/keys :req-un [::siltanimi ::siltatunnus ::tarkastusaika ::tarkasttaja]))

(defmethod infopaneeli-skeema :tietyomaa [_]
  (s/keys :req-un [::yllapitokohteen-nimi ::yllapitokohteen-numero ::aika
                   ::kaistat ::ajoradat ::nopeusrajoitus]))

(defmethod infopaneeli-skeema :tietyoilmoitus [_]
  (s/keys :req [::t/alku ::t/ilmoittaja ::t/urakoitsijayhteyshenkilo ::t/tilaajayhteyshenkilo
                ::t/tyotyypit ::t/osoite ::t/tien-nimi ::t/kunnat ::t/alkusijainnin-kuvaus
                ::t/loppusijainnin-kuvaus ::t/alku ::t/loppu ::t/tyoajat ::t/vaikutussuunta
                ::t/kaistajarjestelyt ::t/nopeusrajoitukset ::t/tienpinnat ::t/kiertotien-pituus
                ::t/kiertotien-mutkaisuus ::t/kiertotienpinnat ::t/liikenteenohjaus
                ::t/liikenteenohjaaja ::t/viivastys-normaali-liikenteessa ::t/viivastys-ruuhka-aikana
                ::t/ajoneuvorajoitukset ::t/huomautukset ::t/ajoittaiset-pysaytykset
                ::t/ajoittain-suljettu-tie ::t/pysaytysten-alku ::t/pysaytysten-loppu
                ::t/lisatietoja ::t/urakoitsijan-nimi]))

;; Infopaneelin tuloksen spec päätetään :tyyppi-kartalla avaimen perusteella
(s/def ::tulos (s/multi-spec infopaneeli-skeema :tyyppi-kartalla))
