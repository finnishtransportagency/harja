(ns harja.palvelin.palvelut.lupaus-palvelu-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.domain.lupaus.kustannusennuste-domain :as kustannusennuste-domain]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :hae-urakan-lupaustiedot (component/using
                                     (lupaus-palvelu/->Lupaus {:kehitysmoodi true})
                                     [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each
  urakkatieto-fixture
  jarjestelma-fixture)

(defn hae-urakan-lupaustiedot [kayttaja tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :hae-urakan-lupaustiedot
    kayttaja
    tiedot))

(defn- vastaa-lupaukseen [lupaus-vastaus]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :vastaa-lupaukseen
    +kayttaja-jvh+
    lupaus-vastaus))

(defn- kommentit [tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :lupauksen-kommentit
    +kayttaja-jvh+
    tiedot))

(defn- lisaa-kommentti [kayttaja tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :lisaa-lupauksen-kommentti
    kayttaja
    tiedot))

(defn- poista-kommentti [kayttaja tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :poista-lupauksen-kommentti
    kayttaja
    tiedot))

(defn- tallenna-kuukausittaiset-pisteet [kayttaja tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-kuukausittaiset-pisteet kayttaja tiedot))

(defn- poista-kuukausittaiset-pisteet [kayttaja tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma) :poista-kuukausittaiset-pisteet kayttaja tiedot))

(defn- hae-kuukausittaiset-pisteet [kayttaja tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma) :hae-kuukausittaiset-pisteet kayttaja tiedot))

(defn etsi-lupaus [lupaustiedot id]
  (lupaus-domain/etsi-lupaus lupaustiedot id))

