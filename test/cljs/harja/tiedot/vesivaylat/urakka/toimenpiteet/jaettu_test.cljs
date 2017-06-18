(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu-test
  (:require [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.loki :refer [log]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]
            [harja.pvm :as pvm]
            [harja.domain.toteuma :as tot]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.vesivaylat.vayla :as va]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [cljs-time.core :as t]
            [cljs.spec.alpha :as s]))

(def testitila {:nakymassa? true
                :infolaatikko-nakyvissa false
                :valinnat {:urakka-id nil
                           :sopimus-id nil
                           :aikavali [nil nil]
                           :vaylatyyppi :kauppamerenkulku
                           :vayla nil
                           :tyolaji :kiintea
                           :tyoluokka :kuljetuskaluston-huolto-ja-kunnossapito
                           :toimenpide :alukset-ja-veneet}
                :toimenpiteet [{::to/id 0
                                ::to/tyolaji :viitat
                                ::to/vayla {::va/nimi "Kuopio, Iisalmen väylä"
                                            ::va/id 1}
                                ::to/tyoluokka :asennus-ja-huolto
                                ::to/toimenpide :huoltotyo
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                               {::to/id 1
                                ::to/tyolaji :viitat
                                ::to/vayla {::va/nimi "Kuopio, Iisalmen väylä"
                                            ::va/id 1}
                                ::to/tyoluokka :asennus-ja-huolto
                                ::to/toimenpide :huoltotyo
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}
                                :valittu? true}
                               {::to/id 2
                                ::to/tyolaji :viitat
                                ::to/vayla {::va/nimi "Kuopio, Iisalmen väylä"
                                            ::va/id 1}
                                ::to/tyoluokka :asennus-ja-huolto
                                ::to/toimenpide :huoltotyo
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                               {::to/id 3
                                ::to/tyolaji :viitat
                                ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                            ::va/id 2}
                                ::to/tyoluokka :asennus-ja-huolto
                                ::to/toimenpide :huoltotyo
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                               {::to/id 4
                                ::to/tyolaji :kiinteat
                                ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                            ::va/id 2}
                                ::to/tyoluokka :asennus-ja-huolto
                                ::to/toimenpide :huoltotyo
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                               {::to/id 5
                                ::to/tyolaji :poijut
                                ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                            ::va/id 2}
                                ::to/tyoluokka :asennus-ja-huolto
                                ::to/toimenpide :huoltotyo
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                               {::to/id 6
                                ::to/tyolaji :poijut
                                ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                            ::va/id 2}
                                ::to/tyoluokka :asennus-ja-huolto
                                ::to/toimenpide :huoltotyo
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}]})

(deftest rivin-valinta
  (testing "Rivin asettaminen valituksi"
    (let [vanha-tila testitila
          vanha-kohde (to/toimenpide-idlla (:toimenpiteet vanha-tila) 0)
          uusi-tila (e! (tiedot/->ValitseToimenpide {:id 0 :valinta true}
                                                    (:toimenpiteet vanha-tila)) vanha-tila)
          muokattu-kohde (to/toimenpide-idlla (:toimenpiteet uusi-tila) 0)]
      (is (not (:valittu? vanha-kohde)))
      (is (true? (:valittu? muokattu-kohde)))))

  (testing "Rivin asettaminen ei-valituksi"
    (let [vanha-tila testitila
          vanha-kohde (to/toimenpide-idlla (:toimenpiteet vanha-tila) 1)
          uusi-tila (e! (tiedot/->ValitseToimenpide {:id 1 :valinta false}
                                                    (:toimenpiteet vanha-tila)) vanha-tila)
          muokattu-kohde (to/toimenpide-idlla (:toimenpiteet uusi-tila) 1)]
      (is (true? (:valittu? vanha-kohde)))
      (is (false? (:valittu? muokattu-kohde))))))


(deftest tyolajin-rivien-valinta
  (testing "Valitaan viitat"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->ValitseTyolaji {:tyolaji :viitat
                                                  :valinta true}
                                                 (:toimenpiteet vanha-tila))
                        vanha-tila)
          viitat (to/toimenpiteet-tyolajilla (:toimenpiteet uusi-tila) :viitat)]
      (is (every? true? (map :valittu? viitat)))))

  (testing "Asetetaan valinnat pois viitoilta"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->ValitseTyolaji {:tyolaji :viitat
                                                  :valinta false}
                                                 (:toimenpiteet vanha-tila))
                        vanha-tila)
          viitat (to/toimenpiteet-tyolajilla (:toimenpiteet uusi-tila) :viitat)]
      (is (every? false? (map :valittu? viitat))))))

(deftest infolaatikon-tila
  (testing "Asetetaan infolaatikko näkyviin"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->AsetaInfolaatikonTila :listaus-1 true) vanha-tila)]
      (is (false? (:listaus-1 (:infolaatikko-nakyvissa vanha-tila))))
      (is (true? (:listaus-1 (:infolaatikko-nakyvissa uusi-tila)))))))

