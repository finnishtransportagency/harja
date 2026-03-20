(ns harja.tyokalut.figwheel-konffi-test
  (:require [clojure.test :refer [deftest is testing]]
            [harja.tyokalut.figwheel-konffi :as figwheel-konffi]))

(deftest paivita-ring-server-portti-paivittaa-vain-portin
  (testing "Ring-server portti päivitetään metadataan ja muut metadata-avaimet säilyvät"
    (let [alku-build (with-meta {:main 'foo.bar}
                      {:watch-dirs ["src/cljs"]
                       :ring-server-options {:port 3449}})
          uusi-build (figwheel-konffi/paivita-ring-server-portti alku-build 3450)]
      (is (= 3450 (get-in (meta uusi-build) [:ring-server-options :port])))
      (is (= ["src/cljs"] (:watch-dirs (meta uusi-build))))
      (is (= {:main 'foo.bar} (dissoc uusi-build :dummy))))))
