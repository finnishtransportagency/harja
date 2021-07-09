(ns harja.palvelin.palvelut.vesivaylat.hinnoittelut-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.hinta :as hinta]
            [harja.domain.vesivaylat.toimenpide :as toi]
            [harja.domain.vesivaylat.tyo :as tyo]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.urakka :as u]
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet.apurit :as apurit]
            [harja.palvelin.palvelut.vesivaylat.hinnoittelut :as hin]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [harja.kyselyt.vesivaylat.toimenpiteet :as q]
            [clojure.spec.alpha :as s])
  (:import (org.postgresql.util PSQLException)))

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


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-hinnoittelutiedot-toimenpiteille
  (let [toimenpide-id (hae-reimari-toimenpide-poiujen-korjaus)
        vastaus (q/hae-hinnoittelutiedot-toimenpiteille (:db jarjestelma)
                                                        #{toimenpide-id})]
    (is (number? toimenpide-id))
    (is (= (count vastaus) 1))))

(deftest tallenna-vv-toimenpiteen-hinta
  (let [toimenpide-id (hae-helsingin-reimari-toimenpide-ilman-hinnoittelua)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        hinnoittelut-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
        hinnat-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
        insert-params {::toi/urakka-id urakka-id
                       ::toi/id toimenpide-id
                       ::h/tallennettavat-hinnat [{::hinta/otsikko "Testihinta 1"
                                                   ::hinta/yleiskustannuslisa 0
                                                   ::hinta/summa 666
                                                   ::hinta/ryhma :muu}
                                                  {::hinta/otsikko "Testihinta 2"
                                                   ::hinta/yleiskustannuslisa 12
                                                   ::hinta/yksikkohinta 100
                                                   ::hinta/yksikko "h"
                                                   ::hinta/summa nil
                                                   ::hinta/maara 3
                                                   ::hinta/ryhma :tyo}
                                                  {::hinta/otsikko "Testihinta 3"
                                                   ::hinta/yleiskustannuslisa 12
                                                   ::hinta/komponentti-id "-2139967596"
                                                   ::hinta/komponentti-tilamuutos "1"
                                                   ::hinta/yksikkohinta 2000
                                                   ::hinta/yksikko "kpl"
                                                   ::hinta/summa nil
                                                   ::hinta/maara 1
                                                   ::hinta/ryhma :komponentti}]
                       ::h/tallennettavat-tyot []}
        insert-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-vv-toimenpiteen-hinta +kayttaja-jvh+
                                       insert-params)
        hinnat-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
        hinnoittelut-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))]

    (testing "Uusien hintojen lisäys"
      (is (s/valid? ::h/tallenna-vv-toimenpiteen-hinta-kysely insert-params))
      (is (s/valid? ::h/tallenna-vv-toimenpiteen-hinta-vastaus insert-vastaus))

      (is (= (count (::h/hinnat insert-vastaus)) 3))
      (is (some #(== (::hinta/summa %) 666) (::h/hinnat insert-vastaus)))
      (is (some #(= (::hinta/ryhma %) :tyo) (::h/hinnat insert-vastaus)))
      (is (some #(= (::hinta/maara %) 3.00M) (::h/hinnat insert-vastaus)))
      (is (some #(= (::hinta/yksikkohinta %) 2000.00M) (::h/hinnat insert-vastaus)))
      (is (= (+ hinnoittelut-ennen 1) hinnoittelut-jalkeen) "Toimenpiteelle luotiin hinnoittelu")
      (is (= (+ hinnat-ennen 3) hinnat-jalkeen) "Molemmat testihinnat lisättiin"))

    (testing "Lisättyjen hintojen päivittäminen"
      (let [hinnoittelut-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
            hinnat-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
            update-params {::toi/urakka-id urakka-id
                           ::toi/id toimenpide-id
                           ::h/tallennettavat-hinnat (mapv (fn [hinta]
                                                             (case (::hinta/ryhma hinta)
                                                               :komponentti (assoc hinta ::hinta/yksikkohinta 15000
                                                                                         ::hinta/summa nil)
                                                               :tyo (assoc hinta ::hinta/maara 6
                                                                                 ::hinta/summa nil)
                                                               :muu (assoc hinta ::hinta/summa 555)))
                                                           (::h/hinnat insert-vastaus))
                           ::h/tallennettavat-tyot []}
            update-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-vv-toimenpiteen-hinta +kayttaja-jvh+
                                           update-params)
            hinnat-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
            hinnoittelut-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))]

        (is (s/valid? ::h/tallenna-vv-toimenpiteen-hinta-kysely update-params))
        (is (s/valid? ::h/tallenna-vv-toimenpiteen-hinta-vastaus update-vastaus))

        (is (= (count (::h/hinnat update-vastaus)) 3))
        (is (some #(== (::hinta/summa %) 555) (::h/hinnat update-vastaus)))
        (is (= hinnoittelut-ennen hinnoittelut-jalkeen))
        (is (= hinnat-ennen hinnat-jalkeen))))))

(deftest tallenna-toimenpiteelle-tyot
  (testing "Uusien töiden lisäys"
    (let [toimenpide-id (hae-helsingin-reimari-toimenpide-ilman-hinnoittelua)
          urakka-id (hae-helsingin-vesivaylaurakan-id)
          toimenpidekoodi-id (ffirst (q "SELECT id
                                        FROM toimenpidekoodi
                                        WHERE nimi = 'Henkilöstö: Ammattimies'"))
          hinnoittelut-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
          tyot-ennen (ffirst (q "SELECT COUNT(*) FROM vv_tyo WHERE poistettu IS NOT TRUE"))
          insert-params {::toi/urakka-id urakka-id
                         ::toi/id toimenpide-id
                         ::h/tallennettavat-hinnat []
                         ::h/tallennettavat-tyot
                         [{::tyo/toimenpidekoodi-id toimenpidekoodi-id
                           ::tyo/maara 666}
                          {::tyo/toimenpidekoodi-id toimenpidekoodi-id
                           ::tyo/maara 123}]}
          insert-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :tallenna-vv-toimenpiteen-hinta +kayttaja-jvh+
                                         insert-params)
          tyot-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_tyo WHERE poistettu IS NOT TRUE"))
          hinnoittelut-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))]

      (is (s/valid? ::h/tallenna-vv-toimenpiteen-hinta-kysely insert-params))
      (is (s/valid? ::h/tallenna-vv-toimenpiteen-hinta-vastaus insert-vastaus))

      (is (= (count (::h/tyot insert-vastaus)) 2))
      (is (some #(== (::tyo/maara %) 666) (::h/tyot insert-vastaus)))
      (is (some #(== (::tyo/maara %) 123) (::h/tyot insert-vastaus)))
      (is (= (+ hinnoittelut-ennen 1) hinnoittelut-jalkeen) "Toimenpiteelle luotiin hinnoittelu")
      (is (= (+ tyot-ennen 2) tyot-jalkeen) "Molemmat työt lisättiin")

      (testing "Lisättyjen töiden päivittäminen"
        (let [hinnoittelut-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
              tyot-ennen (ffirst (q "SELECT COUNT(*) FROM vv_tyo WHERE poistettu IS NOT TRUE"))
              update-params {::toi/urakka-id urakka-id
                             ::toi/id toimenpide-id
                             ::h/tallennettavat-tyot
                             (mapv (fn [hinta]
                                     (assoc hinta ::tyo/maara
                                                  (case (::tyo/maara hinta)
                                                    666M 555
                                                    123M 321)))
                                   (::h/tyot insert-vastaus))
                             ::h/tallennettavat-hinnat []}
              update-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-vv-toimenpiteen-hinta +kayttaja-jvh+
                                             update-params)
              tyot-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_tyo WHERE poistettu IS NOT TRUE"))
              hinnoittelut-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))]

          (is (s/valid? ::h/tallenna-vv-toimenpiteen-hinta-kysely update-params))
          (is (s/valid? ::h/tallenna-vv-toimenpiteen-hinta-vastaus update-vastaus))

          (is (= (count (::h/tyot update-vastaus)) 2))
          (is (some #(== (::tyo/maara %) 555) (::h/tyot update-vastaus)))
          (is (some #(== (::tyo/maara %) 321) (::h/tyot update-vastaus)))
          (is (= hinnoittelut-ennen hinnoittelut-jalkeen))
          (is (= tyot-ennen tyot-jalkeen)))))))

(deftest tallenna-vv-toimenpiteen-hinta-kun-toimenpide-ei-kuulu-urakkaan
  (let [toimenpide-id (hae-helsingin-reimari-toimenpide-ilman-hinnoittelua)
        muhos-id (hae-muhoksen-paallystysurakan-id)
        insert-params {::toi/urakka-id muhos-id
                       ::toi/id toimenpide-id
                       ::h/tallennettavat-hinnat []
                       ::h/tallennettavat-tyot []}]
    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                         :tallenna-vv-toimenpiteen-hinta +kayttaja-jvh+
                         insert-params)))))

(deftest tallenna-tyot-jotka-eivat-kuulu-toimenpiteeseen
  (let [toimenpide-id (hae-helsingin-reimari-toimenpide-ilman-hinnoittelua)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        toimenpidekoodi-id (ffirst (q "SELECT id
                                        FROM toimenpidekoodi
                                        WHERE nimi = 'Henkilöstö: Ammattimies'"))
        insert-params {::toi/urakka-id urakka-id
                       ::toi/id toimenpide-id
                       ::h/tallennettavat-hinnat []
                       ::h/tallennettavat-tyot
                       [{::tyo/toimenpidekoodi-id toimenpidekoodi-id
                         ::tyo/maara 666
                         ::tyo/id 1}
                        {::tyo/toimenpidekoodi-id toimenpidekoodi-id
                         ::tyo/maara 123
                         ::tyo/id 2}]}]
    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-vv-toimenpiteen-hinta +kayttaja-jvh+
                                                   insert-params)))))

(deftest tallenna-vv-toimenpiteen-hinta-ilman-kirjoitusoikeutta
  (let [toimenpide-id (hae-helsingin-reimari-toimenpide-ilman-hinnoittelua)
        urakka-id (hae-muhoksen-paallystysurakan-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/id toimenpide-id
                       ::h/tallennettavat-hinnat []
                       ::h/tallennettavat-tyot []}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-vv-toimenpiteen-hinta +kayttaja-tero+
                                           kysely-params)))))

(deftest tallenna-vv-toimenpiteen-hinta-kun-toimenpide-ei-kuulu-urakkaan
  (let [toimenpide-id (hae-helsingin-reimari-toimenpide-ilman-hinnoittelua)
        urakka-id (hae-muhoksen-paallystysurakan-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/id toimenpide-id
                       ::h/tallennettavat-hinnat []
                       ::h/tallennettavat-tyot []}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-vv-toimenpiteen-hinta +kayttaja-jvh+
                                                   kysely-params)))))

(deftest tallenna-vv-toimenpiteen-hinta-kun-hinnat-eivat-kuulu-toimenpiteeseen
  (let [toimenpide-id (hae-helsingin-reimari-toimenpide-ilman-hinnoittelua)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/id toimenpide-id
                       ::h/tallennettavat-hinnat [{::hinta/id (hae-vantaan-vesivaylaurakan-hinta)
                                                   ::hinta/otsikko "Testihinta 1"
                                                   ::hinta/yleiskustannuslisa 0
                                                   ::hinta/ryhma :muu
                                                   ::hinta/summa 666}]
                       ::h/tallennettavat-tyot []}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-vv-toimenpiteen-hinta +kayttaja-jvh+
                                                   kysely-params)))))

