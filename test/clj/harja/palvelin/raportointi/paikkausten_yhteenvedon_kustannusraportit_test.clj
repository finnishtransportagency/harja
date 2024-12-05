(ns harja.palvelin.raportointi.paikkausten-yhteenvedon-kustannusraportit-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi.raportit.paikkausten-yhteenveto :as paikkausten-yhteenveto]
            [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta-palvelu]
            [harja.palvelin.palvelut.yllapitokohteet.kustannukset-palvelu :as kustannukset-palvelu]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :pdf-vienti (component/using
                        (pdf-vienti/luo-pdf-vienti)
                        [:http-palvelin])
          :raportointi (component/using
                         (raportointi/luo-raportointi)
                         [:db :pdf-vienti])
          :raportit (component/using
                      (raportit/->Raportit)
                      [:http-palvelin :db :raportointi :pdf-vienti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest paikkausten-yhteenvedon-kustannusraportti-PPU-toimii
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        parametrit {:vuosi 2024 :urakkatyyppi :paallystys, :kasittelija nil, :urakka-id 5}

        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :ppu-paikkausten-yhteenveto
                   :konteksti "urakka"
                   :urakka-id urakka-id
                   :parametrit parametrit})
        raportin-nimi (-> vastaus second :nimi)
        teksti1 (->  (nth vastaus 2) second)
        odotettu-teksti1 "Utajärven päällystysurakka 2021-2024"
        teksti2 (->  (nth vastaus 3) second)
        odotettu-teksti2 "01.01.2024 - 31.12.2024"
        yhteenvetotaulukko (nth vastaus 4)
        kustannukset-pkluokittain (nth vastaus 5)
        kustannukset-tyomenetelmittain (nth vastaus 6)
        maarat-tyomenetelmittain (nth vastaus 7)
        muut-kustannukset (nth vastaus 9) ;; Hypätään indeksissä yksi eteen, koska PPU raportilla ei ole reikäpaikkausosiota
        ]

    (is (= raportin-nimi "Paikkausten yhteenveto"))
    (is (= (:otsikko (second yhteenvetotaulukko)) "Yhteenveto"))
    ;; PK-luokittain taulukko
    (is (= (:otsikko (second kustannukset-pkluokittain)) "Toteutuneet paikkauskustannukset PK-luokittain"))
    (is (= (count (nth kustannukset-pkluokittain 2)) 4)) ;; 4 Otsikkoa
    (is (= (count (:rivi (first (nth kustannukset-pkluokittain 3)))) 4)) ;; 4 osiota tulosrivillä

    (is (= (:otsikko (second kustannukset-tyomenetelmittain)) "Kustannukset työmenetelmittäin"))
    (is (= (count (nth kustannukset-tyomenetelmittain 2)) 3)) ;; 3 Otsikkoa
    (is (= (count (:rivi (first (nth kustannukset-pkluokittain 3)))) 4)) ;; 4 osiota tulosrivillä

    (is (= (:otsikko (second maarat-tyomenetelmittain)) "Määrät työmenetelmittäin"))
    (is (= (count (nth maarat-tyomenetelmittain 2)) 4)) ;; 4 Otsikkoa

    (is (= (:otsikko (second muut-kustannukset)) "Muut kustannukset"))
    (is (= (count (nth muut-kustannukset 2)) 2)) ;; 2 Otsikkoa
    (is (= (count (:rivi (first (nth muut-kustannukset 3)))) 2)) ;; 2 osiota tulosrivillä
    ))


(deftest paikkausten-yhteenvedon-kustannusraportti-MPU-toimii
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        parametrit {:vuosi 2024 :urakkatyyppi :paallystys, :kasittelija nil, :urakka-id 5}

        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :mpu-paikkausten-yhteenveto
                   :konteksti "urakka"
                   :urakka-id urakka-id
                   :parametrit parametrit})

        raportin-nimi (-> vastaus second :nimi)
        yhteenvetotaulukko (nth vastaus 4)
        kustannukset-pkluokittain (nth vastaus 5)
        kustannukset-tyomenetelmittain (nth vastaus 6)
        maarat-tyomenetelmittain (nth vastaus 7)
        reikapaikkaustaulukko (nth vastaus 8)
        muut-kustannukset (nth vastaus 9)]


    (is (= raportin-nimi "Paikkausten yhteenveto"))
    (is (= (:otsikko (second yhteenvetotaulukko)) "Yhteenveto"))
    ;; PK-luokittain taulukko
    (is (= (:otsikko (second kustannukset-pkluokittain)) "Toteutuneet paikkauskustannukset PK-luokittain"))
    (is (= (count (nth kustannukset-pkluokittain 2)) 4)) ;; 4 Otsikkoa
    (is (= (count (:rivi (first (nth kustannukset-pkluokittain 3)))) 4)) ;; 4 osiota tulosrivillä

    (is (= (:otsikko (second kustannukset-tyomenetelmittain)) "Kustannukset työmenetelmittäin"))
    (is (= (count (nth kustannukset-tyomenetelmittain 2)) 3)) ;; 3 Otsikkoa
    (is (= (count (:rivi (first (nth kustannukset-pkluokittain 3)))) 4)) ;; 4 osiota tulosrivillä

    (is (= (:otsikko (second maarat-tyomenetelmittain)) "Määrät työmenetelmittäin"))
    (is (= (count (nth maarat-tyomenetelmittain 2)) 4)) ;; 4 Otsikkoa

    (is (= (:otsikko (second reikapaikkaustaulukko)) "Reikäpaikkausten kustannukset"))
    (is (= (count (nth reikapaikkaustaulukko 2)) 4)) ;; 4 Otsikkoa

    (is (= (:otsikko (second muut-kustannukset)) "Muut kustannukset"))
    (is (= (count (nth muut-kustannukset 2)) 2)) ;; 2 Otsikkoa
    (is (= (count (:rivi (first (nth muut-kustannukset 3)))) 2)) ;; 2 osiota tulosrivillä
    ))

