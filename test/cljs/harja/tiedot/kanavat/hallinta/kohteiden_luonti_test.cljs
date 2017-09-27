(ns harja.tiedot.kanavat.hallinta.kohteiden-luonti-test
  (:require [harja.tiedot.kanavat.hallinta.kohteiden-luonti :as tiedot]
            [clojure.test :refer :all]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))
