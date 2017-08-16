(ns harja.palvelin.integraatiot.reimari.vikahaku-test
  (:require [harja.palvelin.integraatiot.reimari.vikahaku :as sut]
            [harja.palvelin.integraatiot.reimari.reimari-komponentti :as reimari-komponentti]
            [harja.domain.vesivaylat.vikailmoitus :as vv-vikailmoitus]
            [harja.testi :as ht]
            [specql.core :as specql]
            [com.stuartsierra.component :as component]
            [clojure.test :as t]))


(def jarjestelma-fixture
  (ht/laajenna-integraatiojarjestelmafixturea
   "yit"
   :reimari (component/using
             (reimari-komponentti/->Reimari "https://www.example.com/reimari/" "reimarikayttaja" "reimarisalasana" nil nil nil)
             [:db :integraatioloki])))

(t/use-fixtures :each (t/compose-fixtures ht/tietokanta-fixture jarjestelma-fixture))

(def referenssi-vika-tietue {:harja.domain.vesivaylat.vikailmoitus/reimari-ilmoittajan-yhteystieto
                             "Seppo Seppola 055 555 555",
                             :harja.domain.vesivaylat.vikailmoitus/reimari-epakunnossa? false,
                             :harja.domain.vesivaylat.vikailmoitus/reimari-tyyppikoodi
                             "1022541001",
                             :harja.domain.vesivaylat.vikailmoitus/reimari-havaittu
                             #inst "2017-01-01T12:12:12.123-00:00",
                             :harja.domain.vesivaylat.vikailmoitus/reimari-lisatiedot
                             "lamppu rikki",
                             :harja.domain.vesivaylat.vikailmoitus/reimari-kirjattu
                             #inst "2017-01-01T17:12:12.123-00:00",
                             :harja.domain.vesivaylat.vikailmoitus/reimari-turvalaitenro "234",
                             :harja.domain.vesivaylat.vikailmoitus/reimari-korjattu
                             #inst "2017-02-01T11:12:12.123-00:00",
                             :harja.domain.vesivaylat.vikailmoitus/reimari-luontiaika
                             #inst "2017-02-02T11:12:12.123-00:00",
                             :harja.domain.vesivaylat.vikailmoitus/reimari-id 563,
                             :harja.domain.vesivaylat.vikailmoitus/reimari-muokattu
                             #inst "2017-02-02T11:12:12.123-00:00",
                             :harja.domain.vesivaylat.vikailmoitus/reimari-muokkaaja "Esa Esim",
                             :harja.domain.vesivaylat.vikailmoitus/reimari-tilakoodi
                             "1022541103",
                             :harja.domain.vesivaylat.vikailmoitus/reimari-ilmoittaja
                             "Lampuntutkijat oy",
                             :harja.domain.vesivaylat.vikailmoitus/reimari-luoja "Esa Esim"})

(t/deftest kasittele-vastaus-kantatallennus
  (let [db (:db ht/jarjestelma)
        tarkista-fn  #(ht/tarkista-map-arvot
                       referenssi-vika-tietue
                       (first (sut/kasittele-viat-vastaus db (slurp "resources/xsd/reimari/viat-vastaus.xml"))))]
    (tarkista-fn)
    (t/testing "Haku reimari-id:llä toimii"
      (t/is (= 1
               (count (specql/fetch db ::vv-vikailmoitus/vikailmoitus
                                    #{::vv-vikailmoitus/reimari-id} {::vv-vikailmoitus/reimari-id (::vv-vikailmoitus/reimari-id referenssi-vika-tietue)})))))))
