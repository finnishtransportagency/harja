(ns harja.tiedot.kanavat.urakka.liikenne-test
  (:require [harja.tiedot.kanavat.urakka.liikenne :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]

            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.kohdekokonaisuus :as kok]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.sopimus :as sop]
            [harja.domain.urakka :as ur]
            [harja.domain.kayttaja :as kayttaja]
            [harja.ui.modal :as modal]))

(deftest uusi-tapahtuma
  (is (= {::lt/kuittaaja {::kayttaja/id 1}
          ::lt/aika 1234
          ::lt/sopimus {::sop/id :a ::sop/nimi :b}
          ::lt/urakka {::ur/id :foo}}
         (tiedot/uusi-tapahtuma
           (atom {:id 1})
           (atom [:a :b])
           (atom {:id :foo})
           1234))))

(deftest hakuparametrit
  (testing "Jos urakka-id ja sopimus-id ei ole asetettu, palautetaan nil"
    (is (= nil
           (tiedot/hakuparametrit {:valinnat {:foo nil :bar 1}}))))

  (is (= {::ur/id 1 ::sop/id 1}
         (tiedot/hakuparametrit {:valinnat {::ur/id 1 ::sop/id 1}})))

  (is (= {::ur/id 1
          ::sop/id 1
          :bar 2}
         (tiedot/hakuparametrit {:valinnat {::ur/id 1
                                            ::sop/id 1
                                            :foo nil
                                            :bar 2}}))))

(deftest palvelumuoto-gridiin
  (is (= "Itsepalvelu (15 kpl)"
         (tiedot/palvelumuoto->str {::lt/sulku-palvelumuoto :itse
                                    ::lt/sulku-lkm 15})))

  (is (= "Kauko"
         (tiedot/palvelumuoto->str {::lt/sulku-palvelumuoto :kauko
                                    ::lt/sulku-lkm 1})))

  (is (= "Paikallis"
         (tiedot/palvelumuoto->str {::lt/silta-palvelumuoto :paikallis
                                    ::lt/silta-lkm 1})))

  (is (= "Paikallis (sulku), paikallis (silta)"
         (tiedot/palvelumuoto->str {::lt/silta-palvelumuoto :paikallis
                                    ::lt/silta-lkm 1
                                    ::lt/sulku-palvelumuoto :paikallis
                                    ::lt/sulku-lkm 1})))

  (is (= "Itsepalvelu (15 kpl) (sulku), itsepalvelu (15 kpl) (silta)"
         (tiedot/palvelumuoto->str {::lt/sulku-palvelumuoto :itse
                                    ::lt/sulku-lkm 15
                                    ::lt/silta-palvelumuoto :itse
                                    ::lt/silta-lkm 15}))))

(deftest toimenpide-gridiin
  (is (= "Sulutus, sillan avaus"
         (tiedot/toimenpide->str {::lt/sulku-toimenpide :sulutus
                                  ::lt/silta-avaus true})))

  (is (= "Tyhjennys"
         (tiedot/toimenpide->str {::lt/sulku-toimenpide :tyhjennys
                                  ::lt/silta-avaus false})))

  (is (= "Sillan avaus"
         (tiedot/toimenpide->str {::lt/silta-avaus true})))

  (is (= ""
         (tiedot/toimenpide->str {::lt/silta-avaus false}))))

(deftest tapahtumarivit
  (testing "Jokaisesta nipusta ja aluksesta syntyy oma rivi"
    (let [tapahtuma {::lt/kohde {::kohde/kohdekokonaisuus {::kok/nimi "Saimaa"}
                                 ::kohde/nimi "Iso mutka"
                                 ::kohde/tyyppi :silta}
                     ::lt/alukset [{::lt-alus/suunta :ylos
                                    ::lt-alus/nimi "Ronsu"}]}]
      (is (= [(merge
                tapahtuma
                {::lt-alus/suunta :ylos
                 ::lt-alus/nimi "Ronsu"}
                {:kohteen-nimi "Saimaa, Iso mutka, silta"
                 :suunta :ylos})]
             (tiedot/tapahtumarivit tapahtuma)))))

  (testing "Jos ei aluksia tai nippuja, syntyy silti yksi rivi taulukossa"
    (let [tapahtuma {::lt/kohde {::kohde/kohdekokonaisuus {::kok/nimi "Saimaa"}
                                 ::kohde/nimi "Iso mutka"
                                 ::kohde/tyyppi :silta}
                     ::lt/alukset []
                     ::lt/niput []}]
      (is (= [(merge
                tapahtuma
                {:kohteen-nimi "Saimaa, Iso mutka, silta"})]
             (tiedot/tapahtumarivit tapahtuma))))))

