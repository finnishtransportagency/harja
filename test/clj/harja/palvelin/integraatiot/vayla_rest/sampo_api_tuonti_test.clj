(ns harja.palvelin.integraatiot.vayla-rest.sampo-api-tuonti-test
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [com.stuartsierra.component :as component]
            [harja.integraatio :as integraatio]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.sampo.tyokalut :as sampo-tyokalut]
            [clojure.data.zip.xml :as z]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.vayla-rest.sampo-api :as sampo-api]
            [clojure.string :as str]))

(def sampo-vastaanotto-url "/sampo/api/harja")
(def kayttaja "destia")
(def kayttaja-yit "yit-rakennus")
(def +xsd-polku+ "xsd/sampo/inbound/")

(defonce asetukset integraatio/api-sampo-asetukset)

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-sampo (component/using
                      (sampo-api/->ApiSampo asetukset)
                      [:http-palvelin :db :integraatioloki])))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

;; Helpperit
(defn hae-urakat-sampoidlla [sampoid]
  (q (format "select id from urakka where sampoid = '%s';" sampoid)))

(defn hae-integraatiotapahtumat-tietokannasta []
  (q-map (str "SELECT it.id, it.integraatio, it.alkanut, it.paattynyt, it.onnistunut, i.jarjestelma, i.nimi
            FROM integraatiotapahtuma it JOIN integraatio i ON i.id = it.integraatio order by it.id DESC;")))

(defn hae-integraatioviesti-tietokannasta [integraatiotapahtuma-id]
  (first (q-map (str "SELECT iv.id, iv.suunta, iv.sisaltotyyppi, iv.siirtotyyppi, iv.sisalto, iv.otsikko, iv.parametrit, iv.osoite
            FROM integraatioviesti iv WHERE iv.integraatiotapahtuma = "integraatiotapahtuma-id";" ))))

(defn- tarkista-lahetettava-messageid [viesti message-id] ;"UrakkaMessageId"
  (let [data (xml/lue viesti)]
    (is (= message-id (first (z/xml-> data
                                      (fn [kuittaus]
                                        (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :MessageId))))))
      "Kuittaus on tehty oikeaan viestiin.")))

(defn- tarkista-lahetettava-object-type [viesti object-type] ;"Project"
  (let [data (xml/lue viesti)]
    (is (= object-type (first (z/xml-> data
                              (fn [kuittaus]
                                (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :ObjectType))))))
      (str "Kuittauksen tyyppi on " object-type))))

(defn- tarkista-virheet [viesti]
  (let [data (xml/lue viesti)]
    (is (= "NA" (first (z/xml-> data
                         (fn [kuittaus]
                           (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :ErrorCode))))))
      "Virheitä ei tapahtunut käsittelyssä.")))

(defn- onko-valid-vastaus [viesti]
  (is (xml/validi-xml? +xsd-polku+ "HarjaToSampoAcknowledgement.xsd" viesti) "Kuittaus on validia XML:ää."))

;; Testit

(deftest tarkista-viestin-kasittely-ja-kuittaukset
  (with-redefs [t/now #(t/first-day-of-the-month 2017 2)]
    (let [urakat-alkuun (hae-urakat-sampoidlla "TESTIURAKKA")
          vastaus (api-tyokalut/post-kutsu [sampo-vastaanotto-url] kayttaja-yit portti
                    sampo-tyokalut/+testi-hoitourakka-sanoma+ nil true)
          sampo-urakat-loppuun (hae-urakat-sampoidlla "TESTIURAKKA")]
      (is (= 0 (count urakat-alkuun)) "TESTIURAKKA Sampo ID:llä ei löydy urakkaa ennen tuontia.")

      (onko-valid-vastaus (:body vastaus))
      (tarkista-lahetettava-messageid (:body vastaus) "UrakkaMessageId")
      (tarkista-virheet (:body vastaus))
      (tarkista-lahetettava-object-type (:body vastaus) "Project")
      (is (= 1 (count sampo-urakat-loppuun)) "Viesti on käsitelty ja tietokannasta löytyy urakka Sampo id:llä."))


    (let [urakan-tpi (ffirst (q "SELECT id
                                  FROM toimenpideinstanssi
                                  WHERE urakka = (SELECT id FROM urakka WHERE sampoid = 'TESTIURAKKA')
                                  AND nimi = 'Päällystyksen yksikköhintaiset työt'"))]
      (is (nil? urakan-tpi) "Urakalle ei luotu toimenpideinstanssia"))

    (let [urakan-valitavoitteet (map first (q "SELECT nimi
                                  FROM valitavoite
                                  WHERE urakka = (SELECT id FROM urakka WHERE sampoid = 'TESTIURAKKA')"))]

      (is (= (count urakan-valitavoitteet) 6))
      ;; Kertaluontoiset lisättiin kerran
      (is (= (count (filter #(= "Koko Suomi aurattu" %) urakan-valitavoitteet)) 1))
      (is (= (count (filter #(= "Kaikkien urakoiden kalusto huollettu" %) urakan-valitavoitteet)) 1))
      ;; Toistuvat lisättiin jokaiselle urakan jäljellä olevalle vuodelle (huomio testissä feikattu nykyaika)
      (is (= (count (filter #(= "Koko Suomen liikenneympäristö hoidettu" %) urakan-valitavoitteet)) 4)))))

(deftest tarkista-paallystysurakan-toimenpideinstanssin-luonti
  (let [integ-tapahtumat-alkuun (hae-integraatiotapahtumat-tietokannasta)
        sampo-urakat-alkuun (hae-urakat-sampoidlla "TESTIURAKKA")
        vastaus (api-tyokalut/post-kutsu [sampo-vastaanotto-url] kayttaja-yit portti
                     sampo-tyokalut/+testi-paallystysurakka-sanoma+ nil true)
        sampo-urakat-loppuun (hae-urakat-sampoidlla "TESTIURAKKA")
        integ-tapahtumat-loppuun (hae-integraatiotapahtumat-tietokannasta)]
    (is (empty? integ-tapahtumat-alkuun))
    (is (not (empty? integ-tapahtumat-loppuun)))
    (is (= "sampo-api" (:jarjestelma (first integ-tapahtumat-loppuun))))
    (is (= "sisaanluku" (:nimi (first integ-tapahtumat-loppuun))))
    (is (true? (:onnistunut (first integ-tapahtumat-loppuun))))

    (is (= 0 (count sampo-urakat-alkuun)) "TESTIURAKKA Sampo ID:llä ei löydy urakkaa ennen tuontia.")
    (is (= 1 (count sampo-urakat-loppuun)) "Viesti on käsitelty ja tietokannasta löytyy urakka Sampo id:llä.")

    (onko-valid-vastaus (:body vastaus))
    (tarkista-lahetettava-messageid (:body vastaus) "UrakkaMessageId")
    (tarkista-virheet (:body vastaus))
    (tarkista-lahetettava-object-type (:body vastaus) "Project")


    (let [urakan-tpi (ffirst (q "SELECT id
                                  FROM toimenpideinstanssi
                                  WHERE urakka = (SELECT id FROM urakka WHERE sampoid = 'TESTIURAKKA')
                                  AND toimenpide = (SELECT id FROM toimenpidekoodi WHERE koodi = 'PAAL_YKSHINT')"))]
      (is (some? urakan-tpi) "Urakalle on luotu toimenpideinstanssi"))))

(deftest tarkista-tiemerkintaurakan-toimenpideinstanssin-luonti
  (let [sampo-urakat-alkuun (hae-urakat-sampoidlla "TESTIURAKKA")
        vastaus (api-tyokalut/post-kutsu [sampo-vastaanotto-url] kayttaja-yit portti
                  sampo-tyokalut/+testi-tiemerkintasurakka-sanoma+ nil true)
        sampo-urakat-loppuun (hae-urakat-sampoidlla "TESTIURAKKA")]

    (is (= 0 (count sampo-urakat-alkuun)) "TESTIURAKKA Sampo ID:llä ei löydy urakkaa ennen tuontia.")
    (onko-valid-vastaus (:body vastaus))
    (tarkista-lahetettava-messageid (:body vastaus) "UrakkaMessageId")
    (tarkista-virheet (:body vastaus))
    (tarkista-lahetettava-object-type (:body vastaus) "Project")

    (is (= 1 (count sampo-urakat-loppuun)) "Viesti on käsitelty ja tietokannasta löytyy urakka Sampo id:llä.")
    (let [urakan-tpi (ffirst (q "SELECT id
                                  FROM toimenpideinstanssi
                                  WHERE urakka = (SELECT id FROM urakka WHERE sampoid = 'TESTIURAKKA')
                                  AND toimenpide = (SELECT id FROM toimenpidekoodi WHERE koodi = 'TIEM_YKSHINT')"))]
      (is (some? urakan-tpi) "Urakalle on luotu toimenpideinstanssi"))))

(deftest tarkista-valaistusurakan-toimenpideinstanssin-luonti
  (let [sampo-urakat-alkuun (hae-urakat-sampoidlla "TESTIURAKKA")
        vastaus (api-tyokalut/post-kutsu [sampo-vastaanotto-url] kayttaja-yit portti
                  sampo-tyokalut/+testi-valaistusurakka-sanoma+ nil true)
        sampo-urakat-loppuun (hae-urakat-sampoidlla "TESTIURAKKA")]

    (is (= 0 (count sampo-urakat-alkuun)) "TESTIURAKKA Sampo ID:llä ei löydy urakkaa ennen tuontia.")
    (onko-valid-vastaus (:body vastaus))
    (tarkista-lahetettava-messageid (:body vastaus) "UrakkaMessageId")
    (tarkista-virheet (:body vastaus))
    (tarkista-lahetettava-object-type (:body vastaus) "Project")
    (is (= 1 (count sampo-urakat-loppuun)) "Viesti on käsitelty ja tietokannasta löytyy urakka Sampo id:llä.")
    (let [urakan-tpi (ffirst (q "SELECT id
                                  FROM toimenpideinstanssi
                                  WHERE urakka = (SELECT id FROM urakka WHERE sampoid = 'TESTIURAKKA')
                                  AND toimenpide = (SELECT id FROM toimenpidekoodi WHERE koodi = 'VALA_YKSHINT')"))]
      (is (some? urakan-tpi) "Urakalle on luotu toimenpideinstanssi"))))

(deftest sampo-harja-kutsu-epaonnistuu-test
  (let [integ-tapahtumat-alkuun (hae-integraatiotapahtumat-tietokannasta)
        virheellinen-sanoma (slurp "test/resurssit/sampo/Sampo2Harja_virheellinen_testisanoma.xml")
        vastaus (api-tyokalut/post-kutsu [sampo-vastaanotto-url] kayttaja-yit portti virheellinen-sanoma nil true)
        integ-tapahtumat-lopuksi (hae-integraatiotapahtumat-tietokannasta)]

    (is (empty? integ-tapahtumat-alkuun))
    (is (not (empty? integ-tapahtumat-lopuksi)))
    (is (= "sampo-api" (:jarjestelma (first integ-tapahtumat-lopuksi))))
    (is (= "sisaanluku" (:nimi (first integ-tapahtumat-lopuksi))))
    (is (false? (:onnistunut (first integ-tapahtumat-lopuksi))))))

(deftest sampo-harja-kutsu-vaarallinen-sisalto-test
  (let [integ-tapahtumat-alkuun (hae-integraatiotapahtumat-tietokannasta)
        vaarallinen-sisalto "<![CDATA[<IMG SRC=http://www.example.com/siteLogo.gif onmouseover=javascript:alert(‘XSS’);>]]>"
        vastaus (api-tyokalut/post-kutsu [sampo-vastaanotto-url] kayttaja-yit portti vaarallinen-sisalto nil true)
        integ-tapahtumat-lopuksi (hae-integraatiotapahtumat-tietokannasta)
        integraatiotapahtuma-id (:id (first integ-tapahtumat-lopuksi))
        integraatioviesti (hae-integraatioviesti-tietokannasta integraatiotapahtuma-id)]

    (is (empty? integ-tapahtumat-alkuun))
    (is (not (empty? integ-tapahtumat-lopuksi)))
    (is (= "sampo-api" (:jarjestelma (first integ-tapahtumat-lopuksi))))
    (is (= "sisaanluku" (:nimi (first integ-tapahtumat-lopuksi))))
    (is (false? (:onnistunut (first integ-tapahtumat-lopuksi))))

    ;; Varmistetaan, että todellinen virhe saadaan tallennettua integraatioviestiksi.
    (is (str/includes? integraatioviesti "javascript"))
    (is (= "HTTP" (:siirtotyyppi integraatioviesti)))
    (is (= "sisään" (:suunta integraatioviesti)))))

(deftest lisaa-toimenpiden-instassi-tuonti-onnistuu
  (let [aineisto (slurp "test/resurssit/sampo/Sampo2Harja_onnistunut_aineisto.xml")
        maara-ennen (count (q-map "SELECT id FROM toimenpideinstanssi"))
        vastaus (api-tyokalut/post-kutsu [sampo-vastaanotto-url] kayttaja-yit portti aineisto nil true)
        maara-jalkeen (count (q-map "SELECT id FROM toimenpideinstanssi"))]
    (is (= (+ 1 maara-ennen) maara-jalkeen))
    (onko-valid-vastaus (:body vastaus))
    (tarkista-lahetettava-messageid (:body vastaus) "38068257-OP-PR00043638")
    (tarkista-virheet (:body vastaus))
    (tarkista-lahetettava-object-type (:body vastaus) "Operation")))

(deftest laheta-www-form-urlencoded-viesti-test
  (let [aineisto (slurp "test/resurssit/sampo/Sampo2Harja_onnistunut_aineisto.xml")
        maara-ennen (count (q-map "SELECT id FROM toimenpideinstanssi"))
        vastaus (api-tyokalut/post-kutsu [sampo-vastaanotto-url] kayttaja-yit portti aineisto nil true)
        maara-jalkeen (count (q-map "SELECT id FROM toimenpideinstanssi"))]
    (is (= (+ 1 maara-ennen) maara-jalkeen))
    (onko-valid-vastaus (:body vastaus))
    (tarkista-lahetettava-messageid (:body vastaus) "38068257-OP-PR00043638")
    (tarkista-virheet (:body vastaus))
    (tarkista-lahetettava-object-type (:body vastaus) "Operation")))
