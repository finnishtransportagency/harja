(ns harja.palvelin.palvelut.lupaus-palvelu-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]))

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

(deftest urakan-lupaustietojen-haku-toimii
  (let [tiedot {:urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                :urakan-alkuvuosi 2021
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

(deftest odottaa-kannanottoa
  (let [hakutiedot {:urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                    :urakan-alkuvuosi 2021
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
      "Yhteensä 10 lupausta odottaa kannanottoa tammikuussa: kaikki paitsi 1, 2, 12 ja 14")
    (is (= 4 (get-in vastaus [:yhteenveto :merkitsevat-odottaa-kannanottoa]))
      "Yhteensä 4 lupausta odottaa merkitsevää kannanottoa tammikuussa: 4, 8, 11 ja 13")))

(deftest merkitsevat-odottaa-kannanottoa
  (let [hakutiedot {:urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                    :urakan-alkuvuosi 2021
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
                   :urakan-alkuvuosi 2021
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
                        :urakan-alkuvuosi 2021
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
                          :urakan-alkuvuosi 2021
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
                    :urakan-alkuvuosi 2021
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
  (let [_ (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-luvatut-pisteet +kayttaja-jvh+
            {:pisteet 67
             :id @iin-maanteiden-hoitourakan-lupaussitoutumisen-id
             :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id})
        lupaustiedot (hae-urakan-lupaustiedot +kayttaja-jvh+ {:urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                                                              :urakan-alkuvuosi 2021
                                                              :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                                                                   #inst "2022-09-30T20:59:59.000-00:00"]})
        sitoutuminen (:lupaus-sitoutuminen lupaustiedot)]
    (is (= 67 (:pisteet sitoutuminen)) "luvattu-pistemaara oikein")))

(deftest urakan-lupauspisteiden-tallennus-vaatii-oikean-urakkaidn
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-luvatut-pisteet +kayttaja-jvh+
                                {:id @iin-maanteiden-hoitourakan-lupaussitoutumisen-id
                                 :pisteet 67, :urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
                                 :urakan-alkuvuosi 2021
                                 :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                                      #inst "2022-09-30T20:59:59.000-00:00"]})
        _ (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                         :tallenna-luvatut-pisteet +kayttaja-jvh+
                                                         {:id @iin-maanteiden-hoitourakan-lupaussitoutumisen-id
                                                          :pisteet 167
                                                          :urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")})))]))

(deftest urakan-lupauspisteita-ei-saa-muokata-valikatselmuksen-jalkeen
  (with-redefs [lupaus-palvelu/valikatselmus-tehty-urakalle? (constantly true)]
    (is (thrown? AssertionError (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :tallenna-luvatut-pisteet +kayttaja-jvh+
                                                {:id (hae-iin-maanteiden-hoitourakan-lupaussitoutumisen-id)
                                                 :pisteet 67, :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                                                 :urakan-alkuvuosi 2021
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
    (with-redefs [lupaus-palvelu/valikatselmus-tehty-hoitokaudelle? (constantly true)]
      (is (thrown? AssertionError (vastaa-lupaukseen vastaus)) "Ei saa vastata välikatselmuksen jälkeen"))
    (is (vastaa-lupaukseen vastaus) "Saa vastata")))

(deftest ei-saa-paivittaa-vastausta-valikatselmuksen-jalkeen
  (let [vastaus {:id 2
                 :vastaus false
                 :lupaus-vaihtoehto-id nil}]
    (with-redefs [lupaus-palvelu/valikatselmus-tehty-hoitokaudelle? (constantly true)]
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
                   :urakan-alkuvuosi 2019
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
        _ (u (str "DELETE FROM lupaus_pisteet WHERE vuosi = " vuosi " AND \"urakka-id\" ="  urakka-id ))

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