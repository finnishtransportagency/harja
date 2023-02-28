(ns harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset-test
  (:require [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.tiedot.kanavat.urakka.toimenpiteet :as toimenpiteet]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]
            [harja.domain.vesivaylat.materiaali :as materiaali]
            [harja.testutils :refer [tarkista-map-arvot]]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [cljs.spec.alpha :as s]))


(deftest hakuargumenttien-muodostaminen
  (let [aikavali [(pvm/luo-pvm 2017 1 1)
                  (pvm/luo-pvm 2018 1 1)]
        odotettu {::kanavan-toimenpide/urakka-id 666
                  ::kanavan-toimenpide/sopimus-id 666
                  ::toimenpidekoodi/id 666
                  ::kanavan-toimenpide/kohde-id nil
                  ::kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen
                  :alkupvm (pvm/luo-pvm 2017 1 1)
                  :loppupvm (pvm/luo-pvm 2018 1 1)}]
    (is (= (toimenpiteet/muodosta-kohteiden-hakuargumentit {:urakka {:id 666}
                                                            :sopimus-id 666
                                                            :toimenpide {:id 666}
                                                            :aikavali aikavali}
                                                           :kokonaishintainen)
           odotettu))
    (is (s/valid? ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely odotettu))))

(deftest NakymaAvattu
  (vaadi-async-kutsut
    #{tiedot/->PaivitaValinnat
      tiedot/->HuoltokohteetHaettu
      tiedot/->HuoltokohteidenHakuEpaonnistui}
    (let [{:keys [nakymassa?
                  huoltokohteiden-haku-kaynnissa?]} (e! (tiedot/->NakymaAvattu))]
      (is nakymassa?)
      (is huoltokohteiden-haku-kaynnissa?))))

(deftest NakymaSuljettu
  (is false? (:nakymassa? (e! (tiedot/->NakymaSuljettu)))))

(deftest PaivitaValinnat
  (vaadi-async-kutsut
    #{tiedot/->HaeToimenpiteet tiedot/->HaeMateriaalit}
    (is (= {:valinnat {:foo :bar}}
           (e! (tiedot/->PaivitaValinnat {:foo :bar}))))))

(deftest HaeToimenpiteet
  (vaadi-async-kutsut
    #{tiedot/->ToimenpiteetHaettu tiedot/->ToimenpiteidenHakuEpaonnistui}
    (is (= {:haku-kaynnissa? true}
           (e! (tiedot/->HaeToimenpiteet {:urakka {:id 1}
                                          :sopimus-id 666
                                          :toimenpide {:id 666}}))))))

(deftest ToimenpiteetHaettu
  (is (= {:haku-kaynnissa? false
          :toimenpiteet [{:id 1}]}
         (e! (tiedot/->ToimenpiteetHaettu [{:id 1}])))))

(deftest ToimenpiteetEiHaettu
  (is (= {:haku-kaynnissa? false
          :toimenpiteet []}
         (e! (tiedot/->ToimenpiteidenHakuEpaonnistui)))))

(deftest UusiToimenpide
  (let [tulos (e! (tiedot/->UusiToimenpide))]
    (is (= {::kanavan-toimenpide/sopimus-id nil
            ::kanavan-toimenpide/kuittaaja
            {::kayttaja/id nil
             ::kayttaja/etunimi nil
             ::kayttaja/sukunimi nil}}
           (-> tulos :avattu-toimenpide (dissoc ::kanavan-toimenpide/pvm))))
    (is (some? (get-in tulos [:avattu-toimenpide ::kanavan-toimenpide/pvm])))))

(deftest TyhjennaValittuToimenpide
  (is (nil? (:avattu-toimenpide (e! (tiedot/->TyhjennaAvattuToimenpide))))))

(defn kutsu-poikkeuslokituksella [fn]
  (try
    (let [paluuarvo (fn)]
      paluuarvo)
    (catch js/Error e
      (.log js/console (.-stack e)))))