(deftest koko-tapahtuma
  (is (= {::lt/id 1 :foo :baz}
         (tiedot/koko-tapahtuma {::lt/id 1}
                                {:haetut-tapahtumat [{::lt/id 2 :foo :bar}
                                                     {::lt/id 1 :foo :baz}]}))))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))

(deftest liikennetapahtumien-hakeminen
  (vaadi-async-kutsut
    #{tiedot/->LiikennetapahtumatHaettu tiedot/->LiikennetapahtumatEiHaettu}
    (is (= {:liikennetapahtumien-haku-kaynnissa? true
            :valinnat {::ur/id 1 ::sop/id 1}}
           (e! (tiedot/->HaeLiikennetapahtumat)
               {:valinnat {::ur/id 1 ::sop/id 1}}))))

  (testing "Uusi haku ei lähde jos haku on jo käynnissä"
    (vaadi-async-kutsut
      #{}
      (is (= {:liikennetapahtumien-haku-kaynnissa? true
              :valinnat {::ur/id 1 ::sop/id 1}}
             (e! (tiedot/->HaeLiikennetapahtumat)
                 {:liikennetapahtumien-haku-kaynnissa? true
                  :valinnat {::ur/id 1 ::sop/id 1}})))))

  (testing "Haku ei lähde jos sopimus-id tai urakka-id puuttuu"
    (vaadi-async-kutsut
      #{}
      (is (= {:liikennetapahtumien-haku-kaynnissa? false
              :valinnat {::ur/id nil ::sop/id 1}}
             (e! (tiedot/->HaeLiikennetapahtumat)
                 {:liikennetapahtumien-haku-kaynnissa? false
                  :valinnat {::ur/id nil ::sop/id 1}}))))

    (vaadi-async-kutsut
      #{}
      (is (= {:liikennetapahtumien-haku-kaynnissa? false
              :valinnat {::ur/id 1 ::sop/id nil}}
             (e! (tiedot/->HaeLiikennetapahtumat)
                 {:liikennetapahtumien-haku-kaynnissa? false
                  :valinnat {::ur/id 1 ::sop/id nil}}))))))

(deftest tapahtumat-haettu
  (let [tapahtuma1 {::lt/kohde {::kohde/kohdekokonaisuus {::kok/nimi "Saimaa"}
                                ::kohde/nimi "Iso mutka"
                                ::kohde/tyyppi :silta}
                    ::lt/alukset []
                    ::lt/niput []}
        tapahtuma2 {::lt/kohde {::kohde/kohdekokonaisuus {::kok/nimi "Saimaa"}
                                ::kohde/nimi "Iso mutka"
                                ::kohde/tyyppi :silta}
                    ::lt/alukset [{::lt-alus/suunta :ylos
                                   ::lt-alus/nimi "Ronsu"}]}]
    (is (= {:liikennetapahtumien-haku-kaynnissa? false
            :haetut-tapahtumat [tapahtuma1 tapahtuma2]
            :tapahtumarivit [(merge
                               tapahtuma1
                               {:kohteen-nimi "Saimaa, Iso mutka, silta"})
                             (merge
                               tapahtuma2
                               {::lt-alus/suunta :ylos
                                ::lt-alus/nimi "Ronsu"}
                               {:kohteen-nimi "Saimaa, Iso mutka, silta"
                                :suunta :ylos})]}
           (e! (tiedot/->LiikennetapahtumatHaettu [tapahtuma1 tapahtuma2]))))))

(deftest tapahtumia-ei-haettu
  (is (= {:liikennetapahtumien-haku-kaynnissa? false}
         (e! (tiedot/->LiikennetapahtumatEiHaettu {})))))

