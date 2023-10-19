(ns harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet-test
  (:require [clojure.test :refer-macros [deftest is testing]]
            [harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet :as tiedot]
            [harja.domain.kanavat.hairiotilanne :as hairiotilanne]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.vesivaylat.materiaali :as materiaali]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]
            [harja.pvm :as pvm]))

(deftest NakymaAvattu
  (vaadi-async-kutsut
    #{tiedot/->MateriaalitHaettu
      tiedot/->MateriaalienHakuEpaonnistui}
    (let [{:keys [nakymassa?
                  materiaalien-haku-kaynnissa?
                  kohteet
                  materiaalit]} (e! (tiedot/->NakymaAvattu))]
      (is nakymassa?)
      (is materiaalien-haku-kaynnissa?)
      (is (= materiaalit nil)))))

(deftest NakymaSuljettu
  (is (false? (:nakymassa? (e! (tiedot/->NakymaSuljettu))))))

(deftest PaivitaValinnat
  (vaadi-async-kutsut
    #{tiedot/->HaeHairiotilanteet}
    (is (= {:valinnat {:foo :bar}}
           (e! (tiedot/->PaivitaValinnat {:foo :bar}))))))

(deftest HaeHairiotilanteet
  (vaadi-async-kutsut
    #{tiedot/->HairiotilanteetHaettu tiedot/->HairiotilanteetEiHaettu}
    (is (= {:hairiotilanteiden-haku-kaynnissa? true}
           (e! (tiedot/->HaeHairiotilanteet {:urakka {:id 1}})))))

  ;; Ei tehdä hakua jos on jo käynnissä
  (vaadi-async-kutsut
    #{}
    (is (= {:hairiotilanteiden-haku-kaynnissa? true
            :foo :bar}
           (e! (tiedot/->HaeHairiotilanteet {:urakka {:id 2}})
               {:hairiotilanteiden-haku-kaynnissa? true
                :foo :bar})))))

(deftest HairiotilanteetHaettu
  (is (= {:hairiotilanteiden-haku-kaynnissa? false
          :hairiotilanteet [{::hairiotilanne/id 1}]}
         (e! (tiedot/->HairiotilanteetHaettu [{::hairiotilanne/id 1}])))))

(deftest HairiotilanteetEiHaettu
  (is (= {:hairiotilanteiden-haku-kaynnissa? false
          :hairiotilanteet []}
         (e! (tiedot/->HairiotilanteetEiHaettu)))))

(deftest LisaaHairiotilanne
  (let [t (e! (tiedot/->LisaaHairiotilanne))]

    (is (= {:harja.domain.kanavat.hairiotilanne/tallennuksen-aika? true, 
            :harja.domain.kanavat.hairiotilanne/sopimus-id nil, 
            :harja.domain.kanavat.hairiotilanne/kuittaaja {:harja.domain.kayttaja/id nil, 
                                                           :harja.domain.kayttaja/etunimi nil,
                                                           :harja.domain.kayttaja/sukunimi nil}}
           (-> t
               :valittu-hairiotilanne
               (dissoc ::hairiotilanne/havaintoaika))))

    (is (some? (get-in t [:valittu-hairiotilanne ::hairiotilanne/havaintoaika])))))

