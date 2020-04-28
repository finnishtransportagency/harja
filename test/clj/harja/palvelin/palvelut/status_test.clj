(ns harja.palvelin.palvelut.status-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.palvelut.status :as status]
            [harja.kyselyt.jarjestelman-tila :as j-t]
            [harja.pvm :as pvm]))

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    "ei-ole-kayttajalle-tarvestsa"
    :status (component/using
              (status/luo-status true)
              ;; Ei varsinaisesti tarvitse sonjaa, mutta vaaditaan se tässä, jotta
              ;; voidaan varmistua siitä, että sonja komponentti on lähtenyt hyrräämään
              ;; ennen kuin sen statusta aletaan seuraamaan
              [:http-palvelin :db :pois-kytketyt-ominaisuudet :db-replica :sonja])
    :sonja (component/using
             (sonja/luo-oikea-sonja {:url "tcp://localhost:61617"
                                     :kayttaja ""
                                     :salasana ""
                                     :tyyppi :activemq})
             [:db])))

(use-fixtures :each jarjestelma-fixture)

(deftest toimiiko-dbn-testaus
  (let [db (:db jarjestelma)
        status-komponentti (:status jarjestelma)]
    (testing "Kaikki hienosti"
      (is (true? (get (status/tietokannan-tila! status-komponentti db) :yhteys-master-kantaan-ok?)))
      (is (= (get-in @(:status status-komponentti) [:db :status]) 200)))
    (testing "Kanta ei ole ok"
      (is (false? (with-redefs [status/dbn-tila-ok? (constantly false)]
                    (get (status/tietokannan-tila! status-komponentti db) :yhteys-master-kantaan-ok?))))
      (is (= (get-in @(:status status-komponentti) [:db :status]) 503)))
    (testing "Kanta palautuu"
      (status/tietokannan-tila! status-komponentti db)
      (is (= (get-in @(:status status-komponentti) [:db :status]) 200)))))

(deftest toimiiko-replikoinnin-testaus
  (let [db-replica (:db-replica jarjestelma)
        status-komponentti (:status jarjestelma)]
    (testing "Kaikki hienosti"
      (is (true? (get (status/replikoinnin-tila! status-komponentti db-replica) :replikoinnin-tila-ok?)))
      (is (= (get-in @(:status status-komponentti) [:db-replica :status]) 200)))
    (testing "Replikointi ei ole ok"
      (is (false? (with-redefs [status/replikoinnin-tila-ok? (constantly false)]
                    (get (status/replikoinnin-tila! status-komponentti db-replica) :replikoinnin-tila-ok?))))
      (is (= (get-in @(:status status-komponentti) [:db-replica :status]) 503)))
    (testing "Replikointi palautuu"
      (status/replikoinnin-tila! status-komponentti db-replica)
      (is (= (get-in @(:status status-komponentti) [:db-replica :status]) 200)))))

(deftest toimiiko-sonjan-testaus
  (let [db (:db jarjestelma)
        status-komponentti (:status jarjestelma)
        ok-palautus-kannasta (constantly [{:tila (doto (org.postgresql.util.PGobject.)
                                                   (.setType "json")
                                                   (.setValue "{\"olioiden-tilat\": {\"istunnot\": [{\"jonot\": [{\"jono1\": {\"tuottaja\": {\"virheet\": null, \"tuottajan-tila\": \"ACTIVE\"}, \"vastaanottaja\": null}}],
                                                                                     \"jarjestelma\": \"istunto-jono1\",
                                                                                     \"istunnon-tila\": \"ACTIVE\"},
                                                                                    {\"jonot\": [{\"jono2\": {\"tuottaja\": null, \"vastaanottaja\": {\"virheet\": null, \"vastaanottajan-tila\": \"ACTIVE\"}}}],
                                                                                     \"jarjestelma\": \"istunto-jono2\",
                                                                                     \"istunnon-tila\": \"ACTIVE\"}],
                                                                    \"yhteyden-tila\": \"ACTIVE\"}}"))
                                           :paivitetty (pvm/nyt)}])]
    (with-redefs [j-t/sonjan-tila ok-palautus-kannasta]
      (testing "Kaikki hienosti"
        (is (true? (get (status/sonja-yhteyden-tila! status-komponentti db true) :sonja-yhteys-ok?)))
        (is (= (get-in @(:status status-komponentti) [:sonja :status]) 200)))
      (testing "Replikointi ei ole ok"
        (is (false? (with-redefs [status/sonja-yhteyden-tila-ok? (constantly false)]
                      (get (status/sonja-yhteyden-tila! status-komponentti db true) :sonja-yhteys-ok?))))
        (is (= (get-in @(:status status-komponentti) [:sonja :status]) 503)))
      (testing "Replikointi palautuu"
        (status/sonja-yhteyden-tila! status-komponentti db true)
        (is (= (get-in @(:status status-komponentti) [:sonja :status]) 200))))))