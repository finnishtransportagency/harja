(ns harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet-test
  (:require [clojure.test :refer-macros [deftest is testing]]
            [harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet :as tiedot]
            [harja.domain.kanavat.hairiotilanne :as hairio]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))


(deftest PaivitaValinnat
  (vaadi-async-kutsut
    #{tiedot/->HaeHairiotilanteet}
    (is (= {:valinnat {:foo :bar}}
           (e! (tiedot/->PaivitaValinnat {:foo :bar}))))))

(deftest HaeHairiotilanteet
  (vaadi-async-kutsut
    #{tiedot/->HairiotilanteetHaettu tiedot/->HairiotilanteetEiHaettu}
    (is (= {:hairiotilanteiden-haku-kaynnissa? true}
           (e! (tiedot/->HaeHairiotilanteet {:urakka {:id 1}})))))

  ;; Ei tehdä hakua jos on jo käynnissä
  (vaadi-async-kutsut
    #{}
    (is (= {:hairiotilanteiden-haku-kaynnissa? true
            :foo :bar}
           (e! (tiedot/->HaeHairiotilanteet {:urakka {:id 2}})
               {:hairiotilanteiden-haku-kaynnissa? true
                :foo :bar})))))

(deftest HairiotilanteetHaettu
  (is (= {:hairiotilanteiden-haku-kaynnissa? false
          :hairiotilanteet [{::hairio/id 1}]}
         (e! (tiedot/->HairiotilanteetHaettu [{::hairio/id 1}])))))

(deftest HairiotilanteetEiHaettu
  (is (= {:hairiotilanteiden-haku-kaynnissa? false
          :hairiotilanteet []}
         (e! (tiedot/->HairiotilanteetEiHaettu)))))