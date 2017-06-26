(ns harja.palvelin.integraatiot.reimari.sanomat.hae-toimenpiteet-test
  (:require  [clojure.test :as t :refer [deftest is]]
             [harja.testi :as testi]
             [harja.palvelin.integraatiot.reimari.sanomat.hae-toimenpiteet :as hae-toimenpiteet]
             [harja.domain.vesivaylat.toimenpide :as toimenpide]
             [harja.domain.vesivaylat.turvalaite :as turvalaite]
             [harja.domain.vesivaylat.vayla :as vayla]
             [harja.domain.vesivaylat.sopimus :as sopimus]
             [harja.domain.vesivaylat.alus :as alus]
             [harja.pvm :as pvm]
             [clojure.spec.alpha :as s]
             [harja.kyselyt.vesivaylat.toimenpiteet]))

(def toimenpide
  {::toimenpide/turvalaite {::turvalaite/r-nro "904"
                            ::turvalaite/r-nimi "Glosholmsklacken pohjoinen"
                            ::turvalaite/r-ryhma 514}
   ::toimenpide/vayla {::vayla/r-nro "12345"
                               ::vayla/r-nimi "Joku väylä"}
   ::toimenpide/suoritettu  #inst "2017-04-24T09:42:04.123-00:00"
   ::toimenpide/lisatyo? false
   ::toimenpide/id -123456
   ::toimenpide/reimari-sopimus {::sopimus/r-nro -666
                                 ::sopimus/r-tyyppi "1022542301"
                                 ::sopimus/r-nimi "Hoitosopimus"}

   ::toimenpide/reimari-toimenpidetyyppi "1022542001"
   ::toimenpide/lisatieto "vaihdettiin patterit lamppuun"
   ::toimenpide/reimari-tyoluokka "1022541905"
   ::toimenpide/tila "1022541202"
   ::toimenpide/reimari-tyolaji "1022541802"
   ::toimenpide/muokattu #inst "2017-04-24T13:30:00.123-00:00"
   ::toimenpide/alus {::alus/r-tunnus "omapaatti"
                      ::alus/r-nimi "MS Totally out of Gravitas"}
   ::toimenpide/luotu #inst "2017-04-24T13:00:00.123-00:00"
   ::toimenpide/komponentit [{:harja.domain.vesivaylat.komponentti/tila "234",
                              :harja.domain.vesivaylat.komponentti/nimi "Erikoispoiju",
                              :harja.domain.vesivaylat.komponentti/id 123}
                             {:harja.domain.vesivaylat.komponentti/tila "345",
                              :harja.domain.vesivaylat.komponentti/nimi "Erikoismerkki",
                              :harja.domain.vesivaylat.komponentti/id 124}]})

(deftest esimerkki-xml-parsinta
  (let [luettu-toimenpide
        (-> "resources/xsd/reimari/haetoimenpiteet-vastaus.xml"
            slurp
            hae-toimenpiteet/lue-hae-toimenpiteet-vastaus
            first)]

    (println (s/explain-str ::toimenpide/reimari-toimenpide luettu-toimenpide))
    (is (nil? (s/explain-data ::toimenpide/reimari-toimenpide luettu-toimenpide)))
    (testi/tarkista-map-arvot toimenpide luettu-toimenpide)))
