(ns harja.palvelin.palvelut.suunnittelu.laskutusraja-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]

            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [harja.tyokalut.yleiset :refer :all]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.kulut.kulut :as kulut]
            [harja.palvelin.palvelut.indeksit :refer :all]
            [harja.palvelin.palvelut.indeksit :as indeksit]
            [harja.palvelin.palvelut.suunnittelu.apurit :as apurit]
            [harja.palvelin.palvelut.suunnittelu.tarjous-palvelu :as tarjous-palvelu]
            [harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu :as kust-palvelu]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as kust-kyselyt]))


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
                   [:http-palvelin :db])
          :muutokset (component/using
                       (muutos-palvelu/->Muutos {:kehitysmoodi true})
                       [:http-palvelin :db])
          :indeksit (component/using
                      (->Indeksit)
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

(defn- tallenna-muutos!
  [urakka-id muutos-payload hoitokaudet]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :tallenna-muutos +kayttaja-jvh+
    {:urakka-id urakka-id
     :valittu-hoitokausi (first hoitokaudet)
     :hoitokaudet hoitokaudet
     :muutos muutos-payload}))

(deftest laskutusraja-paivittyy-tavoite-ja-kattohinnan-vahvistuksessa
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitovuoden-alkuvuosi 2025]
    ;; Aseta laskutusraja käyttöön
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id)
    ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
    (apurit/poista-tarjoukset-tietokannasta! urakka-id)


    ;; Tallenna kustannussuunnitelma ja tarjous
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025))

    ;; Varmista että laskutusraja on asetettu jo ennen vahvistusta
    (is (not (nil? (hae-urakan-laskutusraja urakka-id))) "Laskutusrajan pitäisi olla asetettu jo ennen vahvistusta, koska laskutusraja_kaytossa = TRUE")

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
    (apurit/poista-tarjoukset-tietokannasta! urakka-id)(apurit/poista-tarjoukset-tietokannasta! urakka-id)

    ;; Tallenna kustannussuunnitelma ja tarjous
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2019 apurit/johto-ja-hallinto-tietomalli-2019))

    ;; Varmista että laskutusraja on NULL ennen vahvistusta
    (is (nil? (hae-urakan-laskutusraja urakka-id)) "Laskutusrajan pitäisi olla NULL ennen vahvistusta")

    ;; Vahvista
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)
    (is (nil? (hae-urakan-laskutusraja urakka-id)) "Laskutusrajan pitäisi olla NULL kun laskutusraja_kaytossa = FALSE")))


(deftest laskutusraja-pysyy-kun-vahvistus-kumotaan
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitovuoden-alkuvuosi 2025]
    ;; Varmista että laskutusraja_kaytossa = TRUE
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id)
    ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
    (apurit/poista-tarjoukset-tietokannasta! urakka-id)

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
    (is (not (nil? (hae-urakan-laskutusraja urakka-id))) "Laskutusrajan ei pitäisi olla NULL vahvistuksen kumouksen jälkeen")))

(deftest hae-urakan-laskutusraja-kun-asetettu
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitovuoden-alkuvuosi 2025]
    ;; Aseta laskutusraja käyttöön
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id)
    ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
    (apurit/poista-tarjoukset-tietokannasta! urakka-id)

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

(deftest laskutusraja-nousee-kun-muutostyo-ylittaa-3-prosenttia
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitovuoden-alkuvuosi 2025
        +hoitokaudet+ (mapv (fn [vuosi]
                              [(pvm/hoitokauden-alkupvm vuosi)
                               (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                        (range 2025 2030))]
    ;; 1. Aseta laskutusraja käyttöön
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id)
    ;; 2. Poista vanhat tarjoukset
    (apurit/poista-tarjoukset-tietokannasta! urakka-id)
    ;; 3. Tallenna kustannussuunnitelma ja tarjous (jotta tavoitehinta löytyy) ja vahvista
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025))
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)

    ;; 4. Hae laskutusraja ennen muutosta
    (let [laskutusraja-ennen (hae-urakan-laskutusraja urakka-id)
          _ (is (not (nil? laskutusraja-ennen)) "Laskutusrajan pitäisi olla asetettu vahvistuksen jälkeen")

          ;; 5. Tallenna muutostyö jonka summa on > 3% tavoitehinnasta
          tavoitehinta (first (map :tavoitehinta_indeksikorjattu
                                (q-map (format "SELECT tavoitehinta_indeksikorjattu FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = 1" urakka-id))))
          suuri-summa (when tavoitehinta (* tavoitehinta 0.05)) ;; 5% tavoitehinnasta -> ylittää 3%
          muutos-payload {:tyyppi "muutostyo"
                          :alityyppi :erillisrahoitus
                          :nimi "Testimuutostyö"
                          :syy "Testitarkoitus"
                          :voimassa_alkaen #inst "2025-11-01T00:00:00.000-00:00"
                          :tavoitehinnan-muutos suuri-summa}
          _ (tallenna-muutos! urakka-id muutos-payload +hoitokaudet+)

          ;; 6. Tarkista, että laskutusraja on noussut
          laskutusraja-jalkeen (hae-urakan-laskutusraja urakka-id)]
      (is (not (nil? laskutusraja-jalkeen)) "Laskutusrajan pitäisi olla olemassa")
      (is (> laskutusraja-jalkeen laskutusraja-ennen) "Laskutusrajan pitäisi olla noussut muutostyön jälkeen"))))

