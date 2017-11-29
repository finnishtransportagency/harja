(ns harja.palvelin.palvelut.kanavat.hairiotilanteet-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [clojure.spec.alpha :as s]
            [harja.palvelin.palvelut.kanavat.hairiotilanteet :as hairiotilanteet]
            [harja.domain.kanavat.hairiotilanne :as hairiotilanne]
            [harja.domain.vesivaylat.materiaali :as vv-materiaali])
  (:import (java.util UUID)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pois-kytketyt-ominaisuudet testi-pois-kytketyt-ominaisuudet
                        :kan-hairio (component/using
                                      (hairiotilanteet/->Hairiotilanteet)
                                      [:http-palvelin :db :pois-kytketyt-ominaisuudet])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hairiotilanteiden-haku
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        lisasopimus-id (hae-saimaan-kanavaurakan-lisasopimuksen-id)
        saimaan-kaikki-hairiot (ffirst (q "SELECT COUNT(*) FROM kan_hairio WHERE urakka = " urakka-id ";"))]

    (testing "Haku urakkalla"
      (let [args {::hairiotilanne/urakka-id urakka-id}
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-hairiotilanteet
                                    +kayttaja-jvh+
                                    args)]

        (is (s/valid? ::hairiotilanne/hae-hairiotilanteet-kysely args))
        (is (s/valid? ::hairiotilanne/hae-hairiotilanteet-vastaus vastaus))
        (is (>= (count vastaus) saimaan-kaikki-hairiot))))

    (testing "Haku tyhjällä urakkalla ei ole validi"
      (is (not (s/valid? ::hairiotilanne/hae-hairiotilanteet-kysely
                         {::hairiotilanne/urakka-id nil}))))

    ;; Testataan filtterit: jokaisen käyttö pitäisi palauttaa pienempi setti kuin
    ;; kaikki urakan häiriöt

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-sopimus-id lisasopimus-id}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-vikaluokka :sahkotekninen_vika}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-korjauksen-tila :kesken}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-korjauksen-tila :kesken}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-odotusaika-h [60 60]}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-korjausaika-h [20 20]}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-paikallinen-kaytto? true}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-aikavali [(pvm/luo-pvm 2015 0 1)
                                                   (pvm/luo-pvm 2015 2 1)]}))
           saimaan-kaikki-hairiot))))

(deftest hairiotilanteiden-haku-ilman-oikeuksia
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        args {::hairiotilanne/urakka-id urakka-id}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-hairiotilanteet
                                           +kayttaja-ulle+
                                           args)))))

(defn tallennuksen-parametrit [syy]
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        kohde-id (hae-saimaan-kanavan-tikkalasaaren-sulun-kohde-id)
        ;; Saimaan materiaalien haku näyttää vähän rumalta, koska harja.testi/q funktio
        ;; käyttää jdbc funktioita suoraan eikä konvertoi PgArrayta nätisti Clojure vektoriksi.
        ;; Siksipä se muunnos täytyy tehdä itse.
        saimaan-materiaalit (hae-saimaan-kanavan-materiaalit)]
    {::hairiotilanne/hairiotilanne {::hairiotilanne/sopimus-id sopimus-id
                                    ::hairiotilanne/kohde-id kohde-id
                                    ::hairiotilanne/paikallinen-kaytto? true
                                    ::hairiotilanne/vikaluokka :sahkotekninen_vika
                                    ::hairiotilanne/korjaustoimenpide "Vähennetään sähköä"
                                    ::hairiotilanne/korjauksen-tila :kesken
                                    ::hairiotilanne/pvm (pvm/luo-pvm 2017 11 17)
                                    ::hairiotilanne/urakka-id urakka-id
                                    ::hairiotilanne/kuittaaja-id (:id +kayttaja-jvh+)
                                    ::hairiotilanne/huviliikenne-lkm 1
                                    ::hairiotilanne/korjausaika-h 1
                                    ::hairiotilanne/syy syy
                                    ::hairiotilanne/odotusaika-h 4
                                    ::vv-materiaali/materiaalit [{:varaosa {::vv-materiaali/muutokset (first (:muutokset saimaan-materiaalit))
                                                                            ::vv-materiaali/nimi (first (:nimi saimaan-materiaalit))}
                                                                  :maara 10}
                                                                 {:varaosa {::vv-materiaali/muutokset (second (:muutokset saimaan-materiaalit))
                                                                            ::vv-materiaali/nimi (second (:muutokset saimaan-materiaalit))}
                                                                  :maara 20}]
                                    ::hairiotilanne/ammattiliikenne-lkm 2}
     ::hairiotilanne/hae-hairiotilanteet-kysely {::hairiotilanne/urakka-id urakka-id}}))

(deftest hairiotilanteen-tallennus
  (let [syy (str "hairiotilanteen-tallennus-testi-" (UUID/randomUUID))
        parametrit (tallennuksen-parametrit syy)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-hairiotilanne
                                +kayttaja-jvh+
                                parametrit)]
    (is (some #(= syy (::hairiotilanne/syy %)) vastaus))))

(deftest hairiotilanteiden-tallennus-ilman-oikeuksia
  (let [syy (str "hairiotilanteen-tallennus-testi-" (UUID/randomUUID))
        parametrit (tallennuksen-parametrit syy)]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-hairiotilanne
                                           +kayttaja-ulle+
                                           parametrit)))))