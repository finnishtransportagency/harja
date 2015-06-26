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

(defn sisaltaa-ainakin-sarakkeet?
  [tulos sarakkeet]
  (nil?
    (some
      false?
      (map
        #(contains?
          (get-in
            (if (vector? tulos) (first tulos) tulos)
            (when (vector? %) (butlast %)))
          (if (vector? %) (last %) %)) sarakkeet))))

(defn oikeat-sarakkeet-palvelussa?
  ([sarakkeet palvelu]
   (sisaltaa-ainakin-sarakkeet? (kutsu-palvelua (:http-palvelin jarjestelma)
                                                palvelu +kayttaja-jvh+) sarakkeet))
  ([sarakkeet palvelu parametrit]
  (sisaltaa-ainakin-sarakkeet? (kutsu-palvelua (:http-palvelin jarjestelma)
                                               palvelu +kayttaja-jvh+
                                               parametrit) sarakkeet)))



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
    ;; TODO: Olettaisin että täm toimii oikeasti laiskasti, mutta nyt näyttää että ei. Miksi? :(
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

;; TODO: Testit näille puuttuu, mutta jätin tähän muistutukseksi. Lisätään joku sunnuntai..
(deftest tallenna-urakan-materiaalit-test
  (is true))

(deftest tallenna-toteuma-materiaaleja-test
  (is true))

(deftest poista-toteuma-materiaali-test
  (is true))
