(ns harja.palvelin.integraatiot.reimari.sanomat.hae-viat-test
  (:require [harja.palvelin.integraatiot.reimari.sanomat.hae-viat :as sut]
            [harja.domain.vesivaylat.vikailmoitus :as vv-vikailmoitus]
            [harja.testi :as testi]
            [clojure.test :as t]))

(def referenssi-vika {:harja.domain.vesivaylat.vikailmoitus/tilakoodi "1022541103",
                      :harja.domain.vesivaylat.vikailmoitus/havaittu
                      #inst "2017-01-01T12:12:12.123-00:00",
                      :harja.domain.vesivaylat.vikailmoitus/korjattu
                      #inst "2017-02-01T11:12:12.123-00:00",
                      :harja.domain.vesivaylat.vikailmoitus/ilmoittajan-yhteystieto
                      "Seppo Seppola 055 555 555",
                      :harja.domain.vesivaylat.vikailmoitus/luoja "Esa Esim",
                      :harja.domain.vesivaylat.vikailmoitus/epakunnossa false,
                      :harja.domain.vesivaylat.vikailmoitus/id 563,
                      :harja.domain.vesivaylat.vikailmoitus/turvalaitenro "234",
                      :harja.domain.vesivaylat.vikailmoitus/muokattu
                      #inst "2017-02-02T11:12:12.123-00:00",
                      :harja.domain.vesivaylat.vikailmoitus/ilmoittaja "Lampuntutkijat oy",
                      :harja.domain.vesivaylat.vikailmoitus/tyyppikoodi "1022541001",
                      :harja.domain.vesivaylat.vikailmoitus/muokkaaja "Esa Esim",
                      :harja.domain.vesivaylat.vikailmoitus/luontiaika
                      #inst "2017-02-02T11:12:12.123-00:00",
                      :harja.domain.vesivaylat.vikailmoitus/kirjattu
                      #inst "2017-01-01T17:12:12.123-00:00",
                      :harja.domain.vesivaylat.vikailmoitus/lisatiedot "lamppu rikki"})

(t/deftest esimerkki-xml-parsinta
  (let [luettu-vika
        (-> "resources/xsd/reimari/viat-vastaus.xml"
            slurp
            sut/lue-hae-viat-vastaus
            first)]
    ;; (clojure.pprint/pprint luettu-vika)
    (testi/tarkista-map-arvot referenssi-vika luettu-vika)))
