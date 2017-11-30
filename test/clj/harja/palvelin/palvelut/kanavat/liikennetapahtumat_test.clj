(ns harja.palvelin.palvelut.kanavat.liikennetapahtumat-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :as pois-kytketyt-ominaisuudet]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
            [harja.palvelin.palvelut.kanavat.liikennetapahtumat :as kan-liikennetapahtumat]
            [clojure.string :as str]

            [harja.domain.urakka :as ur]
            [harja.domain.sopimus :as sop]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.lt-toiminto :as toiminto]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pois-kytketyt-ominaisuudet testi-pois-kytketyt-ominaisuudet
                        :kan-liikennetapahtumat (component/using
                                                  (kan-liikennetapahtumat/->Liikennetapahtumat)
                                                  [:http-palvelin :db :pois-kytketyt-ominaisuudet])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest tapahtumien-haku
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        params {::ur/id urakka-id
                ::sop/id sopimus-id}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-liikennetapahtumat
                                +kayttaja-jvh+
                                params)]

    (is (s/valid? ::lt/hae-liikennetapahtumat-kysely params))
    (is (s/valid? ::lt/hae-liikennetapahtumat-vastaus vastaus))

    (is (true?
          (boolean
            (some
              (fn [tapahtuma]
                (not-empty (::lt/alukset tapahtuma)))
              vastaus))))))

(deftest edellisten-haku
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        kohde-id (hae-kohde-soskua)
        params {::lt/urakka-id urakka-id
                ::lt/sopimus-id sopimus-id
                ::lt/kohde-id kohde-id}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-edelliset-tapahtumat
                                +kayttaja-jvh+
                                params)]

    (is (s/valid? ::lt/hae-edelliset-tapahtumat-kysely params))
    (is (s/valid? ::lt/hae-edelliset-tapahtumat-vastaus vastaus))

    ;; Nämä muuttuu kun palvelu päivitetään palauttamaan
    ;; edellisten kohteiden tietoja
    (is (nil? (:ylos vastaus)))
    (is (nil? (:alas vastaus)))

    (is (and
          (some? (:kohde vastaus))
          (number? (get-in vastaus [:kohde ::lt/id]))))))

(deftest tapahtuman-tallentaminen
  (testing "Uuden luonti"
    (let [urakka-id (hae-saimaan-kanavaurakan-id)
          sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
          kohde-id (hae-kohde-kansola)
          [kohteenosa-id tyyppi] (hae-kohteenosat-kansola)
          _ (is (= tyyppi "sulku") "Kansolan kohteenosan tyyppiä on vaihdettu, päivitä testi tai testidata.")
          hakuparametrit {::ur/id urakka-id
                          ::sop/id sopimus-id}
          vanhat (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-liikennetapahtumat
                                 +kayttaja-jvh+
                                 hakuparametrit)
          _ (is (not-empty vanhat))
          params {::lt/urakka-id urakka-id
                  ::lt/sopimus-id sopimus-id
                  ::lt/kohde-id kohde-id
                  ::lt/aika (pvm/nyt)
                  ::lt/toiminnot [{::toiminto/kohteenosa-id kohteenosa-id
                              ::toiminto/kohde-id kohde-id
                              ::toiminto/toimenpide :sulutus
                              ::toiminto/palvelumuoto :kauko
                              ::toiminto/lkm 1}]
                  ::lt/alukset [{::lt-alus/laji :HUV
                                 ::lt-alus/lkm 1
                                 ::lt-alus/suunta :ylos}]
                  ::lt/vesipinta-alaraja 500
                  ::lt/vesipinta-ylaraja 1000
                  ::lt/kuittaaja-id (:id +kayttaja-jvh+)
                  ::lt/lisatieto "FOOBAR FOOBAR"
                  :hakuparametrit hakuparametrit}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-liikennetapahtuma
                                  +kayttaja-jvh+
                                  params)]

      (is (s/valid? ::lt/tallenna-liikennetapahtuma-kysely params))
      (is (s/valid? ::lt/tallenna-liikennetapahtuma-vastaus vastaus))

      (is (= (count vastaus) (inc (count vanhat))))))

  (testing "Muokkaaminen"
    (let [urakka-id (hae-saimaan-kanavaurakan-id)
          sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
          [kohteenosa-id tyyppi] (hae-kohteenosat-kansola)
          _ (is (= tyyppi "sulku") "Kansolan kohteenosan tyyppiä on vaihdettu, päivitä testi tai testidata.")
          kohde-id (hae-kohde-kansola)
          tapahtuma-id (ffirst (q (str "SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'FOOBAR FOOBAR';")))
          hakuparametrit {::ur/id urakka-id
                          ::sop/id sopimus-id}
          vanhat (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-liikennetapahtumat
                                 +kayttaja-jvh+
                                 hakuparametrit)
          _ (is (not-empty vanhat))
          params {::lt/urakka-id urakka-id
                  ::lt/sopimus-id sopimus-id
                  ::lt/kohde-id kohde-id
                  ::lt/id tapahtuma-id
                  ::lt/aika (pvm/nyt)
                  ::lt/toiminnot []
                  ::lt/vesipinta-alaraja 500
                  ::lt/vesipinta-ylaraja 1000
                  ::lt/kuittaaja-id (:id +kayttaja-jvh+)
                  ::lt/lisatieto "FOOBAR FOOBAR FOOBAR"
                  :hakuparametrit hakuparametrit}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-liikennetapahtuma
                                  +kayttaja-jvh+
                                  params)]

      (is (s/valid? ::lt/tallenna-liikennetapahtuma-kysely params))
      (is (s/valid? ::lt/tallenna-liikennetapahtuma-vastaus vastaus))

      (is (= (count vastaus) (count vanhat)))

      (is (some #(= (::lt/lisatieto %) "FOOBAR FOOBAR") vanhat))
      (is (some #(= (::lt/lisatieto %) "FOOBAR FOOBAR FOOBAR") vastaus))))

  (testing "Poistaminen"
    (let [urakka-id (hae-saimaan-kanavaurakan-id)
          sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
          kohde-id (hae-kohde-kansola)
          tapahtuma-id (ffirst (q (str "SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'FOOBAR FOOBAR FOOBAR';")))
          hakuparametrit {::ur/id urakka-id
                          ::sop/id sopimus-id}
          vanhat (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-liikennetapahtumat
                                 +kayttaja-jvh+
                                 hakuparametrit)
          _ (is (not-empty vanhat))
          params {::lt/urakka-id urakka-id
                  ::lt/sopimus-id sopimus-id
                  ::lt/kohde-id kohde-id
                  ::lt/id tapahtuma-id
                  ::lt/aika (pvm/nyt)
                  ::lt/toiminnot []
                  ::lt/vesipinta-alaraja 500
                  ::lt/vesipinta-ylaraja 1000
                  ::lt/kuittaaja-id (:id +kayttaja-jvh+)
                  ::lt/lisatieto "FOOBAR FOOBAR FOOBAR"
                  ::m/poistettu? true
                  :hakuparametrit hakuparametrit}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-liikennetapahtuma
                                  +kayttaja-jvh+
                                  params)]

      (is (s/valid? ::lt/tallenna-liikennetapahtuma-kysely params))
      (is (s/valid? ::lt/tallenna-liikennetapahtuma-vastaus vastaus))

      (is (= (count vastaus) (dec (count vanhat))))

      (is (some #(= (::lt/lisatieto %) "FOOBAR FOOBAR FOOBAR") vanhat))
      (is (empty? (filter #(= (::lt/lisatieto %) "FOOBAR FOOBAR FOOBAR") vastaus))))))