(deftest tallenna-toimenpiteelle-ylimaarainen-hinnoittelu
  (let [toimenpide-id (ffirst (hae-helsingin-reimari-toimenpiteet-molemmilla-hinnoitteluilla {:limit 1}))
        hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
        hinnoittelujen-maara (:maara (first (q-map (str "SELECT COUNT(*) AS maara FROM vv_hinnoittelu_toimenpide WHERE \"toimenpide-id\"=" toimenpide-id ";"))))]
    (is (= 2 hinnoittelujen-maara))
    (is (thrown? PSQLException (q-map (str "INSERT INTO vv_hinnoittelu_toimenpide (\"toimenpide-id\", \"hinnoittelu-id\", luoja) VALUES (" toimenpide-id ", " hinnoittelu-id ", 1);"))))))

(deftest tallenna-toimenpiteelle-toinen-ryhmahinnoittelu
  (let [toimenpide-id (hae-helsingin-reimari-toimenpide-yhdella-hinnoittelulla {:hintaryhma? true})
        hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja {:hintaryhma? true})
        hinnoittelujen-maara (:maara (first (q-map (str "SELECT COUNT(*) AS maara FROM vv_hinnoittelu_toimenpide WHERE \"toimenpide-id\"=" toimenpide-id ";"))))]
    (is (= 1 hinnoittelujen-maara))
    (is (thrown? PSQLException (q-map (str "INSERT INTO vv_hinnoittelu_toimenpide (\"toimenpide-id\", \"hinnoittelu-id\", luoja) VALUES (" toimenpide-id ", " hinnoittelu-id ", 1);"))))))

