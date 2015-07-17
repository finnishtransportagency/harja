(ns harja.palvelin.palvelut.materiaalit-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.materiaalit :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
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
                        :poista-toteuma-materiaali! (component/using
                                                      (->Materiaalit)
                                                      [:http-palvelin :db])
                        :tallenna-toteuma-materiaaleja! (component/using
                                                          (->Materiaalit)
                                                          [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-materiaalit-sarakkeet
  (is (oikeat-sarakkeet-palvelussa? [:id :nimi :yksikko :urakkatyyppi :kohdistettava] :hae-materiaalikoodit)))

(deftest hae-urakan-materiaalit-sarakkeet
  (is (oikeat-sarakkeet-palvelussa?
        [:id :alkupvm :loppupvm :maara :sopimus [:materiaali :id] [:materiaali :nimi] [:materiaali :yksikko]
         :kokonaismaara]
        :hae-urakan-materiaalit @oulun-alueurakan-id)))

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
        true?
        (map
        (fn [[urakka toteuma]]
          (oikeat-sarakkeet-palvelussa?
            [[:toteumamateriaalit 0 :materiaali :nimi] [:toteumamateriaalit 0 :materiaali :yksikko]
             [:toteumamateriaalit 0 :maara] :alkanut :paattynyt [:toteumamateriaalit 0 :materiaali  :id]
             :id [:toteumamateriaalit 0 :tmid] :suorittaja :ytunnus :lisatieto]
            :hae-toteuman-materiaalitiedot
            {:urakka-id urakka
             :toteuma-id toteuma}))
        tunnisteet)))))

(deftest hae-urakassa-kaytetyt-materiaalit-sarakkeet
  (let [tunnisteet (q "SELECT DISTINCT t.urakka, t.alkanut, t.paattynyt, t.sopimus
                       FROM materiaalikoodi m
                       LEFT JOIN materiaalin_kaytto mk
                       ON m.id = mk.materiaali AND mk.poistettu IS NOT TRUE
                       LEFT JOIN toteuma_materiaali tm
                       ON tm.materiaalikoodi = m.id and tm.poistettu IS NOT TRUE
                       LEFT JOIN toteuma t
                       ON t.id = tm.toteuma AND t.poistettu IS NOT TRUE
                       WHERE t.urakka is not null and t.alkanut is not null and t.paattynyt is not null and
                       t.sopimus is not null;")]
  (is
    (some
      true?
      (map
        (fn [[urakka alkanut paattynyt sopimus]]
          (oikeat-sarakkeet-palvelussa?
            [[:materiaali :nimi] :maara [:materiaali :yksikko] [:materiaali :id] :kokonaismaara]
            :hae-urakassa-kaytetyt-materiaalit
            {:urakka-id urakka
             :hk-alku alkanut
             :hk-loppu paattynyt
             :sopimus sopimus}))
        tunnisteet)))))

;; TODO: Joku joka käyttää tätä palvelua voisi tehdä testinkin..? Teemu
(deftest tallenna-urakan-materiaalit-test
  (let [] (is true)))

(deftest tallenna-toteuma-materiaaleja-test
  (let [[toteuma_id sopimus] (first (q (str "SELECT id, sopimus FROM toteuma WHERE urakka="@oulun-alueurakan-id" LIMIT 1")))
        vanha-maara 12398751
        uusi-maara 12
        toteumamateriaalit (atom [{:toteuma toteuma_id :maara vanha-maara :materiaalikoodi 1} {:toteuma toteuma_id :maara vanha-maara :materiaalikoodi 1}])
        parametrit {:toteumamateriaalit @toteumamateriaalit :urakka-id @oulun-alueurakan-id :sopimus sopimus}
        hae-materiaalitoteumien-maara (fn [id] (ffirst (q (str "SELECT count(*) FROM toteuma_materiaali
                                                                WHERE poistettu IS NOT TRUE AND toteuma="id))))
        vanhat-materiaalitoteumat-lukumaara (hae-materiaalitoteumien-maara toteuma_id)
        hae-tm-idt (fn [] (flatten (q (str"SELECT id FROM toteuma_materiaali WHERE maara="vanha-maara" AND materiaalikoodi=1
                                           AND poistettu IS NOT TRUE AND toteuma="toteuma_id))))

        tmid (atom nil)]

    ;; Luo kaksi uutta materiaalitoteumaa ja varmista että palvelu vastaa nil
    ;; (palauttaa urakassa käytetyt materiaalit jos hoitokausi on annettu)
    (is (nil? (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-toteuma-materiaaleja! +kayttaja-jvh+ parametrit))
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
                                   (assoc :hoitokausi [(java.sql.Date. 105 9 1) (java.sql.Date. 106 8 30)]))))))

    (is (= (hae-materiaalitoteumien-maara toteuma_id) (+ 1 vanhat-materiaalitoteumat-lukumaara)))
    (is (= uusi-maara (int (ffirst (q (str "SELECT maara FROM toteuma_materiaali WHERE id="@tmid)))))
        "Toteumamateriaalin määrän olisi pitänyt päivittyä.")

    (u (str "DELETE FROM toteuma_materiaali WHERE id="@tmid))))

(deftest poista-toteuma-materiaali-test
  (let [maara 874625
        [urakka sopimus] (first (q "SELECT urakka, sopimus FROM toteuma WHERE id=1"))
        hoitokausi [(java.sql.Date. 105 9 1) (java.sql.Date. 106 8 30)]
        lisaa-materiaalitoteuma (fn [] (ffirst (q "INSERT INTO toteuma_materiaali
                                          (toteuma, materiaalikoodi, maara, luotu, luoja, poistettu)
                                          VALUES (1, 1, "maara", NOW(), "(:id +kayttaja-jvh+)", false) RETURNING id;" )))
        hae-lkm (fn [] (ffirst (q (str "SELECT count(*) FROM toteuma_materiaali WHERE poistettu IS NOT TRUE and toteuma=1;"))))
        alkuperainen-lkm (hae-lkm)
        lisatyt (doall (repeatedly 3 lisaa-materiaalitoteuma))]
    (log/debug (pr-str lisatyt))

    (is (= (hae-lkm) (+ 3 alkuperainen-lkm)))

    ;; Poistamisen pitäisi palauttaa urakassa käytetyt materiaalit jos hoitokausi annetaan
    (is (-> (kutsu-palvelua (:http-palvelin jarjestelma) :poista-toteuma-materiaali! +kayttaja-jvh+
                    {:urakka urakka
                     :sopimus sopimus
                     :id (vec lisatyt)
                     :hk-alku (first hoitokausi)
                     :hk-loppu (second hoitokausi)})
            (first)
            (:kokonaismaara)))

    (is (= alkuperainen-lkm (hae-lkm)))

    (log/debug (vec lisatyt))
    (u (str "DELETE FROM toteuma_materiaali WHERE id in ("(clojure.string/join "," lisatyt)")"))))
