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
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
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

;; VHAR-5571 aiheutti unique constraint poikkeuksen ennen korjausta
(deftest tallenna-suunniteltu-materiaali-tulevillekin-hoitokausille
  (let [urakka-id @oulun-alueurakan-2014-2019-id
        sopimus-id @oulun-alueurakan-2014-2019-paasopimuksen-id
        tallennus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-suunnitellut-materiaalit
                                  +kayttaja-jvh+
                                  {:urakka-id urakka-id
                                   :sopimus-id sopimus-id
                                   :hoitokausi [(pvm/->pvm "1.10.2014") (pvm/->pvm "30.9.2015")]
                                   :hoitokaudet [[(pvm/->pvm "1.10.2014") (pvm/->pvm "30.9.2015")]
                                                 [(pvm/->pvm "1.10.2015") (pvm/->pvm "30.9.2016")]
                                                 [(pvm/->pvm "1.10.2016") (pvm/->pvm "30.9.2017")]
                                                 [(pvm/->pvm "1.10.2017") (pvm/->pvm "30.9.2018")]
                                                 [(pvm/->pvm "1.10.2018") (pvm/->pvm "30.9.2019")]]
                                   :tulevat-hoitokaudet-mukana? true
                                   :materiaalit [{:id 1
                                                  :sopimus sopimus-id
                                                  :alkupvm (pvm/->pvm "1.10.2014")
                                                  :loppupvm (pvm/->pvm "30.9.2015")
                                                  :materiaali {:id 5 :nimi "Hiekoitushiekka" :yksikko "t"}
                                                  :maara 666}
                                                 {:id 1
                                                  :sopimus sopimus-id
                                                  :alkupvm (pvm/->pvm "1.10.2015")
                                                  :loppupvm (pvm/->pvm "30.9.2016")
                                                  :materiaali {:id 5 :nimi "Hiekoitushiekka" :yksikko "t"}
                                                  :maara 666}
                                                 {:id 1
                                                  :sopimus sopimus-id
                                                  :alkupvm (pvm/->pvm "1.10.2016")
                                                  :loppupvm (pvm/->pvm "30.9.2017")
                                                  :materiaali {:id 5 :nimi "Hiekoitushiekka" :yksikko "t"}
                                                  :maara 666}
                                                 {:id 1
                                                  :sopimus sopimus-id
                                                  :alkupvm (pvm/->pvm "1.10.2017")
                                                  :loppupvm (pvm/->pvm "30.9.2018")
                                                  :materiaali {:id 5 :nimi "Hiekoitushiekka" :yksikko "t"}
                                                  :maara 666}
                                                 {:id 1
                                                  :sopimus sopimus-id
                                                  :alkupvm (pvm/->pvm "1.10.2018")
                                                  :loppupvm (pvm/->pvm "30.9.2019")
                                                  :materiaali {:id 5 :nimi "Hiekoitushiekka" :yksikko "t"}
                                                  :maara 666}]})
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-materiaalit
                                +kayttaja-jvh+ urakka-id)]
    (is (= (sort-by :id vastaus)
           (sort-by :id [{:id 1, :alkupvm #inst "2014-09-30T21:00:00.000-00:00", :loppupvm #inst "2015-09-29T21:00:00.000-00:00", :maara 666.0, :sopimus 2, :materiaali {:id 5, :nimi "Hiekoitushiekka", :yksikko "t"}} {:id 2, :alkupvm #inst "2015-09-30T21:00:00.000-00:00", :loppupvm #inst "2016-09-29T21:00:00.000-00:00", :maara 666.0, :sopimus 2, :materiaali {:id 5, :nimi "Hiekoitushiekka", :yksikko "t"}} {:id 3, :alkupvm #inst "2016-09-30T21:00:00.000-00:00", :loppupvm #inst "2017-09-29T21:00:00.000-00:00", :maara 666.0, :sopimus 2, :materiaali {:id 5, :nimi "Hiekoitushiekka", :yksikko "t"}} {:id 4, :alkupvm #inst "2017-09-30T21:00:00.000-00:00", :loppupvm #inst "2018-09-29T21:00:00.000-00:00", :maara 666.0, :sopimus 2, :materiaali {:id 5, :nimi "Hiekoitushiekka", :yksikko "t"}} {:id 6, :alkupvm #inst "2018-09-30T21:00:00.000-00:00", :loppupvm #inst "2019-09-29T21:00:00.000-00:00", :maara 666.0, :sopimus 2, :materiaali {:id 5, :nimi "Hiekoitushiekka", :yksikko "t"}}])))))

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

(deftest tallenna-toteumamateriaaleja-cachet-pysyy-jiirissa-kun-pvm-muuttuu
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
        sopimuksen-kaytetty-mat-ennen-odotettu (set [[2 #inst "2015-02-17T22:00:00.000-00:00" 1 1800M]
                                                     [2 #inst "2015-02-18T22:00:00.000-00:00" 7 200M]
                                                     [2 #inst "2015-02-18T22:00:00.000-00:00" 16 2000M]])
        sopimuksen-kaytetty-mat-jalkeen-odotettu (set [[2 #inst "2015-02-17T22:00:00.000-00:00" 1 1800M]
                                                       [2 #inst "2015-02-18T22:00:00.000-00:00" 7 200M]
                                                       [2 #inst "2015-02-18T22:00:00.000-00:00" 16 123M]])
        hoitoluokittaiset-ennen-odotettu (set [[#inst "2015-02-17T22:00:00.000-00:00" 1 99 4 1800M]
                                               [#inst "2015-02-18T22:00:00.000-00:00" 7 99 4 200M]
                                               [#inst "2015-02-18T22:00:00.000-00:00" 16 99 4 2000M]])
        hoitoluokittaiset-jalkeen-odotettu (set [[#inst "2015-02-17T22:00:00.000-00:00" 1 99 4 1800M]
                                                 [#inst "2015-02-18T22:00:00.000-00:00" 7 99 4 200M]
                                                 [#inst "2015-02-18T22:00:00.000-00:00" 16 99 4 123M]])
        sopimuksen-mat-kaytto-ennen (set (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id
                                                 (pvm-vali-sql-tekstina "alkupvm" "'2015-02-01' AND '2015-02-28'") ";")))
        hoitoluokittaiset-ennen (set (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka-id
                                             (pvm-vali-sql-tekstina "pvm" "'2015-02-01' AND '2015-02-28'") ";")))
        toteuman-id (ffirst (q (str "SELECT id FROM toteuma WHERE lisatieto = 'LYV-toteuma Natriumformiaatti';")))
        tm-id (ffirst (q (str "SELECT id FROM toteuma_materiaali WHERE toteuma = " toteuman-id ";")))


        toteuma-materiaalit [{:id tm-id, :toteuma toteuman-id :sopimus sopimus-id :materiaalikoodi 16 :maara 123}]]
    ;; tarkistetaan että kaikki cachesta palautetut tulokset löytyvät expected-setistä
    (is (= sopimuksen-kaytetty-mat-ennen-odotettu sopimuksen-mat-kaytto-ennen ) "sopimuksen materiaalin käyttö cache ennen muutosta")
    (is (= hoitoluokittaiset-ennen-odotettu hoitoluokittaiset-ennen ) "hoitoluokittaisten cache ennen muutosta")

    ;; kyseessä päivitys, löytyvät kannasta jo ennen palvelukutsua
    (is (= 1 (ffirst (q (str "SELECT count(*) FROM toteuma WHERE id = " toteuman-id " AND poistettu IS NOT TRUE;")))))
    (is (= 1 (ffirst (q (str "SELECT count(*) FROM toteuma_materiaali WHERE toteuma =" toteuman-id " AND poistettu IS NOT TRUE;")))))

    (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-toteuma-materiaaleja! +kayttaja-jvh+
                    {:toteumamateriaalit toteuma-materiaalit
                     :hoitokausi [#inst "2014-09-30T21:00:00.000-00:00" #inst "2015-09-30T20:59:59.000-00:00"]
                     :urakka-id urakka-id
                     :sopimus sopimus-id})
    ;; lisäyksen jälkeenkin jutut löytyvät kannasta yhden kerran...
    (is (= 1 (ffirst (q (str "SELECT count(*) FROM toteuma_materiaali WHERE toteuma = " toteuman-id ";")))))
    (is (= 1 (ffirst (q (str "SELECT count(*) FROM toteuma WHERE id=" toteuman-id " AND poistettu IS NOT TRUE;")))))

    (let [sopimuksen-mat-kaytto-jalkeen (set (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id
                                                     (pvm-vali-sql-tekstina "alkupvm" "'2015-02-01' AND '2015-02-28'") ";")))
          hoitoluokittaiset-jalkeen (set (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka-id
                                                 (pvm-vali-sql-tekstina "pvm" "'2015-02-01' AND '2015-02-28'") ";")))]

      ;; lisäyksen jälkeen cachet päivittyvät oikein, vanhalla pvm:llä ollut määrä poistuu, ja uusi määrä uudelle päivällä
      (is (= sopimuksen-kaytetty-mat-jalkeen-odotettu sopimuksen-mat-kaytto-jalkeen ) "sopimuksen materiaalin käyttö cache jalkeen muutosta")
      (is (= hoitoluokittaiset-jalkeen-odotettu hoitoluokittaiset-jalkeen ) "hoitoluokittaisten cache jalkeen muutosta"))))

(deftest tallenna-toteumamateriaaleja-cachet-pysyy-jiirissa-kun-toteuma-poistetaan
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
        sopimuksen-kaytetty-mat-ennen-odotettu (set [[2 #inst "2015-02-17T22:00:00.000-00:00" 1 1800M]
                                                     [2 #inst "2015-02-18T22:00:00.000-00:00" 7 200M]
                                                     [2 #inst "2015-02-18T22:00:00.000-00:00" 16 2000M]])
        sopimuksen-kaytetty-mat-jalkeen-odotettu (set [[2 #inst "2015-02-17T22:00:00.000-00:00" 1 1800M]
                                                       [2 #inst "2015-02-18T22:00:00.000-00:00" 7 200M]
                                                       [2 #inst "2015-02-18T22:00:00.000-00:00" 16 0M]])
        hoitoluokittaiset-ennen-odotettu (set [[#inst "2015-02-17T22:00:00.000-00:00" 1 99 4 1800M]
                                               [#inst "2015-02-18T22:00:00.000-00:00" 7 99 4 200M]
                                               [#inst "2015-02-18T22:00:00.000-00:00" 16 99 4 2000M]])
        hoitoluokittaiset-jalkeen-odotettu (set [[#inst "2015-02-17T22:00:00.000-00:00" 1 99 4 1800M]
                                                 [#inst "2015-02-18T22:00:00.000-00:00" 7 99 4 200M]])
        sopimuksen-mat-kaytto-ennen (set (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id
                                                 (pvm-vali-sql-tekstina "alkupvm" "'2015-02-01' AND '2015-02-28'") ";")))
        hoitoluokittaiset-ennen (set (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka-id
                                             (pvm-vali-sql-tekstina "pvm" "'2015-02-01' AND '2015-02-28'") ";")))
        toteuman-id (ffirst (q (str "SELECT id FROM toteuma WHERE lisatieto = 'LYV-toteuma Natriumformiaatti';")))
        tm-id (ffirst (q (str "SELECT id FROM toteuma_materiaali WHERE toteuma = " toteuman-id ";")))


        toteuma-materiaalit [{:id tm-id, :toteuma toteuman-id :sopimus sopimus-id :materiaalikoodi 16 :maara 2000 :poistettu true}]]
    ;; tarkistetaan että kaikki cachesta palautetut tulokset löytyvät expected-setistä
    (is (= sopimuksen-kaytetty-mat-ennen-odotettu sopimuksen-mat-kaytto-ennen ) "sopimuksen materiaalin käyttö cache ennen muutosta")
    (is (= hoitoluokittaiset-ennen-odotettu hoitoluokittaiset-ennen ) "hoitoluokittaisten cache ennen muutosta")

    ;; kyseessä päivitys, löytyvät kannasta jo ennen palvelukutsua
    (is (= 1 (ffirst (q (str "SELECT count(*) FROM toteuma WHERE id = " toteuman-id " AND poistettu IS NOT TRUE;")))))
    (is (= 1 (ffirst (q (str "SELECT count(*) FROM toteuma_materiaali WHERE toteuma =" toteuman-id " AND poistettu IS NOT TRUE;")))))

    (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-toteuma-materiaaleja! +kayttaja-jvh+
                    {:toteumamateriaalit toteuma-materiaalit
                     :hoitokausi [#inst "2014-09-30T21:00:00.000-00:00" #inst "2015-09-30T20:59:59.000-00:00"]
                     :urakka-id urakka-id
                     :sopimus sopimus-id})
    ;; kutsun jälkeen jutut löytyvät kannasta poistettuna
    (is (= 1 (ffirst (q (str "SELECT count(*) FROM toteuma_materiaali WHERE toteuma = " toteuman-id " AND poistettu IS TRUE;")))))
    (is (= 1 (ffirst (q (str "SELECT count(*) FROM toteuma WHERE id=" toteuman-id " AND poistettu IS NOT TRUE;")))))

    (let [sopimuksen-mat-kaytto-jalkeen (set (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id
                                                     (pvm-vali-sql-tekstina "alkupvm" "'2015-02-01' AND '2015-02-28'") ";")))
          hoitoluokittaiset-jalkeen (set (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka-id
                                                 (pvm-vali-sql-tekstina "pvm" "'2015-02-01' AND '2015-02-28'") ";")))]

      ;; lisäyksen jälkeen cachet päivittyvät oikein, vanhalla pvm:llä ollut määrä poistuu, ja uusi määrä uudelle päivällä
      (is (= sopimuksen-kaytetty-mat-jalkeen-odotettu sopimuksen-mat-kaytto-jalkeen ) "sopimuksen materiaalin käyttö cache jalkeen muutosta")
      (is (= hoitoluokittaiset-jalkeen-odotettu hoitoluokittaiset-jalkeen ) "hoitoluokittaisten cache jalkeen muutosta"))))



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
    (is (= 200M (:maara (first (hae-paivan-materiaalin-kaytto "Talvisuola, rakeinen NaCl" (t/date-time 2015 2 18 22) testidatasta))))
        "Testidatasta haettu määrä vastaa odotettua")

    (is (= 1800M (:maara (first (hae-paivan-materiaalin-kaytto "Talvisuolaliuos NaCl" (t/date-time 2015 2 17 22) testidatasta))))
        "Testidatasta haettu määrä vastaa odotettua")

    (kirjaa-materiaalitoteuma "2018-01-18 12:00:00.000000" "2018-01-18 12:30:00.000000" 12356789 "Talvisuolaliuos NaCl" 10)
    (kirjaa-materiaalitoteuma "2018-01-18 12:30:00.000000" "2018-01-18 13:00:00.000000" 12356710 "Talvisuolaliuos NaCl" 21)
    (kirjaa-materiaalitoteuma "2018-01-18 13:00:00.000000" "2018-01-18 13:39:00.000000" 12356711 "Talvisuola, rakeinen NaCl" 2)

    (let [koneellisesti-kirjatut (kutsu-palvelua
                                   (:http-palvelin jarjestelma)
                                   :hae-suolatoteumat
                                   +kayttaja-jvh+
                                   {:urakka-id urakka-id
                                    :sopimus-id @oulun-alueurakan-2014-2019-paasopimuksen-id
                                    :alkupvm #inst "2018-01-01T00:00:00.000-00:00"
                                    :loppupvm #inst "2018-02-01T00:00:00.000-00:00"})]
      (is (= 3 (count koneellisesti-kirjatut)) "Kirjauksia löytyy 3 päivälle.")
      (let [nacl-kirjaukset (filter #(= "Talvisuolaliuos NaCl" (get-in % [:materiaali :nimi])) koneellisesti-kirjatut)
            hcoona-kirjaukset (filter #(= "Talvisuola, rakeinen NaCl" (get-in % [:materiaali :nimi])) koneellisesti-kirjatut)]
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


(def suolatoteumat [{:rivinumero -1, :alkanut #inst "2015-02-16T05:25:22.000-00:00", :materiaali {:id 1, :nimi "Talvisuolaliuos NaCl", :yksikko "t", :kohdistettava false, :materiaalityyppi "talvisuola", :urakkatyyppi "hoito"}, :pvm #inst "2015-02-16T21:00:00.000-00:00", :maara 666, :lisatieto "555", :paattynyt #inst "2015-02-16T05:25:22.000-00:00"}])

(def odotettu-ennen
  [{:koneellinen false
    :lisatieto "LYV-toteuma Talvisuola"
    :lukumaara 1
    :maara 200M
    :materiaali {:id 7
                 :nimi "Talvisuola, rakeinen NaCl"}
    :pvm #inst "2015-02-18T22:00:00.000000001-00:00"
    :rivinumero 1
    :tid 1074
    :tmid 13
    :toteumaidt [1074]}
   {:koneellinen false
    :lisatieto "LYV-toteuma"
    :lukumaara 1
    :maara 1800M
    :materiaali {:id 1
                 :nimi "Talvisuolaliuos NaCl"}
    :pvm #inst "2015-02-17T22:00:00.000000001-00:00"
    :rivinumero 2
    :tid 1073
    :tmid 12
    :toteumaidt [1073]}])

(def lisatty-toteuma
  {:tid 1156, :pvm #inst "2015-02-15T22:00:00.000000000-00:00", :toteumaidt [1156], :rivinumero 3, :tmid 27, :lukumaara 1, :koneellinen false, :maara 666M, :materiaali {:id 1, :nimi "Talvisuolaliuos NaCl"}, :lisatieto "555"})



;; (id, nimi, yksikko, kohdistettava, materiaalityyppi) VALUES (1, 'Talvisuolaliuos NaCl', 't', false, 'talvisuola');
;; (id, nimi, yksikko, kohdistettava, materiaalityyppi) VALUES (7, 'Talvisuola, rakeinen NaCl', 't', false, 'talvisuola');
;; (id, nimi, yksikko, kohdistettava, materiaalityyppi) VALUES (16, 'Natriumformiaatti', 't', false, 'muu');
(def sopimuksen-kaytetty-mat-ennen-odotettu
  [[2 #inst "2015-02-17T22:00:00.000-00:00" 1 1800M]
   [2 #inst "2015-02-18T22:00:00.000-00:00" 7 200M]
   [2 #inst "2015-02-18T22:00:00.000-00:00" 16 2000M]])

(defn- sopimuksen-kaytetty-mat-jalkeen-odotettu [lisatty]
  [lisatty
   [2 #inst "2015-02-17T22:00:00.000-00:00" 1 1800M]
   [2 #inst "2015-02-18T22:00:00.000-00:00" 7 200M]
   [2 #inst "2015-02-18T22:00:00.000-00:00" 16 2000M]])

(def hoitoluokittaiset-ennen-odotettu
  [[#inst "2015-02-17T22:00:00.000-00:00" 1 99 4 1800M]
   [#inst "2015-02-18T22:00:00.000-00:00" 7 99 4 200M]
   [#inst "2015-02-18T22:00:00.000-00:00" 16 99 4 2000M]])

(defn- hoitoluokittaiset-jalkeen-odotettu [lisatty]
  [lisatty
   [#inst "2015-02-17T22:00:00.000-00:00" 1 99 4 1800M]
   [#inst "2015-02-18T22:00:00.000-00:00" 7 99 4 200M]
   [#inst "2015-02-18T22:00:00.000-00:00" 16 99 4 2000M]])



;; 1. tarkista alkutila, myös tauluissa sopimuksen_kaytetty_materiaali ja urakan_materiaalin_kaytto_hoitoluokittain
;; 2. tallenna suolatoteumia "käsin"
;; 3. assertoi että sopimuksen_kaytetty_materiaali ja urakan_materiaalin_kaytto_hoitoluokittain päivittyvät oikein
;; käsin syötetyt materiaalit pitää näkyä hoitoluokalla 100, eli "ei tiedossa"

(deftest tallenna-suolatoteumat-testi
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
        ennen  (kutsu-palvelua
                 (:http-palvelin jarjestelma)
                 :hae-suolatoteumat
                 +kayttaja-jvh+
                 {:urakka-id urakka-id
                  :sopimus-id @oulun-alueurakan-2014-2019-paasopimuksen-id
                  :alkupvm #inst "2015-02-15T00:00:00.000-00:00"
                  :loppupvm #inst "2015-02-19T00:00:00.000-00:00"})

        sopimuksen-mat-kaytto-ennen (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id
                                            (pvm-vali-sql-tekstina "alkupvm" "'2015-02-01' AND '2015-02-28'") ";"))
        hoitoluokittaiset-ennen (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka-id
                                        (pvm-vali-sql-tekstina "pvm" "'2015-02-01' AND '2015-02-28'") ";"))

        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-suolatoteumat +kayttaja-jvh+ {:urakka-id urakka-id
                                                                        :sopimus-id sopimus-id
                                                                        :toteumat suolatoteumat})
        jalkeen (kutsu-palvelua
                  (:http-palvelin jarjestelma)
                  :hae-suolatoteumat
                  +kayttaja-jvh+
                  {:urakka-id urakka-id
                   :sopimus-id @oulun-alueurakan-2014-2019-paasopimuksen-id
                   :alkupvm #inst "2015-02-15T00:00:00.000-00:00"
                   :loppupvm #inst "2015-02-19T00:00:00.000-00:00"})
        odotettu-yht-rivi {:lukumaara 2
                           :maara 2000M
                           :materiaali {:nimi "Yhteenveto"}
                           :rivinumero 3
                           :yhteenveto true
                           :yhteenveto-vayla true}
        odotettu-yht-rivi-jalkeen {:lukumaara 3
                                   :maara 2666M
                                   :materiaali {:nimi "Yhteenveto"}
                                   :rivinumero 4
                                   :yhteenveto true
                                   :yhteenveto-vayla true}
        odotettu-jalkeen (conj odotettu-ennen lisatty-toteuma odotettu-yht-rivi-jalkeen)
        sopimuksen-mat-kaytto-jalkeen (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id
                                              (pvm-vali-sql-tekstina "alkupvm" "'2015-02-01' AND '2015-02-28'") ";"))
        hoitoluokittaiset-jalkeen (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka-id
                                          (pvm-vali-sql-tekstina "pvm" "'2015-02-01' AND '2015-02-28'") ";"))]

    (is (= (map #(dissoc % :pvm :tid :toteumaidt) ennen) (map #(dissoc % :pvm :tid :toteumaidt) (conj odotettu-ennen odotettu-yht-rivi))) "Suolatoteumat ennen lisäystä")
    (is (= (map #(dissoc % :pvm :tid :toteumaidt) jalkeen) (map #(dissoc % :pvm :tid :toteumaidt) odotettu-jalkeen)) "Suolatoteumat jälkeen lisäyksen")

    (is (= sopimuksen-mat-kaytto-ennen sopimuksen-kaytetty-mat-ennen-odotettu) "Materiaalicache 1 ennen OK")
    (is (= sopimuksen-mat-kaytto-jalkeen (sopimuksen-kaytetty-mat-jalkeen-odotettu [2 #inst "2015-02-15T22:00:00.000-00:00" 1 666M])) "Materiaalicache 1 jälkeen OK")
    (is (= hoitoluokittaiset-ennen hoitoluokittaiset-ennen-odotettu) "Hoitoluokittainen materiaalicache ennen OK")
    (is (= hoitoluokittaiset-jalkeen (hoitoluokittaiset-jalkeen-odotettu [#inst "2015-02-15T22:00:00.000-00:00" 1 99 4 666M])) "Hoitoluokittainen materiaalicache jälkeen OK")

    (is (true? vastaus) "Suolatoteuman tallennus")))

(defn lisattava-suolatoteuma [toteuman-id tm-id toteuman-pvm]
  [{:tid toteuman-id :tmid tm-id :rivinumero 1, :alkanut #inst "2018-02-16T05:25:22.000-00:00", :materiaali {:id 7, :nimi "Talvisuola", :yksikko "t", :kohdistettava false, :materiaalityyppi "talvisuola", :urakkatyyppi "hoito"}, :pvm toteuman-pvm, :maara 500, :lisatieto "555", :paattynyt #inst "2018-02-16T05:25:22.000-00:00"}])

;; 1. Päivitä kannassa jo olevan toteuman  PVM:ää
;; 2. Assertoi että cachesta poistuu määrä 200 vanhalta pvm:ltä 19.2.2015
;; 3. Assertoi että cacheen lisääntyy määrä 500 uudelle pvm:lle 14.2.2015
(deftest tallenna-suolatoteumat-kasin-muokataan-pvmaa-cachet-toimii
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)

        sopimuksen-mat-kaytto-ennen (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id
                                            (pvm-vali-sql-tekstina "alkupvm" "'2015-02-01' AND '2015-02-28'") ";"))
        hoitoluokittaiset-ennen (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka-id
                                        (pvm-vali-sql-tekstina "pvm" "'2015-02-01' AND '2015-02-28'") ";"))
        toteuman-id (ffirst (q (str "SELECT id FROM toteuma WHERE lisatieto = 'LYV-toteuma Talvisuola';")))
        tm-id (ffirst (q (str "SELECT id FROM toteuma_materiaali WHERE toteuma = " toteuman-id ";")))
        lisattava-toteuma (lisattava-suolatoteuma toteuman-id tm-id (pvm/->pvm "14.2.2015"))
        ;; TALLENNETAAN TÄSSÄ SUOLATOTEUMA KUTEN KÄYTTÖLIITTYMÄSTÄ
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-suolatoteumat +kayttaja-jvh+ {:urakka-id urakka-id
                                                                        :sopimus-id sopimus-id
                                                                        :toteumat lisattava-toteuma})
        sopimuksen-kaytetty-mat-jalkeen-odotettu (set [[2 #inst "2015-02-17T22:00:00.000-00:00" 1 1800M]
                                                       [2 #inst "2015-02-13T22:00:00.000-00:00" 7 500M]
                                                       [2 #inst "2015-02-18T22:00:00.000-00:00" 16 2000M]])
        hoitoluokittaiset-jalkeen-odotettu-pvm-muuttunut (set [[#inst "2015-02-17T22:00:00.000-00:00" 1 99 4 1800M]
                                                               [#inst "2015-02-13T22:00:00.000-00:00" 7 99 4 500M] ;; tässä uusi pvm
                                                               [#inst "2015-02-18T22:00:00.000-00:00" 16 99 4 2000M]])

        sopimuksen-mat-kaytto-jalkeen (set
                                        (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id
                                                (pvm-vali-sql-tekstina "alkupvm" "'2015-02-01' AND '2015-02-28'") ";")))
        hoitoluokittaiset-jalkeen (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka-id
                                          (pvm-vali-sql-tekstina "pvm" "'2015-02-01' AND '2015-02-28'") ";"))]
    (is (true? vastaus) "onnistui")
    (is (= sopimuksen-mat-kaytto-ennen sopimuksen-kaytetty-mat-ennen-odotettu) "Materiaalicache 1 ennen OK")
    (is (= sopimuksen-mat-kaytto-jalkeen sopimuksen-kaytetty-mat-jalkeen-odotettu)
        "Materiaalicache 1 jälkeen OK")
    (is (= hoitoluokittaiset-ennen-odotettu hoitoluokittaiset-ennen) "Hoitoluokittainen materiaalicache ennen OK")
    (is (= hoitoluokittaiset-jalkeen-odotettu-pvm-muuttunut (set hoitoluokittaiset-jalkeen)) "Hoitoluokittainen materiaalicache jälkeen OK")))
