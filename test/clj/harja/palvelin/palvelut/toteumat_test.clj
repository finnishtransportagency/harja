(ns harja.palvelin.palvelut.toteumat-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.palvelut.toteumat :as toteumat]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.domain.tierekisteri.varusteet :as varusteet-domain]))

(def +testi-tierekisteri-url+ "harja.testi.tierekisteri")
(def +oikea-testi-tierekisteri-url+ "https://harja-test.solitaservices.fi/harja/integraatiotesti/tierekisteri")

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (let [tietokanta (tietokanta/luo-tietokanta testitietokanta)]
                      (component/start
                        (component/system-map
                          :db tietokanta
                          :db-replica tietokanta
                          :http-palvelin (testi-http-palvelin)
                          :karttakuvat (component/using
                                         (karttakuvat/luo-karttakuvat)
                                         [:http-palvelin :db])
                          :integraatioloki (component/using
                                             (integraatioloki/->Integraatioloki nil)
                                             [:db])
                          :tierekisteri (component/using
                                          (tierekisteri/->Tierekisteri +testi-tierekisteri-url+ nil)
                                          [:db :integraatioloki])
                          :toteumat (component/using
                                      (toteumat/->Toteumat)
                                      [:http-palvelin :db :db-replica :karttakuvat :tierekisteri]))))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; käyttää testidata.sql:stä tietoa
(deftest erilliskustannukset-haettu-oikein
  (let [alkupvm (pvm/luo-pvm 2005 9 1)
        loppupvm (pvm/luo-pvm 2006 10 30)
        res (kutsu-palvelua (:http-palvelin jarjestelma)
                            :urakan-erilliskustannukset +kayttaja-jvh+
                            {:urakka-id @oulun-alueurakan-2005-2010-id
                             :alkupvm alkupvm
                             :loppupvm loppupvm})
        oulun-alueurakan-toiden-lkm (ffirst (q
                                              (str "SELECT count(*)
                                                       FROM erilliskustannus
                                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @oulun-alueurakan-2005-2010-id
                                                   ") AND pvm >= '2005-10-01' AND pvm <= '2006-09-30'")))]
    (is (= (count res) oulun-alueurakan-toiden-lkm) "Erilliskustannusten määrä")))

