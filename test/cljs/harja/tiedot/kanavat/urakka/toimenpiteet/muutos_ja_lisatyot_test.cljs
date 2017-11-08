(ns harja.tiedot.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot-test
  (:require [harja.tiedot.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))

(deftest PaivitaValinnat
  (vaadi-async-kutsut
    #{tiedot/->HaeToimenpiteet}
    (is (= {:valinnat {:foo :bar}}
           (e! (tiedot/->PaivitaValinnat {:foo :bar}))))))

(deftest HaeToimenpiteet
  (vaadi-async-kutsut
    #{tiedot/->ToimenpiteetHaettu tiedot/->ToimenpiteetEiHaettu}
    (is (= {:toimenpiteiden-haku-kaynnissa? true}
           (e! (tiedot/->HaeToimenpiteet {:urakka {:id 1}
                                          :sopimus-id 666
                                          :toimenpide {:id 666}}))))))

(deftest ToimenpiteetHaettu
  (is (= {:toimenpiteiden-haku-kaynnissa? false
          :toimenpiteet [{:id 1}]}
         (e! (tiedot/->ToimenpiteetHaettu [{:id 1}])))))

(deftest ToimenpiteetEiHaettu
  (is (= {:toimenpiteiden-haku-kaynnissa? false
          :toimenpiteet []}
         (e! (tiedot/->ToimenpiteetEiHaettu)))))