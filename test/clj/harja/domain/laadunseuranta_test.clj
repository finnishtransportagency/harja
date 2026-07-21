(ns harja.domain.laadunseuranta_test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
            [clojure.set :as clj-set]
            [harja.testi :refer :all]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.pvm :as pvm]))

(deftest urakan-mahdolliset-sanktiolajit
  (let [hoidon-lajit-ilman-arvonvahennysta [:muistutus :A :B :C :pohjavesisuolan_ylitys :talvisuolan_ylitys
                                            :tenttikeskiarvo-sanktio :testikeskiarvo-sanktio :vaihtosanktio]
        hoidon-lajit-arvonvahennyksella [:muistutus :A :B :C :arvonvahennyssanktio :pohjavesisuolan_ylitys :talvisuolan_ylitys
                                         :tenttikeskiarvo-sanktio :testikeskiarvo-sanktio :vaihtosanktio]
        yllapidon-lajit [:yllapidon_sakko :yllapidon_muistutus]
        mhu24-urakka {:tyyppi :teiden-hoito :alkupvm (pvm/hoitokauden-alkupvm 2024)}
        mhu25-urakka {:tyyppi :teiden-hoito :alkupvm (pvm/hoitokauden-alkupvm 2025)}
        alueurakka {:tyyppi :hoito :alkupvm (pvm/hoitokauden-alkupvm 2019)}]

    (testing "Ylläpidon urakat (validoinnista riippumatta)"
      (doseq [validoinnit? [true false]]
        (is (= yllapidon-lajit
              (sanktio-domain/urakan-sanktiolajit {:tyyppi :paallystys} 2025 validoinnit?)
              (sanktio-domain/urakan-sanktiolajit {:tyyppi :paikkaus} 2025 validoinnit?)
              (sanktio-domain/urakan-sanktiolajit {:tyyppi :tiemerkinta} 2025 validoinnit?)
              (sanktio-domain/urakan-sanktiolajit {:tyyppi :valaistus} 2025 validoinnit?))
          (str "Ylläpidon sanktiolajit, validoinnit? " validoinnit?))))

    ;; :arvonvahennyssanktio näytetään tässä "vanhassa" listassa vain, kun uutta arvonvähennyslomaketta EI vielä näytetä.
    (testing "Validoinnit käytössä (arvonvahennys_validoinnit_kaytossa = true)"
      (is (= hoidon-lajit-arvonvahennyksella
            (sanktio-domain/urakan-sanktiolajit mhu24-urakka 2025 true))
        "MHU24-urakka ennen 2026 -> arvonvähennyssanktio mukana vanhassa listassa")
      (is (= hoidon-lajit-ilman-arvonvahennysta
            (sanktio-domain/urakan-sanktiolajit mhu24-urakka 2026 true))
        "MHU24-urakka alkuvuodesta 2026 -> ei arvonvähennyssanktiota (uusi lomake käytössä)")
      (is (= hoidon-lajit-ilman-arvonvahennysta
            (sanktio-domain/urakan-sanktiolajit mhu25-urakka 2025 true))
        "MHU25-urakka -> ei arvonvähennyssanktiota vanhassa listassa (uusi lomake käytössä aina)")
      (is (= hoidon-lajit-arvonvahennyksella
            (sanktio-domain/urakan-sanktiolajit alueurakka 2025 true))
        "Alueurakka (ei MHU25) ennen 2026 -> arvonvähennyssanktio mukana vanhassa listassa"))

    (testing "Validoinnit pois käytöstä (arvonvahennys_validoinnit_kaytossa = false)"
      (is (= hoidon-lajit-ilman-arvonvahennysta
            (sanktio-domain/urakan-sanktiolajit mhu24-urakka 2025 false))
        "MHU24-urakka, validoinnit pois -> ei arvonvähennyssanktiota vanhassa listassa")
      (is (= hoidon-lajit-ilman-arvonvahennysta
            (sanktio-domain/urakan-sanktiolajit mhu25-urakka 2025 false))
        "MHU25-urakka, validoinnit pois -> ei arvonvähennyssanktiota")
      (is (= hoidon-lajit-ilman-arvonvahennysta
            (sanktio-domain/urakan-sanktiolajit alueurakka 2025 false))
        "Alueurakka, validoinnit pois -> ei arvonvähennyssanktiota"))


    (is (= [:muistutus :A :B :C :arvonvahennyssanktio :pohjavesisuolan_ylitys :talvisuolan_ylitys :tenttikeskiarvo-sanktio
            :testikeskiarvo-sanktio :vaihtosanktio]
           alueurakan-lajit)
      "Hoidon sanktiolajit alueurakoille")
    (is (= [:muistutus :A :B :C :arvonvahennyssanktio :pohjavesisuolan_ylitys :talvisuolan_ylitys :tenttikeskiarvo-sanktio
            :testikeskiarvo-sanktio :vaihtosanktio]
           mhu-lajit)
      "Hoidon sanktiolajit MH-urakoille")
    (is (= [:yllapidon_sakko :yllapidon_muistutus]
           paallystyksen-lajit paikkauksen-lajit tiemerkinnan-lajit valaistuksen-lajit)
      "Ylläpidon sanktiolajit")))


