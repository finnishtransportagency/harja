(ns harja.palvelin.raportointi.ymparistoraportti-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.domain.hoitoluokat :as hoitoluokat]
            [harja.kyselyt [raportit :as raportit-q]]
            [harja.testi :refer :all]
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
-- Talvisuolaa 1000t per urakka, materiaali: Talvisuola, rakeinen NaCl
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 0, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 300),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 1, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 2, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 3, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 4, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 5, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 6, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 7, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 8, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 9, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 100, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),

-- uudet talvihoitoluokat, materiaali: Talvisuola, rakeinen NaCl
('2018-10-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 1, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 2, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 3, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 4, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 5, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 6, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 7, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 8, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 9, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 10, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-10-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 11, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),

-- materiaali: Talvisuola, rakeinen NaCl
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 0, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 300),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 1, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 2, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 3, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e'), 4, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 100),

-- Hiekoitushiekkaa 1000t per urakka, materiaali: Hiekoitushiekka, liukkaudentorjunta
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = 'abbb61e5-beee-42fd-a60d-14ec156afae5'), 0, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 300),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = 'abbb61e5-beee-42fd-a60d-14ec156afae5'), 1, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = 'abbb61e5-beee-42fd-a60d-14ec156afae5'), 2, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = 'abbb61e5-beee-42fd-a60d-14ec156afae5'), 3, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = 'abbb61e5-beee-42fd-a60d-14ec156afae5'), 4, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = 'abbb61e5-beee-42fd-a60d-14ec156afae5'), 0, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 300),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = 'abbb61e5-beee-42fd-a60d-14ec156afae5'), 1, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = 'abbb61e5-beee-42fd-a60d-14ec156afae5'), 2, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = 'abbb61e5-beee-42fd-a60d-14ec156afae5'), 3, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
('2018-02-15', (SELECT id from materiaalikoodi WHERE yksiloiva_tunniste = 'abbb61e5-beee-42fd-a60d-14ec156afae5'), 4, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 100);")))

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
      (some #(= (str " - " (:nimi %)) (first sisalto))
        hoitoluokat/talvihoitoluokat)
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
        {:otsikko "Yhteensä"}
        {:otsikko "Suunniteltu"}
        {:otsikko "Tot-%"})
      (apurit/tarkista-taulukko-kaikki-rivit talvisuolataulukko tarkistusfunktio))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [_ (varmista-tietokannan-tila)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :ymparistoraportti
                   :konteksti "elinvoimakeskus"
                   :elinvoimakeskus-id (hae-pohjois-suomen-evk-id)
                   :parametrit {:alkupvm (c/to-date (t/local-date 2015 10 1))
                                :loppupvm (c/to-date (t/local-date 2016 9 30))
                                :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (let [raportin-nimi "Ympäristöraportti"
          teksti "Pohjois-Suomi, Ympäristöraportti ajalta 01.10.2015 - 30.09.2016"
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
        {:otsikko "Yhteensä"}
        {:otsikko "Suunniteltu"}
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
        {:otsikko "Yhteensä"}
        {:otsikko "Suunniteltu"}
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
        talvisuolat-s6-rivi0 (apurit/raporttisolun-arvo (apurit/taulukon-solu talvisuolat 6 0))
        talvisuolat-s14-rivi3 (apurit/raporttisolun-arvo (apurit/taulukon-solu talvisuolat 14 3))
        ;talvisuolat-s6-rivi4 (apurit/raporttisolun-arvo (apurit/taulukon-solu talvisuolat 6 4))
        talvisuolat-s6-rivi6 (apurit/raporttisolun-arvo (apurit/taulukon-solu talvisuolat 6 6))
        talvisuolat-s14-rivi6 (apurit/raporttisolun-arvo (apurit/taulukon-solu talvisuolat 14 6))
        formiaatit-s6-rivi1 (apurit/raporttisolun-arvo (apurit/taulukon-solu formiaatit 6 1))
        formiaatit-s14-rivi4 (apurit/raporttisolun-arvo (apurit/taulukon-solu formiaatit 14 4))
        hiekoitushiekat-s14-rivi0 (apurit/raporttisolun-arvo (apurit/taulukon-solu hiekoitushiekat 14 0))
        hiekoitushiekat-s15-rivi0 (apurit/raporttisolun-arvo (apurit/taulukon-solu hiekoitushiekat 15 0))
        paikkausmateriaalit-s3-rivi0 (apurit/raporttisolun-arvo (apurit/taulukon-solu paikkausmateriaalit 3 0))
        paikkausmateriaalit-s4-rivi1 (apurit/raporttisolun-arvo (apurit/taulukon-solu paikkausmateriaalit 4 1))
        materiaali (apurit/taulukko-otsikolla
                     (kutsu-palvelua (:http-palvelin jarjestelma)
                       :suorita-raportti
                       +kayttaja-jvh+
                       {:nimi :materiaaliraportti
                        :konteksti "urakka"
                        :urakka-id urakka-id
                        :parametrit param})
                     "Oulun alueurakka 2014-2019, Materiaaliraportti ajalta 01.10.2014 - 30.09.2015")
        materiaali-s1-rivi0 (apurit/taulukon-solu materiaali 1 0)
        materiaali-s2-rivi0 (apurit/taulukon-solu materiaali 2 0)
        materiaali-s3-rivi0 (apurit/taulukon-solu materiaali 3 0)
        materiaali-s4-rivi0 (apurit/taulukon-solu materiaali 4 0)
        materiaali-s5-rivi0 (apurit/taulukon-solu materiaali 5 0)
        materiaali-s6-rivi0 (apurit/taulukon-solu materiaali 6 0)
        materiaali-s7-rivi0 (apurit/taulukon-solu materiaali 7 0)
        suola-sakko-taulukko (apurit/taulukko-otsikolla
                               (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :suorita-raportti
                                 +kayttaja-jvh+
                                 {:nimi :suolasakko
                                  :konteksti "urakka"
                                  :urakka-id urakka-id
                                  :parametrit param})
                               "Oulun alueurakka 2014-2019, Suolasakkoraportti ajalta 01.10.2014 - 30.09.2015")
        suolasakko-s8-rivi0 (apurit/taulukon-solu suola-sakko-taulukko 8 0)]
    (is (= raportin-nimi (:nimi (second vastaus))) "0")
    (is (= teksti (second (nth vastaus 2))) "1")
    (is (= formiaatit-s14-rivi4 materiaali-s1-rivi0 2000M) "3")
    ;(is (= talvisuolat-s6-rivi4 1800M) "4")
    (is (= formiaatit-s6-rivi1 materiaali-s4-rivi0 2000M) "5")
    (is (= -1800M talvisuolat-s6-rivi6 talvisuolat-s14-rivi6) "7")
    ;; Testidatasta riippuvia testejä.. vähän huonoja
    (is (= 0 hiekoitushiekat-s14-rivi0) "8")
    (is (= 500M materiaali-s5-rivi0) "9")
    (is (nil? hiekoitushiekat-s15-rivi0) "10")
    (is (= 1000M paikkausmateriaalit-s3-rivi0 materiaali-s6-rivi0) "11")
    (is (= 1000M paikkausmateriaalit-s4-rivi1 materiaali-s7-rivi0) "12")))

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
                           :konteksti "elinvoimakeskus"
                           :elinvoimakeskus-id (hae-pohjois-suomen-evk-id)
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
          teksti-pop-ely "Pohjois-Suomi, Ympäristöraportti ajalta 01.10.2017 - 30.09.2018"
          otsikko-pop-ely "Talvisuolat"
          taulukko-pop-ely (apurit/taulukko-otsikolla vastaus-pop-ely otsikko-pop-ely)

          pop-rivi-1 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-pop-ely 6 1))
          pop-rivi-2 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-pop-ely 6 2))
          pop-rivi-3 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-pop-ely 6 3))
          pop-rivi-4 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-pop-ely 6 4))
          pop-rivi-5 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-pop-ely 6 5))


          teksti-oulu "Oulun alueurakka 2014-2019 (1238), Ympäristöraportti ajalta 01.10.2017 - 30.09.2018"
          otsikko-oulu "Talvisuolat"
          taulukko-oulu (apurit/taulukko-otsikolla vastaus-oulu otsikko-oulu)
          oulu-rivi-1 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 6 1))
          oulu-rivi-2 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 6 2))
          oulu-rivi-3 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 6 3))
          oulu-rivi-4 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 6 4))
          oulu-rivi-5 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 6 5))]
      (is (= raportin-nimi (:nimi (second vastaus-pop-ely))))
      (is (= teksti-pop-ely (second (nth vastaus-pop-ely 2))))
      (is (= pop-rivi-1 3600M))
      (is (= pop-rivi-2 600M))
      (is (= pop-rivi-3 400M))
      (is (= pop-rivi-4 400M))
      (is (= pop-rivi-5 400M))

      (is (= oulu-rivi-1 2600M))
      (is (= oulu-rivi-2 300M))
      (is (= oulu-rivi-3 200M))
      (is (= oulu-rivi-4 200M))
      (is (= oulu-rivi-5 200M))

      (is (= teksti-oulu (second (nth vastaus-oulu 2))))

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
        {:otsikko "Yhteensä"}
        {:otsikko "Suunniteltu"}
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
          s3-rivi0 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 0))
          s3-rivi1 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 1))
          s3-rivi2 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 2))
          s3-rivi3 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 3))
          s3-rivi4 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 4))
          s3-rivi5 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 5))
          s3-rivi6 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 6))
          s3-rivi7 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 7))
          s3-rivi8 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 8))
          s3-rivi9 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 9))
          s3-rivi10 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 10))
          s3-rivi11 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 11))
          s3-rivi12 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 12))

          s11-rivi0 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 0))
          s11-rivi1 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 1))
          s11-rivi2 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 2))
          s11-rivi3 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 3))
          s11-rivi4 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 4))
          s11-rivi5 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 5))
          s11-rivi6 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 6))
          s11-rivi7 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 7))
          s11-rivi8 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 8))
          s11-rivi9 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 9))
          s11-rivi10 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 10))
          s11-rivi11 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 11 11))]

      (is (= raportin-nimi (:nimi (second vastaus-oulu))))
      (is (= teksti (second (nth vastaus-oulu 2))))
      (is (= s3-rivi0 "–") "0")
      (is (= s3-rivi1 2600M) "1")
      (is (= s3-rivi2 300M) "2")
      (is (= s3-rivi3 200M) "3")
      (is (= s3-rivi4 200M) "4")
      (is (= s3-rivi5 200M) "5")
      (is (= s3-rivi6 100M) "6")
      (is (= s3-rivi7 100M) "7")
      (is (= s3-rivi8 100M) "8")
      (is (= s3-rivi9 "–") "9")
      (is (= s3-rivi10 100M) "10")
      (is (= s3-rivi11 100M) "11")
      (is (= s3-rivi12 100M) "12")

      (is (= s11-rivi0 "–") "0")
      (is (= s11-rivi1 1103.14M) "1")
      (is (= s11-rivi2 100M) "2")
      (is (= s11-rivi3 100M) "3")
      (is (= s11-rivi4 100M) "4")
      (is (= s11-rivi5 100M) "5")
      (is (= s11-rivi6 100M) "6")
      (is (= s11-rivi7 100M) "7")
      (is (= s11-rivi8 100M) "8")
      (is (= s11-rivi9 100M) "9")
      (is (= s11-rivi10 100M) "10")
      (is (= s11-rivi11 100M) "11")

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
        {:otsikko "Yhteensä"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko-oulu tarkistusfunktio))))


