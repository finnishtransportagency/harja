(ns tarkkailija.palvelin.palvelut.tapahtuma-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [harja.testi :refer :all]
            [harja.palvelin.tyokalut.jarjestelma-rajapinta-kutsut :as rajapinta]))

(defn tarkkailija-jarjestelma [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (pystyta-harja-tarkkailija)
  (testit)
  (lopeta-harja-tarkkailija))

(use-fixtures :each tarkkailija-jarjestelma)

(s/def ::bar (s/keys :req-un [::a]))

(deftest tapahtuma-julkaisija-test
  (testing "Perus julkaisijaa"
    (let [julkaisija (rajapinta/kutsu-palvelua :tapahtuma-julkaisija :foo "testi-host")]
      (is (true? (julkaisija "testi")) "Testitapahtuman julkaisu pitäisi onnistua")
      (is (ffirst (q "SELECT 1 FROM tapahtumatyyppi WHERE nimi = 'foo'"))
          "Tapahtumatyyppi pitäisi löytyä kannasta")
      (is (= 1 (count (q "SELECT 1 FROM tapahtuman_tiedot tt JOIN tapahtumatyyppi t ON tt.kanava=t.kanava WHERE nimi = 'foo'")))
          "Yksi tapahtuma pitäisi löytyä kannasta")))
  (testing "Wrapattynä data spekkiin"
    (let [julkaisija (rajapinta/kutsu-palvelua :tapahtuma-datan-spec
                                               (rajapinta/kutsu-palvelua :tapahtuma-julkaisija :bar "testi-host")
                                               ::bar)]
      (is (true? (julkaisija {:a 1})) "Testitapahtuman julkaisu pitäisi onnistua")
      (is (thrown? IllegalArgumentException (julkaisija "ei-ok"))))))

(deftest lopeta-tapahtuman-kuuntelu-test
  )
(deftest tapahtuman-tarkkailija!-test
  )
(deftest yhta-aikaa-tapahtuman-tarkkailija!-test
  )
(deftest tapahtuman-kuuntelija!-test
  )
(deftest julkaise-tapahtuma-test
  )
(deftest tarkkaile-tapahtumaa-test
  )
(deftest tarkkaile-tapahtumia-test
  )
