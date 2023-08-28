(ns harja.palvelin.palvelut.toteumat-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.palvelin.palvelut.toteumat :as toteumat]
            [harja.palvelin.palvelut.tehtavamaarat :as tehtavamaarat]
            [harja.palvelin.palvelut.materiaalit :refer :all]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]))

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
                          :toteumat (component/using
                                      (toteumat/->Toteumat)
                                      [:http-palvelin :db :db-replica :karttakuvat])
                          :hae-toteuman-materiaalitiedot (component/using
                                                           (->Materiaalit)
                                                           [:http-palvelin :db])
                          :tehtavamaarat (component/using
                                           (tehtavamaarat/->Tehtavamaarat false)
                                           [:http-palvelin :db]))))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; käyttää testidata.sql:stä tietoa
(deftest erilliskustannukset-haettu-oikein
  (let [alkupvm (pvm/luo-pvm 2005 9 1)
        loppupvm (pvm/luo-pvm 2006 10 30)
        res (kutsu-palvelua (:http-palvelin jarjestelma)
                            :urakan-erilliskustannukset +kayttaja-jvh+
                            {:urakka-id @oulun-alueurakan-2005-2010-id
                             :alkupvm   alkupvm
                             :loppupvm  loppupvm})
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
        ek {:urakka-id     @oulun-alueurakan-2005-2010-id
            :alkupvm       hoitokauden-alkupvm
            :loppupvm      hoitokauden-loppupvm
            :pvm           toteuman-pvm :rahasumma 20000.0
            :indeksin_nimi "MAKU 2005" :toimenpideinstanssi 1 :sopimus 1
            :tyyppi        "asiakastyytyvaisyysbonus" :lisatieto toteuman-lisatieto}
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

    ;; Testaa poistetuksi merkitsemistä
    (let [ek-id (:id lisatty)
          poistettu-id (kutsu-palvelua (:http-palvelin jarjestelma)
                         :poista-erilliskustannus +kayttaja-jvh+
                         {:id ek-id
                          :urakka-id @oulun-alueurakan-2005-2010-id})
          poistettu? (ffirst (q (str "SELECT poistettu FROM erilliskustannus WHERE id = " ek-id ";")))]
      (is (= poistettu-id ek-id) "Poistetun erilliskustannuksen id")
      (is (= poistettu? true)))

    ;; Siivoa
    (u
      (str "DELETE FROM erilliskustannus
                    WHERE pvm = '2005-12-12' AND lisatieto = '" toteuman-lisatieto "'"))))


(deftest tallenna-muut-tyot-toteuma-testi
  (let [tyon-pvm (konv/sql-timestamp (pvm/luo-pvm 2005 11 24)) ;;24.12.2005
        hoitokausi-aloituspvm (pvm/luo-pvm 2005 9 1)        ; 1.10.2005
        hoitokausi-lopetuspvm (pvm/luo-pvm 2006 8 30)       ;30.9.2006
        toteuman-lisatieto "Testikeissin lisätieto2"
        tyo {:urakka-id             @oulun-alueurakan-2005-2010-id :sopimus-id @oulun-alueurakan-2005-2010-paasopimuksen-id
             :alkanut               tyon-pvm :paattynyt tyon-pvm
             :hoitokausi-aloituspvm hoitokausi-aloituspvm :hoitokausi-lopetuspvm hoitokausi-lopetuspvm
             :suorittajan-nimi      "Alihankkijapaja Ky" :suorittajan-ytunnus "123456-Y"
             :tyyppi                :muutostyo
             :lisatieto             toteuman-lisatieto
             :tehtava               {:paivanhinta 456, :maara 2, :toimenpidekoodi 1368}}
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
        tyo {:urakka-id             urakka-id
             :sopimus-id            @oulun-alueurakan-2005-2010-paasopimuksen-id
             :alkanut               tyon-pvm :paattynyt tyon-pvm
             :hoitokausi-aloituspvm hoitokausi-aloituspvm
             :hoitokausi-lopetuspvm hoitokausi-lopetuspvm
             :suorittajan-nimi      "Alihankkijapaja Ky" :suorittajan-ytunnus "123456-Y"
             :tyyppi                :yksikkohintainen
             :toteuma-id            nil
             :lisatieto             toteuman-lisatieto
             :tehtavat              [{:toimenpidekoodi 1368 :maara 333}]}
        hae-summat #(->> (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :urakan-toteumien-tehtavien-summat
                                         +kayttaja-jvh+
                                         {:urakka-id  urakka-id
                                          :sopimus-id @oulun-alueurakan-2005-2010-paasopimuksen-id
                                          :alkupvm    hoitokausi-aloituspvm
                                          :loppupvm   hoitokausi-lopetuspvm
                                          :tyyppi     :yksikkohintainen})
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
                                    {:urakka-id  urakka-id
                                     :toteuma-id toteuma-id})
            muokattu-tyo (assoc tyo
                           :toteuma-id toteuma-id
                           :tehtavat [{:toimenpidekoodi 1369 :maara 666
                                       :tehtava-id      (get-in toteuma
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

(deftest tallenna-yksikkohintainen-toteuma-ja-poista-tehtava-testi
  (let [tyon-pvm (konv/sql-timestamp (pvm/luo-pvm 2005 11 24)) ;;24.12.2005
        hoitokausi-aloituspvm (pvm/luo-pvm 2005 9 1)        ; 1.10.2005
        hoitokausi-lopetuspvm (pvm/luo-pvm 2006 8 30)       ;30.9.2006
        urakka-id @oulun-alueurakan-2005-2010-id
        toteuman-lisatieto "Testikeissin lisätieto4"
        tyo {:urakka-id             urakka-id
             :sopimus-id            @oulun-alueurakan-2005-2010-paasopimuksen-id
             :alkanut               tyon-pvm :paattynyt tyon-pvm
             :hoitokausi-aloituspvm hoitokausi-aloituspvm
             :hoitokausi-lopetuspvm hoitokausi-lopetuspvm
             :suorittajan-nimi      "Alihankkijapaja Ky" :suorittajan-ytunnus "123456-Y"
             :tyyppi                :yksikkohintainen
             :toteuma-id            nil
             :lisatieto             toteuman-lisatieto
             :tehtavat              [{:toimenpidekoodi 1368 :maara 333}]}
        hae-summat #(->> (kutsu-palvelua (:http-palvelin jarjestelma)
                           :urakan-toteumien-tehtavien-summat
                           +kayttaja-jvh+
                           {:urakka-id  urakka-id
                            :sopimus-id @oulun-alueurakan-2005-2010-paasopimuksen-id
                            :alkupvm    hoitokausi-aloituspvm
                            :loppupvm   hoitokausi-lopetuspvm
                            :tyyppi     :yksikkohintainen})
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

      ;; Testaa päivitys ja tehtävän poisto

      (let [toteuma-id (get-in lisatty [:toteuma :toteuma-id])
            toteuma (kutsu-palvelua (:http-palvelin jarjestelma) :urakan-toteuma +kayttaja-jvh+
                      {:urakka-id  urakka-id :toteuma-id toteuma-id})
            muokattu-tyo (assoc tyo
                           :toteuma-id toteuma-id
                           :tehtavat [{:toimenpidekoodi 1368 :maara 333 :poistettu true
                                       :tehtava-id      (get-in toteuma
                                                          [:tehtavat 0 :tehtava-id])}
                                      {:toimenpidekoodi 1369 :maara 333}])
            muokattu (kutsu-palvelua (:http-palvelin jarjestelma)
                       :tallenna-urakan-toteuma-ja-yksikkohintaiset-tehtavat
                       +kayttaja-jvh+ muokattu-tyo)
            summat-muokkauksen-jalkeen (hae-summat)]

        (is (= (get-in muokattu [:toteuma :tehtavat 1 :toimenpidekoodi]) 1369))
        (is (= (get-in muokattu [:toteuma :tehtavat 1 :maara]) 333))
        (is (not (contains? summat-muokkauksen-jalkeen 1368)))
        (is (== 333 (get-in summat-muokkauksen-jalkeen [1369 :maara])))
        ;; Siivoa roskat
        (u (str "DELETE FROM toteuma_tehtava WHERE toteuma = " toteuma-id ";"))
        (u (str "DELETE FROM toteuma WHERE id = " toteuma-id))))))

(deftest tallenna-kokonaishintainen-toteuma-testi
  (let [tyon-pvm (konv/sql-timestamp (pvm/luo-pvm 2020 11 24)) ;;24.12.2020
        hoitokausi-aloituspvm (pvm/luo-pvm 2020 9 1)        ; 1.10.2020
        hoitokausi-lopetuspvm (pvm/luo-pvm 2021 8 30)       ;30.9.2021
        urakka-id (hae-oulun-alueurakan-2014-2019-id)
        tyo {:urakka-id             urakka-id
             :sopimus-id            (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
             :alkanut               tyon-pvm :paattynyt tyon-pvm
             :reitti                {:type  :multiline
                                     :lines [{:type :line, :points [[426948.180407029 7212765.48225361] [430650.8691 7212578.8262]]}]}
             :hoitokausi-aloituspvm hoitokausi-aloituspvm
             :hoitokausi-lopetuspvm hoitokausi-lopetuspvm
             :suorittajan-nimi      "Alihankkijapaja Ky" :suorittajan-ytunnus "123456-Y"
             :tyyppi                :kokonaishintainen
             :toteuma-id            nil
             :tehtavat              [{:toimenpidekoodi 1368 :maara 333}]}
        lisatty (some #(when (= (:toimenpidekoodi %) 1368) %)
                      (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :tallenna-urakan-toteuma-ja-kokonaishintaiset-tehtavat
                                      +kayttaja-jvh+ {:toteuma        tyo
                                                      :hakuparametrit {:urakka-id  urakka-id
                                                                       :sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
                                                                       :alkupvm    hoitokausi-aloituspvm :loppupvm hoitokausi-lopetuspvm
                                                                       :toimenpide nil :tehtava nil}}))]
    (is (= (get-in lisatty [:pvm]) tyon-pvm) "Tallennetun työn alkanut pvm")
    (is (= (get-in lisatty [:jarjestelmanlisaama]) false))
    (is (= (get-in lisatty [:nimi]) "Pistehiekoitus"))
    (is (= (get-in lisatty [:yksikko]) "tiekm") "Yksikkö")
    (is (= (get-in lisatty [:toimenpidekoodi]) 1368) "Tallennetun työn tehtävän toimenpidekoodi")
    (is (= (get-in lisatty [:maara]) 333M) "Tallennetun työn tehtävän määrä")))

(deftest hae-urakan-toteuma-kun-toteuma-ei-kuulu-urakkaan
  (let [toteuma-id (ffirst (q "SELECT id FROM toteuma WHERE urakka != " @oulun-alueurakan-2014-2019-id
                              " AND tyyppi = 'yksikkohintainen' LIMIT 1;"))]
    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :urakan-toteuma
                                                   +kayttaja-jvh+
                                                   {:urakka-id  @oulun-alueurakan-2014-2019-id
                                                    :toteuma-id toteuma-id})))))

(deftest tallenna-toteuma-ja-toteumamateriaalit-test
  (let [[urakka sopimus] (first (q (str "SELECT urakka, id FROM sopimus WHERE urakka=" @oulun-alueurakan-2005-2010-id)))
        toteuma (atom {:id     -5, :urakka urakka :sopimus sopimus :alkanut (pvm/luo-pvm 2005 11 24) :paattynyt (pvm/luo-pvm 2005 11 24)
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
                              {:toteuma            @toteuma
                               :toteumamateriaalit @tmt
                               ;pvm/luo-pvm 2006 8 30)]
                               :sopimus            sopimus})))
    (let [tmidt (flatten (q "SELECT id FROM toteuma_materiaali WHERE maara=192837"))
          tid (ffirst (q "SELECT id from toteuma WHERE suorittajan_nimi='UNIT TEST'"))
          uusi-lisatieto "NYT PITÄIS OLLA MUUTTUNUT."
          tierekisteriosoite {:numero 20
                              :alkuosa 1
                              :alkuetaisyys 1000
                              :loppuosa 2
                              :loppuetaisyys 2000}
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
                          (assoc :lisatieto uusi-lisatieto)
                          (assoc :tierekisteriosoite tierekisteriosoite)))

      (is (not (nil? (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-toteuma-ja-toteumamateriaalit +kayttaja-jvh+
                                     {:toteuma            @toteuma
                                      :toteumamateriaalit @tmt
                                      :hoitokausi         [(pvm/luo-pvm 2005 9 1) (pvm/luo-pvm 2006 8 30)]
                                      :sopimus            sopimus}))))

      (is (= 1 (ffirst (q "SELECT count(*) FROM toteuma WHERE suorittajan_nimi='UNIT TEST' AND poistettu IS NOT TRUE"))))
      (is (= 1 (ffirst (q "SELECT count(*) FROM toteuma_materiaali WHERE maara=192837 AND poistettu IS TRUE"))))
      (is (= 1 (ffirst (q "SELECT count(*) FROM toteuma_materiaali WHERE maara=8712 AND poistettu IS NOT TRUE"))))

      (is (= uusi-lisatieto (ffirst (q "SELECT lisatieto FROM toteuma WHERE id=" tid))))
      (is (= 8712 (int (ffirst (q "SELECT maara FROM toteuma_materiaali WHERE id=" (second tmidt))))))

      (let [toteuman-materiaalit (kutsu-palvelua (:http-palvelin jarjestelma) :hae-toteuman-materiaalitiedot +kayttaja-jvh+
                                   {:urakka-id urakka
                                    :toteuma-id tid}
                                   )
            haettu-osoite (:tierekisteriosoite toteuman-materiaalit)]
        (is (not (nil? (:tierekisteriosoite toteuman-materiaalit))))
        (is (not (nil? (:sijainti toteuman-materiaalit))))
        (is (= 20 (:numero haettu-osoite)))
        (is (= 1 (:alkuosa haettu-osoite)))
        (is (= 1000 (:alkuetaisyys haettu-osoite)))
        (is (= 2 (:loppuosa haettu-osoite)))
        (is (= 2000 (:loppuetaisyys haettu-osoite))))

      (u "DELETE FROM toteuma_materiaali WHERE id in (" (clojure.string/join "," tmidt) ")")
      (u "DELETE FROM toteuma WHERE id=" tid))))

(deftest materiaalin-pvm-muuttuu-cachet-pysyy-jiirissa
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
        sopimuksen-kaytetty-mat-ennen-odotettu (set [[2 #inst "2015-02-17T22:00:00.000-00:00" 1 1800M]
                                                     [2 #inst "2015-02-18T22:00:00.000-00:00" 7 200M]
                                                     [2 #inst "2015-02-18T22:00:00.000-00:00" 16 2000M]])
        sopimuksen-kaytetty-mat-jalkeen-odotettu (set [[2 #inst "2015-02-17T22:00:00.000-00:00" 1 1800M]
                                                       [2 #inst "2015-02-18T22:00:00.000-00:00" 7 200M]
                                                       [2 #inst "2015-02-13T22:00:00.000-00:00" 16 2100M]])
        hoitoluokittaiset-ennen-odotettu (set [[#inst "2015-02-17T22:00:00.000-00:00" 1 99 4 1800M]
                                               [#inst "2015-02-18T22:00:00.000-00:00" 7 99 4 200M]
                                               [#inst "2015-02-18T22:00:00.000-00:00" 16 99 4 2000M]])
        hoitoluokittaiset-jalkeen-odotettu (set [[#inst "2015-02-17T22:00:00.000-00:00" 1 99 4 1800M]
                                                 [#inst "2015-02-18T22:00:00.000-00:00" 7 99 4 200M]
                                                 [#inst "2015-02-13T22:00:00.000-00:00" 16 99 4 2100M]])
        sopimuksen-mat-kaytto-ennen (set (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id
                                                 (pvm-vali-sql-tekstina "alkupvm" "'2015-02-01' AND '2015-02-28'") ";")))
        hoitoluokittaiset-ennen (set (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka-id
                                             (pvm-vali-sql-tekstina "pvm" "'2015-02-01' AND '2015-02-28'") ";")))
        toteuman-id (ffirst (q (str "SELECT id FROM toteuma WHERE lisatieto = 'LYV-toteuma Natriumformiaatti';")))
        tm-id (ffirst (q (str "SELECT id FROM toteuma_materiaali WHERE toteuma = " toteuman-id ";")))


        toteuma {:id toteuman-id, :urakka urakka-id :sopimus sopimus-id
                 :alkanut (pvm/->pvm "14.02.2015") :paattynyt (pvm/->pvm "14.02.2015")
                 :tyyppi "materiaali" :suorittajan-nimi "Ahkera hommailija" :suorittajan-ytunnus 1234 :lisatieto "Pvm muutos ja cachet toimii"}
        tmt [{:id tm-id :materiaalikoodi 16 :maara 2100 :toteuma toteuman-id}]]
    ;; tarkistetaan että kaikki cachesta palautetut tulokset löytyvät expected-setistä
    (is (= sopimuksen-kaytetty-mat-ennen-odotettu sopimuksen-mat-kaytto-ennen ) "sopimuksen materiaalin käyttö cache ennen muutosta")
    (is (= hoitoluokittaiset-ennen-odotettu hoitoluokittaiset-ennen ) "hoitoluokittaisten cache ennen muutosta")

    ;; kyseessä päivitys, löytyvät kannasta jo ennen palvelukutsua
    (is (= 1 (ffirst (q (str "SELECT count(*) FROM toteuma_materiaali WHERE toteuma = " toteuman-id " AND poistettu IS NOT TRUE;")))))
    (is (= 1 (ffirst (q (str "SELECT count(*) FROM toteuma WHERE id=" toteuman-id " AND poistettu IS NOT TRUE;")))))

    (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-toteuma-ja-toteumamateriaalit +kayttaja-jvh+
                    {:toteuma toteuma
                     :toteumamateriaalit tmt
                     :hoitokausi [#inst "2014-09-30T21:00:00.000-00:00" #inst "2015-09-30T20:59:59.000-00:00"]
                     :sopimus sopimus-id})
    ;; lisäyksen jälkeenkin jutut löytyvät kannasta yhden kerran...
    (is (= 1 (ffirst (q (str "SELECT count(*) FROM toteuma_materiaali WHERE toteuma = " toteuman-id ";")))))
    (is (= 1 (ffirst (q (str "SELECT count(*) FROM toteuma WHERE id=" toteuman-id " AND poistettu IS NOT TRUE;")))))

    (let [sopimuksen-mat-kaytto-jalkeen (set (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id
                                                     (pvm-vali-sql-tekstina "alkupvm" "'2015-02-01' AND '2015-02-28'") ";")))
          hoitoluokittaiset-jalkeen (set (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka-id
                                                 (pvm-vali-sql-tekstina "pvm" "'2015-02-01' AND '2015-02-28'") ";")))]

      ;; lisäyksen jälkeen cachet päivittyvät oikein, vanhalla pvm:llä ollut määrä poistuu, ja uusi määrä uudelle päivällä
      (is (= sopimuksen-kaytetty-mat-jalkeen-odotettu sopimuksen-mat-kaytto-jalkeen ) "sopimuksen materiaalin käyttö cache jalkeen muutoksen")
      (is (= hoitoluokittaiset-jalkeen-odotettu hoitoluokittaiset-jalkeen ) "hoitoluokittaisten cache jalkeen muutoksen"))))


(deftest uusi-materiaali-cachet-pysyy-jiirissa
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
        sopimuksen-kaytetty-mat-ennen-odotettu (set [])
        sopimuksen-kaytetty-mat-jalkeen-odotettu (set [[2 #inst "2011-02-13T22:00:00.000-00:00" 16 200M]])
        hoitoluokittaiset-ennen-odotettu (set [])
        hoitoluokittaiset-jalkeen-odotettu (set [[#inst "2011-02-13T22:00:00.000-00:00" 16 99 4 200M]])
        sopimuksen-mat-kaytto-ennen (set (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id
                                                 (pvm-vali-sql-tekstina "alkupvm" "'2011-02-01' AND '2011-02-28'") ";")))
        hoitoluokittaiset-ennen (set (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka-id
                                             (pvm-vali-sql-tekstina "pvm" "'2011-02-01' AND '2011-02-28'") ";")))
        toteuman-id nil ;; koska uusi
        tm-id nil  ;; koska uusi
        toteuma {:id toteuman-id, :urakka urakka-id :sopimus sopimus-id
                 :alkanut (pvm/->pvm "14.02.2011") :paattynyt (pvm/->pvm "14.02.2011")
                 :tyyppi "materiaali" :suorittajan-nimi "Ahkera hommailija" :suorittajan-ytunnus 1234 :lisatieto "Täysin uusi jiirijutska"}
        tmt [{:id tm-id :materiaalikoodi 16 :maara 200 :toteuma toteuman-id}]]
    ;; tarkistetaan että kaikki cachesta palautetut tulokset löytyvät expected-setistä
    (is (= sopimuksen-kaytetty-mat-ennen-odotettu sopimuksen-mat-kaytto-ennen ) "sopimuksen materiaalin käyttö cache ennen muutosta")
    (is (= hoitoluokittaiset-ennen-odotettu hoitoluokittaiset-ennen ) "hoitoluokittaisten cache ennen muutosta")

    (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-toteuma-ja-toteumamateriaalit +kayttaja-jvh+
                    {:toteuma toteuma
                     :toteumamateriaalit tmt
                     :hoitokausi [#inst "2010-09-30T21:00:00.000-00:00" #inst "2011-09-30T20:59:59.000-00:00"]
                     :sopimus sopimus-id})

    (let [toteuman-id-jalkeen (ffirst (q (str "SELECT id FROM toteuma WHERE lisatieto='Täysin uusi jiirijutska';")))]
      (is (= 1 (ffirst (q (str "SELECT count(*) FROM toteuma_materiaali WHERE toteuma = " toteuman-id-jalkeen ";")))))
      (is (= 1 (ffirst (q (str "SELECT count(*) FROM toteuma WHERE id=" toteuman-id-jalkeen " AND poistettu IS NOT TRUE;"))))))

    (let [sopimuksen-mat-kaytto-jalkeen (set (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id
                                                     (pvm-vali-sql-tekstina "alkupvm" "'2011-02-01' AND '2011-02-28'") ";")))
          hoitoluokittaiset-jalkeen (set (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka-id
                                                 (pvm-vali-sql-tekstina "pvm" "'2011-02-01' AND '2011-02-28'") ";")))]

      ;; lisäyksen jälkeen cachet päivittyvät oikein, vanhalla pvm:llä ollut määrä poistuu, ja uusi määrä uudelle päivällä
      (is (= sopimuksen-kaytetty-mat-jalkeen-odotettu sopimuksen-mat-kaytto-jalkeen ) "sopimuksen materiaalin käyttö cache jalkeen muutoksen")
      (is (= hoitoluokittaiset-jalkeen-odotettu hoitoluokittaiset-jalkeen ) "hoitoluokittaisten cache jalkeen muutoksen"))))

(deftest kokonaishintaisen-toteuman-siirtymatiedot
  (let [toteuma-id (ffirst (q "SELECT id FROM toteuma WHERE urakka = 2 AND lisatieto = 'Tämä on käsin tekaistu juttu'"))
        tehtava-id (ffirst (q "SELECT id FROM tehtava WHERE nimi = 'Auraus ja sohjonpoisto';"))
        hae #(kutsu-palvelua (:http-palvelin jarjestelma)
                             :siirry-toteuma
                             %
                             toteuma-id)
        ok-tulos {:alkanut            #inst "2008-09-08T21:10:00.000000000-00:00"
                  :urakka-id          2
                  :tyyppi             "kokonaishintainen"
                  :hallintayksikko-id 12
                  :aikavali           {:alku  #inst "2007-09-30T21:00:00.000-00:00"
                                       :loppu #inst "2008-09-29T21:00:00.000-00:00"}
                  :tehtavat
                                      [{:toimenpidekoodi tehtava-id, :toimenpideinstanssi "23104"}]}
        ei-ok-tulos nil]

    (is (some? toteuma-id))

    ;; JVH voi hakea siirtymätiedot
    (tarkista-map-arvot ok-tulos (hae +kayttaja-jvh+))

    ;; Eri urakoitsijalla palautuu poikkeus oikeustarkistuksessa
    (is (thrown? Exception (hae +kayttaja-yit_uuvh+)))

    ;; Toteuman urakan urakoitsijan käyttäjä näkee siirtymätiedot
    (tarkista-map-arvot ok-tulos (hae +kayttaja-ulle+))))

(deftest toteuman-paivitys-sama-partitiolle
  (let [urakka-id (hae-urakan-id-nimella "Pudasjärven alueurakka 2007-2012")
        sopimus-id (hae-pudasjarven-alueurakan-paasopimuksen-id)
        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE urakka = " urakka-id " AND lisatieto = 'Tämä on käsin tekaistu juttu'")))
        toteuma-tehtava-id (ffirst (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id ";")))
        uusi-tyon-pvm-samassa-partitiossa (konv/sql-timestamp (pvm/luo-pvm 2009 11 24)) ;;24.12.2009
        hoitokausi-aloituspvm (pvm/luo-pvm 2008 9 1)        ; 1.10.2016
        hoitokausi-lopetuspvm (pvm/luo-pvm 2010 8 30)       ;30.9.2017
        tyo {:urakka-id             urakka-id
             :sopimus-id            sopimus-id
             :alkanut               uusi-tyon-pvm-samassa-partitiossa :paattynyt uusi-tyon-pvm-samassa-partitiossa
             :reitti                {:type  :multiline
                                     :lines [{:type :line, :points [[426948.180407029 7212765.48225361] [430650.8691 7212578.8262]]}]}
             :hoitokausi-aloituspvm hoitokausi-aloituspvm
             :hoitokausi-lopetuspvm hoitokausi-lopetuspvm
             :suorittajan-nimi      "Alihankkijapaja Ky" :suorittajan-ytunnus "123456-Y"
             :tyyppi                :kokonaishintainen
             :lisatieto             "Tämä on käsin tekaistu juttu"
             :toteuma-id            toteuma-id
             :tehtavat              [{:toimenpidekoodi 1368 :maara 333 :tehtava-id toteuma-tehtava-id}]}
        lisatty (first (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-urakan-toteuma-ja-kokonaishintaiset-tehtavat
                                       +kayttaja-jvh+ {:toteuma        tyo
                                                       :hakuparametrit {:urakka-id  urakka-id
                                                                        :sopimus-id sopimus-id
                                                                        :alkupvm    hoitokausi-aloituspvm :loppupvm hoitokausi-lopetuspvm
                                                                        :toimenpide nil :tehtava nil}}))
        toteuma-id-jalkeen (ffirst (q (str "SELECT id FROM toteuma WHERE urakka = " urakka-id " AND lisatieto = 'Tämä on käsin tekaistu juttu'")))]

    (is (= toteuma-id toteuma-id-jalkeen) "Toteuman id ei saa muuttua")
    (is (= (get-in lisatty [:pvm]) uusi-tyon-pvm-samassa-partitiossa) "Tallennetun työn alkanut pvm")
    (is (= (get-in lisatty [:jarjestelmanlisaama]) false))
    (is (= (get-in lisatty [:nimi]) "Pistehiekoitus"))
    (is (= (get-in lisatty [:yksikko]) "tiekm") "Yksikkö")
    (is (= (get-in lisatty [:toimenpidekoodi]) 1368) "Tallennetun työn tehtävän toimenpidekoodi")
    (is (= (get-in lisatty [:maara]) 333M) "Tallennetun työn tehtävän määrä")))


(deftest toteuman-paivitys-siirtaa-eri-partitiolle
  (let [urakka-id (hae-urakan-id-nimella "Pudasjärven alueurakka 2007-2012")
        sopimus-id (hae-pudasjarven-alueurakan-paasopimuksen-id)
        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE urakka = " urakka-id " AND lisatieto = 'Tämä on käsin tekaistu juttu'")))
        toteuma-tehtava-id (ffirst (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id ";")))
        uusi-tyon-pvm-eri-partitiossa (konv/sql-timestamp (pvm/luo-pvm 2020 11 24)) ;;24.12.2020
        hoitokausi-aloituspvm (pvm/luo-pvm 2020 9 1)
        hoitokausi-lopetuspvm (pvm/luo-pvm 2021 8 30)       ;30.9.2017
        tyo {:urakka-id             urakka-id
             :sopimus-id            sopimus-id
             :alkanut               uusi-tyon-pvm-eri-partitiossa :paattynyt uusi-tyon-pvm-eri-partitiossa
             :reitti                {:type  :multiline
                                     :lines [{:type :line, :points [[426948.180407029 7212765.48225361] [430650.8691 7212578.8262]]}]}
             :hoitokausi-aloituspvm hoitokausi-aloituspvm
             :hoitokausi-lopetuspvm hoitokausi-lopetuspvm
             :suorittajan-nimi      "Alihankkijapaja Ky" :suorittajan-ytunnus "123456-Y"
             :tyyppi                :kokonaishintainen
             :lisatieto             "Tämä on käsin tekaistu juttu"
             :toteuma-id            toteuma-id
             :tehtavat              [{:toimenpidekoodi 1368 :maara 444 :tehtava-id toteuma-tehtava-id}]}
        kaikki (kutsu-palvelua (:http-palvelin jarjestelma)
                               :tallenna-urakan-toteuma-ja-kokonaishintaiset-tehtavat
                               +kayttaja-jvh+ {:toteuma        tyo
                                               :hakuparametrit {:urakka-id  urakka-id
                                                                :sopimus-id sopimus-id
                                                                :alkupvm    hoitokausi-aloituspvm :loppupvm hoitokausi-lopetuspvm
                                                                :toimenpide nil :tehtava nil}})
        lisatty (some #(when (= (:toimenpidekoodi %) 1368) %) kaikki)
        toteuma-id-jalkeen (ffirst (q (str "SELECT id FROM toteuma WHERE urakka = " urakka-id " AND lisatieto = 'Tämä on käsin tekaistu juttu'")))]
    (is (= toteuma-id toteuma-id-jalkeen) "Toteuman id ei saa muuttua")
    (is (= (get-in lisatty [:pvm]) uusi-tyon-pvm-eri-partitiossa) "Tallennetun työn alkanut pvm")
    (is (= (get-in lisatty [:pvm]) uusi-tyon-pvm-eri-partitiossa) "Tallennetun työn alkanut pvm")
    (is (= (get-in lisatty [:jarjestelmanlisaama]) false))
    (is (= (get-in lisatty [:nimi]) "Pistehiekoitus"))
    (is (= (get-in lisatty [:yksikko]) "tiekm") "Yksikkö")
    (is (= (get-in lisatty [:toimenpidekoodi]) 1368) "Tallennetun työn tehtävän toimenpidekoodi")
    (is (= (get-in lisatty [:maara]) 444M) "Tallennetun työn tehtävän määrä")))

(defn luo-testitoteuma [urakka-id sopimus-id alkanut toteuma-id]
  {:sopimus       sopimus-id
   :alkanut       (konv/sql-date alkanut) :paattynyt (konv/sql-date alkanut)
   :reitti        {:type  :multiline
                   :lines [{:type :line, :points [[426948.180407029 7212765.48225361] [430650.8691 7212578.8262]]}]}
   :id            toteuma-id
   :alkuosa       1
   :numero        4
   :alkuetaisyys  1
   :kayttaja      1
   :ytunnus       "123456-Y"
   :urakka        urakka-id
   :suorittaja    "Alihankkijapaja Ky"
   :loppuetaisyys 2
   :loppuosa      2
   :tyyppi        "kokonaishintainen"
   :lisatieto     "Tämä on käsin tekaistu juttu"})

(deftest toteuman-paivitys-ei-muuta-lukumaaraa
  (let [toteuma-count (ffirst (q (str "SELECT count(id) FROM toteuma")))
        urakka-id (hae-urakan-id-nimella "Pudasjärven alueurakka 2007-2012")
        sopimus-id (hae-pudasjarven-alueurakan-paasopimuksen-id)
        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE urakka = " urakka-id " AND lisatieto = 'Tämä on käsin tekaistu juttu'")))]

    ;; Satunnaisia päivämääriä vuosina 2000-2030... (myös eri partitiolle)
    (doseq [alkanut (map #(pvm/->pvm (str (rand-int 28) "."
                                          (rand-int 170) "."
                                          %))
                         (range 2000 2030))]
      (do
        (toteumat-q/paivita-toteuma<! (:db jarjestelma)
                                      (luo-testitoteuma urakka-id sopimus-id alkanut toteuma-id))
        (is (= toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE urakka = " urakka-id " AND lisatieto = 'Tämä on käsin tekaistu juttu'")))) "Toteuma id ei saa muuttua.")
        (is (= alkanut (ffirst (q (str "SELECT alkanut FROM toteuma WHERE urakka = " urakka-id " AND lisatieto = 'Tämä on käsin tekaistu juttu'")))) "Toteuma alkanut OK")
        (is (= toteuma-count (ffirst (q (str "SELECT count(id) FROM toteuma")))) "Toteuma count ei saa muuttua.")))))

(deftest hae-urakan-kokonaishintaisten-toteumien-tehtavien-paivakohtaiset-summat
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
        talvihoito-tpi-id (hae-oulun-alueurakan-talvihoito-tpi-id)
        odotettu [{:pvm #inst "2017-01-31T22:00:00.000-00:00", :toimenpidekoodi 1369, :maara 666M, :jarjestelmanlisaama true, :nimi "Suolaus", :yksikko "tiekm"}
                  {:pvm #inst "2015-01-31T22:00:00.000-00:00", :toimenpidekoodi 1369, :maara 123M, :jarjestelmanlisaama true, :nimi "Suolaus", :yksikko "tiekm"}]

        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-kokonaishintaisten-toteumien-tehtavien-paivakohtaiset-summat
                                +kayttaja-jvh+ {:urakka-id urakka-id
                                                :sopimus-id sopimus-id
                                                :alkupvm (pvm/->pvm "1.10.2014")
                                                :loppupvm (pvm/->pvm "1.1.2018")
                                                :toimenpide talvihoito-tpi-id
                                                :tehtava nil})]
    (is (= odotettu vastaus) "hae-urakan-kokonaishintaisten-toteumien-tehtavien-paivakohtaiset-summat oikein")))
