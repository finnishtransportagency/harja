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
            [harja.domain.vesivaylat.materiaali :as vv-materiaali]
            [clojure.test.check.generators :as gen]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.komponentit.sonja :as sonja])
  (:import (java.util UUID)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pois-kytketyt-ominaisuudet testi-pois-kytketyt-ominaisuudet
                        :fim (component/using
                               (fim/->FIM +testi-fim+)
                               [:db :integraatioloki])
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil)
                                           [:db])
                        :sonja (feikki-sonja)
                        :sonja-sahkoposti (component/using
                                            (sahkoposti/luo-sahkoposti "foo@example.com"
                                                                       {:sahkoposti-sisaan-jono "email-to-harja"
                                                                        :sahkoposti-ulos-jono "harja-to-email"
                                                                        :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                                            [:sonja :db :integraatioloki])
                        :kan-hairio (component/using
                                      (hairiotilanteet/->Hairiotilanteet)
                                      [:http-palvelin :db :fim :sonja-sahkoposti])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each jarjestelma-fixture)

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

(defn tallennuksen-parametrit [syy naulan-kulutus amparin-kulutus]
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        kohde-id (hae-saimaan-kanavan-tikkalasaaren-sulun-kohde-id)]
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
                                    ::hairiotilanne/ammattiliikenne-lkm 2}
     ::hairiotilanne/hae-hairiotilanteet-kysely {::hairiotilanne/urakka-id urakka-id}
     ::vv-materiaali/materiaalikirjaukset [{::vv-materiaali/maara (- naulan-kulutus)
                                            ::vv-materiaali/nimi "Naulat"
                                            ::vv-materiaali/urakka-id urakka-id
                                            ::vv-materiaali/pvm (pvm/nyt)}
                                           {::vv-materiaali/maara (- amparin-kulutus)
                                            ::vv-materiaali/nimi "Ämpäreitä"
                                            ::vv-materiaali/urakka-id urakka-id
                                            ::vv-materiaali/pvm (pvm/nyt)}]
     ::vv-materiaali/poista-materiaalikirjauksia []}))

(deftest hairiotilanteen-tallennus
  (let [syy (str "hairiotilanteen-tallennus-testi-" (UUID/randomUUID))
        naulan-kulutus 10
        amparin-kulutus 20
        parametrit (tallennuksen-parametrit syy naulan-kulutus amparin-kulutus)
        urakka-id (hae-saimaan-kanavaurakan-id)
        saimaan-materiaalit-ennen-tallennusta (hae-saimaan-kanavan-materiaalit)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-hairiotilanne
                                +kayttaja-jvh+
                                parametrit)
        materiaali-fn (fn [nimi]
                        (first (filter #(and (= urakka-id (::vv-materiaali/urakka-id %))
                                             (= nimi (::vv-materiaali/nimi %)))
                                       (:materiaalilistaukset vastaus))))
        naula-materiaali (materiaali-fn "Naulat")
        amaprit-materiaali (materiaali-fn "Ämpäreitä")
        _ (println "MATSKUT ENNEN: " (pr-str saimaan-materiaalit-ennen-tallennusta))
        _ (println "NAULAT: " (pr-str naula-materiaali))
        naula-lkm-ennen (apply + (map :maara (:muutokset (some #(when (= "Naulat" (:nimi %)) %)
                                                               saimaan-materiaalit-ennen-tallennusta))))
        naula-lkm-jalkeen (::vv-materiaali/maara-nyt naula-materiaali)
        _ (println naula-lkm-ennen " " naula-lkm-jalkeen)]
    (is (= naula-lkm-ennen (+ naula-lkm-jalkeen naulan-kulutus)))))

(deftest hairiotilanteiden-tallennus-ilman-oikeuksia
  (let [syy (str "hairiotilanteen-tallennus-testi-" (UUID/randomUUID))
        parametrit (tallennuksen-parametrit syy)]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-hairiotilanne
                                           +kayttaja-ulle+
                                           parametrit)))))