(ns harja.palvelin.palvelut.suunnittelu.kalustoresurssit-test
  (:require [clojure.test :refer [deftest testing use-fixtures compose-fixtures is]]
            [harja.palvelin.palvelut.suunnittelu.kalustoresurssit-palvelu :as kr-palvelu]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (luo-testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :kalustoresurssit (component/using
                              (kr-palvelu/->Kalustoresurssit)
                              [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(defn- ryhman-maara [resurssit hoitoluokkaryhma]
  (some (fn [r]
          (when (= (:hoitoluokkaryhma r) hoitoluokkaryhma)
            (:maara r)))
    resurssit))

(deftest tallenna-ja-hae-kalustoresurssit-onnistuneesti
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")]
    (testing "Kalustoresurssit tallennetaan ja haetaan oikein"
      (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-urakan-kalustoresurssit
                      +kayttaja-jvh+
                      {:urakka-id urakka-id
                       :kalustoresurssit [{:hoitoluokkaryhma "ise-ib" :maara 5}
                                          {:hoitoluokkaryhma "ic-iii" :maara 10}
                                          {:hoitoluokkaryhma "k1-k2-l" :maara 3}]})]
        (is (= 5 (ryhman-maara vastaus "ise-ib")))
        (is (= 10 (ryhman-maara vastaus "ic-iii")))
        (is (= 3 (ryhman-maara vastaus "k1-k2-l")))

        (let [haetut (kutsu-palvelua (:http-palvelin jarjestelma)
                       :hae-urakan-kalustoresurssit
                       +kayttaja-jvh+
                       {:urakka-id urakka-id})]
          (is (= 3 (count haetut)) "Kaikki kolme hoitoluokkaryhmää löytyvät")
          (is (= 5 (ryhman-maara haetut "ise-ib"))))))))

(deftest tallenna-paivittaa-olemassa-olevan-maaran
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")]
    (testing "Saman hoitoluokkaryhmän tallennus päivittää määrän eikä luo uutta riviä"
      (kutsu-palvelua (:http-palvelin jarjestelma)
        :tallenna-urakan-kalustoresurssit
        +kayttaja-jvh+
        {:urakka-id urakka-id
         :kalustoresurssit [{:hoitoluokkaryhma "ise-ib" :maara 5}]})
      (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-urakan-kalustoresurssit
                      +kayttaja-jvh+
                      {:urakka-id urakka-id
                       :kalustoresurssit [{:hoitoluokkaryhma "ise-ib" :maara 42}]})
            ise-ib-rivit (filter #(= "ise-ib" (:hoitoluokkaryhma %)) vastaus)]
        (is (= 1 (count ise-ib-rivit)) "Vain yksi rivi ise-ib-ryhmälle")
        (is (= 42 (ryhman-maara vastaus "ise-ib")) "Määrä on päivittynyt")))))

(deftest virheellinen-hoitoluokkaryhma-hylataan
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")]
    (testing "Tuntematon hoitoluokkaryhmä aiheuttaa virheen"
      (is (thrown? Exception
            (kutsu-palvelua (:http-palvelin jarjestelma)
              :tallenna-urakan-kalustoresurssit
              +kayttaja-jvh+
              {:urakka-id urakka-id
               :kalustoresurssit [{:hoitoluokkaryhma "ei-ole-olemassa" :maara 1}]}))))))
