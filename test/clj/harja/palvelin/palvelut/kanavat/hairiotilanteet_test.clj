(ns harja.palvelin.palvelut.kanavat.hairiotilanteet-test
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
            [harja.palvelin.palvelut.kanavat.hairiotilanteet :as kan-hairio]
            [clojure.string :as str]

            [harja.domain.kanavat.kanava :as kanava]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.domain.kanavat.hairiotilanne :as hairio]
            [harja.domain.urakka :as ur]
            [harja.domain.muokkaustiedot :as m]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pois-kytketyt-ominaisuudet testi-pois-kytketyt-ominaisuudet
                        :kan-hairio (component/using
                                      (kan-hairio/->Hairiotilanteet)
                                      [:http-palvelin :db :pois-kytketyt-ominaisuudet])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hairiotilanteiden-haku
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        paasopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        lisasopimus-id (hae-saimaan-kanavaurakan-lisasopimuksen-id)
        saimaan-kaikki-hairiot (ffirst (q "SELECT COUNT(*) FROM kan_hairio WHERE urakka = " urakka-id ";"))]

    (testing "Haku urakkalla"
      (let [params {::hairio/urakka-id urakka-id}
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-hairiotilanteet
                                    +kayttaja-jvh+
                                    params)]

        (is (s/valid? ::hairio/hae-hairiotilanteet-kysely params))
        (is (s/valid? ::hairio/hae-hairiotilanteet-vastaus vastaus))
        (is (>= (count vastaus) saimaan-kaikki-hairiot))))

    (testing "Haku tyhjällä urakkalla ei toimi"
      (is (not (s/valid? ::hairio/hae-hairiotilanteet-kysely
                         {::hairio/urakka-id nil}))))

    ;; Testataan filtterit: jokaisen käyttö pitäisi palauttaa pienempi setti kuin
    ;; kaikki urakan häiriöt

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                            :hae-hairiotilanteet
                            +kayttaja-jvh+
                                  {::hairio/urakka-id urakka-id
                                   :haku-sopimus-id lisasopimus-id}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairio/urakka-id urakka-id
                                   :haku-vikaluokka :sahkotekninen_vika}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairio/urakka-id urakka-id
                                   :haku-korjauksen-tila :kesken}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairio/urakka-id urakka-id
                                   :haku-korjauksen-tila :kesken}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairio/urakka-id urakka-id
                                   :haku-odotusaika-h [60 60]}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairio/urakka-id urakka-id
                                   :haku-korjausaika-h [20 20]}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairio/urakka-id urakka-id
                                   :haku-paikallinen-kaytto? true}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairio/urakka-id urakka-id
                                   :haku-aikavali [(pvm/luo-pvm 2015 0 1)
                                                   (pvm/luo-pvm 2015 2 1)]}))
           saimaan-kaikki-hairiot))))

(deftest hairiotilanteiden-haku-ilman-oikeuksia
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        params {::hairio/urakka-id urakka-id}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-hairiotilanteet
                                           +kayttaja-ulle+
                                           params)))))