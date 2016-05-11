(ns harja.palvelin.oikeudet-test
    (:require [clojure.test :refer :all]
              [taoensso.timbre :as log]
              [harja.domain.oikeudet :as oikeudet]
              [harja.testi :refer :all]))

;; Järjestelmävastaava
(def jvh {:roolit #{"Jarjestelmavastaava"}
          :urakkaroolit {}
          :organisaatio {:id 1 :tyyppi "liikennevirasto" :nimi "Liikennevirasto"}})

;; ELYn urakanvalvoja
(def ely-uv {:roolit #{}
             :urakkaroolit {1 #{"ELY_Urakanvalvoja"}}
             :organisaation-urakat #{1 2 3}
             :organisaatio {:id 666 :tyyppi "hallintayksikko" :nimi "Mun ely"}})

(def urakoitsija {:id 123 :tyyppi "urakoitsija" :nimi "Asfaltia"})
;; Urakoitsijan pääkäyttäjä
(def ur-pk {:roolit #{"Paakayttaja"}
            :urakkaroolit {}
            :organisaatio urakoitsija
            :organisaation-urakat #{1 2 3}})

;; Urakoitsijan urakanvastuuhenkilö
(def ur-uvh {:roolit #{}
             :urakkaroolit {1 #{"vastuuhenkilo"}}
             :organisaatio urakoitsija
             :organisaation-urakat #{1 2 3}})

(deftest vaadi-jvh-saa-tehda-mita-vaan
  (is (oikeudet/voi-kirjoittaa? oikeudet/hallinta-lampotilat nil jvh))
  (is (oikeudet/voi-lukea? oikeudet/raportit-laskutusyhteenveto 1 jvh))
  (is (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot 2 jvh)))

(deftest ely-uv-voi-lukea-kaikista-ja-kirjoittaa-omaan
  (is (oikeudet/voi-lukea? oikeudet/urakat-suunnittelu-materiaalit 42 ely-uv))
  (is (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-materiaalit 42 ely-uv)))
  (is (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-materiaalit 1 ely-uv)))

(deftest urakoitsijan-pk-voi-lukea-ja-kirjoittaa-omiaan
  (doseq [u [1 2 3]]
    (is (oikeudet/voi-lukea? oikeudet/urakat-laadunseuranta-tarkastukset u ur-pk))
    (is (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-tarkastukset u ur-pk)))
  (is (not (oikeudet/voi-lukea? oikeudet/urakat-laadunseuranta-tarkastukset 4 ur-pk)))
  (is (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-tarkastukset 4 ur-pk))))

(deftest urakoitsija-uvh-voi-lukea-ja-kirjoittaa-omaa-urakkaa
  (is (oikeudet/voi-lukea? oikeudet/urakat-toteumat-kokonaishintaisettyot 1 ur-uvh))
  (is (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-kokonaishintaisettyot 1 ur-uvh))
  (is (not (oikeudet/voi-lukea? oikeudet/urakat-toteumat-kokonaishintaisettyot 2 ur-uvh)))
  (is (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-kokonaishintaisettyot 2 ur-uvh))))

(deftest vain-jvh-nakee-hallinnan
  (is (oikeudet/voi-lukea? oikeudet/hallinta nil jvh))
  (doseq [k [ely-uv ur-pk ur-uvh]]
    (is (not (oikeudet/voi-lukea? oikeudet/hallinta nil k)))))
