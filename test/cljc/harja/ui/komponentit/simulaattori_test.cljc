(ns harja.ui.komponentit.simulaattori-test
  (:require [clojure.test :refer [deftest is testing]]
            [harja.ui.komponentit.simulaattori :as simulaattori]))

(deftest kelvollinen-puu-hyvaksytaan
  (is (simulaattori/kelvollinen?
        [{:komponentti :lomake
          :lapset [{:komponentti :valintaryhma}]}])))

(deftest tuntematon-komponentti-hylataan
  (is (= ["Tuntematon komponentti: :ei-ole"]
         (simulaattori/virheet [{:komponentti :ei-ole}]))))

(deftest vaarallinen-vanhempi-lapsi-suhde-hylataan
  (testing "lomake ei voi sisältää nappia suoraan sivun juurena"
    (is (= [":nappi: vanhempi=:sivu ei ole sallittu"]
           (simulaattori/virheet [{:komponentti :nappi}])))))