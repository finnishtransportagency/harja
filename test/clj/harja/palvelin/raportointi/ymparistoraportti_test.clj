(ns harja.palvelin.raportointi.ymparistoraportti-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.domain.hoitoluokat :as hoitoluokat]
            [harja.kyselyt
             [urakat :as urk-q]
             [raportit :as raportit-q]]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi.testiapurit :as apurit]
            [clojure.string :as str]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :pdf-vienti (component/using
                        (pdf-vienti/luo-pdf-vienti)
                        [:http-palvelin])
          :raportointi (component/using
                         (raportointi/luo-raportointi)
                         [:db :pdf-vienti])
          :raportit (component/using
                      (raportit/->Raportit)
                      [:http-palvelin :db :raportointi :pdf-vienti])))))
  (raportit-q/paivita_raportti_toteutuneet_materiaalit (:db jarjestelma))
  (async/<!! (async/go-loop
               [k 1]
               (let [materiaali-cache-ajettu? (ffirst (q "SELECT exists(SELECT 1 FROM raportti_toteutuneet_materiaalit)"))]
                 (when (and (not materiaali-cache-ajettu?)
                         (< k 10))
                   (async/<! (async/timeout 1000))
                   (recur (inc k))))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      urakkatieto-fixture
                      jarjestelma-fixture))

(defn varmista-tietokannan-tila []
  ;; Ympäristöraportin testaamisen helpottamiseksi osa datasta syötetään suoraa tietokantaan, ilman niihin liittyviä toteumia.
  ;; Tämä helpottaa joidenkin asioiden testaamista.
  ;; Niinpä asetetaan kanta sellaiseen tilaan, että testaaminen on mahdollista
  (u (str "delete from urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019');"))
  (u (str "delete from urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019');"))
  (u (str "
-- Hoitoluokittaiset vastaavasti Ouluun ja Kajaaniin
INSERT INTO urakan_materiaalin_kaytto_hoitoluokittain (pvm, materiaalikoodi, talvihoitoluokka, urakka, maara)
VALUES
-- Talvisuolaa 1000t per urakka
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 0, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 300),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 1, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 2, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 3, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 4, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 5, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 6, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 7, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 8, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 9, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 100, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),

-- uudet talvihoitoluokat
('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 1, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 2, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 3, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 4, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 5, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 6, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 7, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 8, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 9, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 10, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 11, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),

('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 0, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 300),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 1, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 2, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 3, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 4, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 100),

-- Hiekoitushiekkaa 1000t per urakka
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 0, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 300),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 1, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 2, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 3, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 4, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 0, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 300),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 1, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 2, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 3, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 4, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 100);")))

(defn tarkistusfunktio [sisalto]
  (let [nayta-suunnittelu? (if (= (count (:rivi sisalto)) 17) true false)
        rivi (:rivi sisalto)
        materiaali (or (:arvo (second (second rivi))) (second rivi))
        [yhteensa suunniteltu prosentti] (if nayta-suunnittelu? (take-last 3 rivi) [(last rivi) nil nil])
        hoitokaudet (if nayta-suunnittelu? (drop-last 3 (drop 2 rivi)) (drop-last 1 (drop 2 rivi)))
        solu? #(or (nil? %)
                 (= "–" %)
                 (and (not (nil? %)) (number? %))
                 (and (apurit/raporttisolu? %) (nil? (apurit/raporttisolun-arvo %)))
                 (and (apurit/raporttisolu? %) (number? (apurit/raporttisolun-arvo %)))
                 (apurit/tyhja-raporttisolu? %))]

    (or
      ; hoitoluokittainen materiaalitieto
      (and (some #(= (str " - " (:nimi %)) (first sisalto))
             hoitoluokat/talvihoitoluokat))
      ; datarivi
      (and
        (= (count rivi) (if nayta-suunnittelu? 17 15))
        (string? materiaali)
        (every? solu? hoitokaudet)
        (solu? yhteensa)
        (solu? suunniteltu)
        (or (nil? prosentti) (nil? (apurit/raporttisolun-arvo prosentti))
          (and (number? prosentti) (or (= 0 prosentti) (pos? prosentti)))
          (and (vector? prosentti) (= "%" (:yksikko (second prosentti))))) ;; Tämä on näitä keissejä varten [:arvo-ja-yksikko {:arvo 277.625, :yksikko "%", :desimaalien-maara 2}]
         (or (and (every? nil? hoitokaudet) (nil? yhteensa) (nil? (apurit/raporttisolun-arvo yhteensa)))
          (= (apurit/raporttisolun-arvo yhteensa)
            (reduce (fn [summa h]
                      (+ summa (if (number? (apurit/raporttisolun-arvo h))
                                 (apurit/raporttisolun-arvo h)
                                 0)))
              0
              hoitokaudet)))
        (or
          (nil? prosentti) (nil? (apurit/raporttisolun-arvo prosentti))
          (nil? yhteensa) (nil? (apurit/raporttisolun-arvo yhteensa))
          (nil? suunniteltu) (nil? (apurit/raporttisolun-arvo suunniteltu))
          (= (/ (* 100.0 (apurit/raporttisolun-arvo yhteensa)) (apurit/raporttisolun-arvo suunniteltu))
            (if (number? prosentti)
              prosentti
              (apurit/raporttisolun-arvo prosentti))))))))

