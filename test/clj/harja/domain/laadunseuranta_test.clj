(ns harja.domain.laadunseuranta_test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [harja.testi :refer :all]
            [slingshot.test]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.laadunseuranta.sanktion-laskenta :as sanktion-laskenta]
            [harja.domain.laadunseuranta.sanktiotyyppi :as sanktiotyyppi-domain]
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

(deftest sanktiotyypin-nimi-toistaa-lajin-nimen-kun-tyyppia-ei-ole
  (testing "Sanktiotyypin koodi 0 käyttää sanktiolajin nimeä"
    (is (= "Vastuuhenkilön tenttipistemäärän alentuminen"
           (sanktiotyyppi-domain/sanktiotyypin-nimi
             "Vastuuhenkilön tenttipistemäärän alentuminen"
             {:koodi 0
              :nimi "Ei tarvita sanktiotyyppiä"}))))
  (testing "Erillinen sanktiotyyppi näytetään omalla nimellään"
    (is (= "Talvihoito"
           (sanktiotyyppi-domain/sanktiotyypin-nimi
             "Muistutus"
             {:koodi 2
              :nimi "Talvihoito"})))))

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

(deftest sanktiotyypin-kiintea-automaattinen-summa-erottaa-kaavan
  (let [kiintea {:summamaaritykset [{:maaritystapa :automaattinen
                                     :summa-euroina 4000M}]}
        kaava {:summamaaritykset [{:maaritystapa :automaattinen
                                   :summa-euroina 2000M
                                   :ohjeteksti "alkavalta viikolta"}]}
        manuaalinen {:summamaaritykset [{:maaritystapa :manuaalinen
                                         :ohjeteksti "Kirjaa summa"}]}]
    (is (true? (sanktio-domain/sanktiotyypilla-kiintea-automaattinen-summamaaritys? kiintea)))
    (is (= 4000M (sanktio-domain/sanktiotyypin-kiintea-automaattinen-summa kiintea)))
    (is (false? (sanktio-domain/sanktiotyypilla-kiintea-automaattinen-summamaaritys? kaava)))
    (is (nil? (sanktio-domain/sanktiotyypin-kiintea-automaattinen-summa kaava)))
    (is (false? (sanktio-domain/sanktiotyypilla-kiintea-automaattinen-summamaaritys? manuaalinen)))))

(deftest liikennevahinkobonus-ja-alihankintabonus-palauttavat-kanoniset-nimet
  (is (= "Bonus alihankintasopimusten maksuehdoista"
         (sanktio-domain/sanktiolaji->teksti :alihankintabonus)))
  (is (= "Bonus alihankintasopimusten maksuehdoista"
         (sanktio-domain/bonuslaji->teksti :alihankintabonus)))
  (is (= "Bonus liikennevahinkojen aiheuttajien selvittämisestä"
         (sanktio-domain/sanktiolaji->teksti :liikennevahinkojen_aiheuttajien_selvitysbonus)))
  (is (= "Bonus liikennevahinkojen aiheuttajien selvittämisestä"
         (sanktio-domain/bonuslaji->teksti :liikennevahinkojen_aiheuttajien_selvitysbonus)))
  (is (= :bonukset
         (sanktio-domain/rivin-tyyppi
           {:laji :liikennevahinkojen_aiheuttajien_selvitysbonus
            :bonus? true}))))

(def ^:private tiekm-maaritys
  {:maaritystapa :laskettu
   :laskentaversio 1
   :laskentatapa :tiekm-yksikkohinta
   :laskentaparametrit {:syoteavain "tiekm"
                        :yksikko "tiekm"
                        :desimaalit 2
                        :yksikkohinta 200.0}
   :ohjeteksti nil
   :jarjestys 1})

(def ^:private prosenttiosuus-maaritys
  {:maaritystapa :laskettu
   :laskentaversio 1
   :laskentatapa :prosenttiosuus-syotteesta
   :laskentaparametrit {:syoteavain "laskutuskelvottomana_laskutettu_osuus"
                        :yksikko "€"
                        :desimaalit 2
                        :prosentti 20}
   :ohjeteksti nil
   :jarjestys 1})

(deftest laskentaversio-kuuluu-snapshotin-vastaavuuteen
  (let [maaritys (assoc tiekm-maaritys :laskentaversio 2)
        snapshot {:laskentaversio 1
                  :laskentatapa "tiekm_yksikkohinta"
                  :laskentaparametrit (:laskentaparametrit maaritys)}]
    (is (false? (sanktion-laskenta/snapshot-vastaa-maaritysta? snapshot maaritys)))
    (is (false? (sanktion-laskenta/snapshot-vastaa-maaritysta?
                  (assoc snapshot :laskentaversio nil)
                  (assoc maaritys :laskentaversio nil)))
      "Puuttuva laskentaversio ei saa hyväksyä snapshotia yhteensopivaksi")
    (doseq [virheellinen-versio [0 -1 "1"]]
      (is (false? (sanktion-laskenta/snapshot-vastaa-maaritysta?
                    (assoc snapshot :laskentaversio virheellinen-versio)
                    (assoc maaritys :laskentaversio virheellinen-versio)))
        (str "Virheellinen laskentaversio " (pr-str virheellinen-versio)
          " ei saa hyväksyä snapshotia yhteensopivaksi")))))

