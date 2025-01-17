(ns harja.palvelin.integraatiot.api.analytiikka-kustannukset-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is use-fixtures]]
    [cheshire.core :as cheshire]
    [com.stuartsierra.component :as component]
    [harja.pvm :as pvm]
    [harja.testi :refer :all]
    [harja.palvelin.komponentit.tietokanta :as tietokanta]
    [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
    [harja.palvelin.integraatiot.api.analytiikka :as api-analytiikka]
    [harja.palvelin.palvelut.kulut.kulut :as kulut]
    [harja.tyokalut.testidatan-kaytto :as testidatan-kaytto]
    [harja.tyokalut.testidatan-generointi :as testidatan-generointi]
    [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta-palvelu]
    [harja.palvelin.palvelut.toteumat :as toteumat-palvelu]
    [harja.palvelin.palvelut.kulut.valikatselmukset :as valikatselmus-palvelu]))

(def kayttaja-yit "yit-rakennus")
(def kayttaja-analytiikka "analytiikka-testeri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja-yit
    :api-analytiikka (component/using
                       (api-analytiikka/->Analytiikka false)
                       [:http-palvelin :db-replica :integraatioloki])))

(defn http-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :kulut (component/using
                   (kulut/->Kulut)
                   [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each jarjestelma-fixture http-fixture)

(defn uusi-kulu-kustannukset-testiin [urakka-id]
  {:id nil
   :urakka urakka-id
   :viite "12345678"
   :erapaiva #inst "2024-08-01T21:00:00.000-00:00"
   :kokonaissumma 987654321
   :tyyppi "laskutettava"
   :kohdistukset [{:kohdistus-id nil
                   :rivi 1
                   :summa 493827160.5
                   :toimenpideinstanssi (hae-toimenpideinstanssi-id urakka-id "23116")
                   :tehtavaryhma (hae-tehtavaryhman-id "V - Vesakonraivaukset ja puun poisto")
                   :tehtava nil
                   :tyyppi :hankintakulu
                   :tavoitehintainen :true}
                  {:kohdistus-id nil
                   :rivi 2
                   :summa 493827160.5
                   :toimenpideinstanssi (hae-toimenpideinstanssi-id urakka-id "23116")
                   :tehtavaryhma (hae-tehtavaryhman-id "V - Vesakonraivaukset ja puun poisto")
                   :tehtava nil
                   :tyyppi :hankintakulu
                   :tavoitehintainen :true}]
   :koontilaskun-kuukausi "elokuu/5-hoitovuosi"})

(deftest hae-toteutuneet-kustannukset-kulut-onnistuu-test
  (let [;; Pakotetaan urakaksi Oulu MHU
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")

        ;; Luodaan kulu, joka on pakko löytyä aineistosta
        uusi-kulu (uusi-kulu-kustannukset-testiin urakka-id)
        _ (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-kulu
            +kayttaja-jvh+
            {:urakka-id urakka-id
             :kulu-kohdistuksineen uusi-kulu})

        kulut-kannasta (q-map
                         (format "SELECT u.id                        AS urakka,
                                                u.urakkanro                 AS urakkatunnus,
                                                k.id                        AS \"kulu-id\",
                                                k.laskun_numero             AS \"laskun-tunniste\",
                                                k.lisatieto                 AS \"kulun-kuvaus\",
                                                k.poistettu                 AS \"poistettu\",
                                                k.koontilaskun_kuukausi     AS \"koontilaskun-kuukausi\",
                                                k.erapaiva                  AS \"kulun-ajankohta_laskun-paivamaara\",
                                                k.kokonaissumma             AS \"kulun-kokonaissumma\"
                                           FROM kulu k
                                                JOIN kulu_kohdistus kk ON k.id = kk.kulu
                                                JOIN urakka u ON k.urakka = u.id
                                          WHERE u.id = %s
                                          GROUP BY k.id, u.id
                                          ORDER BY k.erapaiva ;" urakka-id))

        ;; Varmista, että kannasta löytyy juuri luotu kulu
        juuri-luotu-kulu-kannasta (first (filter #(= (:kulun-kokonaissumma %) 987654321M) kulut-kannasta))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteutuneet-kustannukset/" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        juuri-luotu-kulu-rajapinnasta (first (filter (fn [k]
                                                       (= (get-in k [:kulu :kulun-kokonaissumma]) 987654321))
                                               (get-in encoodattu-body [:toteutuneet-kustannukset :kulut])))]
    (is (= 200 (:status vastaus)))
    (is (= (:kulun-kokonaissumma juuri-luotu-kulu-kannasta)
          (bigdec (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-kokonaissumma]))) "Tietokannan ja rajapinnan kulu ei täsmää.")
    (is (= 2024 (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-ajankohta :koontilaskun-vuosi])))
    (is (= 8 (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-ajankohta :koontilaskun-kuukausi])))
    (is (= "2024-08-01T21:00:00Z" (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-ajankohta :laskun-paivamaara])))
    (is (= (count kulut-kannasta) (count (get-in encoodattu-body [:toteutuneet-kustannukset :kulut]))))))


