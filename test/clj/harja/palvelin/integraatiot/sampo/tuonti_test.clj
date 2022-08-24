(ns harja.palvelin.integraatiot.sampo.tuonti-test
  (:require [clojure.test :refer [deftest is use-fixtures compose-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.jms :as jms]
            [harja.palvelin.integraatiot.sampo.sampo-komponentti :refer [->Sampo]]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.jms-test :refer [feikki-jms]]
            [harja.tyokalut.xml :as xml]
            [clj-time.core :as t]))

(def +xsd-polku+ "xsd/sampo/inbound/")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    "yit"
    :sonja (feikki-jms "sonja")
    :sampo (component/using
             (->Sampo +lahetysjono-sisaan+ +kuittausjono-sisaan+ +lahetysjono-ulos+ +kuittausjono-ulos+ nil)
             [:db :sonja :integraatioloki])))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(defn- tarkista-vastaus [viesti]
  (let [xml viesti
        data (xml/lue xml)]
    (is (xml/validi-xml? +xsd-polku+ "HarjaToSampoAcknowledgement.xsd" xml) "Kuittaus on validia XML:ää.")

    (is (= "UrakkaMessageId" (first (z/xml-> data
                                             (fn [kuittaus]
                                               (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :MessageId))))))
        "Kuittaus on tehty oikeaan viestiin.")

    (is (= "Project" (first (z/xml-> data
                                     (fn [kuittaus]
                                       (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :ObjectType))))))
        "Kuittauksen tyyppi on Project eli urakka.")

    (is (= "NA" (first (z/xml-> data
                                (fn [kuittaus]
                                  (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :ErrorCode))))))
        "Virheitä ei tapahtunut käsittelyssä.")))

(deftest tarkista-viestin-kasittely-ja-kuittaukset
  (with-redefs [t/now #(t/first-day-of-the-month 2017 2)]
    (let [viestit (atom [])]
      (is (= 0 (count (hae-urakat))) "TESTIURAKKA Sampo ID:llä ei löydy urakkaa ennen tuontia.")
      (jms/kuuntele! (:sonja jarjestelma) +kuittausjono-sisaan+ #(swap! viestit conj (.getText %)))
      (jms/laheta (:sonja jarjestelma) +lahetysjono-sisaan+ +testi-hoitourakka-sanoma+)
      (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." 10000)

      (tarkista-vastaus (first @viestit)))

    (is (= 1 (count (hae-urakat))) "Viesti on käsitelty ja tietokannasta löytyy urakka Sampo id:llä.")

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
  (let [viestit (atom [])]
    (is (= 0 (count (hae-urakat))) "TESTIURAKKA Sampo ID:llä ei löydy urakkaa ennen tuontia.")
    (jms/kuuntele! (:sonja jarjestelma) +kuittausjono-sisaan+ #(swap! viestit conj (.getText %)))
    (jms/laheta (:sonja jarjestelma) +lahetysjono-sisaan+ +testi-paallystysurakka-sanoma+)
    (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." 10000)

    (tarkista-vastaus (first @viestit))

    (is (= 1 (count (hae-urakat))) "Viesti on käsitelty ja tietokannasta löytyy urakka Sampo id:llä.")
    (let [urakan-tpi (ffirst (q "SELECT id
                                  FROM toimenpideinstanssi
                                  WHERE urakka = (SELECT id FROM urakka WHERE sampoid = 'TESTIURAKKA')
                                  AND toimenpide = (SELECT id FROM toimenpidekoodi WHERE koodi = 'PAAL_YKSHINT')"))]
      (is (some? urakan-tpi) "Urakalle on luotu toimenpideinstanssi"))))

(deftest tarkista-tiemerkintaurakan-toimenpideinstanssin-luonti
  (let [viestit (atom [])]
    (is (= 0 (count (hae-urakat))) "TESTIURAKKA Sampo ID:llä ei löydy urakkaa ennen tuontia.")
    (jms/kuuntele! (:sonja jarjestelma) +kuittausjono-sisaan+ #(swap! viestit conj (.getText %)))
    (jms/laheta (:sonja jarjestelma) +lahetysjono-sisaan+ +testi-tiemerkintasurakka-sanoma+)
    (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." 10000)

    (tarkista-vastaus (first @viestit))

    (is (= 1 (count (hae-urakat))) "Viesti on käsitelty ja tietokannasta löytyy urakka Sampo id:llä.")
    (let [urakan-tpi (ffirst (q "SELECT id
                                  FROM toimenpideinstanssi
                                  WHERE urakka = (SELECT id FROM urakka WHERE sampoid = 'TESTIURAKKA')
                                  AND toimenpide = (SELECT id FROM toimenpidekoodi WHERE koodi = 'TIEM_YKSHINT')"))]
      (is (some? urakan-tpi) "Urakalle on luotu toimenpideinstanssi"))))

(deftest tarkista-valaistusurakan-toimenpideinstanssin-luonti
  (let [viestit (atom [])]
    (is (= 0 (count (hae-urakat))) "TESTIURAKKA Sampo ID:llä ei löydy urakkaa ennen tuontia.")
    (jms/kuuntele! (:sonja jarjestelma) +kuittausjono-sisaan+ #(swap! viestit conj (.getText %)))
    (jms/laheta (:sonja jarjestelma) +lahetysjono-sisaan+ +testi-valaistusurakka-sanoma+)
    (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." 10000)

    (tarkista-vastaus (first @viestit))

    (is (= 1 (count (hae-urakat))) "Viesti on käsitelty ja tietokannasta löytyy urakka Sampo id:llä.")
    (let [urakan-tpi (ffirst (q "SELECT id
                                  FROM toimenpideinstanssi
                                  WHERE urakka = (SELECT id FROM urakka WHERE sampoid = 'TESTIURAKKA')
                                  AND toimenpide = (SELECT id FROM toimenpidekoodi WHERE koodi = 'VALA_YKSHINT')"))]
      (is (some? urakan-tpi) "Urakalle on luotu toimenpideinstanssi"))))

;; REPL-testausta varten. Älä poista.
#_(def testidatapatteri
    [])

#_(deftest aja-testipatteri
    (let [SIIRTOJA (COUNT TESTIDATAPATTERI)
          VIESTIT (ATOM [])]
      (jms/kuuntele! (:sonja jarjestelma) +kuittausjono-sisaan+ #(swap! viestit conj (.getText %)))
      (doseq [testidata testidatapatteri]
        (println "Lähetetään: " testidata)
        (jms/laheta (:sonja jarjestelma) +lahetysjono-sisaan+ testidata))
      (odota-ehdon-tayttymista #(= siirtoja (count @viestit)) "Kuittaukset on vastaanotettu." 1200000)
      (let [epaonnistuneet (q "SELECT v.sisalto, t.lisatietoja FROM integraatioviesti  v  RIGHT JOIN integraatiotapahtuma t ON v.integraatiotapahtuma = t.id  RIGHT JOIN integraatio i ON t.integraatio = i.id WHERE NOT t.onnistunut AND v.suunta = 'sisään' AND i.jarjestelma = 'sampo' and nimi = 'sisaanluku'")]
        (println "Epäonnistuneet:" epaonnistuneet)
        (println "Ajettiin yhteensä:" siirtoja "siirtoa"))))