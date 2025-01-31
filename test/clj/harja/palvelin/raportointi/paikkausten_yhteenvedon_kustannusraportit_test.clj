(ns harja.palvelin.raportointi.paikkausten-yhteenvedon-kustannusraportit-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [jeesql.core :refer [defqueries]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi.raportit.paikkausten-yhteenveto-mhu :as paikkausten-yhteenveto-mhu]
            [harja.palvelin.raportointi.raportit.paikkausten-yhteenveto :as paikkausten-yhteenveto]
            [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta-palvelu]
            [harja.palvelin.palvelut.yllapitokohteet.kustannukset-palvelu :as kustannukset-palvelu]
            [harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet :as paikkauskohteet-palvelu]
            [harja.kyselyt.paikkaus :as paikkaus-kyselyt]))

(defqueries "harja/palvelin/raportointi/raportit/paikkausten_yhteenveto.sql")

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
                      [:http-palvelin :db :raportointi :pdf-vienti])
          :paikkauskohteet (component/using
                             (paikkauskohteet-palvelu/->Paikkauskohteet)
                             [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; Helppereitä

(defn testien-yhteiset-tiedot [urakan-nimi vuosi]
  (let [urakka-id (hae-urakan-id-nimella urakan-nimi)]
    {:urakka-id urakka-id
     :vuosi vuosi
     :alkupvm (pvm/->pvm (str "01.01." vuosi))
     :loppupvm (pvm/->pvm (str "31.12." vuosi))}))

(defn lisaa-paikkauskohteet-excelista [urakka-id]
  (let [excel-vastaus (paikkauskohteet-palvelu/vastaanota-excel (:db jarjestelma) nil nil
                        {:params {"urakka-id" (str urakka-id)
                                  "file" {:tempfile (io/file "test/resurssit/excel/Paikkausehdotukset_valid.xlsx")}}
                         :kayttaja +kayttaja-jvh+})
        ;; Varmistetaan, että excelin vastaanotto onnistui
        _ (is (= "{\"message\":\"OK\"}" (get-in excel-vastaus [:body])))]))

(defn valmistele-paikkauskohde-valmiiksi [paikkauskohde urakka-id vuosi]
  (let [;; Tilataan yksi kohde ja lisätään sille toteumia
        _ (u (format "UPDATE paikkauskohde SET \"paikkauskohteen-tila\" = 'tilattu' WHERE id = %s" (:id paikkauskohde)))
        paikkaus {:tie 11746 :ajorata 1 :aosa 3 :aet 0 :losa 7
                  :let 1 :juoksumetri 456 :urakka-id urakka-id
                  :alkuaika (pvm/->pvm "01.06.2021") :loppuaika (pvm/->pvm (str "15.06." vuosi))
                  :tyomenetelma 11
                  :paikkauskohde-id (:id paikkauskohde)}
        _ (paikkaus-kyselyt/tallenna-kasinsyotetty-paikkaus (:db jarjestelma) +kayttaja-jvh+ paikkaus)

        ;; Merkitään paikkauskohde valmiiksi
        paikkauskohde {:id (:id paikkauskohde)
                       :tie 11746
                       :ajorata 1
                       :aosa 3
                       :aet 100
                       :losa 7
                       :let 1000
                       :tyomenetelma 11
                       :toteutunut-juoksumetri 456
                       :urakka-id urakka-id
                       :pot-paatos nil
                       :toteutunut-massamaara nil
                       :loppupvm (pvm/->pvm (str "15.07." vuosi))
                       :pot-valmistumispvm nil
                       :toteutunut-hinta 12000
                       :suunniteltu-maara 1000
                       :yksikko "jm"
                       :toteutunut-pinta-ala nil
                       :takuuaika 2
                       :nimi "Kilpilahden ratatiesilta"
                       :tilattupvm (pvm/->pvm (str "01.05." vuosi))
                       :lisatiedot "Happy day excel testi"
                       :pot-tyo-alki nil
                       :pot-tila nil
                       :toteutunut-kpl nil
                       :alkupvm (pvm/->pvm (str "01.06." vuosi))
                       :yllapitokohde-id nil
                       :toteumien-maara 1
                       :urakoitsija "Skanska Asfaltti Oy"
                       :toteumatyyppi "normaali"
                       :tiemerkintaa-tuhoutunut? nil
                       :pot? false
                       :ulkoinen-id (:ulkoinen-id paikkauskohde)
                       :paikkauskohteen-tila "valmis"
                       :valmistumispvm (pvm/->pvm (str "15.07." vuosi))
                       :tyyppi :paikkauskohteen-muokkaus
                       :massamenekki nil
                       :suunniteltu-hinta 10000
                       :tiemerkintapvm nil
                       :pot-id nil}
        _ (paikkauskohteet-palvelu/tallenna-paikkauskohde! (:db jarjestelma) nil nil +kayttaja-jvh+ paikkauskohde)]))

(deftest paikkausten-yhteenvedon-kustannusraportti-PPU-toimii
  (let [tiedot (testien-yhteiset-tiedot "Utajärven päällystysurakka" 2025)
        parametrit {:alkupvm (:alkupvm tiedot) :loppupvm (:loppupvm tiedot)
                    :urakkatyyppi :paallystys, :kasittelija nil, :urakka-id (:urakka-id tiedot)}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :ppu-paikkausten-yhteenveto
                   :konteksti "urakka"
                   :urakka-id (:urakka-id tiedot)
                   :parametrit parametrit})
        raportin-nimi (-> vastaus second :nimi)
        teksti1 (-> (nth vastaus 2) second)
        odotettu-teksti1 "Utajärven päällystysurakka 2021-2025"
        teksti2 (-> (nth vastaus 3) second)
        odotettu-teksti2 "01.01.2025 - 31.12.2025"
        yhteenvetotaulukko (nth vastaus 4)
        kustannukset-pkluokittain (nth vastaus 5)
        kustannukset-tyomenetelmittain (nth vastaus 6)
        maarat-tyomenetelmittain (nth vastaus 7)
        muut-kustannukset (nth vastaus 9) ;; Hypätään indeksissä yksi eteen, koska PPU raportilla ei ole reikäpaikkausosiota
        ]

    (is (= raportin-nimi "Paikkausten yhteenveto"))
    (is (= teksti1 odotettu-teksti1))
    (is (= teksti2 odotettu-teksti2))
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
  (let [tiedot (testien-yhteiset-tiedot "Muhoksen päällystysurakka" 2024)
        parametrit {:alkupvm (:alkupvm tiedot) :loppupvm (:loppupvm tiedot)
                    :urakkatyyppi :paallystys, :kasittelija nil, :urakka-id (:urakka-id tiedot)}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :mpu-paikkausten-yhteenveto
                   :konteksti "urakka"
                   :urakka-id (:urakka-id tiedot)
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
  (let [tiedot (testien-yhteiset-tiedot "Utajärven päällystysurakka" 2024)
        bonus-summa 99M
        sanktio-summa 222M
        muukustannus-summa 333M

        ;; Haetaan bonukset, sanktiot ja käsin lisätyt kustannukset
        muut-kustannukset (paikkausten-yhteenveto/koosta-muut-kustannukset
                            (:db jarjestelma) +kayttaja-jvh+ (:urakka-id tiedot) (:alkupvm tiedot) (:loppupvm tiedot))
        muut-kustannukset-tyhja-vastaus [{:nimi "Bonukset", :toteutunut-hinta 0M} {:nimi "Sanktiot", :toteutunut-hinta 0M} {:nimi "Yhteensä", :toteutunut-hinta 0M, :yhteenveto true}]
        _ (is (= muut-kustannukset-tyhja-vastaus muut-kustannukset))

        ;; Tallennetaan urakalle bonus (Ylläpidon bonukset menevät sanktio-tauluun ja on aika monimutkainen juttu, niin
        ;; käytetään olemassa olevaa koodia bonuksen tekemiseen)
        sanktio {:laji :yllapidon_bonus :suorasanktio true :summa bonus-summa :perintapvm (pvm/->pvm "11.11.2024")}
        paatos {:kasittelyaika (pvm/nyt) :paatos "sanktio" :perustelu "joku perustelu" :kasittelytapa :tyomaakokous}
        laatupoikkeama {:tekijanimi "tekija" :urakka (:urakka-id tiedot) :yllapitokohde nil :aika (pvm/nyt) :paatos paatos}

        _ (laadunseuranta-palvelu/tallenna-suorasanktio
            (:db jarjestelma) +kayttaja-jvh+ sanktio laatupoikkeama (:urakka-id tiedot) nil)

        ;; Tallennetaan urakalle sanktio
        sanktio {:laji :yllapidon_sakko :suorasanktio true :summa sanktio-summa :perintapvm (pvm/->pvm "11.11.2024")}
        paatos {:kasittelyaika (pvm/nyt) :paatos "sanktio" :perustelu "joku perustelu" :kasittelytapa :tyomaakokous}
        laatupoikkeama {:tekijanimi "tekija" :urakka (:urakka-id tiedot) :yllapitokohde nil :aika (pvm/nyt) :paatos paatos}

        _ (laadunseuranta-palvelu/tallenna-suorasanktio (:db jarjestelma) +kayttaja-jvh+ sanktio laatupoikkeama (:urakka-id tiedot) nil)
        ;; Tallennetaan muu kustannus
        _ (kustannukset-palvelu/tallenna-yllapito-kustannus (:db jarjestelma) +kayttaja-jvh+ {:urakka-id (:urakka-id tiedot)
                                                                                         :selite "Haravoitiin lehdet asfaltilta"
                                                                                         :luoja nil
                                                                                         :kustannustyyppi "Muut kustannukset"
                                                                                         :vuosi (:vuosi tiedot)
                                                                                         :summa muukustannus-summa})
        muut-kustannukset-uudestaan (paikkausten-yhteenveto/koosta-muut-kustannukset
                                      (:db jarjestelma) +kayttaja-jvh+ (:urakka-id tiedot) (:alkupvm tiedot) (:loppupvm tiedot))]
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
                                                                     :toteutunut-hinta)))))

