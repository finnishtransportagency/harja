(ns harja.fmt-test
  (:require [cljs.test :refer-macros [deftest is]]
            [harja.fmt :as fmt]))

(deftest kuvaile-aikavali-toimii
  (is (thrown? js/Error (fmt/kuvaile-paivien-maara nil)))
  (is (thrown? js/Error (fmt/kuvaile-paivien-maara -4)))
  (is (= "" (fmt/kuvaile-paivien-maara 0)))
  (is (= "1 päivä" (fmt/kuvaile-paivien-maara 1)))
  (is (= "6 päivää" (fmt/kuvaile-paivien-maara 6)))
  (is (= "1 viikko" (fmt/kuvaile-paivien-maara 7)))
  (is (= "1 viikko" (fmt/kuvaile-paivien-maara 10)))
  (is (= "2 viikkoa" (fmt/kuvaile-paivien-maara 15)))
  (is (= "1 kuukausi" (fmt/kuvaile-paivien-maara 30)))
  (is (= "3 kuukautta" (fmt/kuvaile-paivien-maara 90)))
  (is (= "1 vuosi" (fmt/kuvaile-paivien-maara 365)))
  (is (= "2 vuotta" (fmt/kuvaile-paivien-maara 850)))
  (is (= "" (fmt/kuvaile-paivien-maara 0 {:lyhenna-yksikot? true})))
  (is (= "1pv" (fmt/kuvaile-paivien-maara 1 {:lyhenna-yksikot? true})))
  (is (= "6pv" (fmt/kuvaile-paivien-maara 6 {:lyhenna-yksikot? true})))
  (is (= "1vk" (fmt/kuvaile-paivien-maara 7 {:lyhenna-yksikot? true})))
  (is (= "1vk" (fmt/kuvaile-paivien-maara 10 {:lyhenna-yksikot? true})))
  (is (= "2vk" (fmt/kuvaile-paivien-maara 15 {:lyhenna-yksikot? true})))
  (is (= "1kk" (fmt/kuvaile-paivien-maara 30 {:lyhenna-yksikot? true})))
  (is (= "3kk" (fmt/kuvaile-paivien-maara 90 {:lyhenna-yksikot? true})))
  (is (= "1v" (fmt/kuvaile-paivien-maara 365 {:lyhenna-yksikot? true})))
  (is (= "2v" (fmt/kuvaile-paivien-maara 850 {:lyhenna-yksikot? true}))))

(deftest formatterien-virheenkasittely
  (is (thrown? js/Error (fmt/euro nil)))
  (is (thrown? js/Error (fmt/euro "asd")))
  (is (thrown? js/Error (fmt/euro "")))
  (is (= "5,00 €" (fmt/euro "5")))
  (is (= "5,00 €" (fmt/euro 5)))

  (is (= "5,0 °C" (fmt/lampotila "5")))
  (is (= "5,0 °C" (fmt/lampotila 5)))

  (is (= "5,0 %" (fmt/prosentti "5")))
  (is (= "5,0 %" (fmt/prosentti 5)))

  (is (nil? (fmt/desimaaliluku nil)))
  (is (= "asd" (fmt/desimaaliluku "asd")))
  (is (= "" (fmt/desimaaliluku "")))
  (is (= "5,00" (fmt/desimaaliluku "5")))
  (is (= "5,00" (fmt/desimaaliluku 5))))

(deftest opt-formatterien-virheenkasittely
  (is (= "" (fmt/euro-opt nil)))
  (is (thrown? js/Error (fmt/euro-opt "asd")))
  (is (= "" (fmt/euro-opt "")))
  (is (= "5,00 €" (fmt/euro-opt "5")))
  (is (= "5,00 €" (fmt/euro-opt 5)))

  (is (= "" (fmt/lampotila-opt nil)))
  (is (= "" (fmt/lampotila-opt "")))
  (is (= "5,0 °C" (fmt/lampotila-opt "5")))
  (is (= "5,0 °C" (fmt/lampotila-opt 5)))

  (is (= "" (fmt/prosentti-opt nil)))
  (is (= "" (fmt/prosentti-opt "")))
  (is (= "5,0 %" (fmt/prosentti-opt "5")))
  (is (= "5,0 %" (fmt/prosentti-opt 5)))

  (is (= "" (fmt/desimaaliluku-opt nil)))
  (is (= "" (fmt/desimaaliluku-opt "")))
  (is (= "5,00" (fmt/desimaaliluku-opt "5")))
  (is (= "5,00" (fmt/desimaaliluku-opt 5))))

(deftest desimaaliluku
  (is (= "123" (fmt/desimaaliluku 123 nil nil false)))
  (is (= "123,1" (fmt/desimaaliluku 123.1 nil nil false)))
  (is (= "123,123456789" (fmt/desimaaliluku 123.123456789 nil nil false)))
  (is (= "123,00" (fmt/desimaaliluku 123 2 3 false)))
  (is (= "123,1234568" (fmt/desimaaliluku 123.123456789 nil 7 false)))
  (is (= "123,123456789" (fmt/desimaaliluku 123.123456789 2 nil false)))
  (is (= "777777777,1234567" (fmt/desimaaliluku 777777777.1234567 nil 7 false)))
  (is (= "123,123456789" (fmt/desimaaliluku 123.123456789012 nil nil false)))
  (is (= "777 777 777,1234567" (fmt/desimaaliluku 777777777.1234567 nil 7 true))))
