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

            [harja.domain.kanavat.kanava :as kanava]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.domain.urakka :as ur]
            [harja.domain.sopimus :as sop]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.kanavat.liikennetapahtuma :as lt]))

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

(deftest kanavien-haku
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