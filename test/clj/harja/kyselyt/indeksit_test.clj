(ns harja.kyselyt.indeksit-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.indeksit :as indeksipalvelu]
            [harja.kyselyt.indeksit :as indeksi-kyselyt]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :indeksit (component/using
                      (indeksipalvelu/->Indeksit)
                      [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(defn indeksikorjaa
  "Indeksikorjaa samalla tavalla kuin kustannussuunnitelmassa"
  [{:keys [db urakka-id hoitovuosi-nro summa]}]
  (let [urakan-indeksit (indeksi-kyselyt/hae-urakan-indeksikertoimet db urakka-id)
        indeksikerroin (indeksi-kyselyt/indeksikerroin urakan-indeksit hoitovuosi-nro)]
    (bigdec (indeksi-kyselyt/indeksikorjaa indeksikerroin summa))))

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
      (indeksipalvelu/tallenna-indeksi
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
      (indeksipalvelu/tallenna-indeksi
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
      (indeksipalvelu/tallenna-indeksi
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
      (indeksipalvelu/tallenna-indeksi
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
