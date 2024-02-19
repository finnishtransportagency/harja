(ns harja.palvelin.raportointi.kanavat-raportti-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]))


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


(deftest kanavat-hairiotilanteet-raportti-toimii
  (let [urakka-id (hae-urakan-id-nimella "Saimaan kanava")
        alkupvm (c/to-date (t/local-date 2023 10 1))
        loppupvm (c/to-date (t/local-date 2024 9 30))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi       :kanavien-hairiotilanteet
                   :konteksti  "urakka"
                   :urakka-id  urakka-id
                   ;; Tämä generoidaan normaalisti suoraan frontin gridin kautta joten parametrit passataan hieman erillä lailla verrattuna normaaliin, mutta tämä ei testaamista estä 
                   :parametrit {:parametrit {:hallintayksikko 1
                                             :urakka {:id urakka-id}
                                             :aikavali [alkupvm loppupvm]
                                             :urakkatyyppi :vesivayla-kanavien-hoito}
                                ;; Manuaalisesti generoidut gridin rivit 
                                :rivit [(list "19.2.2024 6:38" "Kansola" "Liikennevaurio" "Test" 23 2 nil nil nil nil nil 48 "Valmis" "Ei")
                                        (list "19.2.2024 6:13" "Pälli" "Liikennevaurio" "Test" 4 3 5 6 2 nil 0 "Valmis" "Ei")
                                        (list "19.2.2024 4:07" "Soskua" "Sähkötekninen vika" "Jotain meni vikaan" 60 1 2 1 2 "Vika korjattiin" 92 "Valmis" "Kyllä")
                                        (list "19.2.2024 4:03" "Soskua" "Konetekninen vika" "Syy ei tiedossa" nil nil nil nil nil nil 0 "Kesken" "Ei")
                                        (list "19.2.2024 3:48" "Pälli" "Sähkötekninen vika" "Edellinen korjaus tehtiin huonosti, korjattu nyt uudestaan." 70 5 6 5 6 "Vika korjattiin" 20 "Valmis" "Kyllä")]}})
        raportin-nimi (-> vastaus second :nimi)]

    ;; Nimeksi tulee aina urakka, raportti ja passattu aikaväli
    (is (= raportin-nimi "Saimaan kanava, Häiriötilanteet ajalta 01.10.2023 - 30.09.2024"))
    ;; Raportilla pitäisi näkyä passatut tiedot 
    (is (= vastaus
           [:raportti
            {:nimi "Saimaan kanava, Häiriötilanteet ajalta 01.10.2023 - 30.09.2024", :piilota-otsikko? true}
            [:taulukko {:otsikko "Häiriötilanteet", :tyhja nil, :sheet-nimi "Häiriötilanteet"}
             [{:otsikko "Havainto", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "vaalen-tumma-tausta", :leveys 0.75, :tyyppi :varillinen-teksti}
              {:otsikko "Kohde", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.8, :tyyppi :varillinen-teksti}
              {:otsikko "Luokka", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.9, :tyyppi :varillinen-teksti}
              {:otsikko "Syy", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.9, :tyyppi :varillinen-teksti}
              {:otsikko "Vesi odotus (h)", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.5, :tyyppi :varillinen-teksti}
              {:otsikko "Ammatti", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.6, :tyyppi :varillinen-teksti}
              {:otsikko "Huvi", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.4, :tyyppi :varillinen-teksti}
              {:otsikko "Tie odotus (h)", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.5, :tyyppi :varillinen-teksti}
              {:otsikko "Ajoneuvot", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.7, :tyyppi :varillinen-teksti}
              {:otsikko "Toimenpide", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 1, :tyyppi :varillinen-teksti}
              {:otsikko "Korjaus aika", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.6, :tyyppi :varillinen-teksti}
              {:otsikko "Tila", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.6, :tyyppi :varillinen-teksti}
              {:otsikko "Paikallinen", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.8, :tyyppi :varillinen-teksti}]
             [(list "19.2.2024 6:38" "Kansola" "Liikennevaurio" "Test" 23 2 nil nil nil nil nil 48 "Valmis" "Ei")
              (list "19.2.2024 6:13" "Pälli" "Liikennevaurio" "Test" 4 3 5 6 2 nil 0 "Valmis" "Ei")
              (list "19.2.2024 4:07" "Soskua" "Sähkötekninen vika" "Jotain meni vikaan" 60 1 2 1 2 "Vika korjattiin" 92 "Valmis" "Kyllä")
              (list "19.2.2024 4:03" "Soskua" "Konetekninen vika" "Syy ei tiedossa" nil nil nil nil nil nil 0 "Kesken" "Ei")
              (list "19.2.2024 3:48" "Pälli" "Sähkötekninen vika" "Edellinen korjaus tehtiin huonosti, korjattu nyt uudestaan." 70 5 6 5 6 "Vika korjattiin" 20 "Valmis" "Kyllä")]]]))))


