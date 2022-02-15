(ns harja.palvelin.palvelut.varuste-ulkoiset-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.varuste-ulkoiset :as varuste-ulkoiset]
            [harja.testi :refer :all]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :varuste-velho (component/using
                                         (varuste-ulkoiset/->VarusteVelho)
                                         [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each
              urakkatieto-fixture
              jarjestelma-fixture)

(def urakka-id-35 35)
(def urakka-id-33 33)

(deftest palvelu-on-olemassa-ja-vaatii-parametrin-urakka-id
  (is (thrown-with-msg? IllegalArgumentException #"urakka-id on pakollinen"
                        (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-urakan-varustetoteuma-ulkoiset
                                        +kayttaja-jvh+
                                        {:urakka-id nil :hoitovuosi 2019}))))

(deftest palvelu-on-olemassa-ja-vaatii-parametrin-hoitovuosi
  (is (thrown-with-msg? IllegalArgumentException #"hoitovuosi on pakollinen"
                        (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-urakan-varustetoteuma-ulkoiset
                                        +kayttaja-jvh+
                                        {:urakka-id urakka-id-35 :hoitovuosi nil}))))

(defn assertoi-saatu-lista
  [odotettu-lista parametrit hae-fn]
  (let [odotettu-keys (keys (first odotettu-lista))
        vertaa (fn [x] ((apply juxt odotettu-keys) x))
        lajittele (fn [l] (sort-by vertaa l))
        odotettu-oid-lista (lajittele odotettu-lista)
        saatu-oid-lista (->> (hae-fn (:db jarjestelma) +kayttaja-jvh+ parametrit)
                             :toteumat
                             (map #(select-keys % odotettu-keys))
                             vec
                             lajittele)]
    (is (= odotettu-oid-lista saatu-oid-lista))))

(defn assertoi-saatu-oid-lista [odotettu-oid-lista parametrit]
  (let [odotettu-lista (map (fn [x] {:ulkoinen-oid x}) odotettu-oid-lista)]
    (assertoi-saatu-lista odotettu-lista parametrit varuste-ulkoiset/hae-urakan-uusimmat-varustetoteuma-ulkoiset)))

(deftest palvelu-palauttaa-oikean-hoitovuoden-tuloksia
  (assertoi-saatu-oid-lista ["1.2.246.578.4.3.12.512.310173999"
                             "1.2.246.578.4.3.12.512.310174000"]
                            {:urakka-id urakka-id-33 :hoitovuosi 2022}))

(deftest tyhjalle-hoitovuodelle-palautuu-tyhja-tulos
  "Ja rinnakkaisien hoitovuosien alku & loppu ovat 1.10. ja 30.9. Ne eivät tule mukaan tulokseen."
  (assertoi-saatu-oid-lista [] {:urakka-id urakka-id-33 :hoitovuosi 2023}))

(deftest hae-urakan-35-uusimmat-varusteet
  (assertoi-saatu-oid-lista ["1.2.246.578.4.3.12.512.310173990"
                             "1.2.246.578.4.3.12.512.310173991"
                             "1.2.246.578.4.3.12.512.310173992"
                             "1.2.246.578.4.3.12.512.310173993"
                             "1.2.246.578.4.3.12.512.310173994"
                             "1.2.246.578.4.3.12.512.310173995"
                             "1.2.246.578.4.3.12.512.310173996"
                             "1.2.246.578.4.3.12.512.310173997"
                             "1.2.246.578.4.3.12.512.310173998"]
                            {:urakka-id urakka-id-35 :hoitovuosi 2019}))

(deftest hae-vain-urakan-erittain-hyvat-varusteet
  (assertoi-saatu-oid-lista ["1.2.246.578.4.3.12.512.310173990"
                             "1.2.246.578.4.3.12.512.310173997"]
                            {:urakka-id urakka-id-35 :hoitovuosi 2019 :kuntoluokka "Erittäin hyvä"}))

(deftest hae-vain-urakan-erittain-hyvat-paivitetyt-varusteet
  (assertoi-saatu-oid-lista ["1.2.246.578.4.3.12.512.310173997"]
                            {:urakka-id urakka-id-35 :hoitovuosi 2019 :kuntoluokka "Erittäin hyvä" :toteuma "paivitetty"}))

(deftest hae-vain-urakan-erittain-hyvat-paivitetyt-varusteet
  (assertoi-saatu-oid-lista ["1.2.246.578.4.3.12.512.310173994"
                             "1.2.246.578.4.3.12.512.310173997"]
                            {:urakka-id urakka-id-35 :hoitovuosi 2019 :toteuma "paivitetty"}))

(deftest palauta-uusin-versio-varusteesta-josta-loytyy-monta-versiota
  (assertoi-saatu-lista [{:ulkoinen-oid "1.2.246.578.4.3.12.512.310173998" :alkupvm #inst "2020-10-24T21:00:00.000-00:00"}]
                        {:urakka-id urakka-id-35 :hoitovuosi 2020} varuste-ulkoiset/hae-urakan-uusimmat-varustetoteuma-ulkoiset))

(deftest kuukausi-rajaus
  (assertoi-saatu-lista [{:id 9} {:id 10} {:id 11}]
                        {:urakka-id urakka-id-35 :ulkoinen-oid "1.2.246.578.4.3.12.512.310173998"}
                        varuste-ulkoiset/hae-varustetoteumat-ulkoiset))

; TR-osoite filterin testit.
; Testissä v ja w ovat varusteita tietokannassa c - k ovat filtereitä, jotka hakevat kannasta osumia.

; Data
;         1       |        2         osa
; --1---2---3---4-|--1---2---3---4-- etaisyys
; ---------v1=====|=====v2---------- varuste v (aosa 1 aeta 3 => losa 2 leta 6
; ---------w12----|----------------- varuste w
; ----------------|-----x12--------- varuste x

(def v [1 3 2 2])
(def w [1 3 1 3])
(def x [2 2 2 2])

; Filtterit
;         1       |        2         osa
; --1---2---3---4-|--1---2---3---4-- etaisyys
; ----------------|---------c1==c2-- c => ei leikkaa
; -d1==d2---------|----------------- d => ei leikkaa
; -----e1======e2-|----------------- e => leikkaa alusta
; ----------------|-f1======f2------ f => leikkaa lopusta
; -----g1=========|=========g2------ g => sisältää kokonaan
; -------------h1=|=h2-------------- h => sisältyy kokonaan
; ---------i12----|----------------- i => sisältyy kokonaan
; -----j12--------|----------------- j => kokonaan ennen
; ----------------|----------k12---- k => kokonaan jälkeen

(def c [2 3 2 4])
(def d [1 1 1 2])
(def e [1 2 1 4])
(def f [2 1 2 3])
(def g [1 2 2 3])
(def h [1 4 2 1])
(def i [1 3 1 3])
(def j [1 2 1 2])
(def k [2 3 2 3])

(def odotettu {c []
               d [v w]
               e [v w]
               f [v x]
               g [v w x]
               h [v]
               i [v w]
               j []
               k []})

