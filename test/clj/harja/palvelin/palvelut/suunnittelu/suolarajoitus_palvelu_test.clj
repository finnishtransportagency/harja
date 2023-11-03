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
  ;t/tietokanta-fixture
  )

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
        tierekisteriosoite {:tie 4, :aosa 101, :aet 0, :losa 102, :let 100}
        suolarajoitus (suolarajoitus-pohja urakka-id (:id t/+kayttaja-jvh+)
                        tierekisteriosoite
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
                              (assoc :ajoratojen_pituus 1234))
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

(deftest paivita-suolarajoitus2-onnistuu-test
  (let [hk-alkuvuosi 2022
        urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; Luodaan uusi rajoitusalue, jonka olemassaolo voi sotkea muokattavan rajoitusalueen varmistusta
        suolarajoitus-este (suolarajoitus-pohja urakka-id (:id t/+kayttaja-jvh+)
                             {:tie 12, :aosa 219, :aet 5862, :losa 221, :let 455}
                             hk-alkuvuosi)
        suolarajoitus-este (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                             :tallenna-suolarajoitus
                             t/+kayttaja-jvh+
                             suolarajoitus-este)

        ;; Luodaan itse muokattava rajoitus
        muokattava-rajoitus (suolarajoitus-pohja urakka-id (:id t/+kayttaja-jvh+)
                              {:tie 12, :aosa 219, :aet 151, :losa 219, :let 1492}
                              hk-alkuvuosi)
        muokattava-rajoitus (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                              :tallenna-suolarajoitus
                              t/+kayttaja-jvh+
                              muokattava-rajoitus)

        rajoitukset (hae-suolarajoitukset {:urakka-id urakka-id :hoitokauden-alkuvuosi hk-alkuvuosi})

        ;; Kovakoodatusti juuri luotu alue
        paivitetty-rajoitus (-> (first rajoitukset)
                              (assoc :let 1493)
                              (assoc :pituus 1343)
                              (assoc :ajoratojen_pituus 1343))
        paivitetty-rajoitus (t/kutsu-palvelua (:http-palvelin t/jarjestelma) :tallenna-suolarajoitus t/+kayttaja-jvh+
                              paivitetty-rajoitus)
        ;; Siivotaan kanta
        _ (poista-suolarajoitus
            {:rajoitusalue_id (:rajoitusalue_id suolarajoitus-este)
             :hoitokauden-alkuvuosi hk-alkuvuosi
             :urakka_id urakka-id
             :kopioidaan-tuleville-vuosille? true})
        _ (poista-suolarajoitus
            {:rajoitusalue_id (:rajoitusalue_id muokattava-rajoitus)
             :hoitokauden-alkuvuosi hk-alkuvuosi
             :urakka_id urakka-id
             :kopioidaan-tuleville-vuosille? true})]

    (is (not= paivitetty-rajoitus muokattava-rajoitus) "Päivitys onnistui")
    (is (= 1 (:pituus suolarajoitus-este)) "Pituuden päivitys onnistui")
    (is (= 1343 (:pituus paivitetty-rajoitus)) "Pituuden päivitys onnistui")
    (is (= 1343 (:ajoratojen_pituus paivitetty-rajoitus)) "Ajoratojen pituuden päivitys onnistui")))

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
                                 {:tie 4, :aosa 101, :aet 0, :losa 102, :let 100}
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
        tierekisteriosoite {:tie 20 :aosa 4 :aet 4000 :losa 4 :let 5756}
        ;; tie 20, osan 4 pituus on yht: 5752 josta loput 1667m on kahta ajorataa, se vaihtuu kahdeksi ajoradaksi kohdassa 4089
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
        pituudet (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tierekisterin-tiedot
                   t/+kayttaja-jvh+ suolarajoitus)]
    (is (= 1752 (:pituus pituudet)))
    (is (= 3423 (:ajoratojen_pituus pituudet))))) ;; Jos ei otettaisi huomioon, että ajoradan pituus päättyy kohtaan 5752, pituudeksi tulisi 3511

(deftest laske-tierekisteriosoitteelle-pituus4-onnistuu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        tierekisteriosoite {:tie 20 :aosa 4 :aet 4000 :losa 5 :let 1}
        ;; tie 20, osan 4 pituus on yht: 5752 josta loput 1667m on kahta ajorataa, se vaihtuu kahdeksi ajoradaksi kohdassa 4089
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
        pituudet (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tierekisterin-tiedot
                   t/+kayttaja-jvh+ suolarajoitus)]
    (is (= 1753 (:pituus pituudet))) ;; Edelliseen testiin verrattuna ollaan lisätty pituutta yhdellä
    (is (= 3424 (:ajoratojen_pituus pituudet))))) ;; Jos ei otettaisi huomioon, että ajoradan pituus päättyy kohtaan 5752, pituudeksi tulisi 3511

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
    (is (= (+ (- osan-19-pituus aet) let) (:ajoratojen_pituus pituudet)))))

(deftest laske-tierekisteriosoitteelle-pituus6-onnistuu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        osan-125-pituus (+ (- 3170 3100) 2007)
        osan-126-pituus 3950
        pituus (+ osan-125-pituus osan-126-pituus)
        osan-125-apituus (+ 70 (* 2007 2))
        osan-126-apituus (* 3950 2)
        ajoradan-pituus (+ osan-125-apituus osan-126-apituus)
        tierekisteriosoite {:tie 12 :aosa 125 :aet 3100 :losa 126 :let 3950}
        ;; tie 12, osan 125 pituus on yht: 5177. Osa 125, koostuu kolmesta ajoradasta joka vaihtuu 0 -> 1+2 kohdasta:3170 . Sen jälkeen ajoratojen (1,2) pituus: 2007
        ;; tie 12, osan 126 pituus on yht: 6365. Osa 126, koostuu kahdesta ajoradasta 1,2
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
        vastaus (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                  :tierekisterin-tiedot
                  t/+kayttaja-jvh+ suolarajoitus)]
    (is (= pituus (:pituus vastaus)))
    (is (= ajoradan-pituus (:ajoratojen_pituus vastaus)))))


(deftest laske-tierekisteriosoitteelle-pituus7-onnistuu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        tierekisteriosoite {:tie 12 :aosa 219 :aet 151 :losa 219 :let 1492}
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
        pituudet (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tierekisterin-tiedot
                   t/+kayttaja-jvh+ suolarajoitus)]
    (is (= 1341 (:pituus pituudet)))
    (is (= 1341 (:ajoratojen_pituus pituudet)))))

;; Lasketaan tierekisteriosoitteelle pituus, joka koostu alkuostasta, joka alkaa pari osaa aiemmin, kuin loppuosa.
;; Ja jossa keskimmäiselle osalle ei ole olemassa pituutta ajorata taulussa
;; Myöhemmin on päädytty siihen, että tällaiset tilanteet ovat virheellisiä, eikä niille lasketa pituutta
;; Jätän tämän tähän, jos mieli muuttuu joskus taas.
#_(deftest laske-kahdelle-osalle-pituus-onnistuu-test
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
                     t/+kayttaja-jvh+ suolarajoitus)
          _ (println "pituudet: " (:body pituudet))]
      (is (= 4506 (:pituus pituudet)))
      ;; 20 tiellä osalla 4 on 3 ajorataa, joten pituuden pitäisi olla kolminkertainen
      (is (= 4926 (:ajoratojen_pituus pituudet)))))

(deftest laske-tierekisteriosoitteelle-pituus-epaonnistuu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        tierekisteriosoite {:tie 20 :aosa "makkara" :aet "lenkki" :losa "pihvi" :let "hiiligrilli"}
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
        pituudet (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tierekisterin-tiedot
                   t/+kayttaja-jvh+ suolarajoitus)]
    (is (str/includes? (str/trim (get-in pituudet [:vastaus])) "Tierekisteriosoite ei ole kokonaan annettu.") "Väärillä tiedoilla ei voi laskea pituutta.")))

(deftest tr-osoitteen-validointi-test
  (testing "Tieosoite on yksinkertainen ja on olemassa"
    (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
          tierekisteriosoite {:tie 130 :aosa 1 :aet 1 :losa 1 :let 100}
          suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
          vastaus (suolarajoitus-palvelu/tr-osoitteen-validointi (:db t/jarjestelma) suolarajoitus)]
      (is (= nil vastaus) "Tierekisteriä ei löydy tietokannasta.")))
  (testing "Tieosoite on katki, eli osa 9 puuttuu tietokannasta."
    (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
          tierekisteriosoite {:tie 130 :aosa 8 :aet 1 :losa 10 :let 100}
          suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
          vastaus (suolarajoitus-palvelu/tr-osoitteen-validointi (:db t/jarjestelma) suolarajoitus)]
      (is (= " Tierekisteriosoite ei ole yhtenäinen." vastaus) "Tierekisteriosoitte ei ole yhtenäinen."))))


