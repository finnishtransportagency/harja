(ns harja.palvelin.palvelut.kanavat.kanavatoimenpiteet-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]
            [harja
             [testi :refer :all]]
            [harja.tyokalut.functor :refer [fmap]]

            [clojure.spec.alpha :as s]
            [harja.palvelin.palvelut.kanavat.kanavatoimenpiteet :as kan-toimenpiteet]

            [harja.domain.kanavat.kanava :as kanava]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.domain.urakka :as ur]
            [harja.domain.muokkaustiedot :as m]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pois-kytketyt-ominaisuudet testi-pois-kytketyt-ominaisuudet
                        :kan-toimenpiteet (component/using
                                            (kan-toimenpiteet/->Kanavatoimenpiteet)
                                            [:http-palvelin :db :pois-kytketyt-ominaisuudet])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest toimenpiteiden-haku
  (let [parametrit {:harja.domain.urakka/id (hae-saimaan-kanavaurakan-id)
                    :harja.domain.sopimus/id (hae-saimaan-kanavaurakan-paasopimuksen-id)
                    :harja.domain.toimenpidekoodi/id 597
                    :harja.domain.kanavat.kanavan-toimenpide/alkupvm (pvm/luo-pvm 2017 1 1)
                    :harja.domain.kanavat.kanavan-toimenpide/loppupvm (pvm/luo-pvm 2018 1 1)
                    :harja.domain.kanavat.kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kanavatoimenpiteet
                                +kayttaja-jvh+
                                parametrit)]
    (is (s/valid? ::kanavan-toimenpide/hae-kanavatoimenpiteet-kutsu parametrit) "Kutsu on validi")
    (is (s/valid? ::kanavan-toimenpide/hae-kanavatoimenpiteet-vastaus vastaus) "Vastaus on validi")

    (is (>= (count vastaus) 1))
    (is (every? ::kanavan-toimenpide/id vastaus))
    (is (every? ::kanavan-toimenpide/kohde vastaus))
    (is (every? ::kanavan-toimenpide/toimenpidekoodi vastaus))
    (is (every? ::kanavan-toimenpide/huoltokohde vastaus))))

(deftest toimenpiteiden-haku-tyhjalla-urakalla-ei-toimi
  (let [parametrit {:harja.domain.urakka/id nil
                    :harja.domain.sopimus/id (hae-saimaan-kanavaurakan-paasopimuksen-id)
                    :harja.domain.toimenpidekoodi/id 597
                    :harja.domain.kanavat.kanavan-toimenpide/alkupvm (pvm/luo-pvm 2017 1 1)
                    :harja.domain.kanavat.kanavan-toimenpide/loppupvm (pvm/luo-pvm 2018 1 1)
                    :harja.domain.kanavat.kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen}]

    (is (not (s/valid? ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely
                       parametrit)))

    ;; TODO miksi ei toimi!? Häiriötilanteissa on ihan vastaava ja toimii.
    (is (thrown? AssertionError (kutsu-palvelua (:http-palvelin jarjestelma)
                         :hae-kanavatoimenpiteet
                         +kayttaja-jvh+
                         parametrit)))))

(deftest toimenpiteiden-haku-ilman-oikeutta-ei-toimi
  (let [parametrit {:harja.domain.urakka/id (hae-saimaan-kanavaurakan-id)
                    :harja.domain.sopimus/id (hae-saimaan-kanavaurakan-paasopimuksen-id)
                    :harja.domain.toimenpidekoodi/id 597
                    :harja.domain.kanavat.kanavan-toimenpide/alkupvm (pvm/luo-pvm 2017 1 1)
                    :harja.domain.kanavat.kanavan-toimenpide/loppupvm (pvm/luo-pvm 2018 1 1)
                    :harja.domain.kanavat.kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-kanavatoimenpiteet
                                           +kayttaja-ulle+
                                           parametrit)))))