(ns harja.domain.laadunseuranta_test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [harja.testi :refer :all]
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

    (testing "Hoidon urakat, kun arvonvähennys on vielä vanhassa sanktiolistassa"
      (is (= hoidon-lajit-arvonvahennyksella
            (sanktio-domain/urakan-sanktiolajit mhu24-urakka 2025))
        "MHU24-urakka ennen hoitovuotta 2026 -> arvonvähennyssanktio mukana")
      (is (= hoidon-lajit-arvonvahennyksella
            (sanktio-domain/urakan-sanktiolajit alueurakka 2025))
        "Alueurakka ennen hoitovuotta 2026 -> arvonvähennyssanktio mukana"))

    (testing "Hoidon urakat, kun arvonvähennys on siirtynyt omalle lomakkeelle"
      (is (= hoidon-lajit-ilman-arvonvahennysta
            (sanktio-domain/urakan-sanktiolajit mhu24-urakka 2026))
        "MHU24-urakka hoitovuodesta 2026 alkaen -> ei arvonvähennyssanktiota (uusi lomake käytössä)")
      (is (= hoidon-lajit-ilman-arvonvahennysta
            (sanktio-domain/urakan-sanktiolajit mhu24-urakka 2027))
        "MHU24-urakka hoitovuonna 2027 -> ei arvonvähennyssanktiota")
      (is (= hoidon-lajit-ilman-arvonvahennysta
            (sanktio-domain/urakan-sanktiolajit alueurakka 2026))
        "Alueurakka hoitovuodesta 2026 alkaen -> ei arvonvähennyssanktiota")
      (is (= hoidon-lajit-ilman-arvonvahennysta
            (sanktio-domain/urakan-sanktiolajit mhu25-urakka 2025))
        "MHU25-urakka -> ei arvonvähennyssanktiota vanhassa listassa hoitovuodesta riippumatta")
      (is (= hoidon-lajit-ilman-arvonvahennysta
            (sanktio-domain/urakan-sanktiolajit mhu25-urakka 2027))
        "MHU25-urakka hoitovuonna 2027 -> ei arvonvähennyssanktiota"))

    (testing "Ylläpidon urakat saavat aina ylläpidon lajit, hoitovuodesta riippumatta"
      (is (= yllapidon-lajit
            (sanktio-domain/urakan-sanktiolajit {:tyyppi :paallystys} 2025)
            (sanktio-domain/urakan-sanktiolajit {:tyyppi :paallystys} 2026)
            (sanktio-domain/urakan-sanktiolajit {:tyyppi :paallystys} 2027)
            (sanktio-domain/urakan-sanktiolajit {:tyyppi :paikkaus} 2025)
            (sanktio-domain/urakan-sanktiolajit {:tyyppi :paikkaus} 2026)
            (sanktio-domain/urakan-sanktiolajit {:tyyppi :paikkaus} 2027)
            (sanktio-domain/urakan-sanktiolajit {:tyyppi :tiemerkinta} 2025)
            (sanktio-domain/urakan-sanktiolajit {:tyyppi :tiemerkinta} 2026)
            (sanktio-domain/urakan-sanktiolajit {:tyyppi :tiemerkinta} 2027)
            (sanktio-domain/urakan-sanktiolajit {:tyyppi :valaistus} 2025)
            (sanktio-domain/urakan-sanktiolajit {:tyyppi :valaistus} 2026)
            (sanktio-domain/urakan-sanktiolajit {:tyyppi :valaistus} 2027))
        "Ylläpidon sanktiolajit"))

    (testing "Tuntematon urakkatyyppi"
      (is (= []
            (sanktio-domain/urakan-sanktiolajit {:tyyppi :vesivayla-hoito} 2025))
        "Muille urakkatyypeille ei tarjota sanktiolajeja"))))

(deftest laatupoikkeaman-mahdolliset-sanktiolajit
  (let [hoidon-lajit [:muistutus :A :B :C :arvonvahennyssanktio]
        yllapidon-lajit [:yllapidon_sakko :yllapidon_muistutus]
        alkupvm (pvm/hoitokauden-alkupvm 2019)
        alueurakan-lajit (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :hoito :alkupvm (pvm/hoitokauden-alkupvm 2019)})
        mhu-lajit (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :teiden-hoito :alkupvm (pvm/hoitokauden-alkupvm 2019)})
        paallystyksen-lajit (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :paallystys :alkupvm (pvm/hoitokauden-alkupvm 2019)})
        paikkauksen-lajit (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :paikkaus :alkupvm (pvm/hoitokauden-alkupvm 2019)})
        tiemerkinnan-lajit (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :tiemerkinta :alkupvm (pvm/hoitokauden-alkupvm 2019)})
        valaistuksen-lajit (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :valaistus :alkupvm (pvm/hoitokauden-alkupvm 2019)})]

    (is (= [:muistutus :A :B :C :arvonvahennyssanktio]
           alueurakan-lajit mhu-lajit)
      "Hoidon sanktiolajit urakoille laatupoikkeamissa")
    (is (= [:yllapidon_sakko :yllapidon_muistutus]
           paallystyksen-lajit paikkauksen-lajit tiemerkinnan-lajit valaistuksen-lajit)
      "Ylläpidon sanktiolajit laatupoikkeamissa")

    ;; Laatupoikkeamissa hoidon urakat saavat aina arvonvähennyssanktion (validoinnista riippumatta).
    (testing "Hoidon urakat laatupoikkeamissa"
      (is (= hoidon-lajit
            (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :hoito :alkupvm alkupvm})
            (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :teiden-hoito :alkupvm alkupvm}))
        (str "Hoidon sanktiolajit laatupoikkeamissa")))

    (testing "Ylläpidon urakat laatupoikkeamissa"
      (is (= yllapidon-lajit
            (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :paallystys :alkupvm alkupvm})
            (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :paikkaus :alkupvm alkupvm})
            (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :tiemerkinta :alkupvm alkupvm})
            (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :valaistus :alkupvm alkupvm}))
        (str "Ylläpidon sanktiolajit laatupoikkeamissa")))))

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
