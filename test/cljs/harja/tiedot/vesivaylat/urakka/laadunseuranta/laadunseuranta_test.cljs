(ns harja.tiedot.vesivaylat.urakka.laadunseuranta.laadunseuranta-test
  (:require [harja.tiedot.vesivaylat.urakka.laadunseuranta.laadunseuranta :as laadunseuranta]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))
