(ns harja.palvelin.palvelut.suunnittelu.suolarajoitus-palvelu-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer :all]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.suunnittelu.suolarajoitus-palvelu :as suolarajoitus-palvelu]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :suolarajoitukset (component/using
                              (suolarajoitus-palvelu/->Suolarajoitus)
                              [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each
  jarjestelma-fixture
  tietokanta-fixture)

(defn- suolarajoitus-pohja
  "Olettaa saavansa tierekisteriosoitteen muodossa: {:tie 86, :aosa 1, :aet 0, :losa 2, :let 10}"
  [urakka_id kayttaja_id tr_osoite hoitokauden_alkuvuosi]
  {:tie (:tie tr_osoite)
   :aosa (:aosa tr_osoite)
   :aet (:aet tr_osoite)
   :losa (:losa tr_osoite)
   :let (:let tr_osoite)
   :pituus 1
   :ajoratojen_pituus 1
   :suolarajoitus 1234
   :formiaatti false
   :hoitokauden_alkuvuosi hoitokauden_alkuvuosi
   :kopioidaan-tuleville-vuosille? false
   :urakka_id urakka_id
   :kayttaja_id kayttaja_id})

(defn- hae-suolarajoitukset [parametrit]
  (kutsu-palvelua (:http-palvelin jarjestelma) :hae-suolarajoitukset +kayttaja-jvh+ parametrit))

(defn- poista-suolarajoitus [parametrit]
  (kutsu-palvelua (:http-palvelin jarjestelma) :poista-suolarajoitus +kayttaja-jvh+ parametrit))

(defn hae-rajoitusalueet-urakalle [urakka-id]
  (q-map (str "SELECT id as rajoitusalue_id, urakka_id FROM rajoitusalue
  WHERE poistettu = FALSE AND urakka_id = " urakka-id)))

(defn hae-rajoitusalue-rajoitukset-urakalle [urakka-id]
  (q-map (str "SELECT ra.id as rajoitusalue_id, rr.suolarajoitus, rr.hoitokauden_alkuvuosi, ra.urakka_id
  FROM rajoitusalue ra, rajoitusalue_rajoitus rr
  WHERE rr.rajoitusalue_id = ra.id
    AND ra.poistettu = FALSE
    AND rr.poistettu = FALSE
    AND ra.urakka_id = " urakka-id)))

(deftest hae-suolarajoitukset-hoitovuoden-perusteella-onnistuu-test
  (let [urakka_id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        hk_alkuvuosi 2022
        suolarajoitukset (hae-suolarajoitukset {:hoitokauden_alkuvuosi hk_alkuvuosi :urakka_id urakka_id})]

    (is (> (count suolarajoitukset) 0) "Suolarajoitukset löytyy")))

(deftest tallenna-suolarajoitus-onnistuu-test
  (let [hk-alkuvuosi 2022
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        rajoitusalueet-kannasta (hae-rajoitusalueet-urakalle urakka-id)
        rajoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                   :tallenna-suolarajoitus
                   +kayttaja-jvh+
                   (suolarajoitus-pohja
                     urakka-id
                     (:id +kayttaja-jvh+)
                     {:tie 22, :aosa 1, :aet 0, :losa 2, :let 10}
                     hk-alkuvuosi))
        ;; Siivotaan kanta
        _ (poista-suolarajoitus
            {:rajoitusalue_id (:rajoitusalue_id rajoitus)
             :hoitokauden_alkuvuosi hk-alkuvuosi
             :urakka_id urakka-id
             :kopioidaan-tuleville-vuosille? true})]
    (is (> (count rajoitus) 0) "Uusi rajoitus on tallennettu")))

(deftest tallenna-suolarajoitus-pohjavesialueelle-onnistuu-test
  (let [hk-alkuvuosi 2022
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        rajoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                   :tallenna-suolarajoitus
                   +kayttaja-jvh+
                   (suolarajoitus-pohja
                     urakka-id
                     (:id +kayttaja-jvh+)
                     {:tie 4 :aosa 364 :aet 3268 :losa 364 :let 3451}
                     hk-alkuvuosi))
        ;; Siivotaan kanta
        _ (poista-suolarajoitus
            {:rajoitusalue_id (:rajoitusalue_id rajoitus)
             :hoitokauden_alkuvuosi hk-alkuvuosi
             :urakka_id urakka-id
             :kopioidaan-tuleville-vuosille? true})]
    (is (> (count rajoitus) 0) "Uusi rajoitus on tallennettu")
    (is (not (empty? (:pohjavesialueet rajoitus))) "Uusi rajoitus on tallennettu pohjavesialueelle")
    ))

(deftest paivita-suolarajoitus-onnistuu-test
  (let [hk-alkuvuosi 2022
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        suolarajoitus (suolarajoitus-pohja urakka-id (:id +kayttaja-jvh+)
                        {:tie 4, :aosa 11, :aet 0, :losa 12, :let 100}
                        hk-alkuvuosi)
        ;; Luodaan uusi rajoitusalue, jota muokataan
        suolarajoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tallenna-suolarajoitus
                        +kayttaja-jvh+
                        suolarajoitus)
        rajoitukset (hae-suolarajoitukset {:urakka_id urakka-id :hoitokauden_alkuvuosi hk-alkuvuosi})

        ;; Kovakoodatusti juuri luotu alue
        muokattava-rajoitus (-> (first rajoitukset)
                              (assoc :pituus 999)
                              (assoc :ajoratojen_pituus 1234)
                              )
        paivitetty-rajoitus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-suolarajoitus +kayttaja-jvh+
                              muokattava-rajoitus)
        ;; Siivotaan kanta
        _ (poista-suolarajoitus
            {:rajoitusalue_id (:rajoitusalue_id suolarajoitus)
             :hoitokauden_alkuvuosi hk-alkuvuosi
             :urakka_id urakka-id
             :kopioidaan-tuleville-vuosille? true})]

    (is (not= paivitetty-rajoitus muokattava-rajoitus) "Päivitys onnistui")
    (is (= 999 (:pituus paivitetty-rajoitus)) "Pituuden päivitys onnistui")
    (is (= 1234 (:ajoratojen_pituus paivitetty-rajoitus)) "Ajoratojen pituuden päivitys onnistui")))

(deftest poista-suolarajoitus-tulevilta-vuosilta-onnistuu-test
  (let [hk-alkuvuosi 2022
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        suolarajoitusalueet-alkuun (q-map (str "SELECT id, urakka_id FROM rajoitusalue WHERE poistettu = FALSE"))
        suolarajoitukset-alkuun (q-map (str "SELECT id, rajoitusalue_id, suolarajoitus FROM rajoitusalue_rajoitus WHERE poistettu = FALSE"))
        ;; Luodaan uusi rajoitusalue, joka poistetaan
        suolarajoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tallenna-suolarajoitus
                        +kayttaja-jvh+
                        (assoc (suolarajoitus-pohja
                                 urakka-id
                                 (:id +kayttaja-jvh+)
                                 {:tie 4, :aosa 11, :aet 0, :losa 12, :let 100}
                                 hk-alkuvuosi)
                          :kopioidaan-tuleville-vuosille? true))

        ;; Poista luotu rajoitus
        _ (poista-suolarajoitus
            {:rajoitusalue_id (:rajoitusalue_id suolarajoitus)
             :hoitokauden_alkuvuosi hk-alkuvuosi
             :urakka_id urakka-id
             :kopioidaan-tuleville-vuosille? true})

        suolarajoitusalueet-lopuksi (q-map (str "SELECT id, urakka_id FROM rajoitusalue WHERE poistettu = FALSE"))
        suolarajoitukset-lopuksi (q-map (str "SELECT id, rajoitusalue_id, suolarajoitus FROM rajoitusalue_rajoitus WHERE poistettu = FALSE"))]

    (is (= suolarajoitusalueet-lopuksi suolarajoitusalueet-alkuun) "Poistaminen onnistui")
    (is (= suolarajoitukset-lopuksi suolarajoitukset-alkuun) "Poistaminen onnistui")))

(deftest poista-suolarajoitus-vain-talta-vuodelta-onnistuu-test
  (let [hk-alkuvuosi 2022
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        suolarajoitusalueet-alkuun (hae-rajoitusalueet-urakalle urakka-id)
        suolarajoitukset-alkuun (hae-rajoitusalue-rajoitukset-urakalle urakka-id)
        ;; Luodaan uusi rajoitusalue, joka poistetaan
        suolarajoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tallenna-suolarajoitus
                        +kayttaja-jvh+
                        (assoc (suolarajoitus-pohja
                                 urakka-id
                                 (:id +kayttaja-jvh+)
                                 {:tie 4, :aosa 11, :aet 0, :losa 12, :let 100}
                                 hk-alkuvuosi)
                          :kopioidaan-tuleville-vuosille? true))

        ;; Poista luotu rajoitus
        vastaus (poista-suolarajoitus
                  {:rajoitusalue_id (:rajoitusalue_id suolarajoitus)
                   :hoitokauden_alkuvuosi hk-alkuvuosi
                   :urakka_id urakka-id
                   :kopioidaan-tuleville-vuosille? false})

        suolarajoitusalueet-lopuksi (hae-rajoitusalueet-urakalle urakka-id)
        suolarajoitukset-lopuksi (hae-rajoitusalue-rajoitukset-urakalle urakka-id)

        ;; Siivoa kanta
        vastaus (poista-suolarajoitus
                  {:rajoitusalue_id (:rajoitusalue_id suolarajoitus)
                   :hoitokauden_alkuvuosi hk-alkuvuosi
                   :urakka_id urakka-id
                   :kopioidaan-tuleville-vuosille? true})]

    (is (not= suolarajoitusalueet-lopuksi suolarajoitusalueet-alkuun) "Vain yhden poistaminen onnistui")
    (is (not= suolarajoitukset-lopuksi suolarajoitukset-alkuun) "Vain yhden poistaminen onnistui")
    (is (= (+ 3 (count suolarajoitukset-alkuun)) (count suolarajoitukset-lopuksi)) "Vain yhden poistaminen onnistui")))

;; TODO: Tarkista poistossa, että se ei poista menneitä rajoituksia


(deftest laske-tierekisteriosoitteelle-pituus-onnistuu-test
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        tierekisteriosoite {:tie 20 :aosa 4 :aet 0 :losa 4 :let 50}
        suolarajoitus (assoc tierekisteriosoite :urakka_id urakka-id)
        pituudet (kutsu-palvelua (:http-palvelin jarjestelma)
                   :tierekisterin-tiedot
                   +kayttaja-jvh+ suolarajoitus)]
    (is (= 50 (:pituus pituudet)))
    ;; 20 tiellä osalla 4 on 3 ajorataa, joten pituuden pitäisi olla kolminkertainen
    (is (= 150 (:ajoratojen_pituus pituudet)))))

