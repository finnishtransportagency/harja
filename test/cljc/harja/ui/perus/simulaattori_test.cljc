(ns harja.ui.perus.simulaattori-test
  (:require [clojure.test :refer [deftest is testing]]
            [harja.ui.perus :as perus]
            [harja.ui.perus.simulaattori :as simulaattori]))

(deftest kelvollinen-puu-hyvaksytaan
  (is (simulaattori/kelvollinen?
        [{:komponentti :lomake
          :lapset [{:komponentti :livi-pudotusvalikko}]}])))

(deftest tuntematon-komponentti-hylataan
  (is (= ["Tuntematon komponentti: :ei-ole"]
         (simulaattori/virheet [{:komponentti :ei-ole}]))))

(deftest vaarallinen-vanhempi-lapsi-suhde-hylataan
  (testing "lomake ei voi sisältää nappia suoraan sivun juurena"
    (is (= [":nappi: vanhempi=:sivu ei ole sallittu"]
           (simulaattori/virheet [{:komponentti :nappi}])))))

(defn- livi-asetuksilla [asetukset]
  [{:komponentti :lomake
    :lapset [{:komponentti :livi-pudotusvalikko
              :asetukset asetukset}]}])

(deftest tuntematon-asetus-hylataan
  (is (= ["livi-pudotusvalikko: tuntematon asetus :ei-ole"]
         (simulaattori/virheet (livi-asetuksilla {:ei-ole "x"})))))

(deftest suojattu-asetus-hylataan
  (testing "komponentin omistamia avaimia ei voi ylikirjoittaa"
    (doseq [avain [:id :arvo :vaihda-fn]]
      (is (= [(str "livi-pudotusvalikko: suojattu asetus " avain " ei ole sallittu")]
             (simulaattori/virheet (livi-asetuksilla {avain "x"})))
          (str "avain " avain)))))

(deftest funktio-hylataan-asetuksessa
  (is (= ["livi-pudotusvalikko: asetus :otsikko on funktio, ei sallittu"]
         (simulaattori/virheet (livi-asetuksilla {:otsikko (fn [] "x")})))))