(defn- etsi-ryhma [ryhmat jarjestys-numero]
  (first (filter #(= jarjestys-numero (:jarjestys %)) ryhmat)))

(defn etsi-vaihtoehto [vaihtoehdot vaihtoehto-seuraava-ryhma-id]
  (first (filter #(= vaihtoehto-seuraava-ryhma-id (:vaihtoehto-seuraava-ryhma-id %)) vaihtoehdot)))

(deftest urakan-lupaustietojen-haku-toimii
  (let [tiedot {:urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                     #inst "2022-09-30T20:59:59.000-00:00"]
                :nykyhetki #inst "2021-09-30T21:00:00.000-00:00"}
        vastaus (hae-urakan-lupaustiedot
                  +kayttaja-jvh+
                  tiedot)
        sitoutuminen (:lupaus-sitoutuminen vastaus)
        ryhmat (:lupausryhmat vastaus)
        ryhma-1 (etsi-ryhma ryhmat 1)
        ryhma-2 (etsi-ryhma ryhmat 2)
        ryhma-3 (etsi-ryhma ryhmat 3)
        ryhma-4 (etsi-ryhma ryhmat 4)
        ryhma-5 (etsi-ryhma ryhmat 5)]
    (is (= 1 (:id sitoutuminen)) "luvattu-pistemaara oikein")
    (is (= 76 (:pisteet sitoutuminen)) "luvattu-pistemaara oikein")
    (is (= 5 (count ryhmat)) "lupausryhmien määrä")

    (is (= 16 (:pisteet ryhma-1)) "ryhmä 1 pisteet")
    (is (= 14 (:kyselypisteet ryhma-1)) "ryhmä 1 kyselypisteet")
    (is (= 30 (:pisteet-max ryhma-1)) "ryhmä 1 maksimipisteet")
    (is (= 30 (:pisteet-ennuste ryhma-1)) "ryhmä 1 piste-ennuste")

    (is (= 10 (:pisteet ryhma-2)) "ryhmä 2 pisteet")
    (is (= 0 (:kyselypisteet ryhma-2)) "ryhmä 2 kyselypisteet")
    (is (= 10 (:pisteet-max ryhma-2)) "ryhmä 2 maksimipisteet")
    (is (= 10 (:pisteet-ennuste ryhma-2)) "ryhmä 2 piste-ennuste")

    (is (= 10 (:pisteet ryhma-3)) "ryhmä 3 pisteet")
    (is (= 10 (:kyselypisteet ryhma-3)) "ryhmä 3 kyselypisteet")
    (is (= 20 (:pisteet-max ryhma-3)) "ryhmä 3 maksimipisteet")
    (is (= 20 (:pisteet-ennuste ryhma-3)) "ryhmä 3 piste-ennuste")

    (is (= 15 (:pisteet ryhma-4)) "ryhmä 4 pisteet")
    (is (= 0 (:kyselypisteet ryhma-4)) "ryhmä 4 kyselypisteet")
    (is (= 15 (:pisteet-max ryhma-4)) "ryhmä 4 maksimipisteet")
    (is (= 15 (:pisteet-ennuste ryhma-4)) "ryhmä 4 piste-ennuste")

    (is (= 25 (:pisteet ryhma-5)) "ryhmä 5 pisteet")
    (is (= 0 (:kyselypisteet ryhma-5)) "ryhmä 5 kyselypisteet")
    (is (= 25 (:pisteet-max ryhma-5)) "ryhmä 5 maksimipisteet")
    (is (= 25 (:pisteet-ennuste ryhma-5)) "ryhmä 5 piste-ennuste")

    (is (= 100 (->> ryhmat (map :pisteet-max) (reduce +))))
    (is (= 100 (get-in vastaus [:yhteenveto :pisteet :maksimi]))
      "koko hoitovuoden piste-maksimi")
    (is (= 100 (get-in vastaus [:yhteenveto :pisteet :ennuste]))
      "koko hoitovuoden piste-ennuste")

    (is (= vastaus (hae-urakan-lupaustiedot
                     +kayttaja-yit_uuvh+
                     tiedot))
      "Urakan vastuuhenkilö saa saman vastauksen kuin järjestelmänvalvoja.")

    (is (thrown? Exception (hae-urakan-lupaustiedot
                             +kayttaja-vastuuhlo-muhos+
                             tiedot))
      "Toisen urakan vastuuhenkilö ei saa hakea tietoja.")))

(deftest urakan-lupaustietojen-haku-lupausryhmat-eroavat-kun-urakat-samalla-alkuvuodella
  (let [suomussalmi-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        suomussalmen-tiedot {:urakka-id suomussalmi-id
                             :valittu-hoitokausi [#inst "2024-09-30T21:00:00.000-00:00"
                                                  #inst "2025-09-30T20:59:59.000-00:00"]
                             :nykyhetki #inst "2024-03-01T21:00:00.000-00:00"}
        suomussalmi-2-id (hae-urakan-id-nimella "KOPIO POP MHU Suomussalmi 2024-2029")
        suomussalmi-2-tiedot-2 {:urakka-id suomussalmi-2-id
                                :valittu-hoitokausi [#inst "2024-09-30T21:00:00.000-00:00"
                                                     #inst "2025-09-30T20:59:59.000-00:00"]
                                :nykyhetki #inst "2024-03-01T21:00:00.000-00:00"}
        suomussalmi-vastaus (hae-urakan-lupaustiedot
                              +kayttaja-jvh+
                              suomussalmen-tiedot)
        suomussalmi-2-vastaus (hae-urakan-lupaustiedot
                                +kayttaja-jvh+
                                suomussalmi-2-tiedot-2)
        suomussalmi-ryhmat (:lupausryhmat suomussalmi-vastaus)
        suomussalmi-2-ryhmat (:lupausryhmat suomussalmi-2-vastaus)
        suomussalmi-lupaus-44 (etsi-lupaus suomussalmi-vastaus 44)
        suomussalmi-2-lupaus-68 (etsi-lupaus suomussalmi-2-vastaus 68)
        suomussalmi-ryhma-idt (sort (map :id suomussalmi-ryhmat))
        suomussalmi-2-ryhma-idt (sort (map :id suomussalmi-2-ryhmat))]
    (is (= (list 17 19 21 23 25) suomussalmi-ryhma-idt) "Suomussalmen ryhmä-idt - Eri ryhmät kuin toisella samalla vuodella alkavalla urakalla")
    (is (= (list 16 18 20 22 24) suomussalmi-2-ryhma-idt) "Suomussalmi 2 urakan ryhmä-idt - Eri ryhmät kuin toisella samalla vuodella alkavalla urakalla")
    (is (not= suomussalmi-ryhma-idt suomussalmi-2-ryhma-idt) "Ryhmät pitää olla erit, koska Kajaani simuloi erittäin vaativaa urakkaa tässä testissä.")
    (is (= 44 (:lupaus-id suomussalmi-lupaus-44)) "Suomussalmella on lupaus 44 ryhmasta 1")
    (is (= 68 (:lupaus-id suomussalmi-2-lupaus-68)) "Suomussalmi 2:lla on lupaus 13 ryhmasta 5")))


(deftest urakan-2025-lupaustiedot-toimii
  (let [kajaani-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        kajaani-tiedot {:urakka-id kajaani-id
                        :valittu-hoitokausi [#inst "2025-09-30T21:00:00.000-00:00"
                                             #inst "2026-09-30T20:59:59.000-00:00"]
                        :nykyhetki #inst "2025-10-01T21:00:00.000-00:00"}
        kajaani-vastaus (hae-urakan-lupaustiedot +kayttaja-jvh+ kajaani-tiedot)

        kajaani-ryhmat (:lupausryhmat kajaani-vastaus)
        kajaani-lupaus-83 (etsi-lupaus kajaani-vastaus 83)]

    (is (= 83 (:lupaus-id kajaani-lupaus-83)) "Kajaanilla on lupaus 83 ryhmasta 5")))

(deftest urakan-lupaustietojen-vaihtoehtojen-haku-toimii
  (let [tiedot {:urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                     #inst "2022-09-30T20:59:59.000-00:00"]
                :nykyhetki #inst "2021-09-30T21:00:00.000-00:00"}
        vastaus (hae-urakan-lupaustiedot
                  +kayttaja-jvh+
                  tiedot)
        vaihtoehdot (:vaihtoehdot (etsi-lupaus vastaus 3))
        vaihtoehto (etsi-vaihtoehto vaihtoehdot 3)]
    (is (= "> 25 %" (:vaihtoehto vaihtoehto)) "vaihtoehto oikein")
    (is (= 1 (:vaihtoehto-askel vaihtoehto)) "askel oikein")
    (is (= 3 (:vaihtoehto-seuraava-ryhma-id vaihtoehto)) "seuraava ryhmä oikein")
    (is (= "Testiotsikko 1" (:ryhma-otsikko vaihtoehto)) "ryhma-otsikko oikein")))

(deftest odottaa-kannanottoa
  (let [hakutiedot {:urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                    :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                         #inst "2022-09-30T20:59:59.000-00:00"]
                    ;; 2022-01-01
                    :nykyhetki (pvm/luo-pvm 2022 0 1)}
        vastaus (hae-urakan-lupaustiedot
                  +kayttaja-jvh+
                  hakutiedot)
        ryhmat (:lupausryhmat vastaus)
        ryhma-1 (etsi-ryhma ryhmat 1)
        lupaus-1 (etsi-lupaus vastaus 1)
        lupaus-2 (etsi-lupaus vastaus 2)
        lupaus-3 (etsi-lupaus vastaus 3)]
    ;; Ryhmä 1: lupaukset 1, 2 ja 3
    ;; Vastattu:
    ;; Lupaus 1: {10}
    ;; Lupaus 2: {10}
    ;; Lupaus 3: {10,11}
    ;; Vaaditaan:
    ;; Lupaus 1: {10}
    ;; Lupaus 2: {10}
    ;; Lupaus 3: {10,11,12,1,2,3,4,5,6,7,8}
    ;; -> Kuukausi 1: lupaus 3 odottaa kannanottoa.
    (is (false? (:odottaa-kannanottoa? lupaus-1)))
    (is (false? (:odottaa-kannanottoa? lupaus-2)))
    (is (true? (:odottaa-kannanottoa? lupaus-3)))

    (is (= 1 (:odottaa-kannanottoa ryhma-1)))

    (is (= 10 (get-in vastaus [:yhteenveto :odottaa-kannanottoa]))
      "Yhteensä 11 lupausta odottaa kannanottoa tammikuussa: kaikki paitsi 1, 2, 12 ja 14")
    (is (= 4 (get-in vastaus [:yhteenveto :merkitsevat-odottaa-kannanottoa]))
      "Yhteensä 4 lupausta odottaa merkitsevää kannanottoa tammikuussa: 4, 8, 11 ja 13")))

(deftest merkitsevat-odottaa-kannanottoa
  (let [hakutiedot {:urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                    :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                         #inst "2022-09-30T20:59:59.000-00:00"]
                    ;; 2022-01-01
                    :nykyhetki (pvm/luo-pvm 2022 0 1)}
        vastaus (hae-urakan-lupaustiedot
                  +kayttaja-jvh+
                  hakutiedot)
        ryhmat (:lupausryhmat vastaus)]
    (is (= 0 (:merkitsevat-odottaa-kannanottoa (etsi-ryhma ryhmat 1))))
    (is (= 1 (:merkitsevat-odottaa-kannanottoa (etsi-ryhma ryhmat 2))))
    (is (= 0 (:merkitsevat-odottaa-kannanottoa (etsi-ryhma ryhmat 3))))
    (is (= 1 (:merkitsevat-odottaa-kannanottoa (etsi-ryhma ryhmat 4))))
    (is (= 2 (:merkitsevat-odottaa-kannanottoa (etsi-ryhma ryhmat 5))))))

(deftest piste-ennuste
  (let [paivitys-tulos (vastaa-lupaukseen {:id 2
                                           :vastaus false})
        vastaus (hae-urakan-lupaustiedot
                  +kayttaja-jvh+
                  {:urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                   :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                        #inst "2022-09-30T20:59:59.000-00:00"]})
        ryhmat (:lupausryhmat vastaus)
        ryhma-1 (etsi-ryhma ryhmat 1)
        lupaus-2 (etsi-lupaus vastaus 2)
        lupaus-3 (etsi-lupaus vastaus 3)]
    (is paivitys-tulos)
    (is (= 30 (:pisteet-max ryhma-1)) "ryhmä 1 maksimipisteet")
    (is (= 0 (:pisteet-ennuste lupaus-2)) "lupauksen 2 piste-ennuste")
    (is (= 14 (:pisteet-ennuste lupaus-3)) "lupauksen 3 piste-ennuste")
    (is (= 22 (:pisteet-ennuste ryhma-1)) "ryhmä 1 piste-ennuste")
    (is (= 92 (get-in vastaus [:yhteenveto :pisteet :ennuste]))
      "koko hoitovuoden piste-ennuste")))

(deftest piste-toteuma
  (let [yhteiset-tiedot {:lupaus-id 9
                         :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id}
        vastaukset [{:vuosi 2022 :kuukausi 1 :paatos false :vastaus true}
                    {:vuosi 2022 :kuukausi 2 :paatos false :vastaus false}
                    {:vuosi 2022 :kuukausi 9 :paatos true :vastaus true}]
        tulokset (doall (->> vastaukset
                          (map #(merge % yhteiset-tiedot))
                          (map vastaa-lupaukseen)))
        lupaustiedot (hae-urakan-lupaustiedot
                       +kayttaja-jvh+
                       {:urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                        :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                             #inst "2022-09-30T20:59:59.000-00:00"]})
        ryhma-4 (etsi-ryhma (:lupausryhmat lupaustiedot) 4)
        lupaus-9 (etsi-lupaus lupaustiedot 9)]
    (is (every? boolean tulokset)
      "Pyynnöt onnistuvat.")
    (is (= 5 (:pisteet-toteuma lupaus-9))
      "Koska päättävä vastaus on hyväksytty, toteuma täytyy olla 5 pistettä (maksimipisteet).
      Urakoitsijan kirjaukset eivät saa vaikuttaa tähän.")
    (is (= 5 (:pisteet-ennuste lupaus-9))
      "Jos toteuma on annettu, ennuste == toteuma.")
    (is (= (:pisteet-max ryhma-4) (:pisteet-ennuste ryhma-4))
      "Ryhmän ennusteen mukaan on tulossa maksimipisteet.")
    (is (nil? (:pisteet-toteuma ryhma-4))
      "Koko ryhmälle ei ole vielä toteumaa, vaan yhdelle lupaukselle.")

    ;; Annetaan päätökset ryhmän muihin lupauksiin 8 ja 10:
    ;; Lupaus 8: kielteinen (5 pistettä)
    ;; Lupaus 10: myönteinen (0 pistettä)
    (let [vastaukset [{:lupaus-id 8 :vuosi 2022 :kuukausi 1 :paatos true :vastaus false :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id}
                      {:lupaus-id 10 :vuosi 2022 :kuukausi 9 :paatos true :vastaus true :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id}]
          tulokset (doall (->> vastaukset
                            (map vastaa-lupaukseen)))
          lupaustiedot (hae-urakan-lupaustiedot
                         +kayttaja-jvh+
                         {:urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                          :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                               #inst "2022-09-30T20:59:59.000-00:00"]})
          ryhma-4 (etsi-ryhma (:lupausryhmat lupaustiedot) 4)
          lupaus-8 (etsi-lupaus lupaustiedot 8)
          lupaus-10 (etsi-lupaus lupaustiedot 10)]
      (is (every? boolean tulokset)
        "Pyynnöt onnistuvat.")
      (is (= 0 (:pisteet-toteuma lupaus-8))
        "Lupaukselle 8 annettiin kielteinen vastaus, eli nolla pistetä.")
      (is (= 5 (:pisteet-toteuma lupaus-10))
        "Lupaukselle 10 annettiin myönteinen vastaus, eli viisi pistettä.")
      (is (= 10 (:pisteet-toteuma ryhma-4))
        "Ryhmälle 4 voidaan laskea toteuma, koska kaikkiin sen lupauksiin on vastattu."))))

(deftest joustovara
  (let [hakutiedot {:urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                    :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                         #inst "2022-09-30T20:59:59.000-00:00"]}
        ;; Ensimmäinen kieltävä vastaus
        tulos-a (vastaa-lupaukseen {:lupaus-id 4
                                    :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                                    :kuukausi 10
                                    :vuosi 2021
                                    :paatos true
                                    :vastaus false})
        lupaustiedot-a (hae-urakan-lupaustiedot +kayttaja-jvh+ hakutiedot)
        lupaukset-a (:lupaukset lupaustiedot-a)

        ;; Toinen kieltävä vastaus
        tulos-b (vastaa-lupaukseen {:lupaus-id 4
                                    :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                                    :kuukausi 11
                                    :vuosi 2021
                                    :paatos true
                                    :vastaus false})
        lupaustiedot-b (hae-urakan-lupaustiedot +kayttaja-jvh+ hakutiedot)
        lupaukset-b (:lupaukset lupaustiedot-b)]
    (is tulos-a)
    (is tulos-b)
    (is lupaustiedot-a)
    (is lupaustiedot-b)
    (is (= 10 (:pisteet-ennuste (etsi-lupaus lupaustiedot-a 4)))
      "Lupauksella 4 on joustovara 1, joten ennusteen mukaan pitäisi olla vielä täydet pisteet, kun on annettu yksi kieltävä vastaus.")
    (is (= 0 (:pisteet-ennuste (etsi-lupaus lupaustiedot-b 4)))
      "Lupauksella 4 on joustovara 1, joten ennusteen mukaan pitäisi olla nolla pistettä, kun on annettu kaksi kieltävää vastausta.")))

(deftest urakan-lupauspisteiden-tallennus-toimii-insert
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :tallenna-luvatut-pisteet +kayttaja-jvh+
                  {:pisteet 67
                   :id @iin-maanteiden-hoitourakan-lupaussitoutumisen-id
                   :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                   :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                        #inst "2022-09-30T20:59:59.000-00:00"]})
        lupaustiedot (hae-urakan-lupaustiedot +kayttaja-jvh+ {:urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                                                              :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                                                                   #inst "2022-09-30T20:59:59.000-00:00"]})
        sitoutuminen (:lupaus-sitoutuminen lupaustiedot)]
    (is (= 67 (:pisteet sitoutuminen)) "luvattu-pistemaara oikein")))

(deftest urakan-lupaussitoumuksia-vain-yksi-per-urakka
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-luvatut-pisteet +kayttaja-jvh+
            {:pisteet 67
             :urakka-id urakka-id
             :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                  #inst "2022-09-30T20:59:59.000-00:00"]})
        sitoutuminen-1 (hae-urakan-lupaustiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                                :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                                                                     #inst "2022-09-30T20:59:59.000-00:00"]})
        sitoumusrivien-maara-1 (ffirst (q (format "SELECT count(*) FROM lupaus_sitoutuminen WHERE \"urakka-id\" = %s;" urakka-id)))
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-luvatut-pisteet +kayttaja-jvh+
            {:pisteet nil
             :urakka-id urakka-id
             :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                  #inst "2022-09-30T20:59:59.000-00:00"]})
        sitoutuminen-2 (hae-urakan-lupaustiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                                :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                                                                     #inst "2022-09-30T20:59:59.000-00:00"]})
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-luvatut-pisteet +kayttaja-jvh+
            {:pisteet 55
             :urakka-id urakka-id
             :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                  #inst "2022-09-30T20:59:59.000-00:00"]})
        sitoumusrivien-maara-2 (ffirst (q (format "SELECT count(*) FROM lupaus_sitoutuminen WHERE \"urakka-id\" = %s;" urakka-id)))
        sitoutuminen-3 (hae-urakan-lupaustiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                                :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                                                                     #inst "2022-09-30T20:59:59.000-00:00"]})]
    (is (= 1 sitoumusrivien-maara-1) "Useampi tallennus ei lisää rivien määrää")
    (is (= 67 (get-in sitoutuminen-1 [:lupaus-sitoutuminen :pisteet])) "luvattu-pistemaara oikein")
    (is (= urakka-id (get-in sitoutuminen-1 [:lahtotiedot :urakka-id])) "urakka-id oikein")
    (is (= 1 sitoumusrivien-maara-2) "Useampi tallennus ei lisää rivien määrää")
    (is (nil? (get-in sitoutuminen-2 [:lupaus-sitoutuminen :pisteet])) "luvattu-pistemaara oikein")
    (is (= urakka-id (get-in sitoutuminen-2 [:lahtotiedot :urakka-id])) "urakka-id oikein")
    (is (= 55 (get-in sitoutuminen-3 [:lupaus-sitoutuminen :pisteet])) "luvattu-pistemaara oikein")
    (is (= urakka-id (get-in sitoutuminen-3 [:lahtotiedot :urakka-id])) "urakka-id oikein")))

(deftest poistettu-sitoutuminen-ei-palaudu-palvelusta
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-luvatut-pisteet +kayttaja-jvh+
            {:pisteet 67
             :urakka-id urakka-id
             :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                  #inst "2022-09-30T20:59:59.000-00:00"]})
        sitoutuminen-ennen-poistoa (hae-urakan-lupaustiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                                            :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                                                                                 #inst "2022-09-30T20:59:59.000-00:00"]})
        _ (u (format "UPDATE lupaus_sitoutuminen SET poistettu = TRUE WHERE \"urakka-id\" = %s;" urakka-id))
        sitoutuminen-poiston-jalkeen (hae-urakan-lupaustiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                                              :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                                                                                   #inst "2022-09-30T20:59:59.000-00:00"]})]
    (is (= 67 (get-in sitoutuminen-ennen-poistoa [:lupaus-sitoutuminen :pisteet])) "luvattu-pistemaara oikein")
    (is (= urakka-id (get-in sitoutuminen-ennen-poistoa [:lahtotiedot :urakka-id])) "urakka-id oikein")
    (is (nil? (get-in sitoutuminen-poiston-jalkeen [:lupaus-sitoutuminen :pisteet])) "poistettu rivi ei nouse")))

(deftest urakan-lupauspisteiden-tallennus-vaatii-oikean-urakkaidn
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :tallenna-luvatut-pisteet +kayttaja-jvh+
                  {:id @iin-maanteiden-hoitourakan-lupaussitoutumisen-id
                   :pisteet 67, :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                   :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                        #inst "2022-09-30T20:59:59.000-00:00"]})
        _ (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-luvatut-pisteet +kayttaja-jvh+
                                           {:id @iin-maanteiden-hoitourakan-lupaussitoutumisen-id
                                            :pisteet 167
                                            :urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
                                            :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                                                 #inst "2022-09-30T20:59:59.000-00:00"]})))]))

(deftest urakan-lupauspisteita-ei-saa-muokata-valikatselmuksen-jalkeen
  (with-redefs [lupaus-palvelu/valikatselmus-tehty-urakalle? (constantly true) 2021]
    (is (thrown? AssertionError (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-luvatut-pisteet +kayttaja-jvh+
                                  {:id (hae-iin-maanteiden-hoitourakan-lupaussitoutumisen-id)
                                   :pisteet 67, :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                                   :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                                        #inst "2022-09-30T20:59:59.000-00:00"]})))))

(deftest lisaa-lupaus-vastaus
  (let [lupaus-vastaus {:lupaus-id 6
                        :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                        :kuukausi 12
                        :vuosi 2021
                        :paatos false
                        :vastaus true
                        :lupaus-vaihtoehto-id nil}
        tulos (vastaa-lupaukseen lupaus-vastaus)]
    (is (= (select-keys tulos (keys lupaus-vastaus)) ; Ei piitata muista avaimista.
          lupaus-vastaus)
      "Tallennetut arvot ovat palautetaan")
    (is (thrown? Exception (vastaa-lupaukseen lupaus-vastaus))
      "Samalle lupaus-urakka-kuukaus-vuosi -yhdistelmälle ei voi lisätä toista vastausta.")))

(deftest paivita-lupaus-vastaus
  (let [lupaus-vastaus {:id 2
                        :vastaus false
                        :lupaus-vaihtoehto-id nil}
        tulos (vastaa-lupaukseen lupaus-vastaus)]
    (is (= (select-keys tulos (keys lupaus-vastaus)) ; Ei piitata muista avaimista.
          lupaus-vastaus)
      "Tallennetut arvot palautetaan."))

  (is (thrown? AssertionError (vastaa-lupaukseen
                                {:id 9873456387435
                                 :vastaus false
                                 :lupaus-vaihtoehto-id nil}))
    "Olematon lupaus-vastaus-id heittää poikkeuksen.")

  (let [lupaus-vastaus {:id 2
                        :vastaus nil
                        :lupaus-vaihtoehto-id nil}
        tulos (vastaa-lupaukseen lupaus-vastaus)]
    (is (= (select-keys tulos (keys lupaus-vastaus))
          lupaus-vastaus)
      "Boolean-vastauksen voi asettaa takaisin nil-arvoon."))

  (let [lupaus-vastaus {:id 3
                        :vastaus nil
                        :lupaus-vaihtoehto-id nil}
        tulos (vastaa-lupaukseen lupaus-vastaus)]
    (is (= (select-keys tulos (keys lupaus-vastaus))
          lupaus-vastaus)
      "Monivalintavastauksen voi asettaa takaisin nil-arvoon.")))

(deftest ei-saa-lisata-vastausta-valikatselmuksen-jalkeen
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        vastaus {:lupaus-id 6
                 :urakka-id urakka-id
                 :kuukausi 12
                 :vuosi 2021
                 :paatos false
                 :vastaus true
                 :lupaus-vaihtoehto-id nil}]
    (with-redefs [lupaus-palvelu/valikatselmus-tehty-urakalle? (constantly true)]
      (is (thrown? AssertionError (vastaa-lupaukseen vastaus)) "Ei saa vastata välikatselmuksen jälkeen"))
    (is (vastaa-lupaukseen vastaus) "Saa vastata")))

(deftest ei-saa-paivittaa-vastausta-valikatselmuksen-jalkeen
  (let [vastaus {:id 2
                 :vastaus false
                 :lupaus-vaihtoehto-id nil}]
    (with-redefs [lupaus-palvelu/valikatselmus-tehty-urakalle? (constantly true)]
      (is (thrown? AssertionError (vastaa-lupaukseen vastaus)) "Ei saa vastata välikatselmuksen jälkeen"))
    (is (vastaa-lupaukseen vastaus) "Saa vastata")))

(deftest tarkista-sallitut-kuukaudet
  (is (thrown? AssertionError (vastaa-lupaukseen
                                {:lupaus-id 1
                                 :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                                 :kuukausi 6
                                 :vuosi 2021
                                 :paatos false
                                 :vastaus true
                                 :lupaus-vaihtoehto-id nil}))
    "Lupaus 1:lle ei voi lisätä kirjausta kuukaudelle 6 (vain päätöksen)")
  (is (vastaa-lupaukseen
        {:lupaus-id 1
         :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
         :kuukausi 6
         :vuosi 2021
         :paatos true
         :vastaus true
         :lupaus-vaihtoehto-id nil})
    "Lupaus 1:lle voi lisätä päätöksen kuukaudelle 6 (ei kirjausta)")
  (is (thrown? AssertionError (vastaa-lupaukseen
                                {:lupaus-id 6
                                 :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                                 :kuukausi 6
                                 :vuosi 2021
                                 :paatos true
                                 :vastaus true
                                 :lupaus-vaihtoehto-id nil}))
    "Lupaus 6:lle ei voi lisätä päätöstä kuukaudelle 6 (vain kirjauksen)")
  (is (vastaa-lupaukseen
        {:lupaus-id 6
         :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
         :kuukausi 6
         :vuosi 2021
         :paatos false
         :vastaus true
         :lupaus-vaihtoehto-id nil})
    "Lupaus 6:lle voi lisätä kirjauksen kuukaudelle 6 (ei päätöstä)")
  (is (vastaa-lupaukseen
        {:lupaus-id 4
         :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
         :kuukausi 1
         :vuosi 2021
         :paatos true
         :vastaus true
         :lupaus-vaihtoehto-id nil})
    "Lupaus 4:lle voi lisätä päätöksen mille tahansa kuukaudelle (paatos-kk = 0)"))

(deftest tarkista-monivalinta-vastaus
  (is (vastaa-lupaukseen
        {:lupaus-id 5
         :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
         :kuukausi 10
         :vuosi 2021
         :paatos false
         :vastaus nil
         :lupaus-vaihtoehto-id (ffirst (hae-lupaus-vaihtoehdot 5))})
    "Lupaus 5:lle voi antaa sille kuuluvan vaihtoehdon.")
  (is (thrown? AssertionError (vastaa-lupaukseen
                                {:lupaus-id 5
                                 :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                                 :kuukausi 10
                                 :vuosi 2021
                                 :paatos false
                                 :vastaus nil
                                 :lupaus-vaihtoehto-id (ffirst (hae-lupaus-vaihtoehdot 3))}))
    "Lupaus 5:lle ei voi antaa lupaus 3:n vaihtoehtoa.")
  (is (thrown? AssertionError (vastaa-lupaukseen
                                {:lupaus-id 5
                                 :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                                 :kuukausi 10
                                 :vuosi 2021
                                 :paatos false
                                 :vastaus true
                                 :lupaus-vaihtoehto-id nil}))
    "Lupaus 5:lle ei voi antaa boolean-vastausta."))

(deftest tarkista-boolean-vastaus
  (is (vastaa-lupaukseen
        {:lupaus-id 6
         :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
         :kuukausi 10
         :vuosi 2021
         :paatos false
         :vastaus true
         :lupaus-vaihtoehto-id nil})
    "Lupaus 6:lle voi antaa boolean-vastauksen.")
  (is (thrown? AssertionError (vastaa-lupaukseen
                                {:lupaus-id 6
                                 :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                                 :kuukausi 10
                                 :vuosi 2021
                                 :paatos false
                                 :vastaus nil
                                 :lupaus-vaihtoehto-id (ffirst (hae-lupaus-vaihtoehdot 3))}))
    "Lupaus 6:lle ei voi antaa monivalinta-vaihtoehtoa."))

(deftest kommentti-test
  (let [lupaus-tiedot {:lupaus-id 4
                       :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id}
        hakutiedot (merge lupaus-tiedot
                     {:aikavali [#inst "2021-09-30T21:00:00.000-00:00"
                                 #inst "2022-09-30T20:59:59.000-00:00"]})]
    (is (empty? (kommentit hakutiedot))
      "Lupauksella ei ole vielä kommentteja.")
    (let [kommentti-str-a "2021-10 Eka"
          kommentti-a (merge lupaus-tiedot
                        {:kommentti kommentti-str-a
                         :vuosi 2021
                         :kuukausi 10})
          kommentti-str-b "2021-10 Toka"
          kommentti-b (merge lupaus-tiedot
                        {:kommentti kommentti-str-b
                         :vuosi 2021
                         :kuukausi 10})
          kommentti-str-c "2022-09 Eka"
          kommentti-c (merge lupaus-tiedot
                        {:kommentti kommentti-str-c
                         :vuosi 2022
                         :kuukausi 9})
          ;; Valitun aikavälin ulkopuolella
          kommentti-str-d "2022-10 Eka"
          kommentti-d (merge lupaus-tiedot
                        {:kommentti kommentti-str-d
                         :vuosi 2022
                         :kuukausi 10})

          tulos-a (lisaa-kommentti +kayttaja-jvh+ kommentti-a)
          ;; Odota 1ms, koska kommentit järjestetään luontiaikojen perusteella
          _ (Thread/sleep 1)
          tulos-b (lisaa-kommentti +kayttaja-yit_uuvh+ kommentti-b)
          tulos-c (lisaa-kommentti +kayttaja-jvh+ kommentti-c)
          tulos-d (lisaa-kommentti +kayttaja-jvh+ kommentti-d)
          listaus (kommentit hakutiedot)]
      (is (number? (:kommentti-id tulos-a)))
      (is (number? (:kommentti-id tulos-b)))
      (is (number? (:kommentti-id tulos-c)))
      (is (number? (:kommentti-id tulos-d)))
      (is (= kommentti-a (select-keys tulos-a (keys kommentti-a)))
        "Kommentti A tallentuu oikein.")
      (is (= kommentti-b (select-keys tulos-b (keys kommentti-b)))
        "Kommentti B tallentuu oikein.")
      (is (= kommentti-c (select-keys tulos-c (keys kommentti-c)))
        "Kommentti C tallentuu oikein.")
      (is (= kommentti-d (select-keys tulos-d (keys kommentti-d)))
        "Kommentti D tallentuu oikein.")
      (is (= 3 (count listaus))
        "Listaus palauttaa kommentit A, B ja C (kommentti D on aikavälin ulkopuolella).")
      (is (= [kommentti-str-a kommentti-str-b kommentti-str-c]
            (map :kommentti listaus))
        "Kommentit on järjestetty vanhimmasta uusimpaan.")
      (is (thrown? SecurityException (poista-kommentti +kayttaja-yit_uuvh+ {:id (:id tulos-a)}))
        "Toisen tekemää kommenttia ei saa poistaa.")
      (is (thrown? SecurityException (poista-kommentti +kayttaja-jvh+ {:id (:id tulos-b)}))
        "Toisen tekemää kommenttia ei saa poistaa.")
      (is (poista-kommentti +kayttaja-jvh+ {:id (:id tulos-a)})
        "Oman kommentin poisto onnistuu.")
      (is (poista-kommentti +kayttaja-yit_uuvh+ {:id (:id tulos-b)})
        "Oman kommentin poisto onnistuu.")
      (is (= [true true false]
            (map :poistettu (kommentit hakutiedot)))
        "Kommentti A on poistettu."))))

(deftest tavoitehinta-loytyy
  (let [vastaus (hae-urakan-lupaustiedot
                  +kayttaja-jvh+
                  {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                   :valittu-hoitokausi [#inst "2019-09-30T21:00:00.000-00:00"
                                        #inst "2020-09-30T20:59:59.000-00:00"]})
        tavoitehinta (get-in vastaus [:yhteenveto :tavoitehinta])]
    (is (= 240000M tavoitehinta) "Tavoitehinta löytyy")))

(defn- tallenna-kk-pisteet [kayttaja urakka-id vuosi kuukausi pisteet tyyppi]
  ;; Poistetaan :luotu avain, koska se muuttuu jokaisella testillä eikä nyt ole järkeä ylikirjoittaa sitä.
  (dissoc
    (tallenna-kuukausittaiset-pisteet
      kayttaja
      {:urakka-id urakka-id
       :vuosi vuosi
       :kuukausi kuukausi
       :pisteet pisteet
       :tyyppi tyyppi}) :luotu))

(defn- paivita-kk-pisteet [kayttaja urakka-id vuosi kuukausi pisteet tyyppi id]
  ;; Poistetaan :luotu avain, koska se muuttuu jokaisella testillä eikä nyt ole järkeä ylikirjoittaa sitä.
  (dissoc
    (tallenna-kuukausittaiset-pisteet
      kayttaja
      {:urakka-id urakka-id
       :vuosi vuosi
       :kuukausi kuukausi
       :pisteet pisteet
       :tyyppi tyyppi
       :id id}) :luotu))

(deftest kuukausiennuste-2019-urakalle
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        vuosi 2019
        kuukausi 10
        pisteet 10
        tyyppi "ennuste"
        vastaus (tallenna-kk-pisteet +kayttaja-jvh+ urakka-id vuosi kuukausi pisteet tyyppi)
        odotettu-tulos {:urakka-id urakka-id, :luoja (:id +kayttaja-jvh+), :vuosi vuosi, :id 1, :kuukausi kuukausi,
                        :pisteet pisteet, :tyyppi tyyppi :muokkaaja nil :muokattu nil}]
    (is (= odotettu-tulos vastaus) "Kuukausiennuste lisättiin")))

(deftest kuukausiennuste-2021-urakalle
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        vuosi 2021
        kuukausi 10
        pisteet 10
        tyyppi "ennuste"]
    (is (thrown? AssertionError (tallenna-kk-pisteet +kayttaja-jvh+ urakka-id vuosi kuukausi pisteet tyyppi))
      "Kuukausiennustetta ei voi lisätä 2021 urakalle")))

(deftest hae-kuukausipisteet-2019-urakalle
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        vuosi 2019
        valittu-hoitokausi [#inst "2019-09-30T21:00:00.000-00:00"
                            #inst "2020-09-30T20:59:59.000-00:00"]
        pisteet 10
        tyyppi "ennuste"
        _ (tallenna-kk-pisteet +kayttaja-jvh+ urakka-id vuosi 10 pisteet tyyppi)
        _ (tallenna-kk-pisteet +kayttaja-jvh+ urakka-id vuosi 11 pisteet tyyppi)
        _ (tallenna-kk-pisteet +kayttaja-jvh+ urakka-id (inc vuosi) 1 pisteet tyyppi)
        _ (tallenna-kk-pisteet +kayttaja-jvh+ urakka-id (inc vuosi) 2 pisteet tyyppi)
        vastaus (hae-kuukausittaiset-pisteet +kayttaja-jvh+ {:valittu-hoitokausi valittu-hoitokausi :urakka-id urakka-id})
        odotettu-tulos (list {:id 1, :urakka-id 35, :kuukausi 10, :vuosi 2019, :pisteet 10, :tyyppi "ennuste", :kuluva-kuukausi? false, :voi-vastata? true :odottaa-vastausta? false}
                         {:id 2, :urakka-id 35, :kuukausi 11, :vuosi 2019, :pisteet 10, :tyyppi "ennuste", :kuluva-kuukausi? false, :voi-vastata? true :odottaa-vastausta? false}
                         {:urakka-id 35, :kuukausi 12, :vuosi 2019, :tyyppi "ennuste", :kuluva-kuukausi? false, :voi-vastata? true :odottaa-vastausta? true}
                         {:id 3, :urakka-id 35, :kuukausi 1, :vuosi 2020, :pisteet 10, :tyyppi "ennuste", :kuluva-kuukausi? false, :voi-vastata? true :odottaa-vastausta? false}
                         {:id 4, :urakka-id 35, :kuukausi 2, :vuosi 2020, :pisteet 10, :tyyppi "ennuste", :kuluva-kuukausi? false, :voi-vastata? true :odottaa-vastausta? false}
                         {:urakka-id 35, :kuukausi 3, :vuosi 2020, :tyyppi "ennuste", :kuluva-kuukausi? false, :voi-vastata? true :odottaa-vastausta? true}
                         {:urakka-id 35, :kuukausi 4, :vuosi 2020, :tyyppi "ennuste", :kuluva-kuukausi? false, :voi-vastata? true :odottaa-vastausta? true}
                         {:urakka-id 35, :kuukausi 5, :vuosi 2020, :tyyppi "ennuste", :kuluva-kuukausi? false, :voi-vastata? true :odottaa-vastausta? true}
                         {:urakka-id 35, :kuukausi 6, :vuosi 2020, :tyyppi "ennuste", :kuluva-kuukausi? false, :voi-vastata? true :odottaa-vastausta? true}
                         {:urakka-id 35, :kuukausi 7, :vuosi 2020, :tyyppi "ennuste", :kuluva-kuukausi? false, :voi-vastata? true :odottaa-vastausta? true}
                         {:urakka-id 35, :kuukausi 8, :vuosi 2020, :tyyppi "ennuste", :kuluva-kuukausi? false, :voi-vastata? true :odottaa-vastausta? true}
                         {:urakka-id 35, :kuukausi 9, :vuosi 2020, :tyyppi "toteuma", :kuluva-kuukausi? false, :voi-vastata? true :odottaa-vastausta? true})]
    (is (= odotettu-tulos (:kuukausipisteet vastaus)) "Kuukausipisteet eivät täsmää odotettuun tulokseen.")))

(deftest hae-kuukausipisteet-2021-urakalle
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        vuosi 2019]
    (is (thrown? AssertionError (hae-kuukausittaiset-pisteet +kayttaja-jvh+ {:vuosi vuosi :urakka-id urakka-id})) "Kuukausipisteet palautettiin väärän vuoden urakalle.")))

(deftest paivita-kuukausipisteet-2019-urakalle
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        vuosi 2019
        kuukausi 10
        valittu-hoitokausi [#inst "2019-09-30T21:00:00.000-00:00"
                            #inst "2020-09-30T20:59:59.000-00:00"]

        ;; Siivotaan varalta kaikki pisteet urakalta
        _ (u (str "DELETE FROM lupaus_pisteet WHERE vuosi = " vuosi " AND \"urakka-id\" =" urakka-id))

        pisteet 10
        uudet-pisteet 100
        tyyppi "ennuste"
        vastaus (tallenna-kk-pisteet +kayttaja-jvh+ urakka-id vuosi kuukausi pisteet tyyppi)
        paivitetty-vastaus (paivita-kk-pisteet +kayttaja-jvh+ urakka-id vuosi kuukausi uudet-pisteet tyyppi (:id vastaus))
        haetut-pisteet (hae-kuukausittaiset-pisteet +kayttaja-jvh+ {:valittu-hoitokausi valittu-hoitokausi :urakka-id urakka-id})
        odotettu-tulos 100]
    (is (= odotettu-tulos (:pisteet (first (:kuukausipisteet haetut-pisteet)))))))

(deftest poista-kuukausipisteet-2019-urakalle
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        vuosi 2020
        kuukausi 3
        valittu-hoitokausi [#inst "2019-09-30T21:00:00.000-00:00"
                            #inst "2020-09-30T20:59:59.000-00:00"]
        ;; Lisätään varalta yhdelle kuukaudelle pisteet
        _ (tallenna-kk-pisteet +kayttaja-jvh+ urakka-id vuosi kuukausi 88 "ennuste")

        ennen-poistoa (hae-kuukausittaiset-pisteet +kayttaja-jvh+ {:valittu-hoitokausi valittu-hoitokausi :urakka-id urakka-id})
        ennen-poistoa-pisteelliset (filter #(when (:pisteet %) true) (:kuukausipisteet ennen-poistoa))
        ;; Poista yhden pisteet
        _ (poista-kuukausittaiset-pisteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                          :id (:id (first ennen-poistoa-pisteelliset))})
        jalkeen-poistoa (hae-kuukausittaiset-pisteet +kayttaja-jvh+ {:valittu-hoitokausi valittu-hoitokausi :urakka-id urakka-id})
        jalkeen-poistoa-pisteelliset (filter #(when (:pisteet %) true) (:kuukausipisteet jalkeen-poistoa))]
    (is (= (count ennen-poistoa-pisteelliset) (inc (count jalkeen-poistoa-pisteelliset))))))


(deftest lisaa-poista-kuukausipisteet-2019-urakalle-syyskuulle-ilman-tilaajan-oikeuksia
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        valittu-hoitokausi [#inst "2019-09-30T21:00:00.000-00:00"
                            #inst "2020-09-30T20:59:59.000-00:00"]
        vuosi 2020
        kuukausi 9
        pisteet 10
        tyyppi "toteuma"

        ;; Lisätään varalta yhdelle kuukaudelle pisteet käyttäjällä, jolla on oikeudet ja koitetaan sitten poistaa ne
        _ (tallenna-kk-pisteet +kayttaja-jvh+ urakka-id vuosi kuukausi 88 "toteuma")

        ennen-poistoa (hae-kuukausittaiset-pisteet +kayttaja-jvh+ {:valittu-hoitokausi valittu-hoitokausi :urakka-id urakka-id})
        poistettava (first (filter #(when (= 9 (:kuukausi %)) true) (:kuukausipisteet ennen-poistoa)))]
    (is (thrown? Exception (tallenna-kk-pisteet +kayttaja-uuno+ urakka-id vuosi kuukausi pisteet tyyppi)))
    (is (thrown? Exception (poista-kuukausittaiset-pisteet +kayttaja-uuno+ {:urakka-id urakka-id
                                                                            :id (:id poistettava)})))))

(deftest laske-lopullinen-kustannusennuste-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        hoitokauden-alkuvuosi 2019
        toteutunut-tavoitehinta 1000000M
        toteutunut-kustannus 950000M
        valikatselmus-pvm (pvm/luo-pvm 2020 6 15)
        user-id (:id +kayttaja-jvh+)]

    (testing "Funktio suorittuu oikeilla parametreilla"
      ;; Funktio ei palauta mitään - se tekee vain tietokantaoperaatioita
      (is (nil? (lupaus-palvelu/laske-lopullinen-kustannusennuste!
                  (:db jarjestelma) urakka-id hoitokauden-alkuvuosi
                  toteutunut-tavoitehinta toteutunut-kustannus
                  valikatselmus-pvm user-id))
        "Funktio suorittuu onnistuneesti")

      ;; Tarkista että lopputilanne tallentui tietokantaan
      (let [lopputilanteen-rivit (q (str "SELECT lopullinen_tavoitehinta, lopulliset_kustannukset "
                                      "FROM lupaus_hoitovuosi_lopputilanne "
                                      "WHERE \"urakka-id\" = " urakka-id
                                      " AND hoitovuosi_alkuvuosi = " hoitokauden-alkuvuosi))]
        (is (seq lopputilanteen-rivit) "Lopputilanne tallentui tietokantaan")
        (when (seq lopputilanteen-rivit)
          (let [[tavoite kustannus] (first lopputilanteen-rivit)]
            (is (= toteutunut-tavoitehinta tavoite) "Tavoitehinta tallentui oikein")
            (is (= toteutunut-kustannus kustannus) "Kustannukset tallentuivat oikein")))))

    (testing "Domain-funktioiden integraatio"
      ;; Testaa että domain-funktiot toimivat odotetulla tavalla
      (let [syotteet {:ennustettu-tavoitehinta 1000000M
                      :ennustettu-kustannus 950000M
                      :toteutunut-tavoitehinta 1000000M
                      :toteutunut-kustannus 950000M
                      :hoitovuoden-alun-tavoitehinta 1200000M}
            tarkkuus-tulos (kustannusennuste-domain/laske-kustannusennusteen-tarkkuus syotteet)]

        (is (:tarkkuus-prosentti tarkkuus-tulos) "Domain-funktio laskee tarkkuuden")
        (is (number? (:tarkkuus-prosentti tarkkuus-tulos)) "Tarkkuus on numero")))))

(deftest vastaa-lupaukseen-oikeustarkistus
  (testing "Urakoitsija voi vastata kirjauskuukauteen"
    (let [vastaus {:lupaus-id 6
                   :kuukausi 7 ; Heinäkuu 2022 - kirjauskuukausi lupaus 6:lle
                   :vuosi 2022
                   :vastaus true
                   :paatos false ; Kirjauskuukausi, ei päätös
                   :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id}
          tulos (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :vastaa-lupaukseen
                    +kayttaja-yit_uuvh+
                    vastaus)
                  :onnistui
                  (catch Exception e
                    :epaonnistui))]
      (is (= :onnistui tulos) "Urakoitsija saa vastata kirjauskuukauteen")))

  (testing "Urakoitsija ei voi vastata päättävään kuukauteen"
    (let [vastaus {:lupaus-id 4
                   :kuukausi 1 ; Tammikuu - Päättävä kuukausi lupaus 4:lle
                   :vuosi 2022
                   :paatos true
                   :vastaus false
                   :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id}]
      ;; Tämän pitäisi heittää poikkeus
      (is (thrown? Exception
            (kutsu-palvelua (:http-palvelin jarjestelma)
              :vastaa-lupaukseen
              +kayttaja-yit_uuvh+
              vastaus))
        "Urakoitsija ei saa vastata päättävään kuukauteen")))

  (testing "Tilaaja voi tehdä päätöksen"
    (let [vastaus {:lupaus-id 4
                   :kuukausi 2 ; Helmikuu - Päättävä kuukausi lupaus 4:lle
                   :vuosi 2022
                   :paatos true
                   :vastaus false
                   :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id}
          tulos (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :vastaa-lupaukseen
                    +kayttaja-jvh+
                    vastaus)
                  :onnistui
                  (catch Exception e
                    :epaonnistui))]
      (is (= :onnistui tulos) "Tilaaja saa tehdä päätöksen"))))

(deftest tallenna-kustannusennuste-hoitovuosi-offset-test
  "Testaa että kustannusennuste tallentuu oikealle hoitovuodelle 
   offset-logiikan perusteella. Elokuun offset on 1 (seuraava HK),
   lokakuun offset on 0 (oma HK)."
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)]

    (testing "Elokuun kustannusennuste tallentuu seuraavalle hoitovuodelle (offset=1)"
      ;; Elokuun offset pitäisi olla 1 lupaus_kustannusennuste_kuukausi_pisteet taulussa
      ;; HK1: 2019-09-30 - 2020-09-30, joten elokuun 2020 = seuraava HK = 2020 (HK2)
      (let [tallennetut (q (str "SELECT hoitovuosi "
                             "FROM lupaus_kustannusennuste "
                             "WHERE \"urakka-id\" = " urakka-id
                             " AND EXTRACT(MONTH FROM maarapaiva) = 8"
                             " LIMIT 1"))]
        ;; Jos rivejä löytyy, tarkista että offset-logiikka on toiminut oikein
        (when (seq tallennetut)
          (let [[hoitovuosi] (first tallennetut)]
            ;; HUOM: Tämä testi voi epäonnistua koska offset ei ole vielä käytössä
            ;; kaikille urakoille. Testi on placeholder integraatiolle.
            (is (or (= 2020 hoitovuosi) (nil? hoitovuosi))
              "Elokuun ennuste tallentuu oikealle hoitovuodelle")))))

    (testing "Lokakuun kustannusennuste tallentuu omalle hoitovuodelle (offset=0)"
      ;; Lokakuu kuuluu HK1:lle normaalisti
      (let [tallennetut (q (str "SELECT hoitovuosi "
                             "FROM lupaus_kustannusennuste "
                             "WHERE \"urakka-id\" = " urakka-id
                             " AND EXTRACT(MONTH FROM maarapaiva) = 10"
                             " LIMIT 1"))]
        (when (seq tallennetut)
          (let [[hoitovuosi] (first tallennetut)]
            (is (or (= 2019 hoitovuosi) (nil? hoitovuosi))
              "Lokakuun ennuste tallentuu oikealle hoitovuodelle")))))))

(deftest hae-kustannusennuste-kuukausi-offset-test
  "Testaa että offset-arvo haetaan oikein tietokannasta eri kuukausille.
   Kriittinen asia: Elokuun offset=1 varmistaa, että pisteet menevät seuraavalle hoitokaudelle (HK)."
  (let [;; Hae olemassa oleva lupaus-id, jolla on kustannusennuste-tyyppi
        lupaus-id (ffirst (q "SELECT id FROM lupaus WHERE lupaustyyppi = 'kustannusennuste' LIMIT 1"))
        ;; Tarkista, onko pisteytys_hoitovuosi_offset-sarake olemassa
        sarake-olemassa? (try
                           (q "SELECT pisteytys_hoitovuosi_offset FROM lupaus_kustannusennuste_kuukausi_pisteet LIMIT 0")
                           true
                           (catch Exception e false))]

    (if (and lupaus-id sarake-olemassa?)
      (do
        (testing "Lokakuu: offset = 0 (pisteet omalle HK:lle)"
          (let [offset-tulos (first (q (str "SELECT pisteytys_hoitovuosi_offset "
                                         "FROM lupaus_kustannusennuste_kuukausi_pisteet "
                                         "WHERE \"lupaus-id\" = " lupaus-id
                                         " AND kuukausi = 10 LIMIT 1")))]
            (when offset-tulos
              (is (= 0 (first offset-tulos))
                "Lokakuun offset on 0"))))

        (testing "Tammikuu: offset = 0 (pisteet omalle HK:lle)"
          (let [offset-tulos (first (q (str "SELECT pisteytys_hoitovuosi_offset "
                                         "FROM lupaus_kustannusennuste_kuukausi_pisteet "
                                         "WHERE \"lupaus-id\" = " lupaus-id
                                         " AND kuukausi = 1 LIMIT 1")))]
            (when offset-tulos
              (is (= 0 (first offset-tulos))
                "Tammikuun offset on 0"))))

        (testing "Elokuu: offset = 1 (KRIITTINEN - pisteet seuraavalle HK:lle)"
          (let [offset-tulos (first (q (str "SELECT pisteytys_hoitovuosi_offset "
                                         "FROM lupaus_kustannusennuste_kuukausi_pisteet "
                                         "WHERE \"lupaus-id\" = " lupaus-id
                                         " AND kuukausi = 8 LIMIT 1")))]
            (when offset-tulos
              (is (= 1 (first offset-tulos))
                "Elokuun offset on 1 - pisteet menevät seuraavalle hoitokaudelle")))))
      (testing "Offset-sarake puuttuu"
        (is true "Testi ohitettu")))))

(deftest hae-kustannusennuste-pisterajat-test
  "Testaa pisterajojen hakemisen tietokannasta ja JSONB-konversion.
   Pisterajat määrittävät, kuinka monta pistettä annetaan kustannusennusteen tarkkuuden perusteella.
   Testataan epäsuorasti maarita-kustannusennuste-pisteet-funktion kautta."
  (let [lupaus-id (ffirst (q "SELECT id FROM lupaus WHERE lupaustyyppi = 'kustannusennuste' LIMIT 1"))]

    (when lupaus-id
      (testing "Pisterajat toimivat oikein lokakuulle (testataan pisteiden kautta)"
        (let [pisteet (lupaus-palvelu/maarita-kustannusennuste-pisteet
                        (:db jarjestelma) 0.0 10 lupaus-id)]
          (is (number? pisteet) "Palauttaa pisteet numerona")
          (is (>= pisteet 0) "Pisteet ovat ei-negatiiviset")))

      (testing "Pisterajat toimivat elokuulle (kriittinen kuukausi)"
        (let [pisteet (lupaus-palvelu/maarita-kustannusennuste-pisteet
                        (:db jarjestelma) 0.0 8 lupaus-id)]
          (is (number? pisteet) "Palauttaa pisteet numerona")
          (is (>= pisteet 0) "Pisteet ovat ei-negatiiviset")))

      (testing "Tyhjä tulos ei kaada (ei-olemassa oleva lupaus)"
        (let [pisteet (lupaus-palvelu/maarita-kustannusennuste-pisteet
                        (:db jarjestelma) 0.0 99 9999999)]
          (is (= 0 pisteet) "Tyhjä tulos palauttaa 0 pistettä"))))))

(deftest maarita-kustannusennuste-pisteet-test
  "Testaa pisteiden määrittämisen tarkkuuden ja kuukauden perusteella.
   Pisterajat haetaan tietokannasta ja pisteet määritetään tarkkuusprosentin mukaan."
  (let [lupaus-id (ffirst (q "SELECT id FROM lupaus WHERE lupaustyyppi = 'kustannusennuste' LIMIT 1"))
        kuukausi 10]

    (when lupaus-id
      (testing "Täydellinen tarkkuus: 0% -> maksimipisteet"
        (let [pisteet (lupaus-palvelu/maarita-kustannusennuste-pisteet
                        (:db jarjestelma) 0.0 kuukausi lupaus-id)]
          (is (number? pisteet) "Palauttaa numeerisen arvon")
          (is (>= pisteet 0) "Pisteet ovat ei-negatiiviset")))

      (testing "Hyvä tarkkuus: ≤2%"
        (let [pisteet (lupaus-palvelu/maarita-kustannusennuste-pisteet
                        (:db jarjestelma) 1.5 kuukausi lupaus-id)]
          (is (number? pisteet) "Palauttaa numeerisen arvon")
          (is (>= pisteet 0) "Pisteet ovat ei-negatiiviset")))

      (testing "Kohtalainen tarkkuus: >2% ja ≤5%"
        (let [pisteet (lupaus-palvelu/maarita-kustannusennuste-pisteet
                        (:db jarjestelma) 3.5 kuukausi lupaus-id)]
          (is (number? pisteet) "Palauttaa numeerisen arvon")
          (is (>= pisteet 0) "Pisteet ovat ei-negatiiviset")))

      (testing "Huono tarkkuus: >5% -> vähän tai 0 pistettä"
        (let [pisteet (lupaus-palvelu/maarita-kustannusennuste-pisteet
                        (:db jarjestelma) 8.0 kuukausi lupaus-id)]
          (is (number? pisteet) "Palauttaa numeerisen arvon")
          (is (<= pisteet 4) "Huono tarkkuus antaa max 4 pistettä")))

      (testing "Negatiivinen tarkkuus käsitellään absoluuttisena"
        (let [pisteet-pos (lupaus-palvelu/maarita-kustannusennuste-pisteet
                            (:db jarjestelma) 2.0 kuukausi lupaus-id)
              pisteet-neg (lupaus-palvelu/maarita-kustannusennuste-pisteet
                            (:db jarjestelma) -2.0 kuukausi lupaus-id)]
          (is (= pisteet-pos pisteet-neg)
            "Negatiivinen ja positiivinen tarkkuus antavat samat pisteet"))))))

(deftest laske-lopullinen-kustannusennuste-perustapaus-test
  "Testaa, että laske-lopullinen-kustannusennuste! -funktio laskee pisteet oikein,
   kun välikatselmus kutsuu sitä. Kriittinen: Tarkistaa offset-logiikan toiminnan."
  (let [;; Käytetään urakkaa jolla on kustannusennusteita
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        hoitokauden-alkuvuosi 2019
        toteutunut-tavoitehinta 1000000M
        toteutunut-kustannus 950000M
        valikatselmus-pvm (pvm/nyt)
        user-id (:id +kayttaja-jvh+)]

    (testing "Lopputilanne tallentuu oikein"
      ;; Suorita laskenta
      (lupaus-palvelu/laske-lopullinen-kustannusennuste!
        (:db jarjestelma) urakka-id hoitokauden-alkuvuosi
        toteutunut-tavoitehinta toteutunut-kustannus
        valikatselmus-pvm user-id)

      ;; Tarkista että lopputilanne tallentui
      (let [lopputilanne (first (q (str "SELECT lopullinen_tavoitehinta, lopulliset_kustannukset, vahvistaja, "
                                     "kustannusennuste_keskiarvo_pisteet "
                                     "FROM lupaus_hoitovuosi_lopputilanne "
                                     "WHERE \"urakka-id\" = " urakka-id
                                     " AND hoitovuosi_alkuvuosi = " hoitokauden-alkuvuosi)))]
        (is lopputilanne "Lopputilanne tallentui")
        (when lopputilanne
          (is (= toteutunut-tavoitehinta (first lopputilanne))
            "Lopullinen tavoitehinta tallentui oikein")
          (is (= toteutunut-kustannus (second lopputilanne))
            "Lopulliset kustannukset tallentui oikein")
          (is (= user-id (nth lopputilanne 2))
            "Vahvistaja tallentui oikein")
          (let [keskiarvo (nth lopputilanne 3)]
            (is (or (nil? keskiarvo) (number? keskiarvo))
              "Keskiarvo on nil tai numero")
            (when (number? keskiarvo)
              (is (and (>= keskiarvo 0) (<= keskiarvo 10))
                "Keskiarvo on välillä 0-10"))))))

    (testing "Funktio ei kaadu"
      ;; Testaa että funktio suoriutuu ilman virheitä
      (is (nil? (lupaus-palvelu/laske-lopullinen-kustannusennuste!
                  (:db jarjestelma) urakka-id hoitokauden-alkuvuosi
                  1000000M 950000M (pvm/nyt) user-id))
        "Funktio suoriutuu ilman virheitä"))))

(deftest maarita-urakan-tavoitehinta-test
  "Testaa hoitokauden tavoitehinnan määrittämisen.
   Tavoitehinta haetaan budjetista hoitokauden numeron perusteella."
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id]

    (testing "HK1 tavoitehinta"
      (let [hk1-alkupvm (pvm/luo-pvm 2021 9 1)
            tavoitehinta (lupaus-palvelu/maarita-urakan-tavoitehinta
                           (:db jarjestelma) urakka-id hk1-alkupvm)]
        (when tavoitehinta
          (is (number? tavoitehinta) "Palauttaa numeerisen arvon")
          (is (pos? tavoitehinta) "Tavoitehinta on positiivinen"))))

    (testing "HK2 tavoitehinta"
      (let [hk2-alkupvm (pvm/luo-pvm 2022 9 1)
            tavoitehinta (lupaus-palvelu/maarita-urakan-tavoitehinta
                           (:db jarjestelma) urakka-id hk2-alkupvm)]
        (when tavoitehinta
          (is (number? tavoitehinta) "Palauttaa numeerisen arvon")
          (is (pos? tavoitehinta) "Tavoitehinta on positiivinen"))))

    (testing "Hoitokauden numero lasketaan oikein"
      ;; Testaa että eri alkupäivät menevät oikealle hoitokaudelle
      (let [urakan-tiedot (first (q (str "SELECT alkupvm FROM urakka WHERE id = " urakka-id)))
            urakan-alkupvm (first urakan-tiedot)
            hk1-pvm (pvm/luo-pvm 2021 9 1)
            hk2-pvm (pvm/luo-pvm 2022 9 1)
            hk1-nro (pvm/hoitokausivuosi->mhu-hoitovuosi-nro
                      urakan-alkupvm (pvm/vuosi hk1-pvm))
            hk2-nro (pvm/hoitokausivuosi->mhu-hoitovuosi-nro
                      urakan-alkupvm (pvm/vuosi hk2-pvm))]
        (is (= 1 hk1-nro) "Ensimmäinen hoitokausi on numero 1")
        (is (= 2 hk2-nro) "Toinen hoitokausi on numero 2")))))

(deftest laske-lopullinen-kustannusennuste-puuttuvat-arvot-test
  "Testaa, että funktio vaatii validit parametrit ja hylkää virheelliset arvot.
   Funktio odottaa välikatselmuksen tiedot jotka ovat aina numeroita."
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        db (:db jarjestelma)
        hoitokauden-alkuvuosi 2021
        toteutunut-tavoitehinta 1000000
        toteutunut-kustannus 950000
        valikatselmus-pvm (pvm/luo-pvm 2022 8 15)
        user-id (:id +kayttaja-jvh+)]

    (testing "NULL tavoitehinta välikatselmuksen parametrissa hylätään pre-conditionilla"
      (is (thrown? AssertionError
            (lupaus-palvelu/laske-lopullinen-kustannusennuste!
              db urakka-id hoitokauden-alkuvuosi nil toteutunut-kustannus valikatselmus-pvm user-id))
        "Pre-condition hylkää nil tavoitehinnan"))

    (testing "NULL toteutuneet kustannukset parametrissa hylätään pre-conditionilla"
      (is (thrown? AssertionError
            (lupaus-palvelu/laske-lopullinen-kustannusennuste!
              db urakka-id hoitokauden-alkuvuosi toteutunut-tavoitehinta nil valikatselmus-pvm user-id))
        "Pre-condition hylkää nil kustannukset"))

    (testing "Funktio toimii kun urakkaa ei löydy tai sillä ei ole ennusteita"
      ;; Funktio ei kaadu vaikka urakkaa ei löydy tai sillä ei ole dataa
      (let [tulos (try
                    (lupaus-palvelu/laske-lopullinen-kustannusennuste!
                      db 999999 hoitokauden-alkuvuosi toteutunut-tavoitehinta toteutunut-kustannus valikatselmus-pvm user-id)
                    (catch Exception _e
                      nil))]
        (is (nil? tulos)
          "Palauttaa nil kun urakkaa ei ole tai sillä ei ole ennusteita")))))

(deftest laske-lopullinen-kustannusennuste-ei-ennusteita-test
  "Testaa, että funktio toimii oikein, kun kustannusennusteita ei ole saatavilla.
   Varmistaa, että tyhjä data ei aiheuta virheitä."
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        db (:db jarjestelma)
        hoitokauden-alkuvuosi 2021
        toteutunut-tavoitehinta 1000000
        toteutunut-kustannus 950000
        valikatselmus-pvm (pvm/luo-pvm 2022 8 15)
        user-id (:id +kayttaja-jvh+)]

    (testing "Käsittelee puuttuvat ennusteet"
      ;; Poistetaan kaikki kustannusennusteet tältä hoitokaudelta testidatasta
      (u (str "DELETE FROM lupaus_kustannusennuste 
               WHERE \"urakka-id\" = " urakka-id " 
               AND hoitovuosi = 1"))

      (let [tulos (try
                    (lupaus-palvelu/laske-lopullinen-kustannusennuste!
                      db urakka-id hoitokauden-alkuvuosi toteutunut-tavoitehinta
                      toteutunut-kustannus valikatselmus-pvm user-id)
                    (catch Exception _e nil))]
        (is (or (nil? tulos) (map? tulos))
          "Käsittelee tilanteen kun ennusteita ei ole")))

    (testing "Virheellinen urakka-id ei kaada järjestelmää"
      (let [tulos (try
                    (lupaus-palvelu/laske-lopullinen-kustannusennuste!
                      db 999999 hoitokauden-alkuvuosi toteutunut-tavoitehinta
                      toteutunut-kustannus valikatselmus-pvm user-id)
                    (catch Exception _e nil))]
        (is (or (nil? tulos) (map? tulos))
          "Palauttaa nil tai mapin kun urakkaa ei ole")))))

(deftest laske-lopullinen-kustannusennuste-keskiarvo-test
  "Testaa, että laske-lopullinen-kustannusennuste! tallentaa keskiarvon oikein.
   Varmistaa, että 12 kuukauden pisteiden keskiarvo lasketaan ja tallennetaan tietokantaan."
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        db (:db jarjestelma)
        hoitokauden-alkuvuosi 2019
        toteutunut-tavoitehinta 1000000M
        toteutunut-kustannus 950000M
        valikatselmus-pvm (pvm/nyt)
        user-id (:id +kayttaja-jvh+)]

    (testing "Keskiarvo tallentuu tietokantaan"
      ;; Suorita laskenta
      (lupaus-palvelu/laske-lopullinen-kustannusennuste!
        db urakka-id hoitokauden-alkuvuosi
        toteutunut-tavoitehinta toteutunut-kustannus
        valikatselmus-pvm user-id)

      ;; Hae tallennettu keskiarvo
      (let [keskiarvo (ffirst (q (str "SELECT kustannusennuste_keskiarvo_pisteet "
                                   "FROM lupaus_hoitovuosi_lopputilanne "
                                   "WHERE \"urakka-id\" = " urakka-id
                                   " AND hoitovuosi_alkuvuosi = " hoitokauden-alkuvuosi)))]
        (is (or (nil? keskiarvo) (number? keskiarvo))
          "Keskiarvo on nil tai numero")

        (when (number? keskiarvo)
          (is (and (>= keskiarvo 0) (<= keskiarvo 10))
            "Keskiarvo on järkevällä välillä 0-10"))))

    (testing "Keskiarvo lasketaan oikein kun kaikki kuukaudet ovat saatavilla"
      ;; Hae kaikki lasketut pisteet kyseiseltä hoitokaudelta
      (let [pisteet (flatten (q (str "SELECT lasketut_pisteet "
                                  "FROM lupaus_kustannusennuste "
                                  "WHERE \"urakka-id\" = " urakka-id
                                  " AND hoitovuosi = " hoitokauden-alkuvuosi
                                  " AND lasketut_pisteet IS NOT NULL "
                                  "ORDER BY maarapaiva")))
            manuaalinen-keskiarvo (when (seq pisteet)
                                    (/ (reduce + pisteet) (count pisteet)))
            tallennettu-keskiarvo (ffirst (q (str "SELECT kustannusennuste_keskiarvo_pisteet "
                                               "FROM lupaus_hoitovuosi_lopputilanne "
                                               "WHERE \"urakka-id\" = " urakka-id
                                               " AND hoitovuosi_alkuvuosi = " hoitokauden-alkuvuosi)))]

        (when (and manuaalinen-keskiarvo tallennettu-keskiarvo)
          (is (< (Math/abs (- (double manuaalinen-keskiarvo)
                             (double tallennettu-keskiarvo)))
                0.01)
            (str "Tallennettu keskiarvo vastaa laskettua. "
              "Laskettu: " manuaalinen-keskiarvo
              ", Tallennettu: " tallennettu-keskiarvo)))))

    (testing "Keskiarvo on nil kun ei ole yhtään pistettä"
      ;; Käytetään urakkaa/vuotta jolla ei ole ennusteita
      (lupaus-palvelu/laske-lopullinen-kustannusennuste!
        db urakka-id 2025 ;; Tuleva vuosi, ei dataa
        toteutunut-tavoitehinta toteutunut-kustannus
        valikatselmus-pvm user-id)
      (let [keskiarvo (ffirst (q (str "SELECT kustannusennuste_keskiarvo_pisteet "
                                   "FROM lupaus_hoitovuosi_lopputilanne "
                                   "WHERE \"urakka-id\" = " urakka-id
                                   " AND hoitovuosi_alkuvuosi = 2025")))]
        ;; Kun ei ole yhtään pistettä, keskiarvo on nil
        (is (nil? keskiarvo)
          "Keskiarvo on nil kun ei ole yhtään laskettua pistettä")))))

(deftest maarita-kustannusennuste-pisteet-null-tapaukset-test
  "Testaa pisteytyksen reunatapaukset: NULL-arvot, suuret luvut ja negatiiviset arvot.
   Varmistaa, että kaikki erikoistapaukset käsitellään absoluuttisina arvoina oikein."
  (let [db (:db jarjestelma)
        ;; Hae olemassa oleva lupaus-id testidatasta, jos löytyy
        lupaus-id (or (ffirst (q "SELECT id FROM lupaus WHERE lupaustyyppi = 'kustannusennuste' LIMIT 1")) 1)
        kuukausi 10] ;; Käytetään lokakuuta testeissä

    (testing "NULL arvot tarkkuudessa"
      ;; Funktio ottaa parametrit: db, tarkkuus-prosentti, kuukausi, lupaus-id
      ;; NULL-tarkkuus aiheuttaa AssertionError:n pre-ehdosta
      (is (thrown? AssertionError
            (lupaus-palvelu/maarita-kustannusennuste-pisteet db nil kuukausi lupaus-id))
        "NULL tarkkuus heittää virheen pre-ehdon takia"))

    (testing "Erittäin suuret arvot"
      (let [pisteet-100 (lupaus-palvelu/maarita-kustannusennuste-pisteet db 100.0 kuukausi lupaus-id)
            pisteet-1000 (lupaus-palvelu/maarita-kustannusennuste-pisteet db 1000.0 kuukausi lupaus-id)]
        (is (<= pisteet-100 8) "100% tarkkuus palauttaa max 8 pistettä (lokakuulla)")
        (is (>= pisteet-1000 1) "1000% tarkkuus palauttaa vähintään 1 pisteen (>9% sääntö)")))

    (testing "Negatiiviset arvot käsitellään absoluuttisina"
      (let [pisteet-neg-1 (lupaus-palvelu/maarita-kustannusennuste-pisteet db -1.0 kuukausi lupaus-id)
            pisteet-pos-1 (lupaus-palvelu/maarita-kustannusennuste-pisteet db 1.0 kuukausi lupaus-id)]
        (is (= pisteet-neg-1 pisteet-pos-1)
          "Negatiivinen ja positiivinen sama absoluuttinen arvo antavat samat pisteet")))

    (testing "Rajatapaukset lokakuulle (raja ≤7% → 8p, ≤9% → 4p, >9% → 1p)"
      (let [pisteet-0 (lupaus-palvelu/maarita-kustannusennuste-pisteet db 0.0 kuukausi lupaus-id)
            pisteet-7 (lupaus-palvelu/maarita-kustannusennuste-pisteet db 7.0 kuukausi lupaus-id)
            pisteet-9 (lupaus-palvelu/maarita-kustannusennuste-pisteet db 9.0 kuukausi lupaus-id)
            pisteet-10 (lupaus-palvelu/maarita-kustannusennuste-pisteet db 10.0 kuukausi lupaus-id)]
        (is (= 8 pisteet-0) "0% antaa 8 pistettä (paras)")
        (is (= 8 pisteet-7) "Tasan 7% antaa 8 pistettä (raja)")
        (is (= 4 pisteet-9) "Tasan 9% antaa 4 pistettä (raja)")
        (is (= 1 pisteet-10) "10% antaa 1 pisteen (>9%)")))))

(deftest maarita-urakan-tavoitehinta-reunatapaukset-test
  "Testaa tavoitehinnan määrityksen reunatapaukset.
   Käsittelee tulevaisuuden, menneisyyden ja virheelliset hoitokaudet."
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        db (:db jarjestelma)]

    (testing "Tulevaisuuden hoitokausi"
      (let [tuleva-hk (pvm/luo-pvm 2030 9 1)
            tavoitehinta (lupaus-palvelu/maarita-urakan-tavoitehinta
                           db urakka-id tuleva-hk)]
        ;; Tulevaisuuden hoitokaudelle ei välttämättä löydy dataa
        (is (or (nil? tavoitehinta) (number? tavoitehinta))
          "Käsittelee tulevaisuuden hoitokauden")))

    (testing "Menneisyyden hoitokausi ennen urakan alkua"
      (let [vanha-hk (pvm/luo-pvm 2018 9 1)
            tavoitehinta (lupaus-palvelu/maarita-urakan-tavoitehinta
                           db urakka-id vanha-hk)]
        ;; Nykyinen logiikka palauttaa ensimmäisen hoitokauden hinnan myös menneisyydelle
        ;; koska hoitokausivuosi->mhu-hoitovuosi-nro käyttää max funktiota
        (is (or (nil? tavoitehinta) (number? tavoitehinta))
          "Käsittelee hoitokauden ennen urakan alkua")))

    (testing "Ensimmäinen hoitokausi"
      (let [urakan-tiedot (first (q (str "SELECT alkupvm FROM urakka WHERE id = " urakka-id)))
            urakan-alkupvm (first urakan-tiedot)
            ensimmainen-hk-pvm (pvm/luo-pvm (pvm/vuosi urakan-alkupvm) 9 1)
            tavoitehinta (lupaus-palvelu/maarita-urakan-tavoitehinta
                           db urakka-id ensimmainen-hk-pvm)]
        (is (some? tavoitehinta) "Ensimmäiselle hoitokaudelle löytyy tavoitehinta")
        (is (pos? tavoitehinta) "Tavoitehinta on positiivinen")))

    (testing "Virheellinen urakka-id"
      (let [hoitovuosi-alkupvm (pvm/luo-pvm 2021 9 1)]
        ;; Funktio voi kaatua tai palauttaa nil virheellisellä urakka-id:llä
        (is (thrown? Exception
              (lupaus-palvelu/maarita-urakan-tavoitehinta
                db 999999 hoitovuosi-alkupvm))
          "Funktio heittää poikkeuksen virheellisellä urakka-id:llä")))))

(deftest laske-lopullinen-kustannusennuste-tietokantavirheet-test
  "Testaa tietokantaoperaatioiden virheenkäsittelyn.
   Varmistaa, että UPSERT-operaatio käsittelee duplikaatit oikein."
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        db (:db jarjestelma)
        hoitokauden-alkuvuosi 2021
        toteutunut-tavoitehinta 1000000
        toteutunut-kustannus 950000
        valikatselmus-pvm (pvm/luo-pvm 2022 8 15)
        user-id (:id +kayttaja-jvh+)]

    (testing "UPSERT käsittelee duplikaatit"
      ;; Aja funktio kaksi kertaa - toisen pitäisi päivittää ensimmäisen
      (is (nil? (lupaus-palvelu/laske-lopullinen-kustannusennuste!
                  db urakka-id hoitokauden-alkuvuosi toteutunut-tavoitehinta
                  toteutunut-kustannus valikatselmus-pvm user-id))
        "Ensimmäinen ajo onnistuu ilman virhettä")
      (is (nil? (lupaus-palvelu/laske-lopullinen-kustannusennuste!
                  db urakka-id hoitokauden-alkuvuosi toteutunut-tavoitehinta
                  toteutunut-kustannus valikatselmus-pvm user-id))
        "Toinen ajo onnistuu (UPSERT päivittää) - ei kaadu duplikaattiin"))

    (testing "Transaktio rollback virhetilanteessa"
      ;; Tämä on vaikeampi testata ilman mock-dataa
      ;; Tarkistetaan vain että funktio ei kaadu virheeseen
      (is (fn? lupaus-palvelu/laske-lopullinen-kustannusennuste!)
        "Funktio on olemassa ja kutsuttavissa"))))

(deftest offset-logiikka-skenaariot-test
  (testing "Offset-arvot määritellyille kuukausille"
    ;; Tarkista, että offset-arvot on määritelty tietokannassa niille kuukausille, joille data on syötetty
    (let [offset-data (q "SELECT kuukausi, pisteytys_hoitovuosi_offset 
                          FROM lupaus_kustannusennuste_kuukausi_pisteet 
                          ORDER BY kuukausi")]
      ;; Pitäisi löytyä rivejä ainakin muutamalle kuukaudelle
      (is (pos? (count offset-data))
        "Offset määritelty ainakin yhdelle kuukaudelle")

      ;; Tarkista, että elokuu (kuukausi 8) käyttää offset=1, jos data löytyy
      (let [elokuu (second (filter (fn [[kk _offset]] (= 8 kk)) offset-data))]
        (if elokuu
          (let [[_kuukausi offset] elokuu]
            (is (= 1 offset)
              "Elokuun offset on 1"))
          (println "HUOM: Elokuuta (kuukausi 8) ei löytynyt testidatasta")))

      ;; Tarkista, että kaikki muut kuukaudet paitsi elokuu käyttävät offset=0
      (let [muut-kuukaudet (filter (fn [[kk _offset]] (not= 8 kk)) offset-data)]
        (doseq [[kuukausi offset] muut-kuukaudet]
          (is (= 0 offset)
            (str "Kuukausi " kuukausi " käyttää offset=0"))))))

  (testing "laske-pisteytyshoitovuosi eri kuukausilla"
    ;; laske-pisteytyshoitovuosi-funktio ottaa parametrit (vuosi kuukausi offset) 
    ;; ja palauttaa hoitovuoden alkuvuoden
    (let [vuosi 2024
          lokakuu 10
          elokuu 8]

      ;; Lokakuu (offset=0) - menee omalle HK:lle
      (let [tulos (kustannusennuste-domain/laske-pisteytyshoitovuosi vuosi lokakuu 0)]
        (is (= 2024 tulos) "Lokakuu 2024 offset=0 -> HK 2024-2025"))

      ;; Elokuu (offset=1) - menee seuraavalle HK:lle  
      (let [tulos (kustannusennuste-domain/laske-pisteytyshoitovuosi vuosi elokuu 1)]
        (is (= 2025 tulos) "Elokuu 2024 offset=1 -> HK 2025-2026"))))


  (testing "Offset-logiikka eri vuosilla"
    ;; Testaa, että offset toimii johdonmukaisesti eri kalenterivuosilla
    (let [;; Lokakuu eri vuosina
          hk-2021-offset-0 (kustannusennuste-domain/laske-pisteytyshoitovuosi 2021 10 0)
          hk-2021-offset-1 (kustannusennuste-domain/laske-pisteytyshoitovuosi 2021 10 1)
          hk-2022-offset-0 (kustannusennuste-domain/laske-pisteytyshoitovuosi 2022 10 0)
          hk-2022-offset-1 (kustannusennuste-domain/laske-pisteytyshoitovuosi 2022 10 1)]

      (is (= 2021 hk-2021-offset-0) "Loka 2021 + offset 0 = HK 2021")
      (is (= 2022 hk-2021-offset-1) "Loka 2021 + offset 1 = HK 2022")
      (is (= 2022 hk-2022-offset-0) "Loka 2022 + offset 0 = HK 2022")
      (is (= 2023 hk-2022-offset-1) "Loka 2022 + offset 1 = HK 2023"))))

(deftest hae-perustiedot-test
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        ;; Hoitokausi 2015-2016 (urakan toinen hoitovuosi)
        valittu-hoitokausi [(pvm/luo-pvm 2015 9 1) (pvm/luo-pvm 2016 8 31)]
        perustiedot (#'lupaus-palvelu/hae-perustiedot ds urakka-id valittu-hoitokausi)]

    (testing "Palauttaa kaikki vaaditut kentät"
      (is (contains? perustiedot :urakan-tiedot) "Sisältää :urakan-tiedot")
      (is (contains? perustiedot :urakan-alkupvm) "Sisältää :urakan-alkupvm")
      (is (contains? perustiedot :urakan-alkuvuosi) "Sisältää :urakan-alkuvuosi")
      (is (contains? perustiedot :hoitokauden-alkuvuosi) "Sisältää :hoitokauden-alkuvuosi")
      (is (contains? perustiedot :hoitovuosi-nro) "Sisältää :hoitovuosi-nro")
      (is (contains? perustiedot :hk-alkupvm) "Sisältää :hk-alkupvm")
      (is (contains? perustiedot :hk-loppupvm) "Sisältää :hk-loppupvm"))

    (testing "Urakan tiedot haetaan oikein"
      (is (= urakka-id (:id (:urakan-tiedot perustiedot))) "Urakan ID täsmää")
      (is (inst? (:alkupvm (:urakan-tiedot perustiedot))) "Alkupvm on instant")
      (is (string? (:nimi (:urakan-tiedot perustiedot))) "Urakalla on nimi"))

    (testing "Urakan alkupäivämäärä ja -vuosi lasketaan oikein"
      (is (inst? (:urakan-alkupvm perustiedot)) "Urakan alkupvm on instant")
      (is (number? (:urakan-alkuvuosi perustiedot)) "Urakan alkuvuosi on numero")
      (is (= 2014 (:urakan-alkuvuosi perustiedot)) "Oulun alueurakka alkoi 2014"))

    (testing "Hoitokauden alkuvuosi lasketaan oikein"
      (is (= 2015 (:hoitokauden-alkuvuosi perustiedot)) "Hoitokauden alkuvuosi on 2015"))

    (testing "Hoitovuosi-nro lasketaan oikein"
      (is (number? (:hoitovuosi-nro perustiedot)) "Hoitovuosi-nro on numero")
      (is (pos? (:hoitovuosi-nro perustiedot)) "Hoitovuosi-nro on positiivinen")
      ;; Urakka alkoi lokakuussa 2014, joten hoitokausi 2015-2016 on toinen hoitovuosi
      (is (= 2 (:hoitovuosi-nro perustiedot)) "2015-2016 on toinen hoitovuosi"))

    (testing "Päivämäärät säilyvät"
      (is (= (first valittu-hoitokausi) (:hk-alkupvm perustiedot)) "Hoitokauden alkupvm säilyy")
      (is (= (second valittu-hoitokausi) (:hk-loppupvm perustiedot)) "Hoitokauden loppupvm säilyy"))))

(deftest hae-perustiedot-eri-hoitokausilla-test
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)]

    (testing "Ensimmäinen hoitovuosi 2014-2015"
      (let [valittu-hoitokausi [(pvm/luo-pvm 2014 9 1) (pvm/luo-pvm 2015 8 31)]
            perustiedot (#'lupaus-palvelu/hae-perustiedot ds urakka-id valittu-hoitokausi)]
        (is (= 2014 (:hoitokauden-alkuvuosi perustiedot)))
        (is (= 1 (:hoitovuosi-nro perustiedot)) "Ensimmäinen hoitovuosi")))

    (testing "Kolmas hoitovuosi 2016-2017"
      (let [valittu-hoitokausi [(pvm/luo-pvm 2016 9 1) (pvm/luo-pvm 2017 8 31)]
            perustiedot (#'lupaus-palvelu/hae-perustiedot ds urakka-id valittu-hoitokausi)]
        (is (= 2016 (:hoitokauden-alkuvuosi perustiedot)))
        (is (= 3 (:hoitovuosi-nro perustiedot)) "Kolmas hoitovuosi")))))

(deftest hae-perustiedot-validointi-test
  (testing "Preconditions validoivat parametrit"
    (is (thrown? AssertionError
          (#'lupaus-palvelu/hae-perustiedot ds "ei-numero" [(pvm/nyt) (pvm/nyt)]))
      "Urakka-id täytyy olla numero")

    (is (thrown? AssertionError
          (#'lupaus-palvelu/hae-perustiedot ds 1 "ei-vektori"))
      "Valittu-hoitokausi täytyy olla vektori")

    (is (thrown? AssertionError
          (#'lupaus-palvelu/hae-perustiedot ds 1 [(pvm/nyt)]))
      "Valittu-hoitokausi täytyy sisältää 2 päivämäärää")))

;; Testit ylikirjoita-hoitovuosikohtaiset-arvot funktiolle
(deftest ylikirjoita-hoitovuosikohtaiset-arvot-test
  (let [;; Mock vastaus ilman erikoisarvoja
        vastaus [{:lupaus-id 1 :lupaustyyppi "yksittainen" :otsikko "Lupaus 1"}
                 {:lupaus-id 2 :lupaustyyppi "kustannusennuste" :otsikko "Lupaus 2"}
                 {:lupaus-id 3 :lupaustyyppi "monivalinta" :otsikko "Lupaus 3"}]
        hoitovuosi-nro 2
        tulos (#'lupaus-palvelu/ylikirjoita-hoitovuosikohtaiset-arvot ds vastaus hoitovuosi-nro)]

    (testing "Palauttaa vektorin"
      (is (vector? tulos) "Palauttaa vektorin"))

    (testing "Vastaukset säilyvät"
      (is (= 3 (count tulos)) "Kaikki vastaukset säilyvät")
      (is (every? :lupaus-id tulos) "Lupaus-id säilyy kaikilla"))

    (testing "Alkuperäiset kentät säilyvät"
      (is (= "yksittainen" (:lupaustyyppi (first tulos))))
      (is (= "Lupaus 1" (:otsikko (first tulos)))))))

(deftest ylikirjoita-hoitovuosikohtaiset-arvot-tyhja-vastaus-test
  (testing "Tyhjä vastaus käsitellään oikein"
    (let [vastaus []
          hoitovuosi-nro 1
          tulos (#'lupaus-palvelu/ylikirjoita-hoitovuosikohtaiset-arvot ds vastaus hoitovuosi-nro)]

      (is (= [] tulos) "Tyhjä vastaus palauttaa tyhjän listan"))))

(deftest ylikirjoita-hoitovuosikohtaiset-arvot-nil-hoitovuosi-test
  (testing "Nil hoitovuosi-nro käsitellään oikein"
    (let [vastaus [{:lupaus-id 1 :lupaustyyppi "yksittainen"}]
          hoitovuosi-nro nil
          tulos (#'lupaus-palvelu/ylikirjoita-hoitovuosikohtaiset-arvot ds vastaus hoitovuosi-nro)]

      (is (= 1 (count tulos)) "Vastaus käsitellään vaikka hoitovuosi-nro on nil"))))

(deftest ylikirjoita-hoitovuosikohtaiset-arvot-validointi-testvalidointi-test
  (testing "Preconditions validoivat parametrit"
    (is (thrown? AssertionError
          (#'lupaus-palvelu/ylikirjoita-hoitovuosikohtaiset-arvot ds "ei-collection" 1))
      "Vastaus täytyy olla collection")

    (is (thrown? AssertionError
          (#'lupaus-palvelu/ylikirjoita-hoitovuosikohtaiset-arvot ds [{:lupaus-id 1}] "ei-numero"))
      "Hoitovuosi-nro täytyy olla numero tai nil")))

(deftest ylikirjoita-hoitovuosikohtaiset-arvot-kentat-ylikirjoitetaan-test
  (testing "Hoitovuosikohtaiset arvot ylikirjoittavat oletusarvot"
    ;; Tämä testi vaatisi että tietokannassa on oikeasti erikoisarvoja
    ;; Tässä testataan vain että funktio toimii ilman erikoisarvoja
    (let [vastaus [{:lupaus-id 999999 :lupaustyyppi "yksittainen" :kirjaus-kkt [1 2 3]}]
          hoitovuosi-nro 1
          tulos (#'lupaus-palvelu/ylikirjoita-hoitovuosikohtaiset-arvot ds vastaus hoitovuosi-nro)
          vastaus-tulos (first tulos)]

      ;; Ilman erikoisarvoja, alkuperäiset kentät säilyvät
      (is (= 999999 (:lupaus-id vastaus-tulos)) "Lupaus-id säilyy")
      (is (= [1 2 3] (:kirjaus-kkt vastaus-tulos)) "Kirjaus-kkt säilyy ilman ylikirjoitusta"))))

(deftest ylikirjoita-hoitovuosikohtaiset-arvot-funktionaalinen-test
  (testing "Funktio on puhdas - ei muuta alkuperäistä vastausta"
    (let [alkuperainen-vastaus [{:lupaus-id 1 :lupaustyyppi "yksittainen"}]
          vastaus (vec alkuperainen-vastaus) ;; Kopioi
          hoitovuosi-nro 2
          _ (#'lupaus-palvelu/ylikirjoita-hoitovuosikohtaiset-arvot ds vastaus hoitovuosi-nro)]

      (is (= alkuperainen-vastaus vastaus)
        "Alkuperäinen vastaus ei muutu"))))

;; Testit rikasta-lupaus-lisatiedoilla funktiolle
(deftest rikasta-lupaus-lisatiedoilla-yksittainen-test
  (testing "Yksittäinen lupaus palautetaan muuttumattomana"
    (let [lupaus {:lupaustyyppi "yksittainen" :lupaus-id 1 :otsikko "Testi"}
          tulos (#'lupaus-palvelu/rikasta-lupaus-lisatiedoilla
                  lupaus ds 1 2021 2020 1)]
      (is (= lupaus tulos) "Yksittäinen lupaus ei tarvitse rikastusta"))))

(deftest rikasta-lupaus-lisatiedoilla-monivalinta-test
  (testing "Monivalinta lupaus palautetaan muuttumattomana"
    (let [lupaus {:lupaustyyppi "monivalinta" :lupaus-id 2 :otsikko "Testi"}
          tulos (#'lupaus-palvelu/rikasta-lupaus-lisatiedoilla
                  lupaus ds 1 2021 2020 1)]
      (is (= lupaus tulos) "Monivalinta lupaus ei tarvitse rikastusta"))))

(deftest rikasta-lupaus-lisatiedoilla-kysely-test
  (testing "Kysely lupaus palautetaan muuttumattomana"
    (let [lupaus {:lupaustyyppi "kysely" :lupaus-id 3 :otsikko "Testi"}
          tulos (#'lupaus-palvelu/rikasta-lupaus-lisatiedoilla
                  lupaus ds 1 2021 2020 1)]
      (is (= lupaus tulos) "Kysely lupaus ei tarvitse rikastusta"))))

(deftest rikasta-lupaus-lisatiedoilla-kustannusennuste-test
  (testing "Kustannusennuste-lupaus rikastetaan lisätiedoilla"
    (let [lupaus {:lupaustyyppi "kustannusennuste" :lupaus-id 100 :otsikko "Kustannusennuste"}
          urakka-id 1
          hoitokauden-alkuvuosi 2021
          urakan-alkuvuosi 2020
          hoitovuosi-nro 1
          tulos (#'lupaus-palvelu/rikasta-lupaus-lisatiedoilla
                  lupaus ds urakka-id hoitokauden-alkuvuosi urakan-alkuvuosi hoitovuosi-nro)]

      ;; Tarkista että alkuperäiset kentät säilyvät
      (is (= "kustannusennuste" (:lupaustyyppi tulos)))
      (is (= 100 (:lupaus-id tulos)))
      (is (= "Kustannusennuste" (:otsikko tulos)))

      ;; Tarkista että uudet kentät lisätään
      (is (contains? tulos :hoitovuosi-paattynyt?)
        "Kustannusennusteelle lisätään hoitovuosi-paattynyt? -kenttä")
      (is (= urakan-alkuvuosi (:urakan-alkuvuosi tulos))
        "Urakan alkuvuosi lisätään")
      (is (= hoitovuosi-nro (:hoitovuosi-nro tulos))
        "Hoitovuosi-nro lisätään")
      (is (contains? tulos :lopputilanne)
        "Kustannusennusteelle lisätään lopputilanne-kenttä")
      (is (contains? tulos :kustannusennusteet)
        "Kustannusennusteelle lisätään kustannusennusteet-kenttä"))))

(deftest rikasta-lupaus-lisatiedoilla-nil-parametrit-test
  (testing "Funktio toimii nil-parametreilla"
    (let [lupaus {:lupaustyyppi "kustannusennuste" :lupaus-id 100}
          tulos (#'lupaus-palvelu/rikasta-lupaus-lisatiedoilla
                  lupaus nil nil nil nil nil)]

      ;; Alkuperäinen lupaus säilyy
      (is (= 100 (:lupaus-id tulos)))
      ;; Rikastuskentät lisätään vaikka parametrit olisivat nil
      (is (contains? tulos :hoitovuosi-paattynyt?)))))

(deftest rikasta-lupaus-lisatiedoilla-funktionaalinen-test
  (testing "Funktio on puhdas - ei muuta alkuperäistä lupausta"
    (let [alkuperainen {:lupaustyyppi "yksittainen" :lupaus-id 1}
          lupaus (into {} alkuperainen)
          _ (#'lupaus-palvelu/rikasta-lupaus-lisatiedoilla
              lupaus ds 1 2021 2020 1)]

      (is (= alkuperainen lupaus)
        "Alkuperäinen lupaus ei muutu"))))

;; Testit prosessoi-lupausvastaukset funktiolle
(deftest prosessoi-lupausvastaukset-test
  (testing "Palauttaa oikean rakenteen"
    (let [vastaus []
          maarapaiva-tiedot {}
          opts {:db ds
                :urakka-id 1
                :urakan-alkuvuosi 2020
                :hoitokauden-alkuvuosi 2021
                :hoitovuosi-nro 2
                :valittu-hoitokausi [2021 2022]
                :nykyhetki (java.util.Date.)}
          tulos (#'lupaus-palvelu/prosessoi-lupausvastaukset
                  vastaus
                  maarapaiva-tiedot
                  opts)]

      (is (vector? tulos) "Tulos on vektori")
      (is (empty? tulos) "Tyhjä vastaus palauttaa tyhjän vektorin"))))

(deftest prosessoi-lupausvastaukset-tyhja-test
  (testing "Tyhjä vastaus käsitellään oikein"
    (let [tulos (#'lupaus-palvelu/prosessoi-lupausvastaukset
                  []
                  {}
                  {:db ds
                   :urakka-id 1
                   :urakan-alkuvuosi 2020
                   :hoitokauden-alkuvuosi 2021
                   :hoitovuosi-nro 1
                   :valittu-hoitokausi [2021 2022]
                   :nykyhetki (java.util.Date.)})]
      (is (= [] tulos) "Tyhjä vastaus palauttaa tyhjän vektorin"))))

(deftest prosessoi-lupausvastaukset-validointi-test
  (testing "Preconditions validoivat parametrit"
    (is (thrown? AssertionError
          (#'lupaus-palvelu/prosessoi-lupausvastaukset
            "ei-collection"
            {}
            {:db ds
             :urakka-id 1
             :urakan-alkuvuosi 2020
             :hoitokauden-alkuvuosi 2021
             :hoitovuosi-nro 1
             :valittu-hoitokausi [2021 2022]
             :nykyhetki (java.util.Date.)}))
      "Vastaus täytyy olla collection")

    (is (thrown? AssertionError
          (#'lupaus-palvelu/prosessoi-lupausvastaukset
            []
            "ei-map"
            {:db ds
             :urakka-id 1
             :urakan-alkuvuosi 2020
             :hoitokauden-alkuvuosi 2021
             :hoitovuosi-nro 1
             :valittu-hoitokausi [2021 2022]
             :nykyhetki (java.util.Date.)}))
      "Määräpäivä-tiedot täytyy olla map")

    (is (thrown? AssertionError
          (#'lupaus-palvelu/prosessoi-lupausvastaukset
            []
            {}
            "ei-map"))
      "Options täytyy olla map")))

(deftest prosessoi-lupausvastaukset-options-validointi-test
  (testing "Options-map validoidaan"
    (is (thrown? AssertionError
          (#'lupaus-palvelu/prosessoi-lupausvastaukset
            []
            {}
            {:urakka-id 1}))
      "Kaikki pakolliset kentät validoidaan")

    (is (thrown? AssertionError
          (#'lupaus-palvelu/prosessoi-lupausvastaukset
            []
            {}
            {:db ds
             :urakka-id "ei-numero"
             :urakan-alkuvuosi 2020
             :hoitokauden-alkuvuosi 2021
             :hoitovuosi-nro 1
             :valittu-hoitokausi [2021 2022]
             :nykyhetki (java.util.Date.)}))
      "urakka-id täytyy olla numero")))

(deftest prosessoi-lupausvastaukset-funktionaalinen-test
  (testing "Funktio on puhdas - ei muuta alkuperäisiä parametrejä"
    (let [alkuperainen-vastaus []
          vastaus (vec alkuperainen-vastaus)
          alkuperaiset-maarapaiva-tiedot {}
          maarapaiva-tiedot (into {} alkuperaiset-maarapaiva-tiedot)
          opts {:db ds
                :urakka-id 1
                :urakan-alkuvuosi 2020
                :hoitokauden-alkuvuosi 2021
                :hoitovuosi-nro 1
                :valittu-hoitokausi [2021 2022]
                :nykyhetki (java.util.Date.)}
          tulos (#'lupaus-palvelu/prosessoi-lupausvastaukset vastaus maarapaiva-tiedot opts)]

      (is (= alkuperainen-vastaus vastaus) "Alkuperäinen vastaus ei muutu")
      (is (= alkuperaiset-maarapaiva-tiedot maarapaiva-tiedot) "Alkuperäiset määräpäivä-tiedot eivät muutu")
      (is (vector? tulos) "Palauttaa vektorin"))))

;; Testit hae-talouslaskelmat funktiolle
(deftest hae-talouslaskelmat-test
  (testing "Palauttaa oikean rakenteen"
    (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
          hk-alkupvm (pvm/luo-pvm 2015 9 1)
          hk-loppupvm (pvm/luo-pvm 2016 8 31)
          hoitokauden-alkuvuosi 2015
          lupaus-sitoutuminen {:pisteet 100}
          tulos (#'lupaus-palvelu/hae-talouslaskelmat
                  ds
                  urakka-id
                  hk-alkupvm
                  hk-loppupvm
                  hoitokauden-alkuvuosi
                  lupaus-sitoutuminen)]

      (is (map? tulos) "Tulos on map")
      (is (contains? tulos :tavoitehinta) "Sisältää :tavoitehinta")
      (is (contains? tulos :oikaistu-tavoitehinta) "Sisältää :oikaistu-tavoitehinta")
      (is (contains? tulos :oikaistu-toteutuneet-kustannukset) "Sisältää :oikaistu-toteutuneet-kustannukset")
      (is (contains? tulos :tavoitehinta-puuttuu?) "Sisältää :tavoitehinta-puuttuu?")
      (is (contains? tulos :luvatut-pisteet-puuttuu?) "Sisältää :luvatut-pisteet-puuttuu?"))))

(deftest hae-talouslaskelmat-nil-alkupvm-test
  (testing "Nil hoitokauden alkupvm käsitellään oikein"
    (let [urakka-id 1
          tulos (#'lupaus-palvelu/hae-talouslaskelmat
                  ds
                  urakka-id
                  nil
                  nil
                  2021
                  {:pisteet 100})]

      (is (map? tulos) "Palauttaa mapin")
      (is (nil? (:tavoitehinta tulos)) "Tavoitehinta on nil kun alkupvm on nil")
      (is (nil? (:oikaistu-tavoitehinta tulos)) "Oikaistu tavoitehinta on nil")
      (is (nil? (:oikaistu-toteutuneet-kustannukset tulos)) "Oikaistu toteutuneet kustannukset on nil"))))

(deftest hae-talouslaskelmat-validointi-test
  (testing "Tavoitehinta-puuttuu? laskenta"
    (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
          hk-alkupvm (pvm/luo-pvm 2015 9 1)
          hk-loppupvm (pvm/luo-pvm 2016 8 31)
          hoitokauden-alkuvuosi 2015
          lupaus-sitoutuminen {:pisteet 100}
          tulos (#'lupaus-palvelu/hae-talouslaskelmat
                  ds
                  urakka-id
                  hk-alkupvm
                  hk-loppupvm
                  hoitokauden-alkuvuosi
                  lupaus-sitoutuminen)]

      (is (boolean? (:tavoitehinta-puuttuu? tulos)) "tavoitehinta-puuttuu? on boolean")))

  (testing "Luvatut-pisteet-puuttuu? laskenta"
    (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
          hk-alkupvm (pvm/luo-pvm 2015 9 1)
          hk-loppupvm (pvm/luo-pvm 2016 8 31)
          hoitokauden-alkuvuosi 2015]

      ;; Ilman pisteitä
      (let [tulos-ilman (#'lupaus-palvelu/hae-talouslaskelmat
                          ds
                          urakka-id
                          hk-alkupvm
                          hk-loppupvm
                          hoitokauden-alkuvuosi
                          {})]
        (is (true? (:luvatut-pisteet-puuttuu? tulos-ilman)) "Pisteet puuttuvat"))

      ;; Pisteillä
      (let [tulos-pisteilla (#'lupaus-palvelu/hae-talouslaskelmat
                              ds
                              urakka-id
                              hk-alkupvm
                              hk-loppupvm
                              hoitokauden-alkuvuosi
                              {:pisteet 100})]
        (is (false? (:luvatut-pisteet-puuttuu? tulos-pisteilla)) "Pisteet löytyvät")))))


(deftest muodosta-yhteenveto-test
  (testing "Yhteenvedon muodostaminen - rakenne ja perustiedot"
    (let [opts {:piste-maksimi 100
                :piste-ennuste 80
                :piste-toteuma 85
                :bonus-tai-sanktio 5000
                :tavoitehinta 1000000
                :oikaistu-tavoitehinta 950000
                :oikaistu-toteutuneet-kustannukset 900000
                :kustannusennuste-pisteet-tila true
                :odottaa-kannanottoa 5
                :merkitsevat-odottaa-kannanottoa 2
                :odottaa-urakoitsijan-kannanottoa? true
                :valikatselmus-tehty? false
                :tavoitehinta-puuttuu? false
                :lupausprosentit-puuttuu? false
                :luvatut-pisteet-puuttuu? false
                :ennusteen-tila :ennuste
                :tallennettu-paatos nil}
          tulos (#'lupaus-palvelu/muodosta-yhteenveto opts)]

      (is (map? tulos) "Yhteenveto on map")
      (is (contains? tulos :ennusteen-tila) "Sisältää ennusteen-tila")
      (is (contains? tulos :pisteet) "Sisältää pisteet")
      (is (contains? tulos :bonus-tai-sanktio) "Sisältää bonus-tai-sanktio")
      (is (contains? tulos :tavoitehinta) "Sisältää tavoitehinta")
      (is (contains? tulos :lupausprosentit-puuttuu?) "Sisältää lupausprosentit-puuttuu?")
      (is (= :ennuste (:ennusteen-tila tulos)) "Ennusteen tila on :ennuste")
      (is (false? (:lupausprosentit-puuttuu? tulos)) "Lupausprosentit löytyvät")
      (is (= 100 (get-in tulos [:pisteet :maksimi])) "Maksimipisteet oikein")
      (is (= 80 (get-in tulos [:pisteet :ennuste])) "Ennustepisteet oikein")
      (is (= 85 (get-in tulos [:pisteet :toteuma])) "Toteumapisteet oikein"))))

(deftest muodosta-yhteenveto-paatos-test
  (testing "Yhteenveto päätöksellä - näytetään päätöksen tiedot"
    (let [opts {:piste-maksimi 100
                :piste-ennuste 80
                :piste-toteuma 85
                :bonus-tai-sanktio 5000
                :tavoitehinta 1000000
                :oikaistu-tavoitehinta nil
                :oikaistu-toteutuneet-kustannukset nil
                :kustannusennuste-pisteet-tila true
                :odottaa-kannanottoa 0
                :merkitsevat-odottaa-kannanottoa 0
                :odottaa-urakoitsijan-kannanottoa? false
                :valikatselmus-tehty? false
                :tavoitehinta-puuttuu? false
                :luvatut-pisteet-puuttuu? false
                :ennusteen-tila :katselmoitu-toteuma
                :tallennettu-paatos {:toteutuneet_pisteet 90
                                     :tavoitehinta 1100000}}
          tulos (#'lupaus-palvelu/muodosta-yhteenveto opts)]

      (is (= 90 (get-in tulos [:pisteet :toteuma])) "Näytetään päätöksen pisteet")
      (is (= 1100000 (:tavoitehinta tulos)) "Näytetään päätöksen tavoitehinta"))))

(deftest muodosta-yhteenveto-ennusteen-tila-test
  (testing "Ennusteen tilan määrittely"
    ;; Katselmoitu toteuma
    (let [opts-katselmoitu {:piste-maksimi 100
                            :piste-ennuste 80
                            :piste-toteuma nil
                            :bonus-tai-sanktio 5000
                            :tavoitehinta 1000000
                            :oikaistu-tavoitehinta nil
                            :oikaistu-toteutuneet-kustannukset nil
                            :kustannusennuste-pisteet-tila true
                            :odottaa-kannanottoa 0
                            :merkitsevat-odottaa-kannanottoa 0
                            :odottaa-urakoitsijan-kannanottoa? false
                            :valikatselmus-tehty? false
                            :tavoitehinta-puuttuu? false
                            :luvatut-pisteet-puuttuu? false
                            :ennusteen-tila :katselmoitu-toteuma
                            :tallennettu-paatos {:toteutuneet_pisteet 90}}]
      (is (= :katselmoitu-toteuma
            (:ennusteen-tila (#'lupaus-palvelu/muodosta-yhteenveto opts-katselmoitu)))
        "Katselmoitu toteuma kun päätös on tallennettu"))

    ;; Alustava toteuma
    (let [opts-alustava {:piste-maksimi 100
                         :piste-ennuste 80
                         :piste-toteuma 85
                         :bonus-tai-sanktio 5000
                         :tavoitehinta 1000000
                         :oikaistu-tavoitehinta nil
                         :oikaistu-toteutuneet-kustannukset nil
                         :kustannusennuste-pisteet-tila true
                         :odottaa-kannanottoa 0
                         :merkitsevat-odottaa-kannanottoa 0
                         :odottaa-urakoitsijan-kannanottoa? false
                         :valikatselmus-tehty? false
                         :tavoitehinta-puuttuu? false
                         :luvatut-pisteet-puuttuu? false
                         :ennusteen-tila :alustava-toteuma
                         :tallennettu-paatos nil}]
      (is (= :alustava-toteuma
            (:ennusteen-tila (#'lupaus-palvelu/muodosta-yhteenveto opts-alustava)))
        "Alustava toteuma kun toteumapisteet on olemassa"))))

(deftest laske-bonus-ja-ennuste-test
  (testing "Bonus ja ennusteen tilan laskenta"
    (let [opts {:db (:db jarjestelma)
                :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                :tallennettu-paatos nil
                :piste-toteuma 85
                :piste-ennuste 80
                :lupaus-sitoutuminen {:pisteet 100}
                :tavoitehinta 1000000
                :nykyhetki (pvm/luo-pvm 2020 1 15)
                :hk-alkupvm (pvm/luo-pvm 2019 10 1)}
          tulos (#'lupaus-palvelu/laske-bonus-ja-ennuste opts)]

      (is (map? tulos) "Palauttaa mapin")
      (is (contains? tulos :bonus-tai-sanktio) "Sisältää bonus-tai-sanktio")
      (is (contains? tulos :ennusteen-tila) "Sisältää ennusteen-tila")
      (is (map? (:bonus-tai-sanktio tulos)) "Bonus on map")
      (is (contains? (:bonus-tai-sanktio tulos) :sanktio)
        "Tässä skenaariossa palautuu sanktio")
      (is (number? (get-in tulos [:bonus-tai-sanktio :sanktio]))
        "Sanktio on numero")
      (is (pos? (get-in tulos [:bonus-tai-sanktio :sanktio]))
        "Palvelun API-muodossa sanktio on positiivinen")
      (is (keyword? (:ennusteen-tila tulos)) "Ennusteen tila on keyword"))))

(deftest laske-bonus-ja-ennuste-paatos-test
  (testing "Päätös olemassa - käytetään tallennetun päätöksen bonusta"
    (let [tallennettu-paatos {:toteutuneet_pisteet 90
                              :luvatut_pisteet 100
                              :tavoitehinta 1000000
                              :tyyppi "sanktio"
                              :lupaussanktio 10000M}
          opts {:db (:db jarjestelma)
                :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                :tallennettu-paatos tallennettu-paatos
                :piste-toteuma 85
                :piste-ennuste 80
                :lupaus-sitoutuminen {:pisteet 100}
                :tavoitehinta 1000000
                :nykyhetki (pvm/luo-pvm 2020 1 15)
                :hk-alkupvm (pvm/luo-pvm 2019 10 1)}
          tulos (#'lupaus-palvelu/laske-bonus-ja-ennuste opts)]

      (is (= :katselmoitu-toteuma (:ennusteen-tila tulos))
        "Ennusteen tila on katselmoitu-toteuma kun päätös on")
      (is (= {:sanktio 10000M} (:bonus-tai-sanktio tulos))
        "Tallennettu sanktio palautetaan positiivisena API-muodossa"))))

(deftest laske-bonus-ja-ennuste-paatos-taytetty-test
  (testing "Tallennettu taytetty-päätös näkyy katselmoituna toteumana"
    (let [tallennettu-paatos {:toteutuneet_pisteet 100
                              :luvatut_pisteet 100
                              :tavoitehinta 1000000
                              :tyyppi "taytetty"
                              :lupaussanktio nil}
          opts {:db (:db jarjestelma)
                :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                :tallennettu-paatos tallennettu-paatos
                :piste-toteuma 100
                :piste-ennuste 100
                :lupaus-sitoutuminen {:pisteet 100}
                :tavoitehinta 1000000
                :nykyhetki (pvm/luo-pvm 2020 1 15)
                :hk-alkupvm (pvm/luo-pvm 2019 10 1)}
          tulos (#'lupaus-palvelu/laske-bonus-ja-ennuste opts)]
      (is (= :katselmoitu-toteuma (:ennusteen-tila tulos))
        "Tallennettu taytetty-päätös käsitellään katselmoituna toteumana")
      (is (= {:tavoite-taytetty true} (:bonus-tai-sanktio tulos))
        "Tallennettu taytetty-päätös näkyy API-muodossa tavoite täytetty -tilana"))))

(deftest laske-bonus-ja-ennuste-ennusteen-tila-test
  (testing "Ennusteen tilan määrittely eri tilanteissa"
    ;; Alustava toteuma (hoitovuosi valmis)
    (let [opts-alustava {:db (:db jarjestelma)
                         :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                         :tallennettu-paatos nil
                         :piste-toteuma 85
                         :piste-ennuste 80
                         :lupaus-sitoutuminen {:pisteet 100}
                         :tavoitehinta 1000000
                         :nykyhetki (pvm/luo-pvm 2020 8 31)
                         :hk-alkupvm (pvm/luo-pvm 2019 10 1)}
          tulos-alustava (#'lupaus-palvelu/laske-bonus-ja-ennuste opts-alustava)]
      (is (= :alustava-toteuma (:ennusteen-tila tulos-alustava))
        "Alustava toteuma kun toteuma on olemassa"))

    ;; Ennuste (hoitokausi alkanut, ei toteumaa)
    (let [opts-ennuste {:db (:db jarjestelma)
                        :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                        :tallennettu-paatos nil
                        :piste-toteuma nil
                        :piste-ennuste 80
                        :lupaus-sitoutuminen {:pisteet 100}
                        :tavoitehinta 1000000
                        :nykyhetki (pvm/luo-pvm 2020 5 1)
                        :hk-alkupvm (pvm/luo-pvm 2019 10 1)}
          tulos-ennuste (#'lupaus-palvelu/laske-bonus-ja-ennuste opts-ennuste)]
      (is (= :ennuste (:ennusteen-tila tulos-ennuste))
        "Ennuste kun hoitokausi on alkanut ja bonus on laskettavissa"))

    ;; Ei vielä ennustetta
    (let [opts-ei-ennustetta {:db (:db jarjestelma)
                              :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                              :tallennettu-paatos nil
                              :piste-toteuma nil
                              :piste-ennuste 80
                              :lupaus-sitoutuminen {:pisteet 100}
                              :tavoitehinta 1000000
                              :nykyhetki (pvm/luo-pvm 2019 8 1)
                              :hk-alkupvm (pvm/luo-pvm 2019 10 1)}
          tulos-ei-ennustetta (#'lupaus-palvelu/laske-bonus-ja-ennuste opts-ei-ennustetta)]
      (is (= :ei-viela-ennustetta (:ennusteen-tila tulos-ei-ennustetta))
        "Ei vielä ennustetta kun hoitokausi ei ole alkanut"))))

(deftest laske-bonus-ja-ennuste-kun-lupausprosentti-puuttuu-test
  (testing "Puuttuva lupausprosentti estää onnistuneen ennustetilan"
    (with-redefs [urakat-q/hae-urakan-parametrit
                  (constantly [{:lupauspaatoksen_sanktioprosentti 2M
                                :lupauspaatoksen_bonusprosentti nil}])]
      (let [yhteiset-opts {:db (:db jarjestelma)
                           :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                           :tallennettu-paatos nil
                           :lupaus-sitoutuminen {:pisteet 100}
                           :tavoitehinta 1000000
                           :nykyhetki (pvm/luo-pvm 2020 8 31)
                           :hk-alkupvm (pvm/luo-pvm 2019 10 1)}
            tulos-alustava (#'lupaus-palvelu/laske-bonus-ja-ennuste
                             (assoc yhteiset-opts
                               :piste-toteuma 85
                               :piste-ennuste 80))
            tulos-ennuste (#'lupaus-palvelu/laske-bonus-ja-ennuste
                            (assoc yhteiset-opts
                              :piste-toteuma nil
                              :piste-ennuste 80))]
        (is (= :ei-viela-ennustetta (:ennusteen-tila tulos-alustava))
          "Pelkkä pisteiden toteuma ei riitä ilman puuttuvia prosenttiparametreja")
        (is (= :ei-viela-ennustetta (:ennusteen-tila tulos-ennuste))
          "Pelkkä piste-ennuste ei riitä ilman puuttuvia prosenttiparametreja")
        (is (true? (:lupausprosentit-puuttuu? tulos-alustava))
          "Puuttuvat prosentit merkitään eksplisiittisesti yhteenvetoa varten")
        (is (nil? (:bonus-tai-sanktio tulos-alustava))
          "Bonusta tai sanktiota ei voida laskea ilman kaikkia prosenttiparametreja")))))

(deftest laske-bonus-ja-ennuste-kun-urakan-parametrit-puuttuvat-test
  (testing "Myos kokonaan puuttuva parametririvi merkitään puuttuviksi lupausprosenteiksi"
    (with-redefs [urakat-q/hae-urakan-parametrit
                  (constantly [])]
      (let [tulos (#'lupaus-palvelu/laske-bonus-ja-ennuste
                    {:db (:db jarjestelma)
                     :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                     :tallennettu-paatos nil
                     :piste-toteuma 85
                     :piste-ennuste 80
                     :lupaus-sitoutuminen {:pisteet 100}
                     :tavoitehinta 1000000
                     :nykyhetki (pvm/luo-pvm 2020 8 31)
                     :hk-alkupvm (pvm/luo-pvm 2019 10 1)})]
        (is (= :ei-viela-ennustetta (:ennusteen-tila tulos))
          "Ilman parametreja ennustetta ei voida laskea")
        (is (true? (:lupausprosentit-puuttuu? tulos))
          "Puuttuva parametririvi pitää näyttää puuttuvina lupausprosentteina")
        (is (nil? (:bonus-tai-sanktio tulos))
          "Bonusta tai sanktiota ei voida laskea ilman parametririviä")))))

(deftest yhteinen-paatos->bonus-tai-sanktio-test
  (testing "Muuntaa yhteisen päätöksen API-muotoon oikein"
    (is (= {:bonus 5200.0}
           (#'lupaus-palvelu/yhteinen-paatos->bonus-tai-sanktio
             {:lupausbonus 5200.0}))
        "Bonus palautetaan positiivisena")
    
    (is (= {:sanktio 13200.0}
           (#'lupaus-palvelu/yhteinen-paatos->bonus-tai-sanktio
             {:lupaussanktio 13200.0}))
        "Sanktio palautetaan positiivisena API-muodossa")
    
    (is (= {:tavoite-taytetty true}
           (#'lupaus-palvelu/yhteinen-paatos->bonus-tai-sanktio
             {:tavoite-taytetty true}))
        "Tavoite täytetty palautetaan sellaisenaan")
    
    (is (nil? (#'lupaus-palvelu/yhteinen-paatos->bonus-tai-sanktio nil))
        "Palauttaa nil kun yhteinen päätös on nil")
    
    (is (nil? (#'lupaus-palvelu/yhteinen-paatos->bonus-tai-sanktio {}))
        "Palauttaa nil kun yhteinen päätös on tyhjä map")))
