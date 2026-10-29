(ns harja.palvelin.palvelut.muutos-lukitukset-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]
            [harja.palvelin.palvelut.indeksit :refer :all]
            [harja.palvelin.palvelut.suunnittelu.apurit :as apurit]
            [harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu :as kust-palvelu]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :db-replica (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :muutokset (component/using
                       (muutos-palvelu/->Muutos {:kehitysmoodi true})
                       [:http-palvelin :db])
          :uusi-kustannussuunnitelma (component/using
                                       (kust-palvelu/->UusiKustannussuunnitelmaPalvelu)
                                       [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each
  urakkatieto-fixture
  jarjestelma-fixture)


(defn- vahvista-tai-kumoa-tavoite-ja-kattohinta!
  "Vahvistaa tai kumoaa tavoitteen ja kattohinnan"
  [urakka-id hoitovuoden-alkuvuosi vahvista?]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+
    {:urakka-id urakka-id
     :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
     :vahvista? vahvista?}))


(defn- tallenna-muutos!
  [urakka-id muutos-payload hoitokaudet]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :tallenna-muutos +kayttaja-jvh+
    {:urakka-id urakka-id
     :valittu-hoitokausi (first hoitokaudet)
     :hoitokaudet hoitokaudet
     :muutos muutos-payload}))


(defn poista-muutos [kayttaja tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :poista-muutos
    kayttaja
    tiedot))


