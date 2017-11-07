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
    #{tiedot/->HaeHairioTilanteet}
    (is (= {:valinnat {:foo :bar}}
           (e! (tiedot/->PaivitaValinnat {:foo :bar}))))))

(deftest HaeHairioTilanteet
  (vaadi-async-kutsut
    #{tiedot/->HairioTilanteetHaettu tiedot/->HairioTilanteetEiHaettu}
    (is (= {:hairiotilanteiden-haku-kaynnissa? true}
           (e! (tiedot/->HaeHairioTilanteet {:urakka {:id 1}})))))

  ;; Ei tehdä hakua jos on jo käynnissä
  (vaadi-async-kutsut
    #{}
    (is (= {:hairiotilanteiden-haku-kaynnissa? true
            :foo :bar}
           (e! (tiedot/->HaeHairioTilanteet {:urakka {:id 2}})
               {:hairiotilanteiden-haku-kaynnissa? true
                :foo :bar})))))

(deftest HairioTilanteetHaettu
  (is (= {:hairiotilanteiden-haku-kaynnissa? false
          :hairiotilanteet [{::hairio/id 1}]}
         (e! (tiedot/->HairioTilanteetHaettu [{::hairio/id 1}])))))

(deftest HairioTilanteetEiHaettu
  (is (= {:hairiotilanteiden-haku-kaynnissa? false
          :hairiotilanteet []}
         (e! (tiedot/->HairioTilanteetEiHaettu)))))