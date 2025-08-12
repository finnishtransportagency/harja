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



(deftest tallenna-ryhmalle-hinta
  (testing "Hintojen lisääminen hintaryhmälle"
    (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
          urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
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
        urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
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
        urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
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
        urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
  (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
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
  (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
        kysely-params {::u/id urakka-id}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                             :hae-hintaryhmat +kayttaja-tero+
                             kysely-params)))))

(deftest luo-hinnoittelu
  (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
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
  (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
        kysely-params {::u/id urakka-id
                       ::h/nimi "Testi"}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                             :luo-hinnoittelu +kayttaja-tero+
                             kysely-params)))))


(deftest liita-toimenpiteet-hinnoitteluun-ilman-oikeuksia
  (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
        urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
        kysely-params {::toi/idt #{1 2 3}
                       ::h/id hinnoittelu-id
                       ::u/id urakka-id}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                             :liita-toimenpiteet-hinnoitteluun +kayttaja-tero+
                             kysely-params)))))

(deftest liita-toimenpiteet-hinnoitteluun-vaaraan-urakkaan
  (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
        urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        kysely-params {::toi/idt #{1 2 3}
                       ::h/id hinnoittelu-id
                       ::u/id urakka-id}]
    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :liita-toimenpiteet-hinnoitteluun +kayttaja-jvh+
                                     kysely-params)))))

(deftest poista-hintaryhma
  (let [hinnoittelu-id (first (hae-helsingin-vesivaylaurakan-hinnoittelut-jolla-ei-toimenpiteita))
        urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
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
        urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
        kysely-params {::h/urakka-id urakka-id
                       ::h/idt #{hinnoittelu-id}}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                             :poista-tyhjat-hinnoittelut +kayttaja-tero+
                             kysely-params)))))

(deftest poista-hintaryhma-vaarasta-urakkaan
  (let [hinnoittelu-id (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja)
        urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        kysely-params {::h/urakka-id urakka-id
                       ::h/idt #{hinnoittelu-id}}]
    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :poista-tyhjat-hinnoittelut +kayttaja-jvh+
                                     kysely-params)))))

(deftest poista-hintaryhma-jolla-toimenpiteita
  (let [hinnoittelu-id (first (hae-helsingin-vesivaylaurakan-hinnoittelut-jolla-toimenpiteita))
        urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
        kysely-params {::h/urakka-id urakka-id
                       ::h/idt #{hinnoittelu-id}}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                             :poista-tyhjat-hinnoittelut +kayttaja-jvh+
                             kysely-params)))))
