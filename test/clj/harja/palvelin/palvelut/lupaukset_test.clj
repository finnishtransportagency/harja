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
        lupaukset (:lupaukset vastaus)]
    (is (= 1 (:id sitoutuminen)) "luvattu-pistemaara oikein")
    (is (= 76 (:pisteet sitoutuminen)) "luvattu-pistemaara oikein")
    (is (= 5 (count ryhmat)) "lupausryhmien määrä")
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