(deftest laske-tierekisteriosoitteelle-pituus-epaonnistuu-test
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        tierekisteriosoite {:tie 20 :aosa "makkara" :aet "lenkki" :losa "pihvi" :let "hiiligrilli"}
        suolarajoitus (assoc tierekisteriosoite :urakka_id urakka-id)
        pituudet (future (kutsu-palvelua (:http-palvelin jarjestelma)
                           :tierekisterin-tiedot
                           +kayttaja-jvh+ suolarajoitus))]
    (is (= "Tierekisteriosoitteessa virhe." (get-in @pituudet [:vastaus :virhe])) "Väärillä tiedoilla ei voi laskea pituutta.")))


(deftest laske-tierekisteriosoitteelle-pituus-vaarilla-tiedoilla-onnistuu-test
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; 20 tien 4 osan pituus on tietokannassa 5752 metriä.
        ;; Sillä on 3 ajorataa, joiden pituudet on 0 = 4089 m, 1=1667, 2 = 1667 eli yhteensä 7423
        ;; Yritetään antaa kuitenkin virheellinen tieosoite, jossa loppuetäisyys on 6000 metriä. Meidän pitäisi saada vain
        ;; maksimit ulos laskennasta.
        tierekisteriosoite {:tie 20 :aosa 4 :aet 0 :losa 4 :let 6000}
        suolarajoitus (assoc tierekisteriosoite :urakka_id urakka-id)
        pituudet (kutsu-palvelua (:http-palvelin jarjestelma)
                   :tierekisterin-tiedot
                   +kayttaja-jvh+ suolarajoitus)]
    (is (= 5752 (:pituus pituudet)))
    (is (= 7423 (:ajoratojen_pituus pituudet)))))

