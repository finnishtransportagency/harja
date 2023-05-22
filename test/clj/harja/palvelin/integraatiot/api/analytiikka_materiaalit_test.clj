(ns harja.palvelin.integraatiot.api.analytiikka-materiaalit-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is use-fixtures]]
    [cheshire.core :as cheshire]
    [com.stuartsierra.component :as component]
    [harja.pvm :as pvm]
    [harja.testi :refer :all]
    [harja.kyselyt.urakat :as urakat-kyselyt]
    [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
    [harja.palvelin.integraatiot.api.analytiikka :as api-analytiikka]))

(def kayttaja-yit "yit-rakennus")
(def kayttaja-analytiikka "analytiikka-testeri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja-yit
    :api-analytiikka (component/using
                       (api-analytiikka/->Analytiikka false)
                       [:http-palvelin :db-replica :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(deftest hae-materiaalit-yksinkertainen-onnistuu-test
  (let [vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/materiaalit")] kayttaja-analytiikka portti)]
    (is (= 200 (:status vastaus)))))

(deftest hae-materiaalit-onnistuu-test
  (let [materiaalit-kannasta (q-map (str "SELECT id FROM materiaalikoodi"))
        materiaaliluokat-kannasta (q-map (str "SELECT id FROM materiaaliluokka"))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/materiaalit")] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count materiaalit-kannasta) (count (:materiaalikoodit encoodattu-body))))
    (is (= (count materiaaliluokat-kannasta) (count (:materiaaliluokat encoodattu-body))))))

(deftest hae-tehtavat-yksinkertainen-onnistuu-test
  (let [vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/tehtavat")] kayttaja-analytiikka portti)]
    (is (= 200 (:status vastaus)))))

(deftest hae-tehtavat-onnistuu-test
  (let [tehtavat-kannasta (q-map
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
  (let [vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/urakat")] kayttaja-analytiikka portti)]
    (is (= 200 (:status vastaus)))))

(deftest hae-urakat-onnistuu-test
  (let [urakat-kannasta (q-map (str "SELECT id FROM urakka"))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/urakat")] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count urakat-kannasta) (count (:urakat encoodattu-body))))))

(deftest hae-organisaatiot-yksinkertainen-onnistuu-test
  (let [vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/organisaatiot")] kayttaja-analytiikka portti)]
    (is (= 200 (:status vastaus)))))

(deftest hae-organisaatiot-onnistuu-test
  (let [organisaatiot-kannasta (q-map (str "SELECT id FROM organisaatio"))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/organisaatiot")] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count organisaatiot-kannasta) (count (:organisaatiot encoodattu-body))))))


(deftest varmista-toteumien-vaatimat-materiaalit-loytyy
  (let [;; Aseta tiukka hakuväli, josta löytyy vain vähän toteumia
        alkuaika "2004-10-19T00:00:00+03"
        loppuaika "2004-10-20T00:00:00+03"
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

(deftest hae-suunnitellut-materiaalimaarat-alueurakalle-onnistuu-test
  (let [;; Ota hoitourakka (alueurakka)
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

(deftest hae-suunnitellut-materiaalimaarat-alueurakalle-epaonnistuu-test
  (let [vastaus-urakka-id-puuttuu (try
                                    (api-analytiikka/palauta-urakan-suunnitellut-materiaalimaarat (:db jarjestelma)
                                      {:ei "mitään"} kayttaja-analytiikka)
                                    (catch Exception e
                                      e))
        vastaus-alkuvuosi-vaarin (try
                                   (api-analytiikka/palauta-urakan-suunnitellut-materiaalimaarat (:db jarjestelma)
                                     {:urakka-id 1
                                      :alkuvuosi "mitään"} kayttaja-analytiikka)
                                   (catch Exception e
                                     e))
        vastaus-urakka-vaarin (api-tyokalut/get-kutsu [(format "/api/analytiikka/suunnitellut-materiaalit/%s" "mitään")] kayttaja-analytiikka portti)]

    (is (str/includes? vastaus-urakka-id-puuttuu "Urakka-id puuttuu"))
    (is (str/includes? vastaus-alkuvuosi-vaarin "Anna muodossa: 2015 ja varmista, että se on pienempi"))
    (is (str/includes? vastaus-urakka-vaarin "Urakka-id väärässä muodossa"))))


(deftest hae-suunnitellut-materiaalimaarat-MH-urakalle-onnistuu-test
  (let [;; Ota hoitourakka (alueurakka)
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

(deftest hae-suunnitellut-materiaalimaarat-kaikille-urakoille-epaonnistuu-vaara-osoite
  (let [alkuvuosi 2021
        ;; Hae suunnitellut materiaalit
        vastaus (try
                  (api-tyokalut/get-kutsu [(format "/api/analytiikka/suunnitellut-materiaalit/%s/" alkuvuosi)] kayttaja-analytiikka portti)
                  (catch Exception e
                    e))]

    (is (= 403 (:status vastaus)))
    (is (str/includes? vastaus "Todennusvirhe"))))

(deftest hae-suunnitellut-materiaalimaarat-kaikille-urakoille-epaonnistuu-vaarat-vuodet
  (let [alkuvuosi 2030
        loppuvuosi 2022
        ;; Hae suunnitellut materiaalit
        vastaus (api-tyokalut/get-kutsu [(format "/api/analytiikka/suunnitellut-materiaalit/%s/%s" alkuvuosi loppuvuosi)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]

    (is (= 400 (:status vastaus)))
    (is (not (nil? encoodattu-body)))))

(deftest hae-suunnitellut-materiaalimaarat-kaikille-voimassaoleville-urakoille-onnistuu
  (let [alkuvuosi 2021
        loppuvuosi 2022
        ;; Hae suunnitellut materiaalit
        vastaus (api-tyokalut/get-kutsu [(format "/api/analytiikka/suunnitellut-materiaalit/%s/%s" alkuvuosi loppuvuosi)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        ;; Hae hoito ja teiden-hoito tyyppiset urakat, jotka on annettuina vuosina voimassa, jotta voidaan verrata, että montako urakkaa on-
        urakat-sql (q-map (format "SELECT id FROM urakka
                                 WHERE tyyppi in ('hoito', 'teiden-hoito')
                                   AND (alkupvm, loppupvm) OVERLAPS (concat('%s','-10-01')::DATE, concat('%s','-10-01')::DATE);" alkuvuosi loppuvuosi))]

    (is (= 200 (:status vastaus)))
    (is (not (nil? encoodattu-body)))
    ;; Pitäisi olla useamman urakan tiedot
    (is (= (count urakat-sql) (count encoodattu-body)))))

(deftest hae-suunnitellut-materiaalimaarat-kaikille-voimassaoleville-urakoille-palauttaa-voimassaolovuosien-mukaiset-materiaalit
  (let [alkuvuosi 2000
        loppuvuosi 2040
        ;; Hae suunnitellut materiaalit
        vastaus (api-tyokalut/get-kutsu [(format "/api/analytiikka/suunnitellut-materiaalit/%s/%s" alkuvuosi loppuvuosi)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]

    (is (= 200 (:status vastaus)))
    (is (not (nil? encoodattu-body)))
    ;; Varmista, että yhdeltäkään urakalta ei löydy 40 riviä vuosittaisia materiaaleja
    (is (every? (fn [urakka] (if (> 12 (count (:vuosittaiset-suunnitelmat urakka))) true false)) encoodattu-body))))

(deftest hae-suunnitellut-tehtavamaarat-alueurakalle-onnistuu
  (let [;; Ota hoitourakka (alueurakka)
        urakka-id (hae-urakan-id-nimella "Aktiivinen Oulu Testi")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka (:db jarjestelma) {:id urakka-id}))
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        alkupvm (:alkupvm urakan-tiedot)
        loppupvm (pvm/ajan-muokkaus alkupvm true 1 :vuosi)
        loppupvm (pvm/ajan-muokkaus loppupvm false 1 :paiva)

        ;; Poista suunnitellut tehtävät
        _ (u (format "DELETE from yksikkohintainen_tyo where urakka = %s" urakka-id))

        ;; Luo tehtavan suunnittelua yksikkohintainen_tyo tauluun
        soratie-tehtava "Sorateiden kaventaminen"
        sorateiden-kaventaminen-tehtava-id (:id (first (q-map (format "SELECT id FROM toimenpidekoodi WHERE nimi = '%s';" soratie-tehtava))))
        sorateiden-maara 800
        _ (u (format "INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus, luotu, muokattu)
                      VALUES ('%s'::DATE, '%s'::DATE, %s, 'tonni', '8', %s, %s, %s, NOW(), NOW())"
               alkupvm loppupvm sorateiden-maara sorateiden-kaventaminen-tehtava-id urakka-id sopimus-id))

        ;; Luo tehtavan suunnittelua yksikkohintainen_tyo tauluun
        kelirikko-tehtava "Kelirikon hoito ja routaheitt. tas. mursk."
        kelirikon-hoito-tehtava-id (:id (first (q-map (format "SELECT id FROM toimenpidekoodi WHERE nimi = '%s';" kelirikko-tehtava))))
        kelirikon-maara 234
        _ (u (format "INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus, luotu, muokattu)
                      VALUES ('%s'::DATE, '%s'::DATE, %s, 'tonni', '8', %s, %s, %s, NOW(), NOW())"
               alkupvm loppupvm kelirikon-maara kelirikon-hoito-tehtava-id urakka-id sopimus-id))

        ;; Haetaan apista tulokset
        vastaus (api-tyokalut/get-kutsu [(format "/api/analytiikka/suunnitellut-tehtavat/%s" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        ekan-vuoden-suunnitelmat (sort-by :maara (:suunnitellut-tehtavat (first encoodattu-body)))]

    (is (= 200 (:status vastaus)))
    (is (not (nil? encoodattu-body)))
    ;; Kelirikko on ensin
    (is (= kelirikon-maara (:maara (first ekan-vuoden-suunnitelmat))))
    (is (= kelirikko-tehtava (:tehtava (first ekan-vuoden-suunnitelmat))))
    ;; Soratie toisena
    (is (= sorateiden-maara (:maara (second ekan-vuoden-suunnitelmat))))
    (is (= soratie-tehtava (:tehtava (second ekan-vuoden-suunnitelmat))))))

(deftest hae-suunnitellut-tehtavamaarat-mhurakalle-onnistuu
  (let [;; Ota hoitourakka (alueurakka)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka (:db jarjestelma) {:id urakka-id}))
        alkupvm (:alkupvm urakan-tiedot)

        ;; Poista suunnitellut määrät urakka_tehtavamaara -taulusta
        _ (u (format "DELETE from urakka_tehtavamaara where urakka = %s" urakka-id))

        ;; Lisää suunniteltu tehtävä
        pysakkimaara 12
        pysakki-tehtava "Pysäkkikatosten puhdistus"
        pysakkitehtava-id (:id (first (q-map (format "SELECT id FROM toimenpidekoodi WHERE nimi = '%s'" pysakki-tehtava))))
        _ (u (format "insert into urakka_tehtavamaara (urakka, \"hoitokauden-alkuvuosi\", tehtava, maara) values
        (%s, %s, %s, %s)" urakka-id (pvm/vuosi alkupvm) pysakkitehtava-id pysakkimaara))

        ;; Lisää suunniteltu tehtävä
        ajoratamaara 80000
        ajorata1-tehtava "Ise 2-ajorat."
        ajkoratatehtava-id (:id (first (q-map (format "SELECT id FROM toimenpidekoodi WHERE nimi = '%s'" ajorata1-tehtava))))
        _ (u (format "insert into urakka_tehtavamaara (urakka, \"hoitokauden-alkuvuosi\", tehtava, maara) values
        (%s, %s, %s, %s)" urakka-id (pvm/vuosi alkupvm) ajkoratatehtava-id ajoratamaara))

        ;; Merkataan vielä, että tarjouksen tiedot on tallennettu
        _ (u (format "insert into sopimuksen_tehtavamaarat_tallennettu (urakka, tallennettu) values (%s, TRUE)" urakka-id))

        ;; Haetaan apista tulokset
        vastaus (api-tyokalut/get-kutsu [(format "/api/analytiikka/suunnitellut-tehtavat/%s" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        ekan-vuoden-suunnitelmat (sort-by :maara (:suunnitellut-tehtavat (first encoodattu-body)))]

    (is (= 200 (:status vastaus)))
    (is (not (nil? encoodattu-body)))
    ;; Pysäkit ensin
    (is (= pysakkimaara (:maara (first ekan-vuoden-suunnitelmat))))
    (is (= pysakki-tehtava (:tehtava (first ekan-vuoden-suunnitelmat))))
    ;; Ajorata toisena
    (is (= ajoratamaara (:maara (second ekan-vuoden-suunnitelmat))))
    (is (= ajorata1-tehtava (:tehtava (second ekan-vuoden-suunnitelmat))))))

(deftest hae-suunnitellut-tehtavamaarat-ajanjaksolle-onnistuu
  (let [;; Ota hoitourakka (alueurakka)
        oulu-urakka-id (hae-urakan-id-nimella "Aktiivinen Oulu Testi")
        oulu-urakan-tiedot (first (urakat-kyselyt/hae-urakka (:db jarjestelma) {:id oulu-urakka-id}))
        oulu-sopimus-id (hae-annetun-urakan-paasopimuksen-id oulu-urakka-id)
        oulu-alkupvm (:alkupvm oulu-urakan-tiedot)
        oulu-loppupvm (pvm/ajan-muokkaus oulu-alkupvm true 1 :vuosi)
        oulu-loppupvm (pvm/ajan-muokkaus oulu-loppupvm false 1 :paiva)
        ;; Ota MHurakka
        ii-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ii-urakan-tiedot (first (urakat-kyselyt/hae-urakka (:db jarjestelma) {:id ii-urakka-id}))
        ii-alkupvm (:alkupvm ii-urakan-tiedot)
        ;; Tämä saattaa jossain vaiheessa hajota, kun Aktiivinen oulu testin päivämääriä voidaan muokata, mutta Iin urakan päiviä ei.
        ;; Niinpä vuodet tulevat menemään joskus ristiin. Tähän ratkaisuna on silloin vaihtaa Iin urakan päivät tuoreenpiin
        ;; tai vaihtaa tähän Iin urakan tilalle, joku toinen urakka.
        haku-alkaa (pvm/vuosi oulu-alkupvm)
        haku-paattyy (inc haku-alkaa)

        ;; Poista suunnitellut tehtävät
        _ (u (format "DELETE from yksikkohintainen_tyo where urakka = %s" oulu-urakka-id))
        _ (u (format "DELETE from urakka_tehtavamaara where urakka = %s" ii-urakka-id))

        ;; Luo tehtavan suunnittelua yksikkohintainen_tyo tauluun
        soratie-tehtava "Sorateiden kaventaminen"
        sorateiden-kaventaminen-tehtava-id (:id (first (q-map (format "SELECT id FROM toimenpidekoodi WHERE nimi = '%s';" soratie-tehtava))))
        sorateiden-maara 800
        _ (u (format "INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus, luotu, muokattu)
                      VALUES ('%s'::DATE, '%s'::DATE, %s, 'tonni', '8', %s, %s, %s, NOW(), NOW())"
               oulu-alkupvm oulu-loppupvm sorateiden-maara sorateiden-kaventaminen-tehtava-id oulu-urakka-id oulu-sopimus-id))

        ;; Lisää suunniteltu tehtävä ii:n urakalle
        pysakkimaara 12
        pysakki-tehtava "Pysäkkikatosten puhdistus"
        pysakkitehtava-id (:id (first (q-map (format "SELECT id FROM toimenpidekoodi WHERE nimi = '%s'" pysakki-tehtava))))
        _ (u (format "insert into urakka_tehtavamaara (urakka, \"hoitokauden-alkuvuosi\", tehtava, maara) values
        (%s, %s, %s, %s)" ii-urakka-id
               ;; Aseta vuosi oulun urakalta, jotta saadaan hausta nätti. Tämä tosi hajoaa, jos iin urakan vuodet loppuu joskus kesken
               (pvm/vuosi oulu-alkupvm)
               pysakkitehtava-id pysakkimaara))

        ;; Merkataan vielä, että tarjouksen tiedot on tallennettu
        _ (u (format "insert into sopimuksen_tehtavamaarat_tallennettu (urakka, tallennettu) values (%s, TRUE)" ii-urakka-id))

        ;; Haetaan apista tulokset
        vastaus (api-tyokalut/get-kutsu [(format "/api/analytiikka/suunnitellut-tehtavat/%s/%s" haku-alkaa haku-paattyy )] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        ;; Jätetään vain tässä luotujen urakoiden tehtävät
        urakoiden-tehtavat (filter
                             #(when (or (= oulu-urakka-id (:urakka-id %))
                                      (= ii-urakka-id (:urakka-id %)))
                                %)
                             encoodattu-body)]

    (is (= 200 (:status vastaus)))
    (is (not (nil? encoodattu-body)))
    ;; Pysäkki on ensin
    (is (= pysakki-tehtava (:tehtava (first (:suunnitellut-tehtavat (first (:vuosittaiset-suunnitelmat (first urakoiden-tehtavat))))))))
    (is (= pysakkimaara (:maara (first (:suunnitellut-tehtavat (first (:vuosittaiset-suunnitelmat (first urakoiden-tehtavat))))))))

    ;; Soratie toisena
    (is (= soratie-tehtava (:tehtava (first (:suunnitellut-tehtavat (first (:vuosittaiset-suunnitelmat (second urakoiden-tehtavat))))))))
    (is (= sorateiden-maara (:maara (first (:suunnitellut-tehtavat (first (:vuosittaiset-suunnitelmat (second urakoiden-tehtavat))))))))))

(deftest hae-suunnitellut-tehtavamaarat-kaikille-voimassaoleville-urakoille-palauttaa-voimassaolovuosien-mukaiset-tehtavamaarat
  (let [alkuvuosi 2000
        loppuvuosi 2040
        ;; Hae suunnitellut materiaalit
        vastaus (api-tyokalut/get-kutsu [(format "/api/analytiikka/suunnitellut-tehtavat/%s/%s" alkuvuosi loppuvuosi)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]

    (is (= 200 (:status vastaus)))
    (is (not (nil? encoodattu-body)))
    ;; Varmista, että yhdeltäkään urakalta ei löydy 40 riviä vuosittaisia tehtäviä
    (is (every? (fn [urakka] (if (> 12 (count (:vuosittaiset-suunnitelmat urakka))) true false)) encoodattu-body))))

(deftest hae-suunnitellut-tehtavamaarat-epaonnistuu
  (let [;; Haetaan apista tulokset
        uri-error-vastaus (api-tyokalut/get-kutsu [(format "/api/analytiikka/suunnitellut-tehtavat/%s/%s" "haku-alkaa" " haku-paattyy")] kayttaja-analytiikka portti)
        uri-virhe (:error uri-error-vastaus)
        virheellinen-kayttaja-vastaus (api-tyokalut/get-kutsu
                                        [(format "/api/analytiikka/suunnitellut-tehtavat/%s/%s" 2004 2024)]
                                        kayttaja-yit portti)
        vaara-vuodet-vastaus (api-tyokalut/get-kutsu [(format "/api/analytiikka/suunnitellut-tehtavat/%s/%s" "l" 200)] kayttaja-analytiikka portti)]
    (is (not (nil? uri-virhe)))
    (is (str/includes? uri-virhe "java.net.URISyntaxException"))
    (is (str/includes? virheellinen-kayttaja-vastaus "tuntematon-kayttaja"))
    (is (str/includes? vaara-vuodet-vastaus "puutteelliset-parametrit"))))