(deftest laskutusraja-nousee-kun-pysyva-muutos-positiivinen-ensimmaisena-hoitovuonna
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitovuoden-alkuvuosi 2025
        +hoitokaudet+ (mapv (fn [vuosi]
                              [(pvm/hoitokauden-alkupvm vuosi)
                               (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                        (range 2025 2030))]
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id)
    (apurit/poista-tarjoukset-tietokannasta! urakka-id)
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025))
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)

    (let [laskutusraja-ennen (hae-urakan-laskutusraja urakka-id)
          _ (is (not (nil? laskutusraja-ennen)) "Laskutusrajan pitäisi olla asetettu vahvistuksen jälkeen")
          toimenpideinstanssi-id (ffirst (q (str "SELECT id FROM toimenpideinstanssi WHERE urakka = " urakka-id " LIMIT 1")))
          tehtava-id  (ffirst (q "SELECT id FROM tehtava WHERE poistettu IS NOT TRUE LIMIT 1"))

          ;; Pysyvä muutos: positiivinen kustannusvaikutus hoitovuodelle 2025
          ;; -> pitäisi nostaa laskutusrajaa
          muutos-payload {:tyyppi "pysyva"
                          :nimi "Testipysyvä"
                          :syy "Testitarkoitus"
                          :voimassa_alkaen #inst "2025-10-01T00:00:00.000-00:00"
                          :tavoitehinnan-muutos 5000
                          :kustannusvaikutukset [{:toimenpideinstanssi toimenpideinstanssi-id
                                                  :kustannuslaji "hankintakustannukset"
                                                  :summa 5000
                                                  :hoitokauden_alkuvuosi 2025}]
                          :tehtavat_ja_maarat [{:tehtava tehtava-id
                                                :maaramuutos 100
                                                :hoitokauden_alkuvuosi 2025}]}
          _ (tallenna-muutos! urakka-id muutos-payload +hoitokaudet+)
          laskutusraja-jalkeen (hae-urakan-laskutusraja urakka-id)]

      (is (not (nil? laskutusraja-jalkeen)) "Laskutusrajan pitäisi olla olemassa")
      (is (> laskutusraja-jalkeen laskutusraja-ennen)
        "Laskutusrajan pitäisi nousta kun pysyvän muutoksen tavoitehinnan muutos on positiivinen"))))

(deftest laskutusraja-ei-nouse-kun-pysyva-muutos-negatiivinen
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitovuoden-alkuvuosi 2025
        +hoitokaudet+ (mapv (fn [vuosi]
                              [(pvm/hoitokauden-alkupvm vuosi)
                               (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                        (range 2025 2030))]
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id)
    (apurit/poista-tarjoukset-tietokannasta! urakka-id)
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025))
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)

    (let [laskutusraja-ennen (hae-urakan-laskutusraja urakka-id)

          ;; Negatiivinen kustannusvaikutus -> ei pidä nostaa laskutusrajaa
          muutos-payload {:tyyppi "pysyva"
                          :nimi "Testipysyvä negatiivinen"
                          :syy "Testitarkoitus"
                          :voimassa_alkaen #inst "2025-10-01T00:00:00.000-00:00"
                          :tavoitehinnan-muutos -5000
                          :kustannusvaikutukset [{:toimenpideinstanssi 129
                                                  :kustannuslaji "hankintakustannukset"
                                                  :summa -5000
                                                  :hoitokauden_alkuvuosi 2025}]
                          :tehtavat_ja_maarat [{:tehtava 6953
                                                :maaramuutos -100
                                                :hoitokauden_alkuvuosi 2025}]}
          _ (tallenna-muutos! urakka-id muutos-payload +hoitokaudet+)
          laskutusraja-jalkeen (hae-urakan-laskutusraja urakka-id)]

      (is (= laskutusraja-ennen laskutusraja-jalkeen)
        "Laskutusrajan ei pitäisi muuttua kun pysyvän muutoksen tavoitehinnan muutos on negatiivinen"))))

