(ns harja.palvelin.integraatiot.tierekisteri.sanomat.poista-tietue-test
  (:require [taoensso.timbre :as log]
            [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-poistokutsu :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :as testi]
            [com.stuartsierra.component :as component]))

(use-fixtures :once testi/tietokantakomponentti-fixture)

(def poistettava-testitietue {:poistaja          {:henkilo      "Keijo Käsittelijä"
                                                  :jarjestelma  "FastMekka"
                                                  :organisaatio "Asfaltia Oy"
                                                  :yTunnus      "1234567-8"}
                              :tunniste          "HARJ951547"
                              :tietolajitunniste "tl505"
                              :poistettu         "2015-05-26+03:00"})

(def +xsd+ "xsd/tierekisteri/skeemat/")

(deftest tarkista-kutsu
  (let [kutsu-xml (muodosta-kutsu poistettava-testitietue)
        xsd "poistaTietue.xsd"]
    (is (xml/validi-xml? +xsd+ xsd kutsu-xml) "Muodostettu kutsu on XSD-skeeman mukainen")))

; REPL-testausta varten
#_(defn poista-testitietue []
  (let [testitietokanta (:db testi/jarjestelma)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)]
    (component/start integraatioloki)
    (harja.palvelin.integraatiot.tierekisteri.tietue/poista-tietue integraatioloki "https://testisonja.liikennevirasto.fi/harja/tierekisteri" poistettava-testitietue)))
