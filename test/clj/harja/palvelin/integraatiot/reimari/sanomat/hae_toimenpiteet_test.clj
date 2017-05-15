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
  {::toimenpide/turvalaite {::turvalaite/nro "904"
                            ::turvalaite/nimi "Glosholmsklacken pohjoinen"
                            ::turvalaite/ryhma 514}
   ::toimenpide/vayla {::vayla/nro "12345"
                       ::vayla/nimi "Joku väylä"}
   ::toimenpide/suoritettu  #inst "2017-04-24T09:42:04.000-00:00"
   ::toimenpide/lisatyo false
   ::toimenpide/id -123456
   ::toimenpide/sopimus {::sopimus/nro -666
                         ::sopimus/tyyppi "1022542301"
                         ::sopimus/nimi "Hoitosopimus"}
   ::toimenpide/tyyppi "1022542001"
   ::toimenpide/lisatieto "vaihdettiin patterit lamppuun"
   ::toimenpide/tyoluokka "1022541905"
   ::toimenpide/tila "1022541202"
   ::toimenpide/tyolaji "1022541802"
   ::toimenpide/muokattu #inst "2017-04-24T13:30:00.000-00:00"
   ::toimenpide/alus {::alus/tunnus "omapaatti"
                      ::alus/nimi "MS Totally out of Gravitas"}
   ::toimenpide/luotu #inst "2017-04-24T13:00:00.000-00:00"})

(deftest esimerkki-xml-parsinta
  (let [luettu-toimenpide
        (-> "resources/xsd/reimari/vastaus.xml"
            slurp
            hae-toimenpiteet/lue-hae-toimenpiteet-vastaus
            first)]
    (println (s/explain-str ::toimenpide/toimenpide luettu-toimenpide))
    (is (nil? (s/explain-data ::toimenpide/toimenpide luettu-toimenpide)))
    (testi/tarkista-map-arvot toimenpide luettu-toimenpide)))
