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

(defn- linkitystesti [{:keys [domain domain-subject-where domain-taulu domain-liite-taulu domain-sarake]}]
  (let [domain-taulu (or domain-taulu (name domain))
        domain-liite-taulu (or domain-liite-taulu (str domain-taulu "_liite"))
        domain-sarake (or domain-sarake domain-taulu)
        domain-liite (fn [domain-id liite-id]
                       (ffirst (q "SELECT COUNT(*) FROM " domain-liite-taulu " WHERE " domain-sarake " = " domain-id
                                  " AND liite = " liite-id)))
        random-domain-subject (first (q-map "SELECT id, urakka FROM " domain-taulu
                                            (when domain-subject-where
                                              (str " " domain-subject-where))))
        _ (u "INSERT INTO liite (tyyppi, nimi, liite_oid, lahde) VALUES ('image/jpeg', 'testi45435.jpg', '123', 'harja-ui')")
        liite-id (:id (first (q-map "SELECT id FROM liite WHERE nimi = 'testi45435.jpg'")))
        _ (u (str "INSERT INTO " domain-liite-taulu " (" domain-sarake ", liite) VALUES ("
                  (:id random-domain-subject) ", " liite-id ")"))
        liitteet-ennen-testia (domain-liite (:id random-domain-subject) liite-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :poista-liite-linkki +kayttaja-jvh+
                                {:urakka-id (:urakka random-domain-subject)
                                 :domain domain
                                 :liite-id liite-id
                                 :domain-id (:id random-domain-subject)})
        liitteet-testin-jalkeen (domain-liite (:id random-domain-subject) liite-id)]

    (is (= liitteet-ennen-testia 1))
    (is (= liitteet-testin-jalkeen 0))))

(deftest poista-liite-linkitys-test
  (linkitystesti {:domain :laatupoikkeama})
  (linkitystesti {:domain :turvallisuuspoikkeama})
  (linkitystesti {:domain :tarkastus})
  (linkitystesti {:domain :toteuma
                  :domain-subject-where "WHERE tyyppi = 'lisatyo'"})
  (linkitystesti {:domain :toteuma
                  :domain-subject-where "WHERE tyyppi = 'muutostyo'"})
  (linkitystesti {:domain :toteuma
                  :domain-subject-where "WHERE tyyppi = 'yksikkohintainen'"})
  (linkitystesti {:domain :toteuma
                  :domain-subject-where "WHERE tyyppi = 'kokonaishintainen'"}))