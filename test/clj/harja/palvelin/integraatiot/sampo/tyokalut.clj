(ns harja.palvelin.integraatiot.sampo.tyokalut
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.sanomat.sampo-sanoma :as sampo-sanoma]
            [harja.palvelin.integraatiot.sampo.kasittely.hankkeet :as hankkeet]
            [harja.palvelin.integraatiot.sampo.kasittely.urakat :as urakat]
            [harja.palvelin.integraatiot.sampo.kasittely.sopimukset :as sopimukset]
            [harja.palvelin.integraatiot.sampo.kasittely.toimenpiteet :as toimenpiteet]
            [harja.palvelin.integraatiot.sampo.kasittely.organisaatiot :as organisaatiot]
            [harja.palvelin.integraatiot.sampo.kasittely.yhteyshenkilot :as yhteyshenkilot]
            [harja.testi :as testi]
            [clojure.string :as str])
  (:import (javax.jms TextMessage)))

(def +testihanke-sanoma+
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
  <Sampo2harja xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"SampToharja.xsd\">
    <Program id=\"TESTIHANKE\" manager_Id=\"A010098\" manager_User_Name=\"A010098\" message_Id=\"HankeMessageId\"
      name=\"Testi alueurakka 2009-2014\" schedule_finish=\"2013-12-31T00:00:00.0\"
      schedule_start=\"2009-01-01T00:00:00.0\" vv_alueurakkanro=\"TH-123\" vv_code=\"14-1177\">
      <documentLinks/>
    </Program>
  </Sampo2harja>")

(def +testi-hoitourakka-sanoma+
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
    <Sampo2harja xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"SampToharja.xsd\">
      <Project id=\"TESTIURAKKA\" message_Id=\"UrakkaMessageId\" name=\"Testiurakka\" resourceId=\"TESTIHENKILO\"
        programId=\"TESTIHANKE\" vv_transferred_harja=\"2006-08-19T20:27:14+03:00\"
        schedule_start=\"2013-01-01T08:00:00.0\" schedule_finish=\"2020-12-31T17:00:00.0\"
        financialDepartmentHash=\"KP981303\"
        vv_alueurakkanro=\"TH-123\">
        <documentLinks/>
      </Project>
    </Sampo2harja>")

(def +testi-maanteiden-hoidon-urakka-sanoma+
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
    <Sampo2harja xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"SampToharja.xsd\">
      <Project id=\"TESTIURAKKA\" message_Id=\"UrakkaMessageId\" name=\"Testiurakka\" resourceId=\"TESTIHENKILO\"
        programId=\"TESTIHANKE\" vv_transferred_harja=\"2019-09-19T20:27:14+03:00\"
        schedule_start=\"2019-10-01T08:00:00.0\" schedule_finish=\"2024-09-30T17:00:00.0\"
        financialDepartmentHash=\"KP981303\"
        vv_alueurakkanro=\"THJ-321\">
        <documentLinks/>
      </Project>
    </Sampo2harja>")

(def +testi-paallystysurakka-sanoma+
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
    <Sampo2harja xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"SampToharja.xsd\">
      <Project id=\"TESTIURAKKA\" message_Id=\"UrakkaMessageId\" name=\"Testiurakka\" resourceId=\"TESTIHENKILO\"
        programId=\"muho1\" vv_transferred_harja=\"2006-08-19T20:27:14+03:00\"
        schedule_start=\"2013-01-01T08:00:00.0\" schedule_finish=\"2020-12-31T17:00:00.0\"
        financialDepartmentHash=\"KP981303\"
        vv_alueurakkanro=\"TYP-456\">
        <documentLinks/>
      </Project>
    </Sampo2harja>")

(def +testi-tiemerkintasurakka-sanoma+
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
    <Sampo2harja xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"SampToharja.xsd\">
      <Project id=\"TESTIURAKKA\" message_Id=\"UrakkaMessageId\" name=\"Testiurakka\" resourceId=\"TESTIHENKILO\"
        programId=\"tiem1\" vv_transferred_harja=\"2006-08-19T20:27:14+03:00\"
        schedule_start=\"2013-01-01T08:00:00.0\" schedule_finish=\"2020-12-31T17:00:00.0\"
        financialDepartmentHash=\"KP981303\"
        vv_alueurakkanro=\"TYT-789\">
        <documentLinks/>
      </Project>
    </Sampo2harja>")

(def +testi-valaistusurakka-sanoma+
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
    <Sampo2harja xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"SampToharja.xsd\">
      <Project id=\"TESTIURAKKA\" message_Id=\"UrakkaMessageId\" name=\"Testiurakka\" resourceId=\"TESTIHENKILO\"
        programId=\"valai1\" vv_transferred_harja=\"2006-08-19T20:27:14+03:00\"
        schedule_start=\"2013-01-01T08:00:00.0\" schedule_finish=\"2020-12-31T17:00:00.0\"
        financialDepartmentHash=\"KP981303\"
        vv_alueurakkanro=\"TYV-987\">
        <documentLinks/>
      </Project>
    </Sampo2harja>")

(def +testisopimus-sanoma+
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
    <Sampo2harja xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"SampToharja.xsd\">
      <Order contactId=\"\" contractPartyId=\"TESTIORG\" id=\"TESTISOPIMUS\" messageId=\"OrganisaatioMessageId\"
             name=\"Testisopimus\" projectId=\"TESTIURAKKA\" schedule_finish=\"2013-10-31T00:00:00.0\"
             schedule_start=\"2013-09-02T00:00:00.0\" vv_code=\"\" vv_dno=\"-\">
        <documentLinks/>
    </Order>
  </Sampo2harja>")

(def +testiorganisaatio-sanoma+ "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Sampo2harja xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"SampToharja.xsd\">
    <Company id=\"TESTIORG\" messageId=\"OrganisaatioMessageId\" name=\"Testi Oy\" vv_corporate_id=\"3214527-8\">
        <contactInformation address=\"Katu 1\" city=\"Helsinki\" postal_Code=\"00100\" type=\"main\"/>
        <contactPersons id=\"\" first_name=\"\" family_name=\"\" yhthlo_puh=\"\" yht_sposti=\"\"/>
    </Company>
</Sampo2harja>")

(def +testitoimenpide-sanoma+ "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Sampo2harja xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"SampToharja.xsd\">
    <Operation financialDepartmentHash=\"KP921303\"
               financialDepartmentOBS=\"/Liikennevirasto/ELYT, TOA/Varsinais-Suomen ELY, OSA/VAR Tienpidon hankinnat, YK/VAR Tienpidon hankinnat, KP\"
               id=\"TESTITOIMENPIDE\" managerId=\"A009864\" messageId=\"ToimenpideMessageId\"
               name=\"TESTITOIMENPIDE\" productHash=\"\" productOBS=\"\" projectId=\"TESTIURAKKA\"
               schedule_finish=\"2015-12-31T23:59:59.0\" schedule_start=\"2010-01-01T00:00:00.0\" vv_code=\"THIGT-2-1515-2\"
               vv_operation=\"20179\">
        <documentLinks/>
    </Operation>
</Sampo2harja>")

(def +testiyhteyshenkilo-sanoma+ "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Sampo2harja xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"SampToharja.xsd\">
    <Resource department_obs_path=\"\" first_name=\"Teuvo\" id=\"TESTIHENKILO\" last_name=\"Testi\"
              message_Id=\"HenkiloMessageId\" user_Name=\"Teuvo, Testi\">
        <contactInformation address1=\"\" city=\"\" email=\"teuvo.testi@foo.bar\" postal_Code=\"\"/>
    </Resource>
</Sampo2harja>")

(def +testi-duplikaatti-toimenpide-sanoma+ "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Sampo2harja xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"SampToharja.xsd\">
    <Operation financialDepartmentHash=\"KP921303\"
               financialDepartmentOBS=\"/Liikennevirasto/ELYT, TOA/Varsinais-Suomen ELY, OSA/VAR Tienpidon hankinnat, YK/VAR Tienpidon hankinnat, KP\"
               id=\"TESTIDUPTOIMENPI\" managerId=\"A009864\" messageId=\"ToimenpideMessageId\"
               name=\"TESTIDUPTOIMENPI\" productHash=\"\" productOBS=\"\" projectId=\"TESTIURAKKA\"
               schedule_finish=\"2015-12-31T23:59:59.0\" schedule_start=\"2010-01-01T00:00:00.0\" vv_code=\"THIGT-2-1515-2\"
               vv_operation=\"20179\">
        <documentLinks/>
    </Operation>
</Sampo2harja>")

(def +testi-toimenpidekooditon-toimenpide-sanoma+ "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Sampo2harja xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"SampToharja.xsd\">
    <Operation financialDepartmentHash=\"KP921303\"
               financialDepartmentOBS=\"/Liikennevirasto/ELYT, TOA/Varsinais-Suomen ELY, OSA/VAR Tienpidon hankinnat, YK/VAR Tienpidon hankinnat, KP\"
               id=\"TESTITPKTPI\" managerId=\"A009864\" messageId=\"ToimenpideMessageId\"
               name=\"TESTITPKTPI\" productHash=\"\" productOBS=\"\" projectId=\"TESTIURAKKA\"
               schedule_finish=\"2015-12-31T23:59:59.0\" schedule_start=\"2010-01-01T00:00:00.0\" vv_code=\"\"
               vv_operation=\"\">
        <documentLinks/>
    </Operation>
</Sampo2harja>")

(def +testi-toimenpide-sanoma-virheellinen-toimenpidekoodi+ "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Sampo2harja xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"SampToharja.xsd\">
    <Operation financialDepartmentHash=\"KP921303\"
               financialDepartmentOBS=\"/Liikennevirasto/ELYT, TOA/Varsinais-Suomen ELY, OSA/VAR Tienpidon hankinnat, YK/VAR Tienpidon hankinnat, KP\"
               id=\"TESTITPKTPI2\" managerId=\"A009864\" messageId=\"ToimenpideMessageId\"
               name=\"TESTITPKTPI2\" productHash=\"\" productOBS=\"\" projectId=\"TESTIURAKKA\"
               schedule_finish=\"2015-12-31T23:59:59.0\" schedule_start=\"2010-01-01T00:00:00.0\" vv_code=\"\"
               vv_operation=\"31114\">
        <documentLinks/>
    </Operation>
</Sampo2harja>")

(def +lahetysjono-sisaan+ "lahetysjono-sisaan")
(def +kuittausjono-sisaan+ "kuittausjono-sisaan")
(def +lahetysjono-ulos+ "lahetysjono-ulos")
(def +kuittausjono-ulos+ "kuittausjono-ulos")

(defn tee-viesti [sisalto]
  (reify TextMessage
    (getText [this] sisalto)))

(defn tuo-hanke []
  (let [hankkeet (:hankkeet (sampo-sanoma/lue-viesti +testihanke-sanoma+))]
    (hankkeet/kasittele-hankkeet testi/ds hankkeet)))

(defn poista-hanke []
  (u "update urakka set hanke = null where hanke_sampoid = 'TESTIHANKE'")
  (u "delete from hanke where sampoid = 'TESTIHANKE'"))

(defn hae-hankkeet []
  (q "select id from hanke where sampoid = 'TESTIHANKE';"))

(defn tuo-urakka
  ([] (tuo-urakka nil))
  ([sampo-id]
   (let [sanoma (if sampo-id
                  (str/replace +testi-hoitourakka-sanoma+ "TESTIURAKKA" sampo-id )
                  +testi-hoitourakka-sanoma+)
         urakat (:urakat (sampo-sanoma/lue-viesti sanoma))]
     (urakat/kasittele-urakat testi/ds urakat))))

(defn tuo-maanteiden-hoidon-urakka
  ([] (tuo-maanteiden-hoidon-urakka nil))
  ([sampo-id]
   (let [sanoma (if sampo-id
                  (str/replace +testi-maanteiden-hoidon-urakka-sanoma+ "TESTIURAKKA" sampo-id )
                  +testi-maanteiden-hoidon-urakka-sanoma+)
         urakat (:urakat (sampo-sanoma/lue-viesti sanoma))]
     (urakat/kasittele-urakat testi/ds urakat))))

(defn poista-urakka []
  (u "update sopimus set urakka = null where urakka in (select id from urakka where sampoid = 'TESTIURAKKA')")
  (u "delete from yhteyshenkilo_urakka where urakka = (select id from urakka where sampoid = 'TESTIURAKKA')")
  (u "delete from valitavoite where urakka = (select id from urakka where sampoid = 'TESTIURAKKA')")
  (u "delete from urakka where sampoid = 'TESTIURAKKA'"))

(defn hae-urakat []
  (q "select id from urakka where sampoid = 'TESTIURAKKA';"))

(defn hae-urakan-tyyppi []
  (first (first (q "select tyyppi from urakka where sampoid = 'TESTIURAKKA';"))))

(defn hae-urakan-hallintayksikon-nimi []
  (first (first (q "SELECT o.nimi FROM urakka u INNER JOIN organisaatio o on o.id = u.hallintayksikko WHERE u.sampoid = 'TESTIURAKKA';"))))

(defn onko-yhteyshenkilo-sidottu-urakkaan? []
  (first (first (q "SELECT exists(
    SELECT id
    FROM yhteyshenkilo_urakka
    WHERE rooli = 'Sampo yhteyshenkilö' AND
          urakka = (SELECT id
                    FROM urakka
                    WHERE sampoid = 'TESTIURAKKA'));"))))

(defn tuo-sopimus []
  (let [sopimukset (:sopimukset (sampo-sanoma/lue-viesti +testisopimus-sanoma+))]
    (sopimukset/kasittele-sopimukset testi/ds sopimukset)))

(defn poista-sopimus []
  (u "delete from sopimus where sampoid = 'TESTISOPIMUS'"))

(defn tuo-alisopimus []
  (let [sopimukset (:sopimukset (sampo-sanoma/lue-viesti (clojure.string/replace +testisopimus-sanoma+ "TESTISOPIMUS" "TESTIALISOPIMUS")))]
    (sopimukset/kasittele-sopimukset testi/ds sopimukset)))

(defn onko-alisopimus-liitetty-paasopimukseen? []
  (first (first (q "SELECT exists(SELECT id
              FROM sopimus
              WHERE paasopimus = (SELECT id
                                  FROM sopimus
                                  WHERE sampoid = 'TESTISOPIMUS'))"))))

(defn onko-sopimus-sidottu-urakkaan? []
  (first (first (q "SELECT exists(SELECT id
              FROM sopimus
              WHERE urakka = (SELECT id
                                  FROM urakka
                                  WHERE sampoid = 'TESTIURAKKA'))"))))

(defn hae-sopimukset []
  (q "select id from sopimus where sampoid = 'TESTISOPIMUS';"))

(defn poista-alisopimus []
  (u "delete from sopimus where sampoid = 'TESTIALISOPIMUS'"))

(defn onko-urakoitsija-asetettu-urakalle? []
  (first (first (q "SELECT exists(SELECT id
              FROM urakka
              WHERE urakoitsija = (SELECT id
                              FROM organisaatio
                              WHERE sampoid = 'TESTIORG'));"))))

(defn tuo-toimenpide []
  (let [toimenpiteet (:toimenpideinstanssit (sampo-sanoma/lue-viesti +testitoimenpide-sanoma+))]
    (toimenpiteet/kasittele-toimenpiteet testi/ds toimenpiteet)))

(defn tuo-duplikaatti-toimenpide []
  (let [toimenpiteet (:toimenpideinstanssit (sampo-sanoma/lue-viesti +testi-duplikaatti-toimenpide-sanoma+))]
    (toimenpiteet/kasittele-toimenpiteet testi/ds toimenpiteet)))

(defn tuo-toimenpidekooditon-toimenpide []
  (let [toimenpiteet (:toimenpideinstanssit (sampo-sanoma/lue-viesti +testi-toimenpidekooditon-toimenpide-sanoma+))]
    (toimenpiteet/kasittele-toimenpiteet testi/ds toimenpiteet)))

(defn tuo-toimenpide-virheellinen-toimenpidekoodi []
  (let [toimenpiteet (:toimenpideinstanssit (sampo-sanoma/lue-viesti +testi-toimenpide-sanoma-virheellinen-toimenpidekoodi+))]
    (toimenpiteet/kasittele-toimenpiteet testi/ds toimenpiteet)))

(defn poista-toimenpide []
  (u "DELETE FROM kustannussuunnitelma
      WHERE maksuera in (SELECT numero
                        FROM maksuera
                        WHERE toimenpideinstanssi = (
                          SELECT id
                          FROM toimenpideinstanssi
                          WHERE sampoid = 'TESTITOIMENPIDE'));")
  (u "Delete FROM maksuera
             WHERE toimenpideinstanssi = (
                   SELECT id
                   FROM toimenpideinstanssi
                   WHERE sampoid = 'TESTITOIMENPIDE');")
  (u "delete from toimenpideinstanssi where sampoid = 'TESTITOIMENPIDE'"))

(defn hae-toimenpiteet []
  (q "select id from toimenpideinstanssi where sampoid = 'TESTITOIMENPIDE';"))

(defn onko-urakka-sidottu-toimenpiteeseen? []
  (first (first (q "SELECT exists(SELECT id
              FROM toimenpideinstanssi
              WHERE urakka = (SELECT id
                              FROM urakka
                              WHERE sampoid = 'TESTIURAKKA'));"))))

(defn hae-maksuerat []
  (q "SELECT numero
      FROM maksuera
      WHERE toimenpideinstanssi = (
        SELECT id
        FROM toimenpideinstanssi
        WHERE sampoid = 'TESTITOIMENPIDE');"))

(defn hae-kustannussuunnitelmat []
  (q "SELECT maksuera
      FROM kustannussuunnitelma
      WHERE maksuera in (SELECT numero
                        FROM maksuera
                        WHERE toimenpideinstanssi = (
                          SELECT id
                          FROM toimenpideinstanssi
                          WHERE sampoid = 'TESTITOIMENPIDE'));"))

(defn hae-maksuerien-nimet []
  (q "SELECT nimi
      FROM maksuera
      WHERE toimenpideinstanssi = (
        SELECT id
        FROM toimenpideinstanssi
        WHERE sampoid = 'TESTITOIMENPIDE');"))

(defn tuo-organisaatio []
  (let [organisaatiot (:organisaatiot (sampo-sanoma/lue-viesti +testiorganisaatio-sanoma+))]
    (organisaatiot/kasittele-organisaatiot testi/ds organisaatiot)))

(defn poista-organisaatio []
  (u "update urakka set urakoitsija = null where urakoitsija in  (select id from organisaatio where sampoid = 'TESTIORG') ")
  (u "delete from organisaatio where sampoid = 'TESTIORG';"))

(defn hae-organisaatiot []
  (q "select id, tyyppi from organisaatio where sampoid = 'TESTIORG';"))

(defn tuo-yhteyshenkilo []
  (let [yhteyshenkilot (:yhteyshenkilot (sampo-sanoma/lue-viesti +testiyhteyshenkilo-sanoma+))]
    (yhteyshenkilot/kasittele-yhteyshenkilot testi/ds yhteyshenkilot)))

(defn poista-yhteyshenkilo []
  (u "delete from yhteyshenkilo_urakka where yhteyshenkilo = (select id from yhteyshenkilo where sampoid = 'TESTIHENKILO');")
  (u "delete from yhteyshenkilo where sampoid = 'TESTIHENKILO';"))

(defn hae-yhteyshenkilot []
  (q "select id from yhteyshenkilo where sampoid = 'TESTIHENKILO';"))

(defn onko-yhteyshenkilo-asetettu-urakalle? []
  (first (first (q "SELECT exists(
    SELECT id
    FROM urakka
    WHERE id = (
      SELECT urakka
      FROM yhteyshenkilo_urakka
      WHERE yhteyshenkilo = (
        SELECT id
        FROM yhteyshenkilo
        WHERE sampoid = 'TESTIHENKILO')));"))))
