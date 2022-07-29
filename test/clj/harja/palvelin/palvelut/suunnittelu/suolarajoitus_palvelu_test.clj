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

(deftest hae-suolarajoitukset-hoitovuoden-perusteella-onnistuu-test
  (let [urakka_id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        hk_alkuvuosi 2022
        suolarajoitukset (hae-suolarajoitukset {:hoitokauden_alkuvuosi hk_alkuvuosi :urakka_id urakka_id})]

    (is (> (count suolarajoitukset) 0) "Suolarajoitukset löytyy")))

(deftest tallenna-suolarajoitus-onnistuu-test
  (let [hk-alkuvuosi 2022
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
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
             :urakka_id urakka-id})]
    (is (> (count rajoitus) 0) "Uusi rajoitus on tallennettu")))

(deftest paivita-suolarajoitus-onnistuu-test
  (let [hk-alkuvuosi 2022
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; Luodaan uusi rajoitusalue, jota muokataan
        suolarajoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tallenna-suolarajoitus
                        +kayttaja-jvh+
                        (suolarajoitus-pohja
                          urakka-id
                          (:id +kayttaja-jvh+)
                          {:tie 4, :aosa 11, :aet 0, :losa 12, :let 100}
                          hk-alkuvuosi))
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
             :urakka_id urakka-id})]

    (is (not= paivitetty-rajoitus muokattava-rajoitus) "Päivitys onnistui")
    (is (= 999 (:pituus paivitetty-rajoitus)) "Pituuden päivitys onnistui")
    (is (= 1234 (:ajoratojen_pituus paivitetty-rajoitus)) "Ajoratojen pituuden päivitys onnistui")))

(deftest poista-suolarajoitus-onnistuu-test
  (let [hk-alkuvuosi 2022
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        suolarajoitusalueet-alkuun (q-map (str "SELECT id, urakka_id FROM rajoitusalue WHERE poistettu = FALSE"))
        suolarajoitukset-alkuun (q-map (str "SELECT id, rajoitusalue_id, suolarajoitus FROM rajoitusalue_rajoitus WHERE poistettu = FALSE"))
        ;; Luodaan uusi rajoitusalue, jota muokataan
        suolarajoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tallenna-suolarajoitus
                        +kayttaja-jvh+
                        (suolarajoitus-pohja
                          urakka-id
                          (:id +kayttaja-jvh+)
                          {:tie 4, :aosa 11, :aet 0, :losa 12, :let 100}
                          hk-alkuvuosi))

        ;; Poista luotu rajoitus
        _ (poista-suolarajoitus
            {:rajoitusalue_id (:rajoitusalue_id suolarajoitus)
             :hoitokauden_alkuvuosi hk-alkuvuosi
             :urakka_id urakka-id})

        suolarajoitusalueet-lopuksi (q-map (str "SELECT id, urakka_id FROM rajoitusalue WHERE poistettu = FALSE"))
        suolarajoitukset-lopuksi (q-map (str "SELECT id, rajoitusalue_id, suolarajoitus FROM rajoitusalue_rajoitus WHERE poistettu = FALSE"))]

    (is (= suolarajoitusalueet-lopuksi suolarajoitusalueet-alkuun) "Poistaminen onnistui")
    (is (= suolarajoitukset-lopuksi suolarajoitukset-alkuun) "Poistaminen onnistui")))

(deftest laske-tierekisteriosoitteelle-pituus-onnistuu-test
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        tierekisteriosoite {:tie 20 :aosa 4 :aet 0 :losa 4 :let 50}
        suolarajoitus (assoc tierekisteriosoite :urakka_id urakka-id)
        pituudet (kutsu-palvelua (:http-palvelin jarjestelma)
                 :laske-suolarajoituksen-pituudet
                 +kayttaja-jvh+ suolarajoitus)
        _ (println "laske-tierekisteriosoitteelle-pituus-onnistuu-test :: pituudet" pituudet)]
    (is (= 50 (:pituus pituudet)))
    ;; 20 tiellä osalla 4 on 3 ajorataa, joten pituuden pitäisi olla kolminkertainen
    (is (= 150 (:ajoratojen_pituus pituudet)))))

