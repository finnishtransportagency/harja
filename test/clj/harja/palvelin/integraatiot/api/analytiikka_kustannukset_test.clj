(ns harja.palvelin.integraatiot.api.analytiikka-kustannukset-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is use-fixtures]]
    [cheshire.core :as cheshire]
    [com.stuartsierra.component :as component]
    [harja.domain.kulut :as domain-kulut]
    [harja.kyselyt.urakat :as urakat-kyselyt]
    [harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone :as paatoskone]
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
    [harja.palvelin.palvelut.valikatselmus.valikatselmukset :as valikatselmus-palvelu]
    [harja.palvelin.palvelut.valikatselmus.paatos-apurit :as paatos-apurit]
    [harja.kyselyt.paatos-kyselyt :as paatos-kyselyt]
    [harja.kyselyt.urakat :as urakka-kyselyt]
    [taoensso.timbre :as log]))

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

(defn uusi-kulu-kustannukset-testiin [urakka-id summa vuosi urakan-alkupvm]
  (let [erapaiva (pvm/->pvm (str "1.11." vuosi))]
    {:id nil
     :urakka urakka-id
     :viite "12345678"
     :erapaiva erapaiva
     :kokonaissumma summa
     :tyyppi "laskutettava"
     :kohdistukset [{:kohdistus-id nil
                     :rivi 1
                     :summa (/ summa 2)
                     :toimenpideinstanssi (hae-toimenpideinstanssi-id urakka-id "23116")
                     :tehtavaryhma (hae-tehtavaryhman-id "V - Vesakonraivaukset ja puun poisto")
                     :tehtava (hae-tehtavan-id-nimella "Runkopuiden poisto")
                     :tyyppi :hankintakulu
                     :tavoitehintainen :true}
                    {:kohdistus-id nil
                     :rivi 2
                     :summa (/ summa 2)
                     :toimenpideinstanssi (hae-toimenpideinstanssi-id urakka-id "23116")
                     :tehtavaryhma (hae-tehtavaryhman-id "V - Vesakonraivaukset ja puun poisto")
                     :tehtava -1                            ;; Tämä on "Muu tehtävä"
                     :tyyppi :hankintakulu
                     :tavoitehintainen :true}]
     :koontilaskun-kuukausi (domain-kulut/pvm->koontilaskun-kuukausi erapaiva urakan-alkupvm)}))

(defn uusi-kulu-tehtavalla-kustannukset-testiin [urakka-id summa vuosi urakan-alkupvm]
  (let [erapaiva (pvm/->pvm (str "1.11." vuosi))]
    {:id nil
     :urakka urakka-id
     :viite "12345678"
     :erapaiva erapaiva
     :kokonaissumma summa
     :tyyppi "laskutettava"
     :kohdistukset [{:kohdistus-id nil
                     :rivi 1
                     :summa (/ summa 2)
                     :toimenpideinstanssi (hae-toimenpideinstanssi-id urakka-id "23104")
                     :tehtavaryhma (hae-tehtavaryhman-id-tunnisteella "3d5962b4-c7ca-4750-81f1-f589b9c7c52b") ;; B1 - Talvisuola
                     :tehtava (hae-tehtavan-id-nimella "Liukkaudentorjunta suolaamalla (materiaali)")
                     :tyyppi :hankintakulu
                     :tavoitehintainen :true}
                    {:kohdistus-id nil
                     :rivi 2
                     :summa (/ summa 2)
                     :toimenpideinstanssi (hae-toimenpideinstanssi-id urakka-id "23104")
                     :tehtavaryhma (hae-tehtavaryhman-id-tunnisteella "3d5962b4-c7ca-4750-81f1-f589b9c7c52b") ;; B1 - Talvisuola
                     :tehtava (hae-tehtavan-id-nimella "Liukkaudentorjunta hiekoituksella (materiaali)")
                     :tyyppi :hankintakulu
                     :tavoitehintainen :true}]
     :koontilaskun-kuukausi (domain-kulut/pvm->koontilaskun-kuukausi erapaiva urakan-alkupvm)}))

