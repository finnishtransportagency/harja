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

(deftest tarkkaile-tapahtumaa-test
  (testing "perus kuuntelija"
    (let [inc-n 3
          testin-tila (atom nil)
          tarkkailija-future (rajapinta/kutsu-palvelua :tarkkaile-tapahtumaa
                                                       :foo
                                                       {:tyyppi :perus}
                                                       (fn [{tapahtuman-data :payload} inc-n]
                                                         (reset! testin-tila (+ tapahtuman-data inc-n)))
                                                       inc-n)
          ;; Odotetaan, että tarkkailu on aloitettu
          kuuntelija @tarkkailija-future]
      (try (reset! testin-tila 0)
           (rajapinta/kutsu-palvelua :julkaise-tapahtuma :foo 1 "testi-host")
           (odota-ehdon-tayttymista #(not= 0 @testin-tila) ":foo tapahtuman tarkkailija triggeröity" 2000)
           (is (= @testin-tila (+ inc-n 1)) "Tapahtuman funkkari toimii")
           (finally
             (rajapinta/kutsu-palvelua :lopeta-tapahtuman-kuuntelu kuuntelija)))))
  (testing "viimeisin kuuntelija ja timeout"
    (rajapinta/kutsu-palvelua :julkaise-tapahtuma :foo :tapahtuma-julkaistu "testi-host")
    (let [testin-tila (atom nil)
          tarkkailija-future (rajapinta/kutsu-palvelua :tarkkaile-tapahtumaa
                                                       :foo
                                                       {:tyyppi :viimeisin
                                                        :timeout 1000}
                                                       (fn [{tapahtuman-data :payload} timeout?]
                                                         (if timeout?
                                                           (reset! testin-tila :timeout)
                                                           (reset! testin-tila tapahtuman-data))))
          ;; Odotetaan, että tarkkailu on aloitettu
          kuuntelija @tarkkailija-future]
      (try (odota-ehdon-tayttymista #(not (nil? @testin-tila)) ":foo tapahtuman tarkkailija triggeröity viimesimmästä tapahtumasta" 2000)
           (is (= @testin-tila :tapahtuma-julkaistu) "Viimeisimmän tapahtuman funkkari toimii")
           (odota-ehdon-tayttymista #(not= :tapahtuma-julkaistu @testin-tila) ":foo tapahtuman tarkkailija triggeröity timeoutista" 2000)
           (is (= @testin-tila :timeout) "Timeout funkkari toimii")
           (finally
             (rajapinta/kutsu-palvelua :lopeta-tapahtuman-kuuntelu kuuntelija))))))

(deftest tarkkaile-tapahtumia-test
  )
(deftest yhta-aikaa-tapahtuman-tarkkailija!-test
  )
(deftest tapahtuman-kuuntelija!-test
  )
(deftest lopeta-tapahtuman-kuuntelu-test
  )
(deftest tapahtuman-tarkkailija!-test
  )
