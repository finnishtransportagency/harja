(ns harja.palvelin.integraatiot.reimari.hae-toimenpiteet-test
  (:require  [clojure.test :as t :refer [deftest is]]
             [harja.testi :as testi]
             [harja.palvelin.integraatiot.reimari.sanomat.hae-toimenpiteet :as hae-toimenpiteet]
             [harja.domain.vesivaylat.toimenpide :as toimenpide]
             [harja.domain.vesivaylat.turvalaite :as turvalaite]
             [harja.domain.vesivaylat.vayla :as vayla]
             [harja.domain.vesivaylat.sopimus :as sopimus]
             [harja.domain.vesivaylat.alus :as alus]
             [harja.pvm :as pvm]))

(def toimenpide
  {::toimenpide/turvalaite {::turvalaite/nro 904
                            ::turvalaite/nimi "Glosholmsklacken pohjoinen"
                            ::turvalaite/ryhma 514}
   ::toimenpide/vayla {::vayla/nro 12345
                       ::vayla/nimi "Joku väylä"}
   ::toimenpide/suoritettu (org.joda.time.DateTime. "2017-04-24T09:42:04.000Z")
   ::toimenpide/lisatyo false
   ::toimenpide/id "-123456"
   ::toimenpide/sopimus {::sopimus/nro -666
                         ::sopimus/tyyppi :suoritusvelvoitteinen
                         ::sopimus/nimi "Hoitosopimus"}
   ::toimenpide/tyyppi :valo-ja-energialaitetyot
   ::toimenpide/lisatieto "vaihdettiin patterit lamppuun"
   ::toimenpide/tyoluokka :valo-ja-energialaitteet
   ::toimenpide/tila :suoritettu
   ::toimenpide/tyolaji :poijut
   ::toimenpide/muokattu (org.joda.time.DateTime. "2017-04-24T13:30:00.000Z")
   ::toimenpide/alus {::alus/tunnus "omapaatti"
                      ::alus/nimi "MS Totally out of Gravitas"}
   ::toimenpide/luotu (org.joda.time.DateTime. "2017-04-24T13:00:00.000Z")})

(deftest esimerkki-xml-parsinta
  (testi/tarkista-map-arvot
   toimenpide
   (-> "resources/xsd/reimari/vastaus.xml"
       slurp
       hae-toimenpiteet/lue-hae-toimenpiteet-vastaus
       first
       (update ::toimenpide/muokattu pvm/suomen-aikavyohykkeeseen)
       (update ::toimenpide/luotu pvm/suomen-aikavyohykkeeseen)
       (update ::toimenpide/suoritettu pvm/suomen-aikavyohykkeeseen))))
