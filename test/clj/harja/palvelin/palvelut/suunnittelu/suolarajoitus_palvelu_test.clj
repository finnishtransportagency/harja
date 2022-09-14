(ns harja.palvelin.palvelut.suunnittelu.suolarajoitus-palvelu-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :as t]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.suunnittelu.suolarajoitus-palvelu :as suolarajoitus-palvelu]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'t/jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta t/testitietokanta)
          :http-palvelin (t/testi-http-palvelin)
          :suolarajoitukset (component/using
                              (suolarajoitus-palvelu/->Suolarajoitus)
                              [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'t/jarjestelma component/stop))

(use-fixtures :each
  jarjestelma-fixture
  t/tietokanta-fixture)

(defn- suolarajoitus-pohja
  "Olettaa saavansa tierekisteriosoitteen muodossa: {:tie 86, :aosa 1, :aet 0, :losa 2, :let 10}"
  [urakka_id kayttaja_id tr_osoite hoitokauden-alkuvuosi]
  {:tie (:tie tr_osoite)
   :aosa (:aosa tr_osoite)
   :aet (:aet tr_osoite)
   :losa (:losa tr_osoite)
   :let (:let tr_osoite)
   :pituus 1
   :ajoratojen_pituus 1
   :suolarajoitus 1234
   :formiaatti false
   :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
   :kopioidaan-tuleville-vuosille? false
   :urakka_id urakka_id
   :kayttaja_id kayttaja_id})

(defn- hae-suolarajoitukset [parametrit]
  (t/kutsu-palvelua (:http-palvelin t/jarjestelma) :hae-suolarajoitukset t/+kayttaja-jvh+ parametrit))

(defn- poista-suolarajoitus [parametrit]
  (t/kutsu-palvelua (:http-palvelin t/jarjestelma) :poista-suolarajoitus t/+kayttaja-jvh+ parametrit))

(defn hae-rajoitusalueet-urakalle [urakka-id]
  (t/q-map (str "SELECT id as rajoitusalue_id, urakka_id FROM rajoitusalue
  WHERE poistettu = FALSE AND urakka_id = " urakka-id)))

(defn hae-rajoitusalue-rajoitukset-urakalle [urakka-id]
  (t/q-map (str "SELECT ra.id as rajoitusalue_id, rr.suolarajoitus, rr.hoitokauden_alkuvuosi, ra.urakka_id
  FROM rajoitusalue ra, rajoitusalue_rajoitus rr
  WHERE rr.rajoitusalue_id = ra.id
    AND ra.poistettu = FALSE
    AND rr.poistettu = FALSE
    AND ra.urakka_id = " urakka-id)))

(deftest hae-suolarajoitukset-hoitovuoden-perusteella-onnistuu-test
  (let [urakka_id (t/hae-urakan-id-nimella "Oulun MHU 2019-2024")
        hk_alkuvuosi 2022
        suolarajoitukset (hae-suolarajoitukset {:hoitokauden-alkuvuosi hk_alkuvuosi :urakka-id urakka_id})]

    (is (> (count suolarajoitukset) 0) "Suolarajoitukset löytyy")))

(deftest tallenna-suolarajoitus-onnistuu-test
  (let [hk-alkuvuosi 2022
        urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        rajoitus (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tallenna-suolarajoitus
                   t/+kayttaja-jvh+
                   (suolarajoitus-pohja
                     urakka-id
                     (:id t/+kayttaja-jvh+)
                     {:tie 22, :aosa 1, :aet 0, :losa 2, :let 10}
                     hk-alkuvuosi))
        ;; Siivotaan kanta
        _ (poista-suolarajoitus
            {:rajoitusalue_id (:rajoitusalue_id rajoitus)
             :hoitokauden-alkuvuosi hk-alkuvuosi
             :urakka_id urakka-id
             :kopioidaan-tuleville-vuosille? true})]
    (is (> (count rajoitus) 0) "Uusi rajoitus on tallennettu")))

(deftest tallenna-suolarajoitus-pohjavesialueelle-onnistuu-test
  (let [hk-alkuvuosi 2022
        urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        rajoitus (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tallenna-suolarajoitus
                   t/+kayttaja-jvh+
                   (suolarajoitus-pohja
                     urakka-id
                     (:id t/+kayttaja-jvh+)
                     {:tie 4 :aosa 364 :aet 3268 :losa 364 :let 3451}
                     hk-alkuvuosi))
        ;; Siivotaan kanta
        _ (poista-suolarajoitus
            {:rajoitusalue_id (:rajoitusalue_id rajoitus)
             :hoitokauden-alkuvuosi hk-alkuvuosi
             :urakka_id urakka-id
             :kopioidaan-tuleville-vuosille? true})]
    (is (> (count rajoitus) 0) "Uusi rajoitus on tallennettu")
    (is (seq (:pohjavesialueet rajoitus)) "Uusi rajoitus on tallennettu pohjavesialueelle")))

(deftest paivita-suolarajoitus-onnistuu-test
  (let [hk-alkuvuosi 2022
        urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        suolarajoitus (suolarajoitus-pohja urakka-id (:id t/+kayttaja-jvh+)
                        {:tie 4, :aosa 11, :aet 0, :losa 12, :let 100}
                        hk-alkuvuosi)
        ;; Luodaan uusi rajoitusalue, jota muokataan
        suolarajoitus (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                        :tallenna-suolarajoitus
                        t/+kayttaja-jvh+
                        suolarajoitus)
        rajoitukset (hae-suolarajoitukset {:urakka-id urakka-id :hoitokauden-alkuvuosi hk-alkuvuosi})

        ;; Kovakoodatusti juuri luotu alue
        muokattava-rajoitus (-> (first rajoitukset)
                              (assoc :pituus 999)
                              (assoc :ajoratojen_pituus 1234)
                              )
        paivitetty-rajoitus (t/kutsu-palvelua (:http-palvelin t/jarjestelma) :tallenna-suolarajoitus t/+kayttaja-jvh+
                              muokattava-rajoitus)
        ;; Siivotaan kanta
        _ (poista-suolarajoitus
            {:rajoitusalue_id (:rajoitusalue_id suolarajoitus)
             :hoitokauden-alkuvuosi hk-alkuvuosi
             :urakka_id urakka-id
             :kopioidaan-tuleville-vuosille? true})]

    (is (not= paivitetty-rajoitus muokattava-rajoitus) "Päivitys onnistui")
    (is (= 999 (:pituus paivitetty-rajoitus)) "Pituuden päivitys onnistui")
    (is (= 1234 (:ajoratojen_pituus paivitetty-rajoitus)) "Ajoratojen pituuden päivitys onnistui")))

(deftest poista-suolarajoitus-tulevilta-vuosilta-onnistuu-test
  (let [hk-alkuvuosi 2022
        urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        suolarajoitusalueet-alkuun (t/q-map (str "SELECT id, urakka_id FROM rajoitusalue WHERE poistettu = FALSE"))
        suolarajoitukset-alkuun (t/q-map (str "SELECT id, rajoitusalue_id, suolarajoitus FROM rajoitusalue_rajoitus WHERE poistettu = FALSE"))
        ;; Luodaan uusi rajoitusalue, joka poistetaan
        suolarajoitus (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                        :tallenna-suolarajoitus
                        t/+kayttaja-jvh+
                        (assoc (suolarajoitus-pohja
                                 urakka-id
                                 (:id t/+kayttaja-jvh+)
                                 {:tie 4, :aosa 11, :aet 0, :losa 12, :let 100}
                                 hk-alkuvuosi)
                          :kopioidaan-tuleville-vuosille? true))

        ;; Poista luotu rajoitus
        _ (poista-suolarajoitus
            {:rajoitusalue_id (:rajoitusalue_id suolarajoitus)
             :hoitokauden-alkuvuosi hk-alkuvuosi
             :urakka_id urakka-id
             :kopioidaan-tuleville-vuosille? true})

        suolarajoitusalueet-lopuksi (t/q-map (str "SELECT id, urakka_id FROM rajoitusalue WHERE poistettu = FALSE"))
        suolarajoitukset-lopuksi (t/q-map (str "SELECT id, rajoitusalue_id, suolarajoitus FROM rajoitusalue_rajoitus WHERE poistettu = FALSE"))]

    (is (= suolarajoitusalueet-lopuksi suolarajoitusalueet-alkuun) "Poistaminen onnistui")
    (is (= suolarajoitukset-lopuksi suolarajoitukset-alkuun) "Poistaminen onnistui")))

(deftest poista-suolarajoitus-vain-talta-vuodelta-onnistuu-test
  (let [hk-alkuvuosi 2022
        urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        suolarajoitusalueet-alkuun (hae-rajoitusalueet-urakalle urakka-id)
        suolarajoitukset-alkuun (hae-rajoitusalue-rajoitukset-urakalle urakka-id)
        ;; Luodaan uusi rajoitusalue, joka poistetaan
        suolarajoitus (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                        :tallenna-suolarajoitus
                        t/+kayttaja-jvh+
                        (assoc (suolarajoitus-pohja
                                 urakka-id
                                 (:id t/+kayttaja-jvh+)
                                 {:tie 4, :aosa 11, :aet 0, :losa 12, :let 100}
                                 hk-alkuvuosi)
                          :kopioidaan-tuleville-vuosille? true))

        ;; Poista luotu rajoitus
        _ (poista-suolarajoitus
            {:rajoitusalue_id (:rajoitusalue_id suolarajoitus)
             :hoitokauden-alkuvuosi hk-alkuvuosi
             :urakka_id urakka-id
             :kopioidaan-tuleville-vuosille? false})

        suolarajoitusalueet-lopuksi (hae-rajoitusalueet-urakalle urakka-id)
        suolarajoitukset-lopuksi (hae-rajoitusalue-rajoitukset-urakalle urakka-id)

        ;; Siivoa kanta
        _ (poista-suolarajoitus
            {:rajoitusalue_id (:rajoitusalue_id suolarajoitus)
             :hoitokauden-alkuvuosi hk-alkuvuosi
             :urakka_id urakka-id
             :kopioidaan-tuleville-vuosille? true})]

    (is (not= suolarajoitusalueet-lopuksi suolarajoitusalueet-alkuun) "Vain yhden poistaminen onnistui")
    (is (not= suolarajoitukset-lopuksi suolarajoitukset-alkuun) "Vain yhden poistaminen onnistui")
    (is (= (+ 3 (count suolarajoitukset-alkuun)) (count suolarajoitukset-lopuksi)) "Vain yhden poistaminen onnistui")))

;; TODO: Tarkista poistossa, että se ei poista menneitä rajoituksia


(deftest laske-tierekisteriosoitteelle-pituus-onnistuu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        tierekisteriosoite {:tie 20 :aosa 4 :aet 0 :losa 4 :let 50}
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
        pituudet (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tierekisterin-tiedot
                   t/+kayttaja-jvh+ suolarajoitus)]
    (is (= 50 (:pituus pituudet)))
    (is (= 50 (:ajoratojen_pituus pituudet)))))

(deftest laske-tierekisteriosoitteelle-pituus2-onnistuu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        tierekisteriosoite {:tie 20 :aosa 4 :aet 4000 :losa 4 :let 4100}
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
        pituudet (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tierekisterin-tiedot
                   t/+kayttaja-jvh+ suolarajoitus)]
    (is (= 100 (:pituus pituudet)))
    (is (= 111 (:ajoratojen_pituus pituudet)))))

(deftest laske-tierekisteriosoitteelle-pituus3-onnistuu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        tierekisteriosoite {:tie 20 :aosa 4 :aet 4000 :losa 4 :let 5799}
        ;; tie 20, osan 4 pituus on yht: 5752 josta loput 1667m on kahta ajorataa, se vaihtuu kahdeksi ajoradaksi kohdassa 4089
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
        pituudet (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tierekisterin-tiedot
                   t/+kayttaja-jvh+ suolarajoitus)]
    (is (= 1752 (:pituus pituudet)))
    (is (= 3423 (:ajoratojen_pituus pituudet)))))           ;; Jos ei otettaisi huomioon, että ajoradan pituus päättyy kohtaan 5752, pituudeksi tulisi 3511

(deftest laske-tierekisteriosoitteelle-pituus4-onnistuu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        tierekisteriosoite {:tie 20 :aosa 4 :aet 4000 :losa 5 :let 1}
        ;; tie 20, osan 4 pituus on yht: 5752 josta loput 1667m on kahta ajorataa, se vaihtuu kahdeksi ajoradaksi kohdassa 4089
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
        pituudet (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tierekisterin-tiedot
                   t/+kayttaja-jvh+ suolarajoitus)]
    (is (= 1753 (:pituus pituudet)))                        ;; Edelliseen testiin verrattuna ollaan lisätty pituutta yhdellä
    (is (= 3424 (:ajoratojen_pituus pituudet)))))           ;; Jos ei otettaisi huomioon, että ajoradan pituus päättyy kohtaan 5752, pituudeksi tulisi 3511

