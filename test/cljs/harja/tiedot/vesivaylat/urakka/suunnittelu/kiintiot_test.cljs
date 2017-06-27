(ns harja.tiedot.vesivaylat.urakka.suunnittelu.kiintiot-test
  (:require [harja.tiedot.vesivaylat.urakka.suunnittelu.kiintiot :as tiedot]
            [cljs.core.async :refer [chan <!]]
            [cljs.test :refer-macros [deftest is async use-fixtures]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(deftest KiintiotTallennettu
  (async done
    (go
      (let [ch (chan)]
        (is (= {:kiintiot [{:id 1}]
                :kiintioiden-tallennus-kaynnissa? false}
               (e! (tiedot/->KiintiotTallennettu [{:id 1}] ch))))
        (= [{:id 1}] (<! ch))
        (done)))))

(deftest KiintiotEiTallennettu
  (async done
    (go
      (let [ch (chan)]
        (is (= {:kiintioiden-tallennus-kaynnissa? false
                :kiintiot [{:foo :bar}]}
               (e! (tiedot/->KiintiotEiTallennettu {:msg :error} ch)
                   {:kiintiot [{:foo :bar}]})))
        (= [{:foo :bar}] (<! ch))
        (done)))))

(deftest nakymassa
  (is (= {:nakymassa? true}
         (e! (tiedot/->Nakymassa? true))))

  (is (= {:nakymassa? false}
         (e! (tiedot/->Nakymassa? false)))))

(deftest PaivitaValinnat
  (vaadi-async-kutsut
    #{tiedot/->HaeKiintiot}
    (is (= {:valinnat {:foo :bar}}
          (e! (tiedot/->PaivitaValinnat {:foo :bar}))))))

(deftest HaeKiintiot
  (vaadi-async-kutsut
    #{tiedot/->KiintiotEiHaettu tiedot/->KiintiotHaettu}
    (is (= {:kiintioiden-haku-kaynnissa? true}
           (e! (tiedot/->HaeKiintiot)))))

  (vaadi-async-kutsut
    #{}
    (is (= {:kiintioiden-haku-kaynnissa? true
            :foo :bar}
           (e! (tiedot/->HaeKiintiot) {:kiintioiden-haku-kaynnissa? true
                                       :foo :bar})))))

(deftest KiintiotHaettu
  (is (= {:kiintioiden-haku-kaynnissa? false
          :kiintiot [{:id 1}]}
         (e! (tiedot/->KiintiotHaettu [{:id 1}])))))

(deftest KiintiotEiHaettu
  (is (= {:kiintioiden-haku-kaynnissa? false
          :kiintiot []}
         (e! (tiedot/->KiintiotEiHaettu)))))

(deftest TallennaKiintiot
  (vaadi-async-kutsut
    #{tiedot/->KiintiotEiTallennettu tiedot/->KiintiotTallennettu}

    (is (= {:kiintioiden-tallennus-kaynnissa? true}
           (e! (tiedot/->TallennaKiintiot [{:id 1}] (chan))))))

  (vaadi-async-kutsut
    #{}
    (is (= {:kiintioiden-tallennus-kaynnissa? true
            :foo :bar}
           (e! (tiedot/->TallennaKiintiot [{:id 1}] (chan))
               {:kiintioiden-tallennus-kaynnissa? true
                :foo :bar})))))