(deftest kanavat-toimenpiteet-raportti-toimii
  (let [urakka-id (hae-urakan-id-nimella "Saimaan kanava")
        alkupvm (c/to-date (t/local-date 2023 10 1))
        loppupvm (c/to-date (t/local-date 2024 9 30))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi       :kanavien-kokonaishintaiset-toimenpiteet
                   :konteksti  "urakka"
                   :urakka-id  urakka-id
                   ;; Tämä generoidaan normaalisti suoraan frontin gridin kautta joten parametrit passataan hieman erillä lailla verrattuna normaaliin, mutta tämä ei testaamista estä 
                   :parametrit {:parametrit {:hallintayksikko 1
                                             :urakka {:id urakka-id}
                                             :aikavali [alkupvm loppupvm]
                                             :urakkatyyppi :vesivayla-kanavien-hoito}
                                ;; Manuaalisesti generoidut gridin rivit 
                                :rivit [(list "19.2.2024 6:38" "Kansola" "Testi kohde" "Testi tehtävä" "Tehtiin juttuja" "Lisätietoja antaa päällikkö" "Samu Salminen" "Panu Harjalainen")
                                        (list "19.2.2024 6:38" "Kansola" "Testi kohde" "Testi tehtävä" "Tehtiin juttuja" "Lisätietoja antaa päällikkö" "Tea Salminen" "Markus Harjalainen")
                                        (list "19.2.2024 6:38" "Kansola" "Testi kohde" "Testi tehtävä" "Tehtiin juttuja" "Lisätietoja antaa päällikkö" "Santtu Salminen" "Paula Harjalainen")]}})
        raportin-nimi (-> vastaus second :nimi)]

    ;; Nimeksi tulee aina urakka, raportti ja passattu aikaväli
    (is (= raportin-nimi "Saimaan kanava, Kokonaishintaiset toimenpiteet ajalta 01.10.2023 - 30.09.2024"))
    ;; Raportilla pitäisi näkyä passatut tiedot 
    (is (= vastaus
           [:raportti 
            {:nimi "Saimaan kanava, Kokonaishintaiset toimenpiteet ajalta 01.10.2023 - 30.09.2024", :piilota-otsikko? true} 
            [:taulukko {:otsikko "Kokonaishintaiset toimenpiteet", :tyhja nil, :sheet-nimi "Kokonaishintaiset toimenpiteet"} 
             [{:otsikko "Pvm", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "vaalen-tumma-tausta", :leveys 0.5, :tyyppi :varillinen-teksti} 
              {:otsikko "Kohde", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.8, :tyyppi :varillinen-teksti}
              {:otsikko "Huoltokohde", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.8, :tyyppi :varillinen-teksti} 
              {:otsikko "Tehtävä", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.45, :tyyppi :varillinen-teksti}
              {:otsikko "Muu toimenpide", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.6, :tyyppi :varillinen-teksti} 
              {:otsikko "Lisätieto", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.8, :tyyppi :varillinen-teksti}
              {:otsikko "Suorittaja", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.5, :tyyppi :varillinen-teksti} 
              {:otsikko "Kuittaaja", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.8, :tyyppi :varillinen-teksti}] 
             [(list "19.2.2024 6:38" "Kansola" "Testi kohde" "Testi tehtävä" "Tehtiin juttuja" "Lisätietoja antaa päällikkö" "Samu Salminen" "Panu Harjalainen") 
              (list "19.2.2024 6:38" "Kansola" "Testi kohde" "Testi tehtävä" "Tehtiin juttuja" "Lisätietoja antaa päällikkö" "Tea Salminen" "Markus Harjalainen")
              (list "19.2.2024 6:38" "Kansola" "Testi kohde" "Testi tehtävä" "Tehtiin juttuja" "Lisätietoja antaa päällikkö" "Santtu Salminen" "Paula Harjalainen")]]]))))


