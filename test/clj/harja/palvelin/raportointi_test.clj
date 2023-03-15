(ns harja.palvelin.raportointi-test
  (:require [harja.palvelin.raportointi :as r]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.palvelin.raportointi.raportit.tyomaakokous :as tyomaakokous]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.kyselyt.pohjavesialueet :as p]
            [harja.palvelin.palvelut.raportit :as raportit]
            [clojure.test :refer [deftest is testing] :as t]
            [clj-time.coerce :as c]
            [clj-time.core :as time]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.excel :as excel]))

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
                                    (r/luo-raportointi)
                                    [:db :pdf-vienti])
                      :raportit (component/using
                                  (raportit/->Raportit)
                                  [:http-palvelin :db :raportointi :pdf-vienti])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(t/use-fixtures :each jarjestelma-fixture)

(deftest raporttien-haku-toimii
  (let [r (r/hae-raportit (:raportointi jarjestelma))]
    (is (contains? r :yks-hint-kuukausiraportti) "yks-hint-kuukausiraportti löytyy raporteista")
    (is (contains? r :erilliskustannukset) "Erilliskustannusraportti löytyy raporteista")
    (is (contains? r :tiestotarkastusraportti) "tiestotarkastusraportti löytyy raporteista")
    (is (contains? r :kelitarkastusraportti) "kelitarkastusraportti löytyy raporteista")
    (is (contains? r :ilmoitusraportti) "Ilmoitusraportti löytyy raporteista")
    (is (contains? r :turvallisuus) "turvallisuus löytyy raporteista")
    (is (contains? r :tyomaakokous) "tyomaakokous löytyy raporteista")
    (is (contains? r :suolasakko) "suolasakko löytyy raporteista")
    (is (contains? r :ymparistoraportti) "ymparistoraportti löytyy raporteista")
    (is (contains? r :laskutusyhteenveto) "Laskutusyhteenveto löytyy raporteista")
    (is (contains? r :materiaaliraportti) "Materiaaliraportti löytyy raporteista")
    (is (contains? r :yks-hint-tyot) "Yksikköhintaiset työt löytyy raporteista")
    (is (contains? r :yks-hint-tehtavien-summat) "yks-hint-tehtavien-summat löytyy raporteista")
    (is (not (contains? r :olematon-raportti)))))

(deftest kuukaudet-apuri-test
  (let [odotettu '("2014/10"
                   "2014/11"
                   "2014/12"
                   "2015/01"
                   "2015/02"
                   "2015/03"
                   "2015/04"
                   "2015/05"
                   "2015/06"
                   "2015/07"
                   "2015/08"
                   "2015/09")
        alkupvm (pvm/->pvm "1.10.2014")
        loppupvm (pvm/->pvm "30.9.2015")]
    (is (= odotettu (yleinen/kuukaudet alkupvm loppupvm))) "oikeat hoitokauden kuukaudet"))

(deftest raportin-suorituksen-oikeustarkistus
  (let [kutsu-palvelua (fn [kayttaja]
                         (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :suorita-raportti
                                         kayttaja
                                         {:nimi       :yks-hint-kuukausiraportti
                                          :konteksti  "koko maa"
                                          :parametrit {:alkupvm  (c/to-date (time/local-date 2005 10 10))
                                                       :loppupvm (c/to-date (time/local-date 2010 10 10))}}))]
    (is (thrown? Exception (kutsu-palvelua +kayttaja-yit_uuvh+)))
    ;; ei heitä virhettä järjestelmänvastuuhenkilölle
    (is (some? (kutsu-palvelua +kayttaja-jvh+)))
    ;; ei heitä virhettä tilaajan urakanvalvojalle
    (is (some? (kutsu-palvelua +kayttaja-tero+)))))

