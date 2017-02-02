(ns harja.ui.kartta.infopaneelin-sisalto-test
  (:require [cljs.test :as test :refer-macros [deftest is testing]]
            [harja.ui.kartta.infopaneelin-sisalto :as paneeli]))

(defn hakufunktion-validaattori-toimii? [data polku]
  (try
    ((:validointi-fn (paneeli/hakufunktio polku (constantly nil))) data)
    (catch js/Error e
      (do (println (pr-str e))
          false))))

(deftest hakufunktion-validaattori
  (let [data {:id 1 :foo false :bar nil
              :nested {:n-id 1 :n-foo false :n-bar nil}}]

    (testing "Sisaltaa? toimii pelkille keywordeille"
      (is (hakufunktion-validaattori-toimii? data :id))
      (is (hakufunktion-validaattori-toimii? data :foo))
      (is (hakufunktion-validaattori-toimii? data :bar))
      (is (not (hakufunktion-validaattori-toimii? data :baz))))

    (testing "Ensimmäisen tason kentät voi hakea myös vektorilla"
      (is (hakufunktion-validaattori-toimii? data #{:id}))
      (is (hakufunktion-validaattori-toimii? data #{:foo}))
      (is (hakufunktion-validaattori-toimii? data #{:bar}))
      (is (hakufunktion-validaattori-toimii? data #{:nested}))
      (is (not (hakufunktion-validaattori-toimii? data #{:baz}))))

    (testing "Monta avainta vektorissa hakee ensimmäisen tason keywordit"
      (is (hakufunktion-validaattori-toimii? data #{:id :foo :bar}))
      (is (not (hakufunktion-validaattori-toimii? data #{:nested :n-id}))))

    (testing "Vektori vektorissa hakee nested avaimia"
      (is (hakufunktion-validaattori-toimii? data #{[:nested :n-id]}))
      (is (hakufunktion-validaattori-toimii? data #{[:nested :n-foo]}))
      (is (hakufunktion-validaattori-toimii? data #{[:nested :n-bar]}))
      (is (not (hakufunktion-validaattori-toimii? data #{[:nested :n-baz]}))))

    (testing "Monta vektoria vektorissa toimii myös"
      (is (hakufunktion-validaattori-toimii? data #{[:nested :n-id] [:nested :n-foo] [:nested :n-bar]}))
      (is (hakufunktion-validaattori-toimii? data #{[:nested :n-id] :id})))))

(deftest haun-validointi-tippuu-pois
  (let [skeema {:otsikko "Foobar" :hae {:validointi-fn :bar :haku-fn (constantly true)}}]
    (is ((:hae (paneeli/rivin-skeema-ilman-haun-validointia skeema))))))

(deftest rivin-skeeman-validointi
  (let [data {:data {:id 1 :foo false :bar nil}}
        otsikolla #(assoc % :otsikko :foo)
        validointi-onnistuu? #(some? (paneeli/validoi-rivin-skeema data %))]

    (testing "Rivillä pitää olla otsikko"
      (is (validointi-onnistuu? {:otsikko :foo :nimi :id}))
      (is (not (validointi-onnistuu? {:otsikko nil :nimi :id}))))

    (testing "Hakutapa pitää olla"
      (is (validointi-onnistuu? (otsikolla {:nimi :foo :hae nil})))
      (is (validointi-onnistuu? (otsikolla {:nimi :foo})))
      (is (validointi-onnistuu? (otsikolla {:nimi nil :hae {:haku-fn :foo :validointi-fn #(contains? % :foo)}})))
      (is (validointi-onnistuu? (otsikolla {:hae {:haku-fn :foo :validointi-fn #(contains? % :foo)}})))

      (is (not (validointi-onnistuu? (otsikolla {:nimi nil :hae nil}))))
      ;; :hae funktion pitää tässä vaiheessa sisältää :validointi-fn ja :hae-fn
      (is (not (validointi-onnistuu? (otsikolla {:nimi nil :hae (constantly true)}))))
      (is (not (validointi-onnistuu? (otsikolla {})))))

    (testing "Jos hakutapa on :nimi, avaimen pitää löytyä"
      (is (validointi-onnistuu? (otsikolla {:nimi :id})))
      (is (validointi-onnistuu? (otsikolla {:nimi :foo})))
      (is (validointi-onnistuu? (otsikolla {:nimi :bar})))
      (is (not (validointi-onnistuu? (otsikolla {:nimi :baz})))))

    (testing "Jos hakutapa on :hae, pitää sillä olla validointi"
      (is (validointi-onnistuu? (otsikolla {:hae {:validointi-fn #(contains? % :foo) :haku-fn :foo}})))
      (is (not (validointi-onnistuu? (otsikolla {:hae {:haku-fn :foo}})))))

    (testing ":hae validointi-funktion pitää onnistua"
      (is (validointi-onnistuu? (otsikolla {:hae {:validointi-fn (constantly true) :haku-fn :foo}})))
      (is (validointi-onnistuu? (otsikolla {:hae {:validointi-fn #(contains? % :id) :haku-fn :foo}})))
      (is (not (validointi-onnistuu? (otsikolla {:hae {:validointi-fn (constantly false) :haku-fn :foo}}))))
      (is (not (validointi-onnistuu? (otsikolla {:hae {:validointi-fn #(contains? % :baz) :haku-fn :foo}})))))))

(deftest validoi-infopaneeli-skeema
  (let [data {:otsikko :foo
              :tiedot [{:otsikko :bar :nimi :bar}]
              :jarjesta-fn :aika
              :data {:bar "bar" :aika (harja.pvm/nyt)}}
        validointi-onnistuu? #(some? (paneeli/validoi-infopaneeli-skeema %))]

    (testing "Otsikko pitää olla"
      (is (validointi-onnistuu? data))
      (is (not (validointi-onnistuu? (dissoc data :otsikko)))))

    (testing "Jarjesta-fn pitää olla"
      (is (not (validointi-onnistuu? (dissoc data :jarjesta-fn))))
      (is (not (validointi-onnistuu? (assoc data :jarjesta-fn nil))))
      (is (not (validointi-onnistuu? (assoc data :jarjesta-fn (constantly nil)))))
      (is (validointi-onnistuu? (assoc data :jarjesta-fn (constantly false))))
      (is (validointi-onnistuu? (assoc data :jarjesta-fn :aika))))))

(deftest jarjestaminen-toimii
  (testing "Järjestä pieni joukko asioita"
    (let [yksi {:jarjesta-fn (constantly (harja.pvm/nyt)) :id 1}
         kaksi {:jarjesta-fn (constantly (harja.pvm/luo-pvm 2015 10 10)) :id 2}
         nelja {:jarjesta-fn (constantly false) :id 4}
         kolme {:jarjesta-fn (constantly false) :id 3}
         sorttaamaton [nelja yksi kaksi kolme]
         sortattu1 [yksi kaksi kolme nelja]
         sortattu2 [yksi kaksi nelja kolme]
         tulos (sort-by #((:jarjesta-fn %)) paneeli/jarjesta sorttaamaton)]
     (is (or (= tulos sortattu1) (= tulos sortattu2)))
     (is (not (= tulos sorttaamaton)))))

  (testing "Järjestä isompi joukko"
    (let [paivamaarat (map (fn [vuosi] {:jarjesta-fn (harja.pvm/luo-pvm vuosi 10 10)
                                        :vuosi vuosi}) (range 100 3000))
          alkutilanne (shuffle paivamaarat)
          tulos (sort-by :jarjesta-fn paneeli/jarjesta alkutilanne)]
      (is (loop [edellinen (first tulos)
                 nykyinen (second tulos)
                 loput (drop 2 tulos)]
            (if-not (>= (:vuosi edellinen) (:vuosi nykyinen))
              false

              (if-not (empty? (rest loput))
                (recur nykyinen (first loput) (rest loput))
                true)))))))
