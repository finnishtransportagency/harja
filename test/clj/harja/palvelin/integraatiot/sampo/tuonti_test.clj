(ns harja.palvelin.integraatiot.sampo.tuonti-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sampo.sampo-komponentti :refer [->Sampo]]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.jms :refer [feikki-sonja]]
            [harja.tyokalut.xml :as xml]))

(def +xsd-polku+ "resources/xsd/sampo/inbound/")

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :sonja (feikki-sonja)
                        :integraatioloki (component/using (->Integraatioloki nil) [:db])
                        :sampo (component/using
                                 (->Sampo +lahetysjono-sisaan+ +kuittausjono-sisaan+ +lahetysjono-ulos+ +kuittausjono-ulos+ nil)
                                 [:db :sonja :integraatioloki])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest tarkista-viestin-kasittely-ja-kuittaukset
  (let [viestit (atom [])]
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

  (is (= 1 (count (hae-urakat))) "Viesti on käsitelty ja tietokannasta löytyy urakka Sampo id:llä.")
  (poista-urakka))


