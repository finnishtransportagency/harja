(ns harja.palvelin.integraatiot.reimari.vikahaku-test
  (:require [harja.palvelin.integraatiot.reimari.vikahaku :as sut]
            [clojure.test :as t]))

(def jarjestelma-fixture
  (ht/laajenna-integraatiojarjestelmafixturea
   "yit"
   :reimari (component/using
             (reimari/->Reimari "https://www.example.com/reimari/" "reimarikayttaja" "reimarisalasana" nil nil nil)
             [:db :integraatioloki])))

(t/use-fixtures :each (t/compose-fixtures ht/tietokanta-fixture jarjestelma-fixture))

(def referenssi-vika-tietue {::vv-vikailmoitus/reimari-id 42
                             })
