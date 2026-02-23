(ns harja.palvelin.palvelut.suunnittelu.laskutusraja-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [harja.tyokalut.yleiset :refer :all]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.kulut.kulut :as kulut]
            [harja.palvelin.palvelut.suunnittelu.apurit :as apurit]
            [harja.palvelin.palvelut.suunnittelu.tarjous-palvelu :as tarjous-palvelu]
            [harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu :as kust-palvelu]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (luo-testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :uusi-kustannussuunnitelma (component/using
                                       (kust-palvelu/->UusiKustannussuunnitelmaPalvelu)
                                       [:http-palvelin :db])
          :tarjous (component/using
                     (tarjous-palvelu/->Tarjous)
                     [:http-palvelin :db])
          :kulut (component/using
                   (kulut/->Kulut)
                   [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))


(defn- luo-testikulu!
  "Luo testikulun ja palauttaa sen id:n"
  [urakka-id koontilaskun-kuukausi erapaiva-sql kokonaissumma laskun-numero lisatieto & {:keys [poistettu?] :or {poistettu? false}}]
  (let [poistettu-kentta (if poistettu? ", poistettu" "")
        poistettu-arvo (if poistettu? ", TRUE" "")]
    (ffirst (q (str "INSERT INTO kulu (urakka, koontilaskun_kuukausi, erapaiva, kokonaissumma, "
                 "laskun_numero, lisatieto" poistettu-kentta ", luoja, luotu) "
                 "VALUES (" urakka-id ", '" koontilaskun-kuukausi "', '" erapaiva-sql "', " kokonaissumma ", "
                 "'" laskun-numero "', '" lisatieto "'" poistettu-arvo ", (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW()) "
                 "RETURNING id")))))

(defn- lisaa-kulu-kohdistus!
  "Lisää kulun kohdistuksen"
  [kulu-id summa toimenpideinstanssi-id tehtavaryhma-id]
  (u (str "INSERT INTO kulu_kohdistus (rivi, kulu, summa, toimenpideinstanssi, tehtavaryhma, "
       "maksueratyyppi, tyyppi, tavoitehintainen, luotu, luoja) "
       "VALUES (0, " kulu-id ", " summa ", " toimenpideinstanssi-id ", " tehtavaryhma-id ", "
       "'kokonaishintainen', 'muukulu', TRUE, NOW(), "
       "(SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'))")))

(defn- poista-urakan-kulut-aikaväliltä!
  "Poistaa urakan kulut ja niiden kohdistukset annetulta aikaväliltä"
  [urakka-id alkupvm-sql loppupvm-sql]
  (u (str "DELETE FROM kulu_kohdistus WHERE kulu IN (SELECT id FROM kulu WHERE urakka = " urakka-id " AND erapaiva BETWEEN '" alkupvm-sql "' AND '" loppupvm-sql "')"))
  (u (str "DELETE FROM kulu WHERE urakka = " urakka-id " AND erapaiva BETWEEN '" alkupvm-sql "' AND '" loppupvm-sql "'")))

(defn- poista-kulu!
  "Poistaa yksittäisen kulun ja sen kohdistukset"
  [kulu-id]
  (u (str "DELETE FROM kulu_kohdistus WHERE kulu = " kulu-id))
  (u (str "DELETE FROM kulu WHERE id = " kulu-id)))

(defn- poista-kulut!
  "Poistaa useita kuluja ja niiden kohdistukset"
  [& kulu-idt]
  (let [ids-str (str/join ", " kulu-idt)]
    (u (str "DELETE FROM kulu_kohdistus WHERE kulu IN (" ids-str ")"))
    (u (str "DELETE FROM kulu WHERE id IN (" ids-str ")"))))

(defn- vahvista-tai-kumoa-tavoite-ja-kattohinta!
  "Vahvistaa tai kumoaa tavoitteen ja kattohinnan"
  [urakka-id hoitovuoden-alkuvuosi vahvista?]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+
    {:urakka-id urakka-id
     :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
     :vahvista? vahvista?}))


