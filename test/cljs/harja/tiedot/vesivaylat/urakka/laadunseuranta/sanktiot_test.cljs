(ns harja.tiedot.vesivaylat.urakka.laadunseuranta.sanktiot-test
  (:require [harja.tiedot.vesivaylat.urakka.laadunseuranta.sanktiot :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))
