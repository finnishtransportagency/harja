(ns harja.palvelin.integraatiot.tierekisteri.tietolajin-kuvauksen-kasittely-test
  (:require [clojure.test :refer [deftest is]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.tierekisteri.tietolajin-kuvauksen-kasittely :as tierekisteri-tietue]
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
  (let [muunnos (tierekisteri-tietue/tietolajin-arvot-map->merkkijono
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

(deftest tarkista-pakollisen-kentan-validointi
  (let [tietolajin-kuvaus {:tunniste "tl506",
                           :ominaisuudet
                           [{:kenttatunniste "tie"
                             :jarjestysnumero 1
                             :pakollinen true
                             :tietotyyppi :merkkijono
                             :pituus 20}]}]
    (is (thrown-with-msg? Exception #"Virhe tietolajin tl506 arvojen käsittelyssä: Pakollinen arvo puuttuu kentästä 'tie'."
                          (tierekisteri-tietue/tietolajin-arvot-map->merkkijono
                            {"tie" nil}
                            tietolajin-kuvaus))
        "Puuttuva pakollinen arvo huomattiin")))

(deftest tarkista-pakollisen-kentan-validointi-menee-lapi
  (let [tietolajin-kuvaus {:tunniste "tl506",
                           :ominaisuudet
                           [{:kenttatunniste "tie"
                             :jarjestysnumero 1
                             :pakollinen true
                             :tietotyyppi :merkkijono
                             :pituus 20}]}]
    (tierekisteri-tietue/tietolajin-arvot-map->merkkijono
      {"tie" "123"}
      tietolajin-kuvaus)
    (is true "Poikkeusta ei heitetty")))

(deftest tarkista-liian-pitkan-kentan-validointi
  (let [tietolajin-kuvaus {:tunniste "tl506",
                           :ominaisuudet
                           [{:kenttatunniste "tunniste"
                             :jarjestysnumero 1
                             :tietotyyppi :merkkijono
                             :pituus 20}]}]
    (is (thrown-with-msg? Exception #"Virhe tietolajin tl506 arvojen käsittelyssä: Liian pitkä arvo kentässä 'tunniste', maksimipituus: 20."
                          (tierekisteri-tietue/tietolajin-arvot-map->merkkijono
                            {"tunniste" "1234567890112345678901"}
                            tietolajin-kuvaus))
        "Liian pitkä arvo huomattiin")))

(deftest tarkista-liian-pitkan-kentan-validointi-menee-lapi
  (let [tietolajin-kuvaus {:tunniste "tl506",
                           :ominaisuudet
                           [{:kenttatunniste "tunniste"
                             :jarjestysnumero 1
                             :tietotyyppi :merkkijono
                             :pituus 20}]}]
    (tierekisteri-tietue/tietolajin-arvot-map->merkkijono
      {"tunniste" "12345678901234567890"}
      tietolajin-kuvaus)
    (is true "Poikkeusta ei heitetty")))

(deftest tarkista-numero-kentan-validointi
  (let [tietolajin-kuvaus {:tunniste "tl506",
                           :ominaisuudet
                           [{:kenttatunniste "tunniste"
                             :jarjestysnumero 1
                             :tietotyyppi :numeerinen
                             :pituus 20}]}]
    (is (thrown-with-msg? Exception #"Virhe tietolajin tl506 arvojen käsittelyssä: Kentän 'tunniste' arvo ei ole kokonaisluku."
                          (tierekisteri-tietue/tietolajin-arvot-map->merkkijono
                            {"tunniste" "a"}
                            tietolajin-kuvaus))
        "Ei-numero tyyppinen arvo huomattiin")))

(deftest tarkista-numero-kentan-validointi-menee-lapi
  (let [tietolajin-kuvaus {:tunniste "tl506",
                           :ominaisuudet
                           [{:kenttatunniste "tunniste"
                             :jarjestysnumero 1
                             :tietotyyppi :numeerinen
                             :pituus 20}]}]
    (tierekisteri-tietue/tietolajin-arvot-map->merkkijono
      {"tunniste" "42"}
      tietolajin-kuvaus)
    (is true "Poikkeusta ei heitetty")))

(deftest tarkista-pvm-kentan-validointi
  (let [tietolajin-kuvaus {:tunniste "tl506",
                           :ominaisuudet
                           [{:kenttatunniste "tunniste"
                             :jarjestysnumero 1
                             :tietotyyppi :paivamaara
                             :pituus 20}]}]
    (is (thrown-with-msg? Exception #"Virhe tietolajin tl506 arvojen käsittelyssä: Kentän 'tunniste' arvo ei ole muotoa iso-8601."
                          (tierekisteri-tietue/tietolajin-arvot-map->merkkijono
                            {"tunniste" "2010-12"}
                            tietolajin-kuvaus))
        "Ei-pvm tyyppinen kenttä huomattiin")))

(deftest tarkista-pvm-kentan-validointi-menee-lapi
  (let [tietolajin-kuvaus {:tunniste "tl506",
                           :ominaisuudet
                           [{:kenttatunniste "tunniste"
                             :jarjestysnumero 2
                             :tietotyyppi :paivamaara
                             :pituus 20}]}]
    (tierekisteri-tietue/tietolajin-arvot-map->merkkijono
      {"tunniste" "2010-12-12"}
      tietolajin-kuvaus)
    (is true "Poikkeusta ei heitetty")))

(deftest tarkista-koodisto-kentan-validointi
  (let [tietolajin-kuvaus {:tunniste "tl506",
                           :ominaisuudet
                           [{:kenttatunniste "tunniste",
                             :selite "Tunniste",
                             :jarjestysnumero 2,
                             :koodisto
                             [{:koodiryhma "kuntoluokk",
                               :koodi 1,
                               :lyhenne "huono",
                               :selite "Ala-arvoinen",
                               :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
                              {:koodiryhma "kuntoluokk",
                               :koodi 2,
                               :lyhenne "välttävä",
                               :selite "Merkittäviä puutteita",
                               :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
                              {:koodiryhma "kuntoluokk",
                               :koodi 3,
                               :lyhenne "tyydyttävä",
                               :selite "Epäoleellisia puutteita",
                               :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
                              {:koodiryhma "kuntoluokk",
                               :koodi 4,
                               :lyhenne "hyvä",
                               :selite "hyvä",
                               :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
                              {:koodiryhma "kuntoluokk",
                               :koodi 5,
                               :lyhenne "erinomaine",
                               :selite "erinomainen",
                               :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}],
                             :desimaalit nil,
                             :voimassaolo
                             {:alkupvm #inst "2009-03-22T22:00:00.000-00:00", :loppupvm nil},
                             :alaraja nil,
                             :pakollinen false,
                             :tietotyyppi :koodisto,
                             :pituus 1,
                             :ylaraja nil}]}]
    (is (thrown-with-msg? Exception #"Virhe tietolajin tl506 arvojen käsittelyssä: Kentän 'tunniste' arvo ei sisälly koodistoon."
                          (tierekisteri-tietue/tietolajin-arvot-map->merkkijono
                            {"tunniste" "8"}
                            tietolajin-kuvaus))
        "Koodistoon kuulumaton arvo huomattiin")

    (try (tierekisteri-tietue/tietolajin-arvot-map->merkkijono
           {"tunniste" ""}
           tietolajin-kuvaus)
         (is true "Valinnaiselle kentälle hyväksyttiin tyhjä arvo")
         (catch Exception e
           (is false "Valinnaiselle kentälle täytyy hyväksyä tyhjä arvo")))

    (is (thrown-with-msg? Exception #"Virhe tietolajin tl506 arvojen käsittelyssä: Kentän 'tunniste' arvo ei sisälly koodistoon."
                          (tierekisteri-tietue/tietolajin-arvot-map->merkkijono
                            {"tunniste" "8"}
                            (assoc tietolajin-kuvaus :ominaisuudet [(assoc (first (:ominaisuudet tietolajin-kuvaus)) :pakollinen true)]))
                          "Pakolliselle kentälle ei hyväksytä tyhjää arvoa"))))

(deftest tarkista-koodisto-kentan-validointi-menee-lapi
  (let [tietolajin-kuvaus {:tunniste "tl506",
                           :ominaisuudet
                           [{:kenttatunniste "tunniste",
                             :selite "Tunniste",
                             :jarjestysnumero 2,
                             :koodisto
                             [{:koodiryhma "kuntoluokk",
                               :koodi 1,
                               :lyhenne "huono",
                               :selite "Ala-arvoinen",
                               :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
                              {:koodiryhma "kuntoluokk",
                               :koodi 2,
                               :lyhenne "välttävä",
                               :selite "Merkittäviä puutteita",
                               :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
                              {:koodiryhma "kuntoluokk",
                               :koodi 3,
                               :lyhenne "tyydyttävä",
                               :selite "Epäoleellisia puutteita",
                               :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
                              {:koodiryhma "kuntoluokk",
                               :koodi 4,
                               :lyhenne "hyvä",
                               :selite "hyvä",
                               :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
                              {:koodiryhma "kuntoluokk",
                               :koodi 5,
                               :lyhenne "erinomaine",
                               :selite "erinomainen",
                               :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}],
                             :desimaalit nil,
                             :voimassaolo
                             {:alkupvm #inst "2009-03-22T22:00:00.000-00:00", :loppupvm nil},
                             :alaraja nil,
                             :pakollinen false,
                             :tietotyyppi :koodisto,
                             :pituus 1,
                             :ylaraja nil}]}]
    (tierekisteri-tietue/tietolajin-arvot-map->merkkijono
      {"tunniste" "3"}
      tietolajin-kuvaus)
    (is true "Poikkeusta ei heitetty")))

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

(deftest tarkista-tietolajin-arvojen-validointi-ei-hyvaksy-tyhjaa-tunnistetta
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
    (is (thrown? AssertionError
                 (tierekisteri-tietue/validoi-tietolajin-arvot nil arvot kenttien-kuvaukset)))))

(deftest tarkista-tietolajin-arvojen-validointi-ei-hyvaksy-tyhjia-arvoja
  (let [kenttien-kuvaukset {:tunniste "tl506",
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
    (is (thrown? AssertionError
                 (tierekisteri-tietue/validoi-tietolajin-arvot "tl506" nil kenttien-kuvaukset)))))

(deftest tarkista-tietolajin-arvojen-validointi-ei-hyvaksy-tyhjia-kenttakuvauksia
  (let [arvot {"LMNUMERO" "9987"
               "SIVUTIE" "2"}]
    (is (thrown? AssertionError
                 (tierekisteri-tietue/validoi-tietolajin-arvot "tl506" arvot nil)))))

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
                              :pakollinen false
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