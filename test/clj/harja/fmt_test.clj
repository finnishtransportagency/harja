(ns harja.fmt-test
  (:require
    [clojure.test :refer :all]
    [harja.fmt :as fmt]
    [taoensso.timbre :as log]
    [harja.pvm :as pvm]))

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

  (is (thrown? Exception (fmt/lampotila "5")))
  (is (= (fmt/lampotila 5) "5,0\u00A0°C"))

  (is (thrown? Exception (fmt/prosentti "5")))
  (is (= (fmt/prosentti 5) "5,0\u00A0%"))

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
  (is (= (fmt/lampotila-opt "") ""))
  (is (thrown? Exception (fmt/lampotila-opt "5")))
  (is (= (fmt/lampotila-opt 5) "5,0 °C"))

  (is (= (fmt/prosentti-opt nil) ""))
  (is (= (fmt/prosentti-opt "") ""))
  (is (thrown? Exception (fmt/prosentti-opt "5")))
  (is (= (fmt/prosentti-opt 5) "5,0 %"))

  (is (= (fmt/desimaaliluku-opt nil) ""))
  (is (= (fmt/desimaaliluku-opt "") ""))
  (is (thrown? Exception (fmt/desimaaliluku-opt "5")))
  (is (= (fmt/desimaaliluku-opt 5) "5,00")))

(deftest formatoi-arvo-raportille-tapaukset
  (is (= (fmt/formatoi-arvo-raportille 5.0012M) 5M))
  (is (= (fmt/formatoi-arvo-raportille 1235.1251M) 1235.13M))
  (is (= (fmt/formatoi-arvo-raportille 5.125M) 5.13M))
  (is (= (fmt/formatoi-arvo-raportille 5.135M) 5.14M))
  (is (= (fmt/formatoi-arvo-raportille (java.lang.Long/valueOf 33)) 33.00M)))

(deftest desimaaliluku
  (is (= (fmt/desimaaliluku 123 nil nil false) "123") "tarkkuuden voi jättää määrittelemättä kokonaisluvulle")
  (is (= (fmt/desimaaliluku 123.1 nil nil false) "123,1") "tarkkuuden voi jättää määrittelemättä desimaaliluvulle")
  (is (= (fmt/desimaaliluku 123.123456789 nil nil false) "123,123456789") "tarkkuuden voi jättää määrittelemättä pitkälle desimaaliluvulle")
  (is (= (fmt/desimaaliluku 123 2 3 false) "123,00") "min-tarkkuus toimii")
  (is (= (fmt/desimaaliluku 123.123456789 nil 7 false) "123,1234568") "max-tarkkuus toimii")
  (is (= (fmt/desimaaliluku 123.123456789 2 nil false) "123,123456789") "min-tarkkuus toimii, vaikka max-tarkkuutta ei ole määritetty")
  (is (= (fmt/desimaaliluku 777777777.1234567 nil 7 false) "777777777,1234567") "kokonaislukuosa 9 numeroa, desimaaliosa 7 numeroa")
  (is (= (fmt/desimaaliluku 123.123456789012 nil nil false) "123,123456789") "max-desimaalit oletusarvo on 10")
  (is (= (fmt/desimaaliluku 777777777.1234567 nil 7 true) "777 777 777,1234567") "ryhmittely toimii"))

(deftest hoitokauden-formatointi
  (let [hoitokaudet [[(pvm/->pvm "1.10.2020") (pvm/->pvm "30.9.2021")]
                     [(pvm/->pvm "1.10.2021") (pvm/->pvm "30.9.2022")]
                     [(pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2023")]
                     [(pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2024")]]
        valittu-hk [(pvm/->pvm "1.10.2021") (pvm/->pvm "30.9.2022")]]
    (is (= (fmt/hoitokauden-jarjestysluku-ja-vuodet valittu-hk hoitokaudet)
           "2. hoitovuosi (2021—2022)"))))

(deftest hoitokauden-formatointi-huono-input
  (let [hoitokaudet [[(pvm/->pvm "1.10.2020") (pvm/->pvm "30.9.2021")]
                     [(pvm/->pvm "1.10.2021") (pvm/->pvm "30.9.2022")]
                     [(pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2023")]
                     [(pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2024")]]
        ;; tähän input joka ei ole hoitokausi -> silti näytettävä hoitokausi oikein vaikka järjestysnumeroa ei saada
        valittu-hk [(pvm/->pvm "5.10.2021") (pvm/->pvm "4.9.2022")]]
    (is (= (fmt/hoitokauden-jarjestysluku-ja-vuodet valittu-hk hoitokaudet)
           "hoitovuosi (2021—2022)"))))

(deftest hoitovuoden-vuosimuodon-formatointi
  (let [hoitovuodet [2020 2021 2022 2023 2024]
        valittu-hk 2021]
    (is (= (fmt/hoitovuoden-jarjestysluku-ja-vuodet-vuodesta valittu-hk hoitovuodet)
          "2. hoitovuosi (2021—2022)"))))

(deftest hoitovuoden-vuosimuodon-formatointi-huono-input
  (let [hoitovuodet [2020 2021 2022 2023 2024]
        ;; Valittu hoitokausi ei ole annettujen hoitokausien sisällä
        valittu-hk 2019]
    ;; Saadaan hiotovuosi, mutta ei järjestysnumeroa
    (is (= (fmt/hoitovuoden-jarjestysluku-ja-vuodet-vuodesta valittu-hk hoitovuodet)
          "hoitovuosi (2019—2020)"))))
