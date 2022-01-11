(ns harja.palvelin.palvelut.varuste-ulkoiset-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.varuste-ulkoiset :as varuste-velho]
            [harja.testi :refer :all]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :varuste-velho (component/using
                                         (varuste-velho/->VarusteVelho)
                                         [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each
              urakkatieto-fixture
              jarjestelma-fixture)

(deftest hae-urakan-35-uusimmat-varusteet
  (let [urakka-id 35
        odotettu-oid-lista (sort ["1.2.246.578.4.3.12.512.310173990"
                                  "1.2.246.578.4.3.12.512.310173991"
                                  "1.2.246.578.4.3.12.512.310173992"
                                  "1.2.246.578.4.3.12.512.310173993"
                                  "1.2.246.578.4.3.12.512.310173994"
                                  "1.2.246.578.4.3.12.512.310173995"
                                  "1.2.246.578.4.3.12.512.310173996"
                                  "1.2.246.578.4.3.12.512.310173997"
                                  "1.2.246.578.4.3.12.512.310173998"])
        saatu-oid-lista (->> (varuste-velho/hae-urakan-varustetoteuma-ulkoiset (:db jarjestelma) +kayttaja-jvh+ {:urakka-id urakka-id})
                             :toteumat
                             (map :ulkoinen-oid)
                             vec
                             sort)]
    (is (= odotettu-oid-lista saatu-oid-lista))))

(deftest palvelu-on-olemassa-ja-vaatii-parametrin-urakka-id
  (let [urakka-id 35
        saatu (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-urakan-varustetoteuma-ulkoiset
                              +kayttaja-jvh+
                              {:urakka-id urakka-id})]
    (is (not-empty (:toteumat saatu)))
    (is (thrown-with-msg? IllegalArgumentException #"urakka-id on pakollinen"
                          (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-urakan-varustetoteuma-ulkoiset
                                          +kayttaja-jvh+
                                          {:urakka-id nil})))))