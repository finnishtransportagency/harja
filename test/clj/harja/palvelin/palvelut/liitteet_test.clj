(ns harja.palvelin.palvelut.liitteet-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.liitteet :as liitteet]
            [harja.palvelin.komponentit.liitteet :as liitteet-komponentti]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component])
  (:import (org.apache.commons.io IOUtils)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :liitteiden-hallinta (component/using
                                               (liitteet-komponentti/->Liitteet nil nil)
                                               [:db])
                        :liitteet (component/using
                                    (liitteet/->Liitteet)
                                    [:http-palvelin :db :liitteiden-hallinta])))))
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

(deftest siltatarkastusliite-toimii
  (let [liitteiden-hallinta (:liitteiden-hallinta jarjestelma)
        silta-id (ffirst (q "SELECT id FROM silta WHERE siltanimi = 'Kajaanintien silta'"))
        siltatarkastus-id (ffirst (q "SELECT id FROM siltatarkastus WHERE silta = " silta-id " AND tarkastaja='Samuel Siltanen'"))
        tiedosto "dev-resources/images/harja-brand-text.png"
        tiedoston-sisalto (IOUtils/toByteArray (io/input-stream tiedosto))
        ;; Liite kuuluu 'aktiivinen oulu testi'-urakalle
        luotu-liite (liitteet-komponentti/luo-liite liitteiden-hallinta nil (hae-aktiivinen-oulu-testi-id) "harja-brand-text.png" "image/png" 3 tiedoston-sisalto nil "harja-ui")
        liite-id (:id luotu-liite)
        ;; Merkitään liite kuuluvaksi siltatarkastukseen
        _ (u (format "INSERT INTO siltatarkastus_kohde_liite (siltatarkastus, kohde, liite) VALUES (%s, 1, %s)", siltatarkastus-id, liite-id))

        haettu-liite (liitteet/lataa-siltatarkastusliite liitteiden-hallinta
                       ;; Haetaan liite käyttäjällä, jolla ei ole oikeutta liitteen urakkaan, mutta on oikeus
                       ;; siltaan, jonka tarkastukseen liite on yhdistetty
                       {:kayttaja +kayttaja-urakan-vastuuhenkilo+
                        :params {"id" (str liite-id)}})]

    (is (= 200 (:status haettu-liite)))
    (is (= {"Content-Type" "image/png"
            "Content-Length" 3} (:headers haettu-liite)))

    (is (thrown-with-msg? Exception #"EiOikeutta"
          (liitteet/lataa-liite liitteiden-hallinta
            ;; Jos liitettä yritetään ladata lataa-liite-rajapinnan kautta, käytetään normaalia oikeustarkastusta.
            ;; Toisen urakan käyttäjällä ei siis saisi olla oikeutta ladata mitään liitteitä sen kautta.
            {:kayttaja +kayttaja-urakan-vastuuhenkilo+
             :params {"id" (str liite-id)}})))

    (is (thrown-with-msg? Exception #"EiOikeutta"
          (liitteet/lataa-siltatarkastusliite liitteiden-hallinta
            ;; Haetaan vielä Seppona, Sepolla ei pitäisi olla mitään oikeuksia.
            {:kayttaja +kayttaja-seppo+
             :params {"id" (str liite-id)}})))))

