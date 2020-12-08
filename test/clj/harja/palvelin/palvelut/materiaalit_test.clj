(ns harja.palvelin.palvelut.materiaalit-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.materiaalit :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]))

(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-materiaalikoodit (component/using
                                                (->Materiaalit)
                                                [:http-palvelin :db])
                        :hae-urakan-materiaalit (component/using
                                                  (->Materiaalit)
                                                  [:http-palvelin :db])
                        :hae-urakan-toteumat-materiaalille (component/using
                                                             (->Materiaalit)
                                                             [:http-palvelin :db])
                        :hae-toteuman-materiaalitiedot (component/using
                                                         (->Materiaalit)
                                                         [:http-palvelin :db])
                        :hae-urakassa-kaytetyt-materiaalit (component/using
                                                             (->Materiaalit)
                                                             [:http-palvelin :db])

                        :tallenna-urakan-materiaalit (component/using
                                                       (->Materiaalit)
                                                       [:http-palvelin :db])
                        :tallenna-toteuma-materiaaleja! (component/using
                                                          (->Materiaalit)
                                                          [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-materiaalit-sarakkeet
  (is (oikeat-sarakkeet-palvelussa? [:id :nimi :yksikko :urakkatyyppi :kohdistettava] :hae-materiaalikoodit)))

(deftest hae-urakan-materiaalit-sarakkeet
  (let [tallennus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-suunnitellut-materiaalit
                                  +kayttaja-jvh+

                                  {:urakka-id @oulun-alueurakan-2005-2010-id
                                   :sopimus-id @oulun-alueurakan-2005-2010-paasopimuksen-id
                                   :hoitokausi [(pvm/->pvm "1.10.2014") (pvm/->pvm "30.9.2015")]
                                   :tulevat-hoitokaudet-mukana? false
                                   :materiaalit [{:alkupvm (pvm/->pvm "1.10.2014")
                                                  :loppupvm (pvm/->pvm "30.9.2015")
                                                  :materiaali {:id 5}
                                                  :maara 666
                                                  }]})
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-materiaalit
                                +kayttaja-jvh+ @oulun-alueurakan-2005-2010-id)]
    (is (some #(and (= (:maara %) 666.0)
                    (= (:sopimus %) @oulun-alueurakan-2005-2010-paasopimuksen-id)
                    (= (:id (:materiaali %)) 5))
              vastaus))))

