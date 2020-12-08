(ns harja.palvelin.raportointi.siltatarkastusraportti-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi.testiapurit :as apurit]))

(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
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
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest raportin-suoritus-sillalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :siltatarkastus
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                 :parametrit {:vuosi 2007
                                              :silta-id 1}})
        odotettu [:raportti {:orientaatio :landscape, :nimi "Siltatarkastusraportti"} [:taulukko {:otsikko "Siltatarkastusraportti, Oulun alueurakka 2005-2012, Oulujoen silta (O-00001), 2007", :tyhja "Sillalle ei ole tehty tarkastusta valittuna vuonna.", :viimeinen-rivi-yhteenveto? false, :sheet-nimi "Siltatarkastusraportti"} [{:leveys 2, :otsikko "#"} {:leveys 15, :otsikko "Kohde"} {:leveys 2, :otsikko "Tulos"} {:leveys 10, :otsikko "Lisätieto"} {:leveys 5, :otsikko "Liitteet"}] [{:otsikko "Aluerakenne", :virhe? false, :korosta? false} {:rivi [1 "Maatukien siisteys ja kunto" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [2 "Välitukien siisteys ja kunto" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [3 "Laakeritasojen siisteys ja kunto" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:otsikko "Päällysrakenne", :virhe? false, :korosta? false} {:rivi [4 "Kansilaatta" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [5 "Päällysteen kunto" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [6 "Reunapalkin siisteys ja kunto" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [7 "Reunapalkin liikuntasauma" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [8 "Reunapalkin ja päälllysteen välisen sauman siisteys ja kunto" "B" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [9 "Sillanpäiden saumat" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [10 "Sillan ja penkereen raja" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:otsikko "Varusteet ja laitteet", :virhe? false, :korosta? false} {:rivi [11 "Kaiteiden ja suojaverkkojen vauriot" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [12 "Liikuntasaumalaitteiden siisteys ja kunto" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [13 "Laakerit" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [14 "Syöksytorvet" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [15 "Tippuputket" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [16 "Kosketussuojat ja niiden kiinnitykset" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [17 "Valaistuslaitteet" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [18 "Johdot ja kaapelit" "D" nil [:liitteet []]], :virhe? true, :korosta? true} {:rivi [19 "Liikennemerkit" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:otsikko "Siltapaikan rakenteet", :virhe? false, :korosta? false} {:rivi [20 "Kuivatuslaitteiden siisteys ja kunto" "C" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [21 "Etuluiskien siisteys ja kunto" "B" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [22 "Keilojen siisteys ja kunto" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [23 "Tieluiskien siisteys ja kunto" "A" nil [:liitteet []]], :virhe? false, :korosta? false} {:rivi [24 "Portaiden siisteys ja kunto" "A" nil [:liitteet []]], :virhe? false, :korosta? false}]] [:yhteenveto [["Tarkastaja" "Sirkka Sillankoestaja"] ["Tarkastettu" "25.02.2007"]]] [:yhteenveto [["Siltoja urakassa" 7] ["Tarkastettu 2007" "2 (28,6%)"] ["Tarkastamatta 2007" "5 (71,4%)"]]]]]
    (is (= vastaus odotettu))
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Siltatarkastusraportti")
    (let [otsikko "Siltatarkastusraportti, Oulun alueurakka 2005-2012, Oulujoen silta (O-00001), 2007"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-otsikko taulukko otsikko)
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "#"}
                                          {:otsikko "Kohde"}
                                          {:otsikko "Tulos"}
                                          {:otsikko "Lisätieto"}
                                          {:otsikko "Liitteet"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko
                                             (fn [rivi]
                                               (let [[numero kohde tulos lisatieto [_ liitteet] :as rivi]
                                                     (if (map? rivi)
                                                       (:rivi rivi)
                                                       rivi)]
                                                 (if rivi
                                                   (and (= (count rivi) 5)
                                                        (number? numero)
                                                        (string? kohde)
                                                        (if lisatieto (string? lisatieto)
                                                                      true)
                                                        (vector? liitteet))
                                                   ;; väliotsikkoriveille palautetaan elsestä true
                                                   true)))))))

(deftest raportin-suoritus-urakan-kaikille-silloille-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :siltatarkastus
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                 :parametrit {:vuosi 2007
                                              :silta-id :kaikki}})
        odotettu [:raportti {:orientaatio :landscape, :nimi "Siltatarkastusraportti"} [:taulukko {:otsikko "Siltatarkastusraportti, Oulun alueurakka 2005-2012 vuodelta 2007", :tyhja "Sillalle ei ole tehty tarkastusta valittuna vuonna.", :viimeinen-rivi-yhteenveto? true, :sheet-nimi "Siltatarkastusraportti"} [{:leveys 5, :otsikko "Siltanumero"} {:leveys 10, :otsikko "Silta"} {:leveys 5, :otsikko "Tarkastettu", :fmt :pvm} {:leveys 5, :otsikko "Tarkastaja"} {:leveys 5, :otsikko "A"} {:leveys 5, :otsikko "B"} {:leveys 5, :otsikko "C"} {:leveys 5, :otsikko "D"} {:leveys 5, :otsikko "Liitteet"}] [{:rivi [0 "Tekaistu kuntasilta" [:varillinen-teksti {:arvo "Tarkastamatta", :tyyli :info}] "-" [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:liitteet []]], :virhe? false, :tarkastamaton? true, :korosta? false, :lihavoi? true} {:rivi [6666 "Joutsensilta" [:varillinen-teksti {:arvo "Tarkastamatta", :tyyli :info}] "-" [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:liitteet []]], :virhe? false, :tarkastamaton? true, :korosta? false, :lihavoi? true} {:rivi [7777 "Kajaanintien silta" [:varillinen-teksti {:arvo "Tarkastamatta", :tyyli :info}] "-" [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:liitteet []]], :virhe? false, :tarkastamaton? true, :korosta? false, :lihavoi? true} {:rivi [7778 "Pyhäjoen silta" [:varillinen-teksti {:arvo "Tarkastamatta", :tyyli :info}] "-" [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:liitteet []]], :virhe? false, :tarkastamaton? true, :korosta? false, :lihavoi? true} {:rivi [325235 "Kempeleen testisilta" [:varillinen-teksti {:arvo "Tarkastamatta", :tyyli :info}] "-" [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:arvo-ja-osuus {:arvo 0, :osuus 0}] [:liitteet []]], :virhe? false, :tarkastamaton? true, :korosta? false, :lihavoi? true} {:rivi [902 "Pyhäjoen silta" #inst "2007-05-04T21:00:00.000000000-00:00" "Mari Mittatarkka" [:arvo-ja-osuus {:arvo 20, :osuus 83}] [:arvo-ja-osuus {:arvo 2, :osuus 8}] [:arvo-ja-osuus {:arvo 1, :osuus 4}] [:arvo-ja-osuus {:arvo 1, :osuus 4}] [:liitteet []]], :virhe? true, :tarkastamaton? false, :korosta? true, :lihavoi? false} {:rivi [1537 "Oulujoen silta" #inst "2007-02-24T22:00:00.000000000-00:00" "Sirkka Sillankoestaja" [:arvo-ja-osuus {:arvo 20, :osuus 83}] [:arvo-ja-osuus {:arvo 2, :osuus 8}] [:arvo-ja-osuus {:arvo 1, :osuus 4}] [:arvo-ja-osuus {:arvo 1, :osuus 4}] [:liitteet []]], :virhe? true, :tarkastamaton? false, :korosta? true, :lihavoi? false} ["Yhteensä" nil nil nil [:arvo-ja-osuus {:arvo 40, :osuus 83}] [:arvo-ja-osuus {:arvo 4, :osuus 8}] [:arvo-ja-osuus {:arvo 2, :osuus 4}] [:arvo-ja-osuus {:arvo 2, :osuus 4}] [:liitteet nil]]]] nil [:yhteenveto [["Siltoja urakassa" 7] ["Tarkastettu 2007" "2 (28,6%)"] ["Tarkastamatta 2007" "5 (71,4%)"]]]]]
    (is (= odotettu vastaus))
    (is (vector? vastaus))
    (is (= 5 (count vastaus)))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :siltatarkastus
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:vuosi 2007}})]
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Siltatarkastusraportti")
    (let [otsikko "Siltatarkastusraportti, Pohjois-Pohjanmaa 2007"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-otsikko taulukko otsikko)
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Urakka"}
                                          {:otsikko "A"}
                                          {:otsikko "B"}
                                          {:otsikko "C"}
                                          {:otsikko "D"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko
                                             (fn [rivi]
                                               (let [[yhteensa a b c d :as rivi]
                                                     (if (map? rivi)
                                                       (:rivi rivi)
                                                       rivi)]
                                                 (and (= (count rivi) 5)
                                                      (string? yhteensa)
                                                      (keyword? (first a))
                                                      (keyword? (first b))
                                                      (keyword? (first c))
                                                      (keyword? (first d))
                                                      (map? (second a))
                                                      (map? (second b))
                                                      (map? (second c))
                                                      (map? (second d)))))))))
