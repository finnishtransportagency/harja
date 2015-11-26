(ns harja.palvelin.integraatiot.api.paivystajat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.paivystajatiedot :as api-paivystajatiedot]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [taoensso.timbre :as log]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]
            [harja.palvelin.integraatiot.api.reittitoteuma :as api-reittitoteuma]
            [cheshire.core :as cheshire])
  (:import (java.util Date)
           (java.text SimpleDateFormat)))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea kayttaja
                                           :api-paivystajatiedot
                                           (component/using
                                            (api-paivystajatiedot/->Paivystajatiedot)
                                            [:http-palvelin :db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(defn hae-vapaa-yhteyshenkilo-ulkoinen-id []
  (let [id (rand-int 10000)
        vastaus (q (str "SELECT * FROM yhteyshenkilo WHERE ulkoinen_id = '" id "';"))]
    (if (empty? vastaus) id (recur))))

(deftest tallenna-paivystajatiedot
  (let [ulkoinen-id (hae-vapaa-yhteyshenkilo-ulkoinen-id)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/paivystajatiedot"] kayttaja portti
                                               (-> "test/resurssit/api/kirjaa_paivystajatiedot.json"
                                                   slurp
                                                   (.replace "__ID__" (str ulkoinen-id))
                                                   (.replace "__ETUNIMI__" "Päivi")
                                                   (.replace "__SUKUNIMI__" "Päivystäjä")
                                                   (.replace "__EMAIL__" "paivi.paivystaja@sahkoposti.com")
                                                   (.replace "__MATKAPUHELIN__" "04001234567")
                                                   (.replace "__TYOPUHELIN__" "04005555555")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [paivystaja-id (ffirst (q (str "SELECT id FROM yhteyshenkilo WHERE ulkoinen_id = '" (str ulkoinen-id)"';")))
          paivystaja (first (q (str "SELECT ulkoinen_id, etunimi, sukunimi, sahkoposti, matkapuhelin, tyopuhelin FROM yhteyshenkilo WHERE ulkoinen_id = '" (str ulkoinen-id) "';")))
          paivystys (first (q (str "SELECT yhteyshenkilo, vastuuhenkilo, varahenkilo FROM paivystys WHERE yhteyshenkilo = " paivystaja-id)))]
      (is (= paivystaja [(str ulkoinen-id) "Päivi" "Päivystäjä" "paivi.paivystaja@sahkoposti.com" "04001234567" "04005555555"]))
      (is (= paivystys [paivystaja-id true false]))

      (let [vastaus-paivitys (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/paivystajatiedot"] kayttaja portti
                                                    (-> "test/resurssit/api/kirjaa_paivystajatiedot.json"
                                                        slurp
                                                        (.replace "__ID__" (str ulkoinen-id))
                                                        (.replace "__ETUNIMI__" "Taneli")
                                                        (.replace "__SUKUNIMI__" "Tähystäjä")
                                                        (.replace "__EMAIL__" "taneli.tahystaja@gmail.com")
                                                        (.replace "__MATKAPUHELIN__" "05001234567")))]
        (is (= 200 (:status vastaus-paivitys)))
        (let [paivystaja (first (q (str "SELECT ulkoinen_id, etunimi, sukunimi, sahkoposti, matkapuhelin FROM yhteyshenkilo WHERE ulkoinen_id = '" (str ulkoinen-id) "';")))
              paivystys (first (q (str "SELECT yhteyshenkilo FROM paivystys WHERE yhteyshenkilo = " paivystaja-id)))]
          (is (= paivystaja [(str ulkoinen-id) "Taneli" "Tähystäjä" "taneli.tahystaja@gmail.com" "05001234567"]))
          (is (= paivystys [paivystaja-id]))

        (u (str "DELETE FROM yhteyshenkilo WHERE ulkoinen_id = '" (str ulkoinen-id) "';"))
        (u (str "DELETE FROM paivystys WHERE yhteyshenkilo = " paivystaja-id)))))))

(deftest hae-paivystajatiedot-urakan-idlla
  (let [urakka-id @oulun-alueurakan-2014-2019-id
        vastaus (api-tyokalut/get-kutsu ["/api/urakat/" urakka-id "/paivystajatiedot"] kayttaja portti)]
  (is (= 200 (:status vastaus)))))

(deftest hae-paivystajatiedot-puhelinnumerolla
  (let [vastaus (api-tyokalut/post-kutsu ["/api/paivystajatiedot/haku/puhelinnumerolla"] kayttaja portti
                                         (slurp "test/resurssit/api/hae_paivystajatiedot_puhelinnumerolla.json"))
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (log/debug (:body vastaus))
    (is (= (count (:urakat encoodattu-body)) 1))))

; FIXME Etsii sijainnilla aktiivisista urakoista. Mitä tapahtuu kun Oulun alueurakka 2014-2019 päättyy?
(deftest hae-paivystajatiedot-sijainnilla
  (let [vastaus (api-tyokalut/post-kutsu ["/api/paivystajatiedot/haku/sijainnilla"] kayttaja portti
                                         (slurp "test/resurssit/api/hae_paivystajatiedot_sijainnilla.json"))
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (not (empty? (:urakat encoodattu-body))))))