(deftest laskettu-summamaaritys-luetaan-vain-rakenteisesta-maarityksesta
  (let [laskettu-tyyppi {:summamaaritykset [tiekm-maaritys]}
        ohjetekstillinen-manuaalinen {:summamaaritykset [{:maaritystapa :manuaalinen
                                                          :ohjeteksti "200,00 € / tiekm."}]}]
    (is (= tiekm-maaritys (sanktion-laskenta/laskettu-summamaaritys laskettu-tyyppi)))
    (is (nil? (sanktion-laskenta/laskettu-summamaaritys ohjetekstillinen-manuaalinen))
      "Ohjetekstiä ei saa tulkita laskentakaavana")
    (is (nil? (sanktion-laskenta/laskettu-summamaaritys nil)))
    (is (false? (sanktio-domain/sanktiotyypilla-kiintea-automaattinen-summamaaritys? laskettu-tyyppi))
      "Laskettu määritys ei ole kiinteä automaattinen summa")))

(deftest laske-sanktion-summa-tiekm-kertaa-yksikkohinta
  (let [tulos (sanktion-laskenta/laske-sanktion-summa tiekm-maaritys 12.5)]
    (is (= 2500.00M (:summa tulos)))
    (is (= 2 (.scale ^BigDecimal (:summa tulos))) "Tulos pyöristetään kahteen desimaaliin")
    (is (= {:syoteavain "tiekm"
            :syote 12.5M
            :yksikko "tiekm"
            :laskentaversio 1
            :laskentatapa "tiekm_yksikkohinta"
            :laskentaparametrit (:laskentaparametrit tiekm-maaritys)}
           (:laskennan-syote tulos))
      "Snapshot sisältää syötteen, laskentatavan ja profiiliparametrit")))

(deftest laske-sanktion-summa-prosenttiosuus-syotteesta
  (testing "Kaksi desimaalia"
    (is (= 246.91M (:summa (sanktion-laskenta/laske-sanktion-summa prosenttiosuus-maaritys 1234.56)))))
  (testing "HALF_UP pyöristää tasatilanteen ylöspäin"
    (let [maaritys (assoc-in prosenttiosuus-maaritys [:laskentaparametrit :prosentti] 15)]
      (is (= 0.02M (:summa (sanktion-laskenta/laske-sanktion-summa maaritys 0.10)))
        "0,10 × 15 % = 0,015 -> 0,02")))
  (testing "Kokonaisluku ja BigDecimal kelpaavat"
    (is (= 200.00M (:summa (sanktion-laskenta/laske-sanktion-summa prosenttiosuus-maaritys 1000))))
    (is (= 200.00M (:summa (sanktion-laskenta/laske-sanktion-summa prosenttiosuus-maaritys 1000.00M))))))

(deftest snapshot-vastaa-maaritysta-ja-syotetta
  (let [laskennan-syote {:syoteavain "tiekm"
                         :syote 12.5M
                         :yksikko "tiekm"
                         :laskentaversio 1
                         :laskentatapa "tiekm_yksikkohinta"
                         :laskentaparametrit (:laskentaparametrit tiekm-maaritys)}]
    (is (true? (sanktion-laskenta/syote-vastaa-snapshotia? laskennan-syote 12.50M)))
    (is (true? (sanktion-laskenta/snapshot-vastaa-maaritysta? laskennan-syote tiekm-maaritys)))
    (is (false? (sanktion-laskenta/snapshot-vastaa-maaritysta?
                  laskennan-syote
                  (assoc-in tiekm-maaritys [:laskentaparametrit :yksikkohinta] 300.0))))))