(deftest laskutusraja-paivittyy-tavoite-ja-kattohinnan-vahvistuksessa
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitovuoden-alkuvuosi 2025]
    ;; Aseta laskutusraja käyttöön
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id)
    ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
    (apurit/poista-tarjoukset-tietokannasta!)


    ;; Tallenna kustannussuunnitelma ja tarjous
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025))

    ;; Varmista että laskutusraja on NULL ennen vahvistusta
    (is (nil? (hae-urakan-laskutusraja urakka-id)) "Laskutusrajan pitäisi olla NULL ennen vahvistusta")

    ;; Vahvista
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)

    ;; Tarkista että laskutusraja on asetettu
    (let [laskutusraja (hae-urakan-laskutusraja urakka-id)
          tavoitehinta_indeksikorotettu (:tavoitehinta_indeksikorjattu
                                          (first (q-map (format "SELECT tavoitehinta_indeksikorjattu
                                                FROM urakka_tavoite
                                                WHERE urakka = %s AND hoitokausi = 1" urakka-id))))]
      (is (not (nil? laskutusraja)) "Laskutusrajan pitäisi olla asetettu")
      (is (= laskutusraja tavoitehinta_indeksikorotettu) "Laskutusrajan pitäisi olla sama kuin tavoitehinta_indeksikorjattu"))))


(deftest laskutusraja-ei-paivity-kun-laskutusraja_kaytossa-false
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026") ;; -21 alkanut urakka
        hoitovuoden-alkuvuosi 2024]
    ;; Varmista että laskutusraja_kaytossa = FALSE
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = FALSE WHERE urakkaid = " urakka-id)
    ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
    (apurit/poista-tarjoukset-tietokannasta!)

    ;; Tallenna kustannussuunnitelma ja tarjous
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2019 apurit/johto-ja-hallinto-tietomalli-2019))

    ;; Varmista että laskutusraja on NULL ennen vahvistusta
    (is (nil? (hae-urakan-laskutusraja urakka-id)) "Laskutusrajan pitäisi olla NULL ennen vahvistusta")

    ;; Vahvista
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)
    (is (nil? (hae-urakan-laskutusraja urakka-id)) "Laskutusrajan pitäisi olla NULL kun laskutusraja_kaytossa = FALSE")))


(deftest laskutusraja-nollataan-kun-vahvistus-kumotaan
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitovuoden-alkuvuosi 2025]
    ;; Varmista että laskutusraja_kaytossa = TRUE
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id)
    ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
    (apurit/poista-tarjoukset-tietokannasta!)

    ;; Tallenna kustannussuunnitelma ja tarjous
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025))

    ;; Vahvista
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)
    (is (not (nil? (hae-urakan-laskutusraja urakka-id))) "Laskutusrajan pitäisi olla asetettu")
    (kutsu-palvelua (:http-palvelin jarjestelma)
      :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ {:urakka-id urakka-id
                                                      :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                                                      :vahvista? false})
    (is (nil? (hae-urakan-laskutusraja urakka-id)) "Laskutusrajan pitäisi olla NULL vahvistuksen kumouksen jälkeen")))

(deftest hae-urakan-laskutusraja-kun-asetettu
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitovuoden-alkuvuosi 2025]
    ;; Aseta laskutusraja käyttöön
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id)
    ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
    (apurit/poista-tarjoukset-tietokannasta!)

    ;; Tallenna kustannussuunnitelma ja tarjous
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025))

    ;; Vahvista
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)

    ;; Kutsu palvelua
    (let [vastaus (kutsu-http-palvelua
                    :hae-urakan-laskutusraja
                    +kayttaja-jvh+
                    {:urakka-id urakka-id
                     :hoitovuosi hoitovuoden-alkuvuosi})]
      (is (map? vastaus) "Vastauksen pitäisi olla map")
      (is (contains? vastaus :laskutusraja) "Vastauksessa pitäisi olla :laskutusraja")
      (is (contains? vastaus :laskutusraja-kaytossa) "Vastauksessa pitäisi olla :laskutusraja-kaytossa")
      (is (number? (:laskutusraja vastaus)) "Laskutusrajan pitäisi olla numero")
      (is (pos? (:laskutusraja vastaus)) "Laskutusrajan pitäisi olla positiivinen")
      (is (true? (:laskutusraja-kaytossa vastaus)) "Laskutusraja-käytössä pitäisi olla true"))))

