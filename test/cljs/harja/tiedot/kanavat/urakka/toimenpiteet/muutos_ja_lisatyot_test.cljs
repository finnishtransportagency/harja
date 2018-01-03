(ns harja.tiedot.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot-test
  (:require [harja.tiedot.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot :as tiedot]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.kanavat.hinta :as hinta]
            [harja.domain.kanavat.tyo :as tyo]
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]
            [harja.domain.vesivaylat.materiaali :as materiaali]
            [harja.loki :refer [log]]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))

(deftest PaivitaValinnat
  (vaadi-async-kutsut
   #{tiedot/->HaeToimenpiteet tiedot/->HaeMateriaalit}
    (is (= {:valinnat {:urakka {:id 4}}}
           (e! (tiedot/->PaivitaValinnat {:urakka {:id 4}}))))))

(deftest HaeToimenpiteet
  (vaadi-async-kutsut
    #{tiedot/->ToimenpiteetHaettu tiedot/->ToimenpiteetEiHaettu}
    (is (= {:toimenpiteiden-haku-kaynnissa? true}
           (e! (tiedot/->HaeToimenpiteet {:urakka {:id 1}
                                          :sopimus-id 666
                                          :toimenpide {:id 666}}))))))

(deftest ToimenpiteetHaettu
  (is (= {:toimenpiteiden-haku-kaynnissa? false
          :toimenpiteet [{:id 1}]}
         (e! (tiedot/->ToimenpiteetHaettu [{:id 1}])))))

(deftest ToimenpiteetEiHaettu
  (is (= {:toimenpiteiden-haku-kaynnissa? false
          :toimenpiteet []}
         (e! (tiedot/->ToimenpiteetEiHaettu)))))

(deftest SiirraToimenpideKokonaishintaisiin
  (vaadi-async-kutsut
    #{tiedot/->ValitutSiirretty tiedot/->ValitutEiSiirretty}
    (is (= {:toimenpiteiden-siirto-kaynnissa? true}
           (e! (tiedot/->SiirraValitut))))))

(deftest ValitutSiirretty
  (let [app {:toimenpiteet (sequence [{::kanavan-toimenpide/id 1}])
             :valitut-toimenpide-idt #{1}}]
    (is (= {:toimenpiteiden-siirto-kaynnissa? false
            :valitut-toimenpide-idt #{}
            :toimenpiteet (sequence [])}
           (e! (tiedot/->ValitutSiirretty) app)))))

(deftest ValitutEiSiirretty
  (let [app {:toimenpiteet (sequence [{::kanavan-toimenpide/id 1}])
             :valitut-toimenpide-idt #{1}}]
    (is (= {:toimenpiteiden-siirto-kaynnissa? false
            :toimenpiteet (sequence [{::kanavan-toimenpide/id 1}])
            :valitut-toimenpide-idt #{1}}
           (e! (tiedot/->ValitutEiSiirretty) app)))))

(deftest AloitaToimenpiteenHinnoittelu
  (let [app (assoc @tiedot/tila
                   :toimenpiteet [{::toimenpide/id 42}]
                   :valinnat {:urakka {:id 7}}
                   :seppo 42)
        tila (e! (tiedot/->AloitaToimenpiteenHinnoittelu 42) app)]
    (is (-> app :valinnat :urakka :id (= 7)))))

(deftest LisaaMuuKulurivi
  (let [hinnat [{::hinta/id 1}
                {::hinta/id 2}]
        uusi-hinta {::hinta/id -1
                    ::hinta/otsikko ""
                    ::hinta/summa 0
                    ::hinta/ryhma "muu"
                    ::hinta/yleiskustannuslisa 0}]
    (is (= (e! (tiedot/->LisaaMuuKulurivi)
               {:hinnoittele-toimenpide {::hinta/hinnat hinnat}})
           {:hinnoittele-toimenpide {::hinta/hinnat (conj hinnat uusi-hinta)}})))

  (let [hinnat [{::hinta/id 1}
                {::hinta/id 2}
                {::hinta/id -1}]
        uusi-hinta {::hinta/id -2
                    ::hinta/otsikko ""
                    ::hinta/summa 0
                    ::hinta/ryhma "muu"
                    ::hinta/yleiskustannuslisa 0}]
    (is (= (e! (tiedot/->LisaaMuuKulurivi)
               {:hinnoittele-toimenpide {::hinta/hinnat hinnat}})
           {:hinnoittele-toimenpide {::hinta/hinnat (conj hinnat uusi-hinta)}}))))

(deftest toimenpiteen-hinnoittelun-peruminen
  (let [vanha-tila {}
        uusi-tila (e! (tiedot/->PeruToimenpiteenHinnoittelu)
                      vanha-tila)]
    (is (nil? (get-in uusi-tila [:hinnoittele-toimenpide ::hinta/hinnat])))))


(def app-materiaalin-hinnoittelutestille {:urakan-materiaalit (:urakan-materiaalit '({::materiaali/urakka-id 1
                               ::materiaali/toimenpide 2
                               ::materiaali/muutokset [{::materiaali/maara 1000
                                                        ::materiaali/id 4}
                                                       {::materiaali/maara -3
                                                        ::materiaali/id 5}
                                                       {::materiaali/maara -3
                                                        ::materiaali/lisatieto "Käytetty häiriötilanteessa 10.12.2017 kohteessa Pälli"
                                                        ::materiaali/id 13
                                                        ::materiaali/toimenpide 2}
                                                       {::materiaali/maara -1
                                                        ::materiaali/lisatieto "Käytetty häiriötilanteessa 10.12.2017 kohteessa Soskua"
                                                        ::materiaali/id 16
                                                        ::materiaali/toimenpide 3}]
                               ::materiaali/nimi "Naulat"}
                              {::materiaali/urakka-id 1
                               ::materiaali/toimenpide 2
                                ::materiaali/muutokset [{::materiaali/maara 500
                                                         ::materiaali/id 8}
                                                        {::materiaali/maara -12
                                                         ::materiaali/lisatieto "Käytetty häiriötilanteessa 10.12.2017 kohteessa Pälli"
                                                         ::materiaali/id 12
                                                         ::materiaali/toimenpide 2}]
                                ::materiaali/nimi "Ämpäreitä"}))
                            :avattu-toimenpide {::kanavan-toimenpide/id 2
                                                ::kanavan-toimenpide/luotu (pvm/luo-pvm 2017 11 10)
                                                ::materiaali/materiaalit (seq [{:maara 4
                                                                                :varaosa {::materiaali/nimi "Naulat"
                                                                                          ::materiaali/urakka-id 1
                                                                                          ::materiaali/pvm nil
                                                                                          ::materiaali/id 13}}
                                                                               {:poistettu true
                                                                                :maara 12
                                                                                :varaosa {::materiaali/nimi "Ämpäreitä"
                                                                                          ::materiaali/urakka-id 1
                                                                                          ::materiaali/pvm nil
                                                                                          ::materiaali/id 12}}
                                                                               ])
                                                ::materiaali/muokkaamattomat-materiaalit (seq [{:maara 3
                                                                                                :varaosa {::materiaali/nimi "Naulat"
                                                                                                          ::materiaali/urakka-id 1
                                                                                                          ::materiaali/pvm nil
                                                                                                          ::materiaali/id 13}}
                                                                                               {:maara 12
                                                                                                :varaosa {::materiaali/nimi "Ämpäreitä"
                                                                                                          ::materiaali/urakka-id 1
                                                                                                          ::materiaali/pvm nil
                                                                                                          ::materiaali/id 12}}])}})