(deftest laske-sanktion-summa-hylkaa-virheellisen-syotteen-kentan-nimella
  (let [hylkaa (fn [maaritys syote osat]
                 (let [virhe (try (sanktion-laskenta/laske-sanktion-summa maaritys syote)
                               nil
                               (catch IllegalArgumentException e (.getMessage e)))]
                   (is (some? virhe) (str "Syöte " (pr-str syote) " pitää hylätä"))
                   (doseq [osa osat]
                     (is (str/includes? (str virhe) osa) (str "Virheessä pitää olla " osa ": " virhe)))))]
    (testing "Puuttuva syöte"
      (hylkaa tiekm-maaritys nil ["tiekm" "skalaarinen numero" "nil"]))
    (testing "Merkkijono ei ole numero"
      (hylkaa tiekm-maaritys "12" ["tiekm" "skalaarinen numero" "java.lang.String"]))
    (testing "Kokoelma ei ole skalaarinen"
      (hylkaa tiekm-maaritys {:arvo 12} ["tiekm" "skalaarinen numero" "PersistentArrayMap"])
      (hylkaa tiekm-maaritys [12] ["tiekm" "skalaarinen numero" "PersistentVector"]))
    (testing "Suhdeluku ei ole skalaarinen desimaaliluku"
      (hylkaa tiekm-maaritys 1/3 ["tiekm" "skalaarinen numero" "Ratio"]))
    (testing "Ei-äärelliset arvot"
      (hylkaa tiekm-maaritys Double/NaN ["tiekm" "äärellinen"])
      (hylkaa tiekm-maaritys Double/POSITIVE_INFINITY ["tiekm" "äärellinen"]))
    (testing "Nolla ja negatiivinen"
      (hylkaa tiekm-maaritys 0 ["tiekm" "nollaa suurempi" "java.lang.Long"])
      (hylkaa tiekm-maaritys -1.5 ["tiekm" "nollaa suurempi" "java.lang.Double"]))
    (testing "Liian monta desimaalia"
      (hylkaa tiekm-maaritys 1.234 ["tiekm" "enintään 2 desimaalia"])
      (hylkaa prosenttiosuus-maaritys 10.001M ["laskutuskelvottomana_laskutettu_osuus" "enintään 2 desimaalia"]))
    (testing "Profiilin minimi- ja maksimiraja"
      (let [rajattu (update tiekm-maaritys :laskentaparametrit assoc :minimi 0.5 :maksimi 100)]
        (hylkaa rajattu 0.25 ["tiekm" "vähintään 0.5"])
        (hylkaa rajattu 100.01 ["tiekm" "enintään 100"])
        (is (= 100.00M (:summa (sanktion-laskenta/laske-sanktion-summa
                                 (update tiekm-maaritys :laskentaparametrit assoc :yksikkohinta 1.0 :maksimi 100)
                                 100))))))
    (testing "Profiilin desimaalitarkkuus rajaa syötettä"
      (hylkaa (update tiekm-maaritys :laskentaparametrit assoc :desimaalit 0) 1.5
        ["tiekm" "enintään 0 desimaalia"]))))

(deftest laske-sanktion-summa-hylkaa-virheellisen-laskentamaarityksen
  (testing "Tuntematon laskentatapa"
    (is (thrown-with-msg? IllegalArgumentException #"laskentatapa.*:kaava-ohjetekstista"
          (sanktion-laskenta/laske-sanktion-summa (assoc tiekm-maaritys :laskentatapa :kaava-ohjetekstista) 1))))
  (testing "Puuttuva yksikköhinta"
    (is (thrown-with-msg? IllegalArgumentException #"laskentaparametrit.*yksikkohinta"
          (sanktion-laskenta/laske-sanktion-summa
            (update tiekm-maaritys :laskentaparametrit dissoc :yksikkohinta) 1))))
  (testing "Palvelimen tukemaa tarkkuutta suurempi profiilitarkkuus"
    (is (thrown-with-msg? IllegalArgumentException #"laskentaparametrit.*desimaalit"
          (sanktion-laskenta/laske-sanktion-summa
            (update tiekm-maaritys :laskentaparametrit assoc :desimaalit 3) 1)))))

(deftest syotteen-otsikko-muodostuu-profiilin-laskentaparametreista
  (is (= "Tiekilometrit (tiekm)"
         (sanktion-laskenta/syotteen-otsikko (:laskentaparametrit tiekm-maaritys))))
  (is (= "Laskutuskelvottomana laskutettu osuus (€)"
         (sanktion-laskenta/syotteen-otsikko (:laskentaparametrit prosenttiosuus-maaritys)))))

