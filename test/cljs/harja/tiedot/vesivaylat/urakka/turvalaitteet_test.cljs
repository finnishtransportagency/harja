(ns harja.tiedot.vesivaylat.urakka.turvalaitteet-test
  (:require [harja.tiedot.vesivaylat.urakka.turvalaitteet :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]
            [harja.pvm :as pvm]))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))