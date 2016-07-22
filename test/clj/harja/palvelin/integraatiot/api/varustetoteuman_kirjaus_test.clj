(ns harja.palvelin.integraatiot.api.varustetoteuman-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.tyokalut :as tyokalut]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.integraatiot.api.varustetoteuma :as api-varustetoteuma]
            [harja.palvelin.integraatiot.tierekisteri.tietolajit :as tietolajit]
            [clojure.java.io :as io]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]))

(def kayttaja "destia")
(def +testi-tierekisteri-url+ "harja.testi.tierekisteri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :tierekisteri (component/using (tierekisteri/->Tierekisteri +testi-tierekisteri-url+) [:db :integraatioloki])
    :api-varusteoteuma (component/using
                         (api-varustetoteuma/->Varustetoteuma)
                         [:http-palvelin :db :integraatioloki :tierekisteri])))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-varustetoteuma
  (tietolajit/tyhjenna-tietolajien-kuvaukset-cache)
  (let [hae-tietolaji-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))
        lisaa-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
        paivita-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
        poista-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
        varustetoteumat-ennen-pyyntoa (ffirst (q
                                                (str "SELECT count(*)
                                                       FROM varustetoteuma")))
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        payload (-> "test/resurssit/api/varustetoteuma.json"
                    slurp
                    (.replace "__ID__" (str ulkoinen-id)))
        varustetoteuma-api-url ["/api/urakat/" urakka "/toteumat/varuste"]]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/haetietolaji") hae-tietolaji-xml
       (str +testi-tierekisteri-url+ "/lisaatietue") lisaa-tietue-xml
       (str +testi-tierekisteri-url+ "/paivitatietue") paivita-tietue-xml
       (str +testi-tierekisteri-url+ "/poistatietue") poista-tietue-xml
       #"http?://localhost" :allow]
      (let [vastaus-lisays (api-tyokalut/post-kutsu varustetoteuma-api-url kayttaja portti
                                                    payload)]

        (is (= 200 (:status vastaus-lisays)))
        (let [varustetoteumat-pyynnon-jalkeen (ffirst (q
                                                        (str "SELECT count(*)
                                                       FROM varustetoteuma")))
              toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi "
                                              "FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              varuste-arvot-kannassa (ffirst (q (str "SELECT arvot FROM varustetoteuma WHERE toteuma = " toteuma-id)))
              lahetystiedot-kannassa (q (str "SELECT lahetetty_tierekisteriin FROM varustetoteuma WHERE toteuma = " 17))]
          (is (= (+ varustetoteumat-ennen-pyyntoa 4) varustetoteumat-pyynnon-jalkeen))
          (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tehotekijät Oy"]))
          (is (= varuste-arvot-kannassa "9987        2           2         010           11   Testi liikennemerkki                              Omistaja                                                              4          400   "))
          (is (every? #(= [true] %) lahetystiedot-kannassa))
          (is (string? varuste-arvot-kannassa)))))

    ;; Lähetetään sama pyyntö uudelleen. Varustetoteumien määrä ei saa lisääntyä eikä niitä saa lähettää
    ;; uudelleen tierekisteriin. Käytännössä mitään ei saa tapahtua.

    (with-fake-http
      [#"http?://localhost" :allow]
      (let [varustetoteumat-ennen-uutta-pyyntoa (ffirst (q
                                                          (str "SELECT count(*)
                                                                FROM varustetoteuma")))
            vastaus-lisays (api-tyokalut/post-kutsu varustetoteuma-api-url kayttaja portti
                                                    payload)]
        (println (:body vastaus-lisays))
        (is (= 200 (:status vastaus-lisays)))
        (let [varustetoteumat-uuden-pyynnon-jalkeen (ffirst (q
                                                              (str "SELECT count(*)
                                                                    FROM varustetoteuma")))]
          (is (= varustetoteumat-ennen-uutta-pyyntoa
                 varustetoteumat-uuden-pyynnon-jalkeen)))))))

;; TODO Testaa mitä tapahtuu kun missä tahansa vaiheessa tierekisteri antaa virheellisen vastauksen