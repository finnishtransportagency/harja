(ns harja.tiedot.kanavat.hallinta.kohteiden-luonti-test
  (:require [harja.tiedot.kanavat.hallinta.kohteiden-luonti :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]

            [harja.domain.kanavat.kohdekokonaisuus :as kok]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.urakka :as ur]))

(deftest kohderivit
  (is (= [{::kohde/id 1
           ::kohde/tyyppi :sulku
           ::kok/id 1
           ::kok/nimi "Foobar"}
          {::kohde/id 2
           ::kohde/tyyppi :silta
           ::kohde/nimi "komea silta"
           ::kok/id 1
           ::kok/nimi "Foobar"}
          {::kohde/id 3
           ::kohde/tyyppi :sulku
           ::kok/id 2
           ::kok/nimi "Bazbar"}]
         (tiedot/kohderivit
           [{::kok/id 1
             ::kok/nimi "Foobar"
             ::kok/kohteet [{::kohde/id 1
                             ::kohde/tyyppi :sulku}
                            {::kohde/id 2
                             ::kohde/tyyppi :silta
                             ::kohde/nimi "komea silta"}]}
            {::kok/id 2
             ::kok/nimi "Bazbar"
             ::kok/kohteet [{::kohde/id 3
                             ::kohde/tyyppi :sulku}]}]))))

(deftest kanavat
  (is (= [{::kok/id 1
           ::kok/nimi "Foobar"}
          {::kok/id 2
           ::kok/nimi "Bazbar"}]
         (tiedot/kohdekokonaisuudet
           [{::kok/id 1
             ::kok/nimi "Foobar"
             :huahuhue "joo"
             ::kok/kohteet [{::kohde/id 1
                             ::kohde/tyyppi :sulku}
                            {::kohde/id 2
                             ::kohde/tyyppi :silta
                             ::kohde/nimi "komea silta"}]}
            {::kok/id 2
             ::kok/nimi "Bazbar"
             :huahuhue "joo"
             ::kok/kohteet [{::kohde/id 3
                             ::kohde/tyyppi :sulku}]}]))))

(deftest tallennusparametrit
  (is (= [{::kohde/nimi :foo
           ::kohde/id 1
           ::kohde/kanava-id 1
           ::kohde/tyyppi :sulku
           ::m/poistettu? true}
          {::kohde/nimi :foo
           ::kohde/id 2
           ::kohde/kanava-id 1
           ::kohde/tyyppi :sulku}]
         (tiedot/tallennusparametrit-kohde
           {:kanava {::kok/id 1}
            :kohteet [{::kohde/nimi :foo
                       :id 1
                       ::kohde/kanava-id 1
                       ::kohde/tyyppi :sulku
                       :poistettu true}
                      {::kohde/nimi :foo
                       :id 2
                       ::kohde/kanava-id 1
                       ::kohde/tyyppi :sulku}]}))))

(deftest kohteen-urakat
  (is (= "A, B, C"
         (tiedot/kohteen-urakat
           {::kohde/urakat [{::ur/nimi "B"}
                            {::ur/nimi "C"}
                            {::ur/nimi "A"}]}))))

(deftest kohteen-kuuluminen-urakkaan
  (is (true?
        (tiedot/kohde-kuuluu-urakkaan?
          {:uudet-urakkaliitokset {}}
          {::kohde/urakat [{::ur/id 1}]}
          {::ur/id 1})))

  (is (true?
        (tiedot/kohde-kuuluu-urakkaan?
          {:uudet-urakkaliitokset {[666 1] true}}
          {::kohde/id 666
           ::kohde/urakat []}
          {::ur/id 1})))

  (is (false?
        (tiedot/kohde-kuuluu-urakkaan?
          {:uudet-urakkaliitokset {[666 1] false}}
          {::kohde/id 666
           ::kohde/urakat []}
          {::ur/id 1})))

  (is (false?
        (tiedot/kohde-kuuluu-urakkaan?
          {:uudet-urakkaliitokset {}}
          {::kohde/urakat [{::ur/id 1}]}
          {::ur/id 2})))

  (is (false?
        (tiedot/kohde-kuuluu-urakkaan?
          {:uudet-urakkaliitokset {}}
          {::kohde/urakat []}
          {::ur/id 1}))))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))