(deftest laske-tierekisteriosoitteelle-pituus-epaonnistuu-test
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        tierekisteriosoite {:tie 20 :aosa "makkara" :aet "lenkki" :losa "pihvi" :let "hiiligrilli"}
        suolarajoitus (assoc tierekisteriosoite :urakka_id urakka-id)
        pituudet (future (kutsu-palvelua (:http-palvelin jarjestelma)
                           :laske-suolarajoituksen-pituudet
                           +kayttaja-jvh+ suolarajoitus))]
    (is (thrown? Exception @pituudet) "Väärillä tiedoilla heitetään poikkeus.")))


(deftest laske-tierekisteriosoitteelle-pituus-vaarilla-tiedoilla-onnistuu-test
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; 20 tien 4 osan pituus on tietokannassa 5752 metriä.
        ;; Sillä on 3 ajorataa, joiden pituudet on 0 = 4089 m, 1=1667, 2 = 1667 eli yhteensä 7423
        ;; Yritetään antaa kuitenkin virheellinen tieosoite, jossa loppuetäisyys on 6000 metriä. Meidän pitäisi saada vain
        ;; maksimit ulos laskennasta.
        tierekisteriosoite {:tie 20 :aosa 4 :aet 0 :losa 4 :let 6000}
        suolarajoitus (assoc tierekisteriosoite :urakka_id urakka-id)
        pituudet (kutsu-palvelua (:http-palvelin jarjestelma)
                   :laske-suolarajoituksen-pituudet
                   +kayttaja-jvh+ suolarajoitus)
        _ (println "laske-tierekisteriosoitteelle-pituus-onnistuu-test :: pituudet" pituudet)]
    (is (= 5752 (:pituus pituudet)))
    (is (= 7423 (:ajoratojen_pituus pituudet)))))

(deftest tallenna-suolarajoitus-tuleville-vuosille-onnistuu
  (let [hk-alkuvuosi 2022
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")

        suolarajoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tallenna-suolarajoitusalue
                        +kayttaja-jvh+
                        (suolarajoitus-pohja
                          urakka-id
                          (:id +kayttaja-jvh+)
                          {:tie 14, :aosa 1, :aet 0, :losa 2, :let 0}
                          hk-alkuvuosi))
        suolarajoitus (-> suolarajoitus
                                 (assoc :suolarajoitus 123)
                                 (assoc :formiaatti true))
        muokattu-suolarajoitus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-suolarajoitus +kayttaja-jvh+
                                 muokattu-suolarajoitus)
        _ (println "muokattu-suolarajoitus" muokattu-suolarajoitus)
        ;; Siivotaan kanta
        _ (poista-suolarajoitus
            {:rajoitusalue_id (:rajoitusalue_id suolarajoitus)
             :hoitokauden_alkuvuosi hk-alkuvuosi
             :urakka_id urakka-id})
        ]

    (is (not (nil? (:muokkaaja muokattu-suolarajoitus))) "Muokkaaja löytyy")
    (is (not= suolarajoitus muokattu-suolarajoitus) "Päivitys onnistui")
    (is (= 123M (:suolarajoitus muokattu-suolarajoitus)) "Suolarajoitus asetettu")))

#_(deftest paivita-suolarajoitus-uselle-vuodelle-onnistuu
    (let [hk-alkuvuosi 2022
          urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")

          suolarajoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                          :tallenna-suolarajoitusalue
                          +kayttaja-jvh+
                          (suolarajoitus-pohja
                            urakka-id
                            (:id +kayttaja-jvh+)
                            {:tie 14, :aosa 1, :aet 0, :losa 2, :let 0}
                            hk-alkuvuosi))
          muokattu-suolarajoitus (-> suolarajoitus
                                   (assoc :suolarajoitus 123)
                                   (assoc :formiaatti true))
          muokattu-suolarajoitus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-suolarajoitus +kayttaja-jvh+
                                   muokattu-suolarajoitus)
          _ (println "muokattu-suolarajoitus" muokattu-suolarajoitus)
          ;; Siivotaan kanta
          _ (poista-suolarajoitus
              {:rajoitusalue_id (:rajoitusalue_id suolarajoitus)
               :hoitokauden_alkuvuosi hk-alkuvuosi
               :urakka_id urakka-id})
          ]

      (is (not (nil? (:muokkaaja muokattu-suolarajoitus))) "Muokkaaja löytyy")
      (is (not= suolarajoitus muokattu-suolarajoitus) "Päivitys onnistui")
      (is (= 123M (:suolarajoitus muokattu-suolarajoitus)) "Suolarajoitus asetettu")))
