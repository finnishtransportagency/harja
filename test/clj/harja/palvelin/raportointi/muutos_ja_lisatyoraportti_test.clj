(ns harja.palvelin.raportointi.muutos-ja-lisatyoraportti-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]

            [harja.fmt :as fmt]
            [harja.pvm :as pvm]

            [harja.domain.kulut :as domain-kulut]

            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]

            [harja.palvelin.raportointi.raportit.muutos-ja-lisatyoraportti :as muutos-raportti]
            [harja.palvelin.raportointi.testiapurit :as apurit]
            [harja.palvelin.raportointi :as raportointi]

            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]
            [harja.palvelin.palvelut.raportit :as raportit]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :muutokset (component/using
                       (muutos-palvelu/->Muutos {:kehitysmoodi true})
                       [:http-palvelin :db])
          :pdf-vienti (component/using
                        (pdf-vienti/luo-pdf-vienti)
                        [:http-palvelin])
          :raportointi (component/using
                         (raportointi/luo-raportointi)
                         [:db :pdf-vienti])
          :raportit (component/using
                      (raportit/->Raportit)
                      [:http-palvelin :db :raportointi :pdf-vienti])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; Apufunktiot
(defn- siivoa-muutosdata!
  "Poistaa kaikki muutoksiin liittyvät tiedot testidatakannasta Kajaanin 2025-2030 urakalle."
  [urakka-id]
  (let [muutos-ehto (str " WHERE muutos IN (SELECT id FROM mhu_muutos WHERE urakka = " urakka-id ")")]
    ;; Rahavarausmuutossyyt
    (u (str "DELETE FROM mhu_muutos_rahavarausmuutoksen_syy WHERE urakka = " urakka-id))
    ;; Poista mahdolliset rahavarausliitokset
    (u (str "DELETE FROM rahavaraus_urakka WHERE urakka_id = " urakka-id))

    ;; Liitetaulut
    (u (str "DELETE FROM mhu_muutos_liite_historia" muutos-ehto))
    (u (str "DELETE FROM mhu_muutos_liite" muutos-ehto))

    ;; Kustannusvaikutukset ja tehtävä-määräluettelo (historia ensin)
    (u (str "DELETE FROM mhu_muutos_kustannusvaikutus_historia" muutos-ehto))
    (u (str "DELETE FROM mhu_muutos_kustannusvaikutus" muutos-ehto))
    (u (str "DELETE FROM mhu_muutos_tehtava_ja_maaraluettelo_historia" muutos-ehto))
    (u (str "DELETE FROM mhu_muutos_tehtava_ja_maaraluettelo" muutos-ehto))

    ;; Tehtävätiedot
    (u (str "DELETE FROM mhu_muutos_tehtava_tiedot WHERE urakka = " urakka-id))

    ;; uutos-kulu linkitys — ENSIN historia, sitten päätaulu
    ;;    TÄMÄ TÄYTYY TAPAHTUA ENNEN kulu-taulun poistoa, koska mhu_muutos_kulu.kulu REFERENCES kulu(id)
    (u (str "DELETE FROM mhu_muutos_kulu_historia" muutos-ehto))
    (u (str "DELETE FROM mhu_muutos_kulu" muutos-ehto))

    ;; Kulut — nyt vasta kun mhu_muutos_kulu ei enää viittaa kulu-riveihin
    (u (str "UPDATE kulu_kohdistus SET poistettu = TRUE WHERE kulu IN (SELECT id FROM kulu WHERE urakka = " urakka-id ")"))
    (u (str "UPDATE kulu SET poistettu = TRUE WHERE urakka = " urakka-id))

    ;; Muutokset
    (u (str "DELETE FROM mhu_muutos_historia WHERE urakka = " urakka-id))
    (u (str "UPDATE mhu_muutos set poistettu = TRUE WHERE urakka = " urakka-id))))