(deftest testaa-hae-urakan-laskutusraja-virheet
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")]
    ;; Testi ilman hoitovuotta
    (is (thrown? IllegalArgumentException
          (kulut/hae-urakan-laskutusraja
            (:db jarjestelma)
            +kayttaja-jvh+
            {:urakka-id urakka-id
             :hoitovuosi nil}))
      "Pitäisi heittää poikkeus kun hoitovuosi on nil")

    ;; Testi virheellisellä urakka-id:llä
    (is (thrown? IllegalArgumentException
          (kulut/hae-urakan-laskutusraja
            (:db jarjestelma)
            +kayttaja-jvh+
            {:urakka-id 999999999
             :hoitovuosi 2025}))
      "Pitäisi heittää poikkeus kun urakka-id ei ole olemassa")))

(deftest hae-hoitokauden-kulujen-summa-tyhja-hoitokausi
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        alkupvm (pvm/->pvm "01.10.2025")
        loppupvm (pvm/->pvm "30.09.2026")
        alkupvm-sql (konv/sql-timestamp alkupvm)
        loppupvm-sql (konv/sql-timestamp loppupvm)]
    ;; Varmista että hoitokaudella ei ole kuluja - Poista mahdolliset olemassa olevat kulut
    (poista-urakan-kulut-aikaväliltä! urakka-id alkupvm-sql loppupvm-sql)

    ;; Kutsu palvelua
    (let [summa (kutsu-http-palvelua
                  :hae-hoitokauden-kulujen-summa
                  +kayttaja-jvh+
                  {:urakka-id urakka-id
                   :alkupvm alkupvm
                   :loppupvm loppupvm})]
      (is (number? summa) "Summan pitäisi olla numero")
      (is (zero? summa) "Summan pitäisi olla 0 kun ei kuluja"))))

(deftest hae-hoitokauden-kulujen-summa-kun-kuluja-olemassa
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        alkupvm (pvm/->pvm "01.10.2025")
        loppupvm (pvm/->pvm "30.09.2026")
        alkupvm-sql (konv/sql-timestamp alkupvm)
        loppupvm-sql (konv/sql-timestamp loppupvm)
        toimenpideinstanssi-id (ffirst (q (str "SELECT id FROM toimenpideinstanssi WHERE urakka = " urakka-id " LIMIT 1")))
        tehtavaryhma-id (ffirst (q "SELECT id FROM tehtavaryhma WHERE nimi = 'A - Talvihoito'"))
        erapaiva (pvm/->pvm "15.11.2025")
        erapaiva-sql (konv/sql-timestamp erapaiva)]

    ;; Varmista että tarvittavat tiedot löytyvät
    (is (some? toimenpideinstanssi-id) "Toimenpideinstanssi pitäisi löytyä")
    (is (some? tehtavaryhma-id) "Tehtäväryhmä pitäisi löytyä")

    ;; Varmista että hoitokaudella ei ole vanhoja kuluja
    (poista-urakan-kulut-aikaväliltä! urakka-id alkupvm-sql loppupvm-sql)

    ;; Lisää testikulu
    (let [kulu-id (luo-testikulu! urakka-id "marraskuu/1-hoitovuosi" erapaiva-sql 1000.00 "TESTI-123" "Testikulu")]
      ;; Lisää kohdistus
      (lisaa-kulu-kohdistus! kulu-id 1000.00 toimenpideinstanssi-id tehtavaryhma-id)

      ;; Kutsu palvelua
      (let [summa (kutsu-http-palvelua
                    :hae-hoitokauden-kulujen-summa
                    +kayttaja-jvh+
                    {:urakka-id urakka-id
                     :alkupvm alkupvm
                     :loppupvm loppupvm})]
        (is (number? summa) "Summan pitäisi olla numero")
        (is (= 1000.00M summa) "Summan pitäisi olla 1000.00"))

      ;; Siivoa
      (poista-kulu! kulu-id))))

