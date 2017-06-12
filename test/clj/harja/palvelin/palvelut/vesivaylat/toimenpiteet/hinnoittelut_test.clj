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
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet.hinnoittelut :as hin]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet.yksikkohintaiset :as yks]
            [harja.kyselyt.vesivaylat.toimenpiteet :as q]
            [clojure.spec.alpha :as s]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :vv-hinnoittelut (component/using
                                           (hin/->Hinnoittelut)
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
  (testing "Uusien hintojen lisäys"
    (let [toimenpide-id (hae-reimari-toimenpide-ilman-hinnoittelua)
          urakka-id (hae-helsingin-vesivaylaurakan-id)
          hinnoittelut-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
          hinnat-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
          insert-params {::toi/urakka-id urakka-id
                         ::toi/id toimenpide-id
                         ::h/hintaelementit [{::hinta/otsikko "Testihinta 1"
                                              ::hinta/yleiskustannuslisa 0
                                              ::hinta/maara 666}
                                             {::hinta/otsikko "Testihinta 2"
                                              ::hinta/yleiskustannuslisa 12
                                              ::hinta/maara 123}]}
          insert-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :tallenna-toimenpiteelle-hinta +kayttaja-jvh+
                                         insert-params)
          hinnat-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
          hinnoittelut-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))]

      (is (s/valid? ::h/tallenna-toimenpiteelle-hinta-kysely insert-params))
      (is (s/valid? ::h/tallenna-toimenpiteelle-hinta-vastaus insert-vastaus))


      (is (= (count (::h/hinnat insert-vastaus)) 2))
      (is (some #(== (::hinta/maara %) 666) (::h/hinnat insert-vastaus)))
      (is (some #(== (::hinta/maara %) 123) (::h/hinnat insert-vastaus)))
      (is (= (+ hinnoittelut-ennen 1) hinnoittelut-jalkeen) "Toimenpiteelle luotiin hinnoittelut")
      (is (= (+ hinnat-ennen 2) hinnat-jalkeen) "Molemmat testihinnat lisättiin")

      (testing "Lisättyjen hintojen päivittäminen"
        (let [hinnoittelut-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
              hinnat-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
              update-params {::toi/urakka-id urakka-id
                             ::toi/id toimenpide-id
                             ::h/hintaelementit (mapv (fn [hinta]
                                                        (assoc hinta ::hinta/maara
                                                                     (case (::hinta/maara hinta)
                                                                       666M 555
                                                                       123M 321)))
                                                      (::h/hinnat insert-vastaus))}
              update-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-toimenpiteelle-hinta +kayttaja-jvh+
                                             update-params)
              hinnat-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
              hinnoittelut-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))]

          (is (s/valid? ::h/tallenna-toimenpiteelle-hinta-kysely update-params))
          (is (s/valid? ::h/tallenna-toimenpiteelle-hinta-vastaus update-vastaus))

          (is (= (count (::h/hinnat update-vastaus)) 2))
          (is (some #(== (::hinta/maara %) 555) (::h/hinnat update-vastaus)))
          (is (some #(== (::hinta/maara %) 321) (::h/hinnat update-vastaus)))
          (is (= hinnoittelut-ennen hinnoittelut-jalkeen))
          (is (= hinnat-ennen hinnat-jalkeen)))))))

(deftest tallenna-toimenpiteelle-hinta-ilman-kirjoitusoikeutta
  (let [toimenpide-id (hae-reimari-toimenpide-ilman-hinnoittelua)
        urakka-id (hae-muhoksen-paallystysurakan-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/id toimenpide-id
                       ::h/hintaelementit []}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-toimenpiteelle-hinta +kayttaja-tero+
                                           kysely-params)))))

(deftest tallenna-toimenpiteelle-hinta-kun-toimenpide-ei-kuulu-urakkaan
  (let [toimenpide-id (hae-reimari-toimenpide-ilman-hinnoittelua)
        urakka-id (hae-muhoksen-paallystysurakan-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/id toimenpide-id
                       ::h/hintaelementit []}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-toimenpiteelle-hinta +kayttaja-jvh+
                                                   kysely-params)))))