(deftest hae-toteutuneet-kustannukset-kulut-onnistuu-test
  (let [;; Pakotetaan urakaksi Oulu MHU
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka (:db jarjestelma) {:id urakka-id}))
        urakan-alkupvm (:alkupvm urakan-tiedot)

        ;; Luodaan kulu, joka on pakko löytyä aineistosta
        kulu-summa 987654321M
        uusi-kulu (uusi-kulu-kustannukset-testiin urakka-id kulu-summa 2023 urakan-alkupvm)
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
        juuri-luotu-kulu-kannasta (first (filter #(= (:kulun-kokonaissumma %) kulu-summa) kulut-kannasta))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteutuneet-kustannukset/" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        juuri-luotu-kulu-rajapinnasta (first (filter (fn [k]
                                                       (= (get-in k [:kulu :kulun-kokonaissumma]) (bigint kulu-summa)))
                                               (get-in encoodattu-body [:toteutuneet-kustannukset :kulut])))]
    (is (= 200 (:status vastaus)))
    (is (= (:kulun-kokonaissumma juuri-luotu-kulu-kannasta)
          (bigdec (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-kokonaissumma]))) "Tietokannan ja rajapinnan kulu ei täsmää.")
    (is (= 2023 (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-ajankohta :koontilaskun-vuosi])))
    (is (= 11 (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-ajankohta :koontilaskun-kuukausi])))
    (is (= "2023-10-31T22:00:00Z" (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-ajankohta :laskun-paivamaara])))
    (is (= true (get-in (first (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulukohdistukset])) [:kulukohdistus :tavoitehintainen])) "Tavoitehintaisuus ei palaudu oikein.")
    (is (= (count kulut-kannasta) (count (get-in encoodattu-body [:toteutuneet-kustannukset :kulut]))))))