(deftest laske-tierekisteriosoitteelle-pituus5-onnistuu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        aet 6000
        osan-19-pituus 7311
        let 3300
        tierekisteriosoite {:tie 25 :aosa 19 :aet aet :losa 20 :let let}
        ;; tie 25, osan 19 pituus on yht: 7311. Osa 20, koostuu kolmesta ajoradasta joka vaihtuu 1->2 kohdasta:3531 . Sen jälkeen ajoratojen (1,2) pituus: 3965
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
        pituudet (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tierekisterin-tiedot
                   t/+kayttaja-jvh+ suolarajoitus)]
    (is (= (+ (- osan-19-pituus aet) let) (:pituus pituudet)))
    (is (= (+ (- osan-19-pituus aet) let) (:pituus pituudet)))))

;; Lasketaan tierekisteriosoitteelle pituus, joka koostu alkuostasta, joka alkaa pari osaa aiemmin, kuin loppuosa.
;; Ja jossa keskimmäiselle osalle ei ole olemassa pituutta ajorata taulussa
(deftest laske-kahdelle-osalle-pituus-onnistuu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        tierekisteriosoite {:tie 25 :aosa 9 :aet 2177 :losa 11 :let 2995}
        ;tie 25 osa: 9 pituus: 3688
        ;tie 25 osa 11 pituus 5870
        ;; Osien Laskenta
        ; osan 9, pituus on 3688 metriä, joten 3688 - 2177 = 1511 - haetaan siis loppuosan pituus
        ;; Osan 10 pituus on 0
        ;; osan 11 pituus on 5870, joten kohtaaan 2995 asti otetaan kokonaan kaikki -> 1511 + 2995 = 4506

        ;; Ajoratalaskenta
        ;tie 25 osa 9, ajorata 0 pituus 32681
        ;tie 25 osa 9 ajorata 1 pituus 420
        ;tie 25 osa 9 ajorata 2 pituus 420
        ;tie 25 osa 11 ajorata 0 pituus 5870
        ;; Osan 9 ajoratojen pituudeksi tulee siis kohdasta 2177 eteenpäin: (3268+420+420) -> 4108 - 2177 = 1931
        ;; Osan 10 pituus on 0
        ;; OSan 11 pituus on 5870, joten me otetaan koko mitta 2995 , kokonais ajoratojen pituus on siis 1931 +2995 = 4926

        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
        pituudet (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tierekisterin-tiedot
                   t/+kayttaja-jvh+ suolarajoitus)]
    (is (= 4506 (:pituus pituudet)))
    ;; 20 tiellä osalla 4 on 3 ajorataa, joten pituuden pitäisi olla kolminkertainen
    (is (= 4926 (:ajoratojen_pituus pituudet)))))

