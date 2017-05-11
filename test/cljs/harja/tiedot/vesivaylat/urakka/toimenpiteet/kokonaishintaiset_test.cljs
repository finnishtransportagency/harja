(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset-test
  (:require [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.loki :refer [log]]
            [harja.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]
            [harja.pvm :as pvm]
            [harja.domain.vesivaylat.toimenpide :as to]))

(def testitila {:nakymassa? true
                :infolaatikko-nakyvissa? false
                :toimenpiteet [{::to/id 0
                                ::to/tyolaji :viitat
                                ::to/vayla {:nimi "Kuopio, Iisalmen väylä"
                                            :id 1}
                                ::to/tyoluokka "Asennus ja huolto"
                                ::to/toimenpide "Huoltotyö"
                                ::to/pvm (pvm/nyt)
                                ::to/vikakorjaus true
                                ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                               {::to/id 1
                                ::to/tyolaji :viitat
                                ::to/vayla {:nimi "Kuopio, Iisalmen väylä"
                                            :id 1}
                                ::to/tyoluokka "Asennus ja huolto"
                                ::to/toimenpide "Huoltotyö"
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {:nimi "Siitenluoto (16469)"}
                                :valittu? true}
                               {::to/id 2
                                ::to/tyolaji :viitat
                                ::to/vayla {:nimi "Kuopio, Iisalmen väylä"
                                            :id 1}
                                ::to/tyoluokka "Asennus ja huolto"
                                ::to/toimenpide "Huoltotyö"
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                               {::to/id 3
                                ::to/tyolaji :viitat
                                ::to/vayla {:nimi "Varkaus, Kuopion väylä"
                                            :id 2}
                                ::to/tyoluokka "Asennus ja huolto"
                                ::to/toimenpide "Huoltotyö"
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                               {::to/id 4
                                ::to/tyolaji :kiinteat
                                ::to/vayla {:nimi "Varkaus, Kuopion väylä"
                                            :id 2}
                                ::to/tyoluokka "Asennus ja huolto"
                                ::to/toimenpide "Huoltotyö"
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                               {::to/id 5
                                ::to/tyolaji :poijut
                                ::to/vayla {:nimi "Varkaus, Kuopion väylä"
                                            :id 2}
                                ::to/tyoluokka "Asennus ja huolto"
                                ::to/toimenpide "Huoltotyö"
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                               {::to/id 6
                                ::to/tyolaji :poijut
                                ::to/vayla {:nimi "Varkaus, Kuopion väylä"
                                            :id 2}
                                ::to/tyoluokka "Asennus ja huolto"
                                ::to/toimenpide "Huoltotyö"
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {:nimi "Siitenluoto (16469)"}}]})

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))

(deftest rivin-valinta
  (testing "Rivin asettaminen valituksi"
    (let [vanha-tila testitila
          vanha-kohde (to/toimenpide-idlla (:toimenpiteet vanha-tila) 0)
          uusi-tila (e! (tiedot/->ValitseToimenpide {:id 0 :valinta true}) vanha-tila)
          muokattu-kohde (to/toimenpide-idlla (:toimenpiteet uusi-tila) 0)]
      (is (not (:valittu? vanha-kohde)))
      (is (true? (:valittu? muokattu-kohde)))))

  (testing "Rivin asettaminen ei-valituksi"
    (let [vanha-tila testitila
          vanha-kohde (to/toimenpide-idlla (:toimenpiteet vanha-tila) 1)
          uusi-tila (e! (tiedot/->ValitseToimenpide {:id 1 :valinta false}) vanha-tila)
          muokattu-kohde (to/toimenpide-idlla (:toimenpiteet uusi-tila) 1)]
      (is (true? (:valittu? vanha-kohde)))
      (is (false? (:valittu? muokattu-kohde))))))


(deftest tyolajin-rivien-valinta
  (testing "Valitaan viitat"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->ValitseTyolaji {:tyolaji :viitat
                                                  :valinta true})
                        vanha-tila)
          viitat (to/toimenpiteet-tyolajilla (:toimenpiteet uusi-tila) :viitat)]
      (is (every? true? (map :valittu? viitat)))))

  (testing "Asetetaan valinnat pois viitoilta"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->ValitseTyolaji {:tyolaji :viitat
                                                  :valinta false})
                        vanha-tila)
          viitat (to/toimenpiteet-tyolajilla (:toimenpiteet uusi-tila) :viitat)]
      (is (every? false? (map :valittu? viitat))))))

(deftest infolaatikon-tila
  (testing "Asetetaan infolaatikko näkyviin"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->AsetaInfolaatikonTila true) vanha-tila)]
      (is (false? (:infolaatikko-nakyvissa? vanha-tila)))
      (is (true? (:infolaatikko-nakyvissa? uusi-tila))))))

(deftest vaylan-rivien-valinta
  (testing "Valitaan Iisalmen väylä"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->ValitseVayla {:vayla-id 1
                                                :valinta true})
                        vanha-tila)
          viitat (to/toimenpiteet-vaylalla (:toimenpiteet uusi-tila) 1)]
      (is (every? true? (map :valittu? viitat)))))

  (testing "Asetetaan valinnat pois Iisalmen väylältä"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->ValitseVayla {:vayla-id 1
                                                :valinta false})
                        vanha-tila)
          viitat (to/toimenpiteet-vaylalla (:toimenpiteet uusi-tila) 1)]
      (is (every? false? (map :valittu? viitat))))))

(deftest valintojen-paivittaminen
  (testing "Asetetaan uudet valinnat"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->PaivitaValinnat {:urakka-id 666})
                        vanha-tila)]
      (is (nil? (get-in vanha-tila [:valinnat :urakka-id])))
      (is (= (get-in uusi-tila [:valinnat :urakka-id]) 666)))))