(deftest jhh-muutos-ei-vaikuta-laskutusrajaan
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitovuoden-alkuvuosi 2025
        +hoitokaudet+ (mapv (fn [vuosi]
                              [(pvm/hoitokauden-alkupvm vuosi)
                               (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                        (range 2025 2030))]
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id)
    (apurit/poista-tarjoukset-tietokannasta! urakka-id)
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025))
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)
    (let [laskutusraja-ennen (hae-urakan-laskutusraja urakka-id)
          jhh-muutos {:tyyppi "johto-ja-hallintokorvaus"
                      :voimassa_alkaen #inst "2025-10-01"
                      :syy "JHH-muutos testiin"
                      :nimi "JHH testi"
                      :kulut [{:pvm #inst "2025-10-01" :tavoitehinnan-muutos -1000}]}
          _ (tallenna-muutos! urakka-id jhh-muutos +hoitokaudet+)
          laskutusraja-jalkeen (hae-urakan-laskutusraja urakka-id)]
      (testing "JHH-muutos ei muuta laskutusrajaa"
        (is (= laskutusraja-ennen laskutusraja-jalkeen)
          "Laskutusrajan pitäisi pysyä samana JHH-muutoksen jälkeen")))))

(deftest laskutusraja-paivittyy-kun-muutostyo-poistetaan
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitovuoden-alkuvuosi 2025
        hoitokaudet [(pvm/hoitokauden-alkupvm 2025) (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm 2026))]]
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id)
    (apurit/poista-tarjoukset-tietokannasta! urakka-id)
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025))
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)
    (let [laskutusraja-ennen (hae-urakan-laskutusraja urakka-id)
          _ (is (not (nil? laskutusraja-ennen)) "Laskutusrajan pitäisi olla asetettu vahvistuksen jälkeen")
          summa 5000
          muutos-payload {:tyyppi "muutostyo"
                          :alityyppi :erillisrahoitus
                          :nimi "Testimuutostyö muutoksen poistamisen testausta varten"
                          :syy "Testitarkoitus"
                          :voimassa_alkaen #inst "2025-11-01T00:00:00.000-00:00"
                          :tavoitehinnan-muutos summa}
          tallenna-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tallenna-muutos +kayttaja-jvh+
                                 {:urakka-id urakka-id
                                  :valittu-hoitokausi hoitokaudet
                                  :hoitokaudet [hoitokaudet]
                                  :laskenta-automatiikka? true
                                  :muutos muutos-payload})
          muutos-id (->> (:kirjatut-muutokset tallenna-vastaus)
                                (filter #(= (:nimi %) "Testimuutostyö muutoksen poistamisen testausta varten"))
                                first
                                :id)

          laskutusraja-tallennuksen-jalkeen (hae-urakan-laskutusraja urakka-id)
          ;; Poistetaan muutos
          _ (kutsu-palvelua (:http-palvelin jarjestelma)
                           :poista-muutos +kayttaja-jvh+
                           {:muutos-id muutos-id
                            :urakka-id urakka-id
                            :valittu-hoitokausi hoitokaudet
                            :hoitokaudet [hoitokaudet]
                            :laskenta-automatiikka? true})
          laskutusraja-poistamisen-jalkeen (hae-urakan-laskutusraja urakka-id)]
      (is (some? laskutusraja-tallennuksen-jalkeen) "Laskutusrajan pitäisi olla olemassa tallennuksen jälkeen")
      (is (some? laskutusraja-poistamisen-jalkeen) "Laskutusrajan pitäisi olla olemassa poistamisen jälkeen")
      (is (< laskutusraja-poistamisen-jalkeen laskutusraja-tallennuksen-jalkeen)
        "Laskutusrajan pitäisi pienentyä muutoksen poistamisen jälkeen")
      (is (= (- laskutusraja-tallennuksen-jalkeen summa) laskutusraja-poistamisen-jalkeen) "Laskutusrajan pitäisi olla
        poistamisen jälkeen sama kuin tallennuksen jälkeen vähennettynä muutoksen summalla"))))

(deftest laskutusraja-paivittyy-kun-indeksi-muuttuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        indeksi "TESTI-INDEKSI-LASKUTUSRAJA-2015"
        hoitokausi 1
        tavoitehinta 100000M
        kattohinta 110000M
        muutostyo-osuus 5000M  ;; simuloi muutostöiden kumulatiivinen summa
        laskutusraja-alkuperainen tavoitehinta
        laskutusraja-nykyinen (+ tavoitehinta muutostyo-osuus)]

    ;; 1. Aseta laskutusraja käyttöön
    (u (str "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id))

    ;; 2. Tallenna peruslukua varten 2024 syys/loka/marras-indeksit
    ;;    (urakka alkaa 2025, joten perusluku lasketaan 2024 syksystä)
    (indeksit/tallenna-indeksi db +kayttaja-jvh+
      {:nimi indeksi
       :indeksit [{:kannassa? false
                   :vuosi 2024
                   8 100.0
                   9 101.0
                   10 102.0
                   11 103.0}]})

    ;; 3. Vaihda urakka käyttämään testi-indeksiä
    (u (format "UPDATE urakka SET indeksi = '%s' WHERE id = %s" indeksi urakka-id))

    ;; 4. Lisää urakka_tavoite-rivi laskutusrajoineen
    (u (format "DELETE FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s" urakka-id hoitokausi))
    (u (format "INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, kattohinta, laskutusraja, laskutusraja_alkuperainen)
                VALUES (%s, %s, %s, %s, %s, %s)"
         urakka-id hoitokausi tavoitehinta kattohinta laskutusraja-nykyinen laskutusraja-alkuperainen))

    (let [ut-id (ffirst (q (format "SELECT id FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s" urakka-id hoitokausi)))]
      ;; 5. Tallenna elokuun indeksi vuodelle 2025 → laukaisee paivita-urakka-tavoite-indeksille!
      (indeksit/tallenna-indeksi db +kayttaja-jvh+
        {:nimi indeksi
         :indeksit [{:kannassa? false
                     :vuosi 2025
                     8 108.0}]})

      ;; 6. Tarkista että laskutusraja on päivittynyt
      (let [uusi-tavoitehinta-indeksikorjattu (ffirst (q (format "SELECT tavoitehinta_indeksikorjattu FROM urakka_tavoite WHERE id = %s AND hoitokausi = %s" ut-id hoitokausi)))
            uusi-laskutusraja (ffirst (q (format "SELECT laskutusraja FROM urakka_tavoite WHERE id = %s AND hoitokausi = %s" ut-id hoitokausi)))]
        (is (some? uusi-tavoitehinta-indeksikorjattu) "Tavoitehinta_indeksikorjattu pitäisi olla päivitetty")
        (is (some? uusi-laskutusraja) "Laskutusrajan pitäisi olla olemassa")
        ;; Muutostöiden osuus = laskutusraja_nykyinen - laskutusraja_alkuperainen = 5000
        ;; Uusi laskutusraja = uusi tavoitehinta_indeksikorjattu + 5000
        (is (= uusi-laskutusraja (+ uusi-tavoitehinta-indeksikorjattu muutostyo-osuus))
          "Laskutusrajan pitäisi olla uusi tavoitehinta_indeksikorjattu + muutostöiden osuus")))))

(deftest laskutusraja-ei-paivity-kun-indeksi-muuttuu-ennen-2025-alkaneella-urakalla
  (let [db (:db jarjestelma)
        urakka (hae-urakan-id-nimella "Iin MHU 2021-2026")
        indeksi "TESTI-INDEKSI-LASKUTUSRAJA-2015"
        hoitokausi 4]

    ;; 1. Vaihda urakka käyttämään testi-indeksiä
    (u (format "UPDATE urakka SET indeksi = '%s' WHERE id = %s" indeksi urakka))

    ;; 2. Lisää testi-indeksin perusluku (2020 syys-loka-marras)
    (indeksit/tallenna-indeksi
      (:db jarjestelma)
      +kayttaja-jvh+
      {:nimi indeksi
       :indeksit [{:kannassa? false
                   :vuosi 2020
                   9 101.1
                   10 101.6
                   11 101.8}]})

    ;; 3. Lisää syyskuun 2024 indeksi jotta voidaan laskea lokakuun indeksikorjaus
    (indeksit/tallenna-indeksi
      (:db jarjestelma)
      +kayttaja-jvh+
      {:nimi indeksi
       :indeksit [{:kannassa? false
                   :vuosi 2024
                   9 102.9M}]})

    ;; 4. Tarkista alkutilanne
    (let [alkuperainen-tavoitehinta (ffirst (q (format "SELECT tavoitehinta_indeksikorjattu FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s" urakka hoitokausi)))
          alkuperainen-laskutusraja (ffirst (q (format "SELECT laskutusraja FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s" urakka hoitokausi)))]
      (is (some? alkuperainen-tavoitehinta))
      (is (nil? alkuperainen-laskutusraja)) ;; Ei pitäisi olla asetettu

      ;; 5. Päivitä indeksiä (muuta syyskuun 2024 arvoa)
      (indeksit/tallenna-indeksi
        (:db jarjestelma)
        +kayttaja-jvh+
        {:nimi indeksi
         :indeksit [{:kannassa? true
                     :vuosi 2024
                     9 150.0M}]}) ;; Muutettu arvo

      ;; 6. Tarkista että vain tavoitehinta_indeksikorjattu on päivittynyt
      (let [uusi-tavoitehinta (ffirst (q (format "SELECT tavoitehinta_indeksikorjattu FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s" urakka hoitokausi)))
            uusi-laskutusraja (ffirst (q (format "SELECT laskutusraja FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s" urakka hoitokausi)))]
        (is (not= alkuperainen-tavoitehinta uusi-tavoitehinta) "tavoitehinta_indeksikorjattu pitäisi päivittyä")
        (is (nil? uusi-laskutusraja) "laskutusraja ei pitäisi päivittyä ennen 2025 alkaneella urakalla")))))

(deftest hae-laskutusrajan-tarkistukset-palauttaa-tyhjan-kun-ei-muutoksia
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitokauden-alkuvuosi 2025]
    ;; Varmistetaan ettei muutoksia ole
    (u "DELETE FROM mhu_muutos WHERE urakka = " urakka-id
      " AND voimassa_alkaen BETWEEN '2025-10-01' AND '2026-09-30'")

    (let [tulos (kust-kyselyt/hae-laskutusrajan-tarkistukset
                  (:db jarjestelma)
                  {:urakka urakka-id
                   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                   :hoitovuoden_indeksikorjattu_tavoitehinta 100000})]
      (is (empty? tulos) "Tuloksen pitäisi olla tyhjä kun muutoksia ei ole"))))


