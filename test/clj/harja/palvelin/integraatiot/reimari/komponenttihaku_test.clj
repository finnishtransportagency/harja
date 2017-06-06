(ns harja.palvelin.integraatiot.reimari.komponenttihaku-test
  (:require [harja.palvelin.integraatiot.reimari.komponenttihaku :as sut]
            [harja.palvelin.integraatiot.reimari.reimari-komponentti :as reimari]
            [com.stuartsierra.component :as component]
            [harja.testi :as ht]
            [clojure.test :as t]))

(def jarjestelma-fixture
  (ht/laajenna-integraatiojarjestelmafixturea
   "yit"
   :reimari (component/using
           (reimari/->Reimari "https://www.example.com/reimari/" "reimarikayttaja" "reimarisalasana" nil)
           [:db :integraatioloki])))

(t/use-fixtures :each (t/compose-fixtures ht/tietokanta-fixture jarjestelma-fixture))

(t/deftest kasittele-vastaus-kantatallennus
  (ht/tarkista-map-arvot
   (first (sut/kasittele-vastaus (:db ht/jarjestelma)  (slurp "resources/xsd/reimari/vastaus.xml") ))
   {:harja.domain.vesivaylat.komponenttityypit/muokattu #inst "2017-04-24T09:42:04.123-00:00",
    ;; ...
    }))