(deftest hae-urakan-toteumat-materiaalille-sarakkeet
  (let [tunnisteet
        (q
          "SELECT DISTINCT t.urakka, tm.materiaalikoodi, t.sopimus, t.alkanut, t.paattynyt
           FROM toteuma t LEFT JOIN toteuma_materiaali tm ON t.id = tm.toteuma
           AND t.poistettu IS NOT TRUE AND tm.poistettu IS NOT TRUE
           WHERE urakka is not null and materiaalikoodi is not null and sopimus is not null;")]
    ;; TODO: Olettaisin että tämä toimii oikeasti laiskasti, mutta nyt näyttää että ei. Miksi? :(
    (is
      (some
        true?
        (map
          (fn [[urakka materiaalikoodi sopimus alkanut paattynyt]]
            (oikeat-sarakkeet-palvelussa?
              [:id [:materiaali :id] [:materiaali :nimi] [:materiaali :yksikko] [:toteuma :maara]
               [:toteuma :alkanut] [:toteuma :paattynyt] :tmid [:toteuma :lisatieto] [:toteuma :suorittaja]
               :sopimus]
              :hae-urakan-toteumat-materiaalille
              {:urakka-id urakka
               :materiaali-id materiaalikoodi
               :hoitokausi [alkanut paattynyt]
               :sopimus sopimus}))
          tunnisteet)))))

(deftest hae-toteuman-materiaalitiedot-sarakkeet
  (let [tunnisteet
        (q
          "SELECT DISTINCT t.urakka, t.id
           FROM toteuma t
           LEFT JOIN toteuma_materiaali tm
           ON t.id = tm.toteuma AND
           t.poistettu is not true and
           tm.poistettu is not true
           WHERE urakka is not null;")]
    (is
      (some
        (fn [[urakka toteuma]]
          (oikeat-sarakkeet-palvelussa?
            [[:toteumamateriaalit 0 :materiaali :nimi] [:toteumamateriaalit 0 :materiaali :yksikko]
             [:toteumamateriaalit 0 :maara] :alkanut :paattynyt [:toteumamateriaalit 0 :materiaali :id]
             :id [:toteumamateriaalit 0 :tmid] :suorittaja :ytunnus :lisatieto]
            :hae-toteuman-materiaalitiedot
            {:urakka-id urakka
             :toteuma-id toteuma}
            +kayttaja-jvh+
            false))
        tunnisteet))))



(deftest tallenna-toteuma-materiaaleja-test
  (let [[toteuma_id sopimus] (first (q (str "SELECT id, sopimus FROM toteuma WHERE urakka=" @oulun-alueurakan-2005-2010-id
                                            "AND luoja IN (SELECT id FROM kayttaja WHERE jarjestelma IS NOT TRUE) LIMIT 1")))
        vanha-maara 12398751
        uusi-maara 12
        toteumamateriaalit (atom [{:toteuma toteuma_id :maara vanha-maara :materiaalikoodi 1}
                                  {:toteuma toteuma_id :maara vanha-maara :materiaalikoodi 1}])
        parametrit {:toteumamateriaalit @toteumamateriaalit
                    :urakka-id @oulun-alueurakan-2005-2010-id
                    :sopimus sopimus}
        hae-materiaalitoteumien-maara (fn [id] (ffirst (q (str "SELECT count(*) FROM toteuma_materiaali
                                                                WHERE poistettu IS NOT TRUE AND toteuma=" id))))
        vanhat-materiaalitoteumat-lukumaara (hae-materiaalitoteumien-maara toteuma_id)
        hae-tm-idt (fn [] (flatten (q (str "SELECT id FROM toteuma_materiaali WHERE maara=" vanha-maara " AND materiaalikoodi=1
                                           AND poistettu IS NOT TRUE AND toteuma=" toteuma_id))))

        tmid (atom nil)]

    ;; Luo kaksi uutta materiaalitoteumaa ja varmista että palvelu vastaa nil
    ;; (palauttaa urakassa käytetyt materiaalit jos hoitokausi on annettu)
    (is (nil? (kutsu-palvelua (:http-palvelin jarjestelma)
                              :tallenna-toteuma-materiaaleja!
                              +kayttaja-jvh+
                              parametrit))
        "Palvelun ei pitäisi palauttaa mitään jos hoitokautta ei ole annettu")
    (is (= (hae-materiaalitoteumien-maara toteuma_id) (+ 2 vanhat-materiaalitoteumat-lukumaara))
        "Tallentaminen epäonnistui?")

    ;; Lisätyille materiaalitoteumille pitää hakea id:t. Koska palvelu on vähän purkkaa, niin pitää vaan hakea ne mätsäämällä
    ;; toteumaan ja määrään. Jos siis tulee eri määrä kuin kaksi, niin ei testiä voi oikein jatkaa.
    (is (= (count (hae-tm-idt)) 2) "Testissä on ongelma? Kannasta pitäisi löytyä vain kaksi ehtoihin sopivaa materiaalitoteumaa")

    ;; Lisää id:t parametreihin. Hard koodattu, mutta eipä se testissä haittaa
    (reset! toteumamateriaalit
            [(-> (assoc (first @toteumamateriaalit) :id (first (hae-tm-idt)))
                 (assoc :poistettu true))
             (-> (assoc (second @toteumamateriaalit) :id (second (hae-tm-idt)))
                 (assoc :maara uusi-maara))])

    ;; Otetaan päivtettävän materiaalitoteuman id talteen
    (reset! tmid (second (hae-tm-idt)))


    ;; Nyt palvelun pitäisi palauttaa *jotain*, koska hoitokausi menee mukaan
    (is (not (nil?
               (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-toteuma-materiaaleja! +kayttaja-jvh+
                               (-> (assoc parametrit :toteumamateriaalit @toteumamateriaalit)
                                   (assoc :hoitokausi [(pvm/luo-pvm 2005 9 1) (pvm/luo-pvm 2006 8 30)]))))))

    (is (= (hae-materiaalitoteumien-maara toteuma_id) (+ 1 vanhat-materiaalitoteumat-lukumaara)))
    (is (= uusi-maara (int (ffirst (q (str "SELECT maara FROM toteuma_materiaali WHERE id=" @tmid)))))
        "Toteumamateriaalin määrän olisi pitänyt päivittyä.")

    (u (str "DELETE FROM toteuma_materiaali WHERE id=" @tmid))))

(deftest jarjestelman-luomia-materiaaleja-ei-voi-muokata
  (let [[toteuma_id sopimus] (first (q (str "SELECT id, sopimus FROM toteuma WHERE urakka=" @oulun-alueurakan-2005-2010-id "
                                             AND luoja IN (SELECT id FROM kayttaja WHERE jarjestelma IS TRUE) LIMIT 1")))
        vanha-maara 12398751
        toteumamateriaalit (atom [{:toteuma toteuma_id :maara vanha-maara :materiaalikoodi 1}
                                  {:toteuma toteuma_id :maara vanha-maara :materiaalikoodi 1}])
        parametrit {:toteumamateriaalit @toteumamateriaalit
                    :urakka-id @oulun-alueurakan-2005-2010-id
                    :sopimus sopimus}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-toteuma-materiaaleja!
                                                   +kayttaja-jvh+
                                                   parametrit)))))

(def suolakirjauksen-testipayload
  {:otsikko {:lahettaja {:jarjestelma "Urakoitsijan järjestelmä"
                         :organisaatio {:nimi "Urakoitsija"
                                        :ytunnus "1234567-8"}}
             :viestintunniste {:id 123}
             :lahetysaika "2018-01-18T12:00:00+02:00"}
   :reittitoteuma {:toteuma {:tunniste {:id 123}
                             :suorittaja {:nimi "Tehotekijät Oy"
                                          :ytunnus "8765432-1"}
                             :sopimusId @oulun-alueurakan-2014-2019-paasopimuksen-id
                             :alkanut "2018-01-18T12:00:00+02:00"
                             :paattynyt "2018-01-18T12:30:00+02:00"
                             :toteumatyyppi "kokonaishintainen"
                             :tehtavat [{:tehtava {:id 1369
                                                   :maara {:yksikko "km"
                                                           :maara 123}}}]
                             :materiaalit [{:materiaali "Talvisuolaliuos NaCl"
                                            :maara {:yksikko "t"
                                                    :maara 8}}]}
                   :reitti [{:reittipiste {:aika "2018-01-18T12:00:00+02:00"
                                           :koordinaatit {:x 448353
                                                          :y 7225182}
                                           :tehtavat [{:tehtava {:id 1369}}]
                                           :materiaalit [{:materiaali "Talvisuolaliuos NaCl"
                                                          :maara {:yksikko "t"
                                                                  :maara 4}}]}}
                            {:reittipiste {:aika "2018-01-18T12:15:00+02:00"
                                           :koordinaatit {:x 455574
                                                          :y 7227716}
                                           :tehtavat [{:tehtava {:id 1359}}]
                                           :materiaalit [{:materiaali "Talvisuolaliuos NaCl"
                                                          :maara {:yksikko "t"
                                                                  :maara 1}}]}}
                            {:reittipiste {:aika "2018-01-18T12:30:00+02:00"
                                           :koordinaatit {:x 459628
                                                          :y 7223852}
                                           :tehtavat [{:tehtava {:id 1359}}]
                                           :materiaalit [{:materiaali "Talvisuolaliuos NaCl"
                                                          :maara {:yksikko "t"
                                                                  :maara 3}}]}}]}})

(deftest hae-suolatoteumien-trvali-haku
  (let [tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-suolatoteumat-tr-valille +kayttaja-jvh+ {:urakka-id 2
                                                                            :tie 20
                                                                            :alkuosa 14
                                                                            :alkuet 0
                                                                            :loppuosa 18
                                                                            :loppuet 10
                                                                            :threshold 50
                                                                            :alkupvm #inst "2000-02-17T00:00:00.000-00:00"
                                                                            :loppupvm #inst "2018-02-17T00:00:00.000-00:00"})]
    (is (>= (count tulos) 2))
    (let [rivi (first (filter #(= 1 (:rivinumero %))
                              tulos))]
      (is (= 1 (:rivinumero rivi)))
      (is (= 2 (:lukumaara rivi)))
      (is (= 10M (:maara rivi)))
      (is (= {:id 1 :nimi "Talvisuolaliuos NaCl"} (:materiaali rivi))))))

(deftest hae-suolatoteumien-haku
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        testidatasta (kutsu-palvelua
                       (:http-palvelin jarjestelma)
                       :hae-suolatoteumat
                       +kayttaja-jvh+
                       {:urakka-id urakka-id
                        :sopimus-id @oulun-alueurakan-2014-2019-paasopimuksen-id
                        :alkupvm #inst "2015-02-17T00:00:00.000-00:00"
                        :loppupvm #inst "2015-02-19T00:00:00.000-00:00"})
        hae-paivan-materiaalin-kaytto (fn [materiaali-nimi pvm data]
                                        (filter #(and (= pvm (tc/from-sql-date (:pvm %)))
                                                      (= materiaali-nimi (get-in % [:materiaali :nimi])))
                                                data))
        kirjaa-materiaalitoteuma (fn [alkanut loppunut ulkoinen-id materiaalinimi maara]
                                   (u "INSERT INTO toteuma (urakka,
                                                            sopimus,
                                                            luotu,
                                                            alkanut,
                                                            paattynyt,
                                                            luoja,
                                                            tyyppi,
                                                            lahde,
                                                            ulkoinen_id)
                                       VALUES ((SELECT id FROM urakka WHERE sampoid = '1242141-OULU2'),
                                               (SELECT id FROM sopimus WHERE sampoid = '2H16339/01'),
                                               '" alkanut "',
                                               '" alkanut "',
                                               '" loppunut "',
                                               (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus'),
                                               'kokonaishintainen',
                                               'harja-api',
                                               " ulkoinen-id ");")
                                   (u "INSERT INTO toteuma_materiaali (toteuma,
                                                                       materiaalikoodi,
                                                                       maara,
                                                                       luoja, urakka_id)
                                       VALUES ((SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id "),
                                               (SELECT id FROM materiaalikoodi WHERE nimi = '" materiaalinimi "'),
                                               " maara ",
                                               (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus'),
                                               (SELECT id FROM urakka WHERE sampoid = '1242141-OULU2'));"))]
    (is (= 200M (:maara (first (hae-paivan-materiaalin-kaytto "Talvisuola" (t/date-time 2015 2 18 22) testidatasta))))
        "Testidatasta haettu määrä vastaa odotettua")

    (is (= 1800M (:maara (first (hae-paivan-materiaalin-kaytto "Talvisuolaliuos NaCl" (t/date-time 2015 2 17 22) testidatasta))))
        "Testidatasta haettu määrä vastaa odotettua")

    (kirjaa-materiaalitoteuma "2018-01-18 12:00:00.000000" "2018-01-18 12:30:00.000000" 12356789 "Talvisuolaliuos NaCl" 10)
    (kirjaa-materiaalitoteuma "2018-01-18 12:30:00.000000" "2018-01-18 13:00:00.000000" 12356710 "Talvisuolaliuos NaCl" 21)
    (kirjaa-materiaalitoteuma "2018-01-18 13:00:00.000000" "2018-01-18 13:39:00.000000" 12356711 "Talvisuola" 2)

    (let [koneellisesti-kirjatut (kutsu-palvelua
                                   (:http-palvelin jarjestelma)
                                   :hae-suolatoteumat
                                   +kayttaja-jvh+
                                   {:urakka-id urakka-id
                                    :sopimus-id @oulun-alueurakan-2014-2019-paasopimuksen-id
                                    :alkupvm #inst "2018-01-01T00:00:00.000-00:00"
                                    :loppupvm #inst "2018-02-01T00:00:00.000-00:00"})]
      (is (= 2 (count koneellisesti-kirjatut)) "Kirjauksia löytyy 2 päivälle.")
      (let [nacl-kirjaukset (filter #(= "Talvisuolaliuos NaCl" (get-in % [:materiaali :nimi])) koneellisesti-kirjatut)
            hcoona-kirjaukset (filter #(= "Talvisuola" (get-in % [:materiaali :nimi])) koneellisesti-kirjatut)]
        (is (= 2 (:lukumaara (first nacl-kirjaukset))) "Talvisuolaliuos NaCl kirjaukset koostuvat 2 toteumasta")
        (is (= 31M (apply + (map :maara nacl-kirjaukset))) "Määrä on summa kaikista Talvisuolaliuos NaCl kirjauksista")

        (is (= 1 (:lukumaara (first hcoona-kirjaukset))) "Talvisuolaliuos NaCl kirjaukset koostuvat 2 toteumasta")
        (is (= 2M (apply + (map :maara hcoona-kirjaukset))) "Määrä on summa kaikista Talvisuolaliuos NaCl kirjauksista")))))


(deftest hae-suolatoteumien-tarkat-tiedot-test
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        materiaali-id (ffirst (q "SELECT id FROM materiaalikoodi where nimi = 'Hiekoitushiekka';"))
        testidatasta (kutsu-palvelua
                       (:http-palvelin jarjestelma)
                       :hae-suolatoteumien-tarkat-tiedot
                       +kayttaja-jvh+
                       {:toteumaidt [22]
                        :materiaali-id materiaali-id
                        :urakka-id urakka-id})]
    (is (boolean (some #(= (:maara %) 500M) testidatasta)))
    (is (boolean (some #(= (:maara %) 555M) testidatasta)))
    (is (boolean (some #(= (:tid %) 22) testidatasta)))
    (is (= 2 (count testidatasta)))))


(def suolatoteumat [{:rivinumero -1, :alkanut #inst "2020-08-26T05:25:22.000-00:00", :materiaali {:id 1, :nimi "Talvisuolaliuos NaCl", :yksikko "t", :kohdistettava false, :materiaalityyppi "talvisuola", :urakkatyyppi "hoito"}, :pvm #inst "2020-08-25T21:00:00.000-00:00", :maara 666, :lisatieto "555", :paattynyt #inst "2020-08-26T05:25:22.000-00:00"}])

(deftest tallenna-suolatoteumat-testi
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-suolatoteumat +kayttaja-jvh+ {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                                        :sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
                                                                        :toteumat suolatoteumat})]
    (is (true? vastaus) "Suolatoteuman tallennus")))