(ns harja.tiedot.vesivaylat.urakka.suunnittelu.kiintiot-test
  (:require [harja.tiedot.vesivaylat.urakka.suunnittelu.kiintiot :as tiedot]
            [cljs.core.async :refer [chan <!]]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.vesivaylat.kiintio :as kiintio]
            [cljs.test :refer-macros [deftest is async testing use-fixtures]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(deftest nakymassa
  (is (= (e! (tiedot/->Nakymassa? true))
         {:nakymassa? true}))

  (is (= (e! (tiedot/->Nakymassa? false))
         {:nakymassa? false})))

(deftest PaivitaValinnat
  (vaadi-async-kutsut
    #{tiedot/->HaeKiintiot}
    (is (= (e! (tiedot/->PaivitaValinnat {:foo :bar}))
           {:valinnat {:foo :bar}}))))

(deftest HaeKiintiot
  (vaadi-async-kutsut
    #{tiedot/->KiintiotEiHaettu tiedot/->KiintiotHaettu}
    (is (= (e! (tiedot/->HaeKiintiot))
           {:kiintioiden-haku-kaynnissa? true})))

  (vaadi-async-kutsut
    #{}
    (is (= (e! (tiedot/->HaeKiintiot) {:kiintioiden-haku-kaynnissa? true
                                       :foo :bar})
           {:kiintioiden-haku-kaynnissa? true
            :foo :bar}))))

(deftest KiintiotHaettu
  (is (= (e! (tiedot/->KiintiotHaettu [{:id 1}]))
         {:kiintioiden-haku-kaynnissa? false
          :kiintiot [{:id 1}]})))

(deftest KiintiotEiHaettu
  (is (= (e! (tiedot/->KiintiotEiHaettu))
         {:kiintioiden-haku-kaynnissa? false
          :kiintiot []})))

(deftest TallennaKiintiot
  (vaadi-async-kutsut
    #{tiedot/->KiintiotEiTallennettu tiedot/->KiintiotTallennettu}

    (is (= (e! (tiedot/->TallennaKiintiot [{:id 1}] (chan)))
           {:kiintioiden-tallennus-kaynnissa? true})))

  (vaadi-async-kutsut
    #{}
    (is (= (e! (tiedot/->TallennaKiintiot [{:id 1}] (chan))
               {:kiintioiden-tallennus-kaynnissa? true
                :foo :bar})
           {:kiintioiden-tallennus-kaynnissa? true
            :foo :bar}))))

(deftest KiintiotTallennettu
  (async done
    (go
      (let [ch (chan)]
        (is (= (e! (tiedot/->KiintiotTallennettu [{:id 1}] ch))
               {:kiintiot [{:id 1}]
                :kiintioiden-tallennus-kaynnissa? false}))
        (= [{:id 1}] (<! ch))
        (done)))))

(deftest KiintiotEiTallennettu
  (async done
    (go
      (let [ch (chan)]
        (is (= (e! (tiedot/->KiintiotEiTallennettu {:msg :error} ch)
                   {:kiintiot [{:foo :bar}]})
               {:kiintioiden-tallennus-kaynnissa? false
                :kiintiot [{:foo :bar}]}))
        (= [{:foo :bar}] (<! ch))
        (done)))))

(deftest ToimenpiteenValinta
  (is (= (e! (tiedot/->ValitseToimenpide {:id 1 :valittu? true}) {:valitut-toimenpide-idt #{}}))
      {:valitut-toimenpide-idt #{1}})
  (is (= (e! (tiedot/->ValitseToimenpide {:id 1 :valittu? true}) {:valitut-toimenpide-idt #{1}})
         {:valitut-toimenpide-idt #{1}}))
  (is (= (e! (tiedot/->ValitseToimenpide {:id 2 :valittu? true}) {:valitut-toimenpide-idt #{1}})
         {:valitut-toimenpide-idt #{1 2}}))
  (is (= (e! (tiedot/->ValitseToimenpide {:id 1 :valittu? false}) {:valitut-toimenpide-idt #{1}})
         {:valitut-toimenpide-idt #{}})))

(deftest IrrotaKiintiosta
  (testing "Kiintiöstä irrotuksen aloittaminen"
    (vaadi-async-kutsut
      #{tiedot/->IrrotettuKiintiosta tiedot/->EiIrrotettuKiintiosta}

      (is (true? (:kiintiosta-irrotus-kaynnissa?
                   (e! (tiedot/->IrrotaKiintiosta #{1})
                       {:valinnat {:urakka-id 5}}))))))

  (testing "Irrotus jo käynnissä"
    (vaadi-async-kutsut #{} ;; Ei saa aloittaa uusia kutsuja
                        (let [tila {:valinnat {:urakka-id 5}
                                    :kiintiosta-irrotus-kaynnissa? true}]
                          (is (= tila (e! (tiedot/->IrrotaKiintiosta #{1}) tila)))))))

(deftest kiintiosta-irrotuksen-valmistuminen
  (let [tulos (e! (tiedot/->IrrotettuKiintiosta {::to/idt #{1 3}})
                  {:foo 1
                   :kiintiosta-irrotus-kaynnissa? true
                   :valitut-toimenpide-idt #{1 3}
                   :kiintiot [{::kiintio/toimenpiteet []}
                              {::kiintio/toimenpiteet [{::to/id 1}]}
                              {::kiintio/toimenpiteet [{::to/id 2}
                                                       {::to/id 3}
                                                       {::to/id 4}]}]})]
    (is (= tulos
           {:foo 1
            :kiintiosta-irrotus-kaynnissa? false
            :valitut-toimenpide-idt #{}
            :kiintiot [{::kiintio/toimenpiteet []}
                       {::kiintio/toimenpiteet []}
                       {::kiintio/toimenpiteet [{::to/id 2}
                                                {::to/id 4}]}]}))))

(deftest kiintioista-irrotus-epaonnistui
  (let [tulos (e! (tiedot/->EiIrrotettuKiintiosta))]
    (is (false? (:kiintiosta-irrotus-kaynnissa? tulos)))))