(deftest paikkausten-yhteenvedon-tyomentelmakustannukset-toimii
  (let [tiedot (testien-yhteiset-tiedot "Utajärven päällystysurakka" 2021)
        parametrit {:urakkaid (:urakka-id tiedot)
                    :alkupvm (:alkupvm tiedot)
                    :loppupvm (:loppupvm tiedot)}

        alkukustannukset (paikkausten-yhteenveto/hae-toteutuneet-tyomentelmakustannukset (:db jarjestelma) parametrit)
        _ (is (= [] alkukustannukset)) ;; Alkuun tyhjä lista

        ;; Lisätään ehdotettu tilassa olevia paikkauskohteita
        _ (lisaa-paikkauskohteet-excelista (:urakka-id tiedot))

        ;; Haetaan kaikki äsken lisätyt paikkauskohteet kannasta
        paikkauskohteet (q-map (format "SELECT * from paikkauskohde WHERE \"urakka-id\" = %s" (:urakka-id tiedot)))

        ehdotetut-kustannukset (paikkausten-yhteenveto/hae-toteutuneet-tyomentelmakustannukset (:db jarjestelma) parametrit)
        _ (is (= ehdotetut-kustannukset alkukustannukset)) ;; Ehdotetut paikkauskohteet eivät vaikuta toteutuneisiin kustannuksiin

        ;; Tilataan yksi kohde ja lisätään sille toteumia
        paikkauskohde (first paikkauskohteet)
        _ (valmistele-paikkauskohde-valmiiksi paikkauskohde (:urakka-id tiedot) (:vuosi tiedot))

        lopulliset-kustannukset (paikkausten-yhteenveto/hae-toteutuneet-tyomentelmakustannukset (:db jarjestelma) parametrit)
        odotettu-vastaus [{:nimi "Avarrussaumaus", :toteutunut-hinta 12000M, :suunniteltu-hinta 10000M}]]
    (is (= odotettu-vastaus lopulliset-kustannukset))))

