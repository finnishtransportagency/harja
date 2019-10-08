(ns harja.palvelin.integraatiot.tloik.tyokalut
  (:require [taoensso.timbre :as log]
            [clojure.test :refer [deftest is use-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clj-time
             [core :as t]
             [format :as df]]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.integraatiot.tloik.kasittely.ilmoitus :as ilmoitus]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitus-sanoma :as ilmoitussanoma]
            [clojure.string :as clj-str]
            [harja.kyselyt.konversio :as konv]
            [clojure.set :as set]
            [harja.palvelin.palvelut.urakat :as urakkapalvelu]))

(def nyt (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki"))
                     (t/now)))

(def +xsd-polku+ "xsd/tloik/")
(def +tloik-ilmoitusviestijono+ "tloik-ilmoitusviestijono")
(def +tloik-ilmoituskuittausjono+ "tloik-ilmoituskuittausjono")
(def +tloik-ilmoitustoimenpideviestijono+ "tloik-ilmoitustoimenpideviestijono")
(def +tloik-ilmoitustoimenpidekuittausjono+ "tloik-ilmoitustoimenpidekuittausjono")
(defn testi-ilmoitus-sanoma
  ([] (testi-ilmoitus-sanoma nyt))
  ([ilmoitettu]
   ; 2015-09-29T14:49:45
   (str "<harja:ilmoitus xmlns:harja=\"http://www.liikennevirasto.fi/xsd/harja\">
  <viestiId>10a24e56-d7d4-4b23-9776-2a5a12f254af</viestiId>
  <ilmoitusId>123456789</ilmoitusId>
  <tunniste>UV-1509-1a</tunniste>
  <versionumero>1</versionumero>
  <ilmoitustyyppi>toimenpidepyynto</ilmoitustyyppi>
  <ilmoitettu>" ilmoitettu "</ilmoitettu>
  <urakkatyyppi>hoito</urakkatyyppi>
  <otsikko>Korkeat vallit</otsikko>
  <paikanKuvaus>Jossain kentällä.</paikanKuvaus>
  <lisatieto>Vanhat vallit ovat liian korkeat ja uutta lunta on satanut reippaasti.</lisatieto>
  <yhteydenottopyynto>false</yhteydenottopyynto>
  <sijainti>
  <tienumero>79</tienumero>
  <x>443199</x>
  <y>7377324</y>
  </sijainti>
  <ilmoittaja>
  <etunimi>Matti</etunimi>
  <sukunimi>Meikäläinen</sukunimi>
  <matkapuhelin>08023394852</matkapuhelin>
  <sahkoposti>matti.meikalainen@palvelu.fi</sahkoposti>
  <tyyppi>tienkayttaja</tyyppi>
  </ilmoittaja>
  <lahettaja>
  <etunimi>Pekka</etunimi>
  <sukunimi>Päivystäjä</sukunimi>
  <matkapuhelin>929304449282</matkapuhelin>
  <sahkoposti>pekka.paivystaja@livi.fi</sahkoposti>
  </lahettaja>
  <seliteet>
  <selite>tyomaajarjestelyihinLiittyvaIlmoitus</selite>
  <selite>kuoppiaTiessa</selite>
  <selite>kelikysely</selite>
  <selite>soratienKuntoHuono</selite>
  <selite>saveaTiella</selite>
  <selite>liikennettaVaarantavaEsteTiella</selite>
  <selite>irtokiviaTiella</selite>
  <selite>kevyenLiikenteenVaylaanLiittyvaIlmoitus</selite>
  <selite>raivausJaKorjaustoita</selite>
  <selite>auraustarve</selite>
  <selite>yliauraus</selite>
  <selite>kaivonKansiRikki</selite>
  <selite>kevyenLiikenteenVaylatOvatLiukkaita</selite>
  <selite>routaheitto</selite>
  <selite>avattavatPuomit</selite>
  <selite>tievalaistusVioittunutOnnettomuudessa</selite>
  <selite>muuKyselyTaiNeuvonta</selite>
  <selite>soratienTasaustarve</selite>
  <selite>tieTaiTienReunaOnPainunut</selite>
  <selite>siltaanLiittyvaIlmoitus</selite>
  <selite>polynsidontatarve</selite>
  <selite>liikennevalotEivatToimi</selite>
  <selite>kunnossapitoJaHoitotyo</selite>
  <selite>vettaTiella</selite>
  <selite>aurausvallitNakemaesteena</selite>
  <selite>ennakoivaVaroitus</selite>
  <selite>levahdysalueeseenLiittyvaIlmoitus</selite>
  <selite>sohjonPoisto</selite>
  <selite>liikennekeskusKuitannutLoppuneeksi</selite>
  <selite>muuToimenpidetarve</selite>
  <selite>hiekoitustarve</selite>
  <selite>tietOvatJaatymassa</selite>
  <selite>jaatavaaSadetta</selite>
  <selite>tienvarsilaitteisiinLiittyvaIlmoitus</selite>
  <selite>oljyaTiella</selite>
  <selite>sahkojohtoOnPudonnutTielle</selite>
  <selite>tieOnSortunut</selite>
  <selite>tievalaistusVioittunut</selite>
  <selite>testilahetys</selite>
  <selite>tievalaistuksenLamppujaPimeana</selite>
  <selite>virkaApupyynto</selite>
  <selite>tiemerkintoihinLiittyvaIlmoitus</selite>
  <selite>tulvavesiOnNoussutTielle</selite>
  <selite>niittotarve</selite>
  <selite>kuormaOnLevinnytTielle</selite>
  <selite>tieOnLiukas</selite>
  <selite>tiellaOnEste</selite>
  <selite>harjaustarve</selite>
  <selite>hoylaystarve</selite>
  <selite>tietyokysely</selite>
  <selite>paallystevaurio</selite>
  <selite>rikkoutunutAjoneuvoTiella</selite>
  <selite>mustaaJaataTiella</selite>
  <selite>kevyenLiikenteenVaylillaOnLunta</selite>
  <selite>hirviaitaVaurioitunut</selite>
  <selite>korvauskysely</selite>
  <selite>puitaOnKaatunutTielle</selite>
  <selite>rumpuunLiittyvaIlmoitus</selite>
  <selite>lasiaTiella</selite>
  <selite>liukkaudentorjuntatarve</selite>
  <selite>alikulkukaytavassaVetta</selite>
  <selite>kevyenliikenteenAlikulkukaytavassaVetta</selite>
  <selite>tievalaistuksenLamppuPimeana</selite>
  <selite>kevyenLiikenteenVaylatOvatJaisiaJaLiukkaita</selite>
  <selite>kuoppa</selite>
  <selite>toimenpidekysely</selite>
  <selite>pysakkiinLiittyvaIlmoitus</selite>
  <selite>nakemaalueenRaivaustarve</selite>
  <selite>vesakonraivaustarve</selite>
  <selite>muuttuvatOpasteetEivatToimi</selite>
  <selite>tievalaistus</selite>
  <selite>vesiSyovyttanytTienReunaa</selite>
  <selite>raskasAjoneuvoJumissa</selite>
  <selite>myrskyvaurioita</selite>
  <selite>kaidevaurio</selite>
  <selite>liikennemerkkeihinLiittyvaIlmoitus</selite>
  <selite>siirrettavaAjoneuvo</selite>
  <selite>tielleOnVuotanutNestettaLiikkuvastaAjoneuvosta</selite>
  <selite>tapahtumaOhi</selite>
  <selite>kevyenLiikenteenVaylatOvatjaatymassa</selite>
  <selite>tietOvatjaisiaJamarkia</selite>
  </seliteet>\n</harja:ilmoitus>")))

(defn testi-ilmoitus-sanoma-jossa-ilmoittaja-urakoitsija
  ([] (testi-ilmoitus-sanoma-jossa-ilmoittaja-urakoitsija nyt))
  ([ilmoitettu]
   (str
     "<harja:ilmoitus xmlns:harja=\"http://www.liikennevirasto.fi/xsd/harja\">
     <viestiId>10a24e56-d7d4-4b23-9776-2a5a12f254af</viestiId>
     <ilmoitusId>123456789</ilmoitusId>
     <tunniste>UV-1509-1a</tunniste>
     <versionumero>1</versionumero>
     <ilmoitustyyppi>toimenpidepyynto</ilmoitustyyppi>
     <ilmoitettu>" ilmoitettu "</ilmoitettu>
  <urakkatyyppi>hoito</urakkatyyppi>
  <otsikko>Korkeat vallit</otsikko>
  <paikanKuvaus>Jossain kentällä.</paikanKuvaus>
  <lisatieto>Vanhat vallit ovat liian korkeat ja uutta lunta on satanut reippaasti.</lisatieto>
  <yhteydenottopyynto>false</yhteydenottopyynto>
  <sijainti>
  <tienumero>79</tienumero>
  <x>443199</x>
  <y>7377324</y>
  </sijainti>
  <ilmoittaja>
  <etunimi>Uuno</etunimi>
  <sukunimi>Urakoitsija</sukunimi>
  <matkapuhelin>08023394852</matkapuhelin>
  <sahkoposti>uuno.urakoitsija@example.com</sahkoposti>
  <tyyppi>tienkayttaja</tyyppi>
  </ilmoittaja>
  <lahettaja>
  <etunimi>Pekka</etunimi>
  <sukunimi>Päivystäjä</sukunimi>
  <matkapuhelin>929304449282</matkapuhelin>
  <sahkoposti>pekka.paivystaja@livi.fi</sahkoposti>
  </lahettaja>
  <seliteet>
  <selite>auraustarve</selite>
  <selite>aurausvallitNakemaesteena</selite>
  </seliteet>
  </harja:ilmoitus>")))

(defn testi-valaistusilmoitus-sanoma
  ([] (testi-valaistusilmoitus-sanoma nyt))
  ([ilmoitettu]
   (str
     "<harja:ilmoitus xmlns:harja=\"http://www.liikennevirasto.fi/xsd/harja\">
      <viestiId>14324234</viestiId>
      <ilmoitusId>987654321</ilmoitusId>
      <tunniste>UV-1509-1a</tunniste>
      <versionumero>1</versionumero>
      <ilmoitustyyppi>toimenpidepyynto</ilmoitustyyppi>
      <ilmoitettu>" ilmoitettu "</ilmoitettu>
    <urakkatyyppi>valaistus</urakkatyyppi>
    <otsikko>Valot pimeänä</otsikko>
    <paikanKuvaus>Hailuodossa</paikanKuvaus>
    <lisatieto>Valot ovat pimeänä.</lisatieto>
    <yhteydenottopyynto>false</yhteydenottopyynto>
    <sijainti>
    <tienumero>816</tienumero>
    <x>421076.487</x>
    <y>7206558.394</y>
    </sijainti>
    <ilmoittaja>
    <etunimi>Matti</etunimi>
    <sukunimi>Meikäläinen</sukunimi>
    <matkapuhelin>08023394852</matkapuhelin>
    <sahkoposti>matti.meikalainen@palvelu.fi</sahkoposti>
    <tyyppi>tienkayttaja</tyyppi>
    </ilmoittaja>
    <lahettaja>
    <etunimi>Pekka</etunimi>
    <sukunimi>Päivystäjä</sukunimi>
    <matkapuhelin>929304449282</matkapuhelin>
    <sahkoposti>pekka.paivystaja@livi.fi</sahkoposti>
    </lahettaja>
    <seliteet>
    <selite>tievalaistusVioittunutOnnettomuudessa</selite>
    </seliteet>
    </harja:ilmoitus>
    ")))

(def +testi-paallystysilmoitus-sanoma+
  "<harja:ilmoitus xmlns:harja=\"http://www.liikennevirasto.fi/xsd/harja\">
   <viestiId>14324234</viestiId>
   <ilmoitusId>987654321</ilmoitusId>
   <tunniste>UV-1509-1a</tunniste>
   <versionumero>1</versionumero>
   <ilmoitustyyppi>toimenpidepyynto</ilmoitustyyppi>
   <ilmoitettu>2016-09-21T10:49:45</ilmoitettu>
   <urakkatyyppi>paallystys</urakkatyyppi>
   <otsikko>[TESTI] Päällystys rikkonainen</otsikko>
   <paikanKuvaus>Lauttarannassa</paikanKuvaus>
   <lisatieto>Päällystyksessä reikiä</lisatieto>
   <yhteydenottopyynto>false</yhteydenottopyynto>
   <sijainti>
   <tienumero>816</tienumero>
   <x>418613.894</x>
   <y>7207249.201</y>
   </sijainti>
   <ilmoittaja>
   <etunimi>Matti</etunimi>
   <sukunimi>Meikäläinen</sukunimi>
   <matkapuhelin>08023394852</matkapuhelin>
   <sahkoposti>matti.meikalainen@palvelu.fi</sahkoposti>
   <tyyppi>tienkayttaja</tyyppi>
   </ilmoittaja>
   <lahettaja>
   <etunimi>Pekka</etunimi>
   <sukunimi>Päivystäjä</sukunimi>
   <matkapuhelin>929304449282</matkapuhelin>
   <sahkoposti>pekka.paivystaja@livi.fi</sahkoposti>
   </lahettaja>
   <seliteet>
   <selite>paallystevaurio</selite>
   </seliteet>
   </harja:ilmoitus>")

(defn luo-tloik-komponentti []
  (->Tloik {:ilmoitusviestijono +tloik-ilmoitusviestijono+
            :ilmoituskuittausjono +tloik-ilmoituskuittausjono+
            :toimenpidejono +tloik-ilmoitustoimenpideviestijono+
            :toimenpidekuittausjono +tloik-ilmoitustoimenpidekuittausjono+}
           true))

(def +ilmoitus-ruotsissa+
  (-> (testi-ilmoitus-sanoma-jossa-ilmoittaja-urakoitsija)
      (clj-str/replace "443199" "319130")
      (clj-str/replace "7377324" "7345904")))

(def +ilmoitus-hailuodon-jaatiella+
  (-> (testi-ilmoitus-sanoma "2015-09-29T14:49:45")
      (clj-str/replace "319130" "7186873")
      (clj-str/replace "414212" "7211797")))

(defn hae-ilmoituksen-urakka-id [{:keys [urakkatyyppi sijainti]}]
  (urakkapalvelu/hae-lahin-urakka-id-sijainnilla (:db jarjestelma) urakkatyyppi sijainti))

(defn tuo-ilmoitus []
  (let [ilmoitus (ilmoitussanoma/lue-viesti (testi-ilmoitus-sanoma-jossa-ilmoittaja-urakoitsija))]
    (ilmoitus/tallenna-ilmoitus (:db jarjestelma) (hae-ilmoituksen-urakka-id ilmoitus) ilmoitus)))

(defn tuo-paallystysilmoitus []
  (let [sanoma (clj-str/replace (testi-ilmoitus-sanoma-jossa-ilmoittaja-urakoitsija)
                                       "<urakkatyyppi>hoito</urakkatyyppi>"
                                       "<urakkatyyppi>paallystys</urakkatyyppi>")
        ilmoitus (ilmoitussanoma/lue-viesti sanoma)]
    (ilmoitus/tallenna-ilmoitus (:db jarjestelma) (hae-ilmoituksen-urakka-id ilmoitus) ilmoitus)))

(defn tuo-ilmoitus-teknisista-laitteista []
  (let [sanoma
        (-> (testi-ilmoitus-sanoma-jossa-ilmoittaja-urakoitsija)
            (clj-str/replace "<urakkatyyppi>hoito</urakkatyyppi>" "<urakkatyyppi>tekniset laitteet</urakkatyyppi>")
            (clj-str/replace "<tienumero>79</tienumero>" "<tienumero>4</tienumero>")
            (clj-str/replace "<x>443199</x>" "<x>326269</x>")
            (clj-str/replace "<y>7377324</y>" "<y>6822985</y>"))
        ilmoitus (assoc (ilmoitussanoma/lue-viesti sanoma) :urakkatyyppi "tekniset-laitteet")]
    (ilmoitus/tallenna-ilmoitus (:db jarjestelma) (hae-ilmoituksen-urakka-id ilmoitus) ilmoitus)))

(defn tuo-ilmoitus-siltapalvelusopimukselle []
  (let [sanoma
        (-> (testi-ilmoitus-sanoma-jossa-ilmoittaja-urakoitsija)
            (clj-str/replace "<urakkatyyppi>hoito</urakkatyyppi>" "<urakkatyyppi>silta</urakkatyyppi>")
            (clj-str/replace "<tienumero>79</tienumero>" "<tienumero>4</tienumero>")
            (clj-str/replace "<x>443199</x>" "<x>595754</x>")
            (clj-str/replace "<y>7377324</y>" "<y>6785914</y>"))
        ilmoitus (assoc (ilmoitussanoma/lue-viesti sanoma) :urakkatyyppi "siltakorjaus")]
    (ilmoitus/tallenna-ilmoitus (:db jarjestelma) (hae-ilmoituksen-urakka-id ilmoitus) ilmoitus)))

(defn tuo-valaistusilmoitus []
  (let [ilmoitus (ilmoitussanoma/lue-viesti (testi-valaistusilmoitus-sanoma))]
    (ilmoitus/tallenna-ilmoitus (:db jarjestelma) (hae-ilmoituksen-urakka-id ilmoitus) ilmoitus)))

(defn tuo-ilmoitus-ilman-tienumeroa []
  (let [sanoma (clj-str/replace (testi-ilmoitus-sanoma-jossa-ilmoittaja-urakoitsija) "<tienumero>4</tienumero>" "")
        ilmoitus (ilmoitussanoma/lue-viesti sanoma)]
    (ilmoitus/tallenna-ilmoitus (:db jarjestelma) (hae-ilmoituksen-urakka-id ilmoitus) ilmoitus)))

(defn hae-testi-ilmoitukset []
  (let [vastaus (mapv
                  #(-> %
                       (konv/array->set :selitteet)
                       (set/rename-keys {:ilmoitusid :ilmoitus-id}))
                  (q-map "select * from ilmoitus where ilmoitusid = 123456789;"))]
    vastaus))

(defn hae-valaistusilmoitus []
  (q "select * from ilmoitus where ilmoitusid = 987654321;"))

(defn hae-ilmoitustoimenpide []
  (q "SELECT kuittaustyyppi
FROM ilmoitustoimenpide
WHERE ilmoitus = (SELECT id FROM ilmoitus WHERE ilmoitusid = 123456789)"))

(defn hae-paivystaja []
  (first (q "select id, matkapuhelin from yhteyshenkilo limit 1;")))

(defn tee-testipaivystys [urakka-id]
  (let [yhteyshenkilo (hae-paivystaja)]
    (u (format "INSERT INTO paivystys (alku, loppu, urakka, yhteyshenkilo, varahenkilo, vastuuhenkilo)
    VALUES (now() - interval '1' day, now() + interval '1' day, %s, %s, false, true)" urakka-id (first yhteyshenkilo)))
    yhteyshenkilo))

(defn poista-ilmoitus []
  (u "delete from paivystajatekstiviesti where ilmoitus = (select id from ilmoitus where ilmoitusid = 123456789);")
  (u "delete from ilmoitustoimenpide where ilmoitus = (select id from ilmoitus where ilmoitusid = 123456789);")
  (u "delete from ilmoitus where ilmoitusid = 123456789;"))

(defn poista-valaistusilmoitus []
  (u "delete from paivystajatekstiviesti where ilmoitus = (select id from ilmoitus where ilmoitusid = 987654321);")
  (u "delete from ilmoitustoimenpide where ilmoitus = (select id from ilmoitus where ilmoitusid = 987654321);")
  (u "delete from ilmoitus where ilmoitusid = 987654321;"))

(defn poista-paivystajatekstiviestit []
  (u "delete from paivystajatekstiviesti"))