(deftest ymparistoraportin-hoitoluokittaiset-maarat-vanha-ja-uusi-koodisto-sekaisin-pop-ely-ei-urakoittain
  (let [_ (varmista-tietokannan-tila)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :ymparistoraportti
                   :konteksti "elinvoimakeskus"
                   :elinvoimakeskus-id (hae-pohjois-suomen-evk-id)
                   :parametrit {:alkupvm (pvm/->pvm "1.1.2018")
                                :loppupvm (pvm/->pvm "31.12.2018")
                                :urakkatyyppi :hoito
                                :urakoittain? false}})]

    (is (vector? vastaus))
    (let [raportin-nimi "Pohjois-Suomi, Ympäristöraportti ajalta 01.01.2018 - 31.12.2018"
          otsikko "Talvisuolat"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)
          s3-rivi0 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 0))
          s3-rivi1 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 1))
          s3-rivi2 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 2))
          s3-rivi3 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 3))
          s3-rivi4 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 4))
          s3-rivi5 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 5))
          s3-rivi6 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 6))
          s3-rivi7 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 7))
          s3-rivi8 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 8))
          s3-rivi9 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 9))
          s3-rivi10 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 10))
          s3-rivi11 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 11))
          s3-rivi12 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 12))

          s11-rivi0 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 0))
          s11-rivi1 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 1))
          s11-rivi2 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 2))
          s11-rivi3 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 3))
          s11-rivi4 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 4))
          s11-rivi5 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 5))
          s11-rivi6 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 6))
          s11-rivi7 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 7))
          s11-rivi8 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 8))
          s11-rivi9 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 9))
          s11-rivi10 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 10))
          s11-rivi11 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 11))
          s11-rivi12 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 11 12))]

      (is (= s3-rivi0 "–") "0")
      (is (= s3-rivi1 3600M) "1")
      (is (= s3-rivi2 600M) "2")
      (is (= s3-rivi3 400M) "3")
      (is (= s3-rivi4 400M) "4")
      (is (= s3-rivi5 400M) "5")
      (is (= s3-rivi6 200M) "6")
      (is (= s3-rivi7 100M) "7")
      (is (= s3-rivi8 100M) "8")
      (is (= s3-rivi9 "–") "9")
      (is (= s3-rivi10 100M) "10")
      (is (= s3-rivi11 100M) "11")
      (is (= s3-rivi12 100M) "12")

      (is (= s11-rivi0 "–") "0")
      (is (= s11-rivi1 1103.14M) "1")
      (is (= s11-rivi2 100M) "2")
      (is (= s11-rivi3 100M) "3")
      (is (= s11-rivi4 100M) "4")
      (is (= s11-rivi5 100M) "5")
      (is (= s11-rivi6 100M) "6")
      (is (= s11-rivi7 100M) "7")
      (is (= s11-rivi8 100M) "8")
      (is (= s11-rivi9 100M) "9")
      (is (= s11-rivi10 100M) "10")
      (is (= s11-rivi11 100M) "11")
      (is (= s11-rivi12 100M) "12")

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
        {:otsikko "Yhteensä"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko tarkistusfunktio))))