(deftest AsetaToimenpiteenTiedot
  (let [toimenpide {:testi-pieni "Olen vain"}]
    (tarkista-map-arvot toimenpide (:avattu-toimenpide (e! (tiedot/->AsetaLomakkeenToimenpiteenTiedot toimenpide))))))

(deftest ValinnatHaettuToimenpiteelle
  (let [valinnat {:foo "bar"}]
    (is (= valinnat (e! (tiedot/->ValinnatHaettuToimenpiteelle valinnat))))))

(deftest HuoltokohteetHaettu
  (let [haetut [{:foo "bar"}]
        {:keys [huoltokohteet huoltokohteiden-haku-kaynnissa?]} (e! (tiedot/->HuoltokohteetHaettu haetut))]
    (is (false? huoltokohteiden-haku-kaynnissa?))
    (is (= huoltokohteet haetut))))

(deftest TallennaToimenpide
  (vaadi-async-kutsut
    #{tiedot/->ToimenpideTallennettu
      tiedot/->ToimenpiteenTallentaminenEpaonnistui}
    (let [{tallennus-kaynnissa? :tallennus-kaynnissa?} (e! (tiedot/->TallennaToimenpide {:foo "bar"} false))]
      (is tallennus-kaynnissa?))))

(deftest ToimenpideTallennettu
  (let [haetut-toimenpiteet {:kanavatoimenpiteet [{:foo "bar"}]}
        {:keys [tallennus-kaynnissa? avattu-toimenpide toimenpiteet]}
        (e! (tiedot/->ToimenpideTallennettu haetut-toimenpiteet false))]
    (is (false? tallennus-kaynnissa?))
    (is (nil? avattu-toimenpide))
    (is (= (:kanavatoimenpiteet haetut-toimenpiteet) toimenpiteet))))

(deftest ToimenpiteidenTallentaminenEpaonnistui
  (is (false? (:tallennus-kaynnissa? (e! (tiedot/->ToimenpiteenTallentaminenEpaonnistui nil false))))))

(deftest ValitseToimenpide
  (let [tiedot {:id 1
                :valittu? true}
        app-jalkeen {:valitut-toimenpide-idt #{1}}
        app-ennen {:valitut-toimenpide-idt #{}}]
    (is (= app-jalkeen (e! (tiedot/->ValitseToimenpide tiedot) app-ennen)))))

(deftest SiirraToimenpideMuutosJaLisatoihin
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

(deftest PaikannusKytketty
  (let [app {:avattu-toimenpide {:paikannus-kaynnissa? nil}}]
    (is (= {:avattu-toimenpide {:paikannus-kaynnissa? true}}
           (e! (tiedot/->KytkePaikannusKaynnissa) app)))
    (is (= {:avattu-toimenpide {:paikannus-kaynnissa? false}}
           (e! (tiedot/->KytkePaikannusKaynnissa) (assoc-in app [:avattu-toimenpide :paikannus-kaynnissa?] true))))))

(def app-tallennustestille {:urakan-materiaalit (:urakan-materiaalit '({::materiaali/urakka-id 1
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
                                                                        ::materiaali/nimi "Naulat"
                                                                        ::materiaali/yksikko "kpl"}
                                                                        {::materiaali/urakka-id 1
                                                                         ::materiaali/toimenpide 2
                                                                         ::materiaali/muutokset [{::materiaali/maara 500
                                                                                                  ::materiaali/id 8}
                                                                                                 {::materiaali/maara -12
                                                                                                  ::materiaali/lisatieto "Käytetty häiriötilanteessa 10.12.2017 kohteessa Pälli"
                                                                                                  ::materiaali/id 12
                                                                                                  ::materiaali/toimenpide 2}]
                                                                         ::materiaali/nimi "Ämpäreitä"
                                                                         ::materiaali/yksikko "kpl"}))
                            :avattu-toimenpide {::kanavan-toimenpide/id 2
                                                ::kanavan-toimenpide/luotu (pvm/luo-pvm 2017 11 10)
                                                ::materiaali/materiaalit (seq [{:maara 4
                                                                                :yksikko "kpl"
                                                                                :tallennetut-materiaalit {::materiaali/nimi "Naulat"
                                                                                          ::materiaali/urakka-id 1
                                                                                          ::materiaali/pvm nil
                                                                                          ::materiaali/id 13
                                                                                          ::materiaali/yksikko "kpl"}}
                                                                               {:poistettu true
                                                                                :maara 12
                                                                                :yksikko "kpl"
                                                                                :tallennetut-materiaalit {::materiaali/nimi "Ämpäreitä"
                                                                                          ::materiaali/urakka-id 1
                                                                                          ::materiaali/pvm nil
                                                                                          ::materiaali/id 12
                                                                                          ::materiaali/yksikko "kpl"}}
                                                                               ])
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
                                                                                                          ::materiaali/yksikko "kpl"
                                                                                                          ::materiaali/id 12}}])}})