(deftest tapahtuman-valitseminen
  (is (= {:valittu-liikennetapahtuma {::lt/id 1 :foo :bar}
          :haetut-tapahtumat [{::lt/id 1 :foo :bar}]}
         (e! (tiedot/->ValitseTapahtuma {::lt/id 1})
             {:haetut-tapahtumat [{::lt/id 1 :foo :bar}]})))

  (is (= {:valittu-liikennetapahtuma {:foo :bar}
          :haetut-tapahtumat [{::lt/id 1 :foo :bar}]}
         (e! (tiedot/->ValitseTapahtuma {:foo :bar})
             {:haetut-tapahtumat [{::lt/id 1 :foo :bar}]}))))

(deftest edellisten-haku
  (vaadi-async-kutsut
    #{tiedot/->EdellisetTiedotHaettu tiedot/->EdellisetTiedotEiHaettu}
    (is (= {:edellisten-haku-kaynnissa? true}
           (e! (tiedot/->HaeEdellisetTiedot {}))))))

(deftest edelliset-haettu
  (is (= {:edelliset {:tama {::lt/vesipinta-alaraja 1
                             ::lt/vesipinta-ylaraja 2}
                      :ylos {:foo :bar}
                      :alas {:baz :baz}}
          :edellisten-haku-kaynnissa? false
          :valittu-liikennetapahtuma {::lt/vesipinta-alaraja 1
                                      ::lt/vesipinta-ylaraja 2}}
         (e! (tiedot/->EdellisetTiedotHaettu {:kohde {::lt/vesipinta-alaraja 1
                                                      ::lt/vesipinta-ylaraja 2}
                                              :ylos {:foo :bar}
                                              :alas {:baz :baz}})))))

(deftest edelliset-ei-haettu
  (is (= {:edellisten-haku-kaynnissa? false}
         (e! (tiedot/->EdellisetTiedotEiHaettu {})))))

(deftest suodatinten-päivittäminen
  (testing "Vanhoja tietoja ei ylikirjoiteta"
    (vaadi-async-kutsut
      #{tiedot/->HaeLiikennetapahtumat}
      (is (= {:valinnat {:foo 1}}
             (e! (tiedot/->PaivitaValinnat {})
                 {:valinnat {:foo 1}})))))

  (testing "Ainoastaan tietyt avaimet parametrista valitaan"
    (vaadi-async-kutsut
      #{tiedot/->HaeLiikennetapahtumat}
      (is (= {:valinnat {:foo 1
                         :aikavali 1}}
             (e! (tiedot/->PaivitaValinnat {:bar 2
                                            :aikavali 1})
                 {:valinnat {:foo 1}})))))

  (testing "Parametrien ylikirjoitus"
    (vaadi-async-kutsut
      #{tiedot/->HaeLiikennetapahtumat}
      (is (= {:valinnat {:aikavali 3}}
             (e! (tiedot/->PaivitaValinnat {:aikavali 3})
                 {:valinnat {:aikavali 1}}))))))

(deftest tapahtuman-muokkaus
  (is (= {:valittu-liikennetapahtuma {:foo :bar}}
         (e! (tiedot/->TapahtumaaMuokattu {:foo :bar})))))

(deftest alusten-muokkaus
  (is (= {:valittu-liikennetapahtuma {::lt/alukset [{:foo :bar}]
                                      :grid-virheita? false}}
         (e! (tiedot/->MuokkaaAluksia [{:foo :bar}] false)
             {:valittu-liikennetapahtuma {}})))

  (is (= {:valittu-liikennetapahtuma nil}
         (e! (tiedot/->MuokkaaAluksia [{:foo :bar}] true)
             {:valittu-liikennetapahtuma nil}))))