(deftest laske-tierekisteriosoitteelle-pituus-epaonnistuu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        tierekisteriosoite {:tie 20 :aosa "makkara" :aet "lenkki" :losa "pihvi" :let "hiiligrilli"}
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
        pituudet (future (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                           :tierekisterin-tiedot
                           t/+kayttaja-jvh+ suolarajoitus))]
    (is (= "Tierekisteriosoitteessa virhe." (str/trim (get-in @pituudet [:vastaus]))) "Väärillä tiedoilla ei voi laskea pituutta.")))


(deftest laske-tierekisteriosoitteelle-pituus-vaarilla-tiedoilla-onnistuu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; 20 tien 4 osan pituus on tietokannassa 5752 metriä.
        ;; Sillä on 3 ajorataa, joiden pituudet on 0 = 4089 m, 1=1667, 2 = 1667 eli yhteensä 7423
        ;; Yritetään antaa kuitenkin virheellinen tieosoite, jossa loppuetäisyys on 6000 metriä. Meidän pitäisi saada vain
        ;; maksimit ulos laskennasta.
        tierekisteriosoite {:tie 20 :aosa 4 :aet 0 :losa 4 :let 6000}
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
        pituudet (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tierekisterin-tiedot
                   t/+kayttaja-jvh+ suolarajoitus)]
    (is (= 5752 (:pituus pituudet)))
    (is (= 7423 (:ajoratojen_pituus pituudet)))))

(deftest validoi-nolla-let-arvo-onnistuu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; 20 tien 4 osan pituus on tietokannassa 5752 metriä.
        ;; Sillä on 3 ajorataa, joiden pituudet on 0 = 4089 m, 1=1667, 2 = 1667 eli yhteensä 7423
        ;; Yritetään antaa kuitenkin virheellinen tieosoite, jossa loppuetäisyys on 6000 metriä. Meidän pitäisi saada vain
        ;; maksimit ulos laskennasta.
        tierekisteriosoite {:tie 20 :aosa 4 :aet 0 :losa 5 :let 0}
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
        pituudet (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tierekisterin-tiedot
                   t/+kayttaja-jvh+ suolarajoitus)]
    (is (= 5752 (:pituus pituudet)))
    (is (= 7423 (:ajoratojen_pituus pituudet)))))