(deftest hae-toteutuneet-kustannukset-sanktiot-onnistuu-test
  (let [;; Pakotetaan urakaksi Oulu MHU
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        ;; Luodaan sanktio, joka on pakko löytyä aineistosta
        sanktiolle-sopiva-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23104") ;; "Oulumhun talvihoito" TODO: Kehittele näiden instanssien hallintaan joku muu tapa, kuin kaivella ne aina suoraa tietokannasta.
        sanktio-summa 123.12M
        sanktio-aika (pvm/nyt)
        laatupoikkeama (testidatan-generointi/luo-laatupoikkeama urakka-id)
        uusi-sanktio (testidatan-generointi/uusi-sanktio (pvm/nyt) sanktio-summa sanktiolle-sopiva-toimenpideinstanssi-id)
        uusi-paatos (testidatan-generointi/luo-sanktio-paatos sanktio-aika "Testi")
        laatupoikkeama (assoc laatupoikkeama
                         :paatos uusi-paatos
                         :sanktiot [uusi-sanktio])
        _ (laadunseuranta-palvelu/tallenna-laatupoikkeama
            {:db (:db jarjestelma)
             :user +kayttaja-jvh+
             :fim nil
             :email nil
             :sms nil
             :laatupoikkeama laatupoikkeama})

        ;; Varmista, että vastauksesta löytyy juuri luotu sanktio
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteutuneet-kustannukset/" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        juuri-luotu-sanktio-rajapinnasta (first (filter (fn [k]
                                                          (= (bigdec (get-in k [:sanktio :sanktion-maara])) sanktio-summa))
                                                  (get-in encoodattu-body [:toteutuneet-kustannukset :sanktiot])))

        ;; Siivoa roskat
        _ (testidatan-kaytto/poista-sanktio-perustelulla "Testi")]
    (is (= 200 (:status vastaus)))
    (is (= sanktio-summa
          (bigdec (get-in juuri-luotu-sanktio-rajapinnasta [:sanktio :sanktion-maara]))) "Rajapinnan sanktio ei täsmää.")))

(deftest hae-toteutuneet-kustannukset-bonukset-onnistuu-test
  (let [;; Pakotetaan urakaksi Oulu MHU
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        sopimus-id (hae-sopimus-id-urakka-idlla urakka-id)
        sopiva-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23151") ;; "Hoidon johto" TODO: Kehittele näiden instanssien hallintaan joku muu tapa, kuin kaivella ne aina suoraa tietokannasta.
        bonus-summa 123.12M
        bonus-aika (pvm/nyt)
        uusi-bonus (testidatan-generointi/uusi-bonus bonus-summa urakka-id bonus-aika sopiva-toimenpideinstanssi-id
                     sopimus-id "Lisätietoja" "asiakastyytyvaisyysbonus")
        _ (toteumat-palvelu/tallenna-erilliskustannus (:db jarjestelma) +kayttaja-jvh+ uusi-bonus)

        ;; Varmista, että vastauksesta löytyy juuri luotu bonus
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteutuneet-kustannukset/" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        juuri-luotu-bonus-rajapinnasta (first (filter (fn [k]
                                                        (= (bigdec (get-in k [:bonus :bonuksen-maara])) bonus-summa))
                                                (get-in encoodattu-body [:toteutuneet-kustannukset :bonukset])))

        ;; Siivoa roskat
        _ (testidatan-kaytto/poista-bonus-idlla (:bonus-id juuri-luotu-bonus-rajapinnasta))]
    (is (= 200 (:status vastaus)))
    (is (= bonus-summa
          (bigdec (get-in juuri-luotu-bonus-rajapinnasta [:bonus :bonuksen-maara]))) "Rajapinnan bonus ei täsmää.")))

