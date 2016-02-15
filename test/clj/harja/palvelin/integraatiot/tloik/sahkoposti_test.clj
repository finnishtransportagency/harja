(ns harja.palvelin.integraatiot.tloik.sahkoposti-test
  (:require [harja.palvelin.integraatiot.tloik.sahkoposti :as sut]
            [clojure.test :as t :refer [deftest is]]))

(deftest kuittausviestin-lukeminen
  (let [{:keys [urakka-id ilmoitus-id kuittaustyyppi kommentti]}
        (sut/lue-kuittausviesti "#[4/1234] Toimenpidepyyntö" "[Aloitettu] aletaanpa hommiin")]
    (is (= urakka-id 4))
    (is (= ilmoitus-id 1234))
    (is (= kuittaustyyppi :aloitettu))
    (is (= kommentti "aletaanpa hommiin"))))

(deftest virheellisen-viestin-lukeminen
  (is (= '(:virhe) (keys (sut/lue-kuittausviesti "#[12/3333 asdasd" "eipä mitään")))))