(deftest suunnan-vaihto
  (is (= {:valittu-liikennetapahtuma {::lt/alukset [{::lt-alus/id 1 ::lt-alus/suunta :ylos}
                                                    {::lt-alus/id 2 ::lt-alus/suunta :alas}]}}
         (e! (tiedot/->VaihdaSuuntaa {::lt-alus/id 1 ::lt-alus/suunta :alas})
             {:valittu-liikennetapahtuma {::lt/alukset [{::lt-alus/id 1 ::lt-alus/suunta :alas}
                                                        {::lt-alus/id 2 ::lt-alus/suunta :alas}]}})))
  (is (= {:valittu-liikennetapahtuma {::lt/alukset [{::lt-alus/id 1 ::lt-alus/suunta :alas}
                                                    {::lt-alus/id 2 ::lt-alus/suunta :alas}]}}
         (e! (tiedot/->VaihdaSuuntaa {::lt-alus/id 1 ::lt-alus/suunta :ylos})
             {:valittu-liikennetapahtuma {::lt/alukset [{::lt-alus/id 1 ::lt-alus/suunta :ylos}
                                                        {::lt-alus/id 2 ::lt-alus/suunta :alas}]}})))

  (is (= {:valittu-liikennetapahtuma {::lt/alukset [{:id -1 ::lt-alus/suunta :ylos}
                                                    {:id -2 ::lt-alus/suunta :alas}]}}
         (e! (tiedot/->VaihdaSuuntaa {:id -1 ::lt-alus/suunta :alas})
             {:valittu-liikennetapahtuma {::lt/alukset [{:id -1 ::lt-alus/suunta :alas}
                                                        {:id -2 ::lt-alus/suunta :alas}]}})))
  (is (= {:valittu-liikennetapahtuma {::lt/alukset [{:id -1 ::lt-alus/suunta :alas}
                                                    {:id -2 ::lt-alus/suunta :alas}]}}
         (e! (tiedot/->VaihdaSuuntaa {:id -1 ::lt-alus/suunta :ylos})
             {:valittu-liikennetapahtuma {::lt/alukset [{:id -1 ::lt-alus/suunta :ylos}
                                                        {:id -2 ::lt-alus/suunta :alas}]}}))))

(deftest tallennus
  (vaadi-async-kutsut
    #{tiedot/->TapahtumaTallennettu tiedot/->TapahtumaEiTallennettu}
    (is (= {:tallennus-kaynnissa? true}
           (e! (tiedot/->TallennaLiikennetapahtuma {})
               {:tallennus-kaynnissa? false}))))

  (vaadi-async-kutsut
    #{}
    (is (= {:tallennus-kaynnissa? true}
           (e! (tiedot/->TallennaLiikennetapahtuma {})
               {:tallennus-kaynnissa? true})))))

(deftest tallennus-valmis
  (swap! modal/modal-sisalto assoc :nakyvissa? true)

  (let [tapahtuma1 {::lt/kohde {::kohde/kohdekokonaisuus {::kok/nimi "Saimaa"}
                                ::kohde/nimi "Iso mutka"
                                ::kohde/tyyppi :silta}
                    ::lt/alukset []
                    ::lt/niput []}
        tapahtuma2 {::lt/kohde {::kohde/kohdekokonaisuus {::kok/nimi "Saimaa"}
                                ::kohde/nimi "Iso mutka"
                                ::kohde/tyyppi :silta}
                    ::lt/alukset [{::lt-alus/suunta :ylos
                                   ::lt-alus/nimi "Ronsu"}]}]
    (is (= {:tallennus-kaynnissa? false
            :valittu-liikennetapahtuma nil
            :liikennetapahtumien-haku-kaynnissa? false
            :haetut-tapahtumat [tapahtuma1 tapahtuma2]
            :tapahtumarivit [(merge
                               tapahtuma1
                               {:kohteen-nimi "Saimaa, Iso mutka, silta"})
                             (merge
                               tapahtuma2
                               {::lt-alus/suunta :ylos
                                ::lt-alus/nimi "Ronsu"}
                               {:kohteen-nimi "Saimaa, Iso mutka, silta"
                                :suunta :ylos})]}
           (e! (tiedot/->TapahtumaTallennettu [tapahtuma1 tapahtuma2])))))

  (is (false? (:nakyvissa? @modal/modal-sisalto))))

(deftest ei-tallennettu
  (is (= {:tallennus-kaynnissa? false}
         (e! (tiedot/->TapahtumaEiTallennettu {})))))