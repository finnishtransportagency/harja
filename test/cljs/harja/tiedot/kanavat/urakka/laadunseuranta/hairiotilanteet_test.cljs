(ns harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet-test
  (:require [clojure.test :refer-macros [deftest is testing]]
            [harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet :as tiedot]
            [harja.domain.kanavat.hairiotilanne :as hairiotilanne]
            [harja.domain.kayttaja :as kayttaja]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]))

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
  (is (= {:valittu-hairiotilanne {::hairiotilanne/sopimus-id nil
                                  ::hairiotilanne/kuittaaja {::kayttaja/id nil
                                                             ::kayttaja/etunimi nil
                                                             ::kayttaja/sukunimi nil}}}

         (e! (tiedot/->LisaaHairiotilanne)))))

(deftest ValitseHairiotilanne
  (let [hairiotilanne {:hairio :tilanne}]
    (is (= hairiotilanne (:valittu-hairiotilanne (e! (tiedot/->ValitseHairiotilanne hairiotilanne)))))))

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
    (let [{tallennus-kaynnissa? :tallennus-kaynnissa?} (e! (tiedot/->TallennaHairiotilanne {:foo "bar"}))]
      (is tallennus-kaynnissa?))))

(deftest PoistaHairiotilanne
  (vaadi-async-kutsut
    #{tiedot/->TallennaHairiotilanne}
    (e! (tiedot/->PoistaHairiotilanne {:hairio :tilanne}))))

(deftest HairiotilanneTallennettu
  (let [haetut [{:hairio :tilanne}]
        {:keys [tallennus-kaynnissa?
                valittu-hairiotilanne
                hairiotilanteet]} (e! (tiedot/->HairiotilanneTallennettu haetut))]
    (is (false? tallennus-kaynnissa?))
    (is (nil? valittu-hairiotilanne))
    (is (= hairiotilanteet haetut))))

(deftest HairiotilanteenTallentaminenEpaonnistui
  (is (false? (:tallennus-kaynnissa? (e! (tiedot/->HairiotilanteenTallentaminenEpaonnistui))))))