(def tallennettava-vertailumap {::kanavan-toimenpide/materiaalipoistot '({:harja.domain.vesivaylat.materiaali/urakka-id 1, :harja.domain.vesivaylat.materiaali/id 12})
                                ::kanavan-toimenpide/materiaalikirjaukset '({:harja.domain.vesivaylat.materiaali/nimi "Naulat"
                                                                             :harja.domain.vesivaylat.materiaali/urakka-id 1
                                                                             :harja.domain.vesivaylat.materiaali/pvm nil
                                                                             :harja.domain.vesivaylat.materiaali/id 13
                                                                             :harja.domain.vesivaylat.materiaali/maara -4
                                                                             :harja.domain.vesivaylat.materiaali/yksikko "kpl"
                                                                             :harja.domain.vesivaylat.materiaali/lisatieto "Käytetty kohteessa: "})
                                })
(deftest materiaalit-vs-tallennus
  (let [app app-tallennustestille
        tyyppi :kokonaishintainen
        tehtavat nil
        ;; app (e! (tiedot/->TallennaToimenpide (:avattu-toimenpide app)))
        toimenpide (:avattu-toimenpide app)
        tallennettava-toimenpide (toimenpiteet/tallennettava-toimenpide tehtavat toimenpide (-> app :valinnat :urakka) tyyppi)]

    (tarkista-map-arvot tallennettava-vertailumap tallennettava-toimenpide)))

(deftest materiaalit-vs-AsetaLomakkeenToimenpiteenTiedot
  (let [state {:urakan-materiaalit '({::materiaali/urakka-id 1
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
                                      ::materiaali/nimi "Naulat"
                                      ::materiaali/yksikko "kpl"}
                                      {::materiaali/urakka-id 1
                                       ::materiaali/toimenpide 2
                                       ::materiaali/muutokset [{::materiaali/maara 500
                                                                ::materiaali/id 8}
                                                               {::materiaali/maara -12
                                                                ::materiaali/lisatieto "Käytetty häiriötilanteessa 10.12.2017 kohteessa Pälli"
                                                                ::materiaali/id 12
                                                                ::materiaali/toimenpide 2}]
                                       ::materiaali/nimi "Ämpäreitä"
                                       ::materiaali/yksikko "kpl"})}
        kysely {::kanavan-toimenpide/id 2
                ::kanavan-toimenpide/luotu (pvm/luo-pvm 2017 11 10)}
        vastaus {:urakan-materiaalit (:urakan-materiaalit state)
                 :avattu-toimenpide {::kanavan-toimenpide/id 2
                                     ::kanavan-toimenpide/luotu (pvm/luo-pvm 2017 11 10)
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

    (tarkista-map-arvot (:avattu-toimenpide vastaus) (:avattu-toimenpide (e! (tiedot/->AsetaLomakkeenToimenpiteenTiedot kysely) state)))))
