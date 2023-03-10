(ns harja.palvelin.palvelut.indeksit-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.indeksit :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.string :as str]
            [harja.domain.urakka :as urakka]
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



;; HAR-4035 bugin verifiointi
(deftest kuukauden-indeksikorotuksen-laskenta
  (let [korotus
        (ffirst (q (str "SELECT korotus from laske_kuukauden_indeksikorotus
 (2016, 10, 'MAKU 2005', 387800, 135.4);")))]
    (is (=marginaalissa? korotus 1145.64))))

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

(defn urakka-tavoite-tavoitehinta-siirretty-indeksikorjattu [id]
  (ffirst (q (format "select tavoitehinta_siirretty_indeksikorjattu from urakka_tavoite where id = %s" id))))

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
  (i (format "INSERT INTO johto_ja_hallintokorvaus (\"urakka-id\", tuntipalkka, vuosi, kuukausi, \"toimenkuva-id\") VALUES (%s, %s, %s, %s, (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'harjoittelija'))"
             urakka tuntipalkka vuosi kuukausi)))

(defn lisaa-urakka-tavoite [{:keys [urakka hoitokausi tavoitehinta tavoitehinta-siirretty kattohinta]}]
  (println "lisaa-urakka-tavoite" urakka hoitokausi tavoitehinta tavoitehinta-siirretty kattohinta)
  (println (format "INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, tavoitehinta_siirretty, kattohinta) VALUES (%s, %s, %s, %s, %s)"
             urakka hoitokausi tavoitehinta tavoitehinta-siirretty kattohinta))
  (u (format "DELETE FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s"
       urakka hoitokausi))
  (i (format "INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, tavoitehinta_siirretty, kattohinta) VALUES (%s, %s, %s, %s, %s)"
       urakka hoitokausi tavoitehinta tavoitehinta-siirretty kattohinta)))

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
          tavoitehinta-siirretty (+ summa 1)
          kattohinta (+ summa 2)
          urakka-tavoite (lisaa-urakka-tavoite
                           {:urakka urakka
                            :hoitokausi 2
                            :tavoitehinta tavoitehinta
                            :tavoitehinta-siirretty tavoitehinta-siirretty
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
        {:nimi     indeksi
         :indeksit [{:kannassa? false
                     :vuosi     2018
                     9          101.1
                     10         101.6
                     11         101.8}]})
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
      (is (nil? (urakka-tavoite-tavoitehinta-siirretty-indeksikorjattu urakka-tavoite))
          "urakka_tavoite.tavoitehinta_siirretty_indeksikorjattu voidaan laskea vasta kun saadaan syyskuun 2019 indeksi")
      (is (nil? (urakka-tavoite-kattohinta-indeksikorjattu urakka-tavoite))
          "urakka_tavoite.kattohinta_indeksikorjattu voidaan laskea vasta kun saadaan syyskuun 2019 indeksi")

      ;; Lisää syyskuun 2019 ja 2020 indeksit, jotta voidaan laskea lokakuun indeksikorjaus
      (indeksit/tallenna-indeksi
        db
        +kayttaja-jvh+
        {:nimi     indeksi
         :indeksit [{:kannassa? false
                     :vuosi     2019
                     9          102.4M}
                    {:kannassa? false
                     :vuosi     2020
                     9          102.9M}]})
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
        (is (= (indeksikorjaa {:db db :urakka-id urakka :hoitovuosi-nro 2 :summa tavoitehinta-siirretty})
               (urakka-tavoite-tavoitehinta-siirretty-indeksikorjattu urakka-tavoite))
            "urakka_tavoite.tavoitehinta_siirretty_indeksikorjattu on laskettu indeksin lisäämisen jälkeen")
        (is (= (indeksikorjaa {:db db :urakka-id urakka :hoitovuosi-nro 2 :summa kattohinta})
               (urakka-tavoite-kattohinta-indeksikorjattu urakka-tavoite))
            "urakka_tavoite.kattohinta_indeksikorjattu on laskettu indeksin lisäämisen jälkeen"))

      ;; Päivitä indeksiä
      (indeksit/tallenna-indeksi
        db
        +kayttaja-jvh+
        {:nimi     indeksi
         :indeksit [{:kannassa? true
                     :vuosi     2020
                     9          666.66666666M}]})
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
        (is (= (indeksikorjaa {:db db :urakka-id urakka :hoitovuosi-nro 2 :summa tavoitehinta-siirretty})
               (urakka-tavoite-tavoitehinta-siirretty-indeksikorjattu urakka-tavoite))
            "urakka_tavoite.tavoitehinta_siirretty_indeksikorjattu on laskettu uusiksi indeksin muokkaamisen jälkeen")
        (is (= (indeksikorjaa {:db db :urakka-id urakka :hoitovuosi-nro 2 :summa kattohinta})
               (urakka-tavoite-kattohinta-indeksikorjattu urakka-tavoite))
            "urakka_tavoite.kattohinta_indeksikorjattu on laskettu uusiksi indeksin muokkaamisen jälkeen"))

      ;; Poista indeksi
      (indeksit/tallenna-indeksi
        db
        +kayttaja-jvh+
        {:nimi     indeksi
         :indeksit [{:kannassa? true
                     :vuosi     2020
                     9          nil}]})
      (is (nil? (kiinteahintainen-tyo-summa-indeksikorjattu kiinteahintainen-tyo))
          "kiinteahintainen_tyo.summa_indeksikorjattu on poistettu indeksin poistamisen jälkeen")
      (is (nil? (kustannusarvioitu-tyo-summa-indeksikorjattu kiinteahintainen-tyo))
          "kustannusarvioitu_tyo.summa_indeksikorjattu on poistettu indeksin poistamisen jälkeen")
      (is (nil? (kustannusarvioitu-tyo-summa-indeksikorjattu tilaajan-rahavaraus))
          "tilaajan rahavaraukselle ei lasketa indeksikorjausta")
      (is (nil? (johto-ja-hallintokorvaus-tuntipalkka-indeksikorjattu johto-ja-hallintokorvaus))
          "johto_ja_hallintokorvaus.tuntipalkka_indeksikorjattu on poistettu indeksin poistamisen jälkeen")
      (is (nil? (urakka-tavoite-tavoitehinta-indeksikorjattu urakka-tavoite))
          "urakka_tavoite.tavoitehinta_indeksikorjattu on poistettu indeksin poistamisen jälkeen")
      (is (nil? (urakka-tavoite-tavoitehinta-siirretty-indeksikorjattu urakka-tavoite))
          "urakka_tavoite.tavoitehinta_siirretty_indeksikorjattu on poistettu indeksin poistamisen jälkeen")
      (is (nil? (urakka-tavoite-kattohinta-indeksikorjattu urakka-tavoite))
          "urakka_tavoite.kattohinta_indeksikorjattu on poistettu indeksin poistamisen jälkeen"))))

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
        {:nimi     indeksi
         :indeksit [{:kannassa? false
                     :vuosi     2018
                     9          101.1
                     10         101.6
                     11         101.8}
                    {:kannassa? false
                     :vuosi     2019
                     9          102.9M}]})
      (is (= 101.5M (indeksilaskennan-perusluku urakka))
          "Indeksilaskennan perusluku on urakan alkupvm:ää edeltävän vuoden syys-, loka- ja marraskuun keskiarvo")
      (is (= summa (kiinteahintainen-tyo-summa-indeksikorjattu kiinteahintainen-tyo))
          "Vahvistettua indeksikorjattua summaa ei saa muuttaa"))))

(deftest maku-2020-laitetaan-2023-urakalle
  (let [urakka (first (q-map "SELECT * FROM urakka WHERE nimi = 'Raahen MHU 2023-2028'"))]
    (is (= (:indeksi urakka) "MAKU 2020"))))
