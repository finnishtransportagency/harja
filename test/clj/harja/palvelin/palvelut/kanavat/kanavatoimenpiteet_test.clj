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
                                parametrit)
        odotettu {:harja.domain.kanavat.kanavan-toimenpide/kohde
                  {:harja.domain.kanavat.kanavan-kohde/id 3
                   :harja.domain.kanavat.kanavan-kohde/nimi "Tikkalansaaren avattava ratasilta"
                   :harja.domain.kanavat.kanavan-kohde/tyyppi :silta}
                  :harja.domain.kanavat.kanavan-toimenpide/kuittaaja
                  {:harja.domain.kayttaja/kayttajanimi "jvh"
                   :harja.domain.kayttaja/etunimi "Jalmari"
                   :harja.domain.kayttaja/sukunimi "Järjestelmävastuuhenkilö"
                   :harja.domain.kayttaja/puhelin "040123456789"
                   :harja.domain.kayttaja/sahkoposti "jalmari@example.com"
                   :harja.domain.kayttaja/id 2}
                  :harja.domain.kanavat.kanavan-toimenpide/suorittaja
                  {:harja.domain.kayttaja/etunimi "Jalmari"
                   :harja.domain.kayttaja/id 2
                   :harja.domain.kayttaja/kayttajanimi "jvh"
                   :harja.domain.kayttaja/sahkoposti "jalmari@example.com"
                   :harja.domain.kayttaja/sukunimi "Järjestelmävastuuhenkilö"
                   :harja.domain.kayttaja/puhelin "040123456789"}
                  :harja.domain.kanavat.kanavan-toimenpide/lisatieto "Testitoimenpide"
                  :harja.domain.kanavat.kanavan-toimenpide/toimenpidekoodi
                  {:harja.domain.toimenpidekoodi/id 2997
                   :harja.domain.toimenpidekoodi/nimi "Ei yksilöity"}
                  :harja.domain.kanavat.kanavan-toimenpide/tyyppi :kokonaishintainen
                  :harja.domain.kanavat.kanavan-toimenpide/huoltokohde
                  {:harja.domain.kanavat.kanavan-huoltokohde/nimi "ASENNONMITTAUSLAITTEET"
                   :harja.domain.kanavat.kanavan-huoltokohde/id 5}
                  :harja.domain.kanavat.kanavan-toimenpide/pvm #inst "2017-10-09T21:00:00.000-00:00"
                  :harja.domain.kanavat.kanavan-toimenpide/id 1}]
    (is (s/valid? ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely parametrit) "Kutsu on validi")
    (is (s/valid? ::kanavan-toimenpide/hae-kanavatoimenpiteet-vastaus vastaus) "Vastaus on validi")
    (is (= odotettu (first vastaus)) "Vastauksena saadaan oletettu data")))

(deftest toimenpiteiden-haku-tyhjalla-urakalla-ei-toimi
  (let [parametrit {:harja.domain.urakka/id nil
                    :harja.domain.sopimus/id (hae-saimaan-kanavaurakan-paasopimuksen-id)
                    :harja.domain.toimenpidekoodi/id 597
                    :harja.domain.kanavat.kanavan-toimenpide/alkupvm (pvm/luo-pvm 2017 1 1)
                    :harja.domain.kanavat.kanavan-toimenpide/loppupvm (pvm/luo-pvm 2018 1 1)
                    :harja.domain.kanavat.kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen}]

    (is (not (s/valid? ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely
                       parametrit)))

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