(deftest tallenna-toimenpiteelle-hinta-kun-hinnat-eivat-kuulu-toimenpiteeseen
  (let [toimenpide-id (hae-reimari-toimenpide-ilman-hinnoittelua)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/id toimenpide-id
                       ::h/hintaelementit [{::hinta/id 666
                                            ::hinta/otsikko "Testihinta 1"
                                            ::hinta/yleiskustannuslisa 0
                                            ::hinta/maara 666}]}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-toimenpiteelle-hinta +kayttaja-jvh+
                                                   kysely-params)))))

;; TODO Kesken
(deftest tallenna-ryhmalle-hinta
  (testing "Hintojen lisääminen hintaryhmälle"
    (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu)
          urakka-id (hae-helsingin-vesivaylaurakan-id)
          hinnoittelut-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
          hinnat-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
          insert-params {::u/id urakka-id
                         ::h/id hinnoittelu-id
                         ::h/hintaelementit [{::hinta/otsikko "Testihinta 1"
                                              ::hinta/yleiskustannuslisa 0
                                              ::hinta/maara 666}
                                             {::hinta/otsikko "Testihinta 2"
                                              ::hinta/yleiskustannuslisa 12
                                              ::hinta/maara 123}]}
          insert-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :tallenna-hintaryhmalle-hinta +kayttaja-jvh+
                                         insert-params)
          hinnat-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
          hinnoittelut-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))]

      (is (s/valid? ::h/tallenna-hintaryhmalle-hinta-kysely insert-params))
      (is (s/valid? ::h/tallenna-hintaryhmalle-hinta-vastaus insert-vastaus))


      (is (= (count (::h/hinnat insert-vastaus)) 2))
      (is (some #(== (::hinta/maara %) 666) (::h/hinnat insert-vastaus)))
      (is (some #(== (::hinta/maara %) 123) (::h/hinnat insert-vastaus)))
      (is (= (+ hinnoittelut-ennen 1) hinnoittelut-jalkeen) "Toimenpiteelle luotiin hinnoittelut")
      (is (= (+ hinnat-ennen 2) hinnat-jalkeen) "Molemmat testihinnat lisättiin")

      ;; TODO TESTAA PÄIVITTÄMINEN
      )))

;; TODO Failaa!?
(deftest tallenna-ryhmalle-hinta-kun-ryhma-ei-kuulu-urakkaan
  (let [hinnoittelu-id (hae-vanhtaan-vesivaylaurakan-hinnoittelu)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::u/id urakka-id
                       ::h/id hinnoittelu-id
                       ::h/hintaelementit [{::hinta/otsikko "Testihinta 1"
                                            ::hinta/yleiskustannuslisa 0
                                            ::hinta/maara 666}
                                           {::hinta/otsikko "Testihinta 2"
                                            ::hinta/yleiskustannuslisa 12
                                            ::hinta/maara 123}]}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-hintaryhmalle-hinta +kayttaja-jvh+
                                                   kysely-params)))))

;; TODO Failaa!?
(deftest tallenna-ryhmalle-hinta-kun-hinnat-eivat-kuulu-hinnoitteluun
  (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::u/id urakka-id
                       ::h/id hinnoittelu-id
                       ::h/hintaelementit [{::hinta/id 666
                                            ::hinta/otsikko "Testihinta 1"
                                            ::hinta/yleiskustannuslisa 0
                                            ::hinta/maara 666}]}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-hintaryhmalle-hinta +kayttaja-jvh+
                                                   kysely-params)))))

(deftest tallenna-ryhmalle-hinta-ilman-kirjoitusoikeutta
  (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu)
        urakka-id (hae-muhoksen-paallystysurakan-id)
        kysely-params {::u/id urakka-id
                       ::h/id hinnoittelu-id
                       ::h/hintaelementit [{::hinta/otsikko "Testihinta 1"
                                            ::hinta/yleiskustannuslisa 0
                                            ::hinta/maara 666}
                                           {::hinta/otsikko "Testihinta 2"
                                            ::hinta/yleiskustannuslisa 12
                                            ::hinta/maara 123}]}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-hintaryhmalle-hinta +kayttaja-tero+
                                           kysely-params)))))