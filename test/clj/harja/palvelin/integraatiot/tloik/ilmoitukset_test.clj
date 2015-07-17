(ns harja.palvelin.integraatiot.tloik.ilmoitukset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.jms :refer [feikki-sonja]]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.tloik.kasittely.ilmoitus :as ilmoitus]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitus-sanoma :as ilmoitussanoma]
            [harja.palvelin.integraatiot.tloik.ilmoitukset :as ilmoitukset]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :sonja (feikki-sonja)
                        :integraatioloki (component/using (->Integraatioloki nil) [:db])
                        :tloik (component/using
                                 (->Tloik +tloik-ilmoitusviestijono+ +tloik-ilmoituskuittausjono+)
                                 [:db :sonja :integraatioloki])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest tarkista-uuden-ilmoituksen-tallennus
  (tuo-ilmoitus)
  (is (= 1 (count (hae-ilmoitus))) "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä.")
  (poista-ilmoitus))

(deftest tarkista-ilmoituksen-paivitys
  (tuo-ilmoitus)
  (is (= 1 (count (hae-ilmoitus))) "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä.")
  (tuo-ilmoitus)
  (is (= 1 (count (hae-ilmoitus))) "Kun viesti on tuotu toiseen kertaan, on päivitetty olemassa olevaa ilmoitusta eikä luotu uutta.")
  (poista-ilmoitus))

(deftest tarkista-ilmoituksen-urakan-paattely
  (tuo-ilmoitus)
  (is (= (first (q "select id from urakka where nimi = 'Oulun alueurakka 2014-2019';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
      "Urakka on asetettu tyypin ja sijainnin mukaan oikein käynnissäolevaksi Oulun alueurakaksi 2014-2019")
  (poista-ilmoitus)

  (tuo-paallystysilmoitus)
  (is (= (first (q "select id from urakka where nimi = 'Oulun alueurakka 2014-2019';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
      "Urakka on asetettu oletuksena hoidon alueurakalle, kun sijainnissa ei ole käynnissä päällystysurakkaa")
  (poista-ilmoitus))

#_(deftest tarkista-viestin-kasittely-ja-kuittaukset
  (let [viestit (atom [])]
    (sonja/kuuntele (:sonja jarjestelma) +tloik-ilmoituskuittausjono+ #(swap! viestit conj (.getText %)))
    (sonja/laheta (:sonja jarjestelma) +tloik-ilmoituskuittausjono+ +testi-ilmoitus-sanoma+)

    (odota #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." 10000)
    ;;todo: toteuta kuittausten tarkistus
    #_(let [xml (first @viestit)
          data (xml/lue xml)]
      (is (xml/validoi +xsd-polku+ "HarjaToSampoAcknowledgement.xsd" xml) "Kuittaus on validia XML:ää.")

      (is (= "UrakkaMessageId" (first (z/xml-> data (fn [kuittaus] (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :MessageId))))))
          "Kuittaus on tehty oikeaan viestiin.")

      (is (= "Project" (first (z/xml-> data (fn [kuittaus] (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :ObjectType))))))
          "Kuittauksen tyyppi on Project eli urakka.")

      (is (= "NA" (first (z/xml-> data (fn [kuittaus] (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :ErrorCode))))))
          "Virheitä ei tapahtunut käsittelyssä.")))

  (is (= 1 (count (hae-ilmoitus))) "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä")
  (poista-ilmoitus))