(deftest raportin-suoritus-urakalle-toimii
  (let [_ (varmista-tietokannan-tila)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :ymparistoraportti
                   :konteksti "urakka"
                   :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                   :parametrit {:alkupvm (c/to-date (t/local-date 2015 10 1))
                                :loppupvm (c/to-date (t/local-date 2016 9 30))}})
        talvisuolojen-kaytto (nth vastaus 3)]
    (is (vector? vastaus))
    (let [raportin-nimi "Ympäristöraportti"
          teksti "Oulun alueurakka 2014-2019 (1238), Ympäristöraportti ajalta 01.10.2015 - 30.09.2016"
          otsikko "Talvisuolat"
          talvisuolataulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (is (= raportin-nimi (:nimi (second vastaus))))
      (is (= teksti (second (nth vastaus 2))))
      (is (= talvisuolojen-kaytto [:teksti "Kokonaisarvot ovat tarkkoja toteumamääriä, hoitoluokittainen jaottelu perustuu reittitietoon ja voi sisältää epätarkkuutta."]) "talvisuolan toteutunut määrä")
      (apurit/tarkista-taulukko-sarakkeet talvisuolataulukko
        {:leveys "2%" :tyyppi :avattava-rivi}
        {:otsikko "Materiaali"}
        {:otsikko "10/15"}
        {:otsikko "11/15"}
        {:otsikko "12/15"}
        {:otsikko "01/16"}
        {:otsikko "02/16"}
        {:otsikko "03/16"}
        {:otsikko "04/16"}
        {:otsikko "05/16"}
        {:otsikko "06/16"}
        {:otsikko "07/16"}
        {:otsikko "08/16"}
        {:otsikko "09/16"}
        {:otsikko "Yhteensä (t)"}
        {:otsikko "Suunniteltu (t)"}
        {:otsikko "Tot-%"})
      (apurit/tarkista-taulukko-kaikki-rivit talvisuolataulukko tarkistusfunktio))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [_ (varmista-tietokannan-tila)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :ymparistoraportti
                   :konteksti "hallintayksikko"
                   :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                   :parametrit {:alkupvm (c/to-date (t/local-date 2015 10 1))
                                :loppupvm (c/to-date (t/local-date 2016 9 30))
                                :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (let [raportin-nimi "Ympäristöraportti"
          teksti "Pohjois-Pohjanmaa, Ympäristöraportti ajalta 01.10.2015 - 30.09.2016"
          otsikko "Talvisuolat"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (is (= raportin-nimi (:nimi (second vastaus))))
      (is (= teksti (second (nth vastaus 2))))
      (apurit/tarkista-taulukko-sarakkeet taulukko
        {:leveys "2%" :tyyppi :avattava-rivi}
        {:otsikko "Materiaali"}
        {:otsikko "10/15"}
        {:otsikko "11/15"}
        {:otsikko "12/15"}
        {:otsikko "01/16"}
        {:otsikko "02/16"}
        {:otsikko "03/16"}
        {:otsikko "04/16"}
        {:otsikko "05/16"}
        {:otsikko "06/16"}
        {:otsikko "07/16"}
        {:otsikko "08/16"}
        {:otsikko "09/16"}
        {:otsikko "Yhteensä (t)"}
        {:otsikko "Suunniteltu (t)"}
        {:otsikko "Tot-%"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko tarkistusfunktio))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [_ (varmista-tietokannan-tila)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :ymparistoraportti
                   :konteksti "koko maa"
                   :parametrit {:alkupvm (c/to-date (t/local-date 2015 10 1))
                                :loppupvm (c/to-date (t/local-date 2016 9 30))
                                :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (let [raportin-nimi "Ympäristöraportti"
          teksti "KOKO MAA, Ympäristöraportti ajalta 01.10.2015 - 30.09.2016"
          otsikko "Talvisuolat"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (is (= raportin-nimi (:nimi (second vastaus))))
      (is (= teksti (second (nth vastaus 2))))
      (apurit/tarkista-taulukko-sarakkeet taulukko
        {:leveys "2%" :tyyppi :avattava-rivi}
        {:otsikko "Materiaali"}
        {:otsikko "10/15"}
        {:otsikko "11/15"}
        {:otsikko "12/15"}
        {:otsikko "01/16"}
        {:otsikko "02/16"}
        {:otsikko "03/16"}
        {:otsikko "04/16"}
        {:otsikko "05/16"}
        {:otsikko "06/16"}
        {:otsikko "07/16"}
        {:otsikko "08/16"}
        {:otsikko "09/16"}
        {:otsikko "Yhteensä (t)"}
        {:otsikko "Suunniteltu (t)"}
        {:otsikko "Tot-%"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko tarkistusfunktio))))

(deftest ymparisto-materiaali-ja-suolaraportin-tulokset-tasmaavat
  (let [_ (varmista-tietokannan-tila)
        urakka-id (hae-oulun-alueurakan-2014-2019-id)
        param {:alkupvm (c/to-date (t/local-date 2014 10 1))
               :loppupvm (c/to-date (t/local-date 2015 9 30))
               :urakkatyyppi :hoito}
        raportin-nimi "Ympäristöraportti"
        teksti "Oulun alueurakka 2014-2019 (1238), Ympäristöraportti ajalta 01.10.2014 - 30.09.2015"
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :ymparistoraportti
                   :konteksti "urakka"
                   :urakka-id urakka-id
                   :parametrit param})
        talvisuolat (apurit/taulukko-otsikolla vastaus "Talvisuolat")
        formiaatit (apurit/taulukko-otsikolla vastaus "Formiaatit")
        kesasuolat (apurit/taulukko-otsikolla vastaus "Kesäsuola")
        hiekoitushiekat (apurit/taulukko-otsikolla vastaus "Hiekoitushiekka")
        paikkausmateriaalit (apurit/taulukko-otsikolla vastaus "Paikkausmateriaalit")
        ymp-kaytetty-suola (apurit/raporttisolun-arvo (apurit/taulukon-solu talvisuolat 6 0))
        ymp-kaytetty-suolaliuos (apurit/raporttisolun-arvo (apurit/taulukon-solu talvisuolat 14 2))
        ;ymp-kaytetty-suolaliuos-hlk-ei-tiedossa (apurit/raporttisolun-arvo (apurit/taulukon-solu talvisuolat 6 4))
        ymp-kaikki-talvisuola-helmikuu (apurit/raporttisolun-arvo (apurit/taulukon-solu talvisuolat 6 4))
        ymp-kaikki-talvisuola-yht (apurit/raporttisolun-arvo (apurit/taulukon-solu talvisuolat 14 4))
        ymp-kaytetty-natriumformiaatti (apurit/raporttisolun-arvo (apurit/taulukon-solu formiaatit 6 1))
        ymp-formiaatit-yht (apurit/raporttisolun-arvo (apurit/taulukon-solu formiaatit 14 3))
        ymp-hiekka-totpros (apurit/raporttisolun-arvo (apurit/taulukon-solu hiekoitushiekat 14 0))
        ymp-hiekka-suunniteltu (apurit/raporttisolun-arvo (apurit/taulukon-solu hiekoitushiekat 15 0))
        ymp-paikkaus-kuumapaallyste (apurit/raporttisolun-arvo (apurit/taulukon-solu paikkausmateriaalit 3 0))
        ymp-paikkaus-massasaumaus (apurit/raporttisolun-arvo (apurit/taulukon-solu paikkausmateriaalit 4 1))
        materiaali (apurit/taulukko-otsikolla
                     (kutsu-palvelua (:http-palvelin jarjestelma)
                       :suorita-raportti
                       +kayttaja-jvh+
                       {:nimi :materiaaliraportti
                        :konteksti "urakka"
                        :urakka-id urakka-id
                        :parametrit param})
                     "Oulun alueurakka 2014-2019, Materiaaliraportti ajalta 01.10.2014 - 30.09.2015")
        mat-kaytetty-kaikki-talvisuola (apurit/taulukon-solu materiaali 1 0)
        mat-kaytetty-suola (apurit/taulukon-solu materiaali 2 0)
        mat-kaytetty-talvisuolaliuos (apurit/taulukon-solu materiaali 3 0)
        mat-kaytetty-natriumformiaatti (apurit/taulukon-solu materiaali 4 0)
        mat-kaytetty-hiekka (apurit/taulukon-solu materiaali 5 0)
        mat-kaytetty-kuumapaallyste (apurit/taulukon-solu materiaali 6 0)
        mat-kaytetty-massasaumaus (apurit/taulukon-solu materiaali 7 0)
        suola-sakko-taulukko (apurit/taulukko-otsikolla
                (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :suolasakko
                   :konteksti "urakka"
                   :urakka-id urakka-id
                   :parametrit param})
                "Oulun alueurakka 2014-2019, Suolasakkoraportti ajalta 01.10.2014 - 30.09.2015")
        suolasakko-kaytetty-suola (apurit/taulukon-solu suola-sakko-taulukko 8 0)]
    (is (= raportin-nimi (:nimi (second vastaus))))
    (is (= teksti (second (nth vastaus 2))))
    (is (= ymp-kaytetty-suolaliuos mat-kaytetty-talvisuolaliuos 1800M)
      "Ympäristö- ja materiaaliraportin pitäisi laskea käytetty Talvisuolaliuos NaCl samalla tavalla")
    (is (= ymp-formiaatit-yht mat-kaytetty-kaikki-talvisuola 2000M)
      "Suolaliuokset yhteensä täsmää")
    ;(is (= ymp-kaytetty-suolaliuos-hlk-ei-tiedossa 1800M) "hoitoluokka ei tiedossa")
    (is (= ymp-kaytetty-natriumformiaatti mat-kaytetty-natriumformiaatti 2000M)
      "Ympäristö- ja materiaaliraportin pitäisi laskea käytetty Natriumformiaatti samalla tavalla")
    (is (= suolasakko-kaytetty-suola
          (+ ymp-kaytetty-suolaliuos ymp-kaytetty-suola)
          (+ mat-kaytetty-suola mat-kaytetty-talvisuolaliuos)
          2000M) "Ympäristö- ja materiaaliraportin pitäisi laskea käytetty Natriumformiaatti samalla tavalla")
    (is (= 2000M ymp-kaikki-talvisuola-helmikuu ymp-kaikki-talvisuola-yht) "Kaikki talvisuola yhteensä")
    ;; Testidatasta riippuvia testejä.. vähän huonoja
    (is (= 0 ymp-hiekka-totpros) "Ympäristöraportin hiekan toteumaprosentin pitäisi olla nolla, toteumia ei ole")
    (is (= 0 mat-kaytetty-hiekka) "Materiaaliraportin pitäisi raportoida hiekan määräksi nolla, koska toteumia ei ole")
    (is (= 800M ymp-hiekka-suunniteltu) "Onko testidata muuttunut? Ympäristöraportti odottaa, että hiekoitushiekkaa on suunniteltu 800t")
    (is (= 1000M ymp-paikkaus-kuumapaallyste mat-kaytetty-kuumapaallyste) "Onko testidata muuttunut? Ympäristöraportti odottaa, että kuumapaallyste-tehtävällä on toteumaa 1000t")
    (is (= 1000M ymp-paikkaus-massasaumaus mat-kaytetty-massasaumaus) "Onko testidata muuttunut? Ympäristöraportti odottaa, että massasaumaus-tehtävällä on toteumaa 1000t")))

(deftest raportin-suunnitellut-arvot-mhu
  (let [_ (varmista-tietokannan-tila)
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        param {:alkupvm (c/to-date (t/local-date 2021 10 1))
               :loppupvm (c/to-date (t/local-date 2022 9 30))
               :urakkatyyppi :hoito}
        raportin-nimi "Ympäristöraportti"
        teksti "Oulun MHU 2019-2024 (1238), Ympäristöraportti ajalta 01.10.2021 - 30.09.2022"
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :ymparistoraportti
                   :konteksti "urakka"
                   :urakka-id urakka-id
                   :parametrit param})
        paikkausmateriaalit (apurit/taulukko-otsikolla vastaus "Paikkausmateriaalit")
        ymp-paikkaus-kuumapaallyste-suunniteltu (apurit/raporttisolun-arvo (apurit/taulukon-solu paikkausmateriaalit 15 0))]
    (is (= raportin-nimi (:nimi (second vastaus))))
    (is (= teksti (second (nth vastaus 2))))
    (is (= 999M ymp-paikkaus-kuumapaallyste-suunniteltu)
      "Onko testidata muuttunut? Ympäristöraportti odottaa, että 'Päällysteiden paikkaus' tehtävälle 'kuumapäällyste' on suunniteltu 999t")))

(deftest jokainen-materiaali-vain-kerran
  (let [_ (varmista-tietokannan-tila)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :ymparistoraportti
                   :konteksti "urakka"
                   :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                   :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                :loppupvm (c/to-date (t/local-date 2015 9 30))}})]
    (testing "Talvisuola -taulukossa nimet vain kerran"
      (let [talvisuolataulukko (apurit/taulukko-otsikolla vastaus "Talvisuolat")
            nimet (filter #(not (or (str/includes? % "Käsin kirjattu")
                                  (str/includes? % "Poikkeama (+/-)")))
                    (apurit/taulukon-sarake talvisuolataulukko 1))]
        (is (= (count nimet) (count (into #{} nimet))) "Materiaalien nimet ovat ympäristöraportissa vain kerran.")))
    (testing "Formiaatti -taulukossa nimet vain kerran"
      (let [formiaattitaulukko (apurit/taulukko-otsikolla vastaus "Formiaatit")
            nimet (filter #(not (str/includes? % "Ei tiedossa"))
                    (apurit/taulukon-sarake formiaattitaulukko 1))]
        (is (= (count nimet) (count (into #{} nimet))) "Materiaalien nimet ovat ympäristöraportissa vain kerran.")))
    (testing "Kesäsuola -taulukossa nimet vain kerran"
      (let [kesasuolataulukko (apurit/taulukko-otsikolla vastaus "Kesäsuola")
            nimet (filter #(not (str/includes? % "Ei tiedossa"))
                    (apurit/taulukon-sarake kesasuolataulukko 1))]
        (is (= (count nimet) (count (into #{} nimet))) "Materiaalien nimet ovat ympäristöraportissa vain kerran.")))
    (testing "Hiekoitushiekka -taulukossa nimet vain kerran"
      (let [hiekoitushiekkataulukko (apurit/taulukko-otsikolla vastaus "Hiekoitushiekka")
            nimet (filter #(not (str/includes? % "Ei tiedossa"))
                    (apurit/taulukon-sarake hiekoitushiekkataulukko 1))]
        (is (= (count nimet) (count (into #{} nimet))) "Materiaalien nimet ovat ympäristöraportissa vain kerran.")))
    (testing "Murskeet -taulukossa nimet vain kerran"
      (let [murskeettaulukko (apurit/taulukko-otsikolla vastaus "Murskeet")
            nimet (filter #(not (str/includes? % "Ei tiedossa"))
                    (apurit/taulukon-sarake murskeettaulukko 1))]
        (is (= (count nimet) (count (into #{} nimet))) "Materiaalien nimet ovat ympäristöraportissa vain kerran.")))
    (testing "Paikkausmateriaalit -taulukossa nimet vain kerran"
      (let [muut-taulukko (apurit/taulukko-otsikolla vastaus "Paikkausmateriaalit")
            nimet (filter #(not (str/includes? % "Ei tiedossa"))
                    (apurit/taulukon-sarake muut-taulukko 1))]
        (is (= (count nimet) (count (into #{} nimet))) "Materiaalien nimet ovat ympäristöraportissa vain kerran.")))
    (testing "Muut materiaalit -taulukossa nimet vain kerran"
      (let [muut-taulukko (apurit/taulukko-otsikolla vastaus "Muut materiaalit")
            nimet (filter #(not (str/includes? % "Ei tiedossa"))
                    (apurit/taulukon-sarake muut-taulukko 1))]
        (is (= (count nimet) (count (into #{} nimet))) "Materiaalien nimet ovat ympäristöraportissa vain kerran.")))))

(deftest ymparistoraportin-hoitoluokittaiset-maarat
  (let [_ (varmista-tietokannan-tila)
        vastaus-pop-ely (kutsu-palvelua (:http-palvelin jarjestelma)
                          :suorita-raportti
                          +kayttaja-jvh+
                          {:nimi :ymparistoraportti
                           :konteksti "hallintayksikko"
                           :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                           :parametrit {:alkupvm (c/to-date (t/local-date 2017 10 1))
                                        :loppupvm (c/to-date (t/local-date 2018 9 30))
                                        :urakkatyyppi :hoito}})
        vastaus-oulu (kutsu-palvelua (:http-palvelin jarjestelma)
                       :suorita-raportti
                       +kayttaja-jvh+
                       {:nimi :ymparistoraportti
                        :konteksti "urakka"
                        :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                        :parametrit {:alkupvm (c/to-date (t/local-date 2017 10 1))
                                     :loppupvm (c/to-date (t/local-date 2018 9 30))
                                     :urakkatyyppi :hoito}})]

    (is (vector? vastaus-pop-ely))
    (let [raportin-nimi "Ympäristöraportti"
          teksti-pop-ely "Pohjois-Pohjanmaa, Ympäristöraportti ajalta 01.10.2017 - 30.09.2018"
          otsikko-pop-ely "Talvisuolat"
          taulukko-pop-ely (apurit/taulukko-otsikolla vastaus-pop-ely otsikko-pop-ely)
          pop-ely-talvisuola-luokka-IsE (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-pop-ely 6 1))
          pop-ely-talvisuola-luokka-Is (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-pop-ely 6 2))
          pop-ely-talvisuola-luokka-I (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-pop-ely 6 3))
          pop-ely-talvisuola-luokka-Ib (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-pop-ely 6 4))
          pop-ely-talvisuola-luokka-Ic (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-pop-ely 6 5))


          teksti-oulu "Oulun alueurakka 2014-2019 (1238), Ympäristöraportti ajalta 01.10.2017 - 30.09.2018"
          otsikko-oulu "Talvisuolat"
          taulukko-oulu (apurit/taulukko-otsikolla vastaus-oulu otsikko-oulu)
          oulu-talvisuola-luokka-IsE (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 6 1))
          oulu-talvisuola-luokka-Is (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 6 2))
          oulu-talvisuola-luokka-I (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 6 3))
          oulu-talvisuola-luokka-Ib (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 6 4))
          oulu-talvisuola-luokka-Ic (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 6 5))]
      (is (= raportin-nimi (:nimi (second vastaus-pop-ely))))
      (is (= teksti-pop-ely (second (nth vastaus-pop-ely 2))))
      (is (= pop-ely-talvisuola-luokka-IsE 600M))
      (is (= pop-ely-talvisuola-luokka-Is 400M))
      (is (= pop-ely-talvisuola-luokka-I 400M))
      (is (= pop-ely-talvisuola-luokka-Ib 400M))
      (is (= pop-ely-talvisuola-luokka-Ic 200M))

      (is (= teksti-oulu (second (nth vastaus-oulu 2))))
      (is (= oulu-talvisuola-luokka-IsE 300M))
      (is (= oulu-talvisuola-luokka-Is 200M))
      (is (= oulu-talvisuola-luokka-I 200M))
      (is (= oulu-talvisuola-luokka-Ib 200M))
      (is (= oulu-talvisuola-luokka-Ic 100M))

      (apurit/tarkista-taulukko-sarakkeet taulukko-pop-ely
        {:leveys "2%", :tyyppi :avattava-rivi}
        {:otsikko "Materiaali"}
        {:otsikko "10/17"}
        {:otsikko "11/17"}
        {:otsikko "12/17"}
        {:otsikko "01/18"}
        {:otsikko "02/18"}
        {:otsikko "03/18"}
        {:otsikko "04/18"}
        {:otsikko "05/18"}
        {:otsikko "06/18"}
        {:otsikko "07/18"}
        {:otsikko "08/18"}
        {:otsikko "09/18"}
        {:otsikko "Yhteensä (t)"}
        {:otsikko "Suunniteltu (t)"}
        {:otsikko "Tot-%"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko-pop-ely tarkistusfunktio))))


(deftest ymparistoraportin-hoitoluokittaiset-maarat-vanha-ja-uusi-koodisto-sekaisin-oulu
  (let [_ (varmista-tietokannan-tila)
        vastaus-oulu (kutsu-palvelua (:http-palvelin jarjestelma)
                       :suorita-raportti
                       +kayttaja-jvh+
                       {:nimi :ymparistoraportti
                        :konteksti "urakka"
                        :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                        :parametrit {:alkupvm (pvm/->pvm "1.1.2018")
                                     :loppupvm (pvm/->pvm "31.12.2018")
                                     :urakkatyyppi :hoito}})]

    (is (vector? vastaus-oulu))
    (let [raportin-nimi "Ympäristöraportti"
          teksti "Oulun alueurakka 2014-2019 (1238), Ympäristöraportti ajalta 01.01.2018 - 31.12.2018"
          otsikko-oulu "Talvisuolat"
          taulukko-oulu (apurit/taulukko-otsikolla vastaus-oulu otsikko-oulu)
          _ (println "taulukko-oulu: " taulukko-oulu)
          oulu-talvisuola-luokka-kaikki-hoitoluokat-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 0))
          oulu-talvisuola-luokka-IsE-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 1))
          oulu-talvisuola-luokka-Is-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 2))
          oulu-talvisuola-luokka-I-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 3))
          oulu-talvisuola-luokka-Ib-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 4))
          oulu-talvisuola-luokka-Ic-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 5))
          oulu-talvisuola-luokka-II-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 6))
          oulu-talvisuola-luokka-III-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 7))
          oulu-talvisuola-luokka-L-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 8))
          oulu-talvisuola-luokka-K1-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 9))
          oulu-talvisuola-luokka-K2-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 10))
          oulu-talvisuola-luokka-ei-talvih-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 11))
          oulu-talvisuola-luokka-ei-tiedossa-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 12))

          oulu-talvisuola-luokka-kaikki-hoitoluokat-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 0))
          oulu-talvisuola-luokka-IsE-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 1))
          oulu-talvisuola-luokka-Is-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 2))
          oulu-talvisuola-luokka-I-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 3))
          oulu-talvisuola-luokka-Ib-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 4))
          oulu-talvisuola-luokka-Ic-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 5))
          oulu-talvisuola-luokka-II-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 6))
          oulu-talvisuola-luokka-III-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 7))
          oulu-talvisuola-luokka-L-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 8))
          oulu-talvisuola-luokka-K1-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 9))
          oulu-talvisuola-luokka-K2-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 10))
          oulu-talvisuola-luokka-ei-talvih-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 11))]

      (is (= raportin-nimi (:nimi (second vastaus-oulu))))
      (is (= teksti (second (nth vastaus-oulu 2))))
      (is (= oulu-talvisuola-luokka-kaikki-hoitoluokat-02-18 2600M))
      (is (= oulu-talvisuola-luokka-IsE-02-18 300M))
      (is (= oulu-talvisuola-luokka-Is-02-18 200M))
      (is (= oulu-talvisuola-luokka-I-02-18 200M))
      (is (= oulu-talvisuola-luokka-Ib-02-18 200M))
      (is (= oulu-talvisuola-luokka-Ic-02-18 100M))
      (is (= oulu-talvisuola-luokka-II-02-18 100M))
      (is (= oulu-talvisuola-luokka-III-02-18 100M))
      (is (= "–" oulu-talvisuola-luokka-L-02-18))
      (is (= oulu-talvisuola-luokka-K1-02-18 100M))
      (is (= oulu-talvisuola-luokka-K2-02-18 100M))
      (is (= oulu-talvisuola-luokka-ei-talvih-02-18 100M))
      (is (= oulu-talvisuola-luokka-ei-tiedossa-02-18 100M))

      (is (= oulu-talvisuola-luokka-kaikki-hoitoluokat-10-18 1103.14M))
      (is (= oulu-talvisuola-luokka-IsE-10-18 100M))
      (is (= oulu-talvisuola-luokka-Is-10-18 100M))
      (is (= oulu-talvisuola-luokka-I-10-18 100M))
      (is (= oulu-talvisuola-luokka-Ib-10-18 100M))
      (is (= oulu-talvisuola-luokka-Ic-10-18 100M))
      (is (= oulu-talvisuola-luokka-II-10-18 100M))
      (is (= oulu-talvisuola-luokka-III-10-18 100M))
      (is (= oulu-talvisuola-luokka-L-10-18 100M))
      (is (= oulu-talvisuola-luokka-K1-10-18 100M))
      (is (= oulu-talvisuola-luokka-K2-10-18 100M))
      (is (= oulu-talvisuola-luokka-ei-talvih-10-18 100M))

      (apurit/tarkista-taulukko-sarakkeet taulukko-oulu
        {:leveys "2%", :tyyppi :avattava-rivi}
        {:otsikko "Materiaali"}
        {:otsikko "01/18"}
        {:otsikko "02/18"}
        {:otsikko "03/18"}
        {:otsikko "04/18"}
        {:otsikko "05/18"}
        {:otsikko "06/18"}
        {:otsikko "07/18"}
        {:otsikko "08/18"}
        {:otsikko "09/18"}
        {:otsikko "10/18"}
        {:otsikko "11/18"}
        {:otsikko "12/18"}
        {:otsikko "Yhteensä (t)"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko-oulu tarkistusfunktio))))