(deftest hae-toteutuneet-kustannukset-tavoitehinnan-muutokset-onnistuu-test
  (let [;; Pakotetaan urakaksi Oulu MHU
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        hoitokauden-alkuvuosi 2023
        oikaisu-summa 123.12M
        selite "Kovasti tuuli"
        uusi-oikaisu (testidatan-generointi/uusi-tavoitehinnan-muutos urakka-id hoitokauden-alkuvuosi oikaisu-summa selite)
        _ (valikatselmus-palvelu/tallenna-tavoitehinnan-oikaisu (:db jarjestelma) +kayttaja-jvh+ uusi-oikaisu)

        ;; Varmista, että vastauksesta löytyy juuri luotu oikaisu
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteutuneet-kustannukset/" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        juuri-luotu-oikaisu-rajapinnasta (first (filter (fn [k]
                                                          (= (bigdec (get-in k [:tavoitehinnan-muutos :muutoksen-maara])) oikaisu-summa))
                                                  (get-in encoodattu-body [:toteutuneet-kustannukset :tavoitehinnan-muutokset])))

        ;; Siivoa roskat
        _ (testidatan-kaytto/poista-tavoitehinnan-muutos-idlla (:muutos-id juuri-luotu-oikaisu-rajapinnasta))]
    (is (= 200 (:status vastaus)))
    (is (= oikaisu-summa
          (bigdec (get-in juuri-luotu-oikaisu-rajapinnasta [:tavoitehinnan-muutos :muutoksen-maara]))) "Rajapinnan tavoitehinnan muutos ei täsmää.")))

(deftest hae-toteutuneet-kustannukset-hoitovuoden-paatokset-onnistuu-test
  (let [;; Pakotetaan urakaksi Oulu MHU
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        hoitokauden-alkuvuosi 2023
        tavoihinta 2000000
        kattohinta 2100000
        tilaajan-maksu 123.12M
        urakoitsijan-maksu 321.32M

        ;; Lisätään urakalle sopiva tavoitehinta - Poistetaan olemassa oleva, jos sellaisia on
        _ (u (format "DELETE FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = 5;" urakka-id))
        insert-str (format "INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, tavoitehinta_indeksikorjattu, kattohinta, kattohinta_indeksikorjattu, luotu) VALUES (%s, 5, %s, %s, %s, %s, now());"
                     urakka-id tavoihinta tavoihinta kattohinta kattohinta)
        _ (u insert-str)

        uusi-paatos (testidatan-generointi/uusi-paatos-tavoitehinnan-ylitys urakka-id hoitokauden-alkuvuosi tilaajan-maksu urakoitsijan-maksu)
        _ (valikatselmus-palvelu/tee-paatos-urakalle (:db jarjestelma) +kayttaja-jvh+ uusi-paatos)

        ;; Varmista, että vastauksesta löytyy juuri luotu oikaisu
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteutuneet-kustannukset/" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        juuri-luotu-paatos-rajapinnasta (first (filter (fn [k]
                                                         (= (bigdec (get-in k [:hoitovuoden-paatos :paatoksen-tulos :tilaaja-maksaa])) tilaajan-maksu))
                                                 (get-in encoodattu-body [:toteutuneet-kustannukset :hoitovuoden-paatokset])))
        ;; Siivoa roskat
        _ (testidatan-kaytto/poista-paatos-idlla (:paatos-id juuri-luotu-paatos-rajapinnasta))]
    (is (= 200 (:status vastaus)))
    (is (= tilaajan-maksu
          (bigdec (get-in juuri-luotu-paatos-rajapinnasta [:hoitovuoden-paatos :paatoksen-tulos :tilaaja-maksaa]))) "Rajapinnan tavoitehinnan muutos ei täsmää.")
    (is (= urakoitsijan-maksu
          (bigdec (get-in juuri-luotu-paatos-rajapinnasta [:hoitovuoden-paatos :paatoksen-tulos :urakoitsija-maksaa]))) "Rajapinnan tavoitehinnan muutos ei täsmää.")))

