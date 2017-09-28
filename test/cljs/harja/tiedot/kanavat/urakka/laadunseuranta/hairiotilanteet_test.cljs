(ns harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet-test
  (:require [harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))