(deftest varmista-paallekaiset-rajoitukset-ei-onnistu-test
  (let [hk-alkuvuosi 2022
        urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        perusrajoitus {:urakka-id urakka-id :hoitokauden-alkuvuosi hk-alkuvuosi :rajoitusalue-id nil}
        ;; Rajoitus, jonka alkuosa ja loppu osa ovat eri kohdassa
        rajoitus (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tallenna-suolarajoitus
                   t/+kayttaja-jvh+
                   (suolarajoitus-pohja
                     urakka-id
                     (:id t/+kayttaja-jvh+)
                     {:tie 25 :aosa 2 :aet 200 :losa 4 :let 2000}
                     hk-alkuvuosi))

        ;; Rajoitus jonka alkuosa ja loppuosa ovat samoja, vain alkuet ja loppuet poikkeaa
        rajoitus2 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                    :tallenna-suolarajoitus
                    t/+kayttaja-jvh+
                    (suolarajoitus-pohja
                      urakka-id
                      (:id t/+kayttaja-jvh+)
                      {:tie 5 :aosa 20 :aet 200 :losa 20 :let 2000}
                      hk-alkuvuosi))]

    (testing "Varmistetaan, että samaa tierekisteriosoitetta ei voi käyttää muissa rajoituksissa"
      (let [tr-sama {:tie 25 :aosa 2 :aet 200 :losa 4 :let 2000}
            suolarajoitus-sama (merge perusrajoitus tr-sama)
            tr-tiedot-sama (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                             :tierekisterin-tiedot
                             t/+kayttaja-jvh+ suolarajoitus-sama)

            tr-sama2 {:tie 5 :aosa 20 :aet 200 :losa 20 :let 2000}
            suolarajoitus-sama2 (merge perusrajoitus tr-sama2)
            tr-tiedot-sama2 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                              :tierekisterin-tiedot
                              t/+kayttaja-jvh+ suolarajoitus-sama2)

            tr-alku-sama {:tie 25 :aosa 4 :aet 2000 :losa 4 :let 2001}
            suolarajoitus-alku-sama (merge perusrajoitus tr-alku-sama)
            tr-tiedot-alku-sama (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                  :tierekisterin-tiedot
                                  t/+kayttaja-jvh+ suolarajoitus-alku-sama)

            tr-alku-sama2 {:tie 25 :aosa 4 :aet 2000 :losa 5 :let 2001}
            suolarajoitus-alku-sama2 (merge perusrajoitus tr-alku-sama2)
            tr-tiedot-alku-sama2 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                   :tierekisterin-tiedot
                                   t/+kayttaja-jvh+ suolarajoitus-alku-sama2)

            tr-alku-sama3 {:tie 5 :aosa 20 :aet 2000 :losa 20 :let 2001}
            suolarajoitus-alku-sama3 (merge perusrajoitus tr-alku-sama3)
            tr-tiedot-alku-sama3 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                   :tierekisterin-tiedot
                                   t/+kayttaja-jvh+ suolarajoitus-alku-sama3)]
        (is (= 400 (:status tr-tiedot-sama)) "Tierekisteriosoitteessa on jo rajoitus.")
        (is (= 400 (:status tr-tiedot-sama2)) "Tierekisteriosoitteessa on jo rajoitus.")
        (is (= {:pituus 1, :ajoratojen_pituus 1, :pohjavesialueet ()} tr-tiedot-alku-sama) "Alku sama, mutta saa talentaa.")
        (is (= 5511 (:pituus tr-tiedot-alku-sama2)) "Alku sama, mutta saa talentaa.")
        (is (= {:pituus 0, :ajoratojen_pituus 0, :pohjavesialueet ()} tr-tiedot-alku-sama3) "Alku sama, mutta saa talentaa.")))

    (testing "Tierekisteri on olemassa olevan välissä"
      (let [ tr {:tie 25 :aosa 3 :aet 200 :losa 3 :let 2000}
            suolarajoitus (merge perusrajoitus tr)
            tr-tiedot (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                        :tierekisterin-tiedot
                        t/+kayttaja-jvh+ suolarajoitus)]
        (is (= 400 (:status tr-tiedot)) "Tierekisteriosoitteessa on jo rajoitus.")))

    (testing "Ei voi tallentaa tierekisteriä, jonka loppuosa on keskellä rajoitusaluetta"
      (let [tr-loppu-keskella {:tie 25 :aosa 2 :aet 1 :losa 3 :let 2001}
            suolarajoitus-loppu-keskella (merge perusrajoitus tr-loppu-keskella)
            tr-tiedot-loppu-keskella (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                       :tierekisterin-tiedot
                                       t/+kayttaja-jvh+ suolarajoitus-loppu-keskella)

            tr-loppu-keskella2 {:tie 25 :aosa 3 :aet 1 :losa 3 :let 2001}
            suolarajoitus-loppu-keskella2 (merge perusrajoitus tr-loppu-keskella2)
            tr-tiedot-loppu-keskella2 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                        :tierekisterin-tiedot
                                        t/+kayttaja-jvh+ suolarajoitus-loppu-keskella2)

            tr-loppu-keskella3 {:tie 5 :aosa 20 :aet 1 :losa 20 :let 300}
            suolarajoitus-loppu-keskella3 (merge perusrajoitus tr-loppu-keskella3)
            tr-tiedot-loppu-keskella3 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                        :tierekisterin-tiedot
                                        t/+kayttaja-jvh+ suolarajoitus-loppu-keskella3)]
        (is (= 400 (:status tr-tiedot-loppu-keskella)) "Loppu keskellä, eikä saa tallentaa")
        (is (= 400 (:status tr-tiedot-loppu-keskella2)) "Loppu keskellä, eikä saa tallentaa")
        (is (= 400 (:status tr-tiedot-loppu-keskella3)) "Loppu keskellä, eikä saa tallentaa")))

    (testing "Ei voi tallentaa tierekisteriä, jonka alkuosa on keskellä rajoitusaluetta"
      (let [tr-alku-keskella {:tie 25 :aosa 3 :aet 1 :losa 7 :let 2001}
            suolarajoitus-alku-keskella (merge perusrajoitus tr-alku-keskella)
            tr-tiedot-alku-keskella (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                      :tierekisterin-tiedot
                                      t/+kayttaja-jvh+ suolarajoitus-alku-keskella)

            tr-alku-keskella2 {:tie 25 :aosa 4 :aet 1 :losa 4 :let 3001}
            suolarajoitus-alku-keskella2 (merge perusrajoitus tr-alku-keskella2)
            tr-tiedot-alku-keskella2 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                       :tierekisterin-tiedot
                                       t/+kayttaja-jvh+ suolarajoitus-alku-keskella2)

            tr-alku-keskella3 {:tie 5 :aosa 20 :aet 300 :losa 20 :let 3001}
            suolarajoitus-alku-keskella3 (merge perusrajoitus tr-alku-keskella3)
            tr-tiedot-alku-keskella3 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                       :tierekisterin-tiedot
                                       t/+kayttaja-jvh+ suolarajoitus-alku-keskella3)]
        (is (= 400 (:status tr-tiedot-alku-keskella)) "Alku keskellä, eikä saa tallentaa")
        (is (= 400 (:status tr-tiedot-alku-keskella2)) "Alku keskellä, eikä saa tallentaa")
        (is (= 400 (:status tr-tiedot-alku-keskella3)) "Alku keskellä, eikä saa tallentaa")))

    (testing "Varmisetaan, että tallennus onnistuu, kun annettu tierekisteri ei ole lähelläkään olemassaolevia rajoituksia"
      (let [tr-ei-lahellakaan {:tie 25 :aosa 10 :aet 1 :losa 10 :let 3001}
            suolarajoitus-ei-lahella (merge perusrajoitus tr-ei-lahellakaan)
            tr-tiedot-ei-lahella (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                   :tierekisterin-tiedot
                                   t/+kayttaja-jvh+ suolarajoitus-ei-lahella)

            tr-ei-lahellakaan2 {:tie 25 :aosa 10 :aet 1 :losa 11 :let 3001}
            suolarajoitus-ei-lahella2 (merge perusrajoitus tr-ei-lahellakaan2)
            tr-tiedot-ei-lahella2 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                    :tierekisterin-tiedot
                                    t/+kayttaja-jvh+ suolarajoitus-ei-lahella2)]
        (is (= {:pituus 0, :ajoratojen_pituus 0, :pohjavesialueet ()} tr-tiedot-ei-lahella) "Ei lähelläkään muita rajoituksia.")
        (is (= 3001 (:pituus tr-tiedot-ei-lahella2)) "Ei lähelläkään muita rajoituksia.")))

    (testing "Tierekisteri olemassaolevan ympärille"
      (let [tr-ymparilla {:tie 25 :aosa 1 :aet 2000 :losa 400 :let 2001}
            suolarajoitus-ymparilla (merge perusrajoitus tr-ymparilla)
            tr-tiedot-ymparilla (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                  :tierekisterin-tiedot
                                  t/+kayttaja-jvh+ suolarajoitus-ymparilla)

            tr-ymparilla2 {:tie 5 :aosa 20 :aet 1 :losa 20 :let 4000}
            suolarajoitus-ymparilla2 (merge perusrajoitus tr-ymparilla2)
            tr-tiedot-ymparilla2 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                   :tierekisterin-tiedot
                                   t/+kayttaja-jvh+ suolarajoitus-ymparilla2)]
        (is (= 400 (:status tr-tiedot-ymparilla)) "Olemassa oleva rajoitus osuu tierekisterin sisään")
        (is (= 400 (:status tr-tiedot-ymparilla2)) "Olemassa oleva rajoitus osuu tierekisterin sisään")))

    ;; Siivotaan kanta
    (poista-suolarajoitus
      {:rajoitusalue_id (:rajoitusalue_id rajoitus)
       :hoitokauden-alkuvuosi hk-alkuvuosi
       :urakka_id urakka-id
       :kopioidaan-tuleville-vuosille? true})
    (poista-suolarajoitus
      {:rajoitusalue_id (:rajoitusalue_id rajoitus2)
       :hoitokauden-alkuvuosi hk-alkuvuosi
       :urakka_id urakka-id
       :kopioidaan-tuleville-vuosille? true})))

