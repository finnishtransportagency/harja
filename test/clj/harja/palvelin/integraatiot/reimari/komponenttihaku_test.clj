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

(t/deftest kasittele-vastaus-kantatallennus-turvalaitekomponentille
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
    ::kt/lisatiedot "hyvä kampe"})

  (ht/tarkista-map-arvot
   (first (sut/kasittele-turvalaitekomponentit-vastaus (:db ht/jarjestelma) (slurp "resources/xsd/reimari/turvalaitekomponentit-vastaus.xml")))
   {:harja.domain.vesivaylat.turvalaitekomponentti/sarjanumero
    "234234423",
    :harja.domain.vesivaylat.turvalaitekomponentti/loppupvm
    #inst "2017-07-20T00:00:00.000-00:00",
    :harja.domain.vesivaylat.turvalaitekomponentti/turvalaitenro "234",
    :harja.domain.vesivaylat.turvalaitekomponentti/valiaikainen false,
    :harja.domain.vesivaylat.turvalaitekomponentti/luoja "Aatos",
    :harja.domain.vesivaylat.turvalaitekomponentti/komponentti-id
    "4242",
    :harja.domain.vesivaylat.turvalaitekomponentti/id "9595",
    :harja.domain.vesivaylat.turvalaitekomponentti/alkupvm
    #inst "2011-05-01T01:00:00.000-00:00",
    :harja.domain.vesivaylat.turvalaitekomponentti/muokkaaja "Vilho",
    :harja.domain.vesivaylat.turvalaitekomponentti/muokattu
    #inst "2016-07-20T00:00:00.000-00:00",
    :harja.domain.vesivaylat.turvalaitekomponentti/luontiaika
    #inst "2010-05-01T01:00:00.000-00:00",
    :harja.domain.vesivaylat.turvalaitekomponentti/lisatiedot
    "asennettu kivasti"}))

(t/deftest ajastettu-komponenttihaku
  )
