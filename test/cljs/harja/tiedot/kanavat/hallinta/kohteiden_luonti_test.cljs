(ns harja.tiedot.kanavat.hallinta.kohteiden-luonti-test
  (:require [harja.tiedot.kanavat.hallinta.kohteiden-luonti :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]

            [harja.domain.kanavat.kanava :as kanava]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.domain.muokkaustiedot :as m]))

(deftest kohderivit
  (is (= [{::kohde/id 1
           ::kohde/tyyppi :sulku
           ::kanava/id 1
           ::kanava/nimi "Foobar"
           :rivin-teksti "Foobar, sulku"}
          {::kohde/id 2
           ::kohde/tyyppi :silta
           ::kohde/nimi "komea silta"
           ::kanava/id 1
           ::kanava/nimi "Foobar"
           :rivin-teksti "Foobar, komea silta, silta"}
          {::kohde/id 3
           ::kohde/tyyppi :sulku
           ::kanava/id 2
           ::kanava/nimi "Bazbar"
           :rivin-teksti "Bazbar, sulku"}]
         (tiedot/kohderivit
           [{::kanava/id 1
             ::kanava/nimi "Foobar"
             ::kanava/kohteet [{::kohde/id 1
                                ::kohde/tyyppi :sulku}
                               {::kohde/id 2
                                ::kohde/tyyppi :silta
                                ::kohde/nimi "komea silta"}]}
            {::kanava/id 2
             ::kanava/nimi "Bazbar"
             ::kanava/kohteet [{::kohde/id 3
                                ::kohde/tyyppi :sulku}]}]))))

(deftest kanavat
  (is (= [{::kanava/id 1
           ::kanava/nimi "Foobar"}
          {::kanava/id 2
           ::kanava/nimi "Bazbar"}]
         (tiedot/kanavat
           [{::kanava/id 1
             ::kanava/nimi "Foobar"
             :huahuhue "joo"
             ::kanava/kohteet [{::kohde/id 1
                                ::kohde/tyyppi :sulku}
                               {::kohde/id 2
                                ::kohde/tyyppi :silta
                                ::kohde/niim "komea silta"}]}
            {::kanava/id 2
             ::kanava/nimi "Bazbar"
             :huahuhue "joo"
             ::kanava/kohteet [{::kohde/id 3
                                ::kohde/tyyppi :sulku}]}]))))

(deftest voi-tallentaa?
  (is (true?
        (tiedot/kohteet-voi-tallentaa? {:kanava 1
                                        :kohteet [{::kohde/tyyppi 1}
                                                  {::kohde/tyyppi 2}]})))

  (is (false?
        (tiedot/kohteet-voi-tallentaa? {:kanava nil
                                        :kohteet [{::kohde/tyyppi 1}
                                                  {::kohde/tyyppi 2}]})))

  (is (false?
        (tiedot/kohteet-voi-tallentaa? {:kanava 1
                                        :kohteet []})))

  (is (false?
        (tiedot/kohteet-voi-tallentaa? {:kanava 1
                                        :kohteet [{::kohde/tyyppi nil}]})))

  (is (false?
        (tiedot/kohteet-voi-tallentaa? {:kanava 1
                                        :kohteet [{::kohde/tyyppi nil}
                                                  {::kohde/tyyppi true}]}))))

(deftest muokattavat-kohteet
  (is (= {:foo :bar}
         (tiedot/muokattavat-kohteet {:lomakkeen-tiedot {:kohteet {:foo :bar}}}))))

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
         (tiedot/tallennusparametrit
           {:kanava {::kanava/id 1}
            :kohteet [{::kohde/nimi :foo
                       :id 1
                       ::kohde/kanava-id 1
                       ::kohde/tyyppi :sulku
                       :poistettu true}
                      {::kohde/nimi :foo
                       :id 2
                       ::kohde/kanava-id 1
                       ::kohde/tyyppi :sulku}]}))))

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
          :kanavat [{::kanava/id 1
                     ::kanava/nimi "Foobar"}
                    {::kanava/id 2
                     ::kanava/nimi "Bazbar"}]
          :kohderivit [{::kohde/id 1
                        ::kohde/tyyppi :sulku
                        ::kanava/id 1
                        ::kanava/nimi "Foobar"
                        :rivin-teksti "Foobar, sulku"}
                       {::kohde/id 2
                        ::kohde/tyyppi :silta
                        ::kohde/nimi "komea silta"
                        ::kanava/id 1
                        ::kanava/nimi "Foobar"
                        :rivin-teksti "Foobar, komea silta, silta"}
                       {::kohde/id 3
                        ::kohde/tyyppi :sulku
                        ::kanava/id 2
                        ::kanava/nimi "Bazbar"
                        :rivin-teksti "Bazbar, sulku"}]}
         (e! (tiedot/->KohteetHaettu [{::kanava/id 1
                                       ::kanava/nimi "Foobar"
                                       ::kanava/kohteet [{::kohde/id 1
                                                          ::kohde/tyyppi :sulku}
                                                         {::kohde/id 2
                                                          ::kohde/tyyppi :silta
                                                          ::kohde/niim "komea silta"}]}
                                      {::kanava/id 2
                                       ::kanava/nimi "Bazbar"
                                       ::kanava/kohteet [{::kohde/id 3
                                                          ::kohde/tyyppi :sulku}]}])))))