(deftest kanavat-liikenne-raportti-toimii
  (let [urakka-id (hae-urakan-id-nimella "Saimaan kanava")
        alkupvm (c/to-date (t/local-date 2010 10 1))
        loppupvm (c/to-date (t/local-date 2070 9 30))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi       :kanavien-liikennetapahtumat
                   :konteksti  "urakka"
                   :urakka-id  urakka-id
                   :parametrit {:hakuparametrit {:urakka-idt #{urakka-id}}
                                :kasittelija :pdf
                                :alkupvm alkupvm
                                :loppupvm loppupvm
                                :urakkatyyppi :vesivayla-kanavien-hoito
                                ;; Yhteenveto passataan raporttiparametrina suoraan joten tehdään sellainen tässä manuaalisesti 
                                :yhteenveto {:toimenpiteet {:sulutukset-ylos 1, :sulutukset-alas 2, :sillan-avaukset 2, :tyhjennykset 1},
                                             :palvelumuoto {:paikallispalvelu 1, :kaukopalvelu 2, :itsepalvelu 0, :muu 0, :yhteensa 3}}}})
        raportin-nimi (-> vastaus second :nimi)]

    (is (= raportin-nimi "Saimaan kanava, Liikennetapahtumat"))
    (is (= vastaus
           [:raportti
            {:orientaatio :landscape, :nimi "Saimaan kanava, Liikennetapahtumat", :lyhennetty-tiedostonimi true}
            (list
              [:otsikko-heading "Liikennetapahtumat" {:padding-top "50px"}]
              [:taulukko {:otsikko nil, :oikealle-tasattavat-kentat #{}, :tyhja "Ei Tietoja.", :piilota-border? false, :viimeinen-rivi-yhteenveto? false}
               [{:otsikko "Aika", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "vaalen-tumma-tausta", :leveys 0.75, :tyyppi :varillinen-teksti}
                {:otsikko "Kohde", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.8, :tyyppi :varillinen-teksti}
                {:otsikko "Tyyppi", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.6, :tyyppi :varillinen-teksti}
                {:otsikko "Sillan avaus", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.6, :tyyppi :varillinen-teksti}
                {:otsikko "Palvelumuoto", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 1, :tyyppi :varillinen-teksti}
                {:otsikko "Suunta", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.6, :tyyppi :varillinen-teksti}
                {:otsikko "Alus", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 1, :tyyppi :varillinen-teksti}
                {:otsikko "Aluslaji", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.6, :tyyppi :varillinen-teksti}
                {:otsikko "Aluksia", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.6, :tyyppi :varillinen-teksti}
                {:otsikko "Matkustajia", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.7, :tyyppi :varillinen-teksti}
                {:otsikko "Nippuja", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.5, :tyyppi :varillinen-teksti}
                {:otsikko "Ylävesi", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.6, :tyyppi :varillinen-teksti}
                {:otsikko "Alavesi", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.6, :tyyppi :varillinen-teksti}
                {:otsikko "Lisätiedot", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 1, :tyyppi :varillinen-teksti}
                {:otsikko "Kuittaaja", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 1, :tyyppi :varillinen-teksti}]
               [[[:varillinen-teksti {:arvo "19.02.2024 klo 04:08"}] [:varillinen-teksti {:arvo "Pälli"}] [:varillinen-teksti {:arvo "Sillan avaus"}] [:varillinen-teksti {:arvo "✓"}] [:varillinen-teksti {:arvo "Itsepalvelu (20 kpl)"}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo 8900M}] [:varillinen-teksti {:arvo 7020M}] [:varillinen-teksti {:arvo "Testidata 7"}] [:varillinen-teksti {:arvo "Tero Toripolliisi"}]]
                [[:varillinen-teksti {:arvo "19.02.2024 klo 04:08"}] [:varillinen-teksti {:arvo "Pälli"}] [:varillinen-teksti {:arvo "Ei avausta"}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo ""}] [:varillinen-teksti {:arvo "Alas"}] [:varillinen-teksti {:arvo "Antin onni"}] [:varillinen-teksti {:arvo "HUV"}] [:varillinen-teksti {:arvo 4}] [:varillinen-teksti {:arvo 26}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo 8900M}] [:varillinen-teksti {:arvo 7020M}] [:varillinen-teksti {:arvo "Testidata 2"}] [:varillinen-teksti {:arvo "Tero Toripolliisi"}]]
                [[:varillinen-teksti {:arvo "19.02.2024 klo 04:08"}] [:varillinen-teksti {:arvo "Pälli"}] [:varillinen-teksti {:arvo "Ei avausta"}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo ""}] [:varillinen-teksti {:arvo "Ylös"}] [:varillinen-teksti {:arvo "Vanha leski"}] [:varillinen-teksti {:arvo "HUV"}] [:varillinen-teksti {:arvo 1}] [:varillinen-teksti {:arvo 4}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo 8900M}] [:varillinen-teksti {:arvo 7020M}] [:varillinen-teksti {:arvo "Testidata 2"}] [:varillinen-teksti {:arvo "Tero Toripolliisi"}]]
                [[:varillinen-teksti {:arvo "19.02.2024 klo 04:07"}] [:varillinen-teksti {:arvo "Kansola"}] [:varillinen-teksti {:arvo "Sulutus"}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo "Kauko"}] [:varillinen-teksti {:arvo "Ylös"}] [:varillinen-teksti {:arvo "Rohmu"}] [:varillinen-teksti {:arvo "RAH"}] [:varillinen-teksti {:arvo 1}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo 7300M}] [:varillinen-teksti {:arvo 7100M}] [:varillinen-teksti {:arvo "Testidata 1"}] [:varillinen-teksti {:arvo "Tero Toripolliisi"}]]
                [[:varillinen-teksti {:arvo "19.02.2024 klo 04:07"}] [:varillinen-teksti {:arvo "Kansola"}] [:varillinen-teksti {:arvo "Sulutus"}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo "Kauko"}] [:varillinen-teksti {:arvo "Ylös"}] [:varillinen-teksti {:arvo "Rölli"}] [:varillinen-teksti {:arvo "ÖLJ"}] [:varillinen-teksti {:arvo 1}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo 7300M}] [:varillinen-teksti {:arvo 7100M}] [:varillinen-teksti {:arvo "Testidata 1"}] [:varillinen-teksti {:arvo "Tero Toripolliisi"}]]
                [[:varillinen-teksti {:arvo "19.02.2024 klo 04:06"}] [:varillinen-teksti {:arvo "Kansola"}] [:varillinen-teksti {:arvo "Sulutus"}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo "Paikallis"}] [:varillinen-teksti {:arvo "Alas"}] [:varillinen-teksti {:arvo "Merikukko"}] [:varillinen-teksti {:arvo "SEK"}] [:varillinen-teksti {:arvo 1}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo 8400M}] [:varillinen-teksti {:arvo 5000M}] [:varillinen-teksti {:arvo "Testidata 3"}] [:varillinen-teksti {:arvo "Tero Toripolliisi"}]]
                [[:varillinen-teksti {:arvo "19.02.2024 klo 04:06"}] [:varillinen-teksti {:arvo "Kansola"}] [:varillinen-teksti {:arvo "Sulutus"}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo "Paikallis"}] [:varillinen-teksti {:arvo "Alas"}] [:varillinen-teksti {:arvo "Ronsu"}] [:varillinen-teksti {:arvo "LAU"}] [:varillinen-teksti {:arvo 1}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo 8400M}] [:varillinen-teksti {:arvo 5000M}] [:varillinen-teksti {:arvo "Testidata 3"}] [:varillinen-teksti {:arvo "Tero Toripolliisi"}]]
                [[:varillinen-teksti {:arvo "19.02.2024 klo 04:03"}] [:varillinen-teksti {:arvo "Kansola"}] [:varillinen-teksti {:arvo "Tyhjennys"}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo "Muu"}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo 8103M}] [:varillinen-teksti {:arvo 7010M}] [:varillinen-teksti {:arvo "Testidata 5"}] [:varillinen-teksti {:arvo "Tero Toripolliisi"}]]
                [[:varillinen-teksti {:arvo "19.02.2024 klo 04:03"}] [:varillinen-teksti {:arvo "Soskua"}] [:varillinen-teksti {:arvo "Sulutus, Ei avausta"}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo "Kauko"}] [:varillinen-teksti {:arvo "Alas"}] [:varillinen-teksti {:arvo "KEMIRA-12"}] [:varillinen-teksti {:arvo "ÖLJ"}] [:varillinen-teksti {:arvo 1}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo 8103M}] [:varillinen-teksti {:arvo 7010M}] [:varillinen-teksti {:arvo "Testidata 6"}] [:varillinen-teksti {:arvo "Tero Toripolliisi"}]]
                [[:varillinen-teksti {:arvo "19.02.2024 klo 03:53"}] [:varillinen-teksti {:arvo "Joensuun mutkan sillat"}] [:varillinen-teksti {:arvo "Sillan avaus, Ei avausta"}] [:varillinen-teksti {:arvo "✓"}] [:varillinen-teksti {:arvo "Kauko"}] [:varillinen-teksti {:arvo "Alas"}] [:varillinen-teksti {:arvo ""}] [:varillinen-teksti {:arvo "ÖLJ"}] [:varillinen-teksti {:arvo 1}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo nil}] [:varillinen-teksti {:arvo 8103M}] [:varillinen-teksti {:arvo 7010M}] [:varillinen-teksti {:arvo "Testidata 8"}] [:varillinen-teksti {:arvo "Tero Toripolliisi"}]]]])
            ;; Tässä pitäisi näkyä yllä passattu yhteenveto
            [:liikenneyhteenveto
             {:toimenpiteet {:sulutukset-ylos 1, :sulutukset-alas 2, :sillan-avaukset 2, :tyhjennykset 1},
              :palvelumuoto {:paikallispalvelu 1, :kaukopalvelu 2, :itsepalvelu 0, :muu 0, :yhteensa 3}}]]))))