(defn- tallenna-kirjallinen-muutos! [urakka-id tyyppi syy voimassa-alkaen valittu-hoitokausi maaramuutos euromuutos]
  (let [muutos-payload (merge
                         {:tyyppi tyyppi
                          :voimassa_alkaen voimassa-alkaen,
                          :syy syy,
                          :nimi syy
                          :tehtavat_ja_maarat [{:tehtava 17345, :maaramuutos maaramuutos, :hoitokauden_alkuvuosi (pvm/vuosi (first valittu-hoitokausi))}],
                          :kustannusvaikutukset [{:summa euromuutos, :toimenpideinstanssi 129, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi (pvm/vuosi (first valittu-hoitokausi))}]}
                         (when (= tyyppi "muutostyo"))
                           {:alityyppi :poikkeama})]
    (kutsu-palvelua (:http-palvelin jarjestelma)
      :tallenna-muutos
      +kayttaja-jvh+
      {:urakka-id urakka-id
       :valittu-hoitokausi valittu-hoitokausi
       :muutos muutos-payload})))

(defn- tallenna-muutostyo-muutos! [urakka-id syy voimassa-alkaen valittu-hoitokausi euromaara]
  (let [payload {:nimi syy
                 :syy syy,
                 :voimassa_alkaen voimassa-alkaen,
                 :tavoitehinnan-muutos euromaara,
                 :tyyppi "muutostyo"
                 :alityyppi :erillisrahoitus}]
    (kutsu-palvelua (:http-palvelin jarjestelma)
      :tallenna-muutos
      +kayttaja-jvh+
      {:urakka-id urakka-id
       :valittu-hoitokausi valittu-hoitokausi
       :muutos payload})))