;; Monista yllä oleva testi, mutta tehtävällä varustetulla kululla
(deftest hae-toteutuneet-kustannukset-kulut-tehtavalla-onnistuu-test
  (let [;; Pakotetaan urakaksi Oulu MHU
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka (:db jarjestelma) {:id urakka-id}))
        urakan-alkupvm (:alkupvm urakan-tiedot)

        ;; Poista urakan kaikki kulut
        _ (u (str "DELETE FROM kulu_kohdistus WHERE kulu in (select id from kulu where urakka = " urakka-id ");"))
        _ (u (str "DELETE FROM kulu_liite WHERE kulu in (select id from kulu where urakka = " urakka-id ");"))
        _ (u (str "DELETE FROM kulu WHERE urakka = " urakka-id ";"))

        ;; Luodaan kulu, joka on pakko löytyä aineistosta
        kulu-summa 1000M
        uusi-kulu (uusi-kulu-tehtavalla-kustannukset-testiin urakka-id kulu-summa 2023 urakan-alkupvm)
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
                                                k.kokonaissumma             AS \"kulun-kokonaissumma\",
                                                kk.tehtava                 AS \"kulukohdistus-tehtava\"
                                           FROM kulu k
                                                JOIN kulu_kohdistus kk ON k.id = kk.kulu
                                                JOIN urakka u ON k.urakka = u.id
                                          WHERE u.id = %s
                                          GROUP BY k.id, u.id, kk.tehtava
                                          ORDER BY k.erapaiva ;" urakka-id))

        ;; Varmista, että kannasta löytyy juuri luotu kulu
        juuri-luotu-kulu-kannasta (first (filter #(= (:kulun-kokonaissumma %) kulu-summa) kulut-kannasta))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteutuneet-kustannukset/" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        juuri-luotu-kulu-rajapinnasta (first (filter (fn [k]
                                                       (= (get-in k [:kulu :kulun-kokonaissumma]) (bigint kulu-summa)))
                                               (get-in encoodattu-body [:toteutuneet-kustannukset :kulut])))]
    (is (= 200 (:status vastaus)))
    (is (= kulu-summa (:kulun-kokonaissumma juuri-luotu-kulu-kannasta)
          (bigdec (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-kokonaissumma]))) "Tietokannan ja rajapinnan kulu ei täsmää.")
    (is (= 2023 (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-ajankohta :koontilaskun-vuosi])))
    (is (= 11 (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-ajankohta :koontilaskun-kuukausi])))
    (is (= "2023-10-31T22:00:00Z" (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-ajankohta :laskun-paivamaara])))
    (is (= true (get-in (first (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulukohdistukset])) [:kulukohdistus :tavoitehintainen]))
    "Tavoitehintaisuus ei palaudu oikein.")
    (is (= (count kulut-kannasta) (count (get-in encoodattu-body [:toteutuneet-kustannukset :kulut 0 :kulu :kulukohdistukset]))))

    ;; Varmista, että tehtävät tuli - Tehtävät on kovakoodattu, niin haetaan ne apufunktioilla
    (is (= (hae-tehtavan-id-nimella "Liukkaudentorjunta suolaamalla (materiaali)") (get-in encoodattu-body [:toteutuneet-kustannukset :kulut 0 :kulu :kulukohdistukset 0 :kulukohdistus :kohdistus :tehtava])))
    (is (= (hae-tehtavan-id-nimella "Liukkaudentorjunta hiekoituksella (materiaali)") (get-in encoodattu-body [:toteutuneet-kustannukset :kulut 0 :kulu :kulukohdistukset 1 :kulukohdistus :kohdistus :tehtava])))))

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
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakka-id}))
        urakan-tiedot (first (urakat-kyselyt/hae-urakka (:db jarjestelma) {:id urakka-id}))
        urakan-alkupvm (:alkupvm urakan-tiedot)
        hoitokauden-alkuvuosi 2023
        tavoitehinta 2000000
        kattohinta 2100000
        ylityksen-maara 100M
        toteutuneet-kustannukset (+ tavoitehinta ylityksen-maara)
        tilaajan-prosentti (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti urakan-parametrit)
        urakoitsijan-prosentti (- 100 (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti urakan-parametrit))
        tilaaja-maksaa (* ylityksen-maara (/ tilaajan-prosentti 100))
        urakoitsija-maksaa (* ylityksen-maara (/ urakoitsijan-prosentti 100))
        siirto 0M

        ;; Luodaan kulu, jolla ylitetään tavoitehinta
        uusi-kulu (uusi-kulu-kustannukset-testiin urakka-id toteutuneet-kustannukset hoitokauden-alkuvuosi urakan-alkupvm)
        kulu-vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-kulu
                       +kayttaja-jvh+
                       {:urakka-id urakka-id
                        :kulu-kohdistuksineen uusi-kulu})

        kulu-id (:id kulu-vastaus)
        ;tilaajan-maksu 123.12M
        ;urakoitsijan-maksu 321.32M
        kayttajaid (:id +kayttaja-jvh+)

        ;; Lisätään urakalle sopiva tavoitehinta - Poistetaan olemassa oleva, jos sellaisia on
        _ (u (format "DELETE FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = 5;" urakka-id))
        insert-str (format "INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, tavoitehinta_indeksikorjattu, kattohinta, kattohinta_indeksikorjattu, luotu) VALUES (%s, 5, %s, %s, %s, %s, now());"
                     urakka-id tavoitehinta tavoitehinta kattohinta kattohinta)
        _ (u insert-str)

        tavoitehinnan-ylitys-paatos (paatos-apurit/tavoitehinnan-ylityspaatos urakka-id hoitokauden-alkuvuosi tavoitehinta toteutuneet-kustannukset
                                      ylityksen-maara tilaajan-prosentti urakoitsijan-prosentti tilaaja-maksaa
                                      urakoitsija-maksaa siirto kulu-id true kayttajaid)
        db-paatos (paatos-kyselyt/tee-tavoitehinnan-ylityspaatos (:db jarjestelma) tavoitehinnan-ylitys-paatos)

        ;; Varmista, että vastauksesta löytyy juuri luotu oikaisu
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteutuneet-kustannukset/" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        juuri-luotu-paatos-rajapinnasta (first (filter (fn [k]
                                                         (= (bigdec (get-in k [:hoitovuoden-paatos :paatoksen-tulos :tilaaja-maksaa])) tilaaja-maksaa))
                                                 (get-in encoodattu-body [:toteutuneet-kustannukset :hoitovuoden-paatokset])))
        ;; Siivoa roskat - Poistetaan päätös
        _ (paatos-kyselyt/poista-tavoitehinnan-ylityspaatos (:db jarjestelma) urakka-id kayttajaid (:id db-paatos))]
    (is (= 200 (:status vastaus)))
    (is (not (nil? (get-in juuri-luotu-paatos-rajapinnasta [:hoitovuoden-paatos :paatos :id]))))
    (is (= tilaaja-maksaa
          (bigdec (get-in juuri-luotu-paatos-rajapinnasta [:hoitovuoden-paatos :paatoksen-tulos :tilaaja-maksaa]))) "Rajapinnan tavoitehinnan muutos ei täsmää.")
    (is (= urakoitsija-maksaa
          (bigdec (get-in juuri-luotu-paatos-rajapinnasta [:hoitovuoden-paatos :paatoksen-tulos :urakoitsija-maksaa]))) "Rajapinnan tavoitehinnan muutos ei täsmää.")
    (is (= false (get-in juuri-luotu-paatos-rajapinnasta [:hoitovuoden-paatos :poistettu])))))