(deftest ymparistoraportin-hoitoluokittaiset-maarat-vanha-ja-uusi-koodisto-sekaisin-pop-ely-ei-urakoittain
  (let [_ (varmista-tietokannan-tila)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :ymparistoraportti
                   :konteksti "hallintayksikko"
                   :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                   :parametrit {:alkupvm (pvm/->pvm "1.1.2018")
                                :loppupvm (pvm/->pvm "31.12.2018")
                                :urakkatyyppi :hoito
                                :urakoittain? false}})]

    (is (vector? vastaus))
    (let [raportin-nimi "Pohjois-Pohjanmaa, Ympäristöraportti ajalta 01.01.2018 - 31.12.2018"
          otsikko "Talvisuolat"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)
          talvisuola-luokka-kaikki-hoitoluokat-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 0))
          talvisuola-luokka-IsE-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 1))
          talvisuola-luokka-Is-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 2))
          talvisuola-luokka-I-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 3))
          talvisuola-luokka-Ib-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 4))
          talvisuola-luokka-Ic-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 5))
          talvisuola-luokka-II-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 6))
          talvisuola-luokka-III-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 7))
          talvisuola-luokka-L-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 8))
          talvisuola-luokka-K1-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 9))
          talvisuola-luokka-K2-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 10))
          talvisuola-luokka-ei-talvih-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 11))
          talvisuola-luokka-ei-tiedossa-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 12))

          talvisuola-luokka-kaikki-hoitoluokat-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 0))
          talvisuola-luokka-IsE-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 1))
          talvisuola-luokka-Is-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 2))
          talvisuola-luokka-I-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 3))
          talvisuola-luokka-Ib-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 4))
          talvisuola-luokka-Ic-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 5))
          talvisuola-luokka-II-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 6))
          talvisuola-luokka-III-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 7))
          talvisuola-luokka-L-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 8))
          talvisuola-luokka-K1-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 9))
          talvisuola-luokka-K2-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 10))
          talvisuola-luokka-ei-talvih-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 11))
          talvisuola-luokka-ei-tiedossa-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 12))]

      (is (= talvisuola-luokka-kaikki-hoitoluokat-02-18 3600M))
      (is (= talvisuola-luokka-IsE-02-18 600M))
      (is (= talvisuola-luokka-Is-02-18 400M))
      (is (= talvisuola-luokka-I-02-18 400M))
      (is (= talvisuola-luokka-Ib-02-18 400M))
      (is (= talvisuola-luokka-Ic-02-18 200M))
      (is (= talvisuola-luokka-II-02-18 100M))
      (is (= talvisuola-luokka-III-02-18 100M))
      (is (= "–" talvisuola-luokka-L-02-18))
      (is (= talvisuola-luokka-K1-02-18 100M))
      (is (= talvisuola-luokka-K2-02-18 100M))
      (is (= talvisuola-luokka-ei-talvih-02-18 100M))
      (is (= talvisuola-luokka-ei-tiedossa-02-18 100M))

      (is (= talvisuola-luokka-kaikki-hoitoluokat-10-18 1103.14M))
      (is (= talvisuola-luokka-IsE-10-18 100M))
      (is (= talvisuola-luokka-Is-10-18 100M))
      (is (= talvisuola-luokka-I-10-18 100M))
      (is (= talvisuola-luokka-Ib-10-18 100M))
      (is (= talvisuola-luokka-Ic-10-18 100M))
      (is (= talvisuola-luokka-II-10-18 100M))
      (is (= talvisuola-luokka-III-10-18 100M))
      (is (= talvisuola-luokka-L-10-18 100M))
      (is (= talvisuola-luokka-K1-10-18 100M))
      (is (= talvisuola-luokka-K2-10-18 100M))
      (is (= talvisuola-luokka-ei-talvih-10-18 100M))
      (is (= "–" talvisuola-luokka-ei-tiedossa-10-18))

      (apurit/tarkista-taulukko-sarakkeet taulukko
        {:leveys "2%", :tyyppi :avattava-rivi}
        {:otsikko "Materiaali" :leveys "14%"}
        {:otsikko "01/18" :leveys "5%" :fmt :numero}
        {:otsikko "02/18"}
        {:otsikko "03/18"}
        {:otsikko "04/18"}
        {:otsikko "05/18"}
        {:otsikko "06/18"}
        {:otsikko "07/18"}
        {:otsikko "08/18"}
        {:otsikko "09/18"}
        {:otsikko "10/18"}
        {:otsikko "11/18"}
        {:otsikko "12/18"}
        {:otsikko "Yhteensä (t)"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko tarkistusfunktio))))