(deftest symboli-hylataan-asetuksessa
  (is (= ["livi-pudotusvalikko: asetus :otsikko on symboli, ei sallittu"]
         (simulaattori/virheet (livi-asetuksilla {:otsikko 'puhelin})))))

(deftest funktio-hylataan-sisempaan-arvoon
  (is (= ["livi-pudotusvalikko: asetus :valinnat on funktio, ei sallittu"]
         (simulaattori/virheet
           (livi-asetuksilla {:valinnat [{:arvo :hoito :nimi (fn [] "x")}]})))))

#?(:clj
   (deftest tagattu-literaali-hylataan
     (is (= ["livi-pudotusvalikko: asetus :otsikko on tagattu literaali, ei sallittu"]
            (simulaattori/virheet
              (livi-asetuksilla {:otsikko (tagged-literal 'foo 1)}))))))

(deftest kelvolliset-asetukset-hyvaksytaan
  (is (simulaattori/kelvollinen?
        (livi-asetuksilla {:otsikko "Urakkatyyppi"
                           :valinnat [{:arvo :hoito :nimi "Hoito"}]
                           :optiot {:data-cy "urakkatyyppi"}}))))

(deftest asetusarvon-tyyppi-tarkistetaan
  (is (= ["pino: asetus :vali on väärän tyyppinen; odotettiin :keyword"]
         (simulaattori/virheet
           [{:komponentti :pino
             :asetukset {:vali "keski"}}]))))

(deftest asetusarvon-sallittu-token-tarkistetaan
  (is (= ["pino: asetus :vali sisältää tuntemattoman arvon :olemattomaksi"]
         (simulaattori/virheet
           [{:komponentti :pino
             :asetukset {:vali :olemattomaksi}}]))))

(deftest poistettu-valintaryhma-hylataan
  (is (= ["Tuntematon komponentti: :valintaryhma"]
         (simulaattori/virheet [{:komponentti :valintaryhma}]))))

(deftest sivutilasopimus-kattaa-pilotin-rajatilat
  (is (= [:normaali :lataus :tyhja :virhe :ei-oikeutta]
         (mapv :id simulaattori/sivutilat)))
  (doseq [tila simulaattori/sivutilat]
    (is (= tila (simulaattori/sivutila (:id tila)))
        (str "tila " (:id tila) " löytyy sopimuksesta"))))

(deftest sivutilan-esitys-kayttaa-tilaviestin-aria-sopimusta
  (doseq [tila (remove (comp #{:normaali} :id) simulaattori/sivutilat)]
    (let [{:keys [tilaviesti rooli]} tila]
      (is (some? (:viesti tilaviesti))
          (str (:id tila) " sisältää käyttäjälle näkyvän viestin"))
      (is (= rooli (get perus/tilaviestin-roolit (:tila tilaviesti)))
          (str (:id tila) " käyttää tilaviestin roolia")))))

(deftest virheellinen-rakenne-antaa-virheen-eika-kaadu
  (testing "asetukset ei ole map"
    (doseq [asetukset ["x" [1 2] 5]]
      (is (= [":lomake: asetukset ei ole map"]
             (simulaattori/virheet [{:komponentti :lomake :asetukset asetukset}]))
          (pr-str asetukset))))
  (testing "lapset ei ole lista"
    (is (= [":lomake: lapset ei ole lista"]
           (simulaattori/virheet [{:komponentti :lomake :lapset "abc"}]))))
  (testing "lapsi ei ole map"
    (is (= ["Solmun pitää olla map: 1"]
           (simulaattori/virheet [{:komponentti :lomake :lapset [1]}])))))

(deftest layout-komponentit-hyvaksyvat-tyypilliset-lapsensa
  (testing "listaussivu: suodattimet, taulukko ja toiminnot"
    (is (simulaattori/kelvollinen?
          [{:komponentti :pino
            :lapset [{:komponentti :suodatinrivi
                      :lapset [{:komponentti :hoitokausi}
                               {:komponentti :livi-pudotusvalikko}
                               {:komponentti :checkbox-pudotusvalikko}
                               {:komponentti :nappi}]}
                     {:komponentti :perusgrid}
                     {:komponentti :toimintoryhma
                      :lapset [{:komponentti :nappi}
                               {:komponentti :linkki}]}]}])))
  (testing "panel sisältää tietoja ja muita pinoja"
    (is (simulaattori/kelvollinen?
          [{:komponentti :pino
            :lapset [{:komponentti :panel
                      :lapset [{:komponentti :tietoja}
                               {:komponentti :taulukkotietonakyma}
                               {:komponentti :info-laatikko}]}]}])))
  (testing "säiliö, osio, ruudukko ja vaaka yhdistyvät pinoon, tilaviesteihin ja nappeihin"
    (is (simulaattori/kelvollinen?
          [{:komponentti :sailio
            :lapset [{:komponentti :osio
                      :asetukset {:data-cy "layout-osio"}
                      :lapset [{:komponentti :pino
                               :lapset [{:komponentti :tietoja}]}
                              {:komponentti :ruudukko
                               :lapset [{:komponentti :pino
                                         :lapset [{:komponentti :nappi}]}
                                        {:komponentti :vaaka
                                         :lapset [{:komponentti :nappi}
                                                  {:komponentti :linkki}]}
                                        {:komponentti :tilaviesti}]}]}]}])))
  (testing "suodatin kuuluu suodatinriviin, ei suoraan sivulle"
    (is (seq (simulaattori/virheet [{:komponentti :suodatin}])))
    (is (simulaattori/kelvollinen?
          [{:komponentti :suodatinrivi
            :lapset [{:komponentti :suodatin}
                     {:komponentti :livi-pudotusvalikko}]}]))))

(deftest suodatinrivi-sallii-checkboxin-lapsena
  (testing "checkbox-pudotusvalikko on sallittu suodatinrivin lapsena rekisterin mukaan"
    (is (simulaattori/kelvollinen?
          [{:komponentti :lomake
            :lapset [{:komponentti :suodatinrivi
                      :lapset [{:komponentti :checkbox-pudotusvalikko}]}]}]))))

(deftest suodatinrivi-sallii-yksittaisen-checkboxin-lapsena
  (testing "plain checkbox on sallittu suodatinrivin lapsena"
    (is (simulaattori/kelvollinen?
          [{:komponentti :suodatinrivi
            :lapset [{:komponentti :checkbox}]}])))
  (testing "plain checkbox hylätään osion alla"
    (is (seq (simulaattori/virheet
               [{:komponentti :osio
                 :asetukset {:data-cy "checkbox-osio"}
                 :lapset [{:komponentti :checkbox}]}])))))

(deftest liitteet-sallitaan-lomakkeen-lapsena-ja-hylataan-osion-alla
  (is (simulaattori/kelvollinen?
        [{:komponentti :lomake
          :lapset [{:komponentti :liitteet}]}]))
  (is (= [":liitteet: vanhempi=:osio ei ole sallittu"]
         (simulaattori/virheet
           [{:komponentti :lomake
             :lapset [{:komponentti :osio
                       :asetukset {:data-cy "liite-osio"}
                       :lapset [{:komponentti :liitteet}]}]}]))))

(deftest sailio-sallitaan-vain-sivun-juuressa
  (is (simulaattori/kelvollinen? [{:komponentti :sailio}]))
  (is (= [":sailio: vanhempi=:lomake ei ole sallittu"]
         (simulaattori/virheet
           [{:komponentti :lomake
             :lapset [{:komponentti :sailio}]}]))))

(deftest suodatinrivi-sallii-tekstikentan-lapsena
  (is (simulaattori/kelvollinen?
        [{:komponentti :lomake
          :lapset [{:komponentti :suodatinrivi
                    :lapset [{:komponentti :tekstikentta}]}]}])))

(deftest pakollinen-simulaattoriasetus-hylataan
  (is (= ["perusgrid: pakollinen asetus :sarakkeet puuttuu"]
         (simulaattori/virheet
           [{:komponentti :perusgrid
             :asetukset {:rivit []}}]))))

(deftest kortti-vaatii-vakaan-simulaattoriankkurin
  (is (= ["kortti: pakollinen asetus :data-cy puuttuu"]
         (simulaattori/virheet [{:komponentti :kortti}]))))

(deftest osio-vaatii-vakaan-simulaattoriankkurin
  (is (= ["osio: pakollinen asetus :data-cy puuttuu"]
         (simulaattori/virheet
           [{:komponentti :osio
             :asetukset {:otsikko "Otsikko"}}]))))

(deftest kortti-hylkaa-virheellisen-tokenin
  (is (= ["kortti: :data-cy pitää olla ei-tyhjä merkkijono"
          "kortti: :otsikkotaso pitää olla kokonaisluku 1–6"
          "kortti: asetus :koko sisältää tuntemattoman arvon :iso"]
         (simulaattori/virheet
           [{:komponentti :kortti
             :asetukset {:data-cy " "
                         :koko :iso
                         :otsikkotaso 7}}]))))

(deftest tietolista-hylkaa-virheellisen-rivin
  (is (= ["tietolista: rivi 2 ei ole {:otsikko ... :arvo ...}-kartta"]
         (simulaattori/virheet
           [{:komponentti :tietolista
             :asetukset {:rivit [{:otsikko "Nimi" :arvo "Urakka"}
                                 "rikki"]}}]))))

(deftest tietolista-hylkaa-skalaaririvit
  (is (= ["tietolista: asetus :rivit on väärän tyyppinen; odotettiin :vektori"]
         (simulaattori/virheet
           [{:komponentti :tietolista
             :asetukset {:rivit "ei-vektori"}}]))))

(deftest tietolista-vaatii-rivit-ilman-asetuskarttaa
  (is (= ["tietolista: pakollinen asetus :rivit puuttuu"]
         (simulaattori/virheet [{:komponentti :tietolista}]))))

(deftest tietolista-hylkaa-virheelliset-asetelmat
  (is (= ["tietolista: asetus :otsikkoleveys sisältää tuntemattoman arvon :massive"
          "tietolista: asetus :tiivis? on väärän tyyppinen; odotettiin :boolean"
          "tietolista: asetus :viivat? on väärän tyyppinen; odotettiin :boolean"]
         (simulaattori/virheet
           [{:komponentti :tietolista
             :asetukset {:rivit []
                         :otsikkoleveys :massive
                         :tiivis? "kyllä"
                         :viivat? 1}}]))))

(deftest kenttaryhma-sallii-yhden-rekisteroidyn-kenttakomponentin
  (doseq [kentta [:tekstikentta
                  :numerokentta
                  :paivamakentta
                  :livi-pudotusvalikko
                  :tekstialue]]
    (is (simulaattori/kelvollinen?
          [{:komponentti :lomake
            :lapset [{:komponentti :kenttaryhma
                      :asetukset {:otsikko "Kenttä"
                                  :data-cy (str "kenttaryhma-" (name kentta))}
                      :lapset [{:komponentti kentta}]}]}])
        (name kentta))))

(deftest kenttaryhma-vaatii-tasmalleen-yhden-kenttakomponentin
  (doseq [lapset [[] [{:komponentti :tekstikentta}
                     {:komponentti :numerokentta}]]]
    (is (= ["kenttaryhma: vaatii täsmälleen yhden kenttäkomponentin"]
           (simulaattori/virheet
             [{:komponentti :lomake
               :lapset [{:komponentti :kenttaryhma
                         :asetukset {:otsikko "Kenttä"
                                     :data-cy "kenttaryhma-testi"}
                         :lapset lapset}]}])))))

(deftest kenttaryhma-vaatii-vakaan-data-cy-tunnisteen
  (is (= ["kenttaryhma: simulaattorissa vaaditaan :data-cy elementti-id:n muodostamiseen"]
         (simulaattori/virheet
           [{:komponentti :lomake
             :lapset [{:komponentti :kenttaryhma
                       :lapset [{:komponentti :tekstikentta}]}]}]))))

(defn- perusgrid-sarakkeella [sarake]
  [{:komponentti :perusgrid
    :asetukset {:sarakkeet [sarake] :rivit []}}])

(deftest tuntematon-solutyyppi-hylataan
  (is (= ["perusgrid: tuntematon solutyyppi :ei-ole"]
         (simulaattori/virheet (perusgrid-sarakkeella {:nimi :tila :solu :ei-ole})))))

(deftest tila-indikaattori-solutyyppi-sallitaan
  (is (simulaattori/kelvollinen?
        (perusgrid-sarakkeella {:nimi :tila :solu :tila-indikaattori}))))

(deftest linkki-solutyyppi-sallitaan
  (is (empty? (simulaattori/virheet
                (perusgrid-sarakkeella {:nimi :tila :solu :linkki})))))

(deftest vetolaatikko-rivin-tiedot-sallitaan
  (is (empty? (simulaattori/virheet
                [{:komponentti :perusgrid
                  :asetukset {:sarakkeet [{:nimi :tiedot :tyyppi :vetolaatikon-tila}]
                              :vetolaatikko :rivin-tiedot
                              :rivit []}}]))))

(deftest tuntematon-vetolaatikko-hylataan
  (is (= ["perusgrid: tuntematon vetolaatikko :ei-ole"]
         (simulaattori/virheet
           [{:komponentti :perusgrid
             :asetukset {:sarakkeet [] :vetolaatikko :ei-ole :rivit []}}]))))

(deftest ryhmaotsikko-rivityyppi-sallitaan
  (is (empty? (simulaattori/virheet
                [{:komponentti :perusgrid
                  :asetukset {:sarakkeet [{:nimi :nimi}]
                              :rivit [{:rivityyppi :otsikko
                                       :teksti "Päättyneet urakat"}
                                      {:id 1 :nimi "Pohjois-Suomi"}]}}]))))

(deftest ryhmaotsikko-vaatii-tekstin
  (is (= ["perusgrid: :otsikko-riviltä puuttuu :teksti"]
         (simulaattori/virheet
           [{:komponentti :perusgrid
             :asetukset {:sarakkeet [{:nimi :nimi}]
                         :rivit [{:rivityyppi :otsikko}]}}]))))

(deftest tuntematon-rivityyppi-hylataan
  (is (= ["perusgrid: tuntematon rivityyppi :ei-ole"]
         (simulaattori/virheet
           [{:komponentti :perusgrid
             :asetukset {:sarakkeet [{:nimi :nimi}]
                         :rivit [{:id 1 :rivityyppi :ei-ole :nimi "Pohjois-Suomi"}]}}]))))

(deftest yhteenvetorivi-sallitaan
  (is (empty? (simulaattori/virheet
                [{:komponentti :perusgrid
                  :asetukset {:sarakkeet [{:nimi :nimi}]
                              :rivit [{:id :yhteensa
                                       :rivityyppi :yhteenveto
                                       :nimi "Yhteensä"}]}}]))))

(deftest sivutus-sallitaan-positiivisella-kokonaisluvulla
  (is (empty? (simulaattori/virheet
                [{:komponentti :perusgrid
                  :asetukset {:sarakkeet [{:nimi :nimi}]
                              :sivuta 2
                              :rivit []}}]))))

(deftest sivutus-hylataan-nollana
  (is (= ["perusgrid: :sivuta pitää olla positiivinen kokonaisluku"]
         (simulaattori/virheet
           [{:komponentti :perusgrid
             :asetukset {:sarakkeet [{:nimi :nimi}]
                         :sivuta 0
                         :rivit []}}]))))

(deftest funktio-solutyyppina-hylataan-kerran
  (is (= ["perusgrid: asetus :sarakkeet on funktio, ei sallittu"]
         (simulaattori/virheet
           (perusgrid-sarakkeella {:nimi :tila :solu (fn [] nil)})))))
