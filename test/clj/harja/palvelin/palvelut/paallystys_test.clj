(ns harja.palvelin.palvelut.paallystys-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.paallystys :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (apply tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :urakan-paallystyskohteet (component/using
                                        (->Paallystys)
                                        [:http-palvelin :db])
          :urakan-paallystysilmoitus-paallystyskohteella (component/using
                                      (->Paallystys)
                                      [:http-palvelin :db])
          :tallenna-paallystysilmoitus (component/using
                                      (->Paallystys)
                                      [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest paallystyskohteet-haettu-oikein
  (let [res (kutsu-palvelua (:http-palvelin jarjestelma)
                            :urakan-paallystyskohteet +kayttaja-jvh+
                            {:urakka-id @muhoksen-paallystysurakan-id
                             :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id})
        kohteiden-lkm (ffirst (q
                                (str "SELECT count(*)
                                      FROM paallystyskohde
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @muhoksen-paallystysurakan-id ")")))]
    (is (= (count res) kohteiden-lkm) "Päällystyskohteiden määrä")))