(deftest paikkausten-yhteenvedon-tyomenetelmamaarat-toimii
  (let [tiedot (testien-yhteiset-tiedot "Utajärven päällystysurakka" 2021)
        parametrit {:urakkaid (:urakka-id tiedot)
                    :alkupvm (:alkupvm tiedot)
                    :loppupvm (:loppupvm tiedot)}

        alkumaarat (paikkausten-yhteenveto/hae-toteutuneet-maarat-tyomenetelmittain (:db jarjestelma) parametrit)
        _ (is (= [] alkumaarat)) ;; Alkuun tyhjä lista

        ;; Lisätään ehdotettu tilassa olevia paikkauskohteita
        _ (lisaa-paikkauskohteet-excelista (:urakka-id tiedot))

        ;; Haetaan kaikki äsken lisätyt paikkauskohteet kannasta
        paikkauskohteet (q-map (format "SELECT * from paikkauskohde WHERE \"urakka-id\" = %s" (:urakka-id tiedot)))

        ehdotetut-maarat (paikkausten-yhteenveto/hae-toteutuneet-maarat-tyomenetelmittain (:db jarjestelma) parametrit)
        _ (is (= ehdotetut-maarat alkumaarat)) ;; Ehdotetut paikkauskohteet eivät vaikuta toteutuneisiin määriin
        paikkauskohde (first paikkauskohteet)
        _ (valmistele-paikkauskohde-valmiiksi paikkauskohde (:urakka-id tiedot) (:vuosi tiedot))

        lopulliset-maarat (paikkausten-yhteenveto/hae-toteutuneet-maarat-tyomenetelmittain (:db jarjestelma) parametrit)
        odotettu-vastaus [{:nimi "Avarrussaumaus", :suunniteltu-maara 1000M :toteutunut-maara 456M :yksikko "jm"}]]
    (is (= odotettu-vastaus lopulliset-maarat))))

