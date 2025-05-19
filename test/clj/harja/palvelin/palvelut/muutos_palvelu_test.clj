(ns harja.palvelin.palvelut.muutos-palvelu-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
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
        hoitokauden-alkuvuosi 2025
        toimenpide-id-paall-paikk (ffirst (q "SELECT id FROM toimenpide WHERE koodi = '20107';")) ; Päällystepaikkaukset
        toimenpide-id-soratiet (ffirst (q "SELECT id FROM toimenpide WHERE koodi = '23124';")) ; Soratiet
        toimenpide-id-mhu-yllapito (ffirst (q "SELECT id FROM toimenpide WHERE koodi = '20191';")) ; -- MHU Ylläpito
        liite-id (ffirst (q "SELECT id FROM liite WHERE nimi = 'rumpu.jpg'"))
        vastaus (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                         :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        odotetut-rivit [{:kulu_kohdistus nil,
                         :kustannusvaikutukset (list {:summa 1000, :toimenpide toimenpide-id-paall-paikk, :kustannuslaji "hankintakustannukset"}),
                         :voimassa_alkaen #inst "2025-05-06T21:00:00.000-00:00", :syy "Täytyykin tehdä enemmän päällysteiden paikkausta, koska pahat kelirikot.",
                         :tehtavat_ja_maarat
                         (list {:tehtava 3116, :uusi_maara 1100, :maaramuutos 100, :edellinen_maara 1000}),
                         :urakka urakka-id, :nimi "Päällysteen paikkausmuutos", :id 1, :liitteet nil, :versio 1, :luonnos false, :tyyppi "pysyva"}
                        {:kulu_kohdistus nil,
                         :kustannusvaikutukset (list {:summa 3000, :toimenpide toimenpide-id-soratiet, :kustannuslaji "hankintakustannukset"}),
                         :voimassa_alkaen #inst "2025-05-06T21:00:00.000-00:00", :syy "Tehdään lisäksi tämä isohko sorastus, ei ollut tiedossa ennen urakan alkua.",
                         :tehtavat_ja_maarat nil,
                         :urakka urakka-id, :nimi "Erillisrahoitettu sorastusmuutos", :id 2, :liitteet nil, :versio 1, :luonnos false, :tyyppi "erillisrahoitettu"}
                        {:kulu_kohdistus nil,
                         :kustannusvaikutukset (list {:summa 1000, :toimenpide toimenpide-id-mhu-yllapito, :kustannuslaji "hankintakustannukset"}),
                         :voimassa_alkaen #inst "2025-05-06T21:00:00.000-00:00", :syy "Ei tehdä tänä kesänä rumpuja, ovat vielä kunnossa.",
                         :tehtavat_ja_maarat
                         (list {:tehtava 1406, :uusi_maara 0, :maaramuutos -40, :edellinen_maara 40} {:tehtava 3029, :uusi_maara 0, :maaramuutos -30, :edellinen_maara 30}),
                         :urakka urakka-id, :nimi "Tämän hoitovuoden määräpoikkeamamuutos", :id 3,
                         :liitteet (list {:liite liite-id, :muutos 3}), :versio 1, :luonnos false, :tyyppi "maarapoikkeama"}]]
    (is (= (count vastaus) 3) "oikea määrä muutoksia")
    (is (every? (fn [rivi] (some #(= rivi %) vastaus)) odotetut-rivit)
      "Kaikki muutosrivit löytyvät vastausjoukosta")))