(deftest varmista-paallekaiset-rajoitukset-onnistu-test
  (let [hk-alkuvuosi 2022
        urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        rajoitus (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tallenna-suolarajoitus
                   t/+kayttaja-jvh+
                   (suolarajoitus-pohja
                     urakka-id
                     (:id t/+kayttaja-jvh+)
                     {:tie 4 :aosa 7 :aet 7 :losa 7 :let 8}
                     hk-alkuvuosi))

        ;; Varmista, että uusi suolarajoitus on hyväksyttävä, vaikka tierekisteri alkaa samasta
        ;; pisteestä, kuin mihin yllä oleva loppui
        tierekisteriosoite {:tie 4 :aosa 7 :aet 8 :losa 7 :let 10}
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id :hoitokauden-alkuvuosi hk-alkuvuosi)
        tierekisterin-tiedot (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                               :tierekisterin-tiedot
                               t/+kayttaja-jvh+ suolarajoitus)
        _ (println "tierekisterin-tiedot: " tierekisterin-tiedot)
        odotettu-tulos {:pituus 0, :ajoratojen_pituus 0, :pohjavesialueet ()}

        ;; Siivotaan kanta
        _ (poista-suolarajoitus
            {:rajoitusalue_id (:rajoitusalue_id rajoitus)
             :hoitokauden-alkuvuosi hk-alkuvuosi
             :urakka_id urakka-id
             :kopioidaan-tuleville-vuosille? true})]
    (is (= odotettu-tulos tierekisterin-tiedot) "Tierekisterin-tiedot on hyväksyttäviä")))