(deftest paikkausten-yhteenvedon-mpu-paikkauskustannusten-haku
  (let [urakka-id (hae-urakan-id-nimella "Kittilän MHU 2019-2024")
        ;; luodaan pieni suunnitelma paikkaushommiin, jotta todetaan että juuri se nousee kannasta
        ;; muuta sälää ja kulua on valmiiksi testidatassa, se ei saa nousta
        sopimus-id (hae-sopimus-id-nimella "Kittilän MHU sopimus")
        paallysteen-paikkauksen-tpi-kittila (ffirst (q (format "SELECT id FROM toimenpideinstanssi WHERE urakka = %s AND
        toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20107');" urakka-id)))
        paikkausten-summa 250
        indeksikorjattuna 280M
        id (i (format "INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, tehtavaryhma,
         tehtava, sopimus, luotu, luoja, muokattu, muokkaaja, summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja,
          versio) VALUES (2020, 6, %s, %s, null, null, %s, '2025-01-30 10:25:54.517112', 1, null, null, %s,
           '2025-01-30 10:25:54.517112', 1, 0);" paikkausten-summa paallysteen-paikkauksen-tpi-kittila sopimus-id indeksikorjattuna))
        haku (paikkausten-yhteenveto-mhu/mhu-paikkausten-suunnitellut-kustannukset (:db jarjestelma)
               {:urakkaid urakka-id
                :alkupvm (pvm/->pvm "1.10.2019")
                :loppupvm (pvm/->pvm "30.09.2020")})
        summa (:summa (first haku))]
    (is (integer? id) "Palautuu uusi id")
    (is (= indeksikorjattuna summa) "paikkausten-summa")))
