(ns harja.fmt-test
  (:require
    [clojure.test :refer :all]
    [harja.fmt :as fmt]
    [taoensso.timbre :as log]))

(deftest kuvaile-aikavali-toimii
  (is (thrown? AssertionError (fmt/kuvaile-paivien-maara nil)))
  (is (thrown? AssertionError (fmt/kuvaile-paivien-maara -4)))
  (is (= (fmt/kuvaile-paivien-maara 0) ""))
  (is (= (fmt/kuvaile-paivien-maara 1) "1 päivä"))
  (is (= (fmt/kuvaile-paivien-maara 6) "6 päivää"))
  (is (= (fmt/kuvaile-paivien-maara 7) "1 viikko"))
  (is (= (fmt/kuvaile-paivien-maara 10) "1 viikko"))
  (is (= (fmt/kuvaile-paivien-maara 15) "2 viikkoa"))
  (is (= (fmt/kuvaile-paivien-maara 30) "1 kuukausi"))
  (is (= (fmt/kuvaile-paivien-maara 90) "3 kuukautta"))
  (is (= (fmt/kuvaile-paivien-maara 365) "1 vuosi"))
  (is (= (fmt/kuvaile-paivien-maara 850) "2 vuotta"))

  (is (= (fmt/kuvaile-paivien-maara 0 {:lyhenna-yksikot? true}) ""))
  (is (= (fmt/kuvaile-paivien-maara 1 {:lyhenna-yksikot? true}) "1pv"))
  (is (= (fmt/kuvaile-paivien-maara 6 {:lyhenna-yksikot? true}) "6pv"))
  (is (= (fmt/kuvaile-paivien-maara 7 {:lyhenna-yksikot? true}) "1vk"))
  (is (= (fmt/kuvaile-paivien-maara 10 {:lyhenna-yksikot? true}) "1vk"))
  (is (= (fmt/kuvaile-paivien-maara 15 {:lyhenna-yksikot? true}) "2vk"))
  (is (= (fmt/kuvaile-paivien-maara 30 {:lyhenna-yksikot? true}) "1kk"))
  (is (= (fmt/kuvaile-paivien-maara 90 {:lyhenna-yksikot? true}) "3kk"))
  (is (= (fmt/kuvaile-paivien-maara 365 {:lyhenna-yksikot? true}) "1v"))
  (is (= (fmt/kuvaile-paivien-maara 850 {:lyhenna-yksikot? true}) "2v")))

(deftest formatterien-virheenkasittely
  ;; Nämä formatterit käyttävät kaikki lopulta desimaalilukuformatteria, siksi sama testiblokki.
  ;; Huomaa erot cljs ja clj välillä!

  (is (thrown? Exception (fmt/euro nil)))
  (is (thrown? Exception (fmt/euro "asd")))
  (is (thrown? Exception (fmt/euro "")))
  (is (thrown? Exception (fmt/euro "5")))
  (is (= (fmt/euro 5) "5,00 €"))

  (is (thrown? Exception (fmt/lampotila nil)))
  (is (thrown? Exception (fmt/lampotila "asd")))
  (is (thrown? Exception (fmt/lampotila "")))
  (is (thrown? Exception (fmt/lampotila "5")))
  (is (= (fmt/lampotila 5) "5,0\u00A0°C"))

  (is (thrown? Exception (fmt/prosentti nil)))
  (is (thrown? Exception (fmt/prosentti "asd")))
  (is (thrown? Exception (fmt/prosentti "")))
  (is (thrown? Exception (fmt/prosentti "5")))
  (is (= (fmt/prosentti 5) "5,0\u00A0%"))

  (is (thrown? Exception (fmt/desimaaliluku nil)))
  (is (thrown? Exception (fmt/desimaaliluku "asd")))
  (is (thrown? Exception (fmt/desimaaliluku "")))
  (is (thrown? Exception (fmt/desimaaliluku "5")))
  (is (= (fmt/desimaaliluku 5) "5,00")))

(deftest opt-formatterien-virheenkasittely
  ;; Nämä formatterit käyttävät kaikki lopulta desimaalilukuformatteria, siksi sama testiblokki.
  ;; Huomaa erot cljs ja clj välillä!

  (is (= (fmt/euro-opt nil) ""))
  (is (thrown? Exception (fmt/euro-opt "asd")))
  (is (= (fmt/euro-opt "") ""))
  (is (thrown? Exception (fmt/euro-opt "5")))
  (is (= (fmt/euro-opt 5) "5,00 €"))

  (is (= (fmt/lampotila-opt nil) ""))
  (is (thrown? Exception (fmt/lampotila-opt "asd")))
  (is (= (fmt/lampotila-opt "") ""))
  (is (thrown? Exception (fmt/lampotila-opt "5")))
  (is (= (fmt/lampotila-opt 5) "5,0 °C"))

  (is (= (fmt/prosentti-opt nil) ""))
  (is (thrown? Exception (fmt/prosentti-opt "asd")))
  (is (= (fmt/prosentti-opt "") ""))
  (is (thrown? Exception (fmt/prosentti-opt "5")))
  (is (= (fmt/prosentti-opt 5) "5,0 %"))

  (is (= (fmt/desimaaliluku-opt nil) ""))
  (is (thrown? Exception (fmt/desimaaliluku-opt "asd")))
  (is (= (fmt/desimaaliluku-opt "") ""))
  (is (thrown? Exception (fmt/desimaaliluku-opt "5")))
  (is (= (fmt/desimaaliluku-opt 5) "5,00")))

(deftest formatoi-arvo-raportille-tapaukset
  (is (= (fmt/formatoi-arvo-raportille 5.0012M) 5M))
  (is (= (fmt/formatoi-arvo-raportille 1235.1251M) 1235.13M))
  (is (= (fmt/formatoi-arvo-raportille 5.125M) 5.13M))
  (is (= (fmt/formatoi-arvo-raportille 5.135M) 5.14M))
  (is (= (fmt/formatoi-arvo-raportille (java.lang.Long/valueOf 33)) 33.00M)))