(deftest kohteet-ei-haettu
  (is (= {:kohteiden-haku-kaynnissa? false}
         (e! (tiedot/->KohteetEiHaettu {})))))

(deftest avaa-lomake
  (is (= {:kohdelomake-auki? true}
         (e! (tiedot/->AvaaKohdeLomake)))))

(deftest sulje-lomake
  (is (= {:kohdelomake-auki? false
          :lomakkeen-tiedot nil}
         (e! (tiedot/->SuljeKohdeLomake)))))

(deftest valitse-kanava
  (is (= {:lomakkeen-tiedot {:kanava {::kanava/id 1}
                             :kohteet [{::kanava/id 1 :id 1}
                                       {::kanava/id 1 :id 3}]}
          :kohderivit [{::kanava/id 1 :id 1}
                       {::kanava/id 2 :id 2}
                       {::kanava/id 1 :id 3}
                       {::kanava/id 3 :id 4}
                       {::kanava/id 2 :id 5}]}
         (e! (tiedot/->ValitseKanava {::kanava/id 1})
             {:kohderivit [{::kanava/id 1 :id 1}
                           {::kanava/id 2 :id 2}
                           {::kanava/id 1 :id 3}
                           {::kanava/id 3 :id 4}
                           {::kanava/id 2 :id 5}]}))))

(deftest kohteiden-lisays
  (is (= {:lomakkeen-tiedot {:kohteet [{:foo :bar}]
                             :kanava 1}}
         (e! (tiedot/->LisaaKohteita [{:foo :bar}])
             {:lomakkeen-tiedot {:kohteet [{:baz :bar}]
                                 :kanava 1}}))))

(deftest kohteiden-tallennus
  (vaadi-async-kutsut
    #{tiedot/->KohteetTallennettu tiedot/->KohteetEiTallennettu}
    (is (true? (:kohteiden-tallennus-kaynnissa? (e! (tiedot/->TallennaKohteet))))))

  (vaadi-async-kutsut
    #{}
    (is (= {:kohteiden-tallennus-kaynnissa? true}
           (e! (tiedot/->TallennaKohteet) {:kohteiden-tallennus-kaynnissa? true})))))

(deftest kohteet-tallennettu
  (is (= {:kohderivit [{::kohde/id 1
                        ::kohde/tyyppi :sulku
                        ::kanava/id 1
                        ::kanava/nimi "Foobar"
                        :rivin-teksti "Foobar, sulku"}
                       {::kohde/id 2
                        ::kohde/tyyppi :silta
                        ::kohde/nimi "komea silta"
                        ::kanava/id 1
                        ::kanava/nimi "Foobar"
                        :rivin-teksti "Foobar, komea silta, silta"}
                       {::kohde/id 3
                        ::kohde/tyyppi :sulku
                        ::kanava/id 2
                        ::kanava/nimi "Bazbar"
                        :rivin-teksti "Bazbar, sulku"}]
          :kanavat [{::kanava/id 1
                     ::kanava/nimi "Foobar"}
                    {::kanava/id 2
                     ::kanava/nimi "Bazbar"}]
          :kohdelomake-auki? false
          :lomakkeen-tiedot nil
          :kohteiden-tallennus-kaynnissa? false}
         (e! (tiedot/->KohteetTallennettu [{::kanava/id 1
                                            ::kanava/nimi "Foobar"
                                            ::kanava/kohteet [{::kohde/id 1
                                                               ::kohde/tyyppi :sulku}
                                                              {::kohde/id 2
                                                               ::kohde/tyyppi :silta
                                                               ::kohde/niim "komea silta"}]}
                                           {::kanava/id 2
                                            ::kanava/nimi "Bazbar"
                                            ::kanava/kohteet [{::kohde/id 3
                                                               ::kohde/tyyppi :sulku}]}])))))

(deftest kohteet-ei-tallennettu
  (is (= {:kohteiden-tallennus-kaynnissa? false}
         (e! (tiedot/->KohteetEiTallennettu {})))))