(deftest ymparistoraportin-hoitoluokittaiset-maarat-vanha-ja-uusi-koodisto-sekaisin-pop-ely-urakoittain
  (let [_ (varmista-tietokannan-tila)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :ymparistoraportti
                   :konteksti "hallintayksikko"
                   :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                   :parametrit {:alkupvm (pvm/->pvm "1.1.2018")
                                :loppupvm (pvm/->pvm "31.12.2018")
                                :urakkatyyppi :hoito
                                :urakoittain? true}})]

    (is (vector? vastaus))
    (let [raportin-nimi "Pohjois-Pohjanmaa, Ympäristöraportti ajalta 01.01.2018 - 31.12.2018"
          otsikko "Talvisuolat"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)
          foo (seq (apurit/taulukon-rivit taulukko))
          talvisuola-luokka-02-18-kajaani-kaikki (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 0))
          talvisuola-luokka-02-18-kajaani-IsE (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 1))
          talvisuola-luokka-02-18-kajaani-Is (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 2))
          talvisuola-luokka-02-18-kajaani-I (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 3))
          talvisuola-luokka-02-18-kajaani-Ib (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 4))
          talvisuola-luokka-02-18-kajaani-Ic (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 5))
          talvisuola-luokka-02-18-kajaani-erotus (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 6))
          talvisuola-luokka-02-18-oulu (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 7))
          talvisuola-luokka-02-18-oulu-IsE (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 8))
          talvisuola-luokka-02-18-oulu-Is (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 9))
          talvisuola-luokka-02-18-oulu-I (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 10))
          talvisuola-luokka-02-18-oulu-Ib (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 11))
          talvisuola-luokka-02-18-oulu-Ic (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 12))
          talvisuola-luokka-02-18-oulu-II (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 13))
          talvisuola-luokka-02-18-oulu-III (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 14))
          talvisuola-luokka-02-18-oulu-L (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 15))
          talvisuola-luokka-02-18-oulu-K1 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 16))
          talvisuola-luokka-02-18-oulu-K2 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 17))
          talvisuola-luokka-02-18-oulu-ei-talvihoitoa (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 18))
          talvisuola-luokka-02-18-oulu-ei-tiedossa (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 19))
          talvisuola-luokka-02-18-oulu-erotus (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 20))]

      (is (= talvisuola-luokka-02-18-kajaani-kaikki 1000M))
      (is (= talvisuola-luokka-02-18-kajaani-IsE 300M))
      (is (= talvisuola-luokka-02-18-kajaani-Is 200M))
      (is (= talvisuola-luokka-02-18-kajaani-I 200M))
      (is (= talvisuola-luokka-02-18-kajaani-Ib 200M))
      (is (= talvisuola-luokka-02-18-kajaani-Ic 100M))
      (is (= talvisuola-luokka-02-18-kajaani-erotus 0M))

      (is (= talvisuola-luokka-02-18-oulu 2600M))
      (is (= talvisuola-luokka-02-18-oulu-IsE 300M))
      (is (= talvisuola-luokka-02-18-oulu-Is 200M))
      (is (= talvisuola-luokka-02-18-oulu-I 200M))
      (is (= talvisuola-luokka-02-18-oulu-Ib 200M))
      (is (= talvisuola-luokka-02-18-oulu-Ic 100M))
      (is (= talvisuola-luokka-02-18-oulu-II 100M))
      (is (= talvisuola-luokka-02-18-oulu-III 100M))
      (is (= "–" talvisuola-luokka-02-18-oulu-L))
      (is (= talvisuola-luokka-02-18-oulu-K1 100M))
      (is (= talvisuola-luokka-02-18-oulu-K2 100M))
      (is (= talvisuola-luokka-02-18-oulu-ei-talvihoitoa 100M))
      (is (= talvisuola-luokka-02-18-oulu-ei-tiedossa 100M))
      (is (= talvisuola-luokka-02-18-oulu-erotus -1000M))


      (apurit/tarkista-taulukko-sarakkeet taulukko
        {:leveys "2%", :tyyppi :avattava-rivi}
        {:otsikko "Urakka"}
        {:otsikko "Materiaali"}
        {:otsikko "01/18"}
        {:otsikko "02/18"}
        {:otsikko "03/18"}
        {:otsikko "04/18"}
        {:otsikko "05/18"}
        {:otsikko "06/18"}
        {:otsikko "07/18"}
        {:otsikko "08/18"}
        {:otsikko "09/18"}
        {:otsikko "10/18"}
        {:otsikko "11/18"}
        {:otsikko "12/18"}
        {:otsikko "Yhteensä (t)"}))))

