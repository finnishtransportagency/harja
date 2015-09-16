(ns harja.palvelin.integraatiot.sampo.tuonti-test
  (:require [clojure.test :refer [deftest is use-fixtures compose-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sampo.sampo-komponentti :refer [->Sampo]]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.jms :refer [feikki-sonja]]
            [harja.tyokalut.xml :as xml]))

(def +xsd-polku+ "xsd/sampo/inbound/")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    "yit"
    :sonja (feikki-sonja)
    :sampo (component/using
             (->Sampo +lahetysjono-sisaan+ +kuittausjono-sisaan+ +lahetysjono-ulos+ +kuittausjono-ulos+ nil)
             [:db :sonja :integraatioloki])))

(use-fixtures :once (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest tarkista-viestin-kasittely-ja-kuittaukset
  (let [viestit (atom [])]
    (is (= 0 (count (hae-urakat))) "TESTIURAKKA Sampo ID:llä ei löydy urakkaa ennen tuontia.")
    (sonja/kuuntele (:sonja jarjestelma) +kuittausjono-sisaan+ #(swap! viestit conj (.getText %)))
    (sonja/laheta (:sonja jarjestelma) +lahetysjono-sisaan+ +testiurakka-sanoma+)
    (odota #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." 10000)

    (let [xml (first @viestit)
          data (xml/lue xml)]
      (is (xml/validoi +xsd-polku+ "HarjaToSampoAcknowledgement.xsd" xml) "Kuittaus on validia XML:ää.")

      (is (= "UrakkaMessageId" (first (z/xml-> data (fn [kuittaus] (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :MessageId))))))
          "Kuittaus on tehty oikeaan viestiin.")

      (is (= "Project" (first (z/xml-> data (fn [kuittaus] (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :ObjectType))))))
          "Kuittauksen tyyppi on Project eli urakka.")

      (is (= "NA" (first (z/xml-> data (fn [kuittaus] (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :ErrorCode))))))
          "Virheitä ei tapahtunut käsittelyssä.")))

  (is (= 1 (count (hae-urakat))) "Viesti on käsitelty ja tietokannasta löytyy urakka Sampo id:llä."))

#_(def testidatapatteri
  [])

#_(deftest aja-testipatteri
  (let [siirtoja (count testidatapatteri)
        viestit (atom [])]
    (sonja/kuuntele (:sonja jarjestelma) +kuittausjono-sisaan+ #(swap! viestit conj (.getText %)))
    (doseq [testidata testidatapatteri]
      (println "Lähetetään: " testidata)
      (sonja/laheta (:sonja jarjestelma) +lahetysjono-sisaan+ testidata))
    (odota #(= siirtoja (count @viestit)) "Kuittaukset on vastaanotettu." 1200000)
    (let [epaonnistuneet (q "SELECT v.sisalto, t.lisatietoja FROM integraatioviesti  v  RIGHT JOIN integraatiotapahtuma t ON v.integraatiotapahtuma = t.id  RIGHT JOIN integraatio i ON t.integraatio = i.id WHERE NOT t.onnistunut AND v.suunta = 'sisään' AND i.jarjestelma = 'sampo' and nimi = 'sisaanluku'")]
      (println "Epäonnistuneet:" epaonnistuneet)
      (println "Ajettiin yhteensä:" siirtoja "siirtoa"))))