(def ^{:private true} +urakka+ (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030"))
(def ^{:private true} +hoitokaudet+ (mapv (fn [vuosi]
                                            [(pvm/hoitokauden-alkupvm vuosi)
                                             (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))]) (range 2025 2030)))


(deftest laskutusraja-nousee-kun-pysyva-muutos-positiivinen-ensimmaisena-hoitovuonna
  (let [hoitovuoden-alkuvuosi 2025
        _ (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " +urakka+)
        _ (apurit/poista-tarjoukset-tietokannasta! +urakka+)
        _ (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
            (:db jarjestelma) +kayttaja-jvh+ +urakka+ hoitovuoden-alkuvuosi
            (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025))

        toimenpideinstanssi-id (ffirst (q (str "SELECT id FROM toimenpideinstanssi WHERE urakka = " +urakka+ " LIMIT 1")))
        tehtava-id (ffirst (q "SELECT id FROM tehtava WHERE poistettu IS NOT TRUE LIMIT 1"))
        muutos-payload {:tyyppi "pysyva"
                        :nimi "Testipysyvä"
                        :syy "Testitarkoitus"
                        :voimassa_alkaen #inst "2025-10-01T00:00:00.000-00:00"
                        :tavoitehinnan-muutos 5000
                        :kustannusvaikutukset [{:toimenpideinstanssi toimenpideinstanssi-id
                                                :kustannuslaji "hankintakustannukset"
                                                :summa 5000
                                                :hoitokauden_alkuvuosi 2025}]
                        :tehtavat_ja_maarat [{:tehtava tehtava-id
                                              :maaramuutos 100
                                              :hoitokauden_alkuvuosi 2025}]}

        muutos-payload-aiempi-vuosi {:tyyppi "pysyva"
                                     :nimi "Testipysyvä"
                                     :syy "Testitarkoitus"
                                     :voimassa_alkaen #inst "2024-10-01T00:00:00.000-00:00"
                                     :tavoitehinnan-muutos 5000
                                     :kustannusvaikutukset [{:toimenpideinstanssi toimenpideinstanssi-id
                                                             :kustannuslaji "hankintakustannukset"
                                                             :summa 5000
                                                             :hoitokauden_alkuvuosi 2024}]
                                     :tehtavat_ja_maarat [{:tehtava tehtava-id
                                                           :maaramuutos 100
                                                           :hoitokauden_alkuvuosi 2024}]}]


    (testing "Pysyvää muutosta ei voi luoda"
      (let [;; Vahvista kustannussuunnitelma, jolloin muutosta ei voi tehdä
            _ (vahvista-tai-kumoa-tavoite-ja-kattohinta! +urakka+ hoitovuoden-alkuvuosi true)
            odotettu-poikkeus "Pysyvää muutosta ei voi luoda, koska sen vaikutukset kohdistuvat vahvistettuun tavoitehintaan"

            ;; Tallennus ei mene läpi
            vastaus (try
                      (tallenna-muutos! +urakka+ muutos-payload +hoitokaudet+)
                      (catch Exception e e))
            _ (is
                (true? (str/includes? vastaus odotettu-poikkeus))
                "Odotettu virhe heitetään, muutosta ei voi luoda kun tavoitehinta on vahvistettu")

            ;; Kumoa vahvistus, muutos voidaan nyt luoda 
            _ (vahvista-tai-kumoa-tavoite-ja-kattohinta! +urakka+ hoitovuoden-alkuvuosi false)

            ;; Tallennus menee läpi
            vastaus (try
                      (tallenna-muutos! +urakka+ muutos-payload +hoitokaudet+)
                      (catch Exception e e))
            _ (is
                (false? (str/includes? vastaus odotettu-poikkeus))
                "Virhettä ei löydy, muutos voidaan luoda")]))


    (testing "Pysyvää muutosta ei voi poistaa"
      (let [_ (vahvista-tai-kumoa-tavoite-ja-kattohinta! +urakka+ hoitovuoden-alkuvuosi false)
            max-id-ennen-tallennusta (ffirst (q "SELECT MAX(id) FROM ONLY mhu_muutos;"))
            _ (tallenna-muutos! +urakka+ muutos-payload-aiempi-vuosi +hoitokaudet+)
            muutos-id (inc max-id-ennen-tallennusta)
            _ (vahvista-tai-kumoa-tavoite-ja-kattohinta! +urakka+ hoitovuoden-alkuvuosi true)

            odotettu-poikkeus "Muutoksen muuttamia päätöksiä on vahvistettu."
            vastaus-poiston-jalkeen (try
                                      (poista-muutos +kayttaja-jvh+
                                        {:muutos-id muutos-id
                                         :urakka-id +urakka+
                                         :valittu-hoitokausi (first +hoitokaudet+)
                                         :hoitokaudet +hoitokaudet+
                                         :laskenta-automatiikka? true})
                                      (catch Exception e e))

            _ (is
                (true? (str/includes? vastaus-poiston-jalkeen odotettu-poikkeus))
                "Muutosta ei voi poistaa, jos poisto vaikuttaa kustiksen päätökseen")]))


    (testing "Pysyvää muutosta ei voi muokata"
      (let [_ (vahvista-tai-kumoa-tavoite-ja-kattohinta! +urakka+ hoitovuoden-alkuvuosi false)
            _ (tallenna-muutos! +urakka+ muutos-payload-aiempi-vuosi +hoitokaudet+)
            max-id-ennen-tallennusta (ffirst (q "SELECT MAX(id) FROM ONLY mhu_muutos;"))
            _ (vahvista-tai-kumoa-tavoite-ja-kattohinta! +urakka+ hoitovuoden-alkuvuosi true)
            odotettu-poikkeus "ei voi muuttaa, koska se on lukittu"

            muokkaa-payload {:id (inc max-id-ennen-tallennusta)
                             :tyyppi "pysyva"
                             :nimi "Testipysyvä"
                             :syy "Testitarkoitus"
                             :voimassa_alkaen #inst "2024-10-01T00:00:00.000-00:00"
                             :tavoitehinnan-muutos 6000
                             :kustannusvaikutukset [{:toimenpideinstanssi toimenpideinstanssi-id
                                                     :kustannuslaji "hankintakustannukset"
                                                     :summa 6000
                                                     :hoitokauden_alkuvuosi 2024}]
                             :tehtavat_ja_maarat [{:tehtava tehtava-id
                                                   :maaramuutos 200
                                                   :hoitokauden_alkuvuosi 2024}]}

            vastaus (try
                      (tallenna-muutos! +urakka+ muokkaa-payload +hoitokaudet+)
                      (catch Exception e e))
            _ (is
                (true? (str/includes? vastaus odotettu-poikkeus))
                "Muutosta ei voi muokata")]))))