(deftest vaylan-rivien-valinta
  (testing "Valitaan Iisalmen väylä"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->ValitseVayla {:vayla-id 1
                                                :valinta true}
                                               (:toimenpiteet vanha-tila))
                        vanha-tila)
          viitat (to/toimenpiteet-vaylalla (:toimenpiteet uusi-tila) 1)]
      (is (every? true? (map :valittu? viitat)))))

  (testing "Asetetaan valinnat pois Iisalmen väylältä"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->ValitseVayla {:vayla-id 1
                                                :valinta false}
                                               (:toimenpiteet vanha-tila))
                        vanha-tila)
          viitat (to/toimenpiteet-vaylalla (:toimenpiteet uusi-tila) 1)]
      (is (every? false? (map :valittu? viitat))))))

(deftest toimenpiteiden-vaylat
  (testing "Valitaan toimenpiteiden väylät"
    (is (= (to/toimenpiteiden-vaylat (:toimenpiteet testitila))
           [{::va/nimi "Kuopio, Iisalmen väylä"
             ::va/id 1}
            {::va/nimi "Varkaus, Kuopion väylä"
             ::va/id 2}]))))

(deftest pudotusvalikko-valinnat
  (is (= [nil 1 2 3] (tiedot/arvot-pudotusvalikko-valinnoiksi {:1 1 :1a 1 :2 2 :3 3}))))

(deftest toimenpiteet-tyolajilla
  (is (= [{::to/tyolaji :foo :id 1}
          {::to/tyolaji :foo :id 2}]
         (tiedot/toimenpiteet-tyolajilla [{::to/tyolaji :foo :id 1}
                                             {::to/tyolaji :foo :id 2}
                                             {::to/tyolaji :bar :id 3}]
                                         :foo))))

(deftest valintatilan-paattely
  (let [kaikki-valittu [{:valittu? true :id 1}
                        {:valittu? true :id 2}
                        {:valittu? true :id 3}]
        osa-valittu [{:valittu? true :id 1}
                     {:valittu? false :id 2}
                     {:valittu? true :id 3}]
        mitaan-ei-valittu [{:valittu? false :id 1}
                           {:valittu? false :id 2}
                           {:valittu? false :id 3}]
        indeterminate :harja.ui.kentat/indeterminate]

    (testing "Kaikki-valittu?"
      (is (true? (tiedot/kaikki-valittu? kaikki-valittu)))
      (is (false? (tiedot/kaikki-valittu? osa-valittu)))
      (is (false? (tiedot/kaikki-valittu? mitaan-ei-valittu))))

    (testing "Mitään-ei-valittu?"
      (is (false? (tiedot/kaikki-valittu? kaikki-valittu)))
      (is (false? (tiedot/kaikki-valittu? osa-valittu)))
      (is (true? (tiedot/kaikki-valittu? mitaan-ei-valittu))))

    (testing "Checkboxin tila"
      (is (true? (tiedot/valinnan-tila kaikki-valittu)))
      (is (false? (tiedot/valinnan-tila mitaan-ei-valittu)))
      (is (= indeterminate (tiedot/valinnan-tila osa-valittu))))))

(deftest toimenpiteet-siirretty
  (is (= {:toimenpiteet [{::to/id 2}]
          :siirto-kaynnissa? false}
         (e! (tiedot/->ToimenpiteetSiirretty [{::to/id 1}]) [{::to/id 1} {::to/id 2}]))))

(deftest toimenpiteet-ei-siirretty
  (is (= {:siirto-kaynnissa? false}
         (e! (tiedot/->ToimenpiteetEiSiirretty)))))

(deftest valittujen-siirto
  (vaadi-async-kutsut
    #{tiedot/->ToimenpiteetSiirretty tiedot/->ToimenpiteetEiSiirretty}
    (is (= {:siirto-kaynnissa? true}
           (tiedot/siirra-valitut! :foo {})))))

(deftest tilan-yhdistaminen

  (testing "Tilan yhdistäminen muuttaa vain valintoja"
    (let [a (atom {:id 1 :foo :bar})
         b (atom {:id 2 :baz :barbaz})]
     (is (= a (tiedot/yhdista-tilat! a b)) "Funktio palauttaa ensimmäisen atomin")
     (is (= @a @(tiedot/yhdista-tilat! a b)) "Sisältö ei muuttunut kutsun aikana")))

  (testing "Tilan yhdistäminen yhdistää :valinnat mäpin"
    (let [a (atom {:id 1 :valinnat {:sopimus 1 :urakka 1}})
          b (atom {:id 2 :baz {:urakka 2 :organisaatio 2}})]
      (is (= a (tiedot/yhdista-tilat! a b)) "Funktio palauttaa ensimmäisen atomin")
      (is (= {:id 1 :valinnat {:sopimus 1 :urakka 2 :organisaatio 2}}
             @(tiedot/yhdista-tilat! a b)) ":valinnat avain yhdistettiin"))))