(defn hae-rajoitukset-kannasta [urakka-id]
  (q-map (str "select ra.id as rajoitusalue_id, rr.id as rajoitus_id, rr.hoitokauden_alkuvuosi as hoitokauden_alkuvuosi, ra.urakka_id as urakka_id
                                                  from rajoitusalue ra join rajoitusalue_rajoitus rr on rr.rajoitusalue_id = ra.id
                                                  WHERE ra.urakka_id = " urakka-id "
                                                  AND ra.poistettu = false
                                                  AND rr.poistettu = false
                                                  ORDER BY hoitokauden_alkuvuosi ASC")))

(deftest tallenna-suolarajoitus-tuleville-vuosille-onnistuu
  (testing "Luodaan urakan ensimmäiselle hoitovuodelle rajoitus ja tarkistetaan, että jokaille tulevallekin hoitovuodelle on olemassa rajoitus"
    (let [hk-alkuvuosi 2005
          urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
          db-suolarajoitukset-alussa (hae-rajoitukset-kannasta urakka-id)

          suolarajoitus (-> (suolarajoitus-pohja
                              urakka-id
                              (:id +kayttaja-jvh+)
                              {:tie 14, :aosa 1, :aet 0, :losa 2, :let 0}
                              hk-alkuvuosi)
                          (assoc :kopioidaan-tuleville-vuosille? true))

          suolarajoitus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-suolarajoitus +kayttaja-jvh+ suolarajoitus)

          ;; TODO: Tarkista, että tallennuksen jälkeen jokaiselle vuodelle on tallentunut rajoitus
          db-suolarajoitukset-jalkeen (hae-rajoitukset-kannasta urakka-id)

          ;; Siivotaan kanta
          _ (poista-suolarajoitus
              {:rajoitusalue_id (:rajoitusalue_id suolarajoitus)
               :hoitokauden_alkuvuosi hk-alkuvuosi
               :urakka_id urakka-id
               :kopioidaan-tuleville-vuosille? true})]

      (is (empty? db-suolarajoitukset-alussa) "Tietokannassa ei ole rajoituksia alussa.")
      (is (= 7 (count db-suolarajoitukset-jalkeen)) "Tietokannassa on jokaiselle hoitovuodelle rajoitus")))
  (testing "Luodaan urakan toiseksi viimeiselle hoitovuodelle rajoitus ja tarkistetaan, että rajoituksia on vain toiseksi viimeisellä ja viimeisellä vuodella"
    (let [hk-alkuvuosi 2010
          urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
          db-suolarajoitukset-alussa (hae-rajoitukset-kannasta urakka-id)

          suolarajoitus (-> (suolarajoitus-pohja
                              urakka-id
                              (:id +kayttaja-jvh+)
                              {:tie 14, :aosa 1, :aet 0, :losa 2, :let 0}
                              hk-alkuvuosi)
                          (assoc :kopioidaan-tuleville-vuosille? true))

          suolarajoitus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-suolarajoitus +kayttaja-jvh+ suolarajoitus)

          ;; TODO: Tarkista, että tallennuksen jälkeen jokaiselle vuodelle on tallentunut rajoitus
          db-suolarajoitukset-jalkeen (hae-rajoitukset-kannasta urakka-id)

          ;; Siivotaan kanta
          _ (poista-suolarajoitus
              {:rajoitusalue_id (:rajoitusalue_id suolarajoitus)
               :hoitokauden_alkuvuosi hk-alkuvuosi
               :urakka_id urakka-id
               :kopioidaan-tuleville-vuosille? true})]

      (is (empty? db-suolarajoitukset-alussa) "Tietokannassa ei ole rajoituksia alussa.")
      (is (= 2 (count db-suolarajoitukset-jalkeen)) "Tietokannassa on jokaiselle hoitovuodelle rajoitus"))))

;; TODO: Lisää tuleville hoitovuosille kopiointiin liittyen yksikkötestejä

(deftest hae-pohjavesialueet-tierekisterille-onnistuu-test
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        tierekisteriosoite {:tie 4 :aosa 364 :aet 3268 :losa 364 :let 3451}

        suolarajoitus (assoc tierekisteriosoite :urakka_id urakka-id)
        tiedot (kutsu-palvelua (:http-palvelin jarjestelma)
                 :tierekisterin-tiedot
                 +kayttaja-jvh+ suolarajoitus)]
    (is (= 183 (:pituus tiedot)))
    ;; 20 tiellä osalla 4 on 3 ajorataa, joten pituuden pitäisi olla kolminkertainen
    (is (= 366 (:ajoratojen_pituus tiedot)))
    (is (= 1 (count (:pohjavesialueet tiedot))))
    (is (= "Kempeleenharju" (:nimi (first (:pohjavesialueet tiedot)))))))


