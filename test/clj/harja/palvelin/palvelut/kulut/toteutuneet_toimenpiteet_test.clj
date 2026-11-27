(ns harja.palvelin.palvelut.kulut.toteutuneet-toimenpiteet-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.kyselyt.tehtavat-maarat-kyselyt :as tehtavat-maarat-kyselyt]
            [harja.palvelin.palvelut.kulut.toteutuneet-toimenpiteet-palvelu :as palvelu]
            [harja.testi :refer :all]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :toteutuneet-toimenpiteet
          (component/using
            (palvelu/->ToteutuneetToimenpiteet)
            [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each
  (compose-fixtures jarjestelma-fixture urakkatieto-fixture))

(deftest vhu-tehtavien-haussa-palautetaan-tehtavaluettelon-tavoitemaarat-ja-kustannukset
  (let [urakka-id (hae-urakan-id-nimella "Rentoselän urakka")
        alkupvm (ffirst (q (str "SELECT alkupvm FROM urakka WHERE id = " urakka-id)))
        alkuvuosi (-> alkupvm str (subs 0 4) Integer/parseInt)
        tehtava-id (ffirst (q "SELECT t.id
                                FROM tehtava t
                                     JOIN tehtavaryhma tr ON tr.id = t.tehtavaryhma
                                     JOIN tehtavaryhmaotsikko tro ON tro.id = tr.tehtavaryhmaotsikko_id
                               WHERE tro.urakkatyyppi = 'vesivayla-hoito'
                                 AND t.\"vhu-tehtava?\" IS TRUE
                               ORDER BY tro.id, tr.jarjestys, tr.id, t.jarjestys
                               LIMIT 1"))
        alkuperainen-maara 17M
        maaramuutos 5M
        tavoitemaara (+ alkuperainen-maara maaramuutos)
        _ (u (format "INSERT INTO urakka_tehtavamaara
                              (urakka, \"hoitokauden-alkuvuosi\", tehtava, maara, luoja, luotu, poistettu)
                       VALUES (%s, %s, %s, %s, %s, NOW(), false)
                       ON CONFLICT (urakka, \"hoitokauden-alkuvuosi\", tehtava)
                       DO UPDATE SET maara = EXCLUDED.maara, poistettu = false"
                    urakka-id alkuvuosi tehtava-id alkuperainen-maara (:id +kayttaja-jvh+)))
        muutos-id (i (format "INSERT INTO mhu_muutos
                                     (urakka, voimassa_alkaen, syy, luoja, luotu, poistettu)
                              VALUES (%s, '%s-10-01', 'VHU-kustannusnäkymän testi', %s, NOW(), false)"
                             urakka-id alkuvuosi (:id +kayttaja-jvh+)))
        _ (u (format "INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo
                              (muutos, tehtava, hoitokauden_alkuvuosi, maaramuutos, versio)
                       VALUES (%s, %s, %s, %s, 1)"
                    muutos-id tehtava-id alkuvuosi maaramuutos))
        toimenpideinstanssi-id (ffirst (q (str "SELECT id FROM toimenpideinstanssi
                                                 WHERE urakka = " urakka-id " LIMIT 1")))
        kulu-id (i (format "INSERT INTO kulu
                                   (kokonaissumma, erapaiva, urakka, koontilaskun_kuukausi, luoja)
                            VALUES (350, '%s-10-15', %s, 'lokakuu/%s-hoitovuosi', %s)"
                           alkuvuosi urakka-id 1 (:id +kayttaja-jvh+)))
        _ (u (format "INSERT INTO kulu_kohdistus
                              (rivi, kulu, summa, toimenpideinstanssi, tehtava, maksueratyyppi, luoja)
                       VALUES (0, %s, 350, %s, %s, 'kokonaishintainen', %s)"
                    kulu-id toimenpideinstanssi-id tehtava-id (:id +kayttaja-jvh+)))
        vastaus (kutsu-palvelua
                  (:http-palvelin jarjestelma)
                  :hae-vhu-toteutuneet-toimenpiteet
                  +kayttaja-jvh+
                  {:urakka-id urakka-id
                   :hoitokauden-alkuvuosi alkuvuosi
                   :alkupvm (str alkuvuosi "-08-01")
                   :loppupvm (str (inc alkuvuosi) "-07-31")})
        toimenpiteet (:toimenpiteet vastaus)
        tehtava (first (filter #(= tehtava-id (:tehtava_id %)) toimenpiteet))
        luettelon-tehtava-idt (->> (tehtavat-maarat-kyselyt/hae-vhu-tehtavat-ja-maarat
                                     (:db jarjestelma)
                                     urakka-id
                                     alkuvuosi)
                                 :tehtavat
                                 (keep :tehtava_id))
        kustannusnakyma-tehtava-idt (mapv :tehtava_id toimenpiteet)]
    (is (seq toimenpiteet))
    (is (= tavoitemaara (:tavoitemaara tehtava)))
    (is (nil? (:toteutunut_maara tehtava)))
    (is (= 350M (:toteutunut_kustannus tehtava)))
    (is (= luettelon-tehtava-idt kustannusnakyma-tehtava-idt)
        "Tehtävät ovat samassa järjestyksessä kuin Tehtävä- ja määräluettelossa")
    (is (every? #(and (:tehtavaryhmaotsikko_id %)
                      (:tehtavaryhmaotsikko %)
                      (:tehtavaryhma_id %)
                      (:tehtavaryhma_nimi %))
          toimenpiteet))))
