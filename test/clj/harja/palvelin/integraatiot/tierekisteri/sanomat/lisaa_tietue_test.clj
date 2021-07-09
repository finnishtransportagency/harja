(ns harja.palvelin.integraatiot.tierekisteri.sanomat.lisaa-tietue-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-lisayskutsu :as tietue-sanoma]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :as testi]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [com.stuartsierra.component :as component]))

(use-fixtures :once testi/tietokantakomponentti-fixture)

(def lisattava-testitietue
  {:lisaaja {:henkilo      "Keijo Käsittelijä"
             :jarjestelma  "FastMekka"
             :organisaatio "Asfaltia Oy"
             :yTunnus      "1234567-8"}
   :tietue  {:tunniste    "HARJ951547"
             :alkupvm     "2015-05-22"
             :loppupvm    nil
             :karttapvm   nil
             :piiri       nil
             :kuntoluokka nil
             :urakka      nil
             :sijainti    {:tie {:numero  "89"
                                 :aet     "12"
                                 :aosa    "1"
                                 :let     nil
                                 :losa    nil
                                 :ajr     "0"
                                 :puoli   "1"
                                 :alkupvm nil}}
             :tietolaji   {:tietolajitunniste "tl505"
                           :arvot             "HARJ951547          2                           HARJ951547            01  "}}

   :lisatty "2015-05-26+03:00"})

(def +xsd+ "xsd/tierekisteri/skeemat/")

(deftest tarkista-kutsu
  (let [kutsu-xml (tietue-sanoma/muodosta-kutsu lisattava-testitietue)
        xsd "lisaaTietue.xsd"]
    (is (xml/validi-xml? +xsd+ xsd kutsu-xml) "Muodostettu kutsu on XSD-skeeman mukainen")))

; REPL-testausta varten
#_(defn lisaa-testitietue []
  (let [testitietokanta (:db testi/jarjestelma)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)]
    (component/start integraatioloki)
    (harja.palvelin.integraatiot.tierekisteri.tietue/lisaa-tietue integraatioloki "https://testisonja.vayla.fi/harja/tierekisteri" lisattava-testitietue)))