(defn- luo-kustannusvaikutus!
  "Luo kustannusvaikutuksen muutokselle."
  [muutos-id hoitokauden-alkuvuosi summa]
  (u (str "INSERT INTO mhu_muutos_kustannusvaikutus (versio, muutos, kustannuslaji, hoitokauden_alkuvuosi, summa)
           VALUES (1, " muutos-id ", 'hankintakustannukset', " hoitokauden-alkuvuosi ", " summa ")")))

(defn- luo-jjh-muutos!
  "Luo johto- ja hallintokorvauksen muutoksen kuluineen.
   Malli: muutos_testidata.sql, Muutos 4."
  [urakka-id syy voimassa-alkaen erapaiva summa]
  (let [tpi-id (hae-toimenpideinstanssi-id urakka-id "23151")
        urakan-alkupvm (ffirst (q (str "SELECT alkupvm FROM urakka WHERE id = " urakka-id)))
        jjh-ryhma-id (ffirst (q "SELECT id FROM tehtavaryhma WHERE nimi = 'J - Johto- ja hallintokorvaus'"))
        ;; Luo muutos
        muutos-id (i (str "INSERT INTO mhu_muutos (versio, urakka, voimassa_alkaen, tyyppi, syy, luoja)
                           VALUES (1, " urakka-id ", '" voimassa-alkaen "',
                                   'johto-ja-hallintokorvaus', '" syy "', " (:id +kayttaja-jvh+) ")"))
        koontilaskun-kuukausi (domain-kulut/pvm->koontilaskun-kuukausi erapaiva urakan-alkupvm)
        ;; Luo kulu
        kulu-id (i (format "INSERT INTO kulu (kokonaissumma, erapaiva, urakka, luoja, lisatieto, koontilaskun_kuukausi)
                         VALUES (%s, '%s', %s, %s, 'JJH-testikulu', '%s')"
                     summa erapaiva urakka-id (:id +kayttaja-jvh+) koontilaskun-kuukausi))]
    ;; Luo kulukohdistus äsken luodulle kululle
    (u (str "INSERT INTO kulu_kohdistus (rivi, kulu, summa, toimenpideinstanssi, tehtavaryhma,
                                         maksueratyyppi, luoja, tyyppi, tavoitehintainen)
             VALUES (0, " kulu-id ", " summa ", " tpi-id ", " jjh-ryhma-id ",
                     'kokonaishintainen', " (:id +kayttaja-jvh+) ", 'jjh-muutos', true)"))
    ;; Linkitä muutos aiemmin luotu muutos äsken luotuun kuluun
    (u (str "INSERT INTO mhu_muutos_kulu (versio, muutos, kulu)
             VALUES (1, " muutos-id ", " kulu-id ")"))
    muutos-id))

(defn- suorita-raportti
  "Suorittaa Muutos- ja lisätyöraportin kutsu-palvelua kautta."
  [urakka-id alkupvm loppupvm]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :suorita-raportti
    +kayttaja-jvh+
    {:nimi :muutos-ja-lisatyot
     :konteksti "urakka"
     :urakka-id urakka-id
     :parametrit {:alkupvm alkupvm
                  :loppupvm loppupvm}}))

(defn- hae-taulukko
  "Hakee raportin taulukon otsikon perusteella. Otsikko voi olla osa taulukon otsikosta."
  [vastaus osa avain]
  (some (fn [elementti]
          (when (and (vector? elementti)
                  (= :taulukko (first elementti))
                  (.contains (str (avain (second elementti))) osa))
            elementti))
    vastaus))

;; ============================================================
;; Testataan Kirjallisesti sovitut muutokset
;; ============================================================

(deftest kirjallisesti-sovitut-muutokset-happy-case
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        maaramuutos 10
        euromuutos-pysyva 5000
        euromuutos-muutos 3000
        euromuutos-jjh 1500
        _ (siivoa-muutosdata! urakka-id)

        ;; Luo testiaineisto: yksi kutakin tyyppiä
        valittu-hoitokausi [(pvm/luo-pvm 2028 10 1) (pvm/luo-pvm 2029 9 30)]
        _ (tallenna-kirjallinen-muutos! urakka-id "pysyva" "Pysyvä syy" (pvm/luo-pvm 2028 11 1)
                         valittu-hoitokausi maaramuutos euromuutos-pysyva)
        _ (tallenna-muutostyo-muutos! urakka-id "Muutostyö syy" (pvm/luo-pvm 2028 12 15) valittu-hoitokausi euromuutos-muutos)
        _ (luo-jjh-muutos! urakka-id "JJH syy" (pvm/->pvm (str "20.10.2028")) (pvm/->pvm (str "15.10.2028")) euromuutos-jjh)
        ;; Suorita raportti hoitokaudelle 2028-2029
        vastaus (suorita-raportti urakka-id
                  (pvm/hoitokauden-alkupvm 2028)
                  (pvm/hoitokauden-loppupvm 2029))
        taulukko (hae-taulukko vastaus "Kirjallisesti sovitut" :sheet-nimi)]
    (is (some? taulukko) "Kirjallisesti sovitut muutokset -taulukko löytyy")
    (let [rivit (apurit/taulukon-rivit taulukko)
          yhteensarivi (last rivit)]
      ;; Pitäisi olla 3 datariviä + 1 yhteensärivi
      (is (= 4 (count rivit)) "Taulukossa on 4 riviä (3 data + yhteensä)")
      ;; Tarkista yhteensä: 5000 + 3000 + 1500 = 9500
      (let [yhteensa-arvo (nth (:rivi yhteensarivi) 3)]
        (is (= 9500M yhteensa-arvo) "Yhteensä-rivin tavoitehinnan muutos on 9500")))))

(deftest kirjallisesti-sovitut-muutokset-ei-dataa
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        _ (siivoa-muutosdata! urakka-id)
        ;; Suorita raportti ilman testiaineistoa
        vastaus (suorita-raportti urakka-id
                  (pvm/hoitokauden-alkupvm 2028)
                  (pvm/hoitokauden-loppupvm 2029))
        taulukko (hae-taulukko vastaus "Kirjallisesti sovitut" :sheet-nimi)]
    (is (some? taulukko) "Taulukko löytyy vaikka dataa ei ole")
    (let [rivit (apurit/taulukon-rivit taulukko)]
      ;; Vain yhteensä-rivi (0 €)§
      (is (= 0 (count rivit)) "Taulukossa ei ole dataa"))))

(deftest laskutusrajan-tarkistukset-tulevat-kyselydatasta
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        _ (siivoa-muutosdata! urakka-id)
        hoitokauden-alkuvuosi 2028
        budjettitavoite {:tavoitehinta-indeksikorjattu 100000M}
        tavoitehinta 100000M
        euromuutos 5000
        valittu-hoitokausi [(pvm/luo-pvm 2028 10 1) (pvm/luo-pvm 2029 9 30)]
        _ (tallenna-muutostyo-muutos! urakka-id "Muutostyö syy" (pvm/luo-pvm 2028 10 15) valittu-hoitokausi euromuutos)
        raportin-osat (muutos-raportti/muodosta-laskutusrajan-tarkistukset
                        (:db jarjestelma) urakka-id hoitokauden-alkuvuosi budjettitavoite tavoitehinta)
        taulukko (first raportin-osat)
        rivit (apurit/taulukon-rivit taulukko)
        eka-rivi (first rivit)]
    (is (= "Laskutusrajan automaattiset tarkistukset" (:otsikko (second taulukko))))
    (is (= 1 (count rivit)) "Taulukossa on yksi tarkistus")
    (is (= 5 (count eka-rivi)) "Taulukossa on viisi saraketta")
    (is (= (pvm/->pvm "15.11.2028") (nth eka-rivi 0)))
    (is (= (bigdec euromuutos) (nth eka-rivi 1)))
    (is (= (str (fmt/prosentti-opt 5.0M)) (nth eka-rivi 2)))
    (is (= "+5 000,00" (nth eka-rivi 3)))))

;; ============================================================
;; Testataan: Aikaisempien vuosien pysyvien muutosten vaikutukset
;; ============================================================

(deftest aiempien-vuosien-pysyvat-muutokset-happy-case
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        _ (siivoa-muutosdata! urakka-id)
        maaramuutos 12
        euromuutos-pysyva 2500
        ;; Luo pysyvä muutos, joka on voimassa alkaen EDELLISELTÄ hoitokaudelta
        ;; mutta jolla on kustannusvaikutus nykyiselle hoitokaudelle 2028
        valittu-hoitokausi [(pvm/luo-pvm 2028 10 1) (pvm/luo-pvm 2029 9 30)]
        _ (tallenna-kirjallinen-muutos! urakka-id "pysyva" "Aiemman vuoden pysyvä" (pvm/luo-pvm 2027 10 2)
            valittu-hoitokausi maaramuutos euromuutos-pysyva)

        ;; Suorita raportti hoitokaudelle 2028-2029
        vastaus (suorita-raportti urakka-id (pvm/hoitokauden-alkupvm 2028) (pvm/hoitokauden-loppupvm 2029))
        taulukko (hae-taulukko vastaus "Aiemmilta hoitovuosilta jatkuvat pysyvät muutokset" :otsikko)]
    (is (some? taulukko) "Aikaisempien vuosien taulukko löytyy")
    (let [rivit (apurit/taulukon-rivit taulukko)
          yhteensarivi (last rivit)]
      (is (= 2 (count rivit)) "Taulukossa on 2 riviä (1 data + yhteensä)")
      ;; Tarkista yhteensä
      (let [yhteensa-arvo (nth (:rivi yhteensarivi) 2)]
        (is (= (bigdec euromuutos-pysyva) yhteensa-arvo) (str "Yhteensä on " euromuutos-pysyva))))))

