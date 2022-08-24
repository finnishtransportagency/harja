(ns harja.palvelin.integraatiot.velho.yhteiset-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.integraatiot.velho.yhteiset :as yhteiset]
            [harja.pvm :as pvm]
            [clj-time.core :as t]))

(defn fake-token-palvelin [_ _ _]
  "{\"access_token\":\"TEST_TOKEN\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")

(def nyt (pvm/iso-8601->aika "2021-11-29 13:28:20.000000"))
(def nyt+30min+1sek (t/plus (pvm/joda-timeksi nyt) (t/minutes 30) (t/seconds 1)))
(def nyt+29min+59sek (t/plus (pvm/joda-timeksi nyt) (t/minutes 29) (t/seconds 59)))
(def token-url "https:/localhost")
(def odotettu-kayttajatunnus "odotettu kayttajatunnus")
(def salasana "salasanaa asdf")
(def odotettu-token "uusi hieno token")
(defn kielletty-fn [& _] (is false "ei saa kutsua"))

(defn tyhjenna-velho-tokenit-atomi []
  (reset! yhteiset/velho-tokenit nil))

(deftest hae-velho-token-palauttaa-token-uudelle-avaimelle-test
  (tyhjenna-velho-tokenit-atomi)
  (with-redefs [yhteiset/pyyda-velho-token (fn [& _] odotettu-token)
                pvm/nyt (fn [] nyt)]
    (let [saatu-token (yhteiset/hae-velho-token token-url odotettu-kayttajatunnus salasana nil nil)]
      (is (= odotettu-token saatu-token)))))

(deftest hae-velho-token-ei-kutsu-pyyntoa-uudelleen-turhaan-heti-test
  (tyhjenna-velho-tokenit-atomi)
  (with-redefs [yhteiset/pyyda-velho-token (fn [& _] odotettu-token)
                pvm/nyt (fn [] nyt)]
    (let [_ (yhteiset/hae-velho-token token-url odotettu-kayttajatunnus salasana nil nil)
          saatu-token (with-redefs [yhteiset/pyyda-velho-token kielletty-fn]
                        (yhteiset/hae-velho-token token-url odotettu-kayttajatunnus salasana nil nil))]
      (is (= odotettu-token saatu-token)))))

(deftest hae-velho-token-ei-hae-uutta-tokenia-29min-59sek-kuluttua-test
  (tyhjenna-velho-tokenit-atomi)
  (let [_ (with-redefs [yhteiset/pyyda-velho-token (fn [& _] odotettu-token)
                        pvm/nyt (fn [] nyt)]
            (yhteiset/hae-velho-token token-url odotettu-kayttajatunnus salasana nil nil))
        saatu-token (with-redefs [yhteiset/pyyda-velho-token kielletty-fn
                                  pvm/nyt (fn [] nyt+29min+59sek)]
                      (yhteiset/hae-velho-token token-url odotettu-kayttajatunnus salasana nil nil))]
    (is (= odotettu-token saatu-token))))

(deftest hae-velho-token-hakee-uuden-tokenin-30min-1sek-kuluttua-test
  (tyhjenna-velho-tokenit-atomi)
  (let [_ (with-redefs [yhteiset/pyyda-velho-token (fn [& _] odotettu-token)
                        pvm/nyt (fn [] nyt)]
            (yhteiset/hae-velho-token token-url odotettu-kayttajatunnus salasana nil nil))
        odotettu-toinen-token "toinen token"
        pyyda-velho-token-fn (fn [& _] odotettu-toinen-token)
        saatu-token (with-redefs [yhteiset/pyyda-velho-token pyyda-velho-token-fn
                                  pvm/nyt (fn [] nyt+30min+1sek)]
                      (yhteiset/hae-velho-token token-url odotettu-kayttajatunnus salasana nil nil))]
    (is (= odotettu-toinen-token saatu-token))))

(deftest hae-velho-token-kutsuu-pyyntoa-heti-uudelleen-kun-kayttaja-vaihtuu-test
  (tyhjenna-velho-tokenit-atomi)
  (let [toinen-kayttajatunnus "eri kayttaja aassdf"
        odotettu-toinen-token "odotettu uusi toinen token"]
    (with-redefs [yhteiset/pyyda-velho-token (fn [_ kayttajatunnus & _]
                                               (if (= kayttajatunnus odotettu-kayttajatunnus)
                                                 odotettu-token
                                                 odotettu-toinen-token))
                  pvm/nyt (fn [] nyt)]
      (let [saatu-token1 (yhteiset/hae-velho-token token-url odotettu-kayttajatunnus salasana nil nil)
            saatu-token2 (yhteiset/hae-velho-token token-url toinen-kayttajatunnus salasana nil nil)
            saatu-token3 (yhteiset/hae-velho-token token-url odotettu-kayttajatunnus salasana nil nil)]
        (is (= odotettu-token saatu-token1))
        (is (= odotettu-toinen-token saatu-token2))
        (is (= odotettu-token saatu-token3))))))