(deftest paikkausten-yhteenvedon-muut-kustannukset-toimii
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        bonus-summa 99M
        sanktio-summa 222M
        muukustannus-summa 333M
        hoitokauden-alkuvuosi 2024
        alkupvm (pvm/->pvm "01.01.2024")
        loppupvm (pvm/->pvm "31.12.2024")

        ;; Haetaan bonukset, sanktiot ja käsin lisätyt kustannukset
        muut-kustannukset (paikkausten-yhteenveto/koosta-muut-kustannukset (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitokauden-alkuvuosi alkupvm loppupvm)
        muut-kustannukset-tyhja-vastaus [{:nimi "Bonukset", :toteutunut-hinta 0M} {:nimi "Sanktiot", :toteutunut-hinta 0M} {:nimi "Yhteensä", :toteutunut-hinta 0M, :yhteenveto true}]
        _ (is (= muut-kustannukset-tyhja-vastaus muut-kustannukset))

        ;; Tallennetaan urakalle bonus (Ylläpidon bonukset menevät sanktio-tauluun ja on aika monimutkainen juttu, niin
        ;; käytetään olemassa olevaa koodia bonuksen tekemiseen)
        sanktio {:laji :yllapidon_bonus :suorasanktio true :summa bonus-summa :perintapvm (pvm/->pvm "11.11.2024")}
        paatos {:kasittelyaika (pvm/nyt) :paatos "sanktio" :perustelu "joku perustelu" :kasittelytapa :tyomaakokous}
        laatupoikkeama {:tekijanimi "tekija" :urakka urakka-id :yllapitokohde nil :aika (pvm/nyt) :paatos paatos}

        bonus-vastaus (laadunseuranta-palvelu/tallenna-suorasanktio (:db jarjestelma) +kayttaja-jvh+ sanktio laatupoikkeama urakka-id nil)

        ;; Tallennetaan urakalle sanktio
        sanktio {:laji :yllapidon_sakko :suorasanktio true :summa sanktio-summa :perintapvm (pvm/->pvm "11.11.2024")}
        paatos {:kasittelyaika (pvm/nyt) :paatos "sanktio" :perustelu "joku perustelu" :kasittelytapa :tyomaakokous}
        laatupoikkeama {:tekijanimi "tekija" :urakka urakka-id :yllapitokohde nil :aika (pvm/nyt) :paatos paatos}

        sanktio-vastaus (laadunseuranta-palvelu/tallenna-suorasanktio (:db jarjestelma) +kayttaja-jvh+ sanktio laatupoikkeama urakka-id nil)
        ;; Tallennetaan muu kustannus
        muukustannus-vastaus (kustannukset-palvelu/tallenna-mpu-kustannus (:db jarjestelma ) +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                          :selite "Haravoitiin lehdet asfaltilta"
                                                                                          :luoja nil
                                                                                          :kustannustyyppi "Muut kustannukset"
                                                                                          :vuosi hoitokauden-alkuvuosi
                                                                                          :summa muukustannus-summa})
        muut-kustannukset-uudestaan (paikkausten-yhteenveto/koosta-muut-kustannukset (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitokauden-alkuvuosi alkupvm loppupvm)]
    (is (= bonus-summa (->> muut-kustannukset-uudestaan
                         (filter #(= (:nimi %) "Bonukset"))
                         first
                         :toteutunut-hinta)))
    (is (= (* -1 sanktio-summa) (->> muut-kustannukset-uudestaan
                                    (filter #(= (:nimi %) "Sanktiot"))
                                    first
                                    :toteutunut-hinta)))
    (is (= muukustannus-summa (->> muut-kustannukset-uudestaan
                                  (filter #(= (:nimi %) "Muut kustannukset"))
                                  first
                                  :toteutunut-hinta)))
    (is (= (+ bonus-summa (* -1 sanktio-summa) muukustannus-summa) (->> muut-kustannukset-uudestaan
                                                                       (filter #(= (:nimi %) "Yhteensä"))
                                                                       first
                                                                       :toteutunut-hinta)))
    ))
