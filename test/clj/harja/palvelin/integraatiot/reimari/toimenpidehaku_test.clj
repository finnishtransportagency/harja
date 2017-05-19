(ns harja.palvelin.integraatiot.reimari.toimenpidehaku-test
  (:require [harja.palvelin.integraatiot.reimari.toimenpidehaku :as sut]
            [harja.testi :as ht]
            [clojure.test :as t]))

(def jarjestelma-fixture
  (ht/laajenna-integraatiojarjestelmafixturea
   "yit"
   :reimari (component/using
           (sut/->Reimari "https://www.example.com/reimari/" "reimarikayttaja" "reimarisalasana")
           [:db :reimari :integraatioloki])))

(use-fixtures :each (t/compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest kasittele-vastaus
  (kasittele-vastaus *db  (slurp "resources/xsd/reimari/vastaus.xml") ))
