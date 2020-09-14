(ns ^:integraatio harja.palvelin.palvelut.status-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.palvelin.komponentit.komponenttien-tila :as komponenttien-tila]
            [harja.palvelin.palvelut.status :as status]
            [harja.kyselyt.jarjestelman-tila :as j-t]
            [harja.palvelin.tyokalut.komponentti-event :as komponentti-event]
            [harja.pvm :as pvm]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (component/using
                              (tietokanta/luo-tietokanta (assoc testitietokanta
                                                                :tarkkailun-timeout-arvot {:paivitystiheys-ms 10000
                                                                                           :kyselyn-timeout-ms 20000}
                                                                :tarkkailun-nimi :db))
                              [:komponentti-event])
                        :db-replica (component/using
                                      (tietokanta/luo-tietokanta (assoc testitietokanta
                                                                        :tarkkailun-timeout-arvot {:paivitystiheys-ms 10000
                                                                                                   :replikoinnin-max-viive-ms 100000}
                                                                        :tarkkailun-nimi :db-replica))
                                      [:komponentti-event])
                        :komponentti-event (komponentti-event/komponentti-event)
                        :komponenttien-tila (component/using
                                              (komponenttien-tila/komponentin-tila)
                                              [:komponentti-event])
                        :klusterin-tapahtumat (component/using
                                                (tapahtumat/luo-tapahtumat)
                                                [:db])

                        :todennus (component/using
                                    (todennus/http-todennus)
                                    [:db :klusterin-tapahtumat])
                        :http-palvelin (component/using
                                         (http/luo-http-palvelin portti true)
                                         [:todennus])
                        :pois-kytketyt-ominaisuudet
                        :status (component/using
                                  (status/luo-status true)
                                  [:http-palvelin :db :pois-kytketyt-ominaisuudet :komponenttien-tila])
                        :sonja (component/using
                                 (sonja/luo-oikea-sonja {:url "tcp://localhost:61617"
                                                         :kayttaja ""
                                                         :salasana ""
                                                         :tyyppi :activemq})
                                 [:db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each jarjestelma-fixture)

(deftest toimiiko-dbn-testaus
  (let [db (:db jarjestelma)
        status-komponentti (:status jarjestelma)
        komponenttien-tila (:komponenttien-tila jarjestelma)]
    (testing "Kaikki hienosti"
      (is (:ok? (status/tietokannan-tila komponenttien-tila)))
      #_(is (true? (get (status/tietokannan-tila komponenttien-tila) :yhteys-master-kantaan-ok?)))
      #_(is (= (get-in @(:status status-komponentti) [:db :status]) 200)))
    #_(testing "Kanta ei ole ok"
      (is (false? (with-redefs [status/dbn-tila-ok? (constantly false)]
                    (get (status/tietokannan-tila! status-komponentti db) :yhteys-master-kantaan-ok?))))
      (is (= (get-in @(:status status-komponentti) [:db :status]) 503)))
    #_(testing "Kanta palautuu"
      (status/tietokannan-tila! status-komponentti db)
      (is (= (get-in @(:status status-komponentti) [:db :status]) 200)))))

#_(deftest toimiiko-replikoinnin-testaus
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

#_(deftest toimiiko-sonjan-testaus
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