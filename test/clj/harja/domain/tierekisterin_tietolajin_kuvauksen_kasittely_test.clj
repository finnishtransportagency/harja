(ns harja.domain.tierekisterin-tietolajin-kuvauksen-kasittely-test
  (:require [clojure.test :refer [deftest is]]
            [harja.testi :refer :all]
            [harja.domain.tierekisterin-tietolajin-kuvauksen-kasittely :as tierekisteri-tietue]
            [clj-time.core :as t]))

(deftest tarkista-kentan-arvon-hakeminen-merkkijonosta
  (let [kenttien-kuvaukset [{:kenttatunniste "a"
                             :jarjestysnumero 1
                             :tietotyyppi :merkkijono
                             :pituus 5}
                            {:kenttatunniste "b"
                             :jarjestysnumero 2
                             :tietotyyppi :merkkijono
                             :pituus 5}
                            {:kenttatunniste "c"
                             :jarjestysnumero 3
                             :tietotyyppi :merkkijono
                             :pituus 10}
                            {:kenttatunniste "d"
                             :jarjestysnumero 4
                             :tietotyyppi :merkkijono
                             :pituus 3}]
        arvot-string "tes  ti   testi     123"]
    (is (= "testi" (#'tierekisteri-tietue/hae-arvo arvot-string kenttien-kuvaukset 3)))
    (is (= "ti" (#'tierekisteri-tietue/hae-arvo arvot-string kenttien-kuvaukset 2)))
    (is (= "123" (#'tierekisteri-tietue/hae-arvo arvot-string kenttien-kuvaukset 4)))))

(deftest tarkista-paivamaarien-kasittely
  (let [kenttien-kuvaukset [{:kenttatunniste "a"
                             :jarjestysnumero 1
                             :tietotyyppi :paivamaara
                             :pituus 10}]
        arvot-string "2009-03-23"
        muunnos (#'tierekisteri-tietue/hae-arvo arvot-string kenttien-kuvaukset 1)]
    (is (= muunnos "2009-03-23"))))


(deftest testaa-arvot-mapin-muuntaminen-merkkijonoksi
  (let [muunnos (tierekisteri-tietue/tietolajin-arvot-map->string
                  {"a" "testi"
                   "b" "1"}
                  {:tunniste "tl506",
                   :ominaisuudet
                   [{:kenttatunniste "a"
                     :jarjestysnumero 1
                     :pakollinen true
                     :tietotyyppi :merkkijono
                     :pituus 20}
                    {:kenttatunniste "b"
                     :jarjestysnumero 2
                     :tietotyyppi :merkkijono
                     :pituus 10}]})]
    (is (= "testi               1         "
           muunnos))))

(deftest testaa-arvot-merkkijonon-muuntaminen-mapiksi
  (let [muunnos (tierekisteri-tietue/tietolajin-arvot-merkkijono->map
                  "testi               1         "
                  {:tunniste "tl506",
                   :ominaisuudet
                   [{:kenttatunniste "a"
                     :jarjestysnumero 1
                     :pakollinen true
                     :tietotyyppi :merkkijono
                     :pituus 20}
                    {:kenttatunniste "b"
                     :jarjestysnumero 2
                     :tietotyyppi :merkkijono
                     :pituus 10}]})]
    (is (= {"a" "testi"
            "b" "1"}
           muunnos))))

(deftest tarkista-kenttien-validoinnit
  (let [tietolajin-kuvaus {:tunniste "tl506",
                           :ominaisuudet
                           [{:kenttatunniste "tie"
                             :jarjestysnumero 1
                             :pakollinen true
                             :tietotyyppi :merkkijono
                             :pituus 20}
                            {:kenttatunniste "tunniste"
                             :jarjestysnumero 2
                             :tietotyyppi :merkkijono
                             :pituus 20}]}]
    (is (thrown-with-msg? Exception #"Virhe tietolajin tl506 arvojen käsittelyssä: Pakollinen arvo puuttuu kentästä: tie"
                          (tierekisteri-tietue/tietolajin-arvot-map->string
                            {"tie" nil}
                            tietolajin-kuvaus))
        "Puuttuva pakollinen arvo huomattiin")
    (is (thrown-with-msg? Exception #"Virhe tietolajin tl506 arvojen käsittelyssä: Liian pitkä arvo kentässä: tunniste maksimipituus: 20"
                          (tierekisteri-tietue/tietolajin-arvot-map->string
                            {"tie" "123"
                             "tunniste" "1234567890112345678901"}
                            tietolajin-kuvaus))
        "Liian pitkä arvo huomattiin")))

(deftest tarkista-tietolajin-arvojen-validointi-menee-lapi
  (let [arvot {"LMNUMERO" "9987"
                            "SIVUTIE" "2"}
        kenttien-kuvaukset {:tunniste "tl506",
                            :ominaisuudet
                            [{:kenttatunniste "LMNUMERO"
                              :jarjestysnumero 1
                              :pakollinen true
                              :tietotyyppi :merkkijono
                              :pituus 20}
                             {:kenttatunniste "SIVUTIE"
                              :jarjestysnumero 2
                              :tietotyyppi :merkkijono
                              :pituus 10}]}]
    (tierekisteri-tietue/validoi-tietolajin-arvot "tl506" arvot kenttien-kuvaukset)
    (is true "Poikkeusta ei heitetty")))

(deftest tarkista-tietolajin-arvojen-validointi-heittaa-poikkeuksen-kun-avain-puuttuu
  (let [arvot {"SIVUTIE" "2"}
        kenttien-kuvaukset {"tunniste" "tl506",
                            :ominaisuudet
                            [{:kenttatunniste "LMNUMERO"
                              :jarjestysnumero 1
                              :pakollinen true
                              :tietotyyppi :merkkijono
                              :pituus 20}
                             {:kenttatunniste "SIVUTIE"
                              :jarjestysnumero 2
                              :tietotyyppi :merkkijono
                              :pituus 10}]}]
    (is (thrown? Exception
                 (tierekisteri-tietue/validoi-tietolajin-arvot "tl506" arvot kenttien-kuvaukset)))))

(deftest tarkista-tietolajin-arvojen-validointi-heittaa-poikkeuksen-kun-ylimaarainen-kentta
  (let [arvot {"LMNUMERO" "9987"
                            "SIVUTIE" "12345678900"
                            "YLIMAARAINEN" "kentta"}
        kenttien-kuvaukset {:tunniste "tl506",
                            :ominaisuudet
                            [{:kenttatunniste "LMNUMERO"
                              :jarjestysnumero 1
                              :pakollinen true
                              :tietotyyppi :merkkijono
                              :pituus 20}
                             {:kenttatunniste "SIVUTIE"
                              :jarjestysnumero 2
                              :tietotyyppi :merkkijono
                              :pituus 10}]}]
    (is (thrown? Exception
                 (tierekisteri-tietue/validoi-tietolajin-arvot "tl506" arvot kenttien-kuvaukset)))))

(deftest tarkista-tietolajin-arvojen-validointi-heittaa-poikkeuksen-kun-tyhja-mappi
  (let [arvot {}
        kenttien-kuvaukset {:tunniste "tl506",
                            :ominaisuudet
                            [{:kenttatunniste "LMNUMERO"
                              :jarjestysnumero 1
                              :pakollinen true
                              :tietotyyppi :merkkijono
                              :pituus 20}
                             {:kenttatunniste "SIVUTIE"
                              :jarjestysnumero 2
                              :tietotyyppi :merkkijono
                              :pituus 10}]}]
    (is (thrown? Exception
                 (tierekisteri-tietue/validoi-tietolajin-arvot "tl506" arvot kenttien-kuvaukset)))))