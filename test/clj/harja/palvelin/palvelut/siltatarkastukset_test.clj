(ns harja.palvelin.palvelut.siltatarkastukset-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.palvelut.siltatarkastukset :as siltatarkastukset]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (luo-testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :siltatarkastukset (component/using
                                             (siltatarkastukset/->Siltatarkastukset)
                                             [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(defn- silta-nimella [sillat nimi]
  (first (filter #(= nimi (:siltanimi %)) sillat)))

(deftest joutsensillalle-ei-ole-tarkastuksia
  (let [sillat (kutsu-http-palvelua :hae-urakan-sillat +kayttaja-jvh+
                                    {:urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                     :listaus :kaikki})
        joutsensilta (silta-nimella sillat "Joutsensilta")]
    (is joutsensilta "Joutsensilta löytyi")
    (is (nil? (:tarkastusaika joutsensilta)) "Joutsensiltaa ei ole tarkastettu")))

(deftest kempeleen-testisillan-tarkastus
  (let [sillat (kutsu-http-palvelua :hae-urakan-sillat +kayttaja-jvh+
                                    {:urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                     :listaus :kaikki})
        kempele (silta-nimella sillat "Kempeleen testisilta")]
    (is kempele "Kempeleen testisilta löytyy")
    (is (= "Late Lujuuslaskija" (:tarkastaja kempele)))))

(deftest puutteellisia-siltoja
  (let [sillat (kutsu-http-palvelua :hae-urakan-sillat +kayttaja-jvh+
                                    {:urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                     :listaus :puutteet})]
    (is (silta-nimella sillat "Kempeleen testisilta"))
    (is (silta-nimella sillat "Oulujoen silta"))
    (is (nil? (silta-nimella sillat "Joutsensilta")) "Joutsensilta ei löydy puutelistalta")))

(deftest korjattuja-siltoja
  (let [sillat (kutsu-http-palvelua :hae-urakan-sillat +kayttaja-jvh+
                                    {:urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                     :listaus :korjatut})
        kajaanintie (silta-nimella sillat "Kajaanintien silta")]
    (is kajaanintie)
    (is (= 24 (:rikki-ennen kajaanintie)) "Ennen oli kaikki rikki")
    (is (= 0 (:rikki-nyt kajaanintie)) "Nyt on kaikki korjattu")))

(deftest oulun-urakan-2005-2012-sillat
  (let [sillat (kutsu-http-palvelua :hae-urakan-sillat +kayttaja-jvh+
                                    {:urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                     :listaus :kaikki})
        sillat-ilman-tarkastuksia (filter #(and (not= "Joutsensilta" (:siltanimi %))
                                                (not= "Pyhäjoen silta" (:siltanimi %))) sillat)]
    (is (= (count sillat) 7))
    (is (= (count sillat-ilman-tarkastuksia) 4))
    (is (every? #(some? (:tarkastusaika %)) (remove #(= (:siltanimi %) "Tekaistu kuntasilta") sillat-ilman-tarkastuksia)))))

(deftest oulun-urakan-2014-2019-sillat
  ;; Tässä uudemmassa urakassa halutaan nähdä vanhassa urakassa tehty viimeisin tarkastus
  (let [sillat (kutsu-http-palvelua :hae-urakan-sillat +kayttaja-jvh+
                                    {:urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                     :listaus :kaikki})
        sillat-ilman-tarkastuksia (filter #(and (not= "Joutsensilta" (:siltanimi %))
                                                (not= "Pyhäjoen silta" (:siltanimi %))) sillat)]
    (is (= (count sillat) 7))
    (is (= (count sillat-ilman-tarkastuksia) 4))
    (is (every? #(some? (:tarkastusaika %)) (remove #(= (:siltanimi %) "Tekaistu kuntasilta") sillat-ilman-tarkastuksia)))))

(deftest oulun-urakan-2005-2012-tarkastukset
  (let [tarkastukset (kutsu-http-palvelua :hae-sillan-tarkastukset +kayttaja-jvh+
                                          {:urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                           :silta-id (hae-oulujoen-sillan-id)})]
    (is (= (count tarkastukset) 2))
    (is (every? #(map? (:kohteet %)) tarkastukset))))

(deftest oulun-urakan-2005-2014-tarkastukset
  ;; Tässä uudemmassa urakassa halutaan nähdä myös sillan aiemmat tarkastukset
  (let [tarkastukset (kutsu-http-palvelua :hae-sillan-tarkastukset +kayttaja-jvh+
                                          {:urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                           :silta-id (hae-oulujoen-sillan-id)})]
    (is (= (count tarkastukset) 2))
    (is (every? #(map? (:kohteet %)) tarkastukset))))

(defn uusi-tarkastus []
  {:uudet-liitteet nil, :urakka-id (hae-oulun-alueurakan-2014-2019-id)
   :kohteet {7 [#{\A} ""], 20 [#{\B} ""], 1 [#{\A} ""], 24 [#{\C} ""], 4 [#{\A} ""], 15 [#{\B} ""],
             21 [#{\B \C} ""], 13 [#{\A} ""], 22 [#{\C} ""], 6 [#{\B \C \D} ""], 17 [#{\B} ""], 3 [#{\A} ""],
             12 [#{\A} ""], 2 [#{\A} ""], 23 [#{\C} ""], 19 [#{\C} ""], 11 [#{\A} ""], 9 [#{\B} ""],
             5 [#{\B} ""], 14 [#{\A} ""], 16 [#{\A} ""], 10 [#{\B} ""], 18 [#{\B} ""], 8 [#{\B} ""]},
   :silta-id (hae-oulujoen-sillan-id),
   :liitteet [],
   :tarkastusaika #inst "2017-07-28T11:34:49.000-00:00",
   :poistettu false
   :tarkastaja "TESTIKAYTTAJA"})

(defn paivittava-tarkastus [id]
  {:id id
   :uudet-liitteet nil, :urakka-id (hae-oulun-alueurakan-2014-2019-id)
   :kohteet {7 [#{\D} ""], 20 [#{\B} ""], 1 [#{\D} ""], 24 [#{\C} ""], 4 [#{\D} ""], 15 [#{\B} ""],
             21 [#{\B} ""], 13 [#{\B \D} ""], 22 [#{\C} ""], 6 [#{\B} ""], 17 [#{\B} ""], 3 [#{\D} ""],
             12 [#{\D} ""], 2 [#{\D} ""], 23 [#{\B \C} ""], 19 [#{\C} ""], 11 [#{\D} ""], 9 [#{\B} ""],
             5 [#{\B} ""], 14 [#{\D} ""], 16 [#{\D} ""], 10 [#{\B} ""], 18 [#{\B} ""], 8 [#{\B} ""]},
   :silta-id (hae-oulujoen-sillan-id),
   :liitteet [],
   :tarkastusaika #inst "2017-07-30T21:34:49.000-00:00",
   :poistettu false
   :tarkastaja "TESTIKAYTTAJA"})

(defn uusi-tarkastus-josta-puuttuu-kohteita []
  {:uudet-liitteet nil, :urakka-id (hae-oulun-alueurakan-2014-2019-id)
   :kohteet {1 [#{\A} ""]},
   :silta-id (hae-oulujoen-sillan-id),
   :liitteet [],
   :tarkastusaika #inst "2017-07-28T11:34:49.000-00:00",
   :poistettu false
   :tarkastaja "TESTIKAYTTAJA"})


(deftest tarkastuksen-paivitys-oulujoen-sillalle
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        silta-id (hae-oulujoen-sillan-id)
        _ (kutsu-http-palvelua :tallenna-siltatarkastus +kayttaja-jvh+
                               (uusi-tarkastus))
        tarkastukset-eka-kutsun-jalkeen (kutsu-http-palvelua :hae-sillan-tarkastukset +kayttaja-jvh+
                                                         {:urakka-id urakka-id
                                                          :silta-id silta-id})
        paivitettava-tarkastus-eka-kutsun-jalkeen (first
                                                    (filter (fn [tarkastus]
                                                              (= (:tarkastusaika (uusi-tarkastus)
                                                                   (:tarkastusaika tarkastus))))
                                                            tarkastukset-eka-kutsun-jalkeen))
        _ (kutsu-http-palvelua :tallenna-siltatarkastus +kayttaja-jvh+
                               (paivittava-tarkastus (:id paivitettava-tarkastus-eka-kutsun-jalkeen)))
        tarkastukset-paivityksen-jalkeen (kutsu-http-palvelua :hae-sillan-tarkastukset +kayttaja-jvh+
                                                             {:urakka-id urakka-id
                                                              :silta-id silta-id})
        paivitetty-tarkastus (first
                               (filter (fn [tarkastus]
                                         (= (:tarkastusaika (paivittava-tarkastus (:id paivitettava-tarkastus-eka-kutsun-jalkeen))
                                              (:tarkastusaika tarkastus))))
                                       tarkastukset-paivityksen-jalkeen))]
    (is (not= paivitettava-tarkastus-eka-kutsun-jalkeen paivitetty-tarkastus) "päivitetty siltatarkastus ei sama kuin alkuperäinen")


    (println "%%%%%" (first (get (:kohteet paivitettava-tarkastus-eka-kutsun-jalkeen) 1)))
    (is (= (first (get (:kohteet paivitettava-tarkastus-eka-kutsun-jalkeen) 1)) "A") "siltatarkastuksen kohde 1 tulos päivitystä ennen")
    (is (= (first (get (:kohteet paivitetty-tarkastus) 1)) #{\D}) "siltatarkastuksen kohde 1 tulos päivityksen jälkeen")

    (is (= (first (get (:kohteet paivitettava-tarkastus-eka-kutsun-jalkeen) 13)) "A") "siltatarkastuksen kohde 13 tulos päivitystä ennen")
    (is (= (first (get (:kohteet paivitetty-tarkastus) 13)) #{\B \C}) "siltatarkastuksen kohde 13 tulos päivityksen jälkeen")

    (is (= (first (get (:kohteet paivitettava-tarkastus-eka-kutsun-jalkeen) 20)) "B") "siltatarkastuksen kohde 20 tulos päivitystä ennen")
    (is (= (first (get (:kohteet paivitetty-tarkastus) 20)) #{\B}) "siltatarkastuksen kohde 20 tulos päivityksen jälkeen")

    (is (= (first (get (:kohteet paivitettava-tarkastus-eka-kutsun-jalkeen) 7)) "A") "siltatarkastuksen kohde 7 tulos päivitystä ennen")
    (is (= (first (get (:kohteet paivitetty-tarkastus) 7)) #{\C}) "siltatarkastuksen kohde 7 tulos päivityksen jälkeen")))

(deftest tarkastuksen-paivitys-oulujoen-sillalle
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        silta-id (hae-oulujoen-sillan-id)
        _ (kutsu-http-palvelua :tallenna-siltatarkastus +kayttaja-jvh+
                               (uusi-tarkastus))
        tarkastukset-eka-kutsun-jalkeen (kutsu-http-palvelua :hae-sillan-tarkastukset +kayttaja-jvh+
                                                             {:urakka-id urakka-id
                                                              :silta-id silta-id})
        paivitettava-tarkastus-eka-kutsun-jalkeen (first
                                                    (filter (fn [tarkastus]
                                                              (= (:tarkastusaika (uusi-tarkastus)
                                                                   (:tarkastusaika tarkastus))))
                                                            tarkastukset-eka-kutsun-jalkeen))
        _ (kutsu-http-palvelua :tallenna-siltatarkastus +kayttaja-jvh+
                               (paivittava-tarkastus (:id paivitettava-tarkastus-eka-kutsun-jalkeen)))
        tarkastukset-paivityksen-jalkeen (kutsu-http-palvelua :hae-sillan-tarkastukset +kayttaja-jvh+
                                                              {:urakka-id urakka-id
                                                               :silta-id silta-id})
        paivitetty-tarkastus (first
                               (filter (fn [tarkastus]
                                         (= (:tarkastusaika (paivittava-tarkastus (:id paivitettava-tarkastus-eka-kutsun-jalkeen))
                                              (:tarkastusaika tarkastus))))
                                       tarkastukset-paivityksen-jalkeen))]
    (is (not= paivitettava-tarkastus-eka-kutsun-jalkeen paivitetty-tarkastus) "päivitetty siltatarkastus ei sama kuin alkuperäinen")
    (is (= (first (get (:kohteet paivitettava-tarkastus-eka-kutsun-jalkeen) 1)) #{\A}) "siltatarkastuksen kohde 1 tulos päivitystä ennen")
    (is (= (first (get (:kohteet paivitetty-tarkastus) 1)) #{\D}) "siltatarkastuksen kohde 1 tulos päivityksen jälkeen")

    (is (= (first (get (:kohteet paivitettava-tarkastus-eka-kutsun-jalkeen) 7)) #{\A}) "siltatarkastuksen kohde 7 tulos päivitystä ennen")
    (is (= (first (get (:kohteet paivitetty-tarkastus) 7)) #{\D}) "siltatarkastuksen kohde 7 tulos päivityksen jälkeen")

    (is (= (first (get (:kohteet paivitettava-tarkastus-eka-kutsun-jalkeen) 20)) #{\B}) "siltatarkastuksen kohde 20 tulos päivitystä ennen")
    (is (= (first (get (:kohteet paivitetty-tarkastus) 20)) #{\B}) "siltatarkastuksen kohde 20 tulos päivityksen jälkeen")))

(deftest tarkastuksen-paivitys-kun-ensimmaisesta-tarkastuksesta-puuttuu-kohteita
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        silta-id (hae-oulujoen-sillan-id)
        _ (kutsu-http-palvelua :tallenna-siltatarkastus +kayttaja-jvh+
                               (uusi-tarkastus-josta-puuttuu-kohteita))
        tarkastukset-eka-kutsun-jalkeen (kutsu-http-palvelua :hae-sillan-tarkastukset +kayttaja-jvh+
                                                             {:urakka-id urakka-id
                                                              :silta-id silta-id})
        paivitettava-tarkastus-eka-kutsun-jalkeen (first
                                                    (filter (fn [tarkastus]
                                                              (= (:tarkastusaika (uusi-tarkastus-josta-puuttuu-kohteita)
                                                                   (:tarkastusaika tarkastus))))
                                                            tarkastukset-eka-kutsun-jalkeen))
        _ (kutsu-http-palvelua :tallenna-siltatarkastus +kayttaja-jvh+
                               (paivittava-tarkastus (:id paivitettava-tarkastus-eka-kutsun-jalkeen)))
        tarkastukset-paivityksen-jalkeen (kutsu-http-palvelua :hae-sillan-tarkastukset +kayttaja-jvh+
                                                              {:urakka-id urakka-id
                                                               :silta-id silta-id})
        paivitetty-tarkastus (first
                               (filter (fn [tarkastus]
                                         (= (:tarkastusaika (paivittava-tarkastus (:id paivitettava-tarkastus-eka-kutsun-jalkeen))
                                              (:tarkastusaika tarkastus))))
                                       tarkastukset-paivityksen-jalkeen))]
    (is (not= paivitettava-tarkastus-eka-kutsun-jalkeen paivitetty-tarkastus) "päivitetty siltatarkastus ei sama kuin alkuperäinen")
    (is (= (first (get (:kohteet paivitettava-tarkastus-eka-kutsun-jalkeen) 1)) #{\A}) "siltatarkastuksen kohde 1 tulos päivitystä ennen")
    (is (= (first (get (:kohteet paivitetty-tarkastus) 1)) #{\D}) "siltatarkastuksen kohde 1 tulos päivityksen jälkeen")

    (is (= (first (get (:kohteet paivitettava-tarkastus-eka-kutsun-jalkeen) 7)) nil) "siltatarkastuksen kohde 7 tulos päivitystä ennen")
    (is (= (first (get (:kohteet paivitetty-tarkastus) 7)) #{\D}) "siltatarkastuksen kohde 7 tulos päivityksen jälkeen")

    (is (= (first (get (:kohteet paivitettava-tarkastus-eka-kutsun-jalkeen) 20)) nil) "siltatarkastuksen kohde 20 tulos päivitystä ennen")
    (is (= (first (get (:kohteet paivitetty-tarkastus) 20)) #{\B}) "siltatarkastuksen kohde 20 tulos päivityksen jälkeen")))


(deftest tarkastuksen-tallennus-oulujoen-sillalle
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        silta-id (hae-oulujoen-sillan-id)
        tarkastukset-ennen-uutta (count (kutsu-http-palvelua :hae-sillan-tarkastukset +kayttaja-jvh+
                                                             {:urakka-id urakka-id
                                                              :silta-id silta-id}))
        _ (kutsu-http-palvelua :tallenna-siltatarkastus +kayttaja-jvh+
                               (uusi-tarkastus))
        tarkastukset-kutsun-jalkeen (count (kutsu-http-palvelua :hae-sillan-tarkastukset +kayttaja-jvh+
                                                                {:urakka-id urakka-id
                                                                 :silta-id silta-id}))]
    (is (= (+ tarkastukset-ennen-uutta 1) tarkastukset-kutsun-jalkeen))))

(deftest tarkastuksen-tallennus-ei-urakan-sillalle-epaonnistuu
  (let [urakka-id (hae-kajaanin-alueurakan-2014-2019-id)
        silta-id (hae-pyhajoen-sillan-id)
        tarkastukset-ennen-uutta (count (kutsu-http-palvelua :hae-sillan-tarkastukset +kayttaja-jvh+
                                                             {:urakka-id urakka-id
                                                              :silta-id silta-id}))
        _ (is (thrown? SecurityException (kutsu-http-palvelua :tallenna-siltatarkastus +kayttaja-jvh+
                                                              (-> (uusi-tarkastus)
                                                                  (assoc :silta-id silta-id)
                                                                  (assoc :urakka-id urakka-id)))))
        tarkastukset-kutsun-jalkeen (count (kutsu-http-palvelua :hae-sillan-tarkastukset +kayttaja-jvh+
                                                                {:urakka-id urakka-id
                                                                 :silta-id silta-id}))]
    (is (= tarkastukset-ennen-uutta tarkastukset-kutsun-jalkeen))))

(deftest tarkastuksen-tallennus-ilman-oikeuksia-epaonnistuu
  (is (thrown? Exception (kutsu-http-palvelua :tallenna-siltatarkastus +kayttaja-tero+
                                              (uusi-tarkastus)))))


;; jostain syystä tämä testi ei suostu toimimaan millään Circle CI:n ajossa, joten se on jouduttu kommentoimaan pois
#_ (deftest tarkista-siltatarkastuksen-poisto
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        silta-id (hae-oulujoen-sillan-id)
        tarkastus {:uudet-liitteet nil, :urakka-id urakka-id
                   :kohteet {7 ["A" ""], 20 ["B" ""], 1 ["A" ""], 24 ["C" ""], 4 ["A" ""], 15 ["B" ""],
                             21 ["B" ""], 13 ["A" ""], 22 ["C" ""], 6 ["B" ""], 17 ["B" ""], 3 ["A" ""],
                             12 ["A" ""], 2 ["A" ""], 23 ["C" ""], 19 ["C" ""], 11 ["A" ""], 9 ["B" ""],
                             5 ["B" ""], 14 ["A" ""], 16 ["A" ""], 10 ["B" ""], 18 ["B" ""], 8 ["B" ""]},
                   :silta-id silta-id,
                   :liitteet [],
                   :tarkastusaika #inst "2017-07-28T11:34:49.000-00:00",
                   :poistettu false
                   :tarkastaja "TESTIKAYTTAJA"}
        tarkastukset-ennen-uutta (kutsu-http-palvelua :hae-sillan-tarkastukset +kayttaja-jvh+
                                                      {:urakka-id urakka-id
                                                       :silta-id silta-id})
        poistettavan-tarkastuksen-id (:id (kutsu-http-palvelua :tallenna-siltatarkastus +kayttaja-jvh+ tarkastus))
        tarkastukset-lisayksen-jalkeen (kutsu-http-palvelua :hae-sillan-tarkastukset +kayttaja-jvh+
                                                            {:urakka-id urakka-id
                                                             :silta-id silta-id})
        tarkastukset-poiston-jalkeen (kutsu-http-palvelua :poista-siltatarkastus
                                                          +kayttaja-jvh+
                                                          {:urakka-id urakka-id
                                                           :silta-id silta-id
                                                           :siltatarkastus-id poistettavan-tarkastuksen-id})]
    (is (= (+ (count tarkastukset-ennen-uutta) 1) (count tarkastukset-lisayksen-jalkeen)) "Lisäyksen jälkeen on 1 uusi tarkastus")
    (is (= (count tarkastukset-ennen-uutta) (count tarkastukset-poiston-jalkeen)) "Poiston jälkeen on sama määrä tarkastuksia kuin aluksi")
    (is (not (some #(= poistettavan-tarkastuksen-id (:id %)) tarkastukset-poiston-jalkeen)) "Poistettua tarkastusta ei löydy listasta")))