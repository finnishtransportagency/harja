(ns harja.tiedot.kanavat.hallinta.kohteiden-luonti-test
  (:require [harja.tiedot.kanavat.hallinta.kohteiden-luonti :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]

            [harja.domain.kanavat.kohdekokonaisuus :as kok]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.kanavat.kohteenosa :as osa]
            [harja.domain.urakka :as ur]))

(deftest kohderivit
  (is (= [{::kohde/id 1
           ::kohde/tyyppi :sulku
           ::kohde/kohdekokonaisuus {::kok/id 1
                                     ::kok/nimi "Foobar"}}
          {::kohde/id 2
           ::kohde/tyyppi :silta
           ::kohde/nimi "komea silta"
           ::kohde/kohdekokonaisuus {::kok/id 1
                                     ::kok/nimi "Foobar"}}
          {::kohde/id 3
           ::kohde/tyyppi :sulku
           ::kohde/kohdekokonaisuus {::kok/id 2
                                     ::kok/nimi "Bazbar"}}]
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

(deftest ryhmittele-kanavalla
  (is (= ["Bar"
          {::kohde/kohdekokonaisuus {::kok/nimi "Bar"
                                     ::kok/id 2}
           ::kohde/nimi "Kolmas"}
          "Foobar"
          {::kohde/kohdekokonaisuus {::kok/nimi "Foobar"
                                              ::kok/id 1}
                    ::kohde/nimi "Eka"}
          {::kohde/kohdekokonaisuus {::kok/nimi "Foobar"
                                     ::kok/id 1}
           ::kohde/nimi "Toka"}]
         (tiedot/ryhmittele-kohderivit-kanavalla [{::kohde/kohdekokonaisuus {::kok/nimi "Foobar"
                                                                             ::kok/id 1}
                                                   ::kohde/nimi "Eka"}
                                                  {::kohde/kohdekokonaisuus {::kok/nimi "Foobar"
                                                                             ::kok/id 1}
                                                   ::kohde/nimi "Toka"}
                                                  {::kohde/kohdekokonaisuus {::kok/nimi "Bar"
                                                                             ::kok/id 2}
                                                   ::kohde/nimi "Kolmas"}]
                                                 identity))))

(deftest kohdekokonaisuudet
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

(deftest voiko-kohdekokonaisuudet-tallentaa?
  (is (true? (tiedot/kohdekokonaisuudet-voi-tallentaa? [{::kok/nimi "F"}
                                                        {::kok/nimi "A"}])))
  (is (true? (tiedot/kohdekokonaisuudet-voi-tallentaa? [{::kok/nimi "F"}
                                                        {::kok/nimi "A"}
                                                        {::kok/nimi ""
                                                         ::kok/id -1
                                                         :poistettu true}])))
  (is (true? (tiedot/kohdekokonaisuudet-voi-tallentaa? [{::kok/nimi "F"}
                                                        {::kok/nimi "A"}
                                                        {::kok/nimi ""
                                                         ::kok/id 1
                                                         :poistettu true}])))

  (is (false? (tiedot/kohdekokonaisuudet-voi-tallentaa? [{::kok/nimi "F"}
                                                        {::kok/nimi "A"}
                                                        {::kok/nimi ""
                                                         ::kok/id -1}])))
  (is (false? (tiedot/kohdekokonaisuudet-voi-tallentaa? [{::kok/nimi "F"}
                                                         {::kok/nimi "A"}
                                                         {::kok/nimi ""
                                                          ::kok/id 1}]))))

(deftest voiko-kohteen-tallentaa?
  (is (true? (tiedot/kohteen-voi-tallentaa? {::kohde/kohdekokonaisuus {:id 1}
                                             ::kohde/nimi "Fo"
                                             ::kohde/kohteenosat [{:id 1}]})))

  (is (false? (tiedot/kohteen-voi-tallentaa? {::kohde/kohdekokonaisuus nil
                                             ::kohde/nimi "Fo"
                                             ::kohde/kohteenosat [{:id 1}]})))
  (is (false? (tiedot/kohteen-voi-tallentaa? {::kohde/kohdekokonaisuus {:id 1}
                                             ::kohde/nimi nil
                                             ::kohde/kohteenosat [{:id 1}]})))
  (is (false? (tiedot/kohteen-voi-tallentaa? {::kohde/kohdekokonaisuus {:id 1}
                                             ::kohde/nimi "Fo"
                                             ::kohde/kohteenosat []}))))

(deftest kokonaisuuden-tallennusparametrit
  (is (= [{::kok/id -1
           ::kok/nimi "Uusi"}
          {::kok/id 10
           ::m/poistettu? true}]
         (tiedot/kohdekokonaisuudet-tallennusparametrit
           [{:koskematon true}
            {:id -1
             ::kok/nimi "Uusi"}
            {::kok/id 10
             :poistettu true}]))))

(deftest tallennusparametrit
  (is (= {::kohde/kohdekokonaisuus-id 1
          ::kohde/kohteenosat [{::m/poistettu? true}]}
         (tiedot/tallennusparametrit-kohde
           {::kohde/kohdekokonaisuus {::kok/id 1}
            ::kohde/urakat []
            ::kohde/kohteenosat [{:poistettu true
                                  ::osa/sijainti {}
                                  :vanha-kohde {}
                                  :sijainti 1
                                  :type 1
                                  :nimi 1
                                  :selite 1
                                  :alue 1
                                  :tyyppi-kartalla 1}]}))))

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

(deftest kohteiden-lkm
  (is (= 2
         (tiedot/kohteiden-lkm-kokonaisuudessa {:kohderivit [{::kohde/kohdekokonaisuus {::kok/id 1}}
                                                             {::kohde/kohdekokonaisuus {::kok/id 1}}
                                                             {::kohde/kohdekokonaisuus {::kok/id 2}}]}
                                               {::kok/id 1}))))

(deftest osa-kuuluu-kohteeseen?
  (is (true? (tiedot/osa-kuuluu-valittuun-kohteeseen? {::osa/kohde {::kohde/id 1}
                                                       ::osa/id 1}
                                                      {:valittu-kohde {::kohde/id 1
                                                                       ::kohde/kohteenosat [{::osa/id 1}]}})))

  (testing "Tarkastus tehdään osan id:n perusteella, ei osan kohdetiedon perusteella"
    (is (false? (tiedot/osa-kuuluu-valittuun-kohteeseen? {::osa/kohde {::kohde/id 1}
                                                          ::osa/id 2}
                                                         {:valittu-kohde {::kohde/id 1
                                                                          ::kohde/kohteenosat [{::osa/id 1}]}})))))

(deftest infopaneeli-otsikko
  (is (= "Irroita"
         (tiedot/kohteenosan-infopaneeli-otsikko {:valittu-kohde {::kohde/id 1}}
                                                 {::osa/kohde {::kohde/id 1}})))

  (is (= "Irroita kohteesta Foobar & Liitä"
         (tiedot/kohteenosan-infopaneeli-otsikko {:valittu-kohde {::kohde/id 1}}
                                                 {::osa/kohde {::kohde/id 2
                                                               ::kohde/nimi "Foobar"}})))

  (is (= "Liitä kohteeseen"
         (tiedot/kohteenosan-infopaneeli-otsikko {:valittu-kohde {::kohde/id 1}}
                                                 {::osa/kohde nil}))))

(deftest nakymaan-tuleminen
  (is (= {:nakymassa? true
          :uudet-urakkaliitokset [1]
          :karttataso-nakyvissa? false}
         (e! (tiedot/->Nakymassa? true)
             {:uudet-urakkaliitokset [1]})))

  (is (= {:nakymassa? true
          :uudet-urakkaliitokset [1]
          :karttataso-nakyvissa? true
          :valittu-kohde {:id 1}}
         (e! (tiedot/->Nakymassa? true)
             {:uudet-urakkaliitokset [1]
              :valittu-kohde {:id 1}})))

  (is (= {:nakymassa? false
          :uudet-urakkaliitokset {}
          :karttataso-nakyvissa? false}
         (e! (tiedot/->Nakymassa? false)))))

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
          :kohdekokonaisuudet [{::kok/id 1
                                ::kok/nimi "Foobar"}
                               {::kok/id 2
                                ::kok/nimi "Bazbar"}]
          :kohderivit [{::kohde/id 1
                        ::kohde/tyyppi :sulku
                        ::kohde/kohdekokonaisuus {::kok/id 1
                                                  ::kok/nimi "Foobar"}}
                       {::kohde/id 2
                        ::kohde/tyyppi :silta
                        ::kohde/nimi "komea silta"
                        ::kohde/kohdekokonaisuus {::kok/id 1
                                                  ::kok/nimi "Foobar"}}
                       {::kohde/id 3
                        ::kohde/tyyppi :sulku
                        ::kohde/kohdekokonaisuus {::kok/id 2
                                                  ::kok/nimi "Bazbar"}}]}
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

(deftest urakan-valinta
  (is (= {:valittu-urakka {:foo :bar}}
         (e! (tiedot/->ValitseUrakka {:foo :bar})))))

(deftest asetaliitos
  (is (= {:uudet-urakkaliitokset {[1 1] true}}
         (e! (tiedot/->AsetaKohteenUrakkaliitos 1 1 true)
             {:uudet-urakkaliitokset {}})))

  (is (= {:uudet-urakkaliitokset {[1 1] true}}
         (e! (tiedot/->AsetaKohteenUrakkaliitos 1 1 true)
             {:uudet-urakkaliitokset {[1 1] true}})))

  (is (= {:uudet-urakkaliitokset {[1 1] false}}
         (e! (tiedot/->AsetaKohteenUrakkaliitos 1 1 false)
             {:uudet-urakkaliitokset {[1 1] true}}))))

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

(deftest kok-lomakkeen-avaus
  (is (= {:kohdekokonaisuuslomake-auki? true}
         (e! (tiedot/->AvaaKohdekokonaisuusLomake)))))

(deftest kok-lomakkeen-sulkeminen
  (is (= {:kohdekokonaisuuslomake-auki? false
          :kohdekokonaisuudet [{::kok/id 1}]}
         (e! (tiedot/->SuljeKohdekokonaisuusLomake)
             {:kohdekokonaisuudet [{::kok/id 1 :poistettu true}
                                   {:id 1}
                                   {::kok/id -1}]}))))

(deftest kok-lisays
  (is (= {:kohdekokonaisuudet [{:foo :bar}]}
         (e! (tiedot/->LisaaKohdekokonaisuuksia [{:foo :bar}])))))

(deftest kokonaisuuksien-tallennus
  (vaadi-async-kutsut
    #{tiedot/->KohdekokonaisuudetTallennettu tiedot/->KohdekokonaisuudetEiTallennettu}
    (is (= {:kohdekokonaisuuksien-tallennus-kaynnissa? true}
           (e! (tiedot/->TallennaKohdekokonaisuudet [])))))

  (vaadi-async-kutsut
    #{}
    (is (= {:kohdekokonaisuuksien-tallennus-kaynnissa? true}
           (e! (tiedot/->TallennaKohdekokonaisuudet [])
               {:kohdekokonaisuuksien-tallennus-kaynnissa? true})))))

(deftest kok-tallennettu
  (is (= {:kohderivit []
          :kohdekokonaisuudet []
          :kohdekokonaisuuslomake-auki? false
          :kohdekokonaisuuksien-tallennus-kaynnissa? false}
         (e! (tiedot/->KohdekokonaisuudetTallennettu [])))))

(deftest kok-ei-tallennettu
  (is (= {:kohdekokonaisuuksien-tallennus-kaynnissa? false}
         (e! (tiedot/->KohdekokonaisuudetEiTallennettu [])))))

(deftest kohteen-valinta
  (is (= {:valittu-kohde {:id 1}
          :karttataso-nakyvissa? true}
         (e! (tiedot/->ValitseKohde {:id 1}))))

  (is (= {:valittu-kohde nil
          :karttataso-nakyvissa? false}
         (e! (tiedot/->ValitseKohde nil)))))

(deftest kohteen-muokkaus
  (is (= {:valittu-kohde {:id 1}}
         (e! (tiedot/->KohdettaMuokattu {:id 1})))))

(deftest kohteen-tallennus
  (vaadi-async-kutsut
    #{tiedot/->KohdeTallennettu tiedot/->KohdeEiTallennettu}
    (is (= {:kohteen-tallennus-kaynnissa? true}
           (e! (tiedot/->TallennaKohde {:id 1})))))

  (vaadi-async-kutsut
    #{}
    (is (= {:kohteen-tallennus-kaynnissa? true}
           (e! (tiedot/->TallennaKohde {:id 1})
               {:kohteen-tallennus-kaynnissa? true})))))

(deftest kohde-tallennettu
  (is (= {:kohderivit []
          :kohdekokonaisuudet []
          :valittu-kohde nil
          :kohteen-tallennus-kaynnissa? false}
         (e! (tiedot/->KohdeTallennettu [])))))

(deftest kohde-ei-tallennettu
  (is (= {:kohteen-tallennus-kaynnissa? false}
         (e! (tiedot/->KohdeEiTallennettu [])))))

(deftest osien-hau
  (vaadi-async-kutsut
    #{tiedot/->KohteenosatHaettu tiedot/->KohteenosatEiHaettu}
    (is (= {:kohteenosien-haku-kaynnissa? true}
           (e! (tiedot/->HaeKohteenosat)))))

  (vaadi-async-kutsut
    #{}
    (is (= {:kohteenosien-haku-kaynnissa? true}
           (e! (tiedot/->HaeKohteenosat)
               {:kohteenosien-haku-kaynnissa? true})))))

(deftest kohteenosat-haettu
  (is (= {:kohteenosien-haku-kaynnissa? false
          :haetut-kohteenosat []}
         (e! (tiedot/->KohteenosatHaettu [])))))

(deftest kohteenosat-ei-haettu
  (is (= {:kohteenosien-haku-kaynnissa? false}
         (e! (tiedot/->KohteenosatEiHaettu [])))))

(deftest osien-muokkaus
  (is (= {:valittu-kohde {::kohde/kohteenosat [{:id 1}]}}
         (e! (tiedot/->MuokkaaKohteenKohteenosia [{:id 1}])))))

(deftest klikkaus-kartalla
  (testing "Varatun kohteen liittäminen"
    (is (= {:valittu-kohde {::kohde/id 2
                            ::kohde/kohteenosat [{::osa/id 2
                                                  ::osa/kohde {::kohde/id 2}}
                                                 {::osa/id 1
                                                  ::osa/kohde {::kohde/id 2
                                                               ::kohde/kohteenosat [{::osa/id 2
                                                                                     ::osa/kohde {::kohde/id 2}}]}
                                                  :vanha-kohde {::kohde/id 1}}]}
            :haetut-kohteenosat [{::osa/id 1
                                  ::osa/kohde {::kohde/id 2
                                               ::kohde/kohteenosat [{::osa/id 2
                                                                     ::osa/kohde {::kohde/id 2}}]}
                                  :vanha-kohde {::kohde/id 1}}
                                 {::osa/id 2
                                  ::osa/kohde {::kohde/id 2}}]}
           (e! (tiedot/->KohteenosaKartallaKlikattu {::osa/id 1 ::osa/kohde {::kohde/id 1}})
               {:valittu-kohde {::kohde/id 2
                                ::kohde/kohteenosat [{::osa/id 2 ::osa/kohde {::kohde/id 2}}]}
                :haetut-kohteenosat [{::osa/id 1 ::osa/kohde {::kohde/id 1}}
                                     {::osa/id 2 ::osa/kohde {::kohde/id 2}}]}))))

  (testing "Varatun kohteen irrottaminen"
    (is (= {:valittu-kohde {::kohde/id 2
                            ::kohde/kohteenosat [{::osa/id 2 ::osa/kohde {::kohde/id 2}}]}
            :haetut-kohteenosat [{::osa/id 1
                                  ::osa/kohde {::kohde/id 1}
                                  :vanha-kohde {::kohde/id 1}}
                                 {::osa/id 2 ::osa/kohde {::kohde/id 2}}]}
           (e! (tiedot/->KohteenosaKartallaKlikattu {::osa/id 1
                                                     ::osa/kohde {::kohde/id 2}
                                                     :vanha-kohde {::kohde/id 1}})
               {:valittu-kohde {::kohde/id 2
                                ::kohde/kohteenosat [{::osa/id 2 ::osa/kohde {::kohde/id 2}}
                                                     {::osa/id 1 ::osa/kohde {::kohde/id 2}}]}
                :haetut-kohteenosat [{::osa/id 1
                                      ::osa/kohde {::kohde/id 2}
                                      :vanha-kohde {::kohde/id 1}}
                                     {::osa/id 2 ::osa/kohde {::kohde/id 2}}]}))))

  (testing "Vapaan kohteen liittäminen"
    (is (= {:valittu-kohde {::kohde/id 2
                            ::kohde/kohteenosat [{::osa/id 2
                                                  ::osa/kohde {::kohde/id 2}}
                                                 {::osa/id 1
                                                  ::osa/kohde {::kohde/id 2
                                                               ::kohde/kohteenosat [{::osa/id 2
                                                                                     ::osa/kohde {::kohde/id 2}}]}
                                                  :vanha-kohde nil}]}
            :haetut-kohteenosat [{::osa/id 1
                                  ::osa/kohde {::kohde/id 2
                                               ::kohde/kohteenosat [{::osa/id 2
                                                                     ::osa/kohde {::kohde/id 2}}]}
                                  :vanha-kohde nil}
                                 {::osa/id 2
                                  ::osa/kohde {::kohde/id 2}}]}
           (e! (tiedot/->KohteenosaKartallaKlikattu {::osa/id 1 ::osa/kohde nil})
               {:valittu-kohde {::kohde/id 2
                                ::kohde/kohteenosat [{::osa/id 2 ::osa/kohde {::kohde/id 2}}]}
                :haetut-kohteenosat [{::osa/id 1 ::osa/kohde nil}
                                     {::osa/id 2 ::osa/kohde {::kohde/id 2}}]}))))

  (testing "Kohteen irrottaminen"
    (is (= {:valittu-kohde {::kohde/id 2
                            ::kohde/kohteenosat [{::osa/id 2 ::osa/kohde {::kohde/id 2}}]}
            :haetut-kohteenosat [{::osa/id 1 ::osa/kohde nil}
                                 {::osa/id 2 ::osa/kohde {::kohde/id 2}}]}
           (e! (tiedot/->KohteenosaKartallaKlikattu {::osa/id 1 ::osa/kohde {::kohde/id 2}})
               {:valittu-kohde {::kohde/id 2
                                ::kohde/kohteenosat [{::osa/id 2 ::osa/kohde {::kohde/id 2}}
                                                     {::osa/id 1 ::osa/kohde {::kohde/id 2}}]}
                :haetut-kohteenosat [{::osa/id 1 ::osa/kohde {::kohde/id 2}}
                                     {::osa/id 2 ::osa/kohde {::kohde/id 2}}]})))))