(defn- aseta-urakalle-talvisuolaraja [talvisuolaraja urakka-id hk-alkuvuosi]
  (let [;; Hae suolauksen tehtävän id
        toimenpidekoodi (q-map (str "select id from toimenpidekoodi where taso = 4
        AND suunnitteluyksikko = 'kuivatonnia' AND suoritettavatehtava = 'suolaus'"))
        suolaus-tehtava-id (:id (first toimenpidekoodi))

        ;; Lisää tehtävälle suunniteltu määrä
        _ (u (str (format "insert into urakka_tehtavamaara (urakka, \"hoitokauden-alkuvuosi\", tehtava, maara) values
        (%s, %s, %s, %s)" urakka-id hk-alkuvuosi suolaus-tehtava-id talvisuolaraja)))]))

(deftest tallenna-ja-hae-suolarajoituksen-kokonaiskayttoraja-onnistuu-test
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hk-alkuvuosi 2022

        ;; Kokonais talvisuolaraja on tallennettu tehtäviin ja määriin tehtävälle "Suolaus"
        ;; Joten lisätään annetulle urakalle urakka_tehtavamaarat tauluun suunniteltuja määriä
        talvisuolaraja 1000M
        sanktio_ylittavalta_tonnilta 100000M
        _ (aseta-urakalle-talvisuolaraja talvisuolaraja urakka-id hk-alkuvuosi)
        kayttoraja {:urakka-id urakka-id
                    :tyyppi "kokonaismaara"
                    :hoitokauden-alkuvuosi hk-alkuvuosi
                    :indeksi "MAKU 2015"
                    :kopioidaan-tuleville-vuosille? false
                    :sanktio_ylittavalta_tonnilta sanktio_ylittavalta_tonnilta}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-talvisuolan-kayttoraja +kayttaja-jvh+ kayttoraja)

        ;; Hae rajoitusalueen suolasanktio, jotta voi vertailla lukuja
        hakutulos (kutsu-palvelua (:http-palvelin jarjestelma)
                    :hae-talvisuolan-kayttorajat +kayttaja-jvh+
                    {:urakka-id urakka-id
                     :hoitokauden-alkuvuosi hk-alkuvuosi})
        ;; Siivotaan kanta
        _ (u (str "DELETE from suolasakko WHERE urakka = " urakka-id))
        _ (u (str (format "DELETE from urakka_tehtavamaara
                            WHERE urakka = %s
                              AND \"hoitokauden-alkuvuosi\" = %s
                              AND maara = %s" urakka-id hk-alkuvuosi talvisuolaraja)))]

    ;; Tarkistetaan tallennuksen vastauksen tiedot
    (is (not (nil? (:id vastaus))))
    (is (= sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta vastaus)))
    (is (= "MAKU 2015" (:indeksi vastaus)))
    (is (= true (:kaytossa vastaus)))
    (is (= "kokonaismaara" (:tyyppi vastaus)))

    ;; Tarkistetaan hakutulos
    (is (not (nil? (get-in hakutulos [:talvisuolan-sanktiot :id]))))
    (is (= talvisuolaraja (:talvisuolan-kokonaismaara hakutulos)))
    (is (= sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta vastaus)))
    (is (= "MAKU 2015" (get-in hakutulos [:talvisuolan-sanktiot :indeksi])))
    (is (= true (get-in hakutulos [:talvisuolan-sanktiot :kaytossa])))
    (is (= "kokonaismaara" (get-in hakutulos [:talvisuolan-sanktiot :tyyppi])))))

(deftest paivita-ja-hae-suolarajoituksen-kokonaiskayttoraja-onnistuu-test
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hk-alkuvuosi 2022

        ;; Kokonais talvisuolaraja on tallennettu tehtäviin ja määriin tehtävälle "Suolaus"
        ;; Joten lisätään annetulle urakalle urakka_tehtavamaarat tauluun suunniteltuja määriä
        talvisuolaraja 1000M
        sanktio_ylittavalta_tonnilta 30000M
        muokattu_sanktio_ylittavalta_tonnilta 30000M
        _ (aseta-urakalle-talvisuolaraja talvisuolaraja urakka-id hk-alkuvuosi)
        kayttoraja {:urakka-id urakka-id
                    :tyyppi "kokonaismaara"
                    :hoitokauden-alkuvuosi hk-alkuvuosi
                    :indeksi "MAKU 2015"
                    :kopioidaan-tuleville-vuosille? false
                    :sanktio_ylittavalta_tonnilta sanktio_ylittavalta_tonnilta}
        uusi-kayttoraja (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-talvisuolan-kayttoraja +kayttaja-jvh+ kayttoraja)
        _ (println "uusi-kayttoraja: " uusi-kayttoraja)

        ;; Muokataan kokonaisrajoitusta hieman
        muokattu-kayttoraja (assoc uusi-kayttoraja :sanktio_ylittavalta_tonnilta muokattu_sanktio_ylittavalta_tonnilta)
        muokattu-vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-talvisuolan-kayttoraja +kayttaja-jvh+ muokattu-kayttoraja)

        ;; Hae rajoitusalueen suolasanktio, jotta voi vertailla lukuja
        hakutulos (kutsu-palvelua (:http-palvelin jarjestelma)
                    :hae-talvisuolan-kayttorajat +kayttaja-jvh+
                    {:urakka-id urakka-id
                     :hoitokauden-alkuvuosi hk-alkuvuosi})

        ;; Siivotaan kanta
        _ (u (str "DELETE from suolasakko WHERE urakka = " urakka-id))
        _ (u (str (format "DELETE from urakka_tehtavamaara
                            WHERE urakka = %s
                              AND \"hoitokauden-alkuvuosi\" = %s
                              AND maara = %s" urakka-id hk-alkuvuosi talvisuolaraja)))]

    ;; Tarkistetaan tallennuksen vastauksen tiedot
    (is (not (nil? (:id uusi-kayttoraja))))
    (is (= sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta uusi-kayttoraja)))
    (is (= "MAKU 2015" (:indeksi uusi-kayttoraja)))
    (is (= true (:kaytossa uusi-kayttoraja)))
    (is (= "kokonaismaara" (:tyyppi uusi-kayttoraja)))

    ;; Tarkistetaan muokatun vastauksen tiedot
    (is (not (nil? (:id muokattu-vastaus))))
    (is (= muokattu_sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta muokattu-vastaus)))
    (is (= "MAKU 2015" (:indeksi muokattu-vastaus)))
    (is (= true (:kaytossa muokattu-vastaus)))
    (is (= "kokonaismaara" (:tyyppi muokattu-vastaus)))

    ;; Tarkistetaan hakutulos
    (is (not (nil? (get-in hakutulos [:talvisuolan-sanktiot :id]))))
    (is (= talvisuolaraja (:talvisuolan-kokonaismaara hakutulos)))
    (is (= muokattu_sanktio_ylittavalta_tonnilta (get-in hakutulos [:talvisuolan-sanktiot :sanktio_ylittavalta_tonnilta])))
    (is (= "MAKU 2015" (get-in hakutulos [:talvisuolan-sanktiot :indeksi])))
    (is (= true (get-in hakutulos [:talvisuolan-sanktiot :kaytossa])))
    (is (= "kokonaismaara" (get-in hakutulos [:talvisuolan-sanktiot :tyyppi])))))


(deftest tallenna-ja-hae-rajoitusalueen-suolasanktio-onnistuu-test
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hk-alkuvuosi 2022
        sanktio-ylittavalta-tonnilta 5000M                  ;; euroa
        aluesanktio {:urakka-id urakka-id
                      :sanktio_ylittavalta_tonnilta sanktio-ylittavalta-tonnilta
                      :tyyppi "rajoitusalue"
                      :hoitokauden-alkuvuosi hk-alkuvuosi
                      :indeksi "MAKU 2015"
                      :kopioidaan-tuleville-vuosille? false}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :tallenna-rajoitusalueen-sanktio +kayttaja-jvh+ aluesanktio)

        ;; Hae rajoitusalueen suolasanktio, jotta voi vertailla lukuja
        sanktio (:rajoitusalueiden-suolasanktio (kutsu-palvelua (:http-palvelin jarjestelma)
                                                  :hae-talvisuolan-kayttorajat +kayttaja-jvh+
                                                  {:urakka-id urakka-id
                                                   :hoitokauden-alkuvuosi hk-alkuvuosi}))

        ;; Siivotaan kanta
        _ (u (str "DELETE from suolasakko WHERE urakka = " urakka-id))]

    ;; Testaa vastauksen tiedot
    (is (not (nil? (:id vastaus))))
    (is (= sanktio-ylittavalta-tonnilta (:sanktio_ylittavalta_tonnilta vastaus)))
    (is (= "MAKU 2015" (:indeksi vastaus)))
    (is (= true (:kaytossa vastaus)))
    (is (= "rajoitusalue" (:tyyppi vastaus)))

    ;; Testaa hakutuloksen tiedot
    (is (not (nil? (:id sanktio))))
    (is (= sanktio-ylittavalta-tonnilta (:sanktio_ylittavalta_tonnilta sanktio)))
    (is (= "MAKU 2015" (:indeksi sanktio)))
    (is (= true (:kaytossa sanktio)))
    (is (= "rajoitusalue" (:tyyppi sanktio)))))

;; Vanhalla uniikkius constraintilla tämä ei voi toimia.
(deftest tallenna-suolarajat-ja-rajoutusaluerajat-onnistuu-test
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hk-alkuvuosi 2022
        sanktio-ylittavalta-tonnilta 5000M                  ;; euroa
        suolasanktio {:urakka-id urakka-id
                      :sanktio_ylittavalta_tonnilta sanktio-ylittavalta-tonnilta
                      :tyyppi "kokonaismaara"
                      :hoitokauden-alkuvuosi hk-alkuvuosi
                      :indeksi "MAKU 2015"
                      :kopioidaan-tuleville-vuosille? false}
        suolasanktio-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                               :tallenna-talvisuolan-kayttoraja +kayttaja-jvh+ suolasanktio)

        aluesanktio {:urakka-id urakka-id
                     :sanktio_ylittavalta_tonnilta sanktio-ylittavalta-tonnilta
                     :tyyppi "rajoitusalue"
                     :hoitokauden-alkuvuosi hk-alkuvuosi
                     :indeksi "MAKU 2015"
                     :kopioidaan-tuleville-vuosille? false}
        aluesanktio-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :tallenna-rajoitusalueen-sanktio +kayttaja-jvh+ aluesanktio)

        ;; Siivotaan kanta
        _ (u (str "DELETE from suolasakko WHERE urakka = " urakka-id))]

    ;; Testaa vastauksen tiedot
    (is (not (nil? (:id suolasanktio-vastaus))))
    (is (not (nil? (:id aluesanktio-vastaus))))
    (is (= sanktio-ylittavalta-tonnilta (:sanktio_ylittavalta_tonnilta suolasanktio-vastaus)))
    (is (= sanktio-ylittavalta-tonnilta (:sanktio_ylittavalta_tonnilta aluesanktio-vastaus)))
    (is (= "MAKU 2015" (:indeksi suolasanktio-vastaus)))
    (is (= "MAKU 2015" (:indeksi aluesanktio-vastaus)))
    (is (= true (:kaytossa suolasanktio-vastaus)))
    (is (= true (:kaytossa aluesanktio-vastaus)))
    (is (= "kokonaismaara" (:tyyppi suolasanktio-vastaus)))
    (is (= "rajoitusalue" (:tyyppi aluesanktio-vastaus)))))