;; Testaa että talvihoitoluokan normalisointisproc-toimii odotetusti.
;;Muutospvm aineistossa 2.7.2018 jonka mukaan vipu vääntyy.
(deftest normalisoi-talvihoitoluokka
  (let [vanha-IsE (ffirst (q "select * from normalisoi_talvihoitoluokka(0, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-IsE (ffirst (q "select * from normalisoi_talvihoitoluokka(1, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-Is (ffirst (q "select * from normalisoi_talvihoitoluokka(1, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-Is (ffirst (q "select * from normalisoi_talvihoitoluokka(2, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-I (ffirst (q "select * from normalisoi_talvihoitoluokka(2, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-I (ffirst (q "select * from normalisoi_talvihoitoluokka(3, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-Ib (ffirst (q "select * from normalisoi_talvihoitoluokka(3, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-Ib (ffirst (q "select * from normalisoi_talvihoitoluokka(4, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-Ic (ffirst (q "select * from normalisoi_talvihoitoluokka(4, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-Ic (ffirst (q "select * from normalisoi_talvihoitoluokka(5, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-II (ffirst (q "select * from normalisoi_talvihoitoluokka(5, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-II (ffirst (q "select * from normalisoi_talvihoitoluokka(6, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-III (ffirst (q "select * from normalisoi_talvihoitoluokka(6, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-III (ffirst (q "select * from normalisoi_talvihoitoluokka(7, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-K1 (ffirst (q "select * from normalisoi_talvihoitoluokka(7, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-K1 (ffirst (q "select * from normalisoi_talvihoitoluokka(9, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-K2 (ffirst (q "select * from normalisoi_talvihoitoluokka(8, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-K2 (ffirst (q "select * from normalisoi_talvihoitoluokka(10, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))

        vanha-ei-talvihoitoa (ffirst (q "select * from normalisoi_talvihoitoluokka(9, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-ei-talvihoitoa (ffirst (q "select * from normalisoi_talvihoitoluokka(11, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))

        vanha-III (ffirst (q "select * from normalisoi_talvihoitoluokka(6, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-III (ffirst (q "select * from normalisoi_talvihoitoluokka(7, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))

        ei-talvihoitoluokkaa-vanha (ffirst (q "select * from normalisoi_talvihoitoluokka(null, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        ei-talvihoitoluokkaa-uusi (ffirst (q "select * from normalisoi_talvihoitoluokka(null, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        ei-talvihoitoluokkaa-outonumero (ffirst (q "select * from normalisoi_talvihoitoluokka(667, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        ]
    (is (= vanha-IsE uusi-IsE 1) "IsE")
    (is (= vanha-Is uusi-Is 2) "Is")
    (is (= vanha-I uusi-I 3) "I")
    (is (= vanha-Ib uusi-Ib 4) "Ib")
    (is (= vanha-Ic uusi-Ic 5) "Ic")
    (is (= vanha-II uusi-II 6) "II")
    (is (= vanha-III uusi-III 7) "III")
    (is (= vanha-K1 uusi-K1 9) "K1")
    (is (= vanha-K2 uusi-K2 10) "K2")
    (is (= vanha-ei-talvihoitoa uusi-ei-talvihoitoa 11) "ei talvihoitoa")
    (is (= ei-talvihoitoluokkaa-vanha ei-talvihoitoluokkaa-uusi ei-talvihoitoluokkaa-outonumero 100) "ei talvihoitoluokkaa")))


(deftest ymparistoraportin-hoitoluokittaiset-ei-hoitoluokkaa-api-ja-kasin-sekaisin
  (let [_ (q (str "select paivita_urakan_materiaalin_kaytto_hoitoluokittain("(hae-oulun-alueurakan-2014-2019-id)",'2019-01-01'::DATE,'2019-12-31'::DATE);"))
        vastaus-oulu (kutsu-palvelua (:http-palvelin jarjestelma)
                       :suorita-raportti
                       +kayttaja-jvh+
                       {:nimi :ymparistoraportti
                        :konteksti "urakka"
                        :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                        :parametrit {:alkupvm (pvm/->pvm "1.1.2019")
                                     :loppupvm (pvm/->pvm "31.12.2019")
                                     :urakkatyyppi :hoito}})]

    (is (vector? vastaus-oulu))
    (let [otsikko-oulu "Talvisuolat"
          taulukko-oulu (apurit/taulukko-otsikolla vastaus-oulu otsikko-oulu)
          oulu-talvisuola-luokka-kaikki-hoitoluokat-02-19 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 0))
          oulu-talvisuola-luokka-IsE-02-19 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 1))
          oulu-talvisuola-luokka-Is-02-19 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 2))
          oulu-talvisuola-luokka-I-02-19 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 3))
          oulu-talvisuola-luokka-Ib-02-19 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 4))
          oulu-talvisuola-luokka-Ic-02-19 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 5))
          oulu-talvisuola-luokka-K2-02-19 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 6))
          oulu-talvisuola-luokka-kasin-kirjattu-02-19 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 7))
          oulu-talvisuola-luokka-ei-tiedossa-02-19 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 8))]

      (is (= oulu-talvisuola-luokka-kaikki-hoitoluokat-02-19 46M))
      (is (= oulu-talvisuola-luokka-IsE-02-19 1M))
      (is (= oulu-talvisuola-luokka-Is-02-19 2M))
      (is (= oulu-talvisuola-luokka-I-02-19 3M))
      (is (= oulu-talvisuola-luokka-Ib-02-19 4M))
      (is (= oulu-talvisuola-luokka-Ic-02-19 5M))
      (is (= oulu-talvisuola-luokka-K2-02-19 6M))

      ;; Tähän tarkoituksella lasketaan myös käsin syötetyt toteumat, joille ei voida saada muuta hoitoluokkaa
      ;; kuten API:n kautta kirjattavilla saadaan
      ;; sis. 15 reittipisteiden kautta ja 10 käsin syötetyn toteuman kautta
      (is (= oulu-talvisuola-luokka-kasin-kirjattu-02-19 10M))
      (is (= oulu-talvisuola-luokka-ei-tiedossa-02-19 15M))

      (apurit/tarkista-taulukko-sarakkeet taulukko-oulu
        {:leveys "2%", :tyyppi :avattava-rivi}
        {:otsikko "Materiaali"}
        {:otsikko "01/19"}
        {:otsikko "02/19"}
        {:otsikko "03/19"}
        {:otsikko "04/19"}
        {:otsikko "05/19"}
        {:otsikko "06/19"}
        {:otsikko "07/19"}
        {:otsikko "08/19"}
        {:otsikko "09/19"}
        {:otsikko "10/19"}
        {:otsikko "11/19"}
        {:otsikko "12/19"}
        {:otsikko "Yhteensä (t)"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko-oulu tarkistusfunktio))))
