(ns harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset-test
  (:require [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]
            [harja.pvm :as pvm]))

(deftest hakuparametrien-muodostaminen
  (let [aikavali [(pvm/luo-pvm 2017 1 1)
                  (pvm/luo-pvm 2018 1 1)]]
    (is (= {:harja.domain.urakka/id 666
            :harja.domain.sopimus/id 666
            :harja.domain.toimenpidekoodi/id 666
            :harja.domain.kanavat.kanavan-toimenpide/alkupvm (pvm/luo-pvm 2017 1 1)
            :harja.domain.kanavat.kanavan-toimenpide/loppupvm (pvm/luo-pvm 2018 1 1)
            :harja.domain.kanavat.kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen}
           (tiedot/muodosta-hakuargumentit {:urakka {:id 666}
                                            :sopimus-id 666
                                            :toimenpide {:id 666}
                                            :aikavali aikavali})))))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))

(deftest PaivitaValinnat
  (vaadi-async-kutsut
    #{tiedot/->HaeKokonaishintaisetToimenpiteet}
    (is (= {:valinnat {:foo :bar}}
           (e! (tiedot/->PaivitaValinnat {:foo :bar}))))))

(deftest HaeKokonaishintaisetToimenpiteet
  (vaadi-async-kutsut
    #{tiedot/->KokonaishintaisetToimenpiteetHaettu tiedot/->KokonaishintaisetToimenpiteetEiHaettu}
    (is (= {:haku-kaynnissa? true}
           (e! (tiedot/->HaeKokonaishintaisetToimenpiteet {:urakka {:id 1}
                                                           :sopimus-id 666
                                                           :toimenpide {:id 666}}))))))

(deftest KokonaishintaisetToimenpiteetHaettu
  (is (= {:haku-kaynnissa? false
          :toimenpiteet [{:id 1}]}
         (e! (tiedot/->KokonaishintaisetToimenpiteetHaettu [{:id 1}])))))

(deftest KokonaishintaisetToimenpiteetEiHaettu
  (is (= {:haku-kaynnissa? false
          :toimenpiteet []}
         (e! (tiedot/->KokonaishintaisetToimenpiteetEiHaettu)))))