(deftest raportin-suoritustietojen-roolien-parsinta
  (let [roolit-jvh #{"Jarjestelmavastaava"}
        roolit-kaksi-roolia #{"Tilaajan_Asiantuntija" "Jarjestelmavastaava"}
        roolit-tyhja {}

        urakkaroolit-vastuuhlo-ja-laadunvalvoja {35 #{"vastuuhenkilo" "Laadunvalvoja"}, 7 #{"vastuuhenkilo"}}
        urakkaroolit-urakanvalvoja {35 #{"ELY_Urakanvalvoja"}}
        urakkaroolit-tyhja {}

        organisaatioroolit-kayttaja-ja-vaihtaja {21 #{"Kayttaja" "Vaipanvaihtaja"}}
        organisaatioroolit-tyhja {}
        ]
    (is (= (r/parsi-roolit roolit-jvh) "Jarjestelmavastaava"))
    (is (= (r/parsi-roolit roolit-kaksi-roolia) "Tilaajan_Asiantuntija,Jarjestelmavastaava"))
    (is (nil? (r/parsi-roolit roolit-tyhja)) "Ei rooleja, tultava nil")

    (is (= (r/parsi-urakka-tai-organisaatioroolit urakkaroolit-vastuuhlo-ja-laadunvalvoja) "vastuuhenkilo,Laadunvalvoja"))
    (is (= (r/parsi-urakka-tai-organisaatioroolit urakkaroolit-urakanvalvoja) "ELY_Urakanvalvoja"))
    (is (nil? (r/parsi-urakka-tai-organisaatioroolit urakkaroolit-tyhja)) "Ei rooleja, tultava nil")

    (is (= (r/parsi-urakka-tai-organisaatioroolit organisaatioroolit-kayttaja-ja-vaihtaja) "Kayttaja,Vaipanvaihtaja"))
    (is (nil? (r/parsi-urakka-tai-organisaatioroolit organisaatioroolit-tyhja)) "Ei rooleja, tultava nil")))

(deftest raportin-suoritustietojen-urakka-idn-tarkistus
  (let [oulun-alueurakan-2014-2019-id (hae-oulun-alueurakan-2014-2019-id)]
    (is (thrown? SecurityException (r/vaadi-urakka-on-olemassa (:db jarjestelma) 666123)) "Urakkaa ei olemassa")
    (is (nil? (r/vaadi-urakka-on-olemassa (:db jarjestelma) oulun-alueurakan-2014-2019-id)) "Urakka on olemassa")))

(deftest raportin-suoritustietojen-org-idn-tarkistus
  (let [pohjois-pohjanmaan-hallintayksikon-id (hae-pohjois-pohjanmaan-hallintayksikon-id)]
    (is (thrown? SecurityException (r/vaadi-hallintayksikko-on-olemassa (:db jarjestelma) 666123)) "Hallintayksikköä ei olemassa")
    (is (nil? (r/vaadi-hallintayksikko-on-olemassa (:db jarjestelma) pohjois-pohjanmaan-hallintayksikon-id)) "Hallintayksikkö on olemassa")))

(def suorituskontekstin-kuvaus-parametrit-urakka
  {:nimi :erilliskustannukset, :konteksti "urakka", :urakka-id 4, :parametrit {:alkupvm #inst "2014-09-30T21:00:00.000-00:00", :loppupvm #inst "2021-09-30T20:59:59.000-00:00", :toimenpide-id 618, :urakkatyyppi :hoito}})

(def suorituskontekstin-kuvaus-raportti
  [:raportti {:nimi "Erilliskustannusten raportti"}
   [:taulukko {:oikealle-tasattavat-kentat #{4 5}, :otsikko "Oulun alueurakka 2014-2019, P, Erilliskustannusten raportti ajalta 01.10.2014 - 30.09.2021", :viimeinen-rivi-yhteenveto? true, :sheet-nimi "Erilliskustannusten raportti"}

    [{:leveys 7, :otsikko "Pvm"}
     {:leveys 7, :otsikko "Sop. nro"}
     {:leveys 12, :otsikko "Toimenpide"}
     {:leveys 7, :otsikko "Tyyppi"}
     {:leveys 6, :otsikko "Summa", :fmt :raha}
     {:leveys 6, :otsikko "Ind.korotus", :fmt :raha}]
    '(["30.09.2019" "011BG-0062699" "Oulun alueurakka 2014-2019, Talvihoito, TP" "As.tyyt.­bonus" 41835.26M 1508.422749850389647078M] ("Yhteensä" 41835.26M 1508.422749850389647078M))]])

(def suorituskontekstin-kuvaus-parametrit-hy
  {:nimi :erilliskustannukset, :konteksti "hallintayksikko", :hallintayksikko-id 12, :parametrit {:alkupvm #inst "2014-09-30T21:00:00.000-00:00", :loppupvm #inst "2015-09-29T21:00:00.000-00:00", :urakkatyyppi :hoito}})

(def suorituskontekstin-kuvaus-raportti-hy-tai-koko-maa-dummy
  [:raportti {:nimi "Erilliskustannusten raportti testi"}])

(def suorituskontekstin-kuvaus-parametrit-koko-maa
  {:nimi :erilliskustannukset, :konteksti "koko maa", :parametrit {:alkupvm #inst "2014-09-30T21:00:00.000-00:00", :loppupvm #inst "2015-09-30T20:59:59.000-00:00", :urakkatyyppi :hoito}})

(deftest suorituskontekstin-kuvaus-urakka
  (let [kuvaus-liitetty (r/liita-suorituskontekstin-kuvaus (:db jarjestelma)
                                                           suorituskontekstin-kuvaus-parametrit-urakka
                                                           suorituskontekstin-kuvaus-raportti)
        odotettu-liitetty [:raportti
                           {:nimi "Erilliskustannusten raportti"
                            :raportin-yleiset-tiedot {:alkupvm "01.10.2014"
                                                      :loppupvm "30.09.2021"
                                                      :raportin-nimi "Erilliskustannusten raportti"
                                                      :urakka "Oulun alueurakka 2014-2019"}
                            :tietoja (list ["Kohde"
                                            "Urakka"]
                                           ["Urakka"
                                            "Oulun alueurakka 2014-2019"]
                                           ["Urakoitsija"
                                            "YIT Rakennus Oy"])}
                           [:taulukko
                            {:oikealle-tasattavat-kentat #{4
                                                           5}
                             :otsikko "Oulun alueurakka 2014-2019, P, Erilliskustannusten raportti ajalta 01.10.2014 - 30.09.2021"
                             :sheet-nimi "Erilliskustannusten raportti"
                             :viimeinen-rivi-yhteenveto? true}
                            [{:leveys 7
                              :otsikko "Pvm"}
                             {:leveys 7
                              :otsikko "Sop. nro"}
                             {:leveys 12
                              :otsikko "Toimenpide"}
                             {:leveys 7
                              :otsikko "Tyyppi"}
                             {:fmt :raha
                              :leveys 6
                              :otsikko "Summa"}
                             {:fmt :raha
                              :leveys 6
                              :otsikko "Ind.korotus"}]
                            (list ["30.09.2019"
                                   "011BG-0062699"
                                   "Oulun alueurakka 2014-2019, Talvihoito, TP"
                                   "As.tyyt.­bonus"
                                   41835.26M
                                   1508.422749850389647078M]
                                  (list "Yhteensä"
                                        41835.26M
                                        1508.422749850389647078M))]]]
    (is (= kuvaus-liitetty odotettu-liitetty) "Raporttiin liitetty suorituskonteksti ihan okei")))


;; testataan hy:n ja kokomaan osalta vain relevantit osat, eli raportin yleiset tieodt ja tietoja avainten sisältö, vähemmän noisea
(deftest suorituskontekstin-kuvaus-hy
  (let [kuvaus-liitetty (r/liita-suorituskontekstin-kuvaus (:db jarjestelma)
                                                           suorituskontekstin-kuvaus-parametrit-hy
                                                           suorituskontekstin-kuvaus-raportti-hy-tai-koko-maa-dummy)
        odotettu-liitetty [:raportti {:nimi "Erilliskustannusten raportti testi",
                                      :raportin-yleiset-tiedot {:urakka "Pohjois-Pohjanmaa",
                                                                :alkupvm "01.10.2014",
                                                                :loppupvm "30.09.2015",
                                                                :raportin-nimi "Erilliskustannusten raportti testi"},
                                      :tietoja (list ["Kohde" "Hallintayksikkö"] ["Hallintayksikkö" "Pohjois-Pohjanmaa"] ["Tyypin hoito urakoita käynnissä" 2])}]]
    (is (= kuvaus-liitetty odotettu-liitetty) "Raporttiin liitetty suorituskonteksti ihan okei")))

(deftest suorituskontekstin-kuvaus-kokomaa
  (let [kuvaus-liitetty (r/liita-suorituskontekstin-kuvaus (:db jarjestelma)
                                                           suorituskontekstin-kuvaus-parametrit-koko-maa
                                                           suorituskontekstin-kuvaus-raportti-hy-tai-koko-maa-dummy)
        odotettu-liitetty [:raportti
                           {:nimi "Erilliskustannusten raportti testi"
                            :raportin-yleiset-tiedot {:alkupvm "01.10.2014"
                                                      :loppupvm "30.09.2015"
                                                      :raportin-nimi "Erilliskustannusten raportti testi"
                                                      :urakka "Koko maa"}
                            :tietoja [["Kohde"
                                       "Koko maa"]
                                      ["Tyypin hoito urakoita käynnissä"
                                       2]]}]]
    (is (= kuvaus-liitetty odotettu-liitetty) "Raporttiin liitetty suorituskonteksti ihan okei")))

;; VHAR-5683 Materialisoitu näkymä unohtui päivittää
;; Kaksi näkymää liittyy Suolauksen Käyttöraja arvon välittymiseen raporteille.
;;   pohjavesialue_talvisuola.talvisuolaraja -> Materialized View - pohjavesialue_kooste.suolarajoitus
;;   pohjavesialue_kooste.suolarajoitus -> raportti_pohjavesialueiden_suolatoteumat.kayttoraja
;;
;; Puuttui hoitokauden alkuvuosi pohjavesialue_kooste ja raportti_pohjavesialueiden_suolatoteumat käsittelyssä
(deftest pohjavesialue-kooste-materialized-view-paivittyy-sql-toteutuksessa
  (let [tunnus 11244001
        vanha-suola 6.6M
        odotettu-tie 846
        suolaa 123
        odotettu-suolaa 123M
        suolaraja-materialized-viewsta (fn [tie] (-> (str "SELECT talvisuolaraja FROM pohjavesialue_kooste WHERE tunnus = '" tunnus "' AND tie = " tie " ORDER BY tunnus, tie")
                                                     q-map
                                                     first
                                                     :talvisuolaraja))
        db (:db jarjestelma)]
    (p/paivita-pohjavesialue-kooste db)                     ; Päivitys voi tapahtua ennen muokkausta
    (is (= vanha-suola (suolaraja-materialized-viewsta odotettu-tie))) ; Alkutilanne
    (is (= 2 (u (str "UPDATE pohjavesialue_talvisuola SET tie = " odotettu-tie ", talvisuolaraja = " suolaa "
        WHERE pohjavesialue = '" tunnus "'
        AND tie = " odotettu-tie))))
    (is (= vanha-suola (suolaraja-materialized-viewsta odotettu-tie))) ; Taulun päivitys ei päivitä MV:ta
    (p/paivita-pohjavesialue-kooste db)
    (is (= odotettu-suolaa (suolaraja-materialized-viewsta odotettu-tie)))
    (is (= 2 (u (str "UPDATE pohjavesialue_talvisuola SET tie = " odotettu-tie ", talvisuolaraja = 6.6
        WHERE pohjavesialue = '" tunnus "'
        AND tie = " odotettu-tie))))))

(deftest tyomaakokousraporttiin-oikea-laskutusyhteenveto
  (let [mhu-oulu-urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        hoito-oulu-urakka-id (hae-oulun-alueurakan-2014-2019-id)
        tiedot-mhu  {:laskutusyhteenveto true, :tiestotarkastusraportti false, :urakka-id mhu-oulu-urakka-id, :loppupvm #inst "2022-01-31T21:59:59.000-00:00", :laatupoikkeamaraportti false, :ilmoitusraportti false, :alkupvm #inst "2021-12-31T22:00:00.000-00:00", :muutos-ja-lisatyot false, :urakkatyyppi :teiden-hoito}
        tiedot-hoito {:laskutusyhteenveto true, :tiestotarkastusraportti false, :urakka-id hoito-oulu-urakka-id, :loppupvm #inst "2022-01-31T21:59:59.000-00:00", :laatupoikkeamaraportti false, :ilmoitusraportti false, :alkupvm #inst "2021-12-31T22:00:00.000-00:00", :muutos-ja-lisatyot false, :urakkatyyppi :hoito}
        laskutusyhteenveto-tuotekohtainen-raportti (tyomaakokous/urakkatyypin-laskutusyhteenveto (:db jarjestelma)
                                                                                      +kayttaja-jvh+
                                                                                      tiedot-mhu)
        laskutusyhteenveto-hoito-raportti (tyomaakokous/urakkatyypin-laskutusyhteenveto (:db jarjestelma)
                                                                                        +kayttaja-jvh+
                                                                                        tiedot-hoito)]
    (is (= "Laskutusyhteenveto (01.01.2022 - 31.01.2022)"
           (-> laskutusyhteenveto-tuotekohtainen-raportti
               second
               :nimi)) "On MHU-tyypin laskutusyhteenveto")
    (is (= "Laskutusyhteenveto"
           (-> laskutusyhteenveto-hoito-raportti
               second
               :nimi)) "On hoito-tyypin laskutusyhteenveto")))

(deftest parsi-excelin-rivinumero
  (is (= 1 (excel/parsi-rivinumero "A1")) "A1 parsitaan rivi 1")
  (is (= 12 (excel/parsi-rivinumero "AC12")) "AC12 parsitaan rivi 12")
  (is (= 665 (excel/parsi-rivinumero "G665")) "G665 parsitaan rivi 665"))

(deftest parsi-excelin-sarakekirjain
  (is (= "A" (excel/parsi-sarakekirjain "A1")) "A1 parsitaan A")
  (is (= "AC" (excel/parsi-sarakekirjain "AC12")) "AC12 parsitaan AC")
  (is (= "ACHHHH" (excel/parsi-sarakekirjain "ACHHHH12")) "ACHHHH12 parsitaan ACHHHH")
  (is (= "G" (excel/parsi-sarakekirjain "G665")) "G665 parsitaan G"))