(deftest kohteiden-hakeminen
  (vaadi-async-kutsut
    #{tiedot/->KohteetHaettu tiedot/->KohteetEiHaettu}
    (is (true? (:kohteiden-haku-kaynnissa? (e! (tiedot/->HaeKohteet))))))

  (vaadi-async-kutsut
    #{}
    (is (= {:kohteiden-haku-kaynnissa? true}
           (e! (tiedot/->HaeKohteet) {:kohteiden-haku-kaynnissa? true})))))

(deftest kohteet-haettu
  (is (= {:kohteiden-haku-kaynnissa? false
          :kanavat [{::kok/id 1
                     ::kok/nimi "Foobar"}
                    {::kok/id 2
                     ::kok/nimi "Bazbar"}]
          :kohderivit [{::kohde/id 1
                        ::kohde/tyyppi :sulku
                        ::kok/id 1
                        ::kok/nimi "Foobar"}
                       {::kohde/id 2
                        ::kohde/tyyppi :silta
                        ::kohde/nimi "komea silta"
                        ::kok/id 1
                        ::kok/nimi "Foobar"}
                       {::kohde/id 3
                        ::kohde/tyyppi :sulku
                        ::kok/id 2
                        ::kok/nimi "Bazbar"}]}
         (e! (tiedot/->KohteetHaettu [{::kok/id 1
                                       ::kok/nimi "Foobar"
                                       ::kok/kohteet [{::kohde/id 1
                                                       ::kohde/tyyppi :sulku}
                                                      {::kohde/id 2
                                                       ::kohde/tyyppi :silta
                                                       ::kohde/nimi "komea silta"}]}
                                      {::kok/id 2
                                       ::kok/nimi "Bazbar"
                                       ::kok/kohteet [{::kohde/id 3
                                                       ::kohde/tyyppi :sulku}]}])))))

(deftest kohteet-ei-haettu
  (is (= {:kohteiden-haku-kaynnissa? false}
         (e! (tiedot/->KohteetEiHaettu {})))))

(deftest aloita-haku
  (vaadi-async-kutsut
    #{tiedot/->UrakatHaettu tiedot/->UrakatEiHaettu}

    (is (= {:urakoiden-haku-kaynnissa? true}
           (e! (tiedot/->AloitaUrakoidenHaku))))))

(deftest urakat-haettu
  (is (= {:urakoiden-haku-kaynnissa? false
          :urakat [{::ur/nimi "Foo" ::ur/id 1}
                   {::ur/nimi "Bar" ::ur/id 2}]}
         (e! (tiedot/->UrakatHaettu
               [{:id 1 :nimi "Foo"}
                {:id 2 :nimi "Bar"}])))))

(deftest urakat-ei-haettu
  (is (= {:urakoiden-haku-kaynnissa? false}
         (e! (tiedot/->UrakatEiHaettu :virhe)))))

(deftest urakan-valinta
  (is (= {:valittu-urakka {:foo :bar}}
         (e! (tiedot/->ValitseUrakka {:foo :bar})))))

(deftest kohteiden-liittaminen-urakkaan
  (vaadi-async-kutsut
    #{tiedot/->LiitoksetPaivitetty tiedot/->LiitoksetEiPaivitetty}
    (is (= (e! (tiedot/->PaivitaKohteidenUrakkaliitokset)
               {:uudet-urakkaliitokset {[1 2] true}})
           {:uudet-urakkaliitokset {[1 2] true}
            :liittaminen-kaynnissa? true}))))

(deftest kohde-liitetty
  (is (= (e! (tiedot/->LiitoksetPaivitetty [])
             {:liittaminen-kaynnissa? true
              :uudet-urakkaliitokset {[1 2] true}})
         {:liittaminen-kaynnissa? false
          :uudet-urakkaliitokset {}
          :kohderivit []})))

(deftest kohde-ei-liitetty
  (is (= (e! (tiedot/->LiitoksetEiPaivitetty)
             {:liittaminen-kaynnissa? true})
         {:liittaminen-kaynnissa? false})))