(deftest laatupoikkeaman-mahdolliset-sanktiolajit
  (let [hoidon-lajit [:muistutus :A :B :C :arvonvahennyssanktio]
        yllapidon-lajit [:yllapidon_sakko :yllapidon_muistutus]
        alkupvm (pvm/hoitokauden-alkupvm 2019)]

    (is (= [:muistutus :A :B :C :arvonvahennyssanktio]
           alueurakan-lajit mhu-lajit)
      "Hoidon sanktiolajit urakoille laatupoikkeamissa")
    (is (= [:yllapidon_sakko :yllapidon_muistutus]
           paallystyksen-lajit paikkauksen-lajit tiemerkinnan-lajit valaistuksen-lajit)
      "Ylläpidon sanktiolajit laatupoikkeamissa")

    ;; Laatupoikkeamissa hoidon urakat saavat aina arvonvähennyssanktion (validoinnista riippumatta).
    (testing "Hoidon urakat laatupoikkeamissa"
      (doseq [validoinnit? [true false]]
        (is (= hoidon-lajit
              (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :hoito :alkupvm alkupvm} 2025 validoinnit?)
              (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :teiden-hoito :alkupvm alkupvm} 2025 validoinnit?))
          (str "Hoidon sanktiolajit laatupoikkeamissa, validoinnit? " validoinnit?))))

    (testing "Ylläpidon urakat laatupoikkeamissa"
      (doseq [validoinnit? [true false]]
        (is (= yllapidon-lajit
              (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :paallystys :alkupvm alkupvm} 2025 validoinnit?)
              (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :paikkaus :alkupvm alkupvm} 2025 validoinnit?)
              (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :tiemerkinta :alkupvm alkupvm} 2025 validoinnit?)
              (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :valaistus :alkupvm alkupvm} 2025 validoinnit?))
          (str "Ylläpidon sanktiolajit laatupoikkeamissa, validoinnit? " validoinnit?))))))

(deftest sanktiolajien-tyyppien-urakkakohtaiset-poikkeudet
  (let [muistutus-tyyppikoodit-ennen-2021 (sanktio-domain/sanktiolaji->sanktiotyyppi-koodi :muistutus (pvm/hoitokauden-alkupvm 2020))
        muistutus-tyyppikoodit-2021-tai-jalkeen (sanktio-domain/sanktiolaji->sanktiotyyppi-koodi :muistutus (pvm/hoitokauden-alkupvm 2021))
        A-tyyppikoodit-ennen-2021 (sanktio-domain/sanktiolaji->sanktiotyyppi-koodi :A (pvm/hoitokauden-alkupvm 2020))
        A-tyyppikoodit-2021-tai-jalkeen (sanktio-domain/sanktiolaji->sanktiotyyppi-koodi :A (pvm/hoitokauden-alkupvm 2021))
        B-tyyppikoodit-ennen-2021 (sanktio-domain/sanktiolaji->sanktiotyyppi-koodi :B (pvm/hoitokauden-alkupvm 2020))
        B-tyyppikoodit-2021-tai-jalkeen (sanktio-domain/sanktiolaji->sanktiotyyppi-koodi :B (pvm/hoitokauden-alkupvm 2021))
        lupaussanktio (sanktio-domain/sanktiolaji->sanktiotyyppi-koodi :lupaussanktio (pvm/hoitokauden-alkupvm 2020))]

    (is (= [13 14 15 16 10] muistutus-tyyppikoodit-ennen-2021)
      "Muistutus sanktiotyypit urakoissa ennen 2020")
    (is (= [13 14 15 16] A-tyyppikoodit-ennen-2021 B-tyyppikoodit-ennen-2021)
      "A ja B lajien sanktiotyypit urakoissa ennen 2020")

    (is (= [13 14 17 10] muistutus-tyyppikoodit-2021-tai-jalkeen)
      "Muistutus sanktiotyypit urakoissa 2020 tai sen jälkeen")
    (is (= [13 14 17] A-tyyppikoodit-2021-tai-jalkeen B-tyyppikoodit-2021-tai-jalkeen)
      "A ja B lajien sanktiotyypit urakoissa 2020 tai sen jälkeen")

    (is (= [0] lupaussanktio)
      "Lupaussanktio")))

(deftest sanktio-konfiguraation-adapteri-palauttaa-lajit-ja-tyypit
  (let [sanktio-konfiguraatio {:sanktio-lajit [{:laji :muistutus
                                                :nimi "Muistutus"
                                                :jarjestys 1
                                                :sanktiotyypit [{:id 10 :koodi 13 :nimi "Tyyppi A"}
                                                                {:id 11 :koodi 14 :nimi "Tyyppi B"}]}
                                               {:laji :A
                                                :nimi "Sakko"
                                                :jarjestys 2
                                                :sanktiotyypit [{:id 12 :koodi 17 :nimi "Tyyppi C"}]}]}]
    (testing "Lajit tulevat resolverin jarjestyksessa"
      (is (= [:muistutus :A]
             (sanktio-domain/sanktio-konfiguraation-lajit sanktio-konfiguraatio))))

    (testing "Lajin nimi luetaan resolverin profiilidatasta"
      (is (= "Sakko"
             (sanktio-domain/sanktio-konfiguraation-lajin-nimi sanktio-konfiguraatio :A))))

    (testing "Sanktiotyypit tulevat suoraan resolverin lajirivilta"
      (is (= [{:id 10 :koodi 13 :nimi "Tyyppi A"}
              {:id 11 :koodi 14 :nimi "Tyyppi B"}]
             (sanktio-domain/sanktio-konfiguraation-sanktiotyypit sanktio-konfiguraatio :muistutus))))

    (testing "Tuntematon laji ei palauta tyyppeja"
      (is (= []
             (sanktio-domain/sanktio-konfiguraation-sanktiotyypit sanktio-konfiguraatio :tuntematon))))))