(deftest hae-kustannussuunnitelma-onnistuu-test
  (let [;; Pakotetaan urakaksi Oulu MHU
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")

        ;; Kiinteät kustannukset kannasta
        kiinteat-kulut-kannasta (:summa (first (q-map
                                                 (format "SELECT SUM(kit.summa) as summa
                                             FROM kiinteahintainen_tyo kit
                                            WHERE kit.sopimus =  (SELECT id FROM sopimus WHERE urakka =  %s);" urakka-id))))

        ;; Arvioidut kustannukset kannasta
        arvioidut-kulut-kannasta (:summa (first (q-map
                                                  (format "SELECT SUM(kt.summa) as summa
                                              FROM kustannusarvioitu_tyo kt
                                             WHERE kt.sopimus = (SELECT id FROM sopimus WHERE urakka =  %s);" urakka-id))))

        ;; Johto-ja-hallintokorvaukset kannasta
        johto-ja-hallintokulut-kannasta (:summa (first (q-map
                                                         (format "SELECT SUM(jjh.tuntipalkka * jjh.tunnit) as summa
                                                     FROM johto_ja_hallintokorvaus jjh
                                                    WHERE jjh.\"urakka-id\" = %s;" urakka-id))))

        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/suunnitellut-kustannukset/" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        kiinteat-kulut-rajapinnasta (apply + (map #(get-in % [:kustannus :summa])
                                               (get-in encoodattu-body [:suunnitellut-kustannukset :kiinteat-kustannukset])))
        arvioidut-kulut-rajapinnasta (apply + (map #(get-in % [:kustannus :summa])
                                                (get-in encoodattu-body [:suunnitellut-kustannukset :arvioidut-kustannukset])))
        johto-ja-hallintokorvaukset-rajapinnasta (apply + (map #(get-in % [:toimenkuvan-kustannus :summa])
                                                            (get-in encoodattu-body [:suunnitellut-kustannukset :johto-ja-hallintokorvaukset])))]

    (is (= 200 (:status vastaus)))
    (is (= kiinteat-kulut-kannasta (bigdec kiinteat-kulut-rajapinnasta)))
    (is (= arvioidut-kulut-kannasta (bigdec arvioidut-kulut-rajapinnasta)))
    (is (= johto-ja-hallintokulut-kannasta (bigdec johto-ja-hallintokorvaukset-rajapinnasta)))))

(deftest hae-kustannussuunnitelma-puutteellisilla-tunnuksilla
  (let [;; Pakotetaan urakaksi Oulu MHU
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")

        ;; Poistetaan oikeudet
        _ (poista-kayttajan-api-oikeudet kayttaja-analytiikka)
        ;; Näillä oikeuksilla ei pitäisi pystyä kutsumaan analytiikan rajapintoja
        _ (anna-kirjoitusoikeus kayttaja-analytiikka)

        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/suunnitellut-kustannukset/" urakka-id)] kayttaja-analytiikka portti)]
    ;; Käyttäjällä ei ole analytiikkaoikeuksia
    (is (= 403 (:status vastaus)) "Käyttäjältä ei löydy analytiikka api oikeuksia")
    (is (str/includes? (:body vastaus) "Käyttäjätunnuksella puutteelliset oikeudet") "Virheviesti löytyy")))

(deftest hae-rahavaraukset-onnistuu-test
  (let [;; Löydetään n. 14 rahavarausta ja niiden tehtävät
        rahavaraukset-kannasta (q-map
                                 (str "SELECT r.id as id, r.nimi as nimi, array_agg(rt.tehtava_id) as tehtavat
                                        FROM rahavaraus r
                                             JOIN rahavaraus_tehtava rt on r.id = rt.rahavaraus_id
                                       GROUP BY r.id, r.nimi;"))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/rahavaraukset")] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count rahavaraukset-kannasta) (count (:rahavaraukset encoodattu-body))))))
