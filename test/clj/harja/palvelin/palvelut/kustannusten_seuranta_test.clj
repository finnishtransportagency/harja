(ns harja.palvelin.palvelut.kustannusten-seuranta-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.kustannusten-seuranta :as kustannusten-seuranta]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.set :as set]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :db-replica (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :kustannusten-seuranta (component/using
                                             (kustannusten-seuranta/->KustannustenSeuranta)
                                             [:http-palvelin :db :db-replica])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn erilliskustannukset-sql-haku [urakka]
  (str "WITH urakan_toimenpideinstanssi_23150 AS
         (SELECT tpi.id AS id
          FROM toimenpideinstanssi tpi
                   JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                   JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
               maksuera m
          WHERE tpi.urakka = " urakka "
            AND m.toimenpideinstanssi = tpi.id
            AND tpk2.koodi = '23150')
        SELECT kat.summa as summa
        FROM kustannusarvioitu_tyo kat
        WHERE kat.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
          AND kat.tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'Erillishankinnat (W)');"))

(defn hoidonjohdonpalkkio-sql-haku [urakka]
  (str "WITH urakan_toimenpideinstanssi_23150 AS
         (SELECT tpi.id AS id
          FROM toimenpideinstanssi tpi
                   JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                   JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
               maksuera m
          WHERE tpi.urakka = " urakka "
            AND m.toimenpideinstanssi = tpi.id
            AND tpk2.koodi = '23150')
        SELECT kat.summa as summa
        FROM kustannusarvioitu_tyo kat
        WHERE kat.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
          AND (kat.tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'Hoidonjohtopalkkio (G)')
               OR kat.tehtava = (SELECT id FROM toimenpidekoodi WHERE yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744')
               );"))


;; Kustannusten seuranta koostuu budjetoiduista kustannuksista ja niihin liitetyistä toteutuneista (laskutetuista) kustannuksista.
;; Seuranta jaetaan monella eri kriteerillä osiin, jotta seuranta helpottuu
;; (mm. Hankintakustannukset, Johto- ja Hallintakorvaus, Hoidonjohdonpalkkio, Erillishankinnat)

;; Testataan/vertaillaan ensimmäisenä erillishankintojen budjetoituja summia
(deftest budjetoidut-erillishankinnat-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        vastaus (kutsu-palvelua
                  (:http-palvelin jarjestelma)
                  :urakan-kustannusten-seuranta-toimenpideittain
                  +kayttaja-tero+
                  {:urakka-id urakka-id
                   :alkupvm alkupvm
                   :loppupvm loppupvm})
        erillishankinnat (filter
                               #(when (= "erillishankinnat" (:paaryhma %))
                                  true)
                               vastaus)
        eh-summa (apply + (map #(:budjetoitu_summa %) erillishankinnat))
        erillishankinnat-sql (q (erilliskustannukset-sql-haku urakka-id))
        sql-summa (apply + (map #(first %) erillishankinnat-sql))
        _ (println "erillishankinnat-sql" eh-summa (pr-str erillishankinnat-sql))
        _ (println "erillishankinnat"  sql-summa (pr-str erillishankinnat))]
    (is (= eh-summa sql-summa))))

;; Testataan/vertaillaan Hoidonjohdonpalkkioiden budjetoituja summia
(deftest budjetoidut-hoidonjohdonpalkkiot-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        vastaus (kutsu-palvelua
                  (:http-palvelin jarjestelma)
                  :urakan-kustannusten-seuranta-toimenpideittain
                  +kayttaja-tero+
                  {:urakka-id urakka-id
                   :alkupvm alkupvm
                   :loppupvm loppupvm})
        hj_palkkiot (filter
                           #(when (= "hoidonjohdonpalkkio" (:paaryhma %))
                              true)
                           vastaus)
        hj-summa (apply + (map #(:budjetoitu_summa %) hj_palkkiot))
        hj-sql (q (hoidonjohdonpalkkio-sql-haku urakka-id))
        sql-summa (apply + (map #(first %) hj-sql))
        _ (println "hj-sql" sql-summa (pr-str hj-sql))
        _ (println "hj"  hj-summa (pr-str hj_palkkiot))]
    (is (= hj-summa sql-summa))))
