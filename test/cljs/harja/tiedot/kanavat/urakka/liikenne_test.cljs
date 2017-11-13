(ns harja.tiedot.kanavat.urakka.liikenne-test
  (:require [harja.tiedot.kanavat.urakka.liikenne :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]

            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.kanava :as kanava]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.domain.sopimus :as sop]
            [harja.domain.urakka :as ur]))


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
         (tiedot/palvelumuoto->str {::lt/palvelumuoto :itse
                                    ::lt/palvelumuoto-lkm 15})))

  (is (= "Kauko"
         (tiedot/palvelumuoto->str {::lt/palvelumuoto :kauko
                                    ::lt/palvelumuoto-lkm 1}))))

(deftest tapahtumarivit
  (testing "Jokaisesta nipusta ja aluksesta syntyy oma rivi"
    (let [tapahtuma {::lt/kohde {::kohde/kohteen-kanava {::kanava/nimi "Saimaa"}
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
    (let [tapahtuma {::lt/kohde {::kohde/kohteen-kanava {::kanava/nimi "Saimaa"}
                                 ::kohde/nimi "Iso mutka"
                                 ::kohde/tyyppi :silta}
                     ::lt/alukset []
                     ::lt/niput []}]
      (is (= [(merge
                tapahtuma
                {:kohteen-nimi "Saimaa, Iso mutka, silta"})]
             (tiedot/tapahtumarivit tapahtuma))))))

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
  (let [tapahtuma1 {::lt/kohde {::kohde/kohteen-kanava {::kanava/nimi "Saimaa"}
                                ::kohde/nimi "Iso mutka"
                                ::kohde/tyyppi :silta}
                    ::lt/alukset []
                    ::lt/niput []}
        tapahtuma2 {::lt/kohde {::kohde/kohteen-kanava {::kanava/nimi "Saimaa"}
                                ::kohde/nimi "Iso mutka"
                                ::kohde/tyyppi :silta}
                    ::lt/alukset [{::lt-alus/suunta :ylos
                                   ::lt-alus/nimi "Ronsu"}]}]
    (is (= {:liikennetapahtumien-haku-kaynnissa? false
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
  (is (= {:valittu-liikennetapahtuma 1}
         (e! (tiedot/->ValitseTapahtuma 1)))))

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

(deftest kohteiden-haku
  (vaadi-async-kutsut
    #{tiedot/->KohteetHaettu tiedot/->KohteetEiHaettu}
    (is (= {:kohteiden-haku-kaynnissa? true}
           (e! (tiedot/->HaeKohteet)))))

  (vaadi-async-kutsut
    #{}
    (is (= {:kohteiden-haku-kaynnissa? true}
           (e! (tiedot/->HaeKohteet)
               {:kohteiden-haku-kaynnissa? true})))))

(deftest kohteet-haettu
  (is (= {:urakan-kohteet [1 2 3]
          :kohteiden-haku-kaynnissa? false}
         (e! (tiedot/->KohteetHaettu [1 2 3])))))

(deftest kohteet-ei-haettu
  (is (= {:kohteiden-haku-kaynnissa? false}
         (e! (tiedot/->KohteetEiHaettu {})))))