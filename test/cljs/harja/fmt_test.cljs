(ns harja.fmt-test
  (:require
    [cljs.test :as test :refer-macros [deftest is]]
    [harja.fmt :as fmt]))

(deftest kuvaile-aikavali-toimii
  (is (thrown? js/Error (fmt/kuvaile-paivien-maara nil)))
  (is (thrown? js/Error (fmt/kuvaile-paivien-maara -4)))
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

  (is (thrown? js/Error (fmt/euro nil)))
  (is (thrown? js/Error (fmt/euro "asd")))
  (is (thrown? js/Error (fmt/euro "")))
  (is (= (fmt/euro "5") "5,00 €"))
  (is (= (fmt/euro 5) "5,00 €"))

  (is (nil? (fmt/lampotila nil)))
  (is (nil? (fmt/lampotila "asd")))
  (is (nil? (fmt/lampotila "")))
  (is (= (fmt/lampotila "5") "5,0\u00A0°C"))
  (is (= (fmt/lampotila 5) "5,0\u00A0°C"))

  (is (nil? (fmt/prosentti nil)))
  (is (nil? (fmt/prosentti "asd")))
  (is (nil? (fmt/prosentti "")))
  (is (= (fmt/prosentti "5") "5,0\u00A0%"))
  (is (= (fmt/prosentti 5) "5,0\u00A0%"))

  (is (nil? (fmt/desimaaliluku nil)))
  (is (nil? (fmt/desimaaliluku "asd")))
  (is (nil? (fmt/desimaaliluku "")))
  (is (= (fmt/desimaaliluku "5") "5,00"))
  (is (= (fmt/desimaaliluku 5) "5,00")))

(deftest opt-formatterien-virheenkasittely
  ;; Nämä formatterit käyttävät kaikki lopulta desimaalilukuformatteria, siksi sama testiblokki.
  ;; Huomaa erot cljs ja clj välillä!

  (is (= (fmt/euro-opt nil) ""))
  (is (thrown? js/Error (fmt/euro-opt "asd")))
  (is (= (fmt/euro-opt "") ""))
  (is (= (fmt/euro-opt "5") "5,00 €"))
  (is (= (fmt/euro-opt 5) "5,00 €"))

  (is (= (fmt/lampotila-opt nil) ""))
  (is (nil? (fmt/lampotila-opt "asd")))
  (is (= (fmt/lampotila-opt "") ""))
  (is (= (fmt/lampotila-opt "5") "5,0\u00A0°C"))
  (is (= (fmt/lampotila-opt 5) "5,0\u00A0°C"))

  (is (= (fmt/prosentti-opt nil) ""))
  (is (nil? (fmt/prosentti-opt "asd")))
  (is (= (fmt/prosentti-opt "") ""))
  (is (= (fmt/prosentti-opt "5") "5,0\u00A0%"))
  (is (= (fmt/prosentti-opt 5) "5,0\u00A0%"))

  (is (= (fmt/desimaaliluku-opt nil) ""))
  (is (nil? (fmt/desimaaliluku-opt "asd")))
  (is (= (fmt/desimaaliluku-opt "") ""))
  (is (= (fmt/desimaaliluku-opt "5") "5,00"))
  (is (= (fmt/desimaaliluku-opt 5) "5,00")))

(deftest desimaaliluku
  (is (= (fmt/desimaaliluku 123 nil nil false) "123") "tarkkuuden voi jättää määrittelemättä kokonaisluvulle")
  (is (= (fmt/desimaaliluku 123.1 nil nil false) "123,1") "tarkkuuden voi jättää määrittelemättä desimaaliluvulle")
  (is (= (fmt/desimaaliluku 123.123456789 nil nil false) "123,123456789") "tarkkuuden voi jättää määrittelemättä pitkälle desimaaliluvulle")
  (is (= (fmt/desimaaliluku 123 2 3 false) "123,00") "min-tarkkuus toimii")
  (is (= (fmt/desimaaliluku 123.123456789 nil 7 false) "123,1234568") "max-tarkkuus toimii")
  (is (= (fmt/desimaaliluku 123.123456789 2 nil false) "123,123456789") "min-tarkkuus toimii, vaikka max-tarkkuutta ei ole määritetty")
  (is (= (fmt/desimaaliluku 777777777.1234567 nil 7 false) "777777777,1234567") "kokonaislukuosa 9 numeroa, desimaaliosa 7 numeroa")
  (is (= (fmt/desimaaliluku 123.123456789012 nil nil false) "123,123456789") "max-desimaalit oletusarvo on 10")
  (is (= (fmt/desimaaliluku 777777777.1234567 nil 7 true) "777 777 777,1234567") "ryhmittely toimii"))