(deftest tallenna-toimenpiteelle-toinen-oma-hinnoittelu
  (let [toimenpide-id (hae-helsingin-reimari-toimenpide-yhdella-hinnoittelulla {:hintaryhma? false})
        hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja {:hintaryhma? false})
        hinnoittelujen-maara (:maara (first (q-map (str "SELECT COUNT(*) AS maara FROM vv_hinnoittelu_toimenpide WHERE \"toimenpide-id\"=" toimenpide-id ";"))))]
    (is (= 1 hinnoittelujen-maara))
    (is (thrown? PSQLException (q-map (str "INSERT INTO vv_hinnoittelu_toimenpide (\"toimenpide-id\", \"hinnoittelu-id\", luoja) VALUES (" toimenpide-id ", " hinnoittelu-id ", 1);"))))))

(deftest tallenna-ryhmalle-hinta
  (testing "Hintojen lisääminen hintaryhmälle"
    (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
          urakka-id (hae-helsingin-vesivaylaurakan-id)
          hinnoittelut-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
          hinnat-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
          insert-params {::u/id urakka-id
                         ::h/id hinnoittelu-id
                         ::h/tallennettavat-hinnat [{::hinta/otsikko "Testihinta 1"
                                                     ::hinta/yleiskustannuslisa 0
                                                     ::hinta/summa 666
                                                     ::hinta/ryhma :muu}
                                                    {::hinta/otsikko "Testihinta 2"
                                                     ::hinta/yleiskustannuslisa 12
                                                     ::hinta/summa 123
                                                     ::hinta/ryhma :muu}]}
          insert-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :tallenna-hintaryhmalle-hinta +kayttaja-jvh+
                                         insert-params)
          hinnat-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
          hinnoittelut-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
          paivitetty-hinnoittelu (first (filter #(= (::h/id %) hinnoittelu-id) insert-vastaus))]

      (is (s/valid? ::h/tallenna-hintaryhmalle-hinta-kysely insert-params))
      (is (s/valid? ::h/tallenna-hintaryhmalle-hinta-vastaus insert-vastaus))

      (is (map? paivitetty-hinnoittelu))
      (is (= (count (::h/hinnat paivitetty-hinnoittelu)) 2))
      (is (some #(== (::hinta/summa %) 666) (::h/hinnat paivitetty-hinnoittelu)))
      (is (some #(== (::hinta/summa %) 123) (::h/hinnat paivitetty-hinnoittelu))
          (is (= (+ hinnat-ennen 2) hinnat-jalkeen) "Molemmat testihinnat lisättiin"))
      (is (= hinnoittelut-ennen hinnoittelut-jalkeen) "Hinnoittelujen määrä ei muuttunut")

      (testing "Lisättyjen hintojen päivittäminen"
        (let [hinnoittelut-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
              hinnat-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
              update-params {::u/id urakka-id
                             ::h/id hinnoittelu-id
                             ::h/tallennettavat-hinnat (mapv (fn [hinta]
                                                               (assoc hinta ::hinta/summa
                                                                            (case (::hinta/summa hinta)
                                                                              666M 555
                                                                              123M 321)))
                                                             (::h/hinnat paivitetty-hinnoittelu))}
              update-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-hintaryhmalle-hinta +kayttaja-jvh+
                                             update-params)
              hinnat-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinta"))
              hinnoittelut-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
              paivitetty-hinnoittelu (first (filter #(= (::h/id %) hinnoittelu-id) update-vastaus))]

          (is (s/valid? ::h/tallenna-hintaryhmalle-hinta-kysely insert-params))
          (is (s/valid? ::h/tallenna-hintaryhmalle-hinta-vastaus insert-vastaus))

          (is (map? paivitetty-hinnoittelu))
          (is (= (count (::h/hinnat paivitetty-hinnoittelu)) 2))
          (is (some #(== (::hinta/summa %) 555) (::h/hinnat paivitetty-hinnoittelu)))
          (is (some #(== (::hinta/summa %) 321) (::h/hinnat paivitetty-hinnoittelu)))
          (is (= hinnat-ennen hinnat-jalkeen) "Hintojen määrä pystyi samana päivityksessä")
          (is (= hinnoittelut-ennen hinnoittelut-jalkeen) "Hinnoittelujen määrä ei muuttunut edelleenkään"))))))

(deftest tallenna-ryhmalle-hinta-kun-ryhma-ei-kuulu-urakkaan
  (let [hinnoittelu-id (hae-vantaan-vesivaylaurakan-hinnoittelu)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::u/id urakka-id
                       ::h/id hinnoittelu-id
                       ::h/tallennettavat-hinnat [{::hinta/otsikko "Testihinta 1"
                                                   ::hinta/yleiskustannuslisa 0
                                                   ::hinta/summa 666
                                                   ::hinta/ryhma :muu}
                                                  {::hinta/otsikko "Testihinta 2"
                                                   ::hinta/yleiskustannuslisa 12
                                                   ::hinta/summa 123
                                                   ::hinta/ryhma :muu}]
                       ::h/tallennettavat-tyot []}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-hintaryhmalle-hinta +kayttaja-jvh+
                                                   kysely-params)))))

(deftest tallenna-ryhmalle-hinta-kun-hinnat-eivat-kuulu-hinnoitteluun
  (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::u/id urakka-id
                       ::h/id hinnoittelu-id
                       ::h/tallennettavat-hinnat [{::hinta/id (hae-vantaan-vesivaylaurakan-hinta)
                                                   ::hinta/otsikko "Testihinta 1"
                                                   ::hinta/yleiskustannuslisa 0
                                                   ::hinta/ryhma :muu
                                                   ::hinta/summa 666}]
                       ::h/tallennettavat-tyot []}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-hintaryhmalle-hinta +kayttaja-jvh+
                                                   kysely-params)))))

(deftest tallenna-ryhmalle-hinta-ilman-kirjoitusoikeutta
  (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
        urakka-id (hae-muhoksen-paallystysurakan-id)
        kysely-params {::u/id urakka-id
                       ::h/id hinnoittelu-id
                       ::h/tallennettavat-hinnat [{::hinta/otsikko "Testihinta 1"
                                                   ::hinta/yleiskustannuslisa 0
                                                   ::hinta/summa 666
                                                   ::hinta/ryhma :muu}
                                                  {::hinta/otsikko "Testihinta 2"
                                                   ::hinta/yleiskustannuslisa 12
                                                   ::hinta/summa 123
                                                   ::hinta/ryhma :muu}]
                       ::h/tallennettavat-tyot []}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-hintaryhmalle-hinta +kayttaja-tero+
                                           kysely-params)))))

