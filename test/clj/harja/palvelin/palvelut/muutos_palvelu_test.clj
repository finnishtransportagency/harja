(ns harja.palvelin.palvelut.muutos-palvelu-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-urakan-muutostiedot (component/using
                                                   (muutos-palvelu/->Muutos {:kehitysmoodi true})
                                                   [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each
              urakkatieto-fixture
              jarjestelma-fixture)

(defn hae-urakan-muutostiedot [kayttaja tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :hae-urakan-muutostiedot
    kayttaja
    tiedot))

(deftest hae-urakan-muutostiedot-ii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
        toimenpide-id-paall-paikk (ffirst (q "SELECT id FROM toimenpide WHERE koodi = '20107';")) ; Päällystepaikkaukset
        toimenpide-id-soratiet (ffirst (q "SELECT id FROM toimenpide WHERE koodi = '23124';")) ; Soratiet
        toimenpide-id-mhu-yllapito (ffirst (q "SELECT id FROM toimenpide WHERE koodi = '20191';")) ; -- MHU Ylläpito
        liite-id (ffirst (q "SELECT id FROM liite WHERE nimi = 'rumpu.jpg'"))
        vastaus (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                         :valittu-hoitokausi valittu-hoitokausi})
        odotetut-kirjatut-muutokset [{:kulu_kohdistus nil,
                                      :kustannusvaikutukset (list {:summa 1000, :toimenpide toimenpide-id-paall-paikk, :kustannuslaji "hankintakustannukset"}),
                                      :tavoitehinnan-muutos 1000
                                      :voimassa_alkaen #inst "2025-05-06T21:00:00.000-00:00", :syy "Täytyykin tehdä enemmän päällysteiden paikkausta, koska pahat kelirikot.",
                                      :tehtavat_ja_maarat
                                      (list {:tehtava 3116, :uusi_maara 1100, :maaramuutos 100, :edellinen_maara 1000}),
                                      :urakka urakka-id, :nimi "Päällysteen paikkausmuutos", :id 1, :liitteet nil, :versio 1, :luonnos false, :tyyppi "pysyva"}
                                     {:kulu_kohdistus nil,
                                      :kustannusvaikutukset (list {:summa 3000, :toimenpide toimenpide-id-soratiet, :kustannuslaji "hankintakustannukset"}),
                                      :tavoitehinnan-muutos 3000
                                      :voimassa_alkaen #inst "2025-05-06T21:00:00.000-00:00", :syy "Tehdään lisäksi tämä isohko sorastus, ei ollut tiedossa ennen urakan alkua.",
                                      :tehtavat_ja_maarat nil,
                                      :urakka urakka-id, :nimi "Erillisrahoitettu sorastusmuutos", :id 2, :liitteet nil, :versio 1, :luonnos false, :tyyppi "erillisrahoitettu"}
                                     {:kulu_kohdistus nil,
                                      :kustannusvaikutukset (list {:summa 1000, :toimenpide toimenpide-id-mhu-yllapito, :kustannuslaji "hankintakustannukset"}),
                                      :tavoitehinnan-muutos 1000
                                      :voimassa_alkaen #inst "2025-05-06T21:00:00.000-00:00", :syy "Ei tehdä tänä kesänä rumpuja, ovat vielä kunnossa.",
                                      :tehtavat_ja_maarat
                                      (list {:tehtava 1406, :uusi_maara 0, :maaramuutos -40, :edellinen_maara 40} {:tehtava 3029, :uusi_maara 0, :maaramuutos -30, :edellinen_maara 30}),
                                      :urakka urakka-id, :nimi "Tämän hoitovuoden määräpoikkeamamuutos", :id 3,
                                      :liitteet (list {:liite liite-id, :muutos 3}), :versio 1, :luonnos false, :tyyppi "maarapoikkeama"}]]
    (is (= (count (:kirjatut-muutokset vastaus)) 3) "oikea määrä muutoksia")
    (is (every? (fn [rivi] (some #(= rivi %) (:kirjatut-muutokset vastaus))) odotetut-kirjatut-muutokset)
      "Kaikki muutosrivit löytyvät vastausjoukosta")))


(deftest hae-urakan-muutostiedot-ii-kun-annetuilla-ehdoilla-ei-loydy
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        valittu-hoitokausi-22-23 [(pvm/->pvm "1.10.2022") (pvm/->pvm "30.09.2023")]
        vastaus-22-23 (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                         :valittu-hoitokausi valittu-hoitokausi-22-23})
        valittu-hoitokausi-23-24 [(pvm/->pvm "1.10.2023") (pvm/->pvm "30.09.2024")]
        vastaus-23-24 (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                               :valittu-hoitokausi valittu-hoitokausi-23-24})
        valittu-hoitokausi-24-25 [(pvm/->pvm "1.10.2024") (pvm/->pvm "30.09.2025")]
        vastaus-24-25 (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                               :valittu-hoitokausi valittu-hoitokausi-24-25})
        odotetut-rivit []]
    (is (= (count (:kirjatut-muutokset vastaus-22-23)) 0) "oikea määrä muutoksia 22-23")
    (is (= (count (:kirjatut-muutokset vastaus-23-24)) 0) "oikea määrä muutoksia 23-24")
    (is (= (count (:kirjatut-muutokset vastaus-24-25)) 0) "oikea määrä muutoksia 24-25")
    (is (= odotetut-rivit (:kirjatut-muutokset vastaus-22-23) (:kirjatut-muutokset vastaus-23-24) (:kirjatut-muutokset vastaus-24-25)))))

(deftest paivita-muutos-ja-tarkista-historiataulut
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
        toimenpide-id-paall-paikk (ffirst (q "SELECT id FROM toimenpide WHERE koodi = '20107';")) ; Päällystepaikkaukset
        toimenpide-id-soratiet (ffirst (q "SELECT id FROM toimenpide WHERE koodi = '23124';")) ; Soratiet
        toimenpide-id-mhu-yllapito (ffirst (q "SELECT id FROM toimenpide WHERE koodi = '20191';")) ; -- MHU Ylläpito
        liite-id (ffirst (q "SELECT id FROM liite WHERE nimi = 'rumpu.jpg'"))
        ;; TODO: tähän muutoksen tallennus, sellaiseen riviin joka on testidatassa että aiheutuu UPDATE
        ;; Assertoidaan datan syntyminen historiatauluihin ja oikeat versiot jne
        ]))
