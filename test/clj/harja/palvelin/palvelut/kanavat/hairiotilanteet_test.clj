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
        saimaan-hairiot (ffirst (q "SELECT COUNT(*) FROM kan_hairio WHERE urakka = " urakka-id ";"))]

    (testing "Haku urakkalla"
      (let [params {::hairio/urakka-id urakka-id}
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-hairiotilanteet
                                    +kayttaja-jvh+
                                    params)]

        (is (s/valid? ::hairio/hae-hairiotilanteet-kysely params))
        (is (s/valid? ::hairio/hae-hairiotilanteet-vastaus vastaus))
        (is (>= (count vastaus) saimaan-hairiot))))

    (testing "Haku tyhjällä urakkalla ei toimi"
      (is (not (s/valid? ::hairio/hae-hairiotilanteet-kysely
                         {::hairio/urakka-id nil}))))))

(deftest hairiotilanteiden-haku-ilman-oikeuksia
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        params {::hairio/urakka-id urakka-id}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-hairiotilanteet
                                           +kayttaja-ulle+
                                           params)))))