(deftest syotteen-virhe-esitarkistaa-syotteen-profiilin-saannoilla
  (let [parametrit (:laskentaparametrit tiekm-maaritys)
        rajattu (assoc parametrit :minimi 0.5 :maksimi 100)]
    (testing "Kelvolliset syötteet"
      (doseq [syote [12.5 20 0.01 1.15 100.00]]
        (is (nil? (sanktion-laskenta/syotteen-virhe parametrit syote)) (str "Syöte " syote))))
    (testing "Puuttuva ja ei-numeerinen syöte"
      (is (some? (sanktion-laskenta/syotteen-virhe parametrit nil)))
      (is (some? (sanktion-laskenta/syotteen-virhe parametrit "12"))))
    (testing "Nolla, negatiivinen ja ei-äärellinen"
      (is (some? (sanktion-laskenta/syotteen-virhe parametrit 0)))
      (is (some? (sanktion-laskenta/syotteen-virhe parametrit -1.5)))
      (is (some? (sanktion-laskenta/syotteen-virhe parametrit Double/NaN)))
      (is (some? (sanktion-laskenta/syotteen-virhe parametrit Double/POSITIVE_INFINITY))))
    (testing "Liian monta desimaalia"
      (is (some? (sanktion-laskenta/syotteen-virhe parametrit 1.234)))
      (is (some? (sanktion-laskenta/syotteen-virhe (assoc parametrit :desimaalit 0) 1.5))))
    (testing "Profiilin minimi ja maksimi"
      (is (some? (sanktion-laskenta/syotteen-virhe rajattu 0.25)))
      (is (some? (sanktion-laskenta/syotteen-virhe rajattu 100.01)))
      (is (nil? (sanktion-laskenta/syotteen-virhe rajattu 100))))))

(deftest laskettava-syote-palautuu-lomakkeelle-palvelimen-snapshotista
  (let [luettu-rivi {:id 1 :summa -2500.0 :laskennan-syote {:syote 12.5 :syoteavain "tiekm"}}]
    (is (= 12.5 (sanktion-laskenta/laskettava-syote luettu-rivi)))
    (is (= 20 (sanktion-laskenta/laskettava-syote (assoc luettu-rivi :laskettava-syote 20)))
      "Käyttäjän muokkaus voittaa snapshotin")
    (is (nil? (sanktion-laskenta/laskettava-syote (assoc luettu-rivi :laskettava-syote nil)))
      "Tyhjennetty kenttä ei palaa snapshotin arvoon")
    (is (nil? (sanktion-laskenta/laskettava-syote {})))))

(deftest laskettu-tulos-on-esikatselu-muokatusta-syotteesta-tai-palvelimen-summa
  (let [luettu-rivi {:id 1 :summa -2500.0 :laskennan-syote {:syote 12.5}}]
    (testing "Muokkaamaton luettu rivi näyttää palvelimen tallentaman summan"
      (is (== 2500 (sanktion-laskenta/laskettu-tulos tiekm-maaritys luettu-rivi))))
    (testing "Muokattu syöte esikatsellaan profiilin laskentatavalla"
      (is (== 4000 (sanktion-laskenta/laskettu-tulos tiekm-maaritys (assoc luettu-rivi :laskettava-syote 20))))
      (is (== 246.91 (sanktion-laskenta/laskettu-tulos prosenttiosuus-maaritys {:laskettava-syote 1234.56}))))
    (testing "Virheellisestä tai puuttuvasta syötteestä ei näytetä tulosta"
      (is (nil? (sanktion-laskenta/laskettu-tulos tiekm-maaritys (assoc luettu-rivi :laskettava-syote 1.234))))
      (is (nil? (sanktion-laskenta/laskettu-tulos tiekm-maaritys (assoc luettu-rivi :laskettava-syote nil)))))
    (testing "Uusi rivi ilman syötettä ei näytä tulosta"
      (is (nil? (sanktion-laskenta/laskettu-tulos tiekm-maaritys {:summa 500}))
        "Vanha käsin syötetty summa ei ole laskettu tulos"))))

(deftest manuaalinen-laskettavan-sisartyyppi-tunnistetaan-profiilin-rakenteesta
  (let [laskettava {:id 22 :summamaaritykset [tiekm-maaritys]}
        muu-manuaalinen {:id 23 :summamaaritykset []}
        c-manuaalinen-ohjetekstilla {:id 30 :summamaaritykset [{:maaritystapa :manuaalinen
                                                                :summa-euroina 200M
                                                                :ohjeteksti "200,00 € / tiekm."}]}
        lajin-tyypit [laskettava muu-manuaalinen]]
    (is (true? (sanktion-laskenta/manuaalinen-laskettavan-sisartyyppi? muu-manuaalinen lajin-tyypit)))
    (is (false? (sanktion-laskenta/manuaalinen-laskettavan-sisartyyppi? laskettava lajin-tyypit))
      "Laskettava tyyppi ei ole manuaalinen")
    (is (false? (sanktion-laskenta/manuaalinen-laskettavan-sisartyyppi? muu-manuaalinen [muu-manuaalinen]))
      "Ilman laskettavaa sisartyyppiä käytös ei muutu")
    (is (false? (sanktion-laskenta/manuaalinen-laskettavan-sisartyyppi? c-manuaalinen-ohjetekstilla
                  [laskettava c-manuaalinen-ohjetekstilla]))
      "Profiilin manuaalinen määritys säilyttää nykyisen käytöksen")
    (is (false? (sanktion-laskenta/manuaalinen-laskettavan-sisartyyppi? nil lajin-tyypit)))))