(defn hae-rajoitukset-kannasta [urakka-id]
  (t/q-map (str "select ra.id as rajoitusalue_id, rr.id as rajoitus_id, rr.hoitokauden_alkuvuosi as hoitokauden_alkuvuosi, ra.urakka_id as urakka_id
                                                  from rajoitusalue ra join rajoitusalue_rajoitus rr on rr.rajoitusalue_id = ra.id
                                                  WHERE ra.urakka_id = " urakka-id "
                                                  AND ra.poistettu = false
                                                  AND rr.poistettu = false
                                                  ORDER BY hoitokauden_alkuvuosi ASC")))

(deftest tallenna-suolarajoitus-tuleville-vuosille-onnistuu
  (testing "Luodaan urakan ensimmäiselle hoitovuodelle rajoitus ja tarkistetaan, että jokaille tulevallekin hoitovuodelle on olemassa rajoitus"
    (let [hk-alkuvuosi 2005
          urakka-id (t/hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
          db-suolarajoitukset-alussa (hae-rajoitukset-kannasta urakka-id)

          suolarajoitus (-> (suolarajoitus-pohja
                              urakka-id
                              (:id t/+kayttaja-jvh+)
                              {:tie 14, :aosa 1, :aet 0, :losa 2, :let 0}
                              hk-alkuvuosi)
                          (assoc :kopioidaan-tuleville-vuosille? true))

          suolarajoitus (t/kutsu-palvelua (:http-palvelin t/jarjestelma) :tallenna-suolarajoitus t/+kayttaja-jvh+ suolarajoitus)

          ;; TODO: Tarkista, että tallennuksen jälkeen jokaiselle vuodelle on tallentunut rajoitus
          db-suolarajoitukset-jalkeen (hae-rajoitukset-kannasta urakka-id)

          ;; Siivotaan kanta
          _ (poista-suolarajoitus
              {:rajoitusalue_id (:rajoitusalue_id suolarajoitus)
               :hoitokauden-alkuvuosi hk-alkuvuosi
               :urakka_id urakka-id
               :kopioidaan-tuleville-vuosille? true})]

      (is (empty? db-suolarajoitukset-alussa) "Tietokannassa ei ole rajoituksia alussa.")
      (is (= 7 (count db-suolarajoitukset-jalkeen)) "Tietokannassa on jokaiselle hoitovuodelle rajoitus")))
  (testing "Luodaan urakan toiseksi viimeiselle hoitovuodelle rajoitus ja tarkistetaan, että rajoituksia on vain toiseksi viimeisellä ja viimeisellä vuodella"
    (let [hk-alkuvuosi 2010
          urakka-id (t/hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
          db-suolarajoitukset-alussa (hae-rajoitukset-kannasta urakka-id)

          suolarajoitus (-> (suolarajoitus-pohja
                              urakka-id
                              (:id t/+kayttaja-jvh+)
                              {:tie 14, :aosa 1, :aet 0, :losa 2, :let 0}
                              hk-alkuvuosi)
                          (assoc :kopioidaan-tuleville-vuosille? true))

          suolarajoitus (t/kutsu-palvelua (:http-palvelin t/jarjestelma) :tallenna-suolarajoitus t/+kayttaja-jvh+ suolarajoitus)

          ;; TODO: Tarkista, että tallennuksen jälkeen jokaiselle vuodelle on tallentunut rajoitus
          db-suolarajoitukset-jalkeen (hae-rajoitukset-kannasta urakka-id)

          ;; Siivotaan kanta
          _ (poista-suolarajoitus
              {:rajoitusalue_id (:rajoitusalue_id suolarajoitus)
               :hoitokauden-alkuvuosi hk-alkuvuosi
               :urakka_id urakka-id
               :kopioidaan-tuleville-vuosille? true})]

      (is (empty? db-suolarajoitukset-alussa) "Tietokannassa ei ole rajoituksia alussa.")
      (is (= 2 (count db-suolarajoitukset-jalkeen)) "Tietokannassa on jokaiselle hoitovuodelle rajoitus"))))

;; TODO: Lisää tuleville hoitovuosille kopiointiin liittyen yksikkötestejä

(deftest hae-pohjavesialueet-tierekisterille-onnistuu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; Tiellä 4, osalla 364 on 2 ajorataa, ja niiden pituudet on: 9505m
        ;; Joten ajoratojen pituudeksi pitäisi  tulla (* 2 (- :let :aet)
        tierekisteriosoite {:tie 4 :aosa 364 :aet 3268 :losa 364 :let 3451}

        suolarajoitus (assoc tierekisteriosoite :urakka_id urakka-id)
        tiedot (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                 :tierekisterin-tiedot
                 t/+kayttaja-jvh+ suolarajoitus)]
    (is (= 183 (:pituus tiedot)))
    ;; 20 tiellä osalla 4 on 3 ajorataa, joten pituuden pitäisi olla kolminkertainen
    (is (= 366 (:ajoratojen_pituus tiedot)))
    (is (= 1 (count (:pohjavesialueet tiedot))))
    (is (= "Kempeleenharju" (:nimi (first (:pohjavesialueet tiedot)))))))


(defn- aseta-urakalle-talvisuolaraja [talvisuolaraja urakka-id hk-alkuvuosi]
  (let [;; Hae suolauksen tehtävän id
        toimenpidekoodi (t/q-map (str "select id from toimenpidekoodi where taso = 4
        AND suunnitteluyksikko = 'kuivatonnia' AND suoritettavatehtava = 'suolaus'"))
        suolaus-tehtava-id (:id (first toimenpidekoodi))

        ;; Lisää tehtävälle suunniteltu määrä
        _ (t/u (str (format "insert into urakka_tehtavamaara (urakka, \"hoitokauden-alkuvuosi\", tehtava, maara) values
        (%s, %s, %s, %s)" urakka-id hk-alkuvuosi suolaus-tehtava-id talvisuolaraja)))]))

(deftest tallenna-ja-hae-suolarajoituksen-kokonaiskayttoraja-onnistuu-mhu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        hk-alkuvuosi 2022

        ;; Kokonais talvisuolaraja on tallennettu tehtäviin ja määriin tehtävälle "Suolaus"
        ;; Joten lisätään annetulle urakalle urakka_tehtavamaarat tauluun suunniteltuja määriä
        talvisuolaraja 1000M
        sanktio_ylittavalta_tonnilta 100000M
        _ (aseta-urakalle-talvisuolaraja talvisuolaraja urakka-id hk-alkuvuosi)
        kayttoraja {:urakka-id urakka-id
                    :tyyppi "kokonaismaara"
                    :hoitokauden-alkuvuosi hk-alkuvuosi
                    ;; Asetetaan payloadiin indeksi, mutta se ei saa tallentua oikeasti!
                    ;; Back-endin kuuluu asettaa indeksi tyhjäksi mhu-urakoille kokonaismäärän käyttörajan sanktioon.
                    :indeksi "MAKU 2222"
                    :kopioidaan-tuleville-vuosille? false
                    :sanktio_ylittavalta_tonnilta sanktio_ylittavalta_tonnilta}
        vastaus (t/kutsu-palvelua (:http-palvelin t/jarjestelma) :tallenna-talvisuolan-kayttoraja t/+kayttaja-jvh+ kayttoraja)

        ;; Hae rajoitusalueen suolasanktio, jotta voi vertailla lukuja
        hakutulos (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                    :hae-talvisuolan-kayttorajat t/+kayttaja-jvh+
                    {:urakka-id urakka-id
                     :hoitokauden-alkuvuosi hk-alkuvuosi})
        ;; Siivotaan kanta
        _ (t/u (str "DELETE from suolasakko WHERE urakka = " urakka-id))
        _ (t/u (str (format "DELETE from urakka_tehtavamaara
                            WHERE urakka = %s
                              AND \"hoitokauden-alkuvuosi\" = %s
                              AND maara = %s" urakka-id hk-alkuvuosi talvisuolaraja)))]

    ;; Tarkistetaan tallennuksen vastauksen tiedot
    (is (not (nil? (:id vastaus))))
    (is (= sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta vastaus)))
    (is (= nil (:indeksi vastaus)))
    (is (= true (:kaytossa vastaus)))
    (is (= "kokonaismaara" (:tyyppi vastaus)))

    ;; Tarkistetaan hakutulos
    (is (not (nil? (get-in hakutulos [:talvisuolan-sanktiot :id]))))
    (is (= talvisuolaraja (get-in hakutulos [:talvisuolan-sanktiot :talvisuolan-kayttoraja])))
    (is (= sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta vastaus)))
    (is (= nil (get-in hakutulos [:talvisuolan-sanktiot :indeksi])))
    (is (= true (get-in hakutulos [:talvisuolan-sanktiot :kaytossa])))
    (is (= "kokonaismaara" (get-in hakutulos [:talvisuolan-sanktiot :tyyppi])))))

(deftest paivita-ja-hae-suolarajoituksen-kokonaiskayttoraja-onnistuu-mhu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
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
                    ;; Asetetaan payloadiin indeksi, mutta se ei saa tallentua oikeasti!
                    ;; Back-endin kuuluu asettaa indeksi tyhjäksi mhu-urakoille kokonaismäärän käyttörajan sanktioon.
                    :indeksi "MAKU 2222"
                    :kopioidaan-tuleville-vuosille? false
                    :sanktio_ylittavalta_tonnilta sanktio_ylittavalta_tonnilta}
        uusi-kayttoraja (t/kutsu-palvelua (:http-palvelin t/jarjestelma) :tallenna-talvisuolan-kayttoraja t/+kayttaja-jvh+ kayttoraja)

        ;; Muokataan kokonaisrajoitusta hieman
        muokattu-kayttoraja (assoc uusi-kayttoraja :sanktio_ylittavalta_tonnilta muokattu_sanktio_ylittavalta_tonnilta)
        muokattu-vastaus (t/kutsu-palvelua (:http-palvelin t/jarjestelma) :tallenna-talvisuolan-kayttoraja t/+kayttaja-jvh+ muokattu-kayttoraja)

        ;; Hae rajoitusalueen suolasanktio, jotta voi vertailla lukuja
        hakutulos (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                    :hae-talvisuolan-kayttorajat t/+kayttaja-jvh+
                    {:urakka-id urakka-id
                     :hoitokauden-alkuvuosi hk-alkuvuosi})

        ;; Siivotaan kanta
        _ (t/u (str "DELETE from suolasakko WHERE urakka = " urakka-id))
        _ (t/u (str (format "DELETE from urakka_tehtavamaara
                            WHERE urakka = %s
                              AND \"hoitokauden-alkuvuosi\" = %s
                              AND maara = %s" urakka-id hk-alkuvuosi talvisuolaraja)))]

    ;; Tarkistetaan tallennuksen vastauksen tiedot
    (is (not (nil? (:id uusi-kayttoraja))))
    (is (= sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta uusi-kayttoraja)))
    (is (= nil (:indeksi uusi-kayttoraja)))
    (is (= true (:kaytossa uusi-kayttoraja)))
    (is (= "kokonaismaara" (:tyyppi uusi-kayttoraja)))

    ;; Tarkistetaan muokatun vastauksen tiedot
    (is (not (nil? (:id muokattu-vastaus))))
    (is (= muokattu_sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta muokattu-vastaus)))
    (is (= nil (:indeksi muokattu-vastaus)))
    (is (= true (:kaytossa muokattu-vastaus)))
    (is (= "kokonaismaara" (:tyyppi muokattu-vastaus)))

    ;; Tarkistetaan hakutulos
    (is (not (nil? (get-in hakutulos [:talvisuolan-sanktiot :id]))))
    (is (= talvisuolaraja (get-in hakutulos [:talvisuolan-sanktiot :talvisuolan-kayttoraja])))
    (is (= muokattu_sanktio_ylittavalta_tonnilta (get-in hakutulos [:talvisuolan-sanktiot :sanktio_ylittavalta_tonnilta])))
    (is (= nil (get-in hakutulos [:talvisuolan-sanktiot :indeksi])))
    (is (= true (get-in hakutulos [:talvisuolan-sanktiot :kaytossa])))
    (is (= "kokonaismaara" (get-in hakutulos [:talvisuolan-sanktiot :tyyppi])))))

(deftest tallenna-ja-hae-suolarajoituksen-kokonaiskayttoraja-onnistuu-alueurakka-test
  (let [urakka-id (t/hae-urakan-id-nimella "Tampereen alueurakka 2017-2022")
        hk-alkuvuosi 2022
        suolasakko-tai-bonus-maara 100M
        vain-sakko-maara 50M
        maksukuukausi 9
        talvisuolan-kayttoraja 500M
        suolasakko-kaytossa? true
        kayttoraja {:suolasakko-tai-bonus-maara suolasakko-tai-bonus-maara
                    :vain-sakko-maara vain-sakko-maara
                    :maksukuukausi maksukuukausi
                    ;; Back-endin kuuluu asettaa oikea indeksi (MAKU 2010 tälle alueurakalle)
                    :indeksi nil
                    :talvisuolan-kayttoraja talvisuolan-kayttoraja
                    :urakka-id urakka-id
                    :suolasakko-kaytossa suolasakko-kaytossa?
                    :hoitokauden-alkuvuosi hk-alkuvuosi}
        vastaus (t/kutsu-palvelua (:http-palvelin t/jarjestelma) :tallenna-talvisuolan-kayttoraja t/+kayttaja-jvh+ kayttoraja)

        ;; Hae rajoitusalueen suolasanktio, jotta voi vertailla lukuja
        hakutulos (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                    :hae-talvisuolan-kayttorajat t/+kayttaja-jvh+
                    {:urakka-id urakka-id
                     :hoitokauden-alkuvuosi hk-alkuvuosi})
        ;; Siivotaan kanta
        _ (t/u (str "DELETE from suolasakko WHERE urakka = " urakka-id))]

    ;; Tarkistetaan tallennuksen vastauksen tiedot
    (is (not (nil? (:id vastaus))))
    (is (= "MAKU 2010" (:indeksi vastaus)))
    (is (= true (:suolasakko-kaytossa vastaus)))
    (is (= suolasakko-tai-bonus-maara (:suolasakko-tai-bonus-maara vastaus)))
    (is (= vain-sakko-maara (:vain-sakko-maara vastaus)))
    (is (= talvisuolan-kayttoraja (:talvisuolan-kayttoraja vastaus)))
    (is (= maksukuukausi (:maksukuukausi vastaus)))
    (is (= "MAKU 2010" (:indeksi vastaus)))
    (is (= true (:suolasakko-kaytossa vastaus)))
    (is (= "kokonaismaara" (:tyyppi vastaus)))

    ;; Tarkistetaan hakutulos
    (is (not (nil? (get-in hakutulos [:talvisuolan-sanktiot :id]))))
    (is (= talvisuolan-kayttoraja (get-in hakutulos [:talvisuolan-sanktiot :talvisuolan-kayttoraja])))
    (is (= suolasakko-tai-bonus-maara (get-in hakutulos [:talvisuolan-sanktiot :suolasakko-tai-bonus-maara])))
    (is (= maksukuukausi (get-in hakutulos [:talvisuolan-sanktiot :maksukuukausi])))
    (is (= vain-sakko-maara (get-in hakutulos [:talvisuolan-sanktiot :vain-sakko-maara])))
    (is (= "MAKU 2010" (get-in hakutulos [:talvisuolan-sanktiot :indeksi])))
    (is (= true (get-in hakutulos [:talvisuolan-sanktiot :suolasakko-kaytossa])))
    (is (= "kokonaismaara" (get-in hakutulos [:talvisuolan-sanktiot :tyyppi])))))

(deftest paivita-ja-hae-suolarajoituksen-kokonaiskayttoraja-onnistuu-alueurakka-test
  (let [urakka-id (t/hae-urakan-id-nimella "Tampereen alueurakka 2017-2022")
        hk-alkuvuosi 2022
        muokattu-suolasakko-tai-bonus-maara 100M
        vain-sakko-maara 50M
        maksukuukausi 9
        talvisuolan-kayttoraja 500M
        suolasakko-kaytossa? true
        kayttoraja {:suolasakko-tai-bonus-maara 50M
                    :vain-sakko-maara vain-sakko-maara
                    :maksukuukausi maksukuukausi
                    ;; Back-endin kuuluu asettaa oikea indeksi (MAKU 2010 tälle urakalle)
                    :indeksi nil
                    :talvisuolan-kayttoraja talvisuolan-kayttoraja
                    :urakka-id urakka-id
                    :suolasakko-kaytossa suolasakko-kaytossa?
                    :hoitokauden-alkuvuosi hk-alkuvuosi}
        vastaus (t/kutsu-palvelua (:http-palvelin t/jarjestelma) :tallenna-talvisuolan-kayttoraja t/+kayttaja-jvh+ kayttoraja)

        ;; Muokataan kokonaisrajoitusta hieman
        muokattu-kayttoraja (assoc kayttoraja :suolasakko-tai-bonus-maara muokattu-suolasakko-tai-bonus-maara
                                              :id (:id vastaus))
        vastaus (t/kutsu-palvelua (:http-palvelin t/jarjestelma) :tallenna-talvisuolan-kayttoraja t/+kayttaja-jvh+ muokattu-kayttoraja)

        ;; Hae rajoitusalueen suolasanktio, jotta voi vertailla lukuja
        hakutulos (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                    :hae-talvisuolan-kayttorajat t/+kayttaja-jvh+
                    {:urakka-id urakka-id
                     :hoitokauden-alkuvuosi hk-alkuvuosi})
        ;; Siivotaan kanta
        _ (t/u (str "DELETE from suolasakko WHERE urakka = " urakka-id))]

    ;; Tarkistetaan tallennuksen vastauksen tiedot
    (is (not (nil? (:id vastaus))))
    (is (= "MAKU 2010" (:indeksi vastaus)))
    (is (= muokattu-suolasakko-tai-bonus-maara (:suolasakko-tai-bonus-maara vastaus)))
    (is (= true (:suolasakko-kaytossa vastaus)))
    (is (= vain-sakko-maara (:vain-sakko-maara vastaus)))
    (is (= talvisuolan-kayttoraja (:talvisuolan-kayttoraja vastaus)))
    (is (= maksukuukausi (:maksukuukausi vastaus)))
    (is (= "MAKU 2010" (:indeksi vastaus)))
    (is (= true (:suolasakko-kaytossa vastaus)))
    (is (= "kokonaismaara" (:tyyppi vastaus)))

    ;; Tarkistetaan hakutulos
    (is (not (nil? (get-in hakutulos [:talvisuolan-sanktiot :id]))))
    (is (= talvisuolan-kayttoraja (get-in hakutulos [:talvisuolan-sanktiot :talvisuolan-kayttoraja])))
    (is (= muokattu-suolasakko-tai-bonus-maara (get-in hakutulos [:talvisuolan-sanktiot :suolasakko-tai-bonus-maara])))
    (is (= maksukuukausi (get-in hakutulos [:talvisuolan-sanktiot :maksukuukausi])))
    (is (= vain-sakko-maara (get-in hakutulos [:talvisuolan-sanktiot :vain-sakko-maara])))
    (is (= "MAKU 2010" (get-in hakutulos [:talvisuolan-sanktiot :indeksi])))
    (is (= true (get-in hakutulos [:talvisuolan-sanktiot :suolasakko-kaytossa])))
    (is (= "kokonaismaara" (get-in hakutulos [:talvisuolan-sanktiot :tyyppi])))))


(deftest tallenna-ja-hae-rajoitusalueen-suolasanktio-onnistuu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        hk-alkuvuosi 2022
        sanktio-ylittavalta-tonnilta 5000M                  ;; euroa
        aluesanktio {:urakka-id urakka-id
                     :sanktio_ylittavalta_tonnilta sanktio-ylittavalta-tonnilta
                     :tyyppi "rajoitusalue"
                     :hoitokauden-alkuvuosi hk-alkuvuosi
                     ;; Tämä arvo ei saisi päätyä tietokantaan.
                     ;; Backendin kuuluu asettaa oikea indeksi (MAKU 2015 tälle alueurakalle).
                     :indeksi "MAKU 2222"
                     :kopioidaan-tuleville-vuosille? false}
        vastaus (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                  :tallenna-rajoitusalueen-sanktio t/+kayttaja-jvh+ aluesanktio)

        ;; Hae rajoitusalueen suolasanktio, jotta voi vertailla lukuja
        sanktio (:rajoitusalueiden-suolasanktio (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                                  :hae-talvisuolan-kayttorajat t/+kayttaja-jvh+
                                                  {:urakka-id urakka-id
                                                   :hoitokauden-alkuvuosi hk-alkuvuosi}))

        ;; Siivotaan kanta
        _ (t/u (str "DELETE from suolasakko WHERE urakka = " urakka-id))]

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
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        hk-alkuvuosi 2022
        sanktio-ylittavalta-tonnilta 5000M                  ;; euroa
        suolasanktio {:urakka-id urakka-id
                      :sanktio_ylittavalta_tonnilta sanktio-ylittavalta-tonnilta
                      :tyyppi "kokonaismaara"
                      :hoitokauden-alkuvuosi hk-alkuvuosi
                      ;; Tämä arvo ei saisi päätyä tietokantaan.
                      ;; Backendin kuuluu tallentaa tyhjä indeksi MHU urakoille kokonaismäärän käyttörajan sanktiolle
                      :indeksi "MAKU 2222"
                      :kopioidaan-tuleville-vuosille? false}
        suolasanktio-vastaus (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                               :tallenna-talvisuolan-kayttoraja t/+kayttaja-jvh+ suolasanktio)

        aluesanktio {:urakka-id urakka-id
                     :sanktio_ylittavalta_tonnilta sanktio-ylittavalta-tonnilta
                     :tyyppi "rajoitusalue"
                     :hoitokauden-alkuvuosi hk-alkuvuosi
                     ;; Tämä arvo ei saisi päätyä tietokantaan.
                     ;; Backendin kuuluu tallentaa oikea indeksi urakalle (Tälle urakalle MAKU 2015).
                     :indeksi "MAKU 2222"
                     :kopioidaan-tuleville-vuosille? false}
        aluesanktio-vastaus (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                              :tallenna-rajoitusalueen-sanktio t/+kayttaja-jvh+ aluesanktio)

        ;; Siivotaan kanta
        _ (t/u (str "DELETE from suolasakko WHERE urakka = " urakka-id))]

    ;; Testaa vastauksen tiedot
    (is (not (nil? (:id suolasanktio-vastaus))))
    (is (not (nil? (:id aluesanktio-vastaus))))
    (is (= sanktio-ylittavalta-tonnilta (:sanktio_ylittavalta_tonnilta suolasanktio-vastaus)))
    (is (= sanktio-ylittavalta-tonnilta (:sanktio_ylittavalta_tonnilta aluesanktio-vastaus)))
    (is (= nil (:indeksi suolasanktio-vastaus)))
    (is (= "MAKU 2015" (:indeksi aluesanktio-vastaus)))
    (is (= true (:kaytossa suolasanktio-vastaus)))
    (is (= true (:kaytossa aluesanktio-vastaus)))
    (is (= "kokonaismaara" (:tyyppi suolasanktio-vastaus)))
    (is (= "rajoitusalue" (:tyyppi aluesanktio-vastaus)))))

(deftest siirra-pohjavesialue-suolatoteumat-rajoitusalueeksi-onnistuu
  (let [urakka-id (t/hae-urakan-id-nimella "Espoon alueurakka 2014-2019") ;; Tällä urakalla ei ole olemassa yhtään pohjavesialue rajoitusta vielä
        hk_alkuvuosi 2014
        urakan-loppuvuosi 2019
        urakan-vuodet (range hk_alkuvuosi urakan-loppuvuosi)
        pohjavesirajoitukset [{:nimi "Kempeleenharju",
                               :tunnus "11244001",
                               :talvisuolaraja 6.6M,
                               :tie 4,
                               :aosa 364,
                               :aet 1599,
                               :losa 364,
                               :let 4296,
                               :pituus 2697,
                               :ajoratojen_pituus 5394,
                               :hoitokauden-alkuvuosi hk_alkuvuosi,
                               :urakkaid urakka-id}]
        _ (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
            :siirra-urakan-pohjavesialueet t/+kayttaja-jvh+ {:urakkaid urakka-id
                                                             :pohjavesialueet pohjavesirajoitukset})
        ;; Haetaan rajoitukset jokaiselle urakan vuodelle
        suolarajoitukset (reduce (fn [rajoitukset vuosi]
                                   (conj rajoitukset (hae-suolarajoitukset {:hoitokauden-alkuvuosi vuosi :urakka-id urakka-id})))
                           [] urakan-vuodet)]
    ;; Jokaiselle tulevalle vuodelle luodaan uusi rajoitus, joten niitä pitää olla yhtä monta kuin lista * vuodet
    (is (= (* (count pohjavesirajoitukset) (count urakan-vuodet)) (count suolarajoitukset)))))
