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
                                {:urakka-id
                                 (hae-oulun-maanteiden-hoitourakan-2019-2024-id)})]
    (is (= 76 (:pisteet vastaus)) "luvattu-pistemaara oikein")
    (is (= (hae-oulun-maanteiden-hoitourakan-2019-2024-id) (:urakka-id vastaus)) "luvattu-pistemaara oikein")))


(deftest urakan-lupauspisteiden-tallennus-toimii-insert
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-luvatut-pisteet +kayttaja-jvh+
                                {:pisteet 67, :urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)})
        odotettu {:urakka-id 35
                  :pisteet 67
                  :poistettu false
                  :muokkaaja nil
                  :muokattu nil
                  :luoja 3}]
    (is (= odotettu (dissoc vastaus :luotu :id)) "lupauspisteen tallennus oikein")))

(deftest urakan-lupauspisteiden-tallennus-vaatii-oikean-urakkaidn
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-luvatut-pisteet +kayttaja-jvh+
                                {:pisteet 67, :urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)})
        odotettu {:urakka-id 35
                  :pisteet 67
                  :poistettu false
                  :muokkaaja nil
                  :muokattu nil
                  :luoja 3}
        _ (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                         :tallenna-luvatut-pisteet +kayttaja-jvh+
                                                         {:id (:id vastaus)
                                                          :pisteet 167
                                                          :urakka-id (hae-muhoksen-paallystysurakan-id)})))]
    (is (= odotettu (dissoc vastaus :luotu :id)) "lupauspisteen tallennus oikein")))