(deftest hae-hoitokauden-kulujen-summa-kun-poistettu-kulu
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        alkupvm (pvm/->pvm "01.10.2025")
        loppupvm (pvm/->pvm "30.09.2026")
        alkupvm-sql (konv/sql-timestamp alkupvm)
        loppupvm-sql (konv/sql-timestamp loppupvm)
        toimenpideinstanssi-id (ffirst (q (str "SELECT id FROM toimenpideinstanssi WHERE urakka = " urakka-id " LIMIT 1")))
        tehtavaryhma-id (ffirst (q "SELECT id FROM tehtavaryhma WHERE nimi = 'A - Talvihoito'"))
        erapaiva (pvm/->pvm "15.11.2025")
        erapaiva-sql (konv/sql-timestamp erapaiva)]

    ;; Varmista että tarvittavat tiedot löytyvät
    (is (some? toimenpideinstanssi-id) "Toimenpideinstanssi pitäisi löytyä")
    (is (some? tehtavaryhma-id) "Tehtäväryhmä pitäisi löytyä")

    ;; Varmista että hoitokaudella ei ole vanhoja kuluja
    (poista-urakan-kulut-aikaväliltä! urakka-id alkupvm-sql loppupvm-sql)

    ;; Lisää testikulu joka on poistettu
    (let [kulu-id (luo-testikulu! urakka-id "marraskuu/1-hoitovuosi" erapaiva-sql 1000.00 "TESTI-456" "Poistettu testikulu" :poistettu? true)]

      ;; Lisää kohdistus
      (lisaa-kulu-kohdistus! kulu-id 1000.00 toimenpideinstanssi-id tehtavaryhma-id)

      ;; Kutsu palvelua (poistetut kulut eivät sisälly)
      (let [summa (kutsu-http-palvelua
                    :hae-hoitokauden-kulujen-summa
                    +kayttaja-jvh+
                    {:urakka-id urakka-id
                     :alkupvm alkupvm
                     :loppupvm loppupvm})]
        (is (number? summa) "Summan pitäisi olla numero")
        (is (zero? summa) "Summan pitäisi olla 0 kun kulu on poistettu"))

      ;; Siivoa
      (poista-kulu! kulu-id))))

(deftest hae-hoitokauden-kulujen-summa-kun-useita-kuluja
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        alkupvm (pvm/->pvm "01.10.2025")
        loppupvm (pvm/->pvm "30.09.2026")
        alkupvm-sql (konv/sql-timestamp alkupvm)
        loppupvm-sql (konv/sql-timestamp loppupvm)
        toimenpideinstanssi-id (ffirst (q (str "SELECT id FROM toimenpideinstanssi WHERE urakka = " urakka-id " LIMIT 1")))
        tehtavaryhma-id (ffirst (q "SELECT id FROM tehtavaryhma WHERE nimi = 'A - Talvihoito'"))
        erapaiva1 (pvm/->pvm "15.11.2025")
        erapaiva1-sql (konv/sql-timestamp erapaiva1)
        erapaiva2 (pvm/->pvm "15.12.2025")
        erapaiva2-sql (konv/sql-timestamp erapaiva2)]

    ;; Varmista että hoitokaudella ei ole vanhoja kuluja
    (poista-urakan-kulut-aikaväliltä! urakka-id alkupvm-sql loppupvm-sql)

    ;; Lisää ensimmäinen testikulu
    (let [kulu-id1 (luo-testikulu! urakka-id "marraskuu/1-hoitovuosi" erapaiva1-sql 1500.00 "TESTI-111" "Ensimmäinen testikulu")
          ;; Lisää toinen testikulu
          kulu-id2 (luo-testikulu! urakka-id "joulukuu/1-hoitovuosi" erapaiva2-sql 2500.00 "TESTI-222" "Toinen testikulu")]
      ;; Lisää kohdistukset
      (lisaa-kulu-kohdistus! kulu-id1 1500.00 toimenpideinstanssi-id tehtavaryhma-id)
      (lisaa-kulu-kohdistus! kulu-id2 2500.00 toimenpideinstanssi-id tehtavaryhma-id)

      ;; Kutsu palvelua
      (let [summa (kutsu-http-palvelua
                    :hae-hoitokauden-kulujen-summa
                    +kayttaja-jvh+
                    {:urakka-id urakka-id
                     :alkupvm alkupvm
                     :loppupvm loppupvm})]
        (is (number? summa) "Summan pitäisi olla numero")
        (is (= 4000.00M summa) "Summan pitäisi olla 4000.00 (1500 + 2500)"))

      ;; Siivoa
      (poista-kulut! kulu-id1 kulu-id2))))