(deftest hae-toteutuneet-kustannukset-tavoitehinnan-alituspaatos-onnistuu-test
  (let [;; Pakotetaan urakaksi Oulu MHU
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakka-id}))
        urakan-tiedot (first (urakat-kyselyt/hae-urakka (:db jarjestelma) {:id urakka-id}))
        urakan-alkupvm (:alkupvm urakan-tiedot)
        hoitokauden-alkuvuosi 2023
        hoitokauden-alun-tavoitehinta 2000000
        hoitokauden-lopun-tavoitehinta 2000000
        tavoitehinta 2000000
        kattohinta 2100000
        alituksen-maara 1000M
        toteutuneet-kustannukset (- tavoitehinta alituksen-maara)
        siirron-maara 0M
        tavoitepalkkion-maksuprosentti (:tavoitepalkkion_maksuprosentti urakan-parametrit)
        tavoitepalkkion_maksimi_prosentti (:tavoitepalkkion_maksimi urakan-parametrit)
        tilaaja-maksaa (* alituksen-maara (/ tavoitepalkkion-maksuprosentti 100))
        tavoitepalkkio tilaaja-maksaa

        ;; Luodaan kulu, jolla alitetaan tavoitehinta
        uusi-kulu (uusi-kulu-kustannukset-testiin urakka-id toteutuneet-kustannukset hoitokauden-alkuvuosi urakan-alkupvm)
        kulu-vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-kulu
                       +kayttaja-jvh+
                       {:urakka-id urakka-id
                        :kulu-kohdistuksineen uusi-kulu})

        kulu-id (:id kulu-vastaus)
        kayttajaid (:id +kayttaja-jvh+)

        ;; Lisätään urakalle sopiva tavoitehinta - Poistetaan olemassa oleva, jos sellaisia on
        _ (u (format "DELETE FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = 5;" urakka-id))
        insert-str (format "INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, tavoitehinta_indeksikorjattu, kattohinta, kattohinta_indeksikorjattu, luotu) VALUES (%s, 5, %s, %s, %s, %s, now());"
                     urakka-id tavoitehinta tavoitehinta kattohinta kattohinta)
        _ (u insert-str)

        tavoitehinnan-alitus-paatos (paatos-apurit/tavoitehinnan-alituspaatos urakka-id hoitokauden-alkuvuosi hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset
                                      alituksen-maara siirron-maara tavoitepalkkio tavoitepalkkion-maksuprosentti tavoitepalkkion_maksimi_prosentti kulu-id true kayttajaid)
        db-paatos (paatos-kyselyt/tee-tavoitehinnan-alituspaatos (:db jarjestelma) tavoitehinnan-alitus-paatos)

        ;; Varmista, että vastauksesta löytyy juuri luotu oikaisu
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteutuneet-kustannukset/" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        juuri-luotu-paatos-rajapinnasta (first (filter (fn [k]
                                                         (= (bigdec (get-in k [:hoitovuoden-paatos :paatoksen-tulos :tilaaja-maksaa])) tilaaja-maksaa))
                                                 (get-in encoodattu-body [:toteutuneet-kustannukset :hoitovuoden-paatokset])))

        ;; Siivoa roskat - Poistetaan päätös
        _ (paatos-kyselyt/poista-tavoitehinnan-alituspaatos (:db jarjestelma) urakka-id kayttajaid (:id db-paatos))]
    (is (= 200 (:status vastaus)))
    (is (not (nil? (get-in juuri-luotu-paatos-rajapinnasta [:hoitovuoden-paatos :paatos :id]))))
    (is (= tilaaja-maksaa
          (bigdec (get-in juuri-luotu-paatos-rajapinnasta [:hoitovuoden-paatos :paatoksen-tulos :tilaaja-maksaa]))) "Rajapinnan tavoitehinnan muutos ei täsmää.")
    (is (= false (get-in juuri-luotu-paatos-rajapinnasta [:hoitovuoden-paatos :poistettu])))))

