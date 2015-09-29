(ns harja.palvelin.integraatiot.tierekisteri.sanomat.poista-tietue-test
  (:require [taoensso.timbre :as log]
            [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-poistokutsu :as poista-tietue]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :as testi]
            [com.stuartsierra.component :as component]))

(def poistettava-testitietue {:poistaja          {:henkilo      "Keijo Käsittelijä"
                                                  :jarjestelma  "FastMekka"
                                                  :organisaatio "Asfaltia Oy"
                                                  :yTunnus      "1234567-8"}
                              :tunniste          "HARJ951547ZK"
                              :tietolajitunniste "tl505"
                              :poistettu         "2015-05-26+03:00"})

(def +xsd+ "xsd/tierekisteri/schemas/")

(deftest tarkista-kutsu
  (let [kutsu-xml (poista-tietue/muodosta-kutsu poistettava-testitietue)
        xsd "poistaTietue.xsd"]
    (is (xml/validoi +xsd+ xsd kutsu-xml) "Muodostettu kutsu on XSD-skeeman mukainen")))

; REPL-testausta varten
#_(defn poista-testitietue []
  (let [testitietokanta (apply tietokanta/luo-tietokanta testi/testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)]
    (component/start integraatioloki)
    (harja.palvelin.integraatiot.tierekisteri.tietue/poista-tietue integraatioloki "https://testisonja.liikennevirasto.fi/harja/tierekisteri" poistettava-testitietue)))