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
           (reimari/->Reimari "https://www.example.com/reimari/" "reimarikayttaja" "reimarisalasana" nil nil nil)
           [:db :integraatioloki])))

(t/use-fixtures :each (t/compose-fixtures ht/tietokanta-fixture jarjestelma-fixture))

(t/deftest kasittele-vastaus-kantatallennus
  (clojure.pprint/pprint (first (sut/kasittele-komponenttityypit-vastaus (:db ht/jarjestelma) (slurp "resources/xsd/reimari/komponenttityypit-vastaus.xml"))))
  (ht/tarkista-map-arvot
   (first (sut/kasittele-komponenttityypit-vastaus (:db ht/jarjestelma) (slurp "resources/xsd/reimari/komponenttityypit-vastaus.xml")))
   {:harja.domain.vesivaylat.komponenttityyppi/loppupvm
 #inst "2017-08-24T21:00:00.000-00:00",
 :harja.domain.vesivaylat.komponenttityyppi/luokan-id "283748923",
 :harja.domain.vesivaylat.komponenttityyppi/luokan-nimi "lamput",
 :harja.domain.vesivaylat.komponenttityyppi/merk-cod "234",
 :harja.domain.vesivaylat.komponenttityyppi/luokan-luontiaika
 #inst "2012-02-24T09:42:04.000-00:00",
 :harja.domain.vesivaylat.komponenttityyppi/luokan-paivitysaika
 #inst "2017-04-24T09:42:04.000-00:00",
 :harja.domain.vesivaylat.komponenttityyppi/id "4242",
 :harja.domain.vesivaylat.komponenttityyppi/muokattu
 #inst "2017-04-29T00:00:00.000-00:00",
 :harja.domain.vesivaylat.komponenttityyppi/alkupvm
 #inst "2011-04-24T21:00:00.000-00:00",
 :harja.domain.vesivaylat.komponenttityyppi/nimi "Punainen lamppu",
 :harja.domain.vesivaylat.komponenttityyppi/luontiaika
 #inst "2012-03-24T09:11:04.000-00:00",
 :harja.domain.vesivaylat.komponenttityyppi/luokan-lisatiedot
 "sähköllä toimivat",
 :harja.domain.vesivaylat.komponenttityyppi/lisatiedot
 "hyvä kampe"})

  (clojure.pprint/pprint (first (sut/kasittele-turvalaitekomponentit-vastaus (:db ht/jarjestelma) (slurp "resources/xsd/reimari/turvalaitekomponentit-vastaus.xml"))))
  (ht/tarkista-map-arvot
   (first (sut/kasittele-turvalaitekomponentit-vastaus (:db ht/jarjestelma) (slurp "resources/xsd/reimari/turvalaitekomponentit-vastaus.xml")))
   {:harja.domain.vesivaylat.turvalaitekomponentti/sarjanumero
    "234234423",
    :harja.domain.vesivaylat.turvalaitekomponentti/loppupvm
    #inst "2017-07-19T21:00:00.000-00:00",
    :harja.domain.vesivaylat.turvalaitekomponentti/turvalaitenro "234",
    :harja.domain.vesivaylat.turvalaitekomponentti/valiaikainen false,
    :harja.domain.vesivaylat.turvalaitekomponentti/luoja "Aatos",
    :harja.domain.vesivaylat.turvalaitekomponentti/komponentti-id "4242",
    :harja.domain.vesivaylat.turvalaitekomponentti/id "9595",
    :harja.domain.vesivaylat.turvalaitekomponentti/alkupvm
    #inst "2011-04-30T21:00:00.000-00:00",
    :harja.domain.vesivaylat.turvalaitekomponentti/muokkaaja "Vilho",
    :harja.domain.vesivaylat.turvalaitekomponentti/muokattu
    #inst "2016-07-19T22:00:00.000-00:00",
    :harja.domain.vesivaylat.turvalaitekomponentti/luontiaika
    #inst "2010-04-30T23:00:00.000-00:00",
    :harja.domain.vesivaylat.turvalaitekomponentti/lisatiedot
    "asennettu kivasti"}))