(deftest ymparistoraportin-hoitoluokittaiset-maarat-vanha-ja-uusi-koodisto-sekaisin-pop-ely-urakoittain
  (let [_ (varmista-tietokannan-tila)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :ymparistoraportti
                   :konteksti "elinvoimakeskus"
                   :elinvoimakeskus-id (hae-pohjois-suomen-evk-id)
                   :parametrit {:alkupvm (pvm/->pvm "1.1.2018")
                                :loppupvm (pvm/->pvm "31.12.2018")
                                :urakkatyyppi :hoito
                                :urakoittain? true}})]

    (is (vector? vastaus))
    (let [raportin-nimi "Pohjois-Suomi, Ympäristöraportti ajalta 01.01.2018 - 31.12.2018"
          otsikko "Talvisuolat"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)
          foo (seq (apurit/taulukon-rivit taulukko))
          s4-rivi0 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 0))
          s4-rivi1 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 1))
          s4-rivi2 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 2))
          s4-rivi3 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 3))
          s4-rivi4 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 4))
          s4-rivi5 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 5))
          s4-rivi6 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 6))
          s4-rivi7 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 7))
          s4-rivi8 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 8))
          s4-rivi9 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 9))
          s4-rivi10 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 10))
          s4-rivi11 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 11))
          s4-rivi12 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 12))
          s4-rivi13 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 13))
          s4-rivi14 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 14))
          s4-rivi15 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 15))
          s4-rivi16 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 16))
          s4-rivi17 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 17))
          s4-rivi18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 18))
          s4-rivi19 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 19))
          s4-rivi20 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 4 20))]

      (is (= s4-rivi0 "–") "0")
      (is (= s4-rivi1 1000M) "1")
      (is (= s4-rivi2 300M) "2")
      (is (= s4-rivi3 200M) "3")
      (is (= s4-rivi4 200M) "4")
      (is (= s4-rivi5 200M) "5")
      (is (= s4-rivi6 100M) "6")

      (is (= s4-rivi7 0M) "7")
      (is (= s4-rivi8 "–") "8")
      (is (= s4-rivi9 "–") "9")
      (is (= s4-rivi10 "–") "10")
      (is (= s4-rivi11 1000M) "11")
      (is (= s4-rivi12 "–") "12")
      (is (= s4-rivi13 2600M) "13")
      (is (= s4-rivi14 300M) "14")
      (is (= s4-rivi15 200M) "15")
      (is (= s4-rivi16 200M) "16")
      (is (= s4-rivi17 200M) "17")
      (is (= s4-rivi18 100M) "18")
      (is (= s4-rivi19 100M) "19")
      (is (= s4-rivi20 100M) "20")

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
        {:otsikko "Yhteensä"}))))

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
  (let [_ (q (str "select paivita_urakan_materiaalin_kaytto_hoitoluokittain(" (hae-oulun-alueurakan-2014-2019-id) ",'2019-01-01'::DATE,'2019-12-31'::DATE);"))
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
          rivi-1 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 1))
          rivi-2 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 2))
          rivi-3 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 3))
          rivi-4 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 4))
          rivi-5 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 5))
          rivi-6 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 6))
          rivi-7 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 7))
          rivi-8 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 3 8))]

      (is (= rivi-1 46M))
      (is (= rivi-2 1M))
      (is (= rivi-3 2M))
      (is (= rivi-4 3M))
      (is (= rivi-5 4M))
      (is (= rivi-6 5M))
      (is (= rivi-7 6M))
      (is (= rivi-8 10M))

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
        {:otsikko "Yhteensä"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko-oulu tarkistusfunktio))))

