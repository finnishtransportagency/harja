(ns harja.palvelin.palvelut.liitteet-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.liitteet :as liitteet]
            [harja.testi :refer :all]
            [clojure.core.match :refer [match]]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :liitteet (component/using
                                    (liitteet/->Liitteet)
                                    [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest poista-liite-linkitys-test
  (let [laatupoikkeaman-liite (fn [laatupoikkeama-id liite-id]
                                (ffirst (q "SELECT COUNT(*) FROM laatupoikkeama_liite WHERE laatupoikkeama = " laatupoikkeama-id
                                           " AND liite = " liite-id)))
        random-laatupoikkeama (first (q-map "SELECT id, urakka FROM laatupoikkeama"))
        _ (u "INSERT INTO liite (tyyppi, nimi, liite_oid, lahde) VALUES ('image/jpeg', 'testi45435.jpg', '123', 'harja-ui')")
        liite-id (:id (first (q-map "SELECT id FROM liite WHERE nimi = 'testi45435.jpg'")))
        _ (u (str "INSERT INTO laatupoikkeama_liite (laatupoikkeama, liite) VALUES (" (:id random-laatupoikkeama) ", " liite-id ")"))
        liitteet-ennen-testia (laatupoikkeaman-liite (:id random-laatupoikkeama) liite-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :poista-liite-linkki +kayttaja-jvh+
                                {:urakka-id (:urakka random-laatupoikkeama)
                                 :domain :laatupoikkeama
                                 :liite-id liite-id
                                 :domain-id (:id random-laatupoikkeama)})
        liitteet-testin-jalkeen (laatupoikkeaman-liite (:id random-laatupoikkeama) liite-id)]

    (is (= liitteet-ennen-testia 1))
    (is (= liitteet-testin-jalkeen 0))))