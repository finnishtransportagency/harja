(ns harja.palvelin.palvelut.indeksit-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.indeksit :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.string :as str]
            [harja.domain.urakka :as urakka]
            [harja.kyselyt.indeksit :as indeksi-kyselyt]
            [harja.palvelin.palvelut.indeksit :as indeksit]
            [harja.palvelin.palvelut.budjettisuunnittelu :as budjettisuunnittelu]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :indeksit (component/using
                      (->Indeksit)
                      [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(defn muuta-vesivaylaurakka
  [urakka-id {:keys [alkupvm loppupvm indeksi]}]
  (u (format
       "UPDATE urakka
           SET tyyppi = 'vesivayla-hoito',
               alkupvm = DATE '%s',
               loppupvm = DATE '%s',
               indeksi = '%s'
         WHERE id = %s"
       alkupvm
       loppupvm
       indeksi
       urakka-id)))

(defn lisaa-indeksit
  [indeksinimi indeksit]
  (doseq [[vuosi kuukausi arvo] indeksit]
    (i (format
         "INSERT INTO indeksi (nimi, vuosi, kuukausi, arvo)
          VALUES ('%s', %s, %s, %s)"
         indeksinimi
         vuosi
         kuukausi
         arvo))))


(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

;; maku 2005 vuonna 2013
;; ["MAKU 2005" 2013] {:vuosi 2013, 12 110.1, 11 110.5, 1 109.2}}

(deftest kaikki-indeksit-haettu-oikein
  (let [indeksit (kutsu-palvelua (:http-palvelin jarjestelma)
                   :indeksit +kayttaja-jvh+)
        maku-2005-2013 (get indeksit ["MAKU 2005" 2013])]
    (is (> (count indeksit) 0))
    (is (= (count maku-2005-2013) 13))
    (is (every? some? maku-2005-2013))
    (is (= (:vuosi maku-2005-2013) 2013))
    (is (= (get maku-2005-2013 12) (float 105.2))))) ;; <- odota ongelmia floatien kanssa



(deftest kuukauden-indeksikorotuksen-laskenta
  (let [korotus
        (ffirst (q (str "SELECT korotus from laske_kuukauden_indeksikorotus
 (2016, 10, 'MAKU 2005', 387800, 135.4, false);")))]
    (is (=marginaalissa? korotus 1145.64))))

(deftest kuukauden-indeksikorotuksen-laskenta-urakalle
  ;; Testataan, että tuleeko eri korotukset, jos urakan alkuvuosi muuttuu. Pidetään muut parametrit samoina.
  (testing "Ennen 2023 alkaneet urakat (käytetään vertailukuukautena syyskuuta)"
    (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          korotus (ffirst (q (str
                               "SELECT korotus from laske_kuukauden_indeksikorotus_urakalle("
                               urakka-id ", 2023, 10, 'MAKU 2020', 1000, 135.4, false);")))]
      (is (=marginaalissa? korotus 39.88))))

  (testing "2023 ja sen jälkeen alkaneet urakat (käytetään vertailukuukautena elokuuta)"
    (let [urakka-id (hae-raahen-maanteiden-hoitourakan-2023-2028-id)
          korotus (ffirst (q (str
                               "SELECT korotus from laske_kuukauden_indeksikorotus_urakalle("
                               urakka-id ", 2023, 10, 'MAKU 2020', 1000, 135.4, false);")))]
      (is (=marginaalissa? korotus 24.37)))))

(deftest indeksikorjaa-apufunktio
  (testing "Ennen 2023 alkaneet urakat (käytetään vertailukuukautena syyskuuta)"
    (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          korotettu (ffirst (q (str "SELECT * FROM indeksikorjaa(1, 2023, 9, " urakka-id ")")))
          testidata-korotettu (ffirst (q (str "SELECT * FROM testidata_indeksikorjaa(1, 2023, 9, " urakka-id ")")))]
      (is (= 1.352M korotettu testidata-korotettu))))
  (testing "2023 ja sen jälkeen alkaneet urakat (käytetään vertailukuukautena elokuuta)"
    (let [urakka-id (hae-raahen-maanteiden-hoitourakan-2023-2028-id)
          korotettu (ffirst (q (str "SELECT * FROM indeksikorjaa(1, 2023, 9, " urakka-id ")")))
          testidata-korotettu (ffirst (q (str "SELECT * FROM testidata_indeksikorjaa(1, 2023, 9, " urakka-id ")")))]
      (is (= 0.986M korotettu testidata-korotettu)))))

(deftest indeksikorjaa-apufunktion-vesivaylaurakan-logiikka
  (let [urakka-id (hae-urakan-id-nimella
                    "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
        indeksinimi "TESTI-VESIVAYLA-TESTIDATA"]

    (muuta-vesivaylaurakka
      urakka-id
      {:alkupvm "2023-10-01"
       :loppupvm "2028-09-30"
       :indeksi indeksinimi})

    ;; Perusluku: vuoden 2022 syys-, loka- ja marraskuun keskiarvo = 100.
    ;; Ensimmäinen urakkavuosi: kesäkuu 2023 = 110.
    ;; Toinen urakkavuosi: elokuu 2024 = 120.
    (lisaa-indeksit
      indeksinimi
      [[2022 9  100]
       [2022 10 100]
       [2022 11 100]
       [2023 6  110]
       [2024 8  120]])

    (testing "Ensimmäinen urakkavuosi käyttää kesäkuuta"
      (let [sql-tulos (ffirst
                        (q (format
                             "SELECT indeksikorjaa(1, 2023, 10, %s)"
                             urakka-id)))
            testidata-tulos (ffirst
                              (q (format
                                   "SELECT testidata_indeksikorjaa(1, 2023, 10, %s)"
                                   urakka-id)))]
        ;; 110 / 100 = 1.100
        (is (= 1.100M sql-tulos))
        (is (= sql-tulos testidata-tulos))))

    (testing "Toinen urakkavuosi käyttää elokuuta"
      (let [sql-tulos (ffirst
                        (q (format
                             "SELECT indeksikorjaa(1, 2024, 10, %s)"
                             urakka-id)))
            testidata-tulos (ffirst
                              (q (format
                                   "SELECT testidata_indeksikorjaa(1, 2024, 10, %s)"
                                   urakka-id)))]
        ;; 120 / 100 = 1.200
        (is (= 1.200M sql-tulos))
        (is (= sql-tulos testidata-tulos))))))

(deftest urakkatyypin-indeksien-haku
  (let [indeksit (kutsu-palvelua (:http-palvelin jarjestelma)
                   :urakkatyypin-indeksit +kayttaja-jvh+)
        {:keys [hoito tiemerkinta paallystys vesivayla-kanavien-hoito]}
        (group-by :urakkatyyppi indeksit)]
    (is (some #(= "MAKU 2005" (:indeksinimi %)) hoito))
    (is (some #(= "MAKU 2010" (:indeksinimi %)) hoito))
    (is (some #(= "MAKU 2015" (:indeksinimi %)) hoito))
    (is (some #(= "MAKU 2020" (:indeksinimi %)) hoito))
    (is (not (some #(= "MAKU 2008" (:indeksinimi %)) hoito))) ;tällaista ei käytetä
    (is (some #(= "MAKU 2010" (:indeksinimi %)) tiemerkinta))
    (is (some #(= "Platts: FO 3,5%S CIF NWE Cargo" (:indeksinimi %)) paallystys))
    (is (some #(= "bitumi" (:raakaaine %)) paallystys))
    (is (some #(= "ABWGL03" (:koodi %)) paallystys))
    (is (some #(str/includes? (:indeksinimi %) "Platts") paallystys))
    (is (some #(= "Palvelujen tuottajahintaindeksi 2010" (:indeksinimi %)) vesivayla-kanavien-hoito))
    (is (some #(= "Palvelujen tuottajahintaindeksi 2015" (:indeksinimi %)) vesivayla-kanavien-hoito))))

(deftest paallystysurakan-indeksitietojen-haku
  (let [indeksit (kutsu-palvelua (:http-palvelin jarjestelma)
                   :paallystysurakan-indeksitiedot
                   +kayttaja-jvh+
                   {::urakka/id 5})]
    (is (= 2 (count indeksit)))
    ;; spec'atun palvelun vastauksen muodollista pätevyyttä ei tarvi tarkistella
    (is (= "Platts: testiindeksi XYZ" (:indeksinimi (:indeksi (first indeksit)))))
    (is (=marginaalissa? 225.0 (:arvo (:indeksi (first indeksit)))))))

(deftest paallystysurakan-indeksitiedot-tallennus
  (let [hyotykuorma [{:id -1 :urakka 5
                      :lahtotason-vuosi 2014 :lahtotason-kuukausi 9
                      :indeksi {:id 8 :urakkatyyppi :paallystys
                                :indeksinimi "Platts: Propane CIF NWE 7kt+"
                                :raakaaine "nestekaasu"
                                :koodi "PMUEE03"}}]
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :tallenna-paallystysurakan-indeksitiedot
                  +kayttaja-jvh+
                  hyotykuorma)]
    ;; Lisättiin yksi, joten nyt indeksejä on kolme
    (is (= 3 (count vastaus)) "indeksivuosien lukumäärä tallennuksen jälkeen")

    (testing "Indeksin merkitseminen poistetuksi"
      (let [hyotykuorma (assoc-in vastaus [0 :poistettu] true)
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysurakan-indeksitiedot
                      +kayttaja-jvh+
                      hyotykuorma)]
        (is (= 2 (count vastaus)) "indeksejä on 2 poiston jälkeen")))))

(deftest laske-vesivaylaurakan-indeksilaskennan-perusluku
  (let [ur (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
        perusluku (ffirst (q (str "select * from indeksilaskennan_perusluku(" ur ");")))]
    ; (103.9+105.2+106.2) / 3 = 105.1M tammi, helmi- ja maaliskuun keskiarvo urakan alkuvuonna
    (is (= 105.1M perusluku))))

(deftest laske-tampereen-2017-alkavan-hoitourakan-indeksilaskennan-perusluku
  (let [ur (hae-urakan-id-nimella "Tampereen alueurakka 2017-2022")
        perusluku (ffirst (q (str "select * from indeksilaskennan_perusluku(" ur ");")))]
    ; alkupvm:ää edeltävän vuoden syys-, loka- ja marraskuun keskiarvo urakan alkuvuonna
    (is (= 115.4M perusluku))))

(defn indeksilaskennan-perusluku [urakka]
  (ffirst (q (format "select * from indeksilaskennan_perusluku(%s)" urakka))))

(defn kiinteahintainen-tyo-summa-indeksikorjattu [id]
  (ffirst (q (format "select summa_indeksikorjattu from kiinteahintainen_tyo where id = %s" id))))

(defn kustannusarvioitu-tyo-summa-indeksikorjattu [id]
  (ffirst (q (format "select summa_indeksikorjattu from kustannusarvioitu_tyo where id = %s" id))))

(defn johto-ja-hallintokorvaus-tuntipalkka-indeksikorjattu [id]
  (ffirst (q (format "select tuntipalkka_indeksikorjattu from johto_ja_hallintokorvaus where id = %s" id))))

(defn urakka-tavoite-tavoitehinta-indeksikorjattu [id]
  (ffirst (q (format "select tavoitehinta_indeksikorjattu from urakka_tavoite where id = %s" id))))

(defn urakka-tavoite-kattohinta-indeksikorjattu [id]
  (ffirst (q (format "select kattohinta_indeksikorjattu from urakka_tavoite where id = %s" id))))

(defn indeksikorjaa
  "Indeksikorjaa samalla tavalla kuin kustannussuunnitelmassa"
  [{:keys [db urakka-id hoitovuosi-nro summa]}]
  (let [urakan-indeksit (budjettisuunnittelu/hae-urakan-indeksikertoimet db +kayttaja-jvh+ {:urakka-id urakka-id})
        indeksikerroin (budjettisuunnittelu/indeksikerroin urakan-indeksit hoitovuosi-nro)]
    (bigdec (budjettisuunnittelu/indeksikorjaa indeksikerroin summa))))

(defn lisaa-kiinteahintainen-tyo [{:keys [vuosi, kuukausi, summa, toimenpideinstanssi]}]
  (i (format "INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi) VALUES (%s, %s, %s, %s)"
       vuosi kuukausi summa toimenpideinstanssi)))

(defn lisaa-kustannusarvioitu-tyo [{:keys [vuosi, kuukausi, summa, toimenpideinstanssi]}]
  (i (format "INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, toimenpideinstanssi) VALUES (%s, %s, %s, %s)"
       vuosi kuukausi summa toimenpideinstanssi)))

(defn lisaa-tilaajan-rahavaraus [{:keys [vuosi, kuukausi, summa, toimenpideinstanssi]}]
  (i (format "INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, tehtavaryhma) VALUES (%s, %s, %s, %s, (select id from tehtavaryhma tr where tr.yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54'))"
       vuosi kuukausi summa toimenpideinstanssi)))

(defn lisaa-johto-ja-hallintokorvaus [{:keys [vuosi, kuukausi, tuntipalkka, urakka]}]
  (i (format "INSERT INTO johto_ja_hallintokorvaus (\"urakka-id\", tuntipalkka, vuosi, kuukausi, \"toimenkuva-id\") VALUES (%s, %s, %s, %s, (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'harjoittelija' AND \"urakka-id\" IS NULL))"
       urakka tuntipalkka vuosi kuukausi)))

(defn lisaa-urakka-tavoite [{:keys [urakka hoitokausi tavoitehinta kattohinta]}]
  (println "lisaa-urakka-tavoite" urakka hoitokausi tavoitehinta kattohinta)
  (println (format "INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, kattohinta) VALUES (%s, %s, %s, %s)"
             urakka hoitokausi tavoitehinta kattohinta))
  (u (format "DELETE FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s"
       urakka hoitokausi))
  (i (format "INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, kattohinta) VALUES (%s, %s, %s, %s)"
       urakka hoitokausi tavoitehinta kattohinta)))

(deftest indeksikorjaukset-lasketaan-uudelleen-kun-indeksia-muokataan
  (let [db (:db jarjestelma)
        urakka (hae-urakan-id-nimella "Kittilän MHU 2019-2024")
        indeksi "TESTI-INDEKSI 2015"]
    ;; Päivitä Kittilän testiurakka käyttämään tämän testin indeksiä
    (is (= 1 (u (format "update urakka set indeksi = '%s' where id = %s" indeksi urakka))))

    (is (nil? (indeksilaskennan-perusluku urakka))
      "Indeksilaskennan peruslukua ei voi vielä laskea, koska indeksejä ei ole")

    (let [summa 70979.86M
          toimenpideinstanssi (hae-kittila-mhu-talvihoito-tpi-id)
          kiinteahintainen-tyo (lisaa-kiinteahintainen-tyo
                                 {:vuosi 2020 :kuukausi 10 :summa summa :toimenpideinstanssi toimenpideinstanssi})
          kustannusarvioitu-tyo (lisaa-kustannusarvioitu-tyo
                                  {:vuosi 2020 :kuukausi 10 :summa summa :toimenpideinstanssi toimenpideinstanssi})
          tilaajan-rahavaraus (lisaa-tilaajan-rahavaraus
                                {:vuosi 2020 :kuukausi 10 :summa summa
                                 :toimenpideinstanssi (hae-kittila-mhu-hallinnolliset-toimenpiteet-tp-id)})
          johto-ja-hallintokorvaus (lisaa-johto-ja-hallintokorvaus
                                     {:vuosi 2020 :kuukausi 10 :tuntipalkka summa :urakka urakka})
          tavoitehinta summa
          kattohinta (+ summa 2)
          urakka-tavoite (lisaa-urakka-tavoite
                           {:urakka urakka
                            :hoitokausi 2
                            :tavoitehinta tavoitehinta
                            :kattohinta kattohinta})]
      (is (number? kiinteahintainen-tyo))
      (is (number? kustannusarvioitu-tyo))
      (is (number? tilaajan-rahavaraus))
      (is (number? johto-ja-hallintokorvaus))
      (is (number? urakka-tavoite))

      ;; Lisää 2018 syys-, loka- ja marraskuun indeksit indeksin peruslukua varten
      (indeksit/tallenna-indeksi
        db
        +kayttaja-jvh+
        {:nimi indeksi
         :indeksit [{:kannassa? false
                     :vuosi 2018
                     9 101.13
                     10 101.68
                     11 101.8}]})
      (is (= 101.5M (indeksilaskennan-perusluku urakka))
        "Indeksilaskennan perusluku on urakan alkupvm:ää edeltävän vuoden syys-, loka- ja marraskuun keskiarvo")
      (is (nil? (kiinteahintainen-tyo-summa-indeksikorjattu kiinteahintainen-tyo))
        "kiinteahintainen_tyo.summa_indeksikorjattu voidaan laskea vasta kun saadaan syyskuun 2019 indeksi")
      (is (nil? (kustannusarvioitu-tyo-summa-indeksikorjattu kustannusarvioitu-tyo))
        "kustannusarvioitu_tyo.summa_indeksikorjattu voidaan laskea vasta kun saadaan syyskuun 2019 indeksi")
      (is (nil? (kustannusarvioitu-tyo-summa-indeksikorjattu tilaajan-rahavaraus))
        "tilaajan rahavaraukselle ei lasketa indeksikorjausta")
      (is (nil? (johto-ja-hallintokorvaus-tuntipalkka-indeksikorjattu johto-ja-hallintokorvaus))
        "johto_ja_hallintokorvaus.tuntipalkka_indeksikorjattu voidaan laskea vasta kun saadaan syyskuun 2019 indeksi")
      (is (nil? (urakka-tavoite-tavoitehinta-indeksikorjattu urakka-tavoite))
        "urakka_tavoite.tavoitehinta_indeksikorjattu voidaan laskea vasta kun saadaan syyskuun 2019 indeksi")
      (is (nil? (urakka-tavoite-kattohinta-indeksikorjattu urakka-tavoite))
        "urakka_tavoite.kattohinta_indeksikorjattu voidaan laskea vasta kun saadaan syyskuun 2019 indeksi")

      ;; Lisää syyskuun 2019 ja 2020 indeksit, jotta voidaan laskea lokakuun indeksikorjaus
      (indeksit/tallenna-indeksi
        db
        +kayttaja-jvh+
        {:nimi indeksi
         :indeksit [{:kannassa? false
                     :vuosi 2019
                     9 102.49M}
                    {:kannassa? false
                     :vuosi 2020
                     9 102.97M}]})
      (let [indeksikorjattu-summa (indeksikorjaa {:db db :urakka-id urakka :hoitovuosi-nro 2 :summa summa})]
        (is (= indeksikorjattu-summa ; CLJ-indeksikorjaus
              (kiinteahintainen-tyo-summa-indeksikorjattu kiinteahintainen-tyo)) ; SQL-indeksikorjaus
          "kiinteahintainen_tyo.summa_indeksikorjattu on laskettu indeksin lisäämisen jälkeen")
        (is (= indeksikorjattu-summa
              (kustannusarvioitu-tyo-summa-indeksikorjattu kustannusarvioitu-tyo))
          "kustannusarvioitu_tyo.summa_indeksikorjattu on laskettu indeksin lisäämisen jälkeen")
        (is (nil? (kustannusarvioitu-tyo-summa-indeksikorjattu tilaajan-rahavaraus))
          "tilaajan rahavaraukselle ei lasketa indeksikorjausta")
        (is (= indeksikorjattu-summa
              (johto-ja-hallintokorvaus-tuntipalkka-indeksikorjattu johto-ja-hallintokorvaus))
          "johto_ja_hallintokorvaus.tuntipalkka_indeksikorjattu on laskettu indeksin lisäämisen jälkeen")
        (is (= (indeksikorjaa {:db db :urakka-id urakka :hoitovuosi-nro 2 :summa tavoitehinta})
              (urakka-tavoite-tavoitehinta-indeksikorjattu urakka-tavoite))
          "urakka_tavoite.tavoitehinta_indeksikorjattu on laskettu indeksin lisäämisen jälkeen")
        (is (= (indeksikorjaa {:db db :urakka-id urakka :hoitovuosi-nro 2 :summa kattohinta})
              (urakka-tavoite-kattohinta-indeksikorjattu urakka-tavoite))
          "urakka_tavoite.kattohinta_indeksikorjattu on laskettu indeksin lisäämisen jälkeen"))

      ;; Päivitä indeksiä
      (indeksit/tallenna-indeksi
        db
        +kayttaja-jvh+
        {:nimi indeksi
         :indeksit [{:kannassa? true
                     :vuosi 2020
                     9 666.66666666M}]})
      (let [indeksikorjattu-summa (indeksikorjaa {:db db :urakka-id urakka :hoitovuosi-nro 2 :summa summa})]
        (is (= indeksikorjattu-summa
              (kiinteahintainen-tyo-summa-indeksikorjattu kiinteahintainen-tyo))
          "kiinteahintainen_tyo.summa_indeksikorjattu on laskettu uusiksi indeksin muokkaamisen jälkeen")
        (is (= indeksikorjattu-summa
              (kustannusarvioitu-tyo-summa-indeksikorjattu kustannusarvioitu-tyo))
          "kustannusarvioitu_tyo.summa_indeksikorjattu on laskettu uusiksi indeksin muokkaamisen jälkeen")
        (is (nil? (kustannusarvioitu-tyo-summa-indeksikorjattu tilaajan-rahavaraus))
          "tilaajan rahavaraukselle ei lasketa indeksikorjausta")
        (is (= indeksikorjattu-summa
              (johto-ja-hallintokorvaus-tuntipalkka-indeksikorjattu johto-ja-hallintokorvaus))
          "johto_ja_hallintokorvaus.tuntipalkka_indeksikorjattu on laskettu uusiksi indeksin muokkaamisen jälkeen")
        (is (= (indeksikorjaa {:db db :urakka-id urakka :hoitovuosi-nro 2 :summa tavoitehinta})
              (urakka-tavoite-tavoitehinta-indeksikorjattu urakka-tavoite))
          "urakka_tavoite.tavoitehinta_indeksikorjattu on laskettu uusiksi indeksin muokkaamisen jälkeen")
        (is (= (indeksikorjaa {:db db :urakka-id urakka :hoitovuosi-nro 2 :summa kattohinta})
              (urakka-tavoite-kattohinta-indeksikorjattu urakka-tavoite))
          "urakka_tavoite.kattohinta_indeksikorjattu on laskettu uusiksi indeksin muokkaamisen jälkeen"))

      ;; Poista indeksi
      (indeksit/tallenna-indeksi
        db
        +kayttaja-jvh+
        {:nimi indeksi
         :indeksit [{:kannassa? true
                     :vuosi 2020
                     9 nil}]})
      (is (nil? (kiinteahintainen-tyo-summa-indeksikorjattu kiinteahintainen-tyo))
        "kiinteahintainen_tyo.summa_indeksikorjattu on poistettu indeksin poistamisen jälkeen")
      (is (nil? (kustannusarvioitu-tyo-summa-indeksikorjattu kustannusarvioitu-tyo))
        "kustannusarvioitu_tyo.summa_indeksikorjattu on poistettu indeksin poistamisen jälkeen")
      (is (nil? (kustannusarvioitu-tyo-summa-indeksikorjattu tilaajan-rahavaraus))
        "tilaajan rahavaraukselle ei lasketa indeksikorjausta")
      (is (nil? (johto-ja-hallintokorvaus-tuntipalkka-indeksikorjattu johto-ja-hallintokorvaus))
        "johto_ja_hallintokorvaus.tuntipalkka_indeksikorjattu on poistettu indeksin poistamisen jälkeen")
      (is (nil? (urakka-tavoite-tavoitehinta-indeksikorjattu urakka-tavoite))
        "urakka_tavoite.tavoitehinta_indeksikorjattu on poistettu indeksin poistamisen jälkeen")
      (is (nil? (urakka-tavoite-kattohinta-indeksikorjattu urakka-tavoite))
        "urakka_tavoite.kattohinta_indeksikorjattu on poistettu indeksin poistamisen jälkeen"))))

(deftest vesivaylaurakan-indeksikorjaukset-paivitetaan-indeksin-muutoksessa
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella
                    "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
        indeksi "TESTI-VESIVAYLA-INTEGRAATIO"
        toimenpideinstanssi (ffirst
                              (q (format
                                   "SELECT id
                                      FROM toimenpideinstanssi
                                     WHERE urakka = %s
                                     LIMIT 1"
                                   urakka-id)))

        ;; Ensimmäisen vesiväyläurakkavuoden lokakuun kustannusrivit.
        kiinteahintainen-tyo-1 (lisaa-kiinteahintainen-tyo
                                {:vuosi 2023
                                 :kuukausi 10
                                 :summa 100M
                                 :toimenpideinstanssi toimenpideinstanssi})

        kustannusarvioitu-tyo-1 (lisaa-kustannusarvioitu-tyo
                                  {:vuosi 2023
                                   :kuukausi 10
                                   :summa 100M
                                   :toimenpideinstanssi toimenpideinstanssi})

        johto-ja-hallintokorvaus-1 (lisaa-johto-ja-hallintokorvaus
                                    {:vuosi 2023
                                     :kuukausi 10
                                     :tuntipalkka 100M
                                     :urakka urakka-id})

        ;; Toisen vesiväyläurakkavuoden lokakuun kustannusrivit.
        kiinteahintainen-tyo-2 (lisaa-kiinteahintainen-tyo
                                {:vuosi 2024
                                 :kuukausi 10
                                 :summa 100M
                                 :toimenpideinstanssi toimenpideinstanssi})

        kustannusarvioitu-tyo-2 (lisaa-kustannusarvioitu-tyo
                                  {:vuosi 2024
                                   :kuukausi 10
                                   :summa 100M
                                   :toimenpideinstanssi toimenpideinstanssi})

        johto-ja-hallintokorvaus-2 (lisaa-johto-ja-hallintokorvaus
                                    {:vuosi 2024
                                     :kuukausi 10
                                     :tuntipalkka 100M
                                     :urakka urakka-id})

        ;; Ensimmäinen urakkavuosi: 1.10.2023–30.9.2024.
        tavoitehinta-1 (lisaa-urakka-tavoite
                        {:urakka urakka-id
                         :hoitokausi 1
                         :tavoitehinta 100M
                         :kattohinta 120M})

        ;; Toinen urakkavuosi: 1.10.2024–30.9.2025.
        tavoitehinta-2 (lisaa-urakka-tavoite
                        {:urakka urakka-id
                         :hoitokausi 2
                         :tavoitehinta 100M
                         :kattohinta 120M})]

    (is (some? toimenpideinstanssi)
      "Vesiväyläurakalle pitää löytyä toimenpideinstanssi")

    (u (format
         "UPDATE urakka
             SET tyyppi = 'vesivayla-hoito',
                 alkupvm = DATE '2023-10-01',
                 loppupvm = DATE '2028-09-30',
                 indeksi = '%s'
           WHERE id = %s"
         indeksi
         urakka-id))

    ;; Ennen indeksitietojen lisäämistä mitään indeksikorjausta ei voida laskea.
    (is (nil? (kiinteahintainen-tyo-summa-indeksikorjattu
                kiinteahintainen-tyo-1)))
    (is (nil? (kiinteahintainen-tyo-summa-indeksikorjattu
                kiinteahintainen-tyo-2)))
    (is (nil? (urakka-tavoite-tavoitehinta-indeksikorjattu
                tavoitehinta-1)))
    (is (nil? (urakka-tavoite-tavoitehinta-indeksikorjattu
                tavoitehinta-2)))

    ;; Perusluku = (100 + 100 + 100) / 3 = 100.
    ;; Vertailukuukaudet puuttuvat vielä, joten kustannuksia ei voida korjata.
    (indeksit/tallenna-indeksi
      db
      +kayttaja-jvh+
      {:nimi indeksi
       :indeksit [{:kannassa? false
                   :vuosi 2022
                   9 100
                   10 100
                   11 100}]})

    (is (= 100M
          (indeksilaskennan-perusluku urakka-id)))

    (is (nil? (kiinteahintainen-tyo-summa-indeksikorjattu
                kiinteahintainen-tyo-1)))
    (is (nil? (kiinteahintainen-tyo-summa-indeksikorjattu
                kiinteahintainen-tyo-2)))
    (is (nil? (urakka-tavoite-tavoitehinta-indeksikorjattu
                tavoitehinta-1)))
    (is (nil? (urakka-tavoite-tavoitehinta-indeksikorjattu
                tavoitehinta-2)))

    ;; Ensimmäisen urakkavuoden vertailuindeksi on kesäkuu 2023.
    ;; 110 / 100 = 1.100.
    (indeksit/tallenna-indeksi
      db
      +kayttaja-jvh+
      {:nimi indeksi
       :indeksit [{:kannassa? false
                   :vuosi 2023
                   6 110}]})

    ;; Ensimmäisen hoitokauden kaikki kustannustyypit päivittyvät.
    (is (= 110M
          (kiinteahintainen-tyo-summa-indeksikorjattu
            kiinteahintainen-tyo-1)))

    (is (= 110M
          (kustannusarvioitu-tyo-summa-indeksikorjattu
            kustannusarvioitu-tyo-1)))

    (is (= 110M
          (johto-ja-hallintokorvaus-tuntipalkka-indeksikorjattu
            johto-ja-hallintokorvaus-1)))

    (is (= 110M
          (urakka-tavoite-tavoitehinta-indeksikorjattu
            tavoitehinta-1)))

    (is (= 132M
          (urakka-tavoite-kattohinta-indeksikorjattu
            tavoitehinta-1)))

    ;; Toinen hoitokausi ei vielä saa indeksikorjausta, koska elokuun 2024
    ;; vertailuindeksiä ei ole tallennettu.
    (is (nil? (kiinteahintainen-tyo-summa-indeksikorjattu
                kiinteahintainen-tyo-2)))

    (is (nil? (kustannusarvioitu-tyo-summa-indeksikorjattu
                kustannusarvioitu-tyo-2)))

    (is (nil? (johto-ja-hallintokorvaus-tuntipalkka-indeksikorjattu
                johto-ja-hallintokorvaus-2)))

    (is (nil? (urakka-tavoite-tavoitehinta-indeksikorjattu
                tavoitehinta-2)))

    ;; Toisen urakkavuoden vertailuindeksi on elokuu 2024.
    ;; 120 / 100 = 1.200.
    (indeksit/tallenna-indeksi
      db
      +kayttaja-jvh+
      {:nimi indeksi
       :indeksit [{:kannassa? false
                   :vuosi 2024
                   8 120}]})

    ;; Ensimmäisen hoitokauden arvot säilyvät ennallaan.
    (is (= 110M
          (kiinteahintainen-tyo-summa-indeksikorjattu
            kiinteahintainen-tyo-1)))

    (is (= 110M
          (kustannusarvioitu-tyo-summa-indeksikorjattu
            kustannusarvioitu-tyo-1)))

    (is (= 110M
          (johto-ja-hallintokorvaus-tuntipalkka-indeksikorjattu
            johto-ja-hallintokorvaus-1)))

    (is (= 110M
          (urakka-tavoite-tavoitehinta-indeksikorjattu
            tavoitehinta-1)))

    ;; Toisen hoitokauden kustannukset käyttävät elokuun 2024 indeksiä.
    (is (= 120M
          (kiinteahintainen-tyo-summa-indeksikorjattu
            kiinteahintainen-tyo-2)))

    (is (= 120M
          (kustannusarvioitu-tyo-summa-indeksikorjattu
            kustannusarvioitu-tyo-2)))

    (is (= 120M
          (johto-ja-hallintokorvaus-tuntipalkka-indeksikorjattu
            johto-ja-hallintokorvaus-2)))

    (is (= 120M
          (urakka-tavoite-tavoitehinta-indeksikorjattu
            tavoitehinta-2)))

    (is (= 144M
          (urakka-tavoite-kattohinta-indeksikorjattu
            tavoitehinta-2)))))

(deftest testaa-hae-vertailu-kk-apufunktio
  (testing "MHU-urakan vertailukuukausi"
    (is (= 9 (indeksi-kyselyt/hae-vertailu-kk "hoito" 2019 2019)))
    (is (= 8 (indeksi-kyselyt/hae-vertailu-kk "hoito" 2023 2023))))

  (testing "vesiväyläurakan ensimmäinen urakkavuosi"
    (is (= 6 (indeksi-kyselyt/hae-vertailu-kk "vesivayla-hoito" 2023 2023))))

  (testing "vesiväyläurakan myöhemmät urakkavuodet"
    (is (= 8 (indeksi-kyselyt/hae-vertailu-kk "vesivayla-hoito" 2023 2024)))))

(deftest vahvistettua-indeksikorjausta-ei-muokata
  (let [db (:db jarjestelma)
        urakka (hae-urakan-id-nimella "Kittilän MHU 2019-2024")
        indeksi "TESTI-INDEKSI 2015"]
    ;; Päivitä Kittilän testiurakka käyttämään tämän testin indeksiä
    (is (= 1 (u (format "update urakka set indeksi = '%s' where id = %s" indeksi urakka))))

    ;; Lisää vahvistettu kiinteähintainen työ urakan ensimmäiselle kuukaudelle
    (let [summa 70979.86M
          kiinteahintainen-tyo (i (format "INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, summa_indeksikorjattu, indeksikorjaus_vahvistettu) VALUES (2019, 10, %s, %s, %s, NOW())"
                                    summa
                                    (hae-kittila-mhu-talvihoito-tpi-id)
                                    summa))]

      ;; Lisää 2018 syys-, loka- ja marraskuun indeksit indeksin peruslukua varten
      ;; Lisää syyskuun 2019 indeksi, jotta voidaan laskea lokakuun indeksikorjaus
      (indeksit/tallenna-indeksi
        db
        +kayttaja-jvh+
        {:nimi indeksi
         :indeksit [{:kannassa? false
                     :vuosi 2018
                     9 101.1
                     10 101.6
                     11 101.8}
                    {:kannassa? false
                     :vuosi 2019
                     9 102.9M}]})
      (is (= 101.5M (indeksilaskennan-perusluku urakka))
        "Indeksilaskennan perusluku on urakan alkupvm:ää edeltävän vuoden syys-, loka- ja marraskuun keskiarvo")
      (is (= summa (kiinteahintainen-tyo-summa-indeksikorjattu kiinteahintainen-tyo))
        "Vahvistettua indeksikorjattua summaa ei saa muuttaa"))))

(deftest maku-2015-laitetaan-2023-urakalle
  (let [urakka (first (q-map "SELECT * FROM urakka WHERE nimi = 'Raahen MHU 2023-2028'"))]
    (is (= (:indeksi urakka) "MAKU 2015"))))

(deftest indeksilaskennan-perusluku-2023-alkavalle-vesivaylaurakalle
  (let [urakka-id (hae-urakan-id-nimella
                    "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
        indeksinimi "TESTI-VESIVAYLA-PERUSLUKU-2023"]

    ;; Muutetaan urakka alkamaan vuonna 2023.
    (muuta-vesivaylaurakka
      urakka-id
      {:alkupvm "2023-10-01"
       :loppupvm "2028-09-30"
       :indeksi indeksinimi})

    ;; Vuonna 2023 alkavan vesiväyläurakan perusluku:
    ;; vuoden 2022 syys-, loka- ja marraskuun keskiarvo.
    (lisaa-indeksit
      indeksinimi
      [[2022 9  100]
       [2022 10 110]
       [2022 11 120]])

    ;; (100 + 110 + 120) / 3 = 110
    (is (= 110.0M
          (indeksilaskennan-perusluku urakka-id)))))

(deftest indeksikorjaa-1.8.-alkava-vesivayla-urakka
  (let [urakka-id (hae-urakan-id-nimella
                    "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
        indeksinimi "TESTI-VESIVAYLA-2023"]

    ;; Muutetaan testissä urakka alkamaan 1.8.2023.
    ;; Loppupäivä on aina 30.9.
    (muuta-vesivaylaurakka
      urakka-id
      {:alkupvm "2023-08-01"
       :loppupvm "2028-09-30"
       :indeksi indeksinimi})

    ;; Perusluku: 2022 syys-, loka- ja marraskuu.
    ;; Keskiarvo = 100.
    (lisaa-indeksit
      indeksinimi
      [[2022 9  100]   ;; perusluku
       [2022 10 100]
       [2022 11 100]
       [2023 6  110]   ;; ensimmäinen urakkavuosi
       [2024 8  120]])  ;; toinen urakkavuosi

    (testing "Elokuu 2023 kuuluu ensimmäiseen urakkavuoteen"
      (let [sql-tulos
            (ffirst
              (q (format
                   "SELECT indeksikorjaa(1, 2023, 8, %s)"
                   urakka-id)))

            testidata-tulos
            (ffirst
              (q (format
                   "SELECT testidata_indeksikorjaa(1, 2023, 8, %s)"
                   urakka-id)))]

        ;; 110 / 100 = 1.100
        (is (= 1.100M sql-tulos))
        (is (= sql-tulos testidata-tulos))))

    (testing "Syyskuu 2024 kuuluu edelleen ensimmäiseen urakkavuoteen"
      (let [sql-tulos
            (ffirst
              (q (format
                   "SELECT indeksikorjaa(1, 2024, 9, %s)"
                   urakka-id)))

            testidata-tulos
            (ffirst
              (q (format
                   "SELECT testidata_indeksikorjaa(1, 2024, 9, %s)"
                   urakka-id)))]

        ;; 1.8.2023 alkaneen urakan ensimmäinen vuosi jatkuu
        ;; seuraavan vuoden syyskuun loppuun.
        ;; 110 / 100 = 1.100
        (is (= 1.100M sql-tulos))
        (is (= sql-tulos testidata-tulos))))

    (testing "Lokakuu 2024 aloittaa toisen urakkavuoden"
      (let [sql-tulos
            (ffirst
              (q (format
                   "SELECT indeksikorjaa(1, 2024, 10, %s)"
                   urakka-id)))

            testidata-tulos
            (ffirst
              (q (format
                   "SELECT testidata_indeksikorjaa(1, 2024, 10, %s)"
                   urakka-id)))]

        ;; Toinen urakkavuosi käyttää elokuuta 2024.
        ;; 120 / 100 = 1.200
        (is (= 1.200M sql-tulos))
        (is (= sql-tulos testidata-tulos))))))

(deftest indeksikorjaa-1.10.-alkava-vesivaylaurakka
  (let [urakka-id (hae-urakan-id-nimella
                    "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
        indeksinimi "TESTI-VESIVAYLA-2025-10"]

    ;; Muutetaan testissä urakka alkamaan 1.10.2025.
    (muuta-vesivaylaurakka
      urakka-id
      {:alkupvm "2025-10-01"
       :loppupvm "2030-09-30"
       :indeksi indeksinimi})

    ;; Vuoden 2024 syys-, loka- ja marraskuu tarvitaan peruslukuun.
    ;; Keskiarvo = 100.
    (lisaa-indeksit
      indeksinimi
      [[2024 9  100]   ; perusluku
       [2024 10 100]
       [2024 11 100]
       [2025 6  110]   ; ensimmäinen urakkavuosi
       [2026 8  120]   ; toinen urakkavuosi
       [2027 8  130]])

    (testing "1.10.2025 alkaneen urakan ensimmäinen urakkavuosi käyttää kesäkuuta 2025"
      ;; Lokakuu 2025 kuuluu ensimmäiseen urakkavuoteen.
      (is (= 1.100M
            (ffirst
              (q (format
                   "SELECT indeksikorjaa(1, 2025, 10, %s)"
                   urakka-id)))))

      ;; Syyskuu 2026 kuuluu edelleen ensimmäiseen urakkavuoteen.
      (is (= 1.100M
            (ffirst
              (q (format
                   "SELECT indeksikorjaa(1, 2026, 9, %s)"
                   urakka-id))))))

    (testing "Toinen urakkavuosi käyttää elokuuta 2026"
      ;; Lokakuu 2026 aloittaa toisen urakkavuoden.
      (is (= 1.200M
            (ffirst
              (q (format
                   "SELECT indeksikorjaa(1, 2026, 10, %s)"
                   urakka-id))))))))

(deftest hae-urakan-indeksikertoimet-vesivaylaurakalle
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella
                    "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
        indeksinimi "TESTI-VESIVAYLA-KERTOIMET"]

    ;; Muutetaan urakka alkamaan 1.10.2023.
    (muuta-vesivaylaurakka
      urakka-id
      {:alkupvm "2023-10-01"
       :loppupvm "2028-09-30"
       :indeksi indeksinimi})

    ;; Perusluvun muodostamiseen tarvitaan vuoden 2022
    ;; syys-, loka- ja marraskuun indeksit.
    (lisaa-indeksit
      indeksinimi
      [[2022 9  100]
       [2022 10 100]
       [2022 11 100]
       [2023 6  110]   ; ensimmäinen urakkavuosi
       [2024 8  120]   ; toinen urakkavuosi
       [2025 8  130]])

    (let [indeksikertoimet (harja.kyselyt.indeksit/hae-urakan-indeksikertoimet
                             db urakka-id)]

      ;; Perusluku = (100 + 100 + 100) / 3 = 100.
      ;; Ensimmäinen kerroin = 110 / 100 = 1.100.
      ;; Toinen kerroin = 120 / 100 = 1.200.
      ;; Kolmas kerroin = 130 / 100 = 1.300.
      (is (= 1.1 (:indeksikerroin (nth indeksikertoimet 0))))
      (is (= 1.2 (:indeksikerroin (nth indeksikertoimet 1))))
      (is (= 1.3 (:indeksikerroin (nth indeksikertoimet 2)))))))