(deftest laske-tierekisteriosoitteelle-pituus-vaarilla-tiedoilla-ei-onnistu-test
  (let [urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; 20 tien 4 osan pituus on tietokannassa 5752 metriä.
        ;; Sillä on 3 ajorataa, joiden pituudet on 0 = 4089 m, 1=1667, 2 = 1667 eli yhteensä 7423
        ;; Yritetään antaa kuitenkin virheellinen tieosoite, jossa loppuetäisyys on 6000 metriä.
        ;; Rajapinta palauttaa virheet ja opastaa antamaan oikeat arvot
        tierekisteriosoite {:tie 20 :aosa 4 :aet 0 :losa 4 :let 6000}
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id)
        pituudet (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                   :tierekisterin-tiedot
                   t/+kayttaja-jvh+ suolarajoitus)]
    (is (str/includes? (str/trim (get-in pituudet [:vastaus])) "Anna loppuosan 4 etäisyys 0 - 5756 väliltä") "Väärillä tiedoilla ei voi laskea pituutta.")))

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
                      {:tie 5 :aosa 115 :aet 200 :losa 115 :let 1623}
                      hk-alkuvuosi))

        ;; Rajoitus jonka alkuosa ja loppuosa ovat samoja, vain alkuet ja loppuet poikkeaa ja on vähä myöhemmin, kun edellinen
        rajoitus3 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                    :tallenna-suolarajoitus
                    t/+kayttaja-jvh+
                    (suolarajoitus-pohja
                      urakka-id
                      (:id t/+kayttaja-jvh+)
                      {:tie 25 :aosa 1 :aet 200 :losa 1 :let 950}
                      hk-alkuvuosi))

        rajoitus4 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                    :tallenna-suolarajoitus
                    t/+kayttaja-jvh+
                    (suolarajoitus-pohja
                      urakka-id
                      (:id t/+kayttaja-jvh+)
                      {:tie 25 :aosa 1 :aet 1030 :losa 1 :let 1224}
                      hk-alkuvuosi))

        ;; Simuloidaan tilannetta, jossa alkuosaltaan samalle rajoitusalueelle tehdään hyvin lähelle toinen
        ;; rajoitusalue. Tästä oli tuotannossa bugi. Eli tässä tehdään rajoitusalue, joka alkaa aosa 219 + aet 5862,
        ;; ja varmistetaan, että aosa 219 voi tallentaa rajoitusaluee, kunhan sen let ei ole 5862 isompi.
        rajoitus5 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                    :tallenna-suolarajoitus
                    t/+kayttaja-jvh+
                    (suolarajoitus-pohja
                      urakka-id
                      (:id t/+kayttaja-jvh+)
                      {:tie 12 :aosa 219 :aet 5862 :losa 221 :let 445}
                      hk-alkuvuosi))
        ]

    (testing "Varmistetaan, että samaa tierekisteriosoitetta ei voi käyttää muissa rajoituksissa"
      (let [tr-sama {:tie 25 :aosa 2 :aet 200 :losa 4 :let 2000}
            suolarajoitus-sama (merge perusrajoitus tr-sama)
            tr-tiedot-sama (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                             :tierekisterin-tiedot
                             t/+kayttaja-jvh+ suolarajoitus-sama)

            tr-sama2 {:tie 5 :aosa 115 :aet 200 :losa 115 :let 1623}
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

            tr-alku-sama3 {:tie 5 :aosa 115 :aet 1623 :losa 116 :let 1}
            suolarajoitus-alku-sama3 (merge perusrajoitus tr-alku-sama3)
            tr-tiedot-alku-sama3 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                   :tierekisterin-tiedot
                                   t/+kayttaja-jvh+ suolarajoitus-alku-sama3)

            tr-alku-sama4 {:tie 5 :aosa 115 :aet 1623 :losa 116 :let 201}
            suolarajoitus-alku-sama4 (merge perusrajoitus tr-alku-sama4)
            tr-tiedot-alku-sama4 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                   :tierekisterin-tiedot
                                   t/+kayttaja-jvh+ suolarajoitus-alku-sama4)

            ;; Varmistaa rajoitus5:sen toiminnan, kun tierekisteriosoitteet on samat
            tr-loppu-eri {:tie 12 :aosa 219 :aet 151 :losa 219 :let 1492}
            suolarajoitus-loppu-eri (merge perusrajoitus tr-loppu-eri)
            tr-tiedot-loppu-eri (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                  :tierekisterin-tiedot
                                  t/+kayttaja-jvh+ suolarajoitus-loppu-eri)

            ;; Tämän tallennus ei saa onnistua, koska rajoitus5 on tälle päällekäinen
            tr-loppu-sama {:tie 12 :aosa 219 :aet 1500 :losa 221 :let 100}
            suolarajoitus-loppu-sama (merge perusrajoitus tr-loppu-sama)
            tr-tiedot-loppu-sama (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                   :tierekisterin-tiedot
                                   t/+kayttaja-jvh+ suolarajoitus-loppu-sama)]
        (is (= 400 (:status tr-tiedot-sama)) "Tierekisteriosoitteessa on jo rajoitus.")
        (is (= 400 (:status tr-tiedot-sama2)) "Tierekisteriosoitteessa on jo rajoitus.")
        (is (= {:pituus 1, :ajoratojen_pituus 1, :pohjavesialueet ()} tr-tiedot-alku-sama) "Alku sama, mutta saa tallentaa.")
        (is (= 5511 (:pituus tr-tiedot-alku-sama2)) "Alku sama, mutta saa tallentaa.")
        (is (= {:pituus 1, :ajoratojen_pituus 1, :pohjavesialueet ()} tr-tiedot-alku-sama3) "Alku sama, kuin yhden loppu, eli saa tallentaa.")
        (is (= {:pituus 201, :ajoratojen_pituus 201, :pohjavesialueet ()} tr-tiedot-alku-sama4) "Alku sama, kuin yhden loppu, eli saa tallentaa.")
        (is (= 1341 (:pituus tr-tiedot-loppu-eri)) "Alku myöhemmin, loppuu eri osaan, saa tallentaa.")
        (is (= 400 (:status tr-tiedot-loppu-sama)) "Tierekisteriosoitteessa on jo rajoitus")))

    (testing "Tierekisteri on olemassa olevan välissä"
      (let [tr {:tie 25 :aosa 3 :aet 200 :losa 3 :let 2000}
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

            tr-loppu-keskella3 {:tie 5 :aosa 115 :aet 1 :losa 115 :let 300}
            suolarajoitus-loppu-keskella3 (merge perusrajoitus tr-loppu-keskella3)
            tr-tiedot-loppu-keskella3 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                        :tierekisterin-tiedot
                                        t/+kayttaja-jvh+ suolarajoitus-loppu-keskella3)

            tr-loppu-keskella4 {:tie 5 :aosa 19 :aet 1 :losa 20 :let 300}
            suolarajoitus-loppu-keskella4 (merge perusrajoitus tr-loppu-keskella4)
            tr-tiedot-loppu-keskella4 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                        :tierekisterin-tiedot
                                        t/+kayttaja-jvh+ suolarajoitus-loppu-keskella4)]
        (is (= 400 (:status tr-tiedot-loppu-keskella)) "Loppu keskellä, eikä saa tallentaa")
        (is (= 400 (:status tr-tiedot-loppu-keskella2)) "Loppu keskellä, eikä saa tallentaa")
        (is (= 400 (:status tr-tiedot-loppu-keskella3)) "Loppu keskellä, eikä saa tallentaa")
        (is (= 400 (:status tr-tiedot-loppu-keskella4)) "Loppu keskellä, eikä saa tallentaa")))

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

            tr-alku-keskella3 {:tie 5 :aosa 115 :aet 300 :losa 115 :let 3001}
            suolarajoitus-alku-keskella3 (merge perusrajoitus tr-alku-keskella3)
            tr-tiedot-alku-keskella3 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                       :tierekisterin-tiedot
                                       t/+kayttaja-jvh+ suolarajoitus-alku-keskella3)]
        (is (= 400 (:status tr-tiedot-alku-keskella)) "Alku keskellä, eikä saa tallentaa")
        (is (= 400 (:status tr-tiedot-alku-keskella2)) "Alku keskellä, eikä saa tallentaa")
        (is (= 400 (:status tr-tiedot-alku-keskella3)) "Alku keskellä, eikä saa tallentaa")))

    (testing "Varmisetaan, että tallennus onnistuu, kun annettu tierekisteri ei ole lähelläkään olemassaolevia rajoituksia"
      (let [tr-ei-lahellakaan {:tie 25 :aosa 11 :aet 1 :losa 11 :let 3001}
            suolarajoitus-ei-lahella (merge perusrajoitus tr-ei-lahellakaan)
            tr-tiedot-ei-lahella (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                   :tierekisterin-tiedot
                                   t/+kayttaja-jvh+ suolarajoitus-ei-lahella)

            tr-ei-lahellakaan2 {:tie 25 :aosa 11 :aet 1 :losa 12 :let 100}
            suolarajoitus-ei-lahella2 (merge perusrajoitus tr-ei-lahellakaan2)
            tr-tiedot-ei-lahella2 (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                    :tierekisterin-tiedot
                                    t/+kayttaja-jvh+ suolarajoitus-ei-lahella2)]
        (is (= {:pituus 3000, :ajoratojen_pituus 3000,
                :pohjavesialueet '({:nimi "Björknäs", :tunnus "183551"}
                                   {:nimi "Ekerö", :tunnus "160651"})} tr-tiedot-ei-lahella) "Ei lähelläkään muita rajoituksia.")
        (is (= 5969 (:pituus tr-tiedot-ei-lahella2)) "Ei lähelläkään muita rajoituksia.")))

    (testing "Tierekisteri olemassaolevan ympärille"
      (let [tr-ymparilla {:tie 25 :aosa 1 :aet 2000 :losa 400 :let 2001}
            suolarajoitus-ymparilla (merge perusrajoitus tr-ymparilla)
            tr-tiedot-ymparilla (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                                  :tierekisterin-tiedot
                                  t/+kayttaja-jvh+ suolarajoitus-ymparilla)

            tr-ymparilla2 {:tie 5 :aosa 115 :aet 1 :losa 115 :let 4000}
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
       :kopioidaan-tuleville-vuosille? true})
    (poista-suolarajoitus
      {:rajoitusalue_id (:rajoitusalue_id rajoitus3)
       :hoitokauden-alkuvuosi hk-alkuvuosi
       :urakka_id urakka-id
       :kopioidaan-tuleville-vuosille? true})
    (poista-suolarajoitus
      {:rajoitusalue_id (:rajoitusalue_id rajoitus4)
       :hoitokauden-alkuvuosi hk-alkuvuosi
       :urakka_id urakka-id
       :kopioidaan-tuleville-vuosille? true})
    (poista-suolarajoitus
      {:rajoitusalue_id (:rajoitusalue_id rajoitus5)
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
        tierekisteriosoite {:tie 4 :aosa 101 :aet 8 :losa 101 :let 10}
        suolarajoitus (assoc tierekisteriosoite :urakka-id urakka-id :hoitokauden-alkuvuosi hk-alkuvuosi)
        tierekisterin-tiedot (t/kutsu-palvelua (:http-palvelin t/jarjestelma)
                               :tierekisterin-tiedot
                               t/+kayttaja-jvh+ suolarajoitus)
        odotettu-tulos {:pituus 2, :ajoratojen_pituus 4, :pohjavesialueet ()}

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
        toimenpidekoodi (t/q-map (str "select id from tehtava where suunnitteluyksikko = 'kuivatonnia' AND suoritettavatehtava = 'suolaus'"))
        suolaus-tehtava-id (:id (first toimenpidekoodi))

        ;; Lisää tehtävälle suunniteltu määrä
        _ (t/u (str (format "insert into urakka_tehtavamaara (urakka, \"hoitokauden-alkuvuosi\", tehtava, maara) values
        (%s, %s, %s, %s)" urakka-id hk-alkuvuosi suolaus-tehtava-id talvisuolaraja)))

        ;; Merkitse urakan sopimus tallennetuksi
        _ (t/u (str (format "insert into sopimuksen_tehtavamaarat_tallennettu (urakka, tallennettu) values (%s,true)"
                      urakka-id)))]))

(deftest tallenna-ja-hae-suolarajoituksen-kokonaiskayttoraja-onnistuu-mhu-test
  (let [ ;; Kokonais talvisuolaraja on tallennettu tehtäviin ja määriin tehtävälle "Suolaus"
        ;; Joten lisätään annetulle urakalle urakka_tehtavamaarat tauluun suunniteltuja määriä
        talvisuolaraja 1000M
        sanktio_ylittavalta_tonnilta 100000M
        urakka-id (t/hae-urakan-id-nimella "Iin MHU 2021-2026")
        hk-alkuvuosi 2022

        ;; Siivotaan kanta alkuun
        _ (t/u (str "DELETE from suolasakko WHERE urakka = " urakka-id))
        _ (t/u (str (format "DELETE from urakka_tehtavamaara
                            WHERE urakka = %s
                              AND \"hoitokauden-alkuvuosi\" = %s
                              AND maara = %s" urakka-id hk-alkuvuosi talvisuolaraja)))
        _ (t/u (str (format "DELETE from sopimuksen_tehtavamaarat_tallennettu
                            WHERE urakka = %s" urakka-id)))

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
                              AND maara = %s" urakka-id hk-alkuvuosi talvisuolaraja)))
        _ (t/u (str (format "DELETE from sopimuksen_tehtavamaarat_tallennettu
                            WHERE urakka = %s" urakka-id)))]

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
        sanktio-ylittavalta-tonnilta 5000M ;; euroa
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
        sanktio-ylittavalta-tonnilta 5000M ;; euroa
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

(deftest tierekisteri-muokattu?-toimii
  (testing "Sama tierekisteri"
    (let [uusi-rajoitusalue {:tie 1 :aosa 1 :aet 1 :losa 2 :let 2}
          vanha-rajoitusalue {:tie 1 :aosa 1 :aet 1 :losa 2 :let 2}]
      (is (false? (suolarajoitus-palvelu/tierekisteri-muokattu? uusi-rajoitusalue vanha-rajoitusalue)))))
  (testing "Muuttunut tierekisteri :: tie"
    (let [uusi-rajoitusalue {:tie 1 :aosa 1 :aet 1 :losa 2 :let 2}
          vanha-rajoitusalue {:tie 2 :aosa 1 :aet 1 :losa 2 :let 2}]
      (is (true? (suolarajoitus-palvelu/tierekisteri-muokattu? uusi-rajoitusalue vanha-rajoitusalue)))))
  (testing "Muuttunut tierekisteri :: aosa"
    (let [uusi-rajoitusalue {:tie 1 :aosa 2 :aet 1 :losa 2 :let 2}
          vanha-rajoitusalue {:tie 1 :aosa 1 :aet 1 :losa 2 :let 2}]
      (is (true? (suolarajoitus-palvelu/tierekisteri-muokattu? uusi-rajoitusalue vanha-rajoitusalue)))))
  (testing "Muuttunut tierekisteri :: aet"
    (let [uusi-rajoitusalue {:tie 1 :aosa 1 :aet 2 :losa 2 :let 2}
          vanha-rajoitusalue {:tie 1 :aosa 1 :aet 1 :losa 2 :let 2}]
      (is (true? (suolarajoitus-palvelu/tierekisteri-muokattu? uusi-rajoitusalue vanha-rajoitusalue)))))
  (testing "Muuttunut tierekisteri :: losa"
    (let [uusi-rajoitusalue {:tie 1 :aosa 1 :aet 1 :losa 3 :let 2}
          vanha-rajoitusalue {:tie 1 :aosa 1 :aet 1 :losa 2 :let 2}]
      (is (true? (suolarajoitus-palvelu/tierekisteri-muokattu? uusi-rajoitusalue vanha-rajoitusalue)))))
  (testing "Muuttunut tierekisteri :: let"
    (let [uusi-rajoitusalue {:tie 1 :aosa 1 :aet 1 :losa 2 :let 3}
          vanha-rajoitusalue {:tie 1 :aosa 1 :aet 1 :losa 2 :let 2}]
      (is (true? (suolarajoitus-palvelu/tierekisteri-muokattu? uusi-rajoitusalue vanha-rajoitusalue))))))

(deftest varmista-monet-osoitteet1
  (let [osoitteet '({:tie 849 :aosa 3 :aet 2675 :losa 3 :let 5600}
                    {:tie 4 :aosa 417 :aet 800 :losa 417 :let 1247}
                    {:tie 1 :aosa 10 :aet 3495 :losa 11 :let 5390}
                    {:tie 1 :aosa 10 :aet 3495 :losa 11 :let 5390}
                    {:tie 1 :aosa 10 :aet 3495 :losa 11 :let 5390}
                    {:tie 16245 :aosa 1 :aet 13239 :losa 1 :let 14581}
                    {:tie 560 :aosa 5 :aet 5658 :losa 5 :let 7307}
                    {:tie 560 :aosa 5 :aet 691 :losa 5 :let 2570}
                    {:tie 552 :aosa 2 :aet 8693 :losa 4 :let 373}
                    {:tie 77 :aosa 26 :aet 2060 :losa 27 :let 145}
                    {:tie 551 :aosa 16 :aet 2997 :losa 16 :let 3776}
                    {:tie 545 :aosa 3 :aet 6437 :losa 4 :let 1060}
                    {:tie 545 :aosa 1 :aet 601 :losa 1 :let 1552}
                    {:tie 543 :aosa 1 :aet 2893 :losa 2 :let 6314}
                    {:tie 543 :aosa 1 :aet 305 :losa 1 :let 2070}
                    {:tie 69 :aosa 16 :aet 1906 :losa 16 :let 3198}
                    {:tie 9 :aosa 323 :aet 3579 :losa 323 :let 4600}
                    {:tie 9 :aosa 319 :aet 3810 :losa 319 :let 5158}
                    {:tie 78 :aosa 203 :aet 1795 :losa 203 :let 2385}
                    {:tie 78 :aosa 201 :aet 1572 :losa 201 :let 3504}
                    {:tie 78 :aosa 123 :aet 2778 :losa 123 :let 3436}
                    {:tie 20 :aosa 22 :aet 3400 :losa 25 :let 105}
                    {:tie 20 :aosa 18 :aet 4611 :losa 22 :let 40}
                    {:tie 20 :aosa 13 :aet 100 :losa 13 :let 2240}
                    {:tie 20 :aosa 14 :aet 7860 :losa 14 :let 11200}
                    {:tie 3052 :aosa 1 :aet 2895 :losa 1 :let 4205}
                    {:tie 3052 :aosa 1 :aet 0 :losa 1 :let 1366}
                    {:tie 130 :aosa 16 :aet 4745 :losa 16 :let 9478}
                    {:tie 130 :aosa 15 :aet 1487 :losa 15 :let 3856}
                    {:tie 290 :aosa 12 :aet 4619 :losa 13 :let 28}
                    {:tie 290 :aosa 12 :aet 941 :losa 12 :let 2412}
                    {:tie 130 :aosa 13 :aet 13926 :losa 13 :let 15725}
                    {:tie 130 :aosa 13 :aet 6567 :losa 13 :let 8224}
                    {:tie 130 :aosa 12 :aet 1495 :losa 12 :let 2963}
                    {:tie 12 :aosa 217 :aet 4520 :losa 217 :let 6441}
                    {:tie 12 :aosa 217 :aet 1945 :losa 217 :let 2975}
                    {:tie 12 :aosa 215 :aet 1503 :losa 215 :let 4862}
                    {:tie 12 :aosa 214 :aet 4022 :losa 215 :let 440}
                    {:tie 12 :aosa 213 :aet 5127 :losa 213 :let 7186}
                    {:tie 10 :aosa 31 :aet 946 :losa 31 :let 2911}
                    {:tie 10 :aosa 23 :aet 5701 :losa 23 :let 7393}
                    {:tie 10 :aosa 21 :aet 7295 :losa 21 :let 8381}
                    {:tie 10 :aosa 25 :aet 3861 :losa 25 :let 4451}
                    {:tie 13609 :aosa 1 :aet 0 :losa 1 :let 1900}
                    {:tie 13621 :aosa 2 :aet 0 :losa 2 :let 2632}
                    {:tie 23622 :aosa 1 :aet 0 :losa 1 :let 85}
                    {:tie 11505 :aosa 1 :aet 5714 :losa 1 :let 9145}
                    {:tie 11355 :aosa 1 :aet 0 :losa 1 :let 5350}
                    {:tie 2892 :aosa 1 :aet 1568 :losa 1 :let 1885}
                    {:tie 1430 :aosa 1 :aet 4372 :losa 2 :let 0}
                    {:tie 1403 :aosa 3 :aet 1229 :losa 3 :let 5129}
                    {:tie 1378 :aosa 1 :aet 0 :losa 1 :let 552}
                    {:tie 1321 :aosa 2 :aet 8469 :losa 2 :let 9736}
                    {:tie 290 :aosa 6 :aet 1275 :losa 6 :let 4348}
                    {:tie 290 :aosa 4 :aet 3281 :losa 6 :let 1275}
                    {:tie 290 :aosa 3 :aet 0 :losa 4 :let 3183}
                    {:tie 143 :aosa 1 :aet 3995 :losa 1 :let 5830}
                    {:tie 3 :aosa 112 :aet 7059 :losa 112 :let 7899}
                    {:tie 3 :aosa 110 :aet 6286 :losa 111 :let 1081}
                    {:tie 13822 :aosa 1 :aet 0 :losa 1 :let 430}
                    {:tie 11591 :aosa 1 :aet 0 :losa 1 :let 1008}
                    {:tie 11299 :aosa 1 :aet 0 :losa 1 :let 6590}
                    {:tie 2834 :aosa 1 :aet 0 :losa 1 :let 4133}
                    {:tie 1379 :aosa 1 :aet 0 :losa 1 :let 2706}
                    {:tie 1321 :aosa 2 :aet 2205 :losa 2 :let 5348}
                    {:tie 1311 :aosa 4 :aet 750 :losa 4 :let 2096}
                    {:tie 139 :aosa 3 :aet 414 :losa 3 :let 626}
                    {:tie 139 :aosa 2 :aet 4996 :losa 3 :let 414}
                    {:tie 132 :aosa 4 :aet 5383 :losa 5 :let 3039}
                    {:tie 130 :aosa 9 :aet 6222 :losa 10 :let 2287}
                    {:tie 130 :aosa 7 :aet 2889 :losa 7 :let 3933}
                    {:tie 130 :aosa 6 :aet 4794 :losa 7 :let 1226}
                    {:tie 130 :aosa 5 :aet 2092 :losa 5 :let 6175}
                    {:tie 130 :aosa 6 :aet 1214 :losa 6 :let 2971}
                    {:tie 130 :aosa 5 :aet 2092 :losa 5 :let 6175}
                    {:tie 54 :aosa 14 :aet 597 :losa 14 :let 3232}
                    {:tie 54 :aosa 9 :aet 4212 :losa 10 :let 2355}
                    {:tie 54 :aosa 6 :aet 3553 :losa 6 :let 5881}
                    {:tie 21861 :aosa 1 :aet 0 :losa 1 :let 145}
                    {:tie 21312 :aosa 23 :aet 0 :losa 23 :let 746}
                    {:tie 21312 :aosa 12 :aet 0 :losa 12 :let 1588}
                    {:tie 45 :aosa 10 :aet 4518 :losa 10 :let 4641}
                    {:tie 45 :aosa 10 :aet 4518 :losa 10 :let 4641}
                    {:tie 45 :aosa 10 :aet 2066 :losa 10 :let 4518}
                    {:tie 45 :aosa 7 :aet 7279 :losa 9 :let 109}
                    {:tie 45 :aosa 10 :aet 984 :losa 10 :let 2066}
                    {:tie 45 :aosa 10 :aet 984 :losa 10 :let 2066}
                    {:tie 45 :aosa 10 :aet 984 :losa 10 :let 2066}
                    {:tie 45 :aosa 7 :aet 7133 :losa 7 :let 7279}
                    {:tie 45 :aosa 6 :aet 692 :losa 6 :let 4610}
                    {:tie 45 :aosa 6 :aet 0 :losa 6 :let 692}
                    {:tie 25 :aosa 34 :aet 4488 :losa 34 :let 5604}
                    {:tie 25 :aosa 32 :aet 844 :losa 32 :let 2845}
                    {:tie 25 :aosa 31 :aet 2973 :losa 32 :let 844}
                    {:tie 25 :aosa 29 :aet 4623 :losa 31 :let 149}
                    {:tie 25 :aosa 28 :aet 5472 :losa 29 :let 4623}
                    {:tie 25 :aosa 25 :aet 4858 :losa 25 :let 5308}
                    {:tie 577 :aosa 2 :aet 1070 :losa 3 :let 270}
                    {:tie 72 :aosa 22 :aet 8960 :losa 22 :let 13420}
                    {:tie 23 :aosa 315 :aet 3315 :losa 315 :let 6214}
                    {:tie 23 :aosa 305 :aet 1815 :losa 305 :let 4800}
                    {:tie 7173 :aosa 1 :aet 1002 :losa 1 :let 3450}
                    {:tie 8 :aosa 303 :aet 630 :losa 303 :let 1374}
                    {:tie 9 :aosa 319 :aet 3810 :losa 319 :let 5158}
                    {:tie 5 :aosa 343 :aet 2175 :losa 343 :let 5160}
                    {:tie 5 :aosa 337 :aet 7945 :losa 339 :let 2132}
                    {:tie 5 :aosa 330 :aet 1596 :losa 330 :let 1902}
                    {:tie 5 :aosa 320 :aet 3840 :losa 320 :let 4438}
                    {:tie 76 :aosa 4 :aet 975 :losa 5 :let 5022}
                    {:tie 76 :aosa 3 :aet 3340 :losa 3 :let 4500}
                    {:tie 8991 :aosa 1 :aet 0 :losa 1 :let 820}
                    {:tie 899 :aosa 5 :aet 2538 :losa 6 :let 1611}
                    {:tie 76 :aosa 2 :aet 612 :losa 3 :let 2300}
                    {:tie 22 :aosa 19 :aet 4700 :losa 20 :let 6500}
                    {:tie 8990 :aosa 1 :aet 0 :losa 1 :let 1249}
                    {:tie 5 :aosa 235 :aet 11160 :losa 304 :let 900}
                    {:tie 75 :aosa 1 :aet 650 :losa 1 :let 750}
                    {:tie 77 :aosa 35 :aet 1556 :losa 35 :let 2505}
                    {:tie 77 :aosa 34 :aet 6652 :losa 35 :let 1556}
                    {:tie 77 :aosa 34 :aet 356 :losa 34 :let 1083}
                    {:tie 77 :aosa 33 :aet 306 :losa 33 :let 762}
                    {:tie 13609 :aosa 1 :aet 0 :losa 1 :let 1900}
                    {:tie 290 :aosa 6 :aet 1275 :losa 6 :let 4348}
                    {:tie 290 :aosa 4 :aet 3281 :losa 6 :let 1275}
                    {:tie 290 :aosa 3 :aet 0 :losa 4 :let 3183}
                    {:tie 143 :aosa 1 :aet 3995 :losa 1 :let 5830}
                    {:tie 3 :aosa 112 :aet 7059 :losa 112 :let 7899}
                    {:tie 77 :aosa 35 :aet 2505 :losa 35 :let 5405}
                    {:tie 75 :aosa 1 :aet 0 :losa 1 :let 650}
                    {:tie 760 :aosa 1 :aet 1431 :losa 1 :let 5260}
                    {:tie 88 :aosa 10 :aet 2245 :losa 10 :let 3538}
                    {:tie 88 :aosa 7 :aet 5497 :losa 9 :let 6701}
                    {:tie 88 :aosa 3 :aet 5611 :losa 3 :let 11298}
                    {:tie 88 :aosa 5 :aet 4338 :losa 6 :let 2981}
                    {:tie 86 :aosa 17 :aet 7198 :losa 20 :let 1310}
                    {:tie 86 :aosa 25 :aet 2131 :losa 25 :let 3414}
                    {:tie 28 :aosa 20 :aet 2641 :losa 21 :let 1402}
                    {:tie 8 :aosa 415 :aet 530 :losa 416 :let 2000}
                    {:tie 8 :aosa 412 :aet 15 :losa 412 :let 2207}
                    {:tie 8 :aosa 430 :aet 2869 :losa 431 :let 2188}
                    {:tie 3 :aosa 210 :aet 2600 :losa 211 :let 1250}
                    {:tie 66 :aosa 17 :aet 2300 :losa 18 :let 2300}
                    {:tie 65 :aosa 9 :aet 3882 :losa 9 :let 5496}
                    {:tie 14137 :aosa 1 :aet 0 :losa 1 :let 1627}
                    {:tie 363 :aosa 1 :aet 960 :losa 2 :let 3049}
                    {:tie 363 :aosa 1 :aet 0 :losa 1 :let 960}
                    {:tie 314 :aosa 7 :aet 1302 :losa 7 :let 2689}
                    {:tie 314 :aosa 4 :aet 3079 :losa 6 :let 998}
                    {:tie 314 :aosa 4 :aet 0 :losa 4 :let 1078}
                    {:tie 314 :aosa 2 :aet 5656 :losa 2 :let 14273}
                    {:tie 314 :aosa 2 :aet 4508 :losa 2 :let 5403}
                    {:tie 314 :aosa 1 :aet 897 :losa 1 :let 4574}
                    {:tie 313 :aosa 4 :aet 8500 :losa 4 :let 8751}
                    {:tie 313 :aosa 4 :aet 4934 :losa 4 :let 7398}
                    {:tie 313 :aosa 3 :aet 3241 :losa 4 :let 330}
                    {:tie 313 :aosa 1 :aet 5664 :losa 3 :let 2319}
                    {:tie 313 :aosa 1 :aet 0 :losa 1 :let 5664}
                    {:tie 140 :aosa 27 :aet 2950 :losa 28 :let 840}
                    {:tie 4231 :aosa 1 :aet 336 :losa 1 :let 694}
                    {:tie 15071 :aosa 1 :aet 1459 :losa 1 :let 4681}
                    {:tie 410 :aosa 1 :aet 556 :losa 2 :let 1970}
                    {:tie 140 :aosa 33 :aet 2123 :losa 33 :let 2540}
                    {:tie 140 :aosa 30 :aet 1450 :losa 30 :let 2932}
                    {:tie 140 :aosa 33 :aet 290 :losa 33 :let 2123}
                    {:tie 140 :aosa 33 :aet 0 :losa 33 :let 290}
                    {:tie 4 :aosa 218 :aet 1407 :losa 219 :let 4559}
                    {:tie 4 :aosa 214 :aet 5484 :losa 216 :let 2005}
                    {:tie 4 :aosa 214 :aet 3997 :losa 214 :let 5484}
                    {:tie 4 :aosa 214 :aet 206 :losa 214 :let 1175}
                    {:tie 4 :aosa 207 :aet 220 :losa 207 :let 3119}
                    {:tie 14091 :aosa 1 :aet 0 :losa 1 :let 1607}
                    {:tie 3138 :aosa 1 :aet 0 :losa 1 :let 1506}
                    {:tie 3134 :aosa 1 :aet 0 :losa 1 :let 1247}
                    {:tie 312 :aosa 1 :aet 11124 :losa 3 :let 5546}
                    {:tie 312 :aosa 1 :aet 9247 :losa 1 :let 10493}
                    {:tie 312 :aosa 1 :aet 8361 :losa 1 :let 8654}
                    {:tie 312 :aosa 1 :aet 7631 :losa 1 :let 8361}
                    {:tie 312 :aosa 1 :aet 4771 :losa 1 :let 7329}
                    {:tie 312 :aosa 1 :aet 0 :losa 1 :let 845}
                    {:tie 12 :aosa 224 :aet 20210 :losa 224 :let 26205}
                    {:tie 12 :aosa 224 :aet 4677 :losa 224 :let 7415}
                    {:tie 24 :aosa 6 :aet 26 :losa 6 :let 4160}
                    {:tie 24 :aosa 4 :aet 6842 :losa 4 :let 9859}
                    {:tie 14070 :aosa 1 :aet 0 :losa 1 :let 3389}
                    {:tie 2955 :aosa 1 :aet 0 :losa 1 :let 1726}
                    {:tie 317 :aosa 7 :aet 4126 :losa 7 :let 4884}
                    {:tie 295 :aosa 6 :aet 2079 :losa 6 :let 2211}
                    {:tie 295 :aosa 6 :aet 1633 :losa 6 :let 2079}
                    {:tie 53 :aosa 5 :aet 7892 :losa 5 :let 9013}
                    {:tie 53 :aosa 3 :aet 13746 :losa 3 :let 14016}
                    {:tie 53 :aosa 3 :aet 12923 :losa 3 :let 13746}
                    {:tie 53 :aosa 3 :aet 9276 :losa 3 :let 9988}
                    {:tie 53 :aosa 3 :aet 5375 :losa 3 :let 8177}
                    {:tie 53 :aosa 3 :aet 2463 :losa 3 :let 5112}
                    {:tie 2955 :aosa 1 :aet 2951 :losa 1 :let 4446}
                    {:tie 2955 :aosa 1 :aet 1726 :losa 1 :let 2951}
                    {:tie 140 :aosa 24 :aet 3285 :losa 25 :let 935}
                    {:tie 54 :aosa 16 :aet 5513 :losa 16 :let 6136}
                    {:tie 24 :aosa 8 :aet 9118 :losa 10 :let 1211}
                    {:tie 24 :aosa 8 :aet 4711 :losa 8 :let 8675}
                    )]
    (map
      #(suolarajoitus-palvelu/tr-osoitteen-validointi (:db t/jarjestelma) %)
      osoitteet)
    ))

(defn varmista-monet-osoitteet2 [db]
  (let [osoitteet '({:tie 24 :aosa 8 :aet 2857 :losa 8 :let 4711}
                    {:tie 167 :aosa 5 :aet 2466 :losa 5 :let 4613}
                    {:tie 167 :aosa 5 :aet 2108 :losa 5 :let 2466}
                    {:tie 167 :aosa 4 :aet 1835 :losa 4 :let 3980}
                    {:tie 24 :aosa 2 :aet 3760 :losa 2 :let 4530}
                    {:tie 12 :aosa 219 :aet 5862 :losa 221 :let 455}
                    {:tie 12 :aosa 219 :aet 151 :losa 219 :let 1492}
                    {:tie 4 :aosa 203 :aet 620 :losa 203 :let 3673}
                    {:tie 4 :aosa 120 :aet 6029 :losa 201 :let 605}
                    {:tie 4 :aosa 120 :aet 462 :losa 120 :let 2788}
                    {:tie 11505 :aosa 1 :aet 2979 :losa 1 :let 9145}
                    {:tie 8 :aosa 228 :aet 4173 :losa 229 :let 873}
                    {:tie 8991 :aosa 1 :aet 0 :losa 1 :let 820}
                    {:tie 899 :aosa 5 :aet 2538 :losa 6 :let 1611}
                    {:tie 76 :aosa 2 :aet 612 :losa 3 :let 2300}
                    {:tie 22 :aosa 19 :aet 4700 :losa 20 :let 6500}
                    {:tie 5 :aosa 235 :aet 11160 :losa 304 :let 900}
                    {:tie 88 :aosa 16 :aet 3500 :losa 19 :let 1000}
                    {:tie 88 :aosa 14 :aet 100 :losa 14 :let 8545}
                    {:tie 88 :aosa 10 :aet 3538 :losa 10 :let 9411}
                    {:tie 58 :aosa 59 :aet 2984 :losa 59 :let 5728}
                    {:tie 760 :aosa 10 :aet 1468 :losa 10 :let 4757}
                    {:tie 760 :aosa 3 :aet 763 :losa 5 :let 12413}
                    {:tie 4 :aosa 220 :aet 1100 :losa 220 :let 2800}
                    {:tie 23566 :aosa 14 :aet 0 :losa 14 :let 470}
                    {:tie 23566 :aosa 25 :aet 0 :losa 25 :let 554}
                    {:tie 65 :aosa 1 :aet 0 :losa 1 :let 2050}
                    {:tie 12 :aosa 125 :aet 3100 :losa 126 :let 3950}
                    {:tie 23675 :aosa 12 :aet 0 :losa 12 :let 248}
                    {:tie 23674 :aosa 12 :aet 0 :losa 12 :let 123}
                    {:tie 23898 :aosa 12 :aet 0 :losa 12 :let 293}
                    {:tie 12 :aosa 126 :aet 5650 :losa 127 :let 2500}
                    {:tie 23643 :aosa 85 :aet 0 :losa 85 :let 189}
                    {:tie 23643 :aosa 82 :aet 0 :losa 82 :let 270}
                    {:tie 23643 :aosa 48 :aet 0 :losa 48 :let 207}
                    {:tie 23643 :aosa 76 :aet 0 :losa 76 :let 215}
                    {:tie 23643 :aosa 73 :aet 0 :losa 73 :let 293}
                    {:tie 23643 :aosa 17 :aet 0 :losa 17 :let 214}
                    {:tie 9 :aosa 121 :aet 6400 :losa 123 :let 590}
                    {:tie 18681 :aosa 1 :aet 0 :losa 1 :let 1668}
                    {:tie 18679 :aosa 1 :aet 1370 :losa 1 :let 2268}
                    {:tie 18637 :aosa 1 :aet 775 :losa 1 :let 9030}
                    {:tie 847 :aosa 1 :aet 0 :losa 2 :let 1650}
                    {:tie 846 :aosa 1 :aet 0 :losa 1 :let 1016}
                    {:tie 816 :aosa 1 :aet 0 :losa 1 :let 3278}
                    {:tie 815 :aosa 3 :aet 1903 :losa 3 :let 2570}
                    {:tie 20 :aosa 9 :aet 3882 :losa 9 :let 4757}
                    {:tie 20 :aosa 6 :aet 4329 :losa 6 :let 4862}
                    {:tie 20 :aosa 4 :aet 2443 :losa 4 :let 3578}
                    {:tie 62 :aosa 11 :aet 3000 :losa 11 :let 3913}
                    {:tie 14 :aosa 4 :aet 2978 :losa 4 :let 3512}
                    {:tie 5 :aosa 143 :aet 1650 :losa 143 :let 3690}
                    {:tie 5 :aosa 136 :aet 550 :losa 136 :let 845}
                    {:tie 471 :aosa 8 :aet 1706 :losa 8 :let 2124}
                    {:tie 471 :aosa 2 :aet 5146 :losa 2 :let 5789}
                    {:tie 479 :aosa 5 :aet 4744 :losa 5 :let 5770}
                    {:tie 479 :aosa 5 :aet 3604 :losa 5 :let 4116}
                    {:tie 479 :aosa 4 :aet 4857 :losa 4 :let 5085}
                    {:tie 479 :aosa 4 :aet 2023 :losa 4 :let 3145}
                    {:tie 479 :aosa 2 :aet 2591 :losa 3 :let 311}
                    {:tie 479 :aosa 1 :aet 363 :losa 1 :let 525}
                    {:tie 479 :aosa 1 :aet 0 :losa 1 :let 240}
                    {:tie 71 :aosa 4 :aet 697 :losa 4 :let 1226}
                    {:tie 14 :aosa 26 :aet 60 :losa 26 :let 979}
                    {:tie 14 :aosa 22 :aet 6551 :losa 22 :let 7719}
                    {:tie 14 :aosa 22 :aet 3996 :losa 22 :let 5138}
                    {:tie 14 :aosa 17 :aet 2666 :losa 17 :let 3976}
                    {:tie 14 :aosa 21 :aet 4508 :losa 22 :let 305}
                    {:tie 849 :aosa 3 :aet 2675 :losa 3 :let 5600}
                    {:tie 4 :aosa 417 :aet 800 :losa 417 :let 1247}
                    {:tie 77 :aosa 6 :aet 169 :losa 6 :let 919}
                    {:tie 648 :aosa 4 :aet 4124 :losa 4 :let 5261}
                    {:tie 13 :aosa 132 :aet 109 :losa 132 :let 3077}
                    {:tie 13 :aosa 131 :aet 3576 :losa 131 :let 6113}
                    {:tie 13 :aosa 129 :aet 6645 :losa 131 :let 3576}
                    {:tie 13 :aosa 129 :aet 236 :losa 129 :let 4782}
                    {:tie 13 :aosa 128 :aet 1168 :losa 128 :let 2004}
                    {:tie 13 :aosa 126 :aet 3381 :losa 127 :let 1749}
                    {:tie 13 :aosa 126 :aet 1158 :losa 126 :let 3381}
                    {:tie 13 :aosa 124 :aet 6373 :losa 125 :let 3282}
                    {:tie 77 :aosa 1 :aet 0 :losa 1 :let 448}
                    {:tie 12 :aosa 207 :aet 2597 :losa 208 :let 699}
                    {:tie 12 :aosa 206 :aet 2538 :losa 206 :let 4696}
                    {:tie 12 :aosa 206 :aet 2249 :losa 206 :let 2425}
                    {:tie 12 :aosa 204 :aet 3806 :losa 205 :let 2505}
                    {:tie 12 :aosa 204 :aet 1425 :losa 204 :let 3806}
                    {:tie 6 :aosa 329 :aet 6622 :losa 330 :let 2195}
                    {:tie 6 :aosa 329 :aet 2359 :losa 329 :let 6602}
                    {:tie 6 :aosa 325 :aet 4771 :losa 325 :let 5019}
                    {:tie 6 :aosa 324 :aet 411 :losa 324 :let 1705}
                    {:tie 6 :aosa 321 :aet 4070 :losa 322 :let 800}
                    {:tie 6 :aosa 321 :aet 3156 :losa 321 :let 4070}
                    {:tie 6 :aosa 320 :aet 1317 :losa 320 :let 1676}
                    {:tie 6 :aosa 319 :aet 3228 :losa 319 :let 5990}
                    {:tie 6 :aosa 318 :aet 3330 :losa 318 :let 5477}
                    {:tie 6 :aosa 316 :aet 5804 :losa 318 :let 322}
                    {:tie 6 :aosa 316 :aet 3938 :losa 316 :let 5575}
                    {:tie 6 :aosa 316 :aet 2064 :losa 316 :let 3472}
                    {:tie 6 :aosa 314 :aet 4663 :losa 315 :let 587}
                    {:tie 6 :aosa 312 :aet 3099 :losa 312 :let 3805}
                    {:tie 6 :aosa 312 :aet 2506 :losa 312 :let 3099}
                    {:tie 6 :aosa 311 :aet 1595 :losa 312 :let 1708}
                    {:tie 24868 :aosa 12 :aet 0 :losa 67 :let 684}
                    {:tie 62 :aosa 23 :aet 3652 :losa 23 :let 4811}
                    {:tie 160 :aosa 311 :aet 1670 :losa 312 :let 1249}
                    {:tie 34561 :aosa 9 :aet 0 :losa 9 :let 18}
                    {:tie 34562 :aosa 9 :aet 0 :losa 9 :let 19}
                    {:tie 62 :aosa 23 :aet 1611 :losa 23 :let 2645}
                    {:tie 14871 :aosa 1 :aet 4003 :losa 1 :let 4610}
                    {:tie 62 :aosa 23 :aet 443 :losa 23 :let 821}
                    {:tie 6 :aosa 311 :aet 1205 :losa 311 :let 1595}
                    {:tie 160 :aosa 311 :aet 745 :losa 311 :let 1670}
                    {:tie 34501 :aosa 9 :aet 0 :losa 9 :let 23}
                    {:tie 6 :aosa 308 :aet 2316 :losa 309 :let 707}
                    {:tie 24859 :aosa 12 :aet 376 :losa 56 :let 293}
                    {:tie 397 :aosa 1 :aet 2146 :losa 1 :let 3023}
                    {:tie 34555 :aosa 9 :aet 0 :losa 9 :let 18}
                    {:tie 397 :aosa 1 :aet 40 :losa 1 :let 543}
                    {:tie 24863 :aosa 12 :aet 0 :losa 56 :let 528}
                    {:tie 397 :aosa 1 :aet 0 :losa 1 :let 30}
                    {:tie 62 :aosa 25 :aet 1907 :losa 25 :let 2146}
                    {:tie 62 :aosa 25 :aet 2213 :losa 25 :let 2970}
                    {:tie 62 :aosa 25 :aet 1142 :losa 25 :let 1881}
                    {:tie 34553 :aosa 9 :aet 0 :losa 9 :let 26}
                    {:tie 62 :aosa 19 :aet 6123 :losa 20 :let 695}
                    {:tie 14867 :aosa 3 :aet 5727 :losa 4 :let 1150}
                    {:tie 62 :aosa 19 :aet 4435 :losa 19 :let 5525}
                    {:tie 62 :aosa 19 :aet 2732 :losa 19 :let 3605}
                    {:tie 6 :aosa 307 :aet 1098 :losa 308 :let 294}
                    {:tie 34554 :aosa 9 :aet 0 :losa 9 :let 20}
                    {:tie 24858 :aosa 12 :aet 0 :losa 68 :let 416}
                    {:tie 34552 :aosa 9 :aet 0 :losa 9 :let 19}
                    {:tie 24856 :aosa 995 :aet 190 :losa 995 :let 787}
                    {:tie 24855 :aosa 12 :aet 0 :losa 12 :let 186}
                    {:tie 6 :aosa 306 :aet 917 :losa 306 :let 6185}
                    {:tie 62 :aosa 19 :aet 1424 :losa 19 :let 2689}
                    {:tie 24862 :aosa 23 :aet 112 :losa 23 :let 165}
                    {:tie 24862 :aosa 56 :aet 461 :losa 56 :let 488}
                    {:tie 24862 :aosa 12 :aet 0 :losa 12 :let 172}
                    {:tie 24861 :aosa 12 :aet 0 :losa 57 :let 351}
                    {:tie 24807 :aosa 12 :aet 0 :losa 57 :let 250}
                    {:tie 6 :aosa 304 :aet 163 :losa 306 :let 77}
                    {:tie 70006 :aosa 327 :aet 0 :losa 329 :let 34}
                    {:tie 70006 :aosa 379 :aet 0 :losa 381 :let 60}
                    {:tie 34516 :aosa 9 :aet 0 :losa 9 :let 20}
                    {:tie 24805 :aosa 23 :aet 208 :losa 45 :let 445}
                    {:tie 24862 :aosa 23 :aet 112 :losa 23 :let 165}
                    {:tie 24862 :aosa 56 :aet 461 :losa 56 :let 488}
                    {:tie 24862 :aosa 12 :aet 0 :losa 12 :let 172}
                    {:tie 24861 :aosa 12 :aet 0 :losa 57 :let 351}
                    {:tie 24807 :aosa 12 :aet 0 :losa 57 :let 250}
                    {:tie 6 :aosa 304 :aet 163 :losa 306 :let 77}
                    {:tie 70006 :aosa 327 :aet 0 :losa 329 :let 34}
                    {:tie 70006 :aosa 379 :aet 0 :losa 381 :let 60}
                    {:tie 34516 :aosa 9 :aet 0 :losa 9 :let 20}
                    {:tie 24805 :aosa 23 :aet 208 :losa 45 :let 445}
                    {:tie 4081 :aosa 1 :aet 409 :losa 1 :let 2816}
                    {:tie 6 :aosa 302 :aet 757 :losa 302 :let 2157}
                    {:tie 24802 :aosa 56 :aet 0 :losa 56 :let 660}
                    {:tie 387 :aosa 1 :aet 0 :losa 1 :let 2909}
                    {:tie 6 :aosa 216 :aet 2916 :losa 301 :let 468}
                    {:tie 24387 :aosa 1 :aet 0 :losa 1 :let 180}
                    {:tie 24801 :aosa 13 :aet 56 :losa 67 :let 611}
                    {:tie 24801 :aosa 67 :aet 611 :losa 67 :let 645}
                    {:tie 24801 :aosa 13 :aet 0 :losa 13 :let 56}
                    {:tie 387 :aosa 1 :aet 2909 :losa 1 :let 3196}
                    {:tie 6 :aosa 216 :aet 73 :losa 216 :let 2916}
                    {:tie 24800 :aosa 23 :aet 412 :losa 56 :let 93}
                    {:tie 408 :aosa 2 :aet 3948 :losa 2 :let 5905}
                    {:tie 70006 :aosa 374 :aet 0 :losa 374 :let 1254}
                    {:tie 70006 :aosa 371 :aet 0 :losa 371 :let 127}
                    {:tie 13 :aosa 237 :aet 2869 :losa 237 :let 4301}
                    {:tie 6 :aosa 212 :aet 4952 :losa 212 :let 9142}
                    {:tie 24710 :aosa 995 :aet 0 :losa 995 :let 964}
                    {:tie 6 :aosa 211 :aet 9829 :losa 212 :let 4952}
                    {:tie 24709 :aosa 995 :aet 0 :losa 995 :let 1023}
                    {:tie 70006 :aosa 352 :aet 0 :losa 352 :let 43}
                    {:tie 24704 :aosa 12 :aet 107 :losa 56 :let 502}
                    {:tie 70006 :aosa 320 :aet 0 :losa 320 :let 56}
                    {:tie 70006 :aosa 370 :aet 0 :losa 370 :let 539}
                    {:tie 387 :aosa 7 :aet 2860 :losa 8 :let 230}
                    {:tie 387 :aosa 7 :aet 2816 :losa 7 :let 2859}
                    {:tie 13 :aosa 234 :aet 1701 :losa 234 :let 2854}
                    {:tie 13 :aosa 233 :aet 1380 :losa 234 :let 1701}
                    {:tie 13 :aosa 233 :aet 1257 :losa 233 :let 1380}
                    {:tie 13 :aosa 233 :aet 530 :losa 234 :let 2874}
                    {:tie 6 :aosa 210 :aet 967 :losa 210 :let 2400}
                    {:tie 70006 :aosa 367 :aet 935 :losa 367 :let 2134}
                    {:tie 24708 :aosa 12 :aet 0 :losa 12 :let 276}
                    {:tie 70006 :aosa 367 :aet 0 :losa 367 :let 935}
                    {:tie 6 :aosa 209 :aet 1649 :losa 210 :let 967}
                    {:tie 70006 :aosa 317 :aet 0 :losa 317 :let 88}
                    {:tie 24702 :aosa 12 :aet 0 :losa 67 :let 685}
                    {:tie 24706 :aosa 1 :aet 0 :losa 1 :let 131}
                    {:tie 24701 :aosa 12 :aet 0 :losa 56 :let 657}
                    {:tie 24705 :aosa 1 :aet 0 :losa 1 :let 130}
                    {:tie 70026 :aosa 366 :aet 0 :losa 367 :let 37}
                    {:tie 26 :aosa 11 :aet 2700 :losa 11 :let 4246}
                    {:tie 6 :aosa 208 :aet 5397 :losa 209 :let 1649}
                    {:tie 24700 :aosa 23 :aet 122 :losa 45 :let 113}
                    {:tie 24700 :aosa 12 :aet 327 :losa 12 :let 447}
                    {:tie 6 :aosa 207 :aet 978 :losa 208 :let 3931}
                    {:tie 26 :aosa 7 :aet 2034 :losa 7 :let 3899}
                    {:tie 6 :aosa 206 :aet 1913 :losa 206 :let 6507}
                    {:tie 6 :aosa 205 :aet 1242 :losa 206 :let 1913}
                    {:tie 6 :aosa 204 :aet 3946 :losa 205 :let 593}
                    {:tie 115 :aosa 2 :aet 3032 :losa 2 :let 4339}
                    {:tie 116 :aosa 2 :aet 3921 :losa 2 :let 5517}
                    {:tie 25 :aosa 20 :aet 3301 :losa 22 :let 1434}
                    {:tie 116 :aosa 2 :aet 314 :losa 2 :let 3921}
                    {:tie 116 :aosa 1 :aet 0 :losa 1 :let 3508}
                    {:tie 51 :aosa 12 :aet 4156 :losa 13 :let 726}
                    {:tie 25 :aosa 19 :aet 6660 :losa 20 :let 3301}
                    {:tie 186 :aosa 14 :aet 647 :losa 14 :let 2021}
                    {:tie 25 :aosa 16 :aet 10027 :losa 18 :let 1863}
                    {:tie 1070 :aosa 4 :aet 746 :losa 5 :let 1041}
                    {:tie 1071 :aosa 1 :aet 0 :losa 1 :let 980}
                    {:tie 104 :aosa 7 :aet 5139 :losa 9 :let 3003}
                    {:tie 104 :aosa 7 :aet 1764 :losa 7 :let 3758}
                    {:tie 104 :aosa 6 :aet 3394 :losa 7 :let 1183}
                    {:tie 25 :aosa 16 :aet 8782 :losa 16 :let 10027}
                    {:tie 25 :aosa 16 :aet 7820 :losa 16 :let 7996}
                    {:tie 186 :aosa 12 :aet 3298 :losa 12 :let 5374}
                    {:tie 21561 :aosa 12 :aet 0 :losa 12 :let 203}
                    {:tie 25 :aosa 16 :aet 3142 :losa 16 :let 7820}
                    {:tie 25 :aosa 15 :aet 2938 :losa 16 :let 3142}
                    {:tie 104 :aosa 5 :aet 3331 :losa 6 :let 2623}
                    {:tie 25 :aosa 15 :aet 680 :losa 15 :let 1339}
                    {:tie 104 :aosa 4 :aet 1135 :losa 5 :let 1848}
                    {:tie 186 :aosa 9 :aet 1307 :losa 9 :let 1607}
                    {:tie 104 :aosa 2 :aet 3698 :losa 4 :let 1135}
                    {:tie 111 :aosa 1 :aet 625 :losa 1 :let 847}
                    {:tie 111 :aosa 1 :aet 1879 :losa 1 :let 2857}
                    {:tie 104 :aosa 2 :aet 1883 :losa 2 :let 3431}
                    {:tie 25 :aosa 12 :aet 2463 :losa 12 :let 2626}
                    {:tie 25 :aosa 11 :aet 2994 :losa 12 :let 2461}
                    {:tie 111 :aosa 2 :aet 8401 :losa 3 :let 1111}
                    {:tie 25 :aosa 9 :aet 2177 :losa 11 :let 2994}
                    {:tie 1002 :aosa 1 :aet 6400 :losa 1 :let 6807}
                    {:tie 1002 :aosa 1 :aet 0 :losa 1 :let 724}
                    {:tie 25 :aosa 7 :aet 6280 :losa 7 :let 7453}
                    {:tie 111 :aosa 4 :aet 9880 :losa 4 :let 11038}
                    {:tie 52 :aosa 2 :aet 4181 :losa 2 :let 4699}
                    {:tie 52 :aosa 2 :aet 4699 :losa 3 :let 271}
                    {:tie 1081 :aosa 1 :aet 0 :losa 1 :let 1156}
                    {:tie 111 :aosa 4 :aet 11842 :losa 4 :let 12908}
                    {:tie 52 :aosa 3 :aet 985 :losa 3 :let 1369}
                    {:tie 1081 :aosa 1 :aet 3407 :losa 1 :let 5145}
                    {:tie 25 :aosa 6 :aet 3962 :losa 7 :let 1877}
                    {:tie 1081 :aosa 1 :aet 7932 :losa 2 :let 2085}
                    {:tie 25 :aosa 5 :aet 4369 :losa 6 :let 1857}
                    {:tie 25 :aosa 5 :aet 1153 :losa 5 :let 4369}
                    {:tie 1081 :aosa 2 :aet 2085 :losa 2 :let 5283}
                    {:tie 25 :aosa 3 :aet 2837 :losa 5 :let 1153}
                    {:tie 1081 :aosa 2 :aet 7020 :losa 3 :let 1581}
                    {:tie 1081 :aosa 3 :aet 3842 :losa 4 :let 2025}
                    {:tie 1081 :aosa 4 :aet 3166 :losa 4 :let 3931}
                    {:tie 1081 :aosa 4 :aet 3931 :losa 4 :let 4948}
                    {:tie 25 :aosa 1 :aet 803 :losa 3 :let 2837}
                    {:tie 134 :aosa 2 :aet 6835 :losa 2 :let 8874}
                    {:tie 134 :aosa 2 :aet 4184 :losa 2 :let 5741}
                    {:tie 134 :aosa 2 :aet 244 :losa 2 :let 1006}
                    {:tie 134 :aosa 1 :aet 3510 :losa 1 :let 5701}
                    {:tie 133 :aosa 1 :aet 3294 :losa 1 :let 5792}
                    {:tie 2 :aosa 2 :aet 1369 :losa 3 :let 1459}
                    {:tie 134 :aosa 1 :aet 820 :losa 1 :let 1837}
                    {:tie 134 :aosa 1 :aet 0 :losa 1 :let 820}
                    {:tie 133 :aosa 1 :aet 94 :losa 1 :let 2346}
                    {:tie 2 :aosa 13 :aet 2294 :losa 13 :let 3580}
                    {:tie 110 :aosa 11 :aet 9145 :losa 13 :let 237}
                    {:tie 1224 :aosa 6 :aet 3555 :losa 6 :let 4079}
                    {:tie 2 :aosa 15 :aet 6846 :losa 16 :let 1182}
                    {:tie 126 :aosa 1 :aet 8291 :losa 1 :let 9188}
                    {:tie 1224 :aosa 6 :aet 6896 :losa 6 :let 8239}
                    {:tie 25 :aosa 20 :aet 3301 :losa 22 :let 1434}
                    {:tie 1282 :aosa 2 :aet 6269 :losa 2 :let 6923}
                    {:tie 127 :aosa 1 :aet 0 :losa 1 :let 419}
                    {:tie 1282 :aosa 2 :aet 1086 :losa 2 :let 2550}
                    {:tie 1090 :aosa 3 :aet 859 :losa 3 :let 2132}
                    {:tie 127 :aosa 3 :aet 1328 :losa 3 :let 2836}
                    {:tie 280 :aosa 3 :aet 4010 :losa 3 :let 5296}
                    {:tie 1090 :aosa 1 :aet 0 :losa 1 :let 620}
                    {:tie 110 :aosa 16 :aet 2262 :losa 17 :let 544}
                    {:tie 280 :aosa 3 :aet 5296 :losa 4 :let 1434}
                    {:tie 280 :aosa 4 :aet 2781 :losa 4 :let 6618}
                    {:tie 116 :aosa 1 :aet 0 :losa 1 :let 3508}
                    {:tie 2821 :aosa 2 :aet 4119 :losa 2 :let 4659}
                    {:tie 284 :aosa 3 :aet 4599 :losa 3 :let 5168}
                    {:tie 10 :aosa 17 :aet 1631 :losa 17 :let 2713}
                    {:tie 284 :aosa 1 :aet 1663 :losa 1 :let 2418}
                    {:tie 10 :aosa 15 :aet 4165 :losa 17 :let 347}
                    {:tie 2 :aosa 23 :aet 7818 :losa 23 :let 9023}
                    {:tie 13561 :aosa 1 :aet 0 :losa 1 :let 1982}
                    {:tie 2813 :aosa 3 :aet 283 :losa 4 :let 1063}
                    {:tie 10 :aosa 13 :aet 2283 :losa 13 :let 3557}
                    {:tie 2 :aosa 28 :aet 660 :losa 28 :let 1165}
                    {:tie 2 :aosa 28 :aet 1168 :losa 28 :let 2808}
                    {:tie 2 :aosa 28 :aet 2927 :losa 28 :let 5311}
                    {:tie 2 :aosa 29 :aet 203 :losa 29 :let 227}
                    {:tie 10 :aosa 13 :aet 388 :losa 13 :let 829}
                    )
        _ (println "kohta rytisee")]
    (mapv
      (fn [osoite]
        (do
          (when-not (nil? (suolarajoitus-palvelu/tr-osoitteen-validointi db osoite)))
          (println "Virhe !!" osoite)))
      osoitteet)
    (println "ohi on: " osoitteet)
    ))



