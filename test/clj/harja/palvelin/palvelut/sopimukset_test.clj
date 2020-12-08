(ns harja.palvelin.palvelut.sopimukset-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.domain.sopimus :as sopimus]
            [taoensso.timbre :as log]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [harja.palvelin.palvelut.sopimukset :as sopimukset]))

(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :sopimukset (component/using
                                      (sopimukset/->Sopimukset)
                                      [:db :http-palvelin])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest sopimuksien-haku-test
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-harjassa-luodut-sopimukset +kayttaja-jvh+ {})]
    (is (>= (count vastaus) 3))
    (is (s/valid? ::sopimus/hae-harjassa-luodut-sopimukset-vastaus vastaus))))

(deftest sopimuksen-tallennus-ja-paivitys-toimii
  (let [testisopimukset (map #(-> %
                                  (assoc ::sopimus/paasopimus-id nil)
                                  (dissoc ::sopimus/id))
                             (gen/sample (s/gen ::sopimus/tallenna-sopimus-kysely)))]

    (doseq [sopimus testisopimukset]
      ;; Luo uusi sopimus
      (let [sopimus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-sopimus +kayttaja-jvh+
                                             sopimus)]
        ;; Uusi sopimus löytyy vastauksesesta
        (is (= (::sopimus/nimi sopimus-kannassa) (::sopimus/nimi sopimus)))

        ;; Päivitetään sopimus (myös reimari-diaarinro, sillä se tallennetaan eri taulukkoon, kuin muu data)
        (let [paivitetty-sopimus (assoc sopimus ::sopimus/nimi (str (::sopimus/nimi sopimus) " päivitetty")
                                                ::sopimus/reimari-diaarinro (str (::sopimus/reimari-diaarinro sopimus) " 5/5")
                                                ::sopimus/id (::sopimus/id sopimus-kannassa))
              paivitetty-sopimus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                          :tallenna-sopimus +kayttaja-jvh+
                                                          paivitetty-sopimus)]

          ;; Sopimus päivittyi
          (is (= (::sopimus/nimi paivitetty-sopimus-kannassa)
                 (::sopimus/nimi paivitetty-sopimus)))
          (is (= (::sopimus/reimari-diaarinro paivitetty-sopimus-kannassa)
                 (::sopimus/reimari-diaarinro paivitetty-sopimus))))))))
