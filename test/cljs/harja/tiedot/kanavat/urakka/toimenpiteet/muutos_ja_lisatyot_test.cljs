(ns harja.tiedot.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot-test
  (:require [harja.tiedot.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot :as tiedot]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.kanavat.hinta :as hinta]
            [harja.domain.kanavat.tyo :as tyo]
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]
            [harja.loki :refer [log]]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]))



(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))

(deftest PaivitaValinnat
  (vaadi-async-kutsut
    #{tiedot/->HaeToimenpiteet}
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