(deftest hae-toteutuneet-kustannukset-kattohinnan-ylityspaatos-urakoitsija-maksaa-onnistuu-test
  (let [;; Pakotetaan urakaksi Oulu MHU
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakka-id}))
        urakan-tiedot (first (urakat-kyselyt/hae-urakka (:db jarjestelma) {:id urakka-id}))
        urakan-alkupvm (:alkupvm urakan-tiedot)
        hoitokauden-alkuvuosi 2019
        kattohinta 275000M
        ylityksen-maara 10000M
        toteutuneet-kustannukset (+ kattohinta ylityksen-maara)
        urakoitsija-maksaa ylityksen-maara
        siirtorajoitus-prosentti (:kattohintaylityksen_siirron_prosenttirajoitus urakan-parametrit)
        maksimi-siirrettava-maara 0M
        siirrettava-maara 0M
        viimeinen_hoitokausi false

        ;; Luodaan kulu, jolla ylitetään kattohinta
        uusi-kulu (uusi-kulu-kustannukset-testiin urakka-id toteutuneet-kustannukset hoitokauden-alkuvuosi urakan-alkupvm)

        kulu-vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-kulu
                       +kayttaja-jvh+
                       {:urakka-id urakka-id
                        :kulu-kohdistuksineen uusi-kulu})

        kulu-id (:id kulu-vastaus)
        kayttajaid (:id +kayttaja-jvh+)

        kattohinnan-ylitys-paatos (paatos-apurit/kattohinnan-ylityspaatos urakka-id hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                                    ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id viimeinen_hoitokausi maksimi-siirrettava-maara siirtorajoitus-prosentti kayttajaid)
        db-paatos (paatos-kyselyt/tee-kattohinnan-ylityspaatos (:db jarjestelma) kattohinnan-ylitys-paatos)

        ;; Varmista, että vastauksesta löytyy juuri luotu oikaisu
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteutuneet-kustannukset/" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        juuri-luotu-paatos-rajapinnasta (first (filter (fn [k]
                                                         (= (get-in k [:hoitovuoden-paatos :paatostyyppi]) "kattohinnan-ylitys"))
                                                 (get-in encoodattu-body [:toteutuneet-kustannukset :hoitovuoden-paatokset])))

        ;; Siivoa roskat - Poistetaan päätös
        _ (paatos-kyselyt/poista-kattohinnan-ylityspaatos (:db jarjestelma) urakka-id kayttajaid (:id db-paatos))]

    (is (= 200 (:status vastaus)))
    (is (not (nil? (get-in juuri-luotu-paatos-rajapinnasta [:hoitovuoden-paatos :paatos :id]))))
    (is (= urakoitsija-maksaa
            (bigdec (get-in juuri-luotu-paatos-rajapinnasta [:hoitovuoden-paatos :paatoksen-tulos :urakoitsija-maksaa]))) "Rajapinnan tavoitehinnan muutos ei täsmää.")
    (is (= false (get-in juuri-luotu-paatos-rajapinnasta [:hoitovuoden-paatos :poistettu])))))

