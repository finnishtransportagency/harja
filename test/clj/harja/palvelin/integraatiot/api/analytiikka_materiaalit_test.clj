(ns harja.palvelin.integraatiot.api.analytiikka-materiaalit-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clj-time
             [core :as t]
             [format :as df]]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [harja.testi :refer :all]
            [harja.jms-test :refer [feikki-jms]]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [cheshire.core :as cheshire]
            [harja.palvelin.integraatiot.api.analytiikka :as api-analytiikka]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.text SimpleDateFormat)
           (java.util Date)))

(def kayttaja-yit "yit-rakennus")
(def kayttaja-analytiikka "analytiikka-testeri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja-yit
    :api-analytiikka (component/using
                       (api-analytiikka/->Analytiikka true)
                       [:http-palvelin :db-replica :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(defn- luo-valiaikainen-kayttaja []
  (u (str "INSERT INTO kayttaja (etunimi, sukunimi, kayttajanimi, organisaatio, \"analytiikka-oikeus\") VALUES
          ('etunimi','sukunimi', 'analytiikka-testeri', (SELECT id FROM organisaatio WHERE nimi = 'Liikennevirasto'), true)")))

(deftest hae-materiaalit-yksinkertainen-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/materiaalit")] kayttaja-analytiikka portti)]
    (is (= 200 (:status vastaus)))))

(deftest hae-materiaalit-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        materiaalit-kannasta (q-map (str "SELECT id FROM materiaalikoodi"))
        materiaaliluokat-kannasta (q-map (str "SELECT id FROM materiaaliluokka"))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/materiaalit")] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count materiaalit-kannasta) (count (:materiaalikoodit encoodattu-body))))
    (is (= (count materiaaliluokat-kannasta) (count (:materiaaliluokat encoodattu-body))))))

(deftest hae-tehtavat-yksinkertainen-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/tehtavat")] kayttaja-analytiikka portti)]
    (is (= 200 (:status vastaus)))))

(deftest hae-tehtavat-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        tehtavat-kannasta (q-map
                            (str "SELECT t.id
                                    FROM toimenpidekoodi t
                                         LEFT JOIN toimenpidekoodi emo ON t.emo = emo.id AND t.taso = 4
                                   WHERE (t.poistettu IS FALSE and t.taso in (1,2,3) OR (t.taso = 4 AND emo.poistettu IS FALSE) OR (t.taso = 4 AND emo.poistettu IS TRUE AND t.poistettu IS FALSE))"))
        tehtavaryhmat-kannasta (q-map (str "SELECT id FROM tehtavaryhma"))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/tehtavat")] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count tehtavat-kannasta) (count (:tehtavat encoodattu-body))))
    (is (= (count tehtavaryhmat-kannasta) (count (:tehtavaryhmat encoodattu-body))))))

(deftest hae-urakat-yksinkertainen-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/urakat")] kayttaja-analytiikka portti)]
    (is (= 200 (:status vastaus)))))

(deftest hae-urakat-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        urakat-kannasta (q-map (str "SELECT id FROM urakka"))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/urakat")] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count urakat-kannasta) (count (:urakat encoodattu-body))))))

(deftest hae-organisaatiot-yksinkertainen-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/organisaatiot")] kayttaja-analytiikka portti)]
    (is (= 200 (:status vastaus)))))

(deftest hae-organisaatiot-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        organisaatiot-kannasta (q-map (str "SELECT id FROM organisaatio"))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/organisaatiot")] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count organisaatiot-kannasta) (count (:organisaatiot encoodattu-body))))))


(deftest varmista-toteumien-vaatimat-materiaalit-loytyy
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        ;; Aseta tiukka hakuväli, josta löytyy vain vähän toteumia
        alkuaika "2004-01-01T00:00:00+03"
        loppuaika "2004-12-31T21:00:00+03"
        toteumavastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti)
        toteuma-body (cheshire/decode (:body toteumavastaus) true)
        toteuma (:toteuma (first (:reittitoteumat toteuma-body)))
        toteuma-materiaalit (:materiaalit toteuma)

        materiaalivastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/materiaalit")] kayttaja-analytiikka portti)
        materiaalibody-body (cheshire/decode (:body materiaalivastaus) true)
        materiaalit (:materiaalikoodit materiaalibody-body)
        materiaalitiedot (reduce
                           (fn [tiedot tot-mat]
                             (let [;; Haetaan materiaalia vastaava id
                                   materiaali-id (some (fn [m]
                                                         (when (= (:nimi m) (:materiaali tot-mat))
                                                           (:id m))) materiaalit)]
                               {:id materiaali-id
                                :nimi (:materiaali tot-mat)}))
                           [] toteuma-materiaalit)
        ;; Me tiedetään, että kutsussa palautuu Hiekoitushiekkaa, niin varmistetaan, että sen id on oikein
        odotetut-materiaalitiedot {:id 5, :nimi "Hiekoitushiekka"}]
    (is (= 200 (:status toteumavastaus)))
    (is (= 200 (:status materiaalivastaus)))
    (is (= odotetut-materiaalitiedot materiaalitiedot))))

;;TODO: Lisää vielä suolat
(deftest hae-suunnitellut-materiaalimaarat-alueurakalle-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)

        ;; Ota hoitourakka (alueurakka)
        urakka-id (hae-urakan-id-nimella "Aktiivinen Oulu Testi")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka (:db jarjestelma) {:id urakka-id}))
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        alkupvm (:alkupvm urakan-tiedot)
        loppupvm (pvm/ajan-muokkaus alkupvm true 1 :vuosi)
        loppupvm (pvm/ajan-muokkaus loppupvm false 1 :paiva)

        ;; Poista materiaalit materiaalin_kaytto - taulusta
        _ (u (format "DELETE from materiaalin_kaytto where urakka = %s" urakka-id))

        ;; Poista suolan suunnittelu
        _ (u (str "DELETE from suolasakko WHERE urakka = " urakka-id))

        ;; Luo materiaalin suunnittelua materiaalin_kaytto tauluun
        materiaali_id 17 ; kelirikkomurske
        maara 1
        _ (u (format "INSERT INTO materiaalin_kaytto (alkupvm, loppupvm, maara, materiaali, urakka, sopimus)
                      VALUES ('%s'::DATE, '%s'::DATE, %s, %s, %s, %s)"
               alkupvm loppupvm maara materiaali_id urakka-id sopimus-id))

        ;; Luo suolasuunnitelma suolasakko -tauluun
        talvisuolaraja 500
        _ (u (format "INSERT INTO suolasakko (maara, hoitokauden_alkuvuosi, maksukuukausi, indeksi, urakka, talvisuolaraja) VALUES
        (%s, %s, %s, 'MAKU 2015', %s, %s)" talvisuolaraja (pvm/vuosi alkupvm), 9, urakka-id, talvisuolaraja))

        vastaus (api-tyokalut/get-kutsu [(format "/api/analytiikka/suunnitellut-materiaalit/%s" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        ensimmainen-hoitovuosi (:suunnitellut-materiaalit (first encoodattu-body))]
    (is (= 200 (:status vastaus)))
    (is (not (nil? encoodattu-body)))
    ;; Pitäisi olla suolaa ja Kelirikkomursketta
    (is (= 2 (count ensimmainen-hoitovuosi)))
    ;; Kelirikkomurske
    (is (= materiaali_id (:materiaali_id (first ensimmainen-hoitovuosi))))
    (is (= "Kelirikkomurske" (:materiaali (first ensimmainen-hoitovuosi))))
    (is (= "murske" (:materiaali_tyyppi (first ensimmainen-hoitovuosi))))
    (is (= "t" (:materiaali_yksikko (first ensimmainen-hoitovuosi))))
    (is (= maara (:maara (first ensimmainen-hoitovuosi))))
    ;; Suola
    (is (nil? (:materiaali_id (second ensimmainen-hoitovuosi))))
    (is (nil? (:materiaali (second ensimmainen-hoitovuosi))))
    (is (nil? (:materiaali_tyyppi (second ensimmainen-hoitovuosi))))
    (is (nil? (:materiaali_yksikko (second ensimmainen-hoitovuosi))))
    (is (= "Talvisuola" (:materiaaliluokka (second ensimmainen-hoitovuosi))))
    (is (= "t" (:materiaaliluokka_yksikko (second ensimmainen-hoitovuosi))))
    (is (= "talvisuola" (:materiaaliluokka_tyyppi (second ensimmainen-hoitovuosi))))
    (is (= talvisuolaraja (:maara (second ensimmainen-hoitovuosi))))))

(deftest hae-suunnitellut-materiaalimaarat-MH-urakalle-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)

        ;; Ota hoitourakka (alueurakka)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka (:db jarjestelma) {:id urakka-id}))
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        alkupvm (:alkupvm urakan-tiedot)
        loppupvm (pvm/ajan-muokkaus alkupvm true 1 :vuosi)
        loppupvm (pvm/ajan-muokkaus loppupvm false 1 :paiva)

        ;; Poista suolan suunnittelu - jostain toisesta taulusta


        ;; Poista materiaalit materiaalin_kaytto - taulusta
        _ (u (format "DELETE from materiaalin_kaytto where urakka = %s" urakka-id))
        ;; Poista suunnitellut määrät urakka_tehtavamaara -taulusta
        _ (u (format "DELETE from urakka_tehtavamaara where urakka = %s" urakka-id))

        ;; Poista suolan suunnittelu
        _ (u (str "DELETE from suolasakko WHERE urakka = " urakka-id))

        ;; Luo materiaalin suunnittelua materiaalin_kaytto tauluun
        materiaali_id 17 ; kelirikkomurske
        maara 1
        _ (u (format "INSERT INTO materiaalin_kaytto (alkupvm, loppupvm, maara, materiaali, urakka, sopimus)
                      VALUES ('%s'::DATE, '%s'::DATE, %s, %s, %s, %s)"
               alkupvm loppupvm maara materiaali_id urakka-id sopimus-id))

        ;; Luo materiaalin suunnittelua
        ;; Lisää tehtävälle suunniteltu määrä
        tehtava-nimi "Liukkaudentorjunta hiekoituksella"
        tehtava-id (:id (first (q-map (format "SELECT id FROM toimenpidekoodi WHERE nimi = '%s'" tehtava-nimi))))
        _ (u (format "insert into urakka_tehtavamaara (urakka, \"hoitokauden-alkuvuosi\", tehtava, maara) values
        (%s, %s, %s, %s)" urakka-id (pvm/vuosi alkupvm) tehtava-id maara))

        ;; Luo suolalle suunnitelma
        talvisuolaraja 500
        suolaustehtava "Suolaus"
        tehtava-id (:id (first (q-map (format "SELECT id FROM toimenpidekoodi WHERE nimi = '%s'" suolaustehtava))))
        _ (u (format "insert into urakka_tehtavamaara (urakka, \"hoitokauden-alkuvuosi\", tehtava, maara) values
        (%s, %s, %s, %s)" urakka-id (pvm/vuosi alkupvm) tehtava-id talvisuolaraja))
        ;; Merkataan vielä, että tarjouksen tiedot on tallennettu
        _ (u (format "insert into sopimuksen_tehtavamaarat_tallennettu (urakka, tallennettu) values (%s, TRUE)" urakka-id))


        ;; Hae suunnitellut materiaalit
        vastaus (api-tyokalut/get-kutsu [(format "/api/analytiikka/suunnitellut-materiaalit/%s" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        _ (println "body: " (pr-str encoodattu-body))
        ensimmainen-hoitovuosi (:suunnitellut-materiaalit (first encoodattu-body))
        ;; Järjestetään materiaalit id:n mukaan
        ensimmainen-hoitovuosi (sort-by :materiaali_id ensimmainen-hoitovuosi)]

    (is (= 200 (:status vastaus)))
    (is (not (nil? encoodattu-body)))
    ;(is (= materiaali_id (:materiaali_id ensimmainen-hoitovuosi)))
    (is (nil? (:materiaali (first ensimmainen-hoitovuosi))))
    (is (= "Talvisuola" (:materiaaliluokka (first ensimmainen-hoitovuosi))))
    (is (= "Hiekoitushiekka" (:materiaali (second ensimmainen-hoitovuosi))))
    (is (= "Kelirikkomurske" (:materiaali (nth ensimmainen-hoitovuosi 2))))
    (is (= "hiekoitushiekka" (:materiaali_tyyppi (second ensimmainen-hoitovuosi))))
    (is (= "murske" (:materiaali_tyyppi (nth ensimmainen-hoitovuosi 2))))
    (is (= "t" (:materiaali_yksikko (second ensimmainen-hoitovuosi))))
    (is (= maara (:maara (second ensimmainen-hoitovuosi))))))
