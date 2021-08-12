(ns harja.palvelin.palvelut.lupaukset-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.lupaukset :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :as testi]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-urakan-lupaustiedot (component/using
                                                   (->Lupaukset)
                                                   [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each jarjestelma-fixture)

(deftest urakan-lupaustietojen-haku-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-lupaustiedot +kayttaja-jvh+
                                {:urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                                 :urakan-alkuvuosi 2021})
        sitoutuminen (:lupaus-sitoutuminen vastaus)
        ryhmat (:lupausryhmat vastaus)
        ryhma-1 (first (filter #(= 1 (:jarjestys %)) ryhmat))
        ryhma-2 (first (filter #(= 2 (:jarjestys %)) ryhmat))
        ryhma-3 (first (filter #(= 3 (:jarjestys %)) ryhmat))
        ryhma-4 (first (filter #(= 4 (:jarjestys %)) ryhmat))
        ryhma-5 (first (filter #(= 5 (:jarjestys %)) ryhmat))
        lupaukset (:lupaukset vastaus)]
    (is (= 1 (:id sitoutuminen)) "luvattu-pistemaara oikein")
    (is (= 76 (:pisteet sitoutuminen)) "luvattu-pistemaara oikein")
    (is (= 5 (count ryhmat)) "lupausryhmien määrä")
    (is (= 16 (:pisteet ryhma-1)) "ryhmä 1 pisteet")
    (is (= 14 (:kyselypisteet ryhma-1)) "ryhmä 1 kyselypisteet")
    (is (= 10 (:pisteet ryhma-2)) "ryhmä 2 pisteet")
    (is (= 0 (:kyselypisteet ryhma-2)) "ryhmä 2 kyselypisteet")
    (is (= 20 (:pisteet ryhma-3)) "ryhmä 3 pisteet")
    (is (= 0 (:kyselypisteet ryhma-3)) "ryhmä 3 kyselypisteet")
    (is (= 15 (:pisteet ryhma-4)) "ryhmä 4 pisteet")
    (is (= 0 (:kyselypisteet ryhma-4)) "ryhmä 4 kyselypisteet")
    (is (= 25 (:pisteet ryhma-5)) "ryhmä 5 pisteet")
    (is (= 0 (:kyselypisteet ryhma-5)) "ryhmä 5 kyselypisteet")
    (is (= 14 (count lupaukset)) "lupausten määrä")))


(deftest urakan-lupauspisteiden-tallennus-toimii-insert
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-luvatut-pisteet +kayttaja-jvh+
                                {:pisteet 67
                                 :id (hae-iin-maanteiden-hoitourakan-lupaussitoutumisen-id)
                                 :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                                 :urakan-alkuvuosi 2021})
        sitoutuminen (:lupaus-sitoutuminen vastaus)
        ryhmat (:lupausryhmat vastaus)
        lupaukset (:lupaukset vastaus)]
    (is (= 67 (:pisteet sitoutuminen)) "luvattu-pistemaara oikein")
    (is (= 5 (count ryhmat)) "lupausryhmien määrä")
    (is (= 14 (count lupaukset)) "lupausten määrä")))

(deftest urakan-lupauspisteiden-tallennus-vaatii-oikean-urakkaidn
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-luvatut-pisteet +kayttaja-jvh+
                                {:id (hae-iin-maanteiden-hoitourakan-lupaussitoutumisen-id)
                                 :pisteet 67, :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                                 :urakan-alkuvuosi 2021})
        _ (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                         :tallenna-luvatut-pisteet +kayttaja-jvh+
                                                         {:id (hae-iin-maanteiden-hoitourakan-lupaussitoutumisen-id)
                                                          :pisteet 167
                                                          :urakka-id (hae-muhoksen-paallystysurakan-id)})))]))

(deftest lisaa-lupaus-vastaus
  (let [lupaus-vastaus {:lupaus-id 1
                        :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                        :kuukausi 12
                        :vuosi 2021
                        :paatos false
                        :vastaus true
                        :lupaus-vaihtoehto-id nil}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :vastaa-lupaukseen
                                +kayttaja-jvh+
                                lupaus-vastaus)]
    (is (= (select-keys vastaus (keys lupaus-vastaus))      ; Ei piitata muista avaimista.
           lupaus-vastaus)
        "Tallennetut arvot ovat palautetaan")
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :vastaa-lupaukseen
                                           +kayttaja-jvh+
                                           lupaus-vastaus))
        "Samalle lupaus-urakka-kuukaus-vuosi -yhdistelmälle ei voi lisätä toista vastausta.")))

(deftest paivita-lupaus-vastaus
  (let [lupaus-vastaus {:id 1
                        :vastaus false
                        :lupaus-vaihtoehto-id nil}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :vastaa-lupaukseen
                                +kayttaja-jvh+
                                lupaus-vastaus)]
    (is (= (select-keys vastaus (keys lupaus-vastaus))      ; Ei piitata muista avaimista.
           lupaus-vastaus)
        "Tallennetut arvot palautetaan.")

    (is (thrown? AssertionError (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :vastaa-lupaukseen
                                                +kayttaja-jvh+
                                                {:id 9873456387435
                                                 :vastaus false
                                                 :lupaus-vaihtoehto-id nil}))
        "Olematon lupaus-vastaus-id heittää poikkeuksen.")))
