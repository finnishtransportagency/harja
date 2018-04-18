(ns harja.tiedot.tieluvat.tieluvat-test
  (:require [clojure.test :refer-macros [deftest is testing]]
            [harja.tiedot.tieluvat.tieluvat :as tiedot]
            [harja.domain.tielupa :as tielupa]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]))


(deftest valinta-wrap
  (let [atomi (tiedot/valinta-wrap e! {:valinnat {:foo 1}} :foo)]
    (is (= 1 @atomi))

    (vaadi-async-kutsut
      #{tiedot/->HaeTieluvat}
      (is (= {:valinnat {:foo 2}}
             (reset! atomi 2))))))

(deftest kenttien-nayttaminen
  (let [nayta? (partial
                 tiedot/nayta-kentat?
                 (fn [_] {:skeemat
                          [{:nimi :foo}
                           {:hae :bar
                            :nimi :ei-tama}]}))]
    (is (true? (nayta? {:foo 1})))
    (is (true? (nayta? {:bar 1})))
    (is (true? (nayta? {:foo 1 :bar 1})))
    (is (true? (nayta? {:bar 1 :ei-tama 1})))
    (is (true? (nayta? {:bar 1 :asd 1})))

    (is (false? (nayta? {:ei-tama 1})))
    (is (false? (nayta? {:asd 1})))))

(deftest nakymassa?
  (is (= {:nakymassa? true} (e! (tiedot/->Nakymassa? true))))
  (is (= {:nakymassa? false} (e! (tiedot/->Nakymassa? false)))))

(deftest valintojen-paivitys
  (vaadi-async-kutsut
    #{tiedot/->HaeTieluvat}
    (let [v (zipmap tiedot/valintojen-avaimet (repeat 1))]
      (is (= {:valinnat v}
             (e! (tiedot/->PaivitaValinnat v))))))

  (vaadi-async-kutsut
    #{tiedot/->HaeTieluvat}
    (let [u {}
          tila {:valinnat {:foo :bar}}]
      (is (= {:valinnat (:valinnat tila)}
             (e! (tiedot/->PaivitaValinnat u) tila))))))

(deftest tielupien-haku
  (vaadi-async-kutsut
    #{tiedot/->TieluvatEiHaettu tiedot/->TieluvatEiHaettu}
    (is (= {:tielupien-haku-kaynnissa? true}
           (e! (tiedot/->HaeTieluvat)))))

  (vaadi-async-kutsut
    #{}
    (is (= {:tielupien-haku-kaynnissa? true}
           (e! (tiedot/->HaeTieluvat)
               {:tielupien-haku-kaynnissa? true})))))

(deftest tielupia-ei-haettu
  (is (= {:tielupien-haku-kaynnissa? false
          :haetut-tieluvat []}
         (e! (tiedot/->TieluvatEiHaettu {})))))

(deftest tielupia-ei-haettu
  (is (= {:tielupien-haku-kaynnissa? false
          :haetut-tieluvat [{:id 1}]}
         (e! (tiedot/->TieluvatHaettu [{:id 1}])))))

(deftest tieluvan-valinta
  (is (= {:valittu-tielupa {:bar :baz}}
         (e! (tiedot/ValitseTielupa {:bar :baz})))))
