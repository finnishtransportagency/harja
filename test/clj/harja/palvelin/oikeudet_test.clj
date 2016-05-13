(ns harja.palvelin.oikeudet-test
    (:require [clojure.test :refer :all]
              [taoensso.timbre :as log]
              [slingshot.slingshot :refer [try+ throw+]]
              [harja.domain.oikeudet :as oikeudet]
              [harja.testi :refer :all]))

;; Järjestelmävastaava
(def jvh {:roolit #{"Jarjestelmavastaava"}
          :urakkaroolit {}
          :organisaatioroolit {}
          :organisaatio {:id 1 :tyyppi "liikennevirasto" :nimi "Liikennevirasto"}
          :organisaation-urakat #{}})

(def ely {:id 666 :tyyppi "hallintayksikko" :nimi "Mun ely"})
(def ely-urakat #{1 2 3})

;; ELYn urakanvalvoja
(def ely-uv {:roolit #{}
             :urakkaroolit {1 #{"ELY_Urakanvalvoja"}}
             :organisaatioroolit {}
             :organisaation-urakat ely-urakat
             :organisaatio ely})

(def ely-kayttaja {:roolit #{"ELY_Kayttaja"}
                   :urakkaroolit {}
                   :organisaatioroolit {}
                   :organisaation-urakat ely-urakat
                   :organisaatio ely})

(def urakoitsija {:id 123 :tyyppi "urakoitsija" :nimi "Asfaltia"})
(def urakoitsijan-urakat #{1 2 3})
;; Urakoitsijan pääkäyttäjä
(def ur-pk {:roolit #{"Paakayttaja"}
            :urakkaroolit {}
            :organisaatioroolit {}
            :organisaatio urakoitsija
            :organisaation-urakat urakoitsijan-urakat})

;; Urakoitsijan urakanvastuuhenkilö
(def ur-uvh {:roolit #{}
             :urakkaroolit {1 #{"vastuuhenkilo"}}
             :organisaatioroolit {}
             :organisaatio urakoitsija
             :organisaation-urakat urakoitsijan-urakat})

;; Tilaajan käyttäjä
(def tilaajan-kayttaja {:roolit #{"Tilaajan_Kayttaja"}
                        :urakkaroolit {}
                        :organisaatio {:id 1 :tyyppi "liikennevirasto" :nimi "Liikennevirasto"}
                        :organisaation-urakat #{}})

;; Urakoitsijan laatupäällikkö
(def ur-laatu {:roolit #{}
               :urakkaroolit {}
               :organisaatioroolit {123 #{"Laatupaallikko"}}
               :organisaatio urakoitsija
               :organisaation-urakat urakoitsijan-urakat})

;; Urakoitsijan Urakan laatuvastaava
(def ur-urakan-lv {:roolit #{}
                   :urakkaroolit {1 #{"Laadunvalvoja"}}
                   :organisaatioroolit {}
                   :organisaatio urakoitsija
                   :organisaation-urakat urakoitsijan-urakat})

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

(deftest tilaajan-kayttajan-lukuoikeuksia
  (is (try+
        (oikeudet/lue oikeudet/urakat tilaajan-kayttaja 1)
        true
        (catch harja.domain.roolit.EiOikeutta e
          false)))
  (is (oikeudet/voi-lukea? oikeudet/urakat nil tilaajan-kayttaja))
  (is (oikeudet/voi-lukea? oikeudet/urakat 1 tilaajan-kayttaja))
  (is (oikeudet/voi-lukea? oikeudet/urakat-yleiset nil tilaajan-kayttaja))
  (is (oikeudet/voi-lukea? oikeudet/urakat-yleiset 1 tilaajan-kayttaja))
  (is (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset 2 tilaajan-kayttaja)))
  (is (oikeudet/voi-lukea? oikeudet/urakat-suunnittelu-materiaalit 42 tilaajan-kayttaja)))


(deftest yrityksen-laatupaallikko-nakee-oman-org
  ;; Saa lukea oman organisaation urakoimien urakoiden yleiset tiedot
  (doseq [u urakoitsijan-urakat]
    (is (oikeudet/voi-lukea? oikeudet/urakat-yleiset u ur-laatu)))

  ;; Ei saa lukea muita urakoita
  (is (not (oikeudet/voi-lukea? oikeudet/urakat-yleiset 666 ur-laatu))))

(deftest ely-kayttaja-voi-vain-lukea
  (is (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-materiaalit 1 ely-kayttaja)))
  (is (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-tarkastukset 1 ely-kayttaja)))

  ;; Voi lukea oman elyn urakoita...
  (doseq [u ely-urakat]
    (is (oikeudet/voi-lukea? oikeudet/urakat-suunnittelu-suola u ely-kayttaja)))

  ;; ...sekä muitakin urakoita
  (is (oikeudet/voi-lukea? oikeudet/urakat-suunnittelu-suola 666 ely-kayttaja)))

(deftest urakoitsijan-urakan-lv-nakee-oman-urakkansa
  (is (oikeudet/voi-lukea? oikeudet/urakat 1 ur-urakan-lv)))
