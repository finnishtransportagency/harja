(ns harja.palvelin.komponentit.http-palvelin-test
  (:require [harja.testi :refer :all]
            [harja.oikea-http-palvelin-jarjestelma-fixture :as op]
            [clojure.test :refer :all]
            [harja.palvelin.komponentit.http-palvelin :as palvelin]))

(use-fixtures :each (op/luo-fixture))

(defn- julkaise-ja-testaa [nimi get? palvelu-fn odotettu-status odotettu-body]
  (palvelin/julkaise-palvelu (:http-palvelin jarjestelma) nimi palvelu-fn)
  (let [vastaus (if get? (op/get-kutsu nimi) (op/post-kutsu nimi "{}"))]
    (comment clojure.pprint/pprint vastaus)
    (is (= odotettu-status (:status vastaus)))
    (is (.contains (:body vastaus) odotettu-body))))

(deftest get-palvelu-palauta-ok
  (julkaise-ja-testaa :ok-palvelu-get true (fn [user] "kaikki OK") 200 "kaikki OK"))

(deftest post-palvelu-palauta-ok
  (julkaise-ja-testaa :ok-palvelu-post false (fn [user _] "kaikki OK") 200 "kaikki OK"))

(deftest get-palvelu-feilaa-sisaisesti
  (julkaise-ja-testaa :huono-palvelu-get true (fn [user] (throw (RuntimeException. "Simuloitu huono palvelu")))
                      500 "Simuloitu huono palvelu"))

(deftest post-palvelu-feilaa-sisaisesti
  (julkaise-ja-testaa :huono-palvelu-post false (fn [user _] (throw (RuntimeException. "Simuloitu huono palvelu")))
                      500 "Simuloitu huono palvelu"))

(deftest get-palvelu-feilaa-kun-on-huono-pyynto
  (julkaise-ja-testaa :huono-pyynto-get true (fn [user] (throw (IllegalArgumentException. "Simuloitu huono pyyntö")))
                      400 "Simuloitu huono pyyntö"))

(deftest post-palvelu-feilaa-kun-on-huono-pyynto
  (julkaise-ja-testaa :huono-pyynto-post false (fn [user _] (throw (IllegalArgumentException. "Simuloitu huono pyyntö")))
                      400 "Simuloitu huono pyyntö"))