(deftest ValitseHairiotilanne
  (let [state {:materiaalit '({::materiaali/urakka-id 1
                               ::materiaali/muutokset [{::materiaali/maara 1000
                                                        ::materiaali/id 4}
                                                       {::materiaali/maara -3
                                                        ::materiaali/id 5}
                                                       {::materiaali/maara -3
                                                        ::materiaali/lisatieto "Käytetty häiriötilanteessa 10.12.2017 kohteessa Pälli"
                                                        ::materiaali/id 13
                                                        ::materiaali/hairiotilanne 2}
                                                       {::materiaali/maara -1
                                                        ::materiaali/lisatieto "Käytetty häiriötilanteessa 10.12.2017 kohteessa Soskua"
                                                        ::materiaali/id 16
                                                        ::materiaali/hairiotilanne 3}]
                               ::materiaali/nimi "Naulat"
                               ::materiaali/yksikko "kpl"}
                               {::materiaali/urakka-id 1
                                ::materiaali/muutokset [{::materiaali/maara 500
                                                         ::materiaali/id 8}
                                                        {::materiaali/maara -12
                                                         ::materiaali/lisatieto "Käytetty häiriötilanteessa 10.12.2017 kohteessa Pälli"
                                                         ::materiaali/id 12
                                                         ::materiaali/hairiotilanne 2}]
                                ::materiaali/nimi "Ämpäreitä"
                                ::materiaali/yksikko "kpl"})}
        kysely {::hairiotilanne/id 2
                ::hairiotilanne/havaintoaika (pvm/luo-pvm 2017 11 10)}
        vastaus {:materiaalit (:materiaalit state)
                 :valittu-hairiotilanne {::hairiotilanne/id 2
                                         ::hairiotilanne/havaintoaika (pvm/luo-pvm 2017 11 10)
                                         ::materiaali/materiaalit (seq [{:maara 3
                                                                         :yksikko "kpl"
                                                                         :tallennetut-materiaalit {::materiaali/nimi "Naulat"
                                                                                   ::materiaali/urakka-id 1
                                                                                   ::materiaali/pvm nil
                                                                                   ::materiaali/id 13
                                                                                   ::materiaali/yksikko "kpl"}}
                                                                        {:maara 12
                                                                         :yksikko "kpl"
                                                                         :tallennetut-materiaalit {::materiaali/nimi "Ämpäreitä"
                                                                                   ::materiaali/urakka-id 1
                                                                                   ::materiaali/pvm nil
                                                                                   ::materiaali/id 12
                                                                                   ::materiaali/yksikko "kpl"}}])
                                         ::materiaali/muokkaamattomat-materiaalit (seq [{:maara 3
                                                                                         :yksikko "kpl"
                                                                                         :tallennetut-materiaalit {::materiaali/nimi "Naulat"
                                                                                                   ::materiaali/urakka-id 1
                                                                                                   ::materiaali/pvm nil
                                                                                                   ::materiaali/id 13
                                                                                                   ::materiaali/yksikko "kpl"}}
                                                                                        {:maara 12
                                                                                         :yksikko "kpl"
                                                                                         :tallennetut-materiaalit {::materiaali/nimi "Ämpäreitä"
                                                                                                   ::materiaali/urakka-id 1
                                                                                                   ::materiaali/pvm nil
                                                                                                   ::materiaali/id 12
                                                                                                   ::materiaali/yksikko "kpl"}}])}}]

    (is (= (:valittu-hairiotilanne vastaus) (:valittu-hairiotilanne (e! (tiedot/->ValitseHairiotilanne kysely) state))))))

(deftest MateriaalitHaettu
  (let [haetut [{:materiaali 1}]
        {:keys [materiaalit materiaalien-haku-kaynnissa?]} (e! (tiedot/->MateriaalitHaettu haetut))]
    (is (false? materiaalien-haku-kaynnissa?))
    (is (= materiaalit haetut))))

(deftest MateriaalienHakuEpaonnistui
  (is (false? (:materiaalien-haku-kaynnissa? (e! (tiedot/->MateriaalienHakuEpaonnistui))))))

(deftest TyhjennaValittuHairiotilanne
  (is (false? (contains? (e! (tiedot/->TyhjennaValittuHairiotilanne)) :valittu-hairiotilanne))))

(deftest AsetaHairiotilanteenTiedot
  (let [valittu {:hairio :tilanne}]
    (is (= valittu (:valittu-hairiotilanne (e! (tiedot/->AsetaHairiotilanteenTiedot valittu)))))))

(deftest TallennaHairiotilanne
  (vaadi-async-kutsut
    #{tiedot/->HairiotilanneTallennettu
      tiedot/->HairiotilanteenTallentaminenEpaonnistui}
    (let [hairiotilanne {:paivamaara (pvm/luo-pvm 2017 11 10)
                         :aika (pvm/->Aika 0 0 0)}
          {tallennus-kaynnissa? :tallennus-kaynnissa?} (e! (tiedot/->TallennaHairiotilanne hairiotilanne))]
      (is tallennus-kaynnissa?))))

(deftest PoistaHairiotilanne
  (vaadi-async-kutsut
    #{tiedot/->TallennaHairiotilanne}
    (e! (tiedot/->PoistaHairiotilanne {:hairio :tilanne}))))

(deftest HairiotilanneTallennettu
  (let [haetut {:hairiotilanteet :tilanne
                :materiaalilistaukset :listaukset}
        {:keys [tallennus-kaynnissa?
                valittu-hairiotilanne
                hairiotilanteet
                materiaalit]} (e! (tiedot/->HairiotilanneTallennettu haetut))]
    (is (false? tallennus-kaynnissa?))
    (is (nil? valittu-hairiotilanne))
    (is (= hairiotilanteet (:hairiotilanteet haetut)))
    (is (= materiaalit (:materiaalilistaukset haetut)))))

(deftest HairiotilanteenTallentaminenEpaonnistui
  (is (false? (:tallennus-kaynnissa? (e! (tiedot/->HairiotilanteenTallentaminenEpaonnistui))))))

(deftest MuokkaaMateriaaleja
  (is (= {:valittu-hairiotilanne {::materiaali/materiaalit [{:materiaali 1}]}}
         (e! (tiedot/->MuokkaaMateriaaleja [{:materiaali 1}]) {:valittu-hairiotilanne {}})))
  (is (= {}
         (e! (tiedot/->MuokkaaMateriaaleja [{:materiaali 1}])))))