(deftest hae-toteutuneet-kustannukset-kattohinnan-ylityspaatos-siirto-onnistuu-test
  (let [;; Pakotetaan urakaksi Oulu MHU
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakka-id}))
        urakan-tiedot (first (urakat-kyselyt/hae-urakka (:db jarjestelma) {:id urakka-id}))
        urakan-alkupvm (:alkupvm urakan-tiedot)
        hoitokauden-alkuvuosi 2019
        kattohinta 275000M
        ylityksen-maara 10000M
        toteutuneet-kustannukset (+ kattohinta ylityksen-maara)
        urakoitsija-maksaa 0M
        siirtorajoitus-prosentti (:kattohintaylityksen_siirron_prosenttirajoitus urakan-parametrit)
        maksimi-siirrettava-maara ylityksen-maara
        siirrettava-maara ylityksen-maara
        viimeinen_hoitokausi false

        ;; Luodaan kulu, jolla ylitetään kattohinta
        uusi-kulu (uusi-kulu-kustannukset-testiin urakka-id toteutuneet-kustannukset hoitokauden-alkuvuosi urakan-alkupvm)

        kulu-vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-kulu
                       +kayttaja-jvh+
                       {:urakka-id urakka-id
                        :kulu-kohdistuksineen uusi-kulu})

        kulu-id (:id kulu-vastaus)
        kayttajaid (:id +kayttaja-jvh+)

        kattohinnan-ylitys-paatos (paatos-apurit/kattohinnan-ylityspaatos urakka-id hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                                    ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id viimeinen_hoitokausi maksimi-siirrettava-maara siirtorajoitus-prosentti kayttajaid)
        db-paatos (paatos-kyselyt/tee-kattohinnan-ylityspaatos (:db jarjestelma) kattohinnan-ylitys-paatos)

        ;; Hae päätöksen tiedot
        paatos-tiedot (q-map (format "SELECT * FROM paatos_kattohinta WHERE urakkaid = %s AND hoitokauden_alkuvuosi = %s" urakka-id hoitokauden-alkuvuosi))

        ;; Varmista, että vastauksesta löytyy juuri luotu oikaisu
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteutuneet-kustannukset/" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        juuri-luotu-paatos-rajapinnasta (first (filter (fn [k]
                                                         (= (get-in k [:hoitovuoden-paatos :paatostyyppi]) "kattohinnan-ylitys"))
                                                 (get-in encoodattu-body [:toteutuneet-kustannukset :hoitovuoden-paatokset])))

        ;; Siivoa roskat - Poistetaan päätös
        _ (paatos-kyselyt/poista-kattohinnan-ylityspaatos (:db jarjestelma) urakka-id kayttajaid (:id db-paatos))]

    (is (= 200 (:status vastaus)))
    (is (not (nil? (get-in juuri-luotu-paatos-rajapinnasta [:hoitovuoden-paatos :paatos :id]))))
    (is (= siirrettava-maara
          (bigdec (get-in juuri-luotu-paatos-rajapinnasta [:hoitovuoden-paatos :paatoksen-tulos :siirretaan-seuraavalle-hoitovuodelle]))) "Kattohinnan ylityksen siirto seuraavalle hoitovuodelle ei täsmää.")
    (is (= false (get-in juuri-luotu-paatos-rajapinnasta [:hoitovuoden-paatos :poistettu])))))

(deftest hae-kustannussuunnitelma-onnistuu-test
  (let [;; Hae kaikki MHU urakat
        urakkalistaus (q-map "SELECT id, nimi FROM urakka WHERE tyyppi = 'teiden-hoito'")]
    (doseq [urakka urakkalistaus]
      (let [urakka-id (:id urakka)

            ;; Kiinteät kustannukset kannasta
            kiinteat-kulut-kannasta (:summa (first (q-map
                                                     (format "SELECT COALESCE(SUM(kit.summa), 0) as summa
                                             FROM kiinteahintainen_tyo kit
                                            WHERE kit.sopimus =  (SELECT id FROM sopimus WHERE urakka =  %s);" urakka-id))))

            ;; Arvioidut kustannukset kannasta
            arvioidut-kulut-kannasta (:summa (first (q-map
                                                      (format "SELECT COALESCE(SUM(kt.summa), 0) as summa
                                              FROM kustannusarvioitu_tyo kt
                                             WHERE kt.sopimus = (SELECT id FROM sopimus WHERE urakka =  %s);" urakka-id))))

            ;; Johto-ja-hallintokorvaukset kannasta
            johto-ja-hallintokulut-kannasta (:summa (first (q-map
                                                             (format "SELECT COALESCE(SUM(jjh.tuntipalkka * jjh.tunnit), 0) as summa
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
        (is (= (bigint arvioidut-kulut-kannasta) (bigint arvioidut-kulut-rajapinnasta)) (format "Urakalle %s arvioidut kulut täsmää" urakka-id))
        (is (= (bigint johto-ja-hallintokulut-kannasta) (bigint johto-ja-hallintokorvaukset-rajapinnasta)))))))

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