(deftest hae-laskutusrajan-tarkistukset-alle-3-prosenttia
  ;; Kun muutosten kumulatiivinen summa on alle 3% tavoitehinnasta,
  ;; laskutusrajan-tarkistus on 0 eikä tarkistettu-laskutusraja nouse
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitokauden-alkuvuosi 2025
        +hoitokaudet+ (mapv (fn [vuosi]
                              [(pvm/hoitokauden-alkupvm vuosi)
                               (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                        (range 2025 2030))]
    ;; Siivotaan hoitokauden muutokset ennen testiä
    (u (format "DELETE FROM mhu_muutos_kustannusvaikutus WHERE muutos IN
                (SELECT id FROM mhu_muutos WHERE urakka = %s
                 AND voimassa_alkaen BETWEEN '2025-10-01' AND '2026-09-30')" urakka-id))
    (u (format "DELETE FROM mhu_muutos WHERE urakka = %s
                AND voimassa_alkaen BETWEEN '2025-10-01' AND '2026-09-30'" urakka-id))
    (apurit/poista-tarjoukset-tietokannasta! urakka-id)
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitokauden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025))
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitokauden-alkuvuosi true)

    ;; Haetaan todellinen indeksikorjattu tavoitehinta, jotta 1% pysyy varmasti alle 3%
    (let [tavoitehinta (first (map :tavoitehinta_indeksikorjattu
                               (q-map (format "SELECT tavoitehinta_indeksikorjattu FROM urakka_tavoite
                                              WHERE urakka = %s AND hoitokausi = 1" urakka-id))))]

      ;; Tallenna muutostyö jonka summa on < 3% tavoitehinnasta (1%)
      (tallenna-muutos! urakka-id
        {:tyyppi "muutostyo"
         :alityyppi :erillisrahoitus
         :nimi "Pieni muutostyö"
         :syy "Testi"
         :voimassa_alkaen #inst "2025-11-01T00:00:00.000-00:00"
         :tavoitehinnan-muutos (* tavoitehinta 0.01)} ;; 1% -> alle 3%
        +hoitokaudet+)

      (let [tulos (kust-kyselyt/hae-laskutusrajan-tarkistukset
                    (:db jarjestelma)
                    {:urakka urakka-id
                     :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                     :hoitovuoden_indeksikorjattu_tavoitehinta tavoitehinta})]
        (is (seq tulos) "Tuloksessa pitäisi olla rivejä")
        (is (every? #(zero? (get % (keyword "laskutusrajan-tarkistus"))) tulos)
          "Laskutusrajan tarkistuksen pitäisi olla 0 kun alle 3%")
        ;; Tarkistettu laskutusraja = alkuperäinen + 0 = alkuperäinen
        ;; Ei siis nil, vaan sama kuin alkuperäinen laskutusraja
        (let [laskutusraja-alkuperainen (first (map :laskutusraja_alkuperainen
                                               (q-map (format "SELECT laskutusraja_alkuperainen FROM urakka_tavoite
                                                              WHERE urakka = %s AND hoitokausi = 1" urakka-id))))]
          (when laskutusraja-alkuperainen
            (is (every? #(= laskutusraja-alkuperainen (get % (keyword "tarkistettu-laskutusraja"))) tulos)
              "Tarkistettu laskutusraja pitäisi olla sama kuin alkuperäinen kun tarkistus on 0")))))))


(deftest hae-laskutusrajan-tarkistukset-yli-3-prosenttia
  ;; Kun muutosten kumulatiivinen summa ylittää 3% tavoitehinnasta,
  ;; laskutusrajan-tarkistus > 0 ja tarkistettu-laskutusraja = alkuperäinen + tarkistus
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitokauden-alkuvuosi 2025
        +hoitokaudet+ (mapv (fn [vuosi]
                              [(pvm/hoitokauden-alkupvm vuosi)
                               (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                        (range 2025 2030))]
    (apurit/poista-tarjoukset-tietokannasta! urakka-id)
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitokauden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025))
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitokauden-alkuvuosi true)

    (let [tavoitehinta (first (map :tavoitehinta_indeksikorjattu
                                (q-map (format "SELECT tavoitehinta_indeksikorjattu FROM urakka_tavoite
                                               WHERE urakka = %s AND hoitokausi = 1" urakka-id))))
          laskutusraja-alkuperainen (first (map :laskutusraja_alkuperainen
                                             (q-map (format "SELECT laskutusraja_alkuperainen FROM urakka_tavoite
                                                            WHERE urakka = %s AND hoitokausi = 1" urakka-id))))
          suuri-summa (* tavoitehinta 0.05)] ;; 5% -> ylittää 3%

      (tallenna-muutos! urakka-id
        {:tyyppi "muutostyo"
         :alityyppi :erillisrahoitus
         :nimi "Suuri muutostyö"
         :syy "Testi"
         :voimassa_alkaen #inst "2025-11-01T00:00:00.000-00:00"
         :tavoitehinnan-muutos suuri-summa}
        +hoitokaudet+)

      (let [tulos (kust-kyselyt/hae-laskutusrajan-tarkistukset
                    (:db jarjestelma)
                    {:urakka urakka-id
                     :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                     :hoitovuoden_indeksikorjattu_tavoitehinta tavoitehinta})
            viimeisin (last tulos)]
        (is (seq tulos) "Tuloksessa pitäisi olla rivejä")
        (is (pos? (get viimeisin (keyword "laskutusrajan-tarkistus")))
          "Laskutusrajan tarkistuksen pitäisi olla positiivinen kun yli 3%")
        (is (>= (:prosenttiosuus viimeisin) 3.00M)
          "Prosenttiosuuden pitäisi olla vähintään 3%")
        (when laskutusraja-alkuperainen
          (is (= (get viimeisin (keyword "tarkistettu-laskutusraja"))
                (+ laskutusraja-alkuperainen (get viimeisin (keyword "laskutusrajan-tarkistus"))))
            "Tarkistettu laskutusraja = alkuperäinen + tarkistus"))))))