(deftest tallenna-erilliskustannus-testi
  (let [hoitokauden-alkupvm (pvm/luo-pvm 2005 9 1)          ;;1.10.2005
        hoitokauden-loppupvm (pvm/luo-pvm 2006 10 30)       ;;30.9.2006
        toteuman-pvm (pvm/luo-pvm 2005 11 12)
        toteuman-lisatieto "Testikeissin lisätieto"
        ek {:urakka-id @oulun-alueurakan-2005-2010-id
            :alkupvm hoitokauden-alkupvm
            :loppupvm hoitokauden-loppupvm
            :pvm toteuman-pvm :rahasumma 20000.0
            :indeksin_nimi "MAKU 2005" :toimenpideinstanssi 1 :sopimus 1
            :tyyppi "asiakastyytyvaisyysbonus" :lisatieto toteuman-lisatieto}
        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*)
                                                       FROM erilliskustannus
                                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @oulun-alueurakan-2005-2010-id
                                            ") AND pvm >= '2005-10-01' AND pvm <= '2006-09-30'")))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-erilliskustannus +kayttaja-jvh+ ek)
        lisatty (first (filter #(and
                                  (= (:pvm %) toteuman-pvm)
                                  (= (:lisatieto %) toteuman-lisatieto)) vastaus))]
    (is (= (:pvm lisatty) toteuman-pvm) "Tallennetun erilliskustannuksen pvm")
    (is (= (:lisatieto lisatty) toteuman-lisatieto) "Tallennetun erilliskustannuksen lisätieto")
    (is (= (:indeksin_nimi lisatty) "MAKU 2005") "Tallennetun erilliskustannuksen indeksin nimi")
    (is (= (:rahasumma lisatty) 20000.0) "Tallennetun erilliskustannuksen pvm")
    (is (= (:urakka lisatty) @oulun-alueurakan-2005-2010-id) "Oikea urakka")
    (is (= (:toimenpideinstanssi lisatty) 1) "Tallennetun erilliskustannuksen tp")
    (is (= (count vastaus) (+ 1 maara-ennen-lisaysta)) "Tallennuksen jälkeen erilliskustannusten määrä")

    ;; Testaa päivittämistä

    (let [ek-id (:id lisatty)
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-erilliskustannus +kayttaja-jvh+
                                  (assoc ek
                                    :id ek-id
                                    :indeksin_nimi "MAKU 2010"))
          paivitetty (first (filter #(= (:id %)
                                        ek-id)
                                    vastaus))]
      (is (= (:indeksin_nimi paivitetty) "MAKU 2010") "Tallennetun erilliskustannuksen indeksin nimi"))

    ;; Testaa virheellinen päivitys vaihtamalla urakka

    (let [ek-id (:id lisatty)
          _ (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                           :tallenna-erilliskustannus +kayttaja-jvh+
                                                           (assoc ek
                                                             :id ek-id
                                                             :indeksin_nimi "MAKSU 2015"
                                                             :urakka-id @oulun-alueurakan-2014-2019-id))))
          urakka (ffirst (q (str "SELECT urakka FROM erilliskustannus WHERE id = " ek-id ";")))
          indeksin-nimi (ffirst (q (str "SELECT indeksin_nimi FROM erilliskustannus WHERE id = " ek-id ";")))]
      (is (= urakka @oulun-alueurakan-2005-2010-id) "Virheellistä urakkaa ei päivitetty")
      (is (= indeksin-nimi "MAKU 2010") "Virheellistä indeksiä ei päivitetty"))

    ;; Poista luotu erilliskustannus
    (u
      (str "DELETE FROM erilliskustannus
                    WHERE pvm = '2005-12-12' AND lisatieto = '" toteuman-lisatieto "'"))))


(deftest tallenna-muut-tyot-toteuma-testi
  (let [tyon-pvm (konv/sql-timestamp (pvm/luo-pvm 2005 11 24)) ;;24.12.2005
        hoitokausi-aloituspvm (pvm/luo-pvm 2005 9 1)        ; 1.10.2005
        hoitokausi-lopetuspvm (pvm/luo-pvm 2006 8 30)       ;30.9.2006
        toteuman-lisatieto "Testikeissin lisätieto2"
        tyo {:urakka-id @oulun-alueurakan-2005-2010-id :sopimus-id @oulun-alueurakan-2005-2010-paasopimuksen-id
             :alkanut tyon-pvm :paattynyt tyon-pvm
             :hoitokausi-aloituspvm hoitokausi-aloituspvm :hoitokausi-lopetuspvm hoitokausi-lopetuspvm
             :suorittajan-nimi "Alihankkijapaja Ky" :suorittajan-ytunnus "123456-Y"
             :tyyppi :muutostyo
             :lisatieto toteuman-lisatieto
             :tehtava {:paivanhinta 456, :maara 2, :toimenpidekoodi 1368}}
        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*)
                                               FROM toteuma
                                              WHERE urakka = " @oulun-alueurakan-2005-2010-id "
                                                     AND sopimus = " @oulun-alueurakan-2005-2010-paasopimuksen-id "
                                                    AND tyyppi IN ('muutostyo', 'lisatyo', 'akillinen-hoitotyo', 'vahinkojen-korjaukset')
                                                    AND alkanut >= to_date('1-10-2005', 'DD-MM-YYYY')
                                                    AND paattynyt <= to_date('30-09-2006', 'DD-MM-YYYY');;")))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-muiden-toiden-toteuma +kayttaja-jvh+ tyo)
        lisatty (first (filter #(and
                                  (= (:lisatieto %) toteuman-lisatieto)) vastaus))]
    (is (= (count vastaus) (+ 1 maara-ennen-lisaysta)) "Tallennuksen jälkeen muiden töiden määrä")
    (is (= (:alkanut lisatty) tyon-pvm) "Tallennetun muun työn alkanut pvm")
    (is (= (:paattynyt lisatty) tyon-pvm) "Tallennetun muun työn paattynyt pvm")
    (is (= (:tyyppi lisatty) :muutostyo) "Tallennetun muun työn tyyppi")
    (is (= (:lisatieto lisatty) toteuman-lisatieto) "Tallennetun erilliskustannuksen lisätieto")
    (is (= (get-in lisatty [:tehtava :paivanhinta]) 456.0) "Tallennetun muun työn päivänhinta")
    (is (= (get-in lisatty [:tehtava :maara]) 2.0) "Tallennetun muun työn määrä")
    (is (= (get-in lisatty [:tehtava :toimenpidekoodi]) 1368) "Tallennetun muun työn toimenpidekoodi")

    ;; Testaa päivitys

    (let [toteuma-id (get-in lisatty [:toteuma :id])
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-muiden-toiden-toteuma +kayttaja-jvh+
                                  (assoc tyo
                                    :toteuma {:id toteuma-id}
                                    :lisatieto "Testikeissi"))
          paivitetty (first (filter #(= (get-in % [:toteuma :id])
                                        toteuma-id)
                                    vastaus))]

      (is (= (:lisatieto paivitetty) "Testikeissi") "Päivitetyn erilliskustannuksen lisätieto"))

    ;; Testaa virheellinen päivitys vaihtamalla urakka

    (try
      (let [toteuma-id (get-in lisatty [:toteuma :id])
            _ (kutsu-palvelua (:http-palvelin jarjestelma)
                              :tallenna-muiden-toiden-toteuma +kayttaja-jvh+
                              (assoc tyo
                                :toteuma {:id toteuma-id}
                                :urakka-id @oulun-alueurakan-2014-2019-id))])
      (is false "Päivitys sallittiin virheellisesti")
      (catch Exception e
        (is true "Päivitystä ei sallittu")))

    ;; Siivotaan lisätyt rivit pois
    (u
      (str "DELETE FROM toteuma_tehtava
                    WHERE toteuma = " (get-in lisatty [:toteuma :id])))
    (u
      (str "DELETE FROM toteuma
                    WHERE id = " (get-in lisatty [:toteuma :id])))))


(deftest tallenna-yksikkohintainen-toteuma-testi
  (let [tyon-pvm (konv/sql-timestamp (pvm/luo-pvm 2005 11 24)) ;;24.12.2005
        hoitokausi-aloituspvm (pvm/luo-pvm 2005 9 1)        ; 1.10.2005
        hoitokausi-lopetuspvm (pvm/luo-pvm 2006 8 30)       ;30.9.2006
        urakka-id @oulun-alueurakan-2005-2010-id
        toteuman-lisatieto "Testikeissin lisätieto4"
        tyo {:urakka-id urakka-id
             :sopimus-id @oulun-alueurakan-2005-2010-paasopimuksen-id
             :alkanut tyon-pvm :paattynyt tyon-pvm
             :hoitokausi-aloituspvm hoitokausi-aloituspvm
             :hoitokausi-lopetuspvm hoitokausi-lopetuspvm
             :suorittajan-nimi "Alihankkijapaja Ky" :suorittajan-ytunnus "123456-Y"
             :tyyppi :yksikkohintainen
             :toteuma-id nil
             :lisatieto toteuman-lisatieto
             :tehtavat [{:toimenpidekoodi 1368 :maara 333}]}
        hae-summat #(->> (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :urakan-toteumien-tehtavien-summat
                                         +kayttaja-jvh+
                                         {:urakka-id urakka-id
                                          :sopimus-id @oulun-alueurakan-2005-2010-paasopimuksen-id
                                          :alkupvm hoitokausi-aloituspvm
                                          :loppupvm hoitokausi-lopetuspvm
                                          :tyyppi :yksikkohintainen})
                         (group-by :tpk_id)
                         (fmap first))

        summat-ennen-lisaysta (hae-summat)]

    (is (not (contains? summat-ennen-lisaysta 1368)))

    (let [lisatty (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-urakan-toteuma-ja-yksikkohintaiset-tehtavat
                                  +kayttaja-jvh+ tyo)
          summat-lisayksen-jalkeen (hae-summat)]

      (is (= (get-in lisatty [:toteuma :alkanut]) tyon-pvm) "Tallennetun työn alkanut pvm")
      (is (= (get-in lisatty [:toteuma :paattynyt]) tyon-pvm) "Tallennetun työn paattynyt pvm")
      (is (= (get-in lisatty [:toteuma :lisatieto]) toteuman-lisatieto) "Tallennetun työn lisätieto")
      (is (= (get-in lisatty [:toteuma :suorittajan-nimi]) "Alihankkijapaja Ky") "Tallennetun työn suorittajan nimi")
      (is (= (get-in lisatty [:toteuma :suorittajan-ytunnus]) "123456-Y") "Tallennetun työn suorittajan y-tunnus")
      (is (= (get-in lisatty [:toteuma :urakka-id]) urakka-id) "Tallennetun työn urakan id")
      (is (= (get-in lisatty [:toteuma :sopimus-id]) @oulun-alueurakan-2005-2010-paasopimuksen-id) "Tallennetun työn pääsopimuksen id")
      (is (= (get-in lisatty [:toteuma :tehtavat 0 :toimenpidekoodi]) 1368) "Tallennetun työn tehtävän toimenpidekoodi")
      (is (= (get-in lisatty [:toteuma :tehtavat 0 :maara]) 333) "Tallennetun työn tehtävän määrä")
      (is (= (get-in lisatty [:toteuma :tyyppi]) :yksikkohintainen) "Tallennetun työn toteuman tyyppi")

      (is (== 333 (get-in summat-lisayksen-jalkeen [1368 :maara])))

      ;; Testaa päivitys

      (let [toteuma-id (get-in lisatty [:toteuma :toteuma-id])
            toteuma (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :urakan-toteuma
                                    +kayttaja-jvh+
                                    {:urakka-id urakka-id
                                     :toteuma-id toteuma-id})
            muokattu-tyo (assoc tyo
                           :toteuma-id toteuma-id
                           :tehtavat [{:toimenpidekoodi 1369 :maara 666
                                       :tehtava-id (get-in toteuma
                                                           [:tehtavat 0 :tehtava-id])}])
            muokattu (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :tallenna-urakan-toteuma-ja-yksikkohintaiset-tehtavat
                                     +kayttaja-jvh+ muokattu-tyo)
            summat-muokkauksen-jalkeen (hae-summat)]

        (is (= (get-in muokattu [:toteuma :tehtavat 0 :toimenpidekoodi]) 1369))
        (is (= (get-in muokattu [:toteuma :tehtavat 0 :maara]) 666))
        (is (= 1 (count (get-in muokattu [:toteuma :tehtavat]))))

        (is (not (contains? summat-muokkauksen-jalkeen 1368)))
        (is (== 666 (get-in summat-muokkauksen-jalkeen [1369 :maara])))

        ;; Testaa virheellinen päivitys

        (try
          (kutsu-palvelua (:http-palvelin jarjestelma)
                          :tallenna-urakan-toteuma-ja-yksikkohintaiset-tehtavat
                          +kayttaja-jvh+ (assoc muokattu-tyo :urakka-id @muhoksen-paallystysurakan-id))
          (is false "Virheellisesti sallittiin päivittää väärällä urakka-id:llä")
          (catch Exception e
            (is true "Ei sallittu päivittää väärällä urakka-id:llä")))

        ;; Siivoa roskat

        (u
          (str "DELETE FROM toteuma_tehtava
                    WHERE toteuma = " toteuma-id ";"))
        (u
          (str "DELETE FROM toteuma
                    WHERE id = " toteuma-id))))))

(deftest tallenna-kokonaishintainen-toteuma-testi
  (let [tyon-pvm (konv/sql-timestamp (pvm/luo-pvm 2020 11 24)) ;;24.12.2005
        hoitokausi-aloituspvm (pvm/luo-pvm 2016 9 1)        ; 1.10.2016
        hoitokausi-lopetuspvm (pvm/luo-pvm 2016 8 30)       ;30.9.2017
        urakka-id (hae-oulun-alueurakan-2014-2019-id)
        tyo {:urakka-id urakka-id
             :sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
             :alkanut tyon-pvm :paattynyt tyon-pvm
             :reitti {:type :multiline
                      :lines [{:type :line, :points [[426948.180407029 7212765.48225361] [430650.8691 7212578.8262]]}]}
             :hoitokausi-aloituspvm hoitokausi-aloituspvm
             :hoitokausi-lopetuspvm hoitokausi-lopetuspvm
             :suorittajan-nimi "Alihankkijapaja Ky" :suorittajan-ytunnus "123456-Y"
             :tyyppi :kokonaishintainen
             :toteuma-id nil
             :tehtavat [{:toimenpidekoodi 1368 :maara 333}]}
        lisatty (first (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tallenna-urakan-toteuma-ja-kokonaishintaiset-tehtavat
                                 +kayttaja-jvh+ {:toteuma tyo
                                                 :hakuparametrit {:urakka-id urakka-id
                                                                  :sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
                                                                  :alkupvm tyon-pvm :loppupvm tyon-pvm
                                                                  :toimenpide nil :tehtava nil}}))]
    (is (= (get-in lisatty [:pvm]) tyon-pvm) "Tallennetun työn alkanut pvm")
    (is (=marginaalissa? (get-in lisatty [:pituus]) 3707.390462) "Tallennetun työn paattynyt pvm")
    (is (= (get-in lisatty [:jarjestelmanlisaama]) false) )
    (is (= (get-in lisatty [:nimi]) "Pistehiekoitus") )
    (is (= (get-in lisatty [:yksikko]) "tiekm") "Yksikkö")
    (is (= (get-in lisatty [:toimenpidekoodi]) 1368) "Tallennetun työn tehtävän toimenpidekoodi")
    (is (= (get-in lisatty [:maara]) 333M) "Tallennetun työn tehtävän määrä")))

(deftest hae-urakan-toteuma-kun-toteuma-ei-kuulu-urakkaan
  (let [toteuma-id (ffirst (q "SELECT id FROM toteuma WHERE urakka != " @oulun-alueurakan-2014-2019-id
                              " AND tyyppi = 'yksikkohintainen' LIMIT 1;"))]
    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :urakan-toteuma
                                                   +kayttaja-jvh+
                                                   {:urakka-id @oulun-alueurakan-2014-2019-id
                                                    :toteuma-id toteuma-id})))))

(deftest tallenna-toteuma-ja-toteumamateriaalit-test
  (let [[urakka sopimus] (first (q (str "SELECT urakka, id FROM sopimus WHERE urakka=" @oulun-alueurakan-2005-2010-id)))
        toteuma (atom {:id -5, :urakka urakka :sopimus sopimus :alkanut (pvm/luo-pvm 2005 11 24) :paattynyt (pvm/luo-pvm 2005 11 24)
                       :tyyppi "yksikkohintainen" :suorittajan-nimi "UNIT TEST" :suorittajan-ytunnus 1234 :lisatieto "Unit test teki tämän"})
        tmt (atom [{:id -1 :materiaalikoodi 1 :maara 192837} {:materiaalikoodi 1 :maara 192837}])
        sopimuksen-kaytetty-materiaali-ennen (q (str "SELECT alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus))]
    ;; tarkistetaan että kaikki cachesta palautetut tulokset löytyvät expected-setistä
    (is (= true (every? #(some?
                           ((set [[#inst "2005-09-30T21:00:00.000-00:00" 1 7M]
                                  [#inst "2005-09-30T21:00:00.000-00:00" 4 9M]
                                  [#inst "2005-09-30T21:00:00.000-00:00" 2 4M]
                                  [#inst "2005-09-30T21:00:00.000-00:00" 3 3M]
                                  [#inst "2004-10-19T21:00:00.000-00:00" 5 25M]]) %))
                        sopimuksen-kaytetty-materiaali-ennen)))
    (is (= 0 (ffirst (q "SELECT count(*) FROM toteuma_materiaali WHERE maara=192837 AND poistettu IS NOT TRUE"))))
    (is (= 0 (ffirst (q "SELECT count(*) FROM toteuma WHERE suorittajan_nimi='UNIT TEST' AND poistettu IS NOT TRUE"))))
    (is (nil? (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-toteuma-ja-toteumamateriaalit +kayttaja-jvh+
                              {:toteuma @toteuma
                               :toteumamateriaalit @tmt
                               ;pvm/luo-pvm 2006 8 30)]
                               :sopimus sopimus})))
    (let [tmidt (flatten (q "SELECT id FROM toteuma_materiaali WHERE maara=192837"))
          tid (ffirst (q "SELECT id from toteuma WHERE suorittajan_nimi='UNIT TEST'"))
          uusi-lisatieto "NYT PITÄIS OLLA MUUTTUNUT."
          sopimuksen-kaytetty-materiaali-jalkeen (q (str "SELECT alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus))]
      ;; tarkistetaan että kaikki cachesta palautetut tulokset löytyvät expected-setistä
      (is (= true (every? #(some?
                             ((set [[#inst "2005-09-30T21:00:00.000-00:00" 1 7M]
                                    [#inst "2005-09-30T21:00:00.000-00:00" 4 9M]
                                    [#inst "2005-09-30T21:00:00.000-00:00" 2 4M]
                                    [#inst "2005-09-30T21:00:00.000-00:00" 3 3M]
                                    [#inst "2004-10-19T21:00:00.000-00:00" 5 25M]
                                    [#inst "2005-12-23T22:00:00.000-00:00" 1 385674M]]) %))
                          sopimuksen-kaytetty-materiaali-jalkeen)))

      (is (= 2 (ffirst (q "SELECT count(*) FROM toteuma_materiaali WHERE maara=192837 AND poistettu IS NOT TRUE"))))
      (is (= 1 (ffirst (q "SELECT count(*) FROM toteuma WHERE suorittajan_nimi='UNIT TEST' AND poistettu IS NOT TRUE"))))

      (reset! tmt
              [(-> (assoc (first @tmt) :id (first tmidt))
                   (assoc :poistettu true))
               (-> (assoc (second @tmt) :id (second tmidt))
                   (assoc :maara 8712))])

      (reset! toteuma (-> (assoc @toteuma :id tid)
                          (assoc :lisatieto uusi-lisatieto)))

      (is (not (nil? (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-toteuma-ja-toteumamateriaalit +kayttaja-jvh+
                                     {:toteuma @toteuma
                                      :toteumamateriaalit @tmt
                                      :hoitokausi [(pvm/luo-pvm 2005 9 1) (pvm/luo-pvm 2006 8 30)]
                                      :sopimus sopimus}))))

      (is (= 1 (ffirst (q "SELECT count(*) FROM toteuma WHERE suorittajan_nimi='UNIT TEST' AND poistettu IS NOT TRUE"))))
      (is (= 1 (ffirst (q "SELECT count(*) FROM toteuma_materiaali WHERE maara=192837 AND poistettu IS TRUE"))))
      (is (= 1 (ffirst (q "SELECT count(*) FROM toteuma_materiaali WHERE maara=8712 AND poistettu IS NOT TRUE"))))

      (is (= uusi-lisatieto (ffirst (q "SELECT lisatieto FROM toteuma WHERE id=" tid))))
      (is (= 8712 (int (ffirst (q "SELECT maara FROM toteuma_materiaali WHERE id=" (second tmidt))))))

      (u "DELETE FROM toteuma_materiaali WHERE id in (" (clojure.string/join "," tmidt) ")")
      (u "DELETE FROM toteuma WHERE id=" tid))))

(deftest varustetoteumat-haettu-oikein
  (let [alkupvm (pvm/luo-pvm 2005 9 1)
        loppupvm (pvm/luo-pvm 2017 10 30)
        hae-tietolaji-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))
        varustetoteumat (with-fake-http [(str +testi-tierekisteri-url+ "/haetietolaji") hae-tietolaji-xml]
                                        (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :urakan-varustetoteumat +kayttaja-jvh+
                                                        {:urakka-id @oulun-alueurakan-2005-2010-id
                                                         :sopimus-id @oulun-alueurakan-2005-2010-paasopimuksen-id
                                                         :alkupvm alkupvm
                                                         :loppupvm loppupvm
                                                         :tietolajit (into #{} (keys varusteet-domain/tietolaji->selitys))}))]
    (is (>= (count varustetoteumat) 3))
    (is (contains? (first varustetoteumat) :sijainti))))

;; circleci:stä ei saane yhteyttä +oikea-testi-tierekisteri-url+
;; Otetaan pois siksi aikaa, että on saatu muutokset tierekisteriin liittyen tuotantoon asti
(when (not (circleci?))
  (deftest varusteiden-testit
    ;; Tässä haetaan liikennemerkin tiedot tierekisterin testipuolelta. Tierekisterikomponentti kakuttaa tuloksen, niin
    ;; tallennuksen aikana ei tarvitse enään tehdä uutta kutsua (jos tehtäisiin, niin se epäonnistuisi, koska annetun tierekisterikomponentin url
    ;; on tekaistu)
    (tierekisteri/hae-tietolaji (assoc (:tierekisteri jarjestelma) :tierekisteri-api-url +oikea-testi-tierekisteri-url+) "tl506" nil)
    (let [toteuma {:ajorata 0
                   :kuntoluokitus nil
                   :sijainti
                   {:type :point :coordinates [428024.7622351866 7210432.45750019]}
                   :tierekisteriosoite {:numero 22 :alkuosa 1 :alkuetaisyys 3}
                   :urakka-id 4
                   :loppupvm nil
                   :arvot {:x 428025 :y 7210432 :asetusnr "11"}
                   :puoli 1
                   :tietolaji "tl506"
                   :id nil
                   :uusi-liite nil
                   :toiminto :lisatty
                   :alkupvm (pvm/luo-pvm 2018 6 11)
                   :tunniste nil
                   :lisatieto nil}
          hakuehdot {:urakka-id 4
                     :sopimus-id 2
                     :alkupvm (pvm/luo-pvm 2017 9 30)
                     :loppupvm (pvm/luo-pvm 3018 9 29)
                     :tienumero nil}
          toteumien-maara (atom (count (q "SELECT id FROM toteuma;")))
          ;; Tämä kopio tarvitaan, jotta sitä voidaan käyttää with-redefs funktiossa. Tarkoitus on vain muuttaa vastaus-saatu
          ;; atomin arvo todeksi, kun tallennusoperaatio on suoritettu, sillä se suoritetaan omassa säikeessään.
          laheta-varustetoteuma-tierekisteriin-copy tierekisteri/laheta-varustetoteuma-tierekisteriin
          haku-fn (fn [haku-parametrit vastaus-atom]
                    (with-redefs [tierekisteri/laheta-varustetoteuma-tierekisteriin (fn [this varustetoteuma-id]
                                                                                      (when (= (:tierekisteri-api-url this) +testi-tierekisteri-url+)
                                                                                        (laheta-varustetoteuma-tierekisteriin-copy this varustetoteuma-id))
                                                                                      (reset! vastaus-atom true))]
                      (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :tallenna-varustetoteuma +kayttaja-jvh+
                                      haku-parametrit)))]
      (testing "Varusteen tallentaminen"
        (let [lisaa-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
              vastaus-saatu (atom false)]
          (with-fake-http
            [(str +testi-tierekisteri-url+ "/lisaatietue") lisaa-tietue-xml]
            (let [vastaus (haku-fn {:hakuehdot hakuehdot :toteuma toteuma} vastaus-saatu)
                  _ (odota-ehdon-tayttymista #(true? @vastaus-saatu) "Tierekisteristä saatiin vastaus" 10000)
                  tallennettu-varuste (q "SELECT luotu, tunniste, tietolaji, toimenpide, tila FROM varustetoteuma WHERE tunniste='HARJ0000000000000002' ORDER BY luotu DESC;")]
              (swap! toteumien-maara inc)
              (is (= (count (q "SELECT id FROM toteuma;")) @toteumien-maara))
              (is (true? @vastaus-saatu) "Saatiin vastaus")
              (is (= (mapv rest tallennettu-varuste) [["HARJ0000000000000002" "tl506" "lisatty" "lahetetty"]]))
              (is (nil? (:tila vastaus)))                   ;; Vastauksen tila pitäisi olla uusissa nil, sillä sitä ei olla vileä keretty lähettää tierekisteriin
              (is (= (:tunniste (first vastaus)) "HARJ0000000000000002"))
              (doseq [[kentta arvo] (:arvot (first vastaus))]
                (when (contains? (:arvot toteuma) kentta)
                  (is (= arvo (str (kentta (:arvot toteuma)))))))))))

      (testing "Varusteen päivittäminen"
        (let [paivita-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
              vastaus-saatu (atom false)]
          (with-fake-http
            [(str +testi-tierekisteri-url+ "/paivitatietue") paivita-tietue-xml]
            (let [toteuma (-> toteuma
                              (update :arvot (fn [arvot] (assoc arvot :lmteksti "foo")))
                              (assoc :toiminto :paivitetty
                                     :tunniste "HARJ0000000000000002"))
                  vastaus (haku-fn {:hakuehdot hakuehdot :toteuma toteuma} vastaus-saatu)
                  _ (odota-ehdon-tayttymista #(true? @vastaus-saatu) "Tierekisteristä saatiin vastaus" 10000)
                  varusteet (q "SELECT luotu, tunniste, tietolaji, toimenpide, tila FROM varustetoteuma WHERE tunniste='HARJ0000000000000002' ORDER BY luotu ASC;")]
              (swap! toteumien-maara inc)
              (is (= (count (q "SELECT id FROM toteuma;")) @toteumien-maara))
              (is (true? @vastaus-saatu) "Saatiin vastaus")
              (is (= (mapv rest varusteet) [["HARJ0000000000000002" "tl506" "lisatty" "lahetetty"]
                                            ["HARJ0000000000000002" "tl506" "paivitetty" "lahetetty"]]))
              (is (= (:tunniste (first vastaus)) "HARJ0000000000000002"))
              (doseq [[kentta arvo] (:arvot (first vastaus))]
                (when (contains? (:arvot toteuma) kentta)
                  (is (= arvo (str (kentta (:arvot toteuma)))))))))))

      (testing "Varusteen poistaminen"
        (let [poista-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
              vastaus-saatu (atom false)]
          (with-fake-http
            [(str +testi-tierekisteri-url+ "/poistatietue") poista-tietue-xml]
            (let [toteuma (-> toteuma
                              (update :arvot (fn [arvot] (assoc arvot :lmteksti "foo")))
                              (assoc :toiminto :poistettu
                                     :tunniste "HARJ0000000000000002"))
                  vastaus (haku-fn {:hakuehdot hakuehdot :toteuma toteuma} vastaus-saatu)
                  _ (odota-ehdon-tayttymista #(true? @vastaus-saatu) "Tierekisteristä saatiin vastaus" 10000)
                  varusteet (q "SELECT luotu, tunniste, tietolaji, toimenpide, tila FROM varustetoteuma WHERE tunniste='HARJ0000000000000002' ORDER BY luotu ASC;")]
              (swap! toteumien-maara inc)
              (is (= (count (q "SELECT id FROM toteuma;")) @toteumien-maara))
              (is (true? @vastaus-saatu) "Saatiin vastaus")
              (is (= (mapv rest varusteet) [["HARJ0000000000000002" "tl506" "lisatty" "lahetetty"]
                                            ["HARJ0000000000000002" "tl506" "paivitetty" "lahetetty"]
                                            ["HARJ0000000000000002" "tl506" "poistettu" "lahetetty"]]))
              (is (= (:tunniste (first vastaus)) "HARJ0000000000000002"))))))

      (testing "Varusteen epäonnistunut tallentaminen"
        (let [lisaa-tietue-virhe-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/virhe-tietueen-lisays-epaonnistui-response.xml"))
              vastaus-saatu (atom false)]
          (with-fake-http
            [(str +testi-tierekisteri-url+ "/lisaatietue") lisaa-tietue-virhe-xml]
            (let [vastaus (haku-fn {:hakuehdot hakuehdot :toteuma toteuma} vastaus-saatu)
                  _ (odota-ehdon-tayttymista #(true? @vastaus-saatu) "Tierekisteristä saatiin vastaus" 10000)
                  tallennettu-varuste (q "SELECT luotu, tunniste, tietolaji, toimenpide, tila, lahetysvirhe FROM varustetoteuma WHERE tunniste='HARJ0000000000000003' ORDER BY luotu DESC;")]
              (swap! toteumien-maara inc)
              (is (= (count (q "SELECT id FROM toteuma;")) @toteumien-maara))
              (is (true? @vastaus-saatu) "Saatiin vastaus")
              (is (= (mapv rest tallennettu-varuste) [["HARJ0000000000000003" "tl506" "lisatty" "virhe" "Virheet: Tietueen tiedot ovat puutteelliset"]]))
              (is (nil? (:tila vastaus)))
              (is (= (:tunniste (first vastaus)) "HARJ0000000000000003"))
              (doseq [[kentta arvo] (:arvot (first vastaus))]
                (when (contains? (:arvot toteuma) kentta)
                  (is (= arvo (str (kentta (:arvot toteuma)))))))))))
      (testing "Varusteen epäonnistuneen tallentamisen tallentaminen"
        (let [lisaa-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
              vastaus-saatu (atom false)]
          (with-fake-http
            [(str +testi-tierekisteri-url+ "/lisaatietue") lisaa-tietue-xml]
            (let [toteuma (-> toteuma
                              (assoc :tunniste "HARJ0000000000000003"
                                     :id (ffirst (q "SELECT id FROM varustetoteuma WHERE tunniste='HARJ0000000000000003'"))))
                  vastaus (haku-fn {:hakuehdot hakuehdot :toteuma toteuma} vastaus-saatu)
                  _ (odota-ehdon-tayttymista #(true? @vastaus-saatu) "Tierekisteristä saatiin vastaus" 10000)
                  tallennettu-varuste (q "SELECT luotu, tunniste, tietolaji, toimenpide, tila, lahetysvirhe FROM varustetoteuma WHERE tunniste='HARJ0000000000000003' ORDER BY luotu DESC;")]
              (is (= (count (q "SELECT id FROM toteuma;")) @toteumien-maara))
              (is (true? @vastaus-saatu) "Saatiin vastaus")
              (is (= (mapv rest tallennettu-varuste) [["HARJ0000000000000003" "tl506" "lisatty" "lahetetty" nil]]))
              (is (= (:tunniste (first vastaus)) "HARJ0000000000000003")))))))))

(deftest kokonaishintaisen-toteuman-siirtymatiedot
  (let [toteuma-id (ffirst (q "SELECT id FROM toteuma WHERE urakka = 2 AND lisatieto = 'Tämä on käsin tekaistu juttu'"))
        hae #(kutsu-palvelua (:http-palvelin jarjestelma)
                             :siirry-toteuma
                             %
                             toteuma-id)
        ok-tulos {:alkanut #inst "2008-09-08T21:10:00.000000000-00:00"
                  :urakka-id 2
                  :tyyppi "kokonaishintainen"
                  :hallintayksikko-id 12
                  :aikavali {:alku #inst "2007-09-30T21:00:00.000-00:00"
                             :loppu #inst "2008-09-29T21:00:00.000-00:00"}
                  :tehtavat
                  [{:toimenpidekoodi 1350, :toimenpideinstanssi "10100"}]}
        ei-ok-tulos nil]

    (is (some? toteuma-id))

    ;; JVH voi hakea siirtymätiedot
    (tarkista-map-arvot ok-tulos (hae +kayttaja-jvh+))

    ;; Eri urakoitsijalla palautuu poikkeus oikeustarkistuksessa
    (is (thrown? Exception (hae +kayttaja-yit_uuvh+)))

    ;; Toteuman urakan urakoitsijan käyttäjä näkee siirtymätiedot
    (tarkista-map-arvot ok-tulos (hae +kayttaja-ulle+))))

(deftest toteuman-paivitys-sama-partitiolle
  (let [urakka-id (hae-pudasjarven-alueurakan-id)
        sopimus-id (hae-pudasjarven-alueurakan-paasopimuksen-id)
        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE urakka = " urakka-id " AND lisatieto = 'Tämä on käsin tekaistu juttu'")))
        toteuma-tehtava-id (ffirst (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id";")))
        uusi-tyon-pvm-samassa-partitiossa (konv/sql-timestamp (pvm/luo-pvm 2009 11 24)) ;;24.12.2005
        hoitokausi-aloituspvm (pvm/luo-pvm 2008 9 1)        ; 1.10.2016
        hoitokausi-lopetuspvm (pvm/luo-pvm 2010 8 30)       ;30.9.2017
        tyo {:urakka-id urakka-id
             :sopimus-id sopimus-id
             :alkanut uusi-tyon-pvm-samassa-partitiossa :paattynyt uusi-tyon-pvm-samassa-partitiossa
             :reitti {:type :multiline
                      :lines [{:type :line, :points [[426948.180407029 7212765.48225361] [430650.8691 7212578.8262]]}]}
             :hoitokausi-aloituspvm hoitokausi-aloituspvm
             :hoitokausi-lopetuspvm hoitokausi-lopetuspvm
             :suorittajan-nimi "Alihankkijapaja Ky" :suorittajan-ytunnus "123456-Y"
             :tyyppi :kokonaishintainen
             :toteuma-id toteuma-id
             :tehtavat [{:toimenpidekoodi 1368 :maara 333 :tehtava-id toteuma-tehtava-id}]}
        lisatty (first (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-urakan-toteuma-ja-kokonaishintaiset-tehtavat
                                       +kayttaja-jvh+ {:toteuma tyo
                                                       :hakuparametrit {:urakka-id urakka-id
                                                                        :sopimus-id sopimus-id
                                                                        :alkupvm hoitokausi-aloituspvm :loppupvm hoitokausi-lopetuspvm
                                                                        :toimenpide nil :tehtava nil}}))]

    (is (= (get-in lisatty [:pvm]) uusi-tyon-pvm-samassa-partitiossa) "Tallennetun työn alkanut pvm")
    (is (=marginaalissa? (get-in lisatty [:pituus]) 3707.390462) "Tallennetun työn paattynyt pvm")
    (is (= (get-in lisatty [:jarjestelmanlisaama]) false))
    (is (= (get-in lisatty [:nimi]) "Pistehiekoitus"))
    (is (= (get-in lisatty [:yksikko]) "tiekm") "Yksikkö")
    (is (= (get-in lisatty [:toimenpidekoodi]) 1368) "Tallennetun työn tehtävän toimenpidekoodi")
    (is (= (get-in lisatty [:maara]) 333M) "Tallennetun työn tehtävän määrä")))


(deftest toteuman-paivitys-siirtaa-eri-partitiolle
  (let [urakka-id (hae-pudasjarven-alueurakan-id)
        sopimus-id (hae-pudasjarven-alueurakan-paasopimuksen-id)
        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE urakka = " urakka-id " AND lisatieto = 'Tämä on käsin tekaistu juttu'")))
        toteuma-tehtava-id (ffirst (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id";")))
        uusi-tyon-pvm-eri-partitiossa (konv/sql-timestamp (pvm/luo-pvm 2020 11 24)) ;;24.12.2020
        hoitokausi-aloituspvm (pvm/luo-pvm 2008 9 1)        ; 1.10.2016
        hoitokausi-lopetuspvm (pvm/luo-pvm 2010 8 30)       ;30.9.2017
        tyo {:urakka-id urakka-id
             :sopimus-id sopimus-id
             :alkanut uusi-tyon-pvm-eri-partitiossa :paattynyt uusi-tyon-pvm-eri-partitiossa
             :reitti {:type :multiline
                      :lines [{:type :line, :points [[426948.180407029 7212765.48225361] [430650.8691 7212578.8262]]}]}
             :hoitokausi-aloituspvm hoitokausi-aloituspvm
             :hoitokausi-lopetuspvm hoitokausi-lopetuspvm
             :suorittajan-nimi "Alihankkijapaja Ky" :suorittajan-ytunnus "123456-Y"
             :tyyppi :kokonaishintainen
             :toteuma-id toteuma-id
             :tehtavat [{:toimenpidekoodi 1368 :maara 333 :tehtava-id toteuma-tehtava-id}]}
        lisatty (first (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-urakan-toteuma-ja-kokonaishintaiset-tehtavat
                                       +kayttaja-jvh+ {:toteuma tyo
                                                       :hakuparametrit {:urakka-id urakka-id
                                                                        :sopimus-id sopimus-id
                                                                        :alkupvm hoitokausi-aloituspvm :loppupvm hoitokausi-lopetuspvm
                                                                        :toimenpide nil :tehtava nil}}))]

    (is (= (get-in lisatty [:pvm]) uusi-tyon-pvm-eri-partitiossa) "Tallennetun työn alkanut pvm")
    (is (=marginaalissa? (get-in lisatty [:pituus]) 3707.390462) "Tallennetun työn paattynyt pvm")
    (is (= (get-in lisatty [:jarjestelmanlisaama]) false))
    (is (= (get-in lisatty [:nimi]) "Pistehiekoitus"))
    (is (= (get-in lisatty [:yksikko]) "tiekm") "Yksikkö")
    (is (= (get-in lisatty [:toimenpidekoodi]) 1368) "Tallennetun työn tehtävän toimenpidekoodi")
    (is (= (get-in lisatty [:maara]) 333M) "Tallennetun työn tehtävän määrä")))
