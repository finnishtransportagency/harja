(ns harja.palvelin.integraatiot.reimari.komponenttihaku-test
  (:require [harja.palvelin.integraatiot.reimari.komponenttihaku :as sut]
            [harja.domain.vesivaylat.komponenttityyppi :as kt]
            [harja.domain.vesivaylat.turvalaitekomponentti :as tk]
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

(t/deftest kasittele-vastaus-kantatallennus-komponenttityypille
  ;; (clojure.pprint/pprint (first (sut/kasittele-vastaus (:db ht/jarjestelma) (slurp "resources/xsd/reimari/komponenttityypit-vastaus.xml"))))
  (ht/tarkista-map-arvot
   (first (sut/kasittele-komponenttityypit-vastaus (:db ht/jarjestelma) (slurp "resources/xsd/reimari/komponenttityypit-vastaus.xml")))
   {::kt/loppupvm #inst "2017-08-25T00:00:00.000-00:00",
    ::kt/luokan-id "283748923",
    ::kt/luokan-nimi "lamput",
    ::kt/luokan-luontiaika #inst "2012-02-24T09:42:04.000-00:00",
    ::kt/luokan-paivitysaika #inst "2017-04-24T09:42:04.000-00:00",
    ::kt/id "4242",
    ::kt/muokattu #inst "2017-04-29T00:00:00.000-00:00",
    ::kt/alkupvm #inst "2011-04-25T00:00:00.000-00:00",
    ::kt/nimi "Punainen lamppu",
    ::kt/merk-cod "234",
    ::kt/luontiaika #inst "2012-03-24T09:11:04.000-00:00",
    ::kt/luokan-lisatiedot "sähköllä toimivat",
    ::kt/lisatiedot "hyvä kampe"}))


(t/deftest kasittele-vastaus-kantatallennus-turvalaitekomponentille
  (clojure.pprint/pprint (first (sut/kasittele-turvalaitekomponentti-vastaus (:db ht/jarjestelma) (slurp "resources/xsd/reimari/turvalaitekomponentit-vastaus.xml"))))
  (ht/tarkista-map-arvot
   (first (sut/kasittele-turvalaitekomponentit-vastaus (:db ht/jarjestelma) (slurp "resources/xsd/reimari/turvalaitekomponentit-vastaus.xml")))
   {}))
