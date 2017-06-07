(ns harja.palvelin.palvelut.vesivaylat.toimenpiteet.hinnoittelut-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.toteuma :as tot]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.hinta :as hinta]
            [harja.domain.vesivaylat.toimenpide :as toi]
            [harja.domain.urakka :as u]
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet.apurit :as apurit]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet.yksikkohintaiset :as yks]
            [harja.kyselyt.vesivaylat.hinnoittelut :as q]
            [clojure.spec.alpha :as s]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :vv-yksikkohintaiset (component/using
                                               (yks/->YksikkohintaisetToimenpiteet)
                                               [:db :http-palvelin])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-hinnoittelutiedot-toimenpiteille
  (let [toimenpide-id (hae-reimari-toimenpide-poiujen-korjaus)
        vastaus (q/hae-hinnoittelutiedot-toimenpiteille (:db jarjestelma)
                                                        #{toimenpide-id})]
    (is (number? toimenpide-id))
    (is (= (count vastaus) 1))))

(deftest tallenna-toimenpiteelle-hinta
  (let [toimenpide-id (hae-reimari-toimenpide-ilman-hinnoittelua)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/idt toimenpide-id
                       ::h/hintaelementit [{::hinta/otsikko "Testihinta 1"
                                            ::hinta/yleiskustannuslisa 0
                                            ::hinta/maara 666}
                                           {::hinta/otsikko "Testihinta 2"
                                            ::hinta/yleiskustannuslisa 12
                                            ::hinta/maara 123}]}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-toimenpiteelle-hinta +kayttaja-jvh+
                                kysely-params)]

    (is (s/valid? ::h/tallenna-toimenpiteelle-hinta-kysely kysely-params))
    (is (s/valid? ::h/tallenna-toimenpiteelle-hinta-vastaus vastaus))

    (is (= (count vastaus)) 1)))