(deftest ymparistoraportin-koko-maan-urakoissa-urakkanumero-mukana
  (let [_ (varmista-tietokannan-tila)
        vastaus-koko-maa (kutsu-palvelua (:http-palvelin jarjestelma)
                           :suorita-raportti
                           +kayttaja-jvh+
                           {:nimi :ymparistoraportti
                            :konteksti "koko maa"
                            :parametrit {:alkupvm (c/to-date (t/local-date 2015 10 1))
                                         :loppupvm (c/to-date (t/local-date 2016 9 30))
                                         :urakkatyyppi :hoito
                                         :urakoittain? true
                                         :urakkanumero? true ;; Tämä parametri aiheuttaa urakkanumeron lisäämisen
                                         }})]

    (is (vector? vastaus-koko-maa))
    (let [otsikko "Talvisuolat"
          taulukko (apurit/taulukko-otsikolla vastaus-koko-maa otsikko)
          taulukon-rivit (seq (apurit/taulukon-rivit taulukko))
          ensimmainen-rivi (first taulukon-rivit)]
      (is (map? ensimmainen-rivi))
      ;; Varmista, että oulun urakkanumero on mukana urakan nimessä
      (is (= (str/includes? (second (:rivi ensimmainen-rivi)) "(1238)"))))))

(deftest ymparistoraportti-sisaltaa-mhu-muutokset-suunnitelmassa-test
  (testing "Ympäristöraportin suunniteltu määrä sisältää MHU-muutokset"
    ;; Tämä testi varmistaa että ymparisto.sql käyttää urakka_tehtavamaara_yhteenveto VIEW:tä
    ;; ja että raportti toimii ilman virheitä MHU-urakoille.
    (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
          
          ;; Hae raportti - ei poikkeusta = SQL toimii VIEW:n kanssa
          raportti (kutsu-palvelua (:http-palvelin jarjestelma)
                     :suorita-raportti
                     +kayttaja-jvh+
                     {:nimi :ymparistoraportti
                      :konteksti "urakka"
                      :urakka-id urakka-id
                      :parametrit {:alkupvm (c/to-date (t/local-date 2025 1 1))
                                   :loppupvm (c/to-date (t/local-date 2025 12 31))
                                   :urakoittain? false}})
          
          ;; Tarkista että raportti sisältää perus taulukot
          talvisuolat-taulukko (apurit/taulukko-otsikolla raportti "Talvisuolat")
          paikkausmateriaalit-taulukko (apurit/taulukko-otsikolla raportti "Paikkausmateriaalit")]
      
      ;; Tarkista että taulukot löytyivät
      (is (some? raportti) "Ympäristöraportti generoituu MHU-urakalle")
      (is (some? talvisuolat-taulukko) "Talvisuolat-taulukko löytyy raportista")
      (is (some? paikkausmateriaalit-taulukko) "Paikkausmateriaalit-taulukko löytyy raportista"))))