(deftest aiempien-vuosien-pysyvat-muutokset-ei-dataa
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        _ (siivoa-muutosdata! urakka-id)
        ;; Suorita raportti ilman testiaineistoa
        vastaus (suorita-raportti urakka-id
                  (pvm/hoitokauden-alkupvm 2028)
                  (pvm/hoitokauden-loppupvm 2029))
        taulukko (hae-taulukko vastaus "Aiemmilta hoitovuosilta jatkuvat pysyvät muutokset" :otsikko)]
    (is (some? taulukko) "Taulukko löytyy vaikka dataa ei ole")
    (let [rivit (apurit/taulukon-rivit taulukko)]
      (is (= 0 (count rivit)) "Taulukossa ei ole dataa."))))

;; ============================================================
;; Testataan: Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset
;; ============================================================

(deftest tehtava-maaramuutokset-happy-case
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        urakan-alkupvm (ffirst (q (str "SELECT alkupvm FROM urakka WHERE id = " urakka-id)))
        _ (siivoa-muutosdata! urakka-id)
        ;; Hae tehtävä jolla on tehtavamaara (suunniteltu) tässä urakassa
        tehtava-tietorivi (first (q (str
                                      "SELECT ut.tehtava AS tehtava_id, ut.maara AS suunniteltu_maara, tk.nimi AS tehtava_nimi
                                    FROM urakka_tehtavamaara ut
                                         JOIN tehtava tk ON tk.id = ut.tehtava
                                   WHERE ut.urakka = " urakka-id "
                                    AND ut.\"hoitokauden-alkuvuosi\" = 2028
                                    AND ut.maara > 0
                                    AND ut.poistettu IS NOT TRUE
                                    AND tk.\"maaramitattava?\" IS TRUE
                                    AND tk.suunnitteluyksikko IS NOT NULL
                                    AND tk.suunnitteluyksikko != 'euroa'
                                  LIMIT 1")))
        tehtava-id (first tehtava-tietorivi)
        tehtava-nimi (nth tehtava-tietorivi 2)
        _ (println "Valittu tehtava-rivi:" tehtava-tietorivi)
        _ (assert (some? tehtava-id) "Tehtävä löytyy testidatasta")
        ;; Luo kulu joka kohdistetaan tehtävälle
        kulusumma 800
        tpi-id (ffirst (q (str "SELECT id FROM toimenpideinstanssi WHERE urakka = " urakka-id " LIMIT 1")))
        erapaiva (pvm/->pvm (str "15.01.2029"))
        koontilaskun-kuukausi (domain-kulut/pvm->koontilaskun-kuukausi erapaiva urakan-alkupvm)
        kulu-id (i (format "INSERT INTO kulu (kokonaissumma, erapaiva, urakka, luoja, koontilaskun_kuukausi)
                         VALUES (%s, '%s', %s, %s, '%s')"
                     kulusumma erapaiva urakka-id (:id +kayttaja-jvh+) koontilaskun-kuukausi))]
    (u (str "INSERT INTO kulu_kohdistus (rivi, kulu, summa, toimenpideinstanssi, tehtava, maksueratyyppi, luoja)
             VALUES (0, " kulu-id ", " kulusumma ", " tpi-id ", " tehtava-id ", 'kokonaishintainen', " (:id +kayttaja-jvh+) ")"))
    ;; Suorita raportti
    (let [vastaus (suorita-raportti urakka-id (pvm/hoitokauden-alkupvm 2028) (pvm/hoitokauden-loppupvm 2029))
          taulukko (hae-taulukko vastaus "Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset" :otsikko)]
      (is (some? taulukko) "Tehtävä-maaramuutokset taulukko löytyy")
      (let [rivit (apurit/taulukon-rivit taulukko)
            _ (println "Rivi määrä:" (count rivit))
            _ (println "rivit:"  rivit)
            tehtava-raporttirivi (some #(when (= tehtava-nimi (first %)) %) rivit)]
        ;; Pitäisi olla vähintään 2 riviä (data + yhteensä)
        (is (>= (count rivit) 2) "Taulukossa on vähintään datarivi ja yhteensä-rivi")
        ;; Tehtävä löytyy raportista ja sen summa on oikein
        (is (some? tehtava-raporttirivi) "Tehtävä löytyy raportista")
        (is (= (bigdec kulusumma) (some-> tehtava-raporttirivi (nth 6))) "Tehtävän summa on 800M")

        ;; Viimeinen rivi on yhteensä
        (let [viimeinen (last rivit)]
          (is (map? viimeinen) "Viimeinen rivi on yhteensä-map")
          (is (:lihavoi? viimeinen) "Yhteensä-rivi on lihavoitu"))))))

(deftest tehtava-maaramuutokset-ei-dataa
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        _ (siivoa-muutosdata! urakka-id)
        ;; Poistetaan myös tehtävämäärät jotta taulukko on tyhjä
        _ (u (str "DELETE FROM urakka_tehtavamaara WHERE urakka = " urakka-id " AND \"hoitokauden-alkuvuosi\" = 2029"))
        vastaus (suorita-raportti urakka-id (pvm/hoitokauden-alkupvm 2029) (pvm/hoitokauden-loppupvm 2030))
        taulukko (hae-taulukko vastaus "Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset" :otsikko)]
    (is (some? taulukko) "Taulukko löytyy vaikka dataa ei ole")
    (let [rivit (apurit/taulukon-rivit taulukko)]
      ;; Vain yhteensä-rivi
      (is (= 0 (count rivit)) "Taulukossa ei ole dataa."))))

;; ============================================================
;; Taulukko 4: Rahavarausten muutokset
;; ============================================================

(deftest rahavarausten-muutokset-happy-case
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        urakan-alkupvm (ffirst (q (str "SELECT alkupvm FROM urakka WHERE id = " urakka-id)))
        hoitokauden-alkuvuosi 2028
        kuukausi 10
        summa1 5000
        summa2 3000
        _ (siivoa-muutosdata! urakka-id)
        sopimus-id (ffirst (q (str "SELECT id FROM sopimus WHERE urakka = " urakka-id " AND paasopimus IS NULL")))
        ;; Luo rahavaraus ja liitä urakkaan
        rahavaraus-id (i (format "INSERT INTO rahavaraus (nimi, jarjestys, luoja, luotu) VALUES
        ('%s', %s, %s, NOW())" "Testirahavaraus", 1, (:id +kayttaja-jvh+)))
        _ (u (format "INSERT INTO rahavaraus_urakka (urakka_id, rahavaraus_id, luotu, luoja)
                   VALUES (%s, %s, NOW(), %S)"
               urakka-id rahavaraus-id (:id +kayttaja-jvh+)))
        ;; Luo suunniteltu kustannus
        tpi-id (ffirst (q (str "SELECT id FROM toimenpideinstanssi WHERE urakka = " urakka-id " LIMIT 1")))
        _ (u (format "INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi, sopimus, rahavaraus_id, luotu, luoja)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, NOW(), %s)"
               hoitokauden-alkuvuosi kuukausi summa1 summa1 tpi-id sopimus-id rahavaraus-id (:id +kayttaja-jvh+)))
        _ (u (format "INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi, sopimus, rahavaraus_id, luotu, luoja)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, NOW(), %s)"
               hoitokauden-alkuvuosi (inc kuukausi) summa2 summa2 tpi-id sopimus-id rahavaraus-id (:id +kayttaja-jvh+)))
        ;; Luo toteutunut kulu kohdistettuna rahavaraukseen
        erapaiva (pvm/->pvm (str "15.11." hoitokauden-alkuvuosi))
        koontilaskun-kuukausi (domain-kulut/pvm->koontilaskun-kuukausi erapaiva urakan-alkupvm)
        kulu-id (i (format "INSERT INTO kulu (kokonaissumma, erapaiva, urakka, luoja, koontilaskun_kuukausi)
        VALUES (%s, '%s', %s, %s, '%s')"
                     6500, erapaiva urakka-id (:id +kayttaja-jvh+), koontilaskun-kuukausi))
        _ (u (str "INSERT INTO kulu_kohdistus (rivi, kulu, summa, toimenpideinstanssi, rahavaraus_id, maksueratyyppi, luoja)
                   VALUES (0, " kulu-id ", 6500, " tpi-id ", " rahavaraus-id ", 'kokonaishintainen', " (:id +kayttaja-jvh+) ")"))
        ;; Suorita raportti
        vastaus (suorita-raportti urakka-id
                  (pvm/hoitokauden-alkupvm 2028)
                  (pvm/hoitokauden-loppupvm 2029))
        rahavaraustaulukko (hae-taulukko vastaus "Rahavarausten muutokset" :otsikko)]
    (is (some? rahavaraustaulukko) "Rahavarausten muutokset -taulukko löytyy")
    (let [rahavarausrivit (apurit/taulukon-rivit rahavaraustaulukko)
          datarivit (filter vector? rahavarausrivit)
          yhteensarivi (last rahavarausrivit)]
      (is (= 2 (count rahavarausrivit)) "Taulukossa on 2 riviä (1 data + yhteensä)")
      ;; Tarkista datarivi: suunniteltu = 8000, toteutunut = 6500, muutos = 6500 - 8000 = -1500
      (when (seq datarivit)
        (let [rivi (first datarivit)]
          (is (= "Testirahavaraus" (nth rivi 0)) "Rahavarauksen nimi on oikein")
          (is (= 8000M (nth rivi 2)) "Suunniteltu määrä on 8000")
          (is (= 6500M (nth rivi 3)) "Toteutunut määrä on 6500")
          (is (= -1500M (nth rivi 4)) "Tavoitehinnan muutos on -1500")))
      ;; Tarkista yhteensä
      (let [yht (:rivi yhteensarivi)]
        (is (= -1500M (nth yht 4)) "Yhteensä tavoitehinnan muutos on -1500")))))

(deftest rahavarausten-muutokset-ei-dataa
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        _ (siivoa-muutosdata! urakka-id)
        vastaus (suorita-raportti urakka-id
                  (pvm/hoitokauden-alkupvm 2028)
                  (pvm/hoitokauden-loppupvm 2029))
        taulukko (hae-taulukko vastaus "Rahavarausten muutokset" :otsikko)]
    (is (some? taulukko) "Taulukko löytyy vaikka dataa ei ole")
    (let [rivit (apurit/taulukon-rivit taulukko)]
      ;; Vain yhteensä-rivi
      (is (= 1 (count rivit)) "Taulukossa on vain yhteensä-rivi")
      (let [yhteensarivi (first rivit)
            yht-muutos (nth (:rivi yhteensarivi) 4)]
        (is (= 0 yht-muutos) "Yhteensä on 0 kun dataa ei ole")))))




(defn- hae-taulukko-sheet-nimella
  "Hakee raportin taulukon sheet-nimen perusteella. Käytetään vanhan tyyppisten urakoiden
   osioille, joissa taulukolla ei ole :otsikko-avainta vaan :sheet-nimi."
  [vastaus sheet-nimi-osa]
  (some (fn [elementti]
          (when (and (vector? elementti)
                  (= :taulukko (first elementti))
                  (.contains (str (:sheet-nimi (second elementti))) sheet-nimi-osa))
            elementti))
    (drop 2 vastaus)))

(defn- siivoa-vanha-urakka-raportti-data!
  "Poistaa tavoitehinnan oikaisut ja lisätyökulut vanhan tyyppisen urakan testidatasta."
  [urakka-id]
  (u (str "DELETE FROM tavoitehinnan_oikaisu WHERE \"urakka-id\" = " urakka-id))
  (u (str "UPDATE kulu_kohdistus SET poistettu = TRUE
           WHERE tyyppi = 'lisatyo'
             AND kulu IN (SELECT id FROM kulu WHERE urakka = " urakka-id ")"))
  (u (str "UPDATE kulu SET poistettu = TRUE
           WHERE urakka = " urakka-id
       " AND id IN (SELECT kk.kulu FROM kulu_kohdistus kk
                     JOIN kulu k ON kk.kulu = k.id
                    WHERE kk.tyyppi = 'lisatyo'
                      AND k.urakka = " urakka-id ")")))

(deftest vanha-urakka-tavoitehinnan-oikaisut-nakyvat
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        _ (siivoa-vanha-urakka-raportti-data! urakka-id)
        ;; Lisää tavoitehinnan oikaisu hoitokaudelle 2020
        _ (u (str "INSERT INTO tavoitehinnan_oikaisu
                   (\"urakka-id\", \"muokkaaja-id\", muokattu, otsikko, selite, summa, \"hoitokauden-alkuvuosi\", poistettu)
                   VALUES (" urakka-id ", " (:id +kayttaja-jvh+) ", NOW(),
                           'Testioikaisu', 'Syystulvat venyttivät urakkaa', 15000, 2020, false)"))
        vastaus (suorita-raportti urakka-id (pvm/hoitokauden-alkupvm 2020) (pvm/hoitokauden-loppupvm 2021))
        taulukko (hae-taulukko-sheet-nimella vastaus "Tavoitehinnan muutokset")]

    ;; Tavoitehinnan oikaisut -taulukko löytyy
    (is (some? taulukko) "Tavoitehinnan oikaisut -taulukko löytyy vanhalla urakalla")
    (let [rivit (apurit/taulukon-rivit taulukko)
          datarivit (filter vector? rivit)
          yhteensarivi (last rivit)]
      (is (= 2 (count rivit)) "Taulukossa on 2 riviä (1 data + yhteensä)")

      ;; Tarkista datarivin syy ja summa
      (when (seq datarivit)
        (let [rivi (first datarivit)]
          (is (= "Syystulvat venyttivät urakkaa" (nth rivi 1)) "Oikaisun selite on oikein")
          (is (= 15000M (nth rivi 2)) "Oikaisun summa on 15000")))
      ;; Tarkista yhteensä
      (is (= 15000M (nth (:rivi yhteensarivi) 2)) "Yhteensä on 15000"))
    ;; Muutoshallinta-osiot EI näy vanhalla urakalla
    (is (nil? (hae-taulukko vastaus "Kirjallisesti sovitut muutokset" :sheet-nimi))
      "Kirjallisesti sovitut -taulukko ei näy vanhalla urakalla")
    (is (nil? (hae-taulukko vastaus "Aiemmilta hoitovuosilta jatkuvat pysyvät muutokset" :otsikko))
      "Aikaisempien vuosien -taulukko ei näy vanhalla urakalla")))

(deftest vanha-urakka-lisatyot-nakyvat
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        urakan-alkupvm (ffirst (q (str "SELECT alkupvm FROM urakka WHERE id = " urakka-id)))
        _ (siivoa-vanha-urakka-raportti-data! urakka-id)
        tpi-id (ffirst (q (str "SELECT id FROM toimenpideinstanssi WHERE urakka = " urakka-id " LIMIT 1")))
        erapaiva (pvm/->pvm (str "15.11.2020"))
        koontilaskun-kuukausi (domain-kulut/pvm->koontilaskun-kuukausi erapaiva urakan-alkupvm)
        ;; Luo kulu
        kulu-id (i (format "INSERT INTO kulu (kokonaissumma, erapaiva, urakka, luoja, koontilaskun_kuukausi)
                            VALUES (%s, '%s', %s, %s, '%s')"
                     7500 erapaiva urakka-id (:id +kayttaja-jvh+) koontilaskun-kuukausi))]
    ;; Luo lisätyö-kohdistus
    (u (str "INSERT INTO kulu_kohdistus (rivi, kulu, summa, toimenpideinstanssi, maksueratyyppi,
                                         luoja, tyyppi, lisatyon_lisatieto)
             VALUES (0, " kulu-id ", 7500, " tpi-id ", 'kokonaishintainen',
                     " (:id +kayttaja-jvh+) ", 'lisatyo', 'Testilisätyö: lumitöiden lisäkustannus')"))
    ;; Suorita raportti
    (let [vastaus (suorita-raportti urakka-id
                    (pvm/hoitokauden-alkupvm 2020)
                    (pvm/hoitokauden-loppupvm 2021))
          taulukko (hae-taulukko-sheet-nimella vastaus "Lisätyöt")]
      ;; Lisätyöt-taulukko löytyy
      (is (some? taulukko) "Lisätyöt-taulukko löytyy vanhalla urakalla")
      (let [rivit (apurit/taulukon-rivit taulukko)
            datarivit (filter vector? rivit)
            yhteensarivi (last rivit)]
        (is (= 2 (count rivit)) "Taulukossa on 2 riviä (1 data + yhteensä)")
        ;; Tarkista datarivin lisätieto ja summa
        (when (seq datarivit)
          (let [rivi (first datarivit)]
            (is (= "Testilisätyö: lumitöiden lisäkustannus" (nth rivi 2)) "Lisätyön lisätieto on oikein")
            (is (= 7500M (nth rivi 3)) "Lisätyön summa on 7500")))
        ;; Tarkista yhteensä
        (is (= 7500M (nth (:rivi yhteensarivi) 3)) "Yhteensä on 7500"))
      ;; Muutoshallinta-osiot EI näy vanhalla urakalla
      (is (nil? (hae-taulukko vastaus "Kirjallisesti sovitut muutokset" :sheet-nimi))
        "Kirjallisesti sovitut -taulukko ei näy vanhalla urakalla"))))