(deftest hae-hinnoittelut
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::u/id urakka-id}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-hintaryhmat +kayttaja-jvh+
                                kysely-params)]

    (is (s/valid? ::h/hae-hintaryhmat-kysely kysely-params))
    (is (s/valid? ::h/hae-hintaryhmat-vastaus vastaus))

    (is (>= (count vastaus) 1))
    (is (>= (count (mapcat ::h/hinnat vastaus)) 1))
    (is (every? (comp not ::m/poistettu?) (mapcat ::h/hinnat vastaus)))
    (is (some #(= (::h/nimi %) "Hietasaaren poijujen korjaus") vastaus))))

(deftest hae-hinnoittelut-ilman-oikeuksia
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::u/id urakka-id}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-hintaryhmat +kayttaja-tero+
                                           kysely-params)))))

(deftest luo-hinnoittelu
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::u/id urakka-id
                       ::h/nimi "Testi123"}]
    (testing "Luodaan uusi hinnoittelu"
      (let [hinnoittelut-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :luo-hinnoittelu +kayttaja-jvh+
                                    kysely-params)
            hinnoittelut-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu"))]

        (is (s/valid? ::h/luo-hinnoittelu-kysely kysely-params))
        (is (s/valid? ::h/luo-hinnoittelu-vastaus vastaus))

        ;; Hinnoittelu lisättiin
        (is (= (+ hinnoittelut-ennen 1) hinnoittelut-jalkeen))

        ;; Sama hinnoittelu palautui
        (is (= (::h/urakka-id vastaus) urakka-id))
        (is (= (::h/nimi vastaus) "Testi123"))
        (is (true? (::h/hintaryhma? vastaus)))
        (is (integer? (::h/id vastaus)))))

    ;; Yritetään luoda samalla nimellä uusi hintaryhmä
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-hintaryhmat +kayttaja-tero+
                                           kysely-params))
        "Hintaryhmän nimi on jo olemassa urakassa, pitäisi tulla poikkeus")))

