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
  jarjestelma-fixture)

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

(deftest tallenna-suolarajoituksen-kokonaikayttoraja-onnistuu-test
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hk-alkuvuosi 2022
        kayttoraja {:urakka-id urakka-id
                    :talvisuolaraja 1
                    :kaytossa true
                    :tyyppi "kokonaismaara"
                    :hoitokauden-alkuvuosi hk-alkuvuosi
                    :indeksi "MAKU 2015"
                    :kopioidaan-tuleville-vuosille? false}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-talvisuolan-kayttoraja +kayttaja-jvh+ kayttoraja)
        ;; Siivotaan kanta
        _ (u (str "DELETE from suolasakko WHERE urakka = "urakka-id))
        ]
    (is (= 1M (:talvisuolaraja vastaus)))
    (is (= "MAKU 2015" (:indeksi vastaus)))
    (is (= true (:kaytossa vastaus)))
    (is (= "kokonaismaara" (:tyyppi vastaus)))
    ))
