(ns harja.palvelin.palvelut.valikatselmus.paatokset-ketjutus-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]

            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.valikatselmus.apurit :as v-apurit]
            [harja.palvelin.palvelut.valikatselmus.paatostyypit :as paatostyypit]
            [harja.palvelin.palvelut.valikatselmus.valikatselmukset :as valikatselmukset]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each
  (compose-fixtures tietokanta-fixture jarjestelma-fixture))


(defn- paatosavaimet [paatokset]
  (set (map :avain paatokset)))


(deftest hae-ketjutetusti-kumoutuvat-paatokset-koko-ketju-toimii
  (let [vastaus (v-apurit/hae-ketjutetusti-kumoutuvat-paatokset
                  paatostyypit/paatostyypit
                  :tavoitehinnan-muutokset)]

    (is (= #{:indeksikorjaus
             :hoitovuoden-lopun-hinta
             :tavoitehinnan-alitus
             :tavoitehinnan-ylitys
             :kattohinnan-ylitys
             :lupaus
             :hoidonjohtopalkkio}
          (paatosavaimet vastaus)))

    (is (not (contains? (paatosavaimet vastaus) :raportti)))))


(deftest hae-ketjutetusti-kumoutuvat-paatokset-raportti-toimii
  (is (empty?
        (v-apurit/hae-ketjutetusti-kumoutuvat-paatokset
          paatostyypit/paatostyypit
          :raportti))))


(deftest hae-ketjutetusti-kumoutuvat-paatokset-palvelu-palauttaa-vain-tehdyt
  (let [hoitokauden-alkuvuosi 2024
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")

        tehdyt [{:id 10
                 :nimi "Hoitovuoden lopun indeksikorjaus"}
                {:id 20
                 :nimi "Tavoitehinnan ylitys"}]

        peruttava {:urakkaid urakkaid
                   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                   :nimi "Tavoitehinnan muutokset"
                   :avain :tavoitehinnan-muutokset}

        vastaus (with-redefs [valikatselmukset/hae-urakan-mahdolliset-paatokset
                              (fn [_db _kayttaja _payload]
                                paatostyypit/paatostyypit)
                              valikatselmukset/palauta-kaikki-mahdolliset-ja-tehdyt-paatokset
                              (fn [_db _kayttaja _payload]
                                {:tietokanta-paatokset tehdyt})]
                  (valikatselmukset/hae-ketjutetusti-kumoutuvat-paatokset
                    (:db jarjestelma)
                    +kayttaja-jvh+
                    peruttava))]

    (is (= #{10 20}
          (set (map :id vastaus))) "Vastaus täsmää tehtyjä päätöksiä")

    (is (= #{:indeksikorjaus
             :tavoitehinnan-ylitys}
          (paatosavaimet vastaus)) "Avaimet ovat oikein")))


(deftest poista-yksittainen-paatos-reititys-toimii
  (let [kutsuttu (atom nil)
        pysyva-muutospaatos (first
                              (filter #(= "tavoitehinnan-pysyvat-muutokset"
                                         (:paatostyyppi %))
                                paatostyypit/paatostyypit))
        tavoitehinnan-muutospaatos (first
                                     (filter #(= "tavoitehinnan-muutokset"
                                                (:paatostyyppi %))
                                       paatostyypit/paatostyypit))]

    (with-redefs [valikatselmukset/poista-tavoitehinnan-pysyva-muutospaatos
                  (fn [_db _kayttaja _paatos]
                    (reset! kutsuttu :tavoitehinnan-pysyva-muutospaatos))

                  valikatselmukset/poista-tavoitehinnan-muutospaatos
                  (fn [_db _kayttaja _paatos]
                    (reset! kutsuttu :tavoitehinnan-muutospaatos))]

      ;; Testaa että haluttuja poistoja kutsutaan, ei ole kovin tärkeä testi 
      (valikatselmukset/poista-yksittainen-paatos :db :kayttaja pysyva-muutospaatos)
      (is (= :tavoitehinnan-pysyva-muutospaatos @kutsuttu))

      (valikatselmukset/poista-yksittainen-paatos :db :kayttaja tavoitehinnan-muutospaatos)
      (is (= :tavoitehinnan-muutospaatos @kutsuttu)))))


(deftest poista-paatokset-ketjutetusti-toimii
  (let [urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        hoitokauden-alkuvuosi 2024

        paatos {:id 1
                :urakkaid urakkaid
                :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                :avain :tavoitehinnan-muutokset}

        kumoutuvat [{:id 2
                     :avain :indeksikorjaus}
                    {:id 3
                     :avain :hoitovuoden-lopun-hinta}
                    {:id 4
                     :avain :tavoitehinnan-ylitys}]

        poistot (atom [])
        odotettu-vastaus {:paatokset :haettu}]

    (with-redefs [valikatselmukset/poista-yksittainen-paatos
                  (fn [_db _kayttaja poistettava]
                    (swap! poistot conj (:id poistettava)))

                  valikatselmukset/hae-valikatselmuksen-tiedot-hoitovuodelle
                  (fn [_db _kayttaja payload]
                    (is (= {:urakkaid urakkaid
                            :hoitovuosi hoitokauden-alkuvuosi}
                          payload))
                    odotettu-vastaus)]

      (is (= odotettu-vastaus
            (valikatselmukset/poista-paatokset-ketjutetusti
              (:db jarjestelma)
              +kayttaja-jvh+
              {:paatos paatos
               :tehdyt-kumoutuvat-paatokset kumoutuvat}))))

    (is (= [1 2 3 4] @poistot))))