(deftest luo-hinnoittelu-ilman-oikeuksia
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::u/id urakka-id
                       ::h/nimi "Testi"}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :luo-hinnoittelu +kayttaja-tero+
                                           kysely-params)))))

(deftest liita-toimenpiteet-hinnoitteluun
  (let [hinnoittelu-on-hintaryhma? (fn [hinnoittelu-id]
                                     (ffirst (q "SELECT hintaryhma FROM vv_hinnoittelu WHERE id = " hinnoittelu-id ";")))
        hae-toimenpiteen-hinnoittelut-idt (fn [toimenpide-id]
                                            (map :hinnoittelu-id
                                                 (q-map "SELECT \"hinnoittelu-id\"
                                              FROM vv_hinnoittelu_toimenpide
                                              WHERE \"toimenpide-id\" = " toimenpide-id
                                                        "AND poistettu IS NOT TRUE;")))
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        toimenpide-id (hae-reimari-toimenpide-poiujen-korjaus)
        toimenpiteen-hinnoittelu-idt-ennen (hae-toimenpiteen-hinnoittelut-idt toimenpide-id)
        liitettava-hinnoittelu-id (first (map :id (q-map (str "SELECT id FROM vv_hinnoittelu
                                             WHERE \"urakka-id\" = " urakka-id "
                                             AND hintaryhma IS TRUE
                                             AND poistettu IS NOT TRUE
                                             AND id NOT IN (" (str/join ", " toimenpiteen-hinnoittelu-idt-ennen) ")"))))
        kysely-params {::toi/idt #{toimenpide-id}
                       ::h/id liitettava-hinnoittelu-id
                       ::u/id urakka-id}
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :liita-toimenpiteet-hinnoitteluun +kayttaja-jvh+
                          kysely-params)
        toimenpiteen-hinnoittelu-idt-jalkeen (hae-toimenpiteen-hinnoittelut-idt toimenpide-id)]

    ;; Tilanne ennen testiä on halutunlainen
    (is (= (count toimenpiteen-hinnoittelu-idt-ennen) 2) "Testattavan toimenpiteen pitää kuulua kahteen hinnoitteluun")
    (is (= (set (map hinnoittelu-on-hintaryhma? toimenpiteen-hinnoittelu-idt-ennen))
           #{true false})
        "Testattavan toimenpiteen kuulua hintaryhmään sekä omaan hinnoitteluun")
    (is (s/valid? ::h/liita-toimenpiteet-hinnotteluun-kysely kysely-params))

    ;; Tilanne testin jälkeen:
    (is (= (count toimenpiteen-hinnoittelu-idt-jalkeen) 2) "Toimenpide kuuluu edelleen kahteen hinnoitteluun")
    (is ((set toimenpiteen-hinnoittelu-idt-jalkeen) liitettava-hinnoittelu-id) "Toimenpide kuuluu nyt uuteen hinnoitteluun")
    (is (= (set (map hinnoittelu-on-hintaryhma? toimenpiteen-hinnoittelu-idt-jalkeen))
           #{true false})
        "Toimenpide kuuluu edelleen hintaryhmään sekä omaan hinnoitteluun")))

(deftest liita-toimenpiteet-hinnoitteluun-ilman-oikeuksia
  (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::toi/idt #{1 2 3}
                       ::h/id hinnoittelu-id
                       ::u/id urakka-id}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :liita-toimenpiteet-hinnoitteluun +kayttaja-tero+
                                           kysely-params)))))

(deftest liita-toimenpiteet-hinnoitteluun-vaaraan-urakkaan
  (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
        urakka-id (hae-muhoksen-paallystysurakan-id)
        kysely-params {::toi/idt #{1 2 3}
                       ::h/id hinnoittelu-id
                       ::u/id urakka-id}]
    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :liita-toimenpiteet-hinnoitteluun +kayttaja-jvh+
                                                   kysely-params)))))

(deftest poista-hintaryhma
  (let [hinnoittelu-id (first (hae-helsingin-vesivaylaurakan-hinnoittelut-jolla-ei-toimenpiteita))
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        hinnoittelut-ennen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu WHERE poistettu IS NOT TRUE"))
        kysely-params {::h/urakka-id urakka-id
                       ::h/idt #{hinnoittelu-id}}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :poista-tyhjat-hinnoittelut +kayttaja-jvh+
                                kysely-params)
        hinnoittelut-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_hinnoittelu WHERE poistettu IS NOT TRUE"))
        hinnoittelu-poistettu? (ffirst (q "SELECT poistettu FROM vv_hinnoittelu WHERE id = " hinnoittelu-id ";"))]

    (is (s/valid? ::h/poista-tyhjat-hinnoittelut-kysely kysely-params))
    (is (s/valid? ::h/poista-tyhjat-hinnoittelut-vastaus vastaus))

    ;; Hinnoittelu poistui
    (is (= hinnoittelut-ennen (+ hinnoittelut-jalkeen 1)))
    (is hinnoittelu-poistettu?)))

(deftest poista-hintaryhma-ilman-oikeuksia
  (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::h/urakka-id urakka-id
                       ::h/idt #{hinnoittelu-id}}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :poista-tyhjat-hinnoittelut +kayttaja-tero+
                                           kysely-params)))))

(deftest poista-hintaryhma-vaarasta-urakkaan
  (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
        urakka-id (hae-muhoksen-paallystysurakan-id)
        kysely-params {::h/urakka-id urakka-id
                       ::h/idt #{hinnoittelu-id}}]
    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :poista-tyhjat-hinnoittelut +kayttaja-jvh+
                                                   kysely-params)))))

(deftest poista-hintaryhma-jolla-toimenpiteita
  (let [hinnoittelu-id (first (hae-helsingin-vesivaylaurakan-hinnoittelut-jolla-toimenpiteita))
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::h/urakka-id urakka-id
                       ::h/idt #{hinnoittelu-id}}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :poista-tyhjat-hinnoittelut +kayttaja-jvh+
                                           kysely-params)))))
