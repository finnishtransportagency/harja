(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset-test
  (:require [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.loki :refer [log]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]
            [harja.pvm :as pvm]
            [harja.domain.toteuma :as tot]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.vesivaylat.vayla :as va]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.tyo :as tyo]
            [harja.domain.vesivaylat.hinta :as hinta]
            [harja.domain.urakka :as u]
            [cljs-time.core :as t]
            [cljs.spec.alpha :as s]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as jaetut-tiedot]))

(def testitila {:nakymassa? true
                :infolaatikko-nakyvissa {}
                :valinnat {:urakka-id nil
                           :sopimus-id nil
                           :aikavali [nil nil]
                           :vaylatyyppi :kauppamerenkulku
                           :vayla nil
                           :tyolaji :kiintea
                           :tyoluokka :kuljetuskaluston-huolto-ja-kunnossapito
                           :toimenpide :alukset-ja-veneet}
                :hintaryhmat [{::h/id 666
                               ::h/hinnat [{::hinta/id 1
                                            ::hinta/otsikko tiedot/hintaryhman-hintakentta-otsikko
                                            ::hinta/maara 600
                                            ::hinta/yleiskustannuslisa 0}]}]
                :hinnoittele-toimenpide {::to/id nil
                                         ::h/hinnat nil}
                :hinnoittele-hintaryhma {::h/id nil
                                         ::h/hinnat nil}
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
                                ::to/oma-hinnoittelu {::h/hinnat [{::hinta/id 1
                                                                   ::hinta/otsikko "Komponentit"
                                                                   ::hinta/maara 1
                                                                   ::hinta/yleiskustannuslisa 0}
                                                                  {::hinta/id 2
                                                                   ::hinta/otsikko "Yleiset materiaalit"
                                                                   ::hinta/maara 2
                                                                   ::hinta/yleiskustannuslisa 0}
                                                                  {::hinta/id 3
                                                                   ::hinta/otsikko "Matkakulut"
                                                                   ::hinta/maara 3
                                                                   ::hinta/yleiskustannuslisa 0}
                                                                  {::hinta/id 4
                                                                   ::hinta/otsikko "Muut kulut"
                                                                   ::hinta/maara 4
                                                                   ::hinta/yleiskustannuslisa 12}
                                                                  {::hinta/id 5
                                                                   ::hinta/otsikko "Päivän hinta"
                                                                   ::hinta/maara 30}]
                                                      ::h/tyot [{::tyo/id 1
                                                                 ::tyo/toimenpidekoodi-id 1
                                                                 ::tyo/maara 60}]}
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
                                ::to/liitteet [{:id 666}]
                                ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}]})

(deftest nakymaan-tuleminen
  (is (= {:nakymassa? true
          :karttataso-nakyvissa? true}
         (e! (tiedot/->Nakymassa? true))))
  (is (= {:nakymassa? false
          :karttataso-nakyvissa? false}
         (e! (tiedot/->Nakymassa? false)))))

(deftest valintojen-paivittaminen
  (testing "Asetetaan uudet valinnat"
    (vaadi-async-kutsut
      #{tiedot/->HaeToimenpiteet}
      (let [vanha-tila testitila
            uusi-tila (e! (tiedot/->PaivitaValinnat {:urakka-id 666
                                                     :sopimus-id 777
                                                     :aikavali [(t/now) (t/now)]
                                                     :vaylatyyppi :muu
                                                     :vayla 1
                                                     :tyolaji :poijut
                                                     :tyoluokka :asennus-ja-huolto
                                                     :toimenpide :autot-traktorit})
                          vanha-tila)]
        (is (nil? (get-in vanha-tila [:valinnat :urakka-id])))
        (is (= (get-in uusi-tila [:valinnat :urakka-id]) 666))

        (is (= (get-in vanha-tila [:valinnat :aikavali]) [nil nil]))
        (is (not= (get-in uusi-tila [:valinnat :aikavali]) [nil nil]))

        (is (nil? (get-in vanha-tila [:valinnat :sopimus-id])))
        (is (= (get-in uusi-tila [:valinnat :sopimus-id]) 777))

        (is (= (get-in vanha-tila [:valinnat :vaylatyyppi]) :kauppamerenkulku))
        (is (= (get-in uusi-tila [:valinnat :vaylatyyppi]) :muu))

        (is (nil? (get-in vanha-tila [:valinnat :vayla])))
        (is (= (get-in uusi-tila [:valinnat :vayla]) 1))

        (is (= (get-in vanha-tila [:valinnat :tyolaji]) :kiintea))
        (is (= (get-in uusi-tila [:valinnat :tyolaji]) :poijut))

        (is (= (get-in vanha-tila [:valinnat :tyoluokka]) :kuljetuskaluston-huolto-ja-kunnossapito))
        (is (= (get-in uusi-tila [:valinnat :tyoluokka]) :asennus-ja-huolto))

        (is (= (get-in vanha-tila [:valinnat :toimenpide]) :alukset-ja-veneet))
        (is (= (get-in uusi-tila [:valinnat :toimenpide]) :autot-traktorit)))))

  (testing "Asetetaan vain yksi valinta"
    (vaadi-async-kutsut
      #{tiedot/->HaeToimenpiteet}
      (let [vanha-tila {}
            uusi-tila (e! (tiedot/->PaivitaValinnat {:vaylatyyppi :muu
                                                     :foo :bar})
                          vanha-tila)]
        (is (nil? (:valinnat vanha-tila)))
        (is (= (:valinnat uusi-tila) {:vaylatyyppi :muu}))))))

(deftest toimenpiteiden-hakemisen-aloitus
  (testing "Haun aloittaminen"
    (vaadi-async-kutsut
      #{tiedot/->ToimenpiteetHaettu tiedot/->ToimenpiteetEiHaettu}

      (is (true? (:toimenpiteiden-haku-kaynnissa? (e! (tiedot/->HaeToimenpiteet {:urakka-id 1})))))))

  (testing "Uusi haku kun haku on jo käynnissä"
    (vaadi-async-kutsut
      ;; Ei saa aloittaa uusia hakuja
      #{}

      (let [tila {:foo :bar :id 1 :toimenpiteiden-haku-kaynnissa? true}]
        (is (= tila (e! (tiedot/->HaeToimenpiteet {}) tila)))))))

(deftest toimenpiteiden-hakemisen-valmistuminen
  (vaadi-async-kutsut
    #{jaetut-tiedot/->HaeToimenpiteidenTurvalaitteetKartalle}

    (let [tulos (e! (tiedot/->ToimenpiteetHaettu [{:id 1}]) {:toimenpiteet []})]
      (is (false? (:toimenpiteiden-haku-kaynnissa? tulos)))
      (is (= [{:id 1 ::to/hintaryhma-id -1}] (:toimenpiteet tulos)))))

  (vaadi-async-kutsut
    #{jaetut-tiedot/->HaeToimenpiteidenTurvalaitteetKartalle}
    (let [tulos (e! (tiedot/->ToimenpiteetHaettu [{:id 1 ::to/reimari-lisatyo? true}]) {:toimenpiteet []})]
      (is (false? (:toimenpiteiden-haku-kaynnissa? tulos)))
      (is (= [{::to/reimari-lisatyo? true :id 1 ::to/hintaryhma-id -2}] (:toimenpiteet tulos)))))

  (vaadi-async-kutsut
    #{jaetut-tiedot/->HaeToimenpiteidenTurvalaitteetKartalle}
    (let [tulos (e! (tiedot/->ToimenpiteetHaettu [{:id 1 ::to/reimari-lisatyo? false}]) {:toimenpiteet []})]
      (is (false? (:toimenpiteiden-haku-kaynnissa? tulos)))
      (is (= [{::to/reimari-lisatyo? false :id 1 ::to/hintaryhma-id -1}] (:toimenpiteet tulos)))))

  (vaadi-async-kutsut
    #{jaetut-tiedot/->HaeToimenpiteidenTurvalaitteetKartalle}
    (let [tulos (e! (tiedot/->ToimenpiteetHaettu [{:id 1 ::to/reimari-lisatyo? true
                                                   ::to/hintaryhma-id 3}]) {:toimenpiteet []})]
      (is (false? (:toimenpiteiden-haku-kaynnissa? tulos)))
      (is (= [{::to/reimari-lisatyo? true :id 1 ::to/hintaryhma-id 3}] (:toimenpiteet tulos))))))

(deftest toimenpiteiden-hakemisen-epaonnistuminen
  (let [tulos (e! (tiedot/->ToimenpiteetEiHaettu nil))]
    (is (false? (:toimenpiteiden-haku-kaynnissa? tulos)))))

(deftest uuden-hintaryhman-lisays
  (is (= {:foo :bar :uuden-hintaryhman-lisays? true}
         (e! (tiedot/->UudenHintaryhmanLisays? true) {:foo :bar})))
  (is (= {:foo :bar :uuden-hintaryhman-lisays? false}
         (e! (tiedot/->UudenHintaryhmanLisays? false) {:foo :bar}))))

(deftest hintaryhman-nimen-paivitys
  (is (= {:foo :bar :uusi-hintaryhma "Bar"}
         (e! (tiedot/->UudenHintaryhmanNimeaPaivitetty "Bar") {:foo :bar}))))

(deftest kokonaishintaisiin-siirto
  (testing "Siirron aloittaminen"
    (vaadi-async-kutsut
      #{jaetut-tiedot/->ToimenpiteetSiirretty jaetut-tiedot/->ToimenpiteetEiSiirretty}
      (let [vanha-tila testitila
            uusi-tila (e! (tiedot/->SiirraValitutKokonaishintaisiin)
                          vanha-tila)]
        (is (true? (:siirto-kaynnissa? uusi-tila)))))))

(deftest kokonaishintaisiin-siirretty
  (let [vanha-tila testitila
        siirretyt #{1 2 3}
        toimenpiteiden-lkm-ennen-testia (count (:toimenpiteet vanha-tila))
        uusi-tila (e! (jaetut-tiedot/->ToimenpiteetSiirretty siirretyt)
                      vanha-tila)
        toimenpiteiden-lkm-testin-jalkeen (count (:toimenpiteet uusi-tila))]

    (is (= toimenpiteiden-lkm-ennen-testia (+ toimenpiteiden-lkm-testin-jalkeen (count siirretyt))))
    (is (empty? (filter #(siirretyt (::to/id %))
                        (:toimenpiteet uusi-tila)))
        "Uudessa tilassa ei ole enää siirrettyjä toimenpiteitä")))

(deftest hintaryhman-luonti
  (vaadi-async-kutsut
    #{tiedot/->HintaryhmaLuotu tiedot/->HintaryhmaEiLuotu}

    (is (= {:hintaryhman-tallennus-kaynnissa? true}
           (e! (tiedot/->LuoHintaryhma :foo)))))

  (testing "Haku ei lähde uudestaan"
    (let [app {:foo :bar :hintaryhman-tallennus-kaynnissa? true}]
      (is (= app (e! (tiedot/->LuoHintaryhma :bar) app))))))

(deftest hintaryhma-luotu
  (let [app {:hintaryhman-tallennus-kaynnissa? false
             :uusi-hintaryhma nil
             :uuden-hintaryhman-lisays? false
             :hintaryhmat [{:id 1}]}]
    (is (= {:hintaryhman-tallennus-kaynnissa? false
            :uusi-hintaryhma nil
            :uuden-hintaryhman-lisays? false
            :hintaryhmat [{:id 1} {:id 2}]}
           (e! (tiedot/->HintaryhmaLuotu {:id 2}) app)))))

(deftest hintaryhmaa-ei-luotu
  (let [app {:hintaryhman-tallennus-kaynnissa? false
             :uusi-hintaryhma nil
             :uuden-hintaryhman-lisays? false
             :hintaryhmat [{:id 1}]}]
    (is (= {:hintaryhman-tallennus-kaynnissa? false
            :uusi-hintaryhma nil
            :uuden-hintaryhman-lisays? false
            :hintaryhmat [{:id 1}]}
           (e! (tiedot/->HintaryhmaEiLuotu {:msg :error}) app)))))

(deftest hintaryhmien-haku
  (vaadi-async-kutsut
    #{tiedot/->HintaryhmatHaettu tiedot/->HintaryhmatEiHaettu}

    (is (= {:valinnat {:urakka-id 1}
            :hintaryhmien-haku-kaynnissa? true}
           (e! (tiedot/->HaeHintaryhmat) {:valinnat {:urakka-id 1}}))))

  (testing "Haku ei lähde uudestaan"
    (let [app {:foo :bar :hintaryhmien-haku-kaynnissa? true}]
      (is (= app (e! (tiedot/->HaeHintaryhmat) app))))))

(deftest hintaryhma-haettu
  (is (= {:hintaryhmat [{:id 1}]
          :hintaryhmien-haku-kaynnissa? false}
         (e! (tiedot/->HintaryhmatHaettu [{:id 1}])))))

(deftest hintaryhmaa-ei-haettu
  (is (= {:hintaryhmien-haku-kaynnissa? false} (e! (tiedot/->HintaryhmatEiHaettu {:msg :error})))))

(deftest hintaryhman-valinta
  (is (= {:valittu-hintaryhma {:id 1}}
         (e! (tiedot/->ValitseHintaryhma {:id 1})))))

(deftest hintaryhmaan-liittaminen
  (vaadi-async-kutsut
    #{tiedot/->ValitutLiitettyHintaryhmaan tiedot/->ValitutEiLiitettyHintaryhmaan}

    (is (= {:hintaryhmien-liittaminen-kaynnissa? true}
           (e! (tiedot/->LiitaValitutHintaryhmaan {::h/id 1} [{::to/id 1}])
               {:hintaryhmien-liittaminen-kaynnissa? false}))))

  (let [app {:hintaryhmien-liittaminen-kaynnissa? true :foo :bar}]
    (is (= app (e! (tiedot/->LiitaValitutHintaryhmaan 1 2) app)))))

(deftest hintaryhmaan-liitetty
  (vaadi-async-kutsut
    #{tiedot/->HaeToimenpiteet tiedot/->HaeHintaryhmat}

    (is (= {:hintaryhmien-liittaminen-kaynnissa? false}
           (e! (tiedot/->ValitutLiitettyHintaryhmaan))))))

(deftest hintaryhmaan-ei-liitetty
  (is (= {:hintaryhmien-liittaminen-kaynnissa? false}
         (e! (tiedot/->ValitutEiLiitettyHintaryhmaan {:msg :error})))))

(deftest poista-hintaryhmat
  (vaadi-async-kutsut
    #{tiedot/->HintaryhmatPoistettu tiedot/->HintaryhmatEiPoistettu}

    (testing "Poista hintaryhmät"
      (is (= (e! (tiedot/->PoistaHintaryhmat #{1 2 3})
                 {:hintaryhmien-poisto-kaynnissa? false})
             {:hintaryhmien-poisto-kaynnissa? true}))))

  (testing "Poisto on jo käynnissä"
    (let [app {:hintaryhmien-poisto-kaynnissa? true :foo :bar}]
      (is (= app (e! (tiedot/->PoistaHintaryhmat #{1 2 3}) app))))))

(deftest hintaryhmat-poistettu
  (is (= (e! (tiedot/->HintaryhmatPoistettu {::h/idt #{2}})
             {:hintaryhmien-poisto-kaynnissa? true
              :hintaryhmat [{::h/id 1} {::h/id 2} {::h/id 3}]})
         ;; Poistetut poistuu sovelluksen tilasta
         {:hintaryhmien-poisto-kaynnissa? false
          :hintaryhmat [{::h/id 1} {::h/id 3}]})))

(deftest hintaryhmat-ei-poistettu
  (is (= {:hintaryhmien-poisto-kaynnissa? false}
         (e! (tiedot/->HintaryhmatEiPoistettu)))))

(deftest toimenpiteen-hinnoittelu
  (testing "Aloita toimenpiteen hinnoittelu, ei aiempia hinnoittelutietoja"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->AloitaToimenpiteenHinnoittelu 0)
                        vanha-tila)]
      (is (nil? (get-in vanha-tila [:hinnoittele-toimenpide ::to/id])))
      (is (= (:hinnoittele-toimenpide uusi-tila)
             {::to/id 0
              ::h/hinnat
              [{::hinta/id nil
                ::hinta/otsikko "Komponentit"
                ::hinta/maara 0
                ::hinta/yleiskustannuslisa 0}
               {::hinta/id nil
                ::hinta/otsikko
                "Yleiset materiaalit"
                ::hinta/maara 0
                ::hinta/yleiskustannuslisa 0}
               {::hinta/id nil
                ::hinta/otsikko "Matkakulut"
                ::hinta/maara 0
                ::hinta/yleiskustannuslisa 0}
               {::hinta/id nil
                ::hinta/otsikko "Muut kulut"
                ::hinta/maara 0
                ::hinta/yleiskustannuslisa 0}]
              ::h/tyot []}))))

  (testing "Aloita toimenpiteen hinnoittelu, aiemmat hinnoittelutiedot olemassa"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->AloitaToimenpiteenHinnoittelu 1)
                        vanha-tila)]
      (is (nil? (get-in vanha-tila [:hinnoittele-toimenpide ::to/id])))
      (is (= (:hinnoittele-toimenpide uusi-tila)
             {::to/id 1
              ::h/hinnat
              [{::hinta/id 1
                ::hinta/otsikko "Komponentit"
                ::hinta/maara 1
                ::hinta/yleiskustannuslisa 0}
               {::hinta/id 2
                ::hinta/otsikko "Yleiset materiaalit"
                ::hinta/maara 2
                ::hinta/yleiskustannuslisa 0}
               {::hinta/id 3
                ::hinta/otsikko "Matkakulut"
                ::hinta/maara 3
                ::hinta/yleiskustannuslisa 0}
               {::hinta/id 4
                ::hinta/otsikko "Muut kulut"
                ::hinta/maara 4
                ::hinta/yleiskustannuslisa 12}]
              ::h/tyot [{:toimenpidekoodi-id 1
                         :hinta-nimi nil
                         :maara 60}
                        {:toimenpidekoodi-id nil
                         :hinta-nimi "Päivän hinta"
                         :maara 30}]})))))

(deftest hintaryhman-hinnoittelu
  (testing "Aloita hintaryhmän hinnoittelu, ei aiempia hinnoittelutietoja"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->AloitaHintaryhmanHinnoittelu 1)
                        vanha-tila)]
      (is (nil? (get-in vanha-tila [:hinnoittele-hintaryhma ::h/id])))
      (is (= (:hinnoittele-hintaryhma uusi-tila)
             {::h/id 1
              ::h/hinnat
              [{::hinta/id nil
                ::hinta/otsikko tiedot/hintaryhman-hintakentta-otsikko
                ::hinta/maara 0
                ::hinta/yleiskustannuslisa 0}]}))))

  (testing "Aloita hintaryhmän hinnoittelu, aiemmat hinnoittelutiedot olemassa"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->AloitaHintaryhmanHinnoittelu 666)
                        vanha-tila)]
      (is (nil? (get-in vanha-tila [:hinnoittele-hintaryhma ::h/id])))
      (is (= (:hinnoittele-hintaryhma uusi-tila)
             {::h/id 666
              ::h/hinnat
              [{::hinta/id 1
                ::hinta/otsikko tiedot/hintaryhman-hintakentta-otsikko
                ::hinta/maara 600
                ::hinta/yleiskustannuslisa 0}]})))))

(deftest toimenpiteen-kentan-hinnoittelu
  (testing "Hinnoittele kentän rahamäärä"
    (let [vanha-tila testitila
          uusi-tila (->> (e! (tiedot/->AloitaToimenpiteenHinnoittelu 1) vanha-tila)
                         (e! (tiedot/->HinnoitteleToimenpideKentta {::hinta/otsikko "Yleiset materiaalit"
                                                                    ::hinta/maara 666})))]
      (is (nil? (get-in vanha-tila [:hinnoittele-toimenpide ::h/hinnat])))
      (is (= (:hinnoittele-toimenpide uusi-tila)
             {::to/id 1
              ::h/hinnat
              [{::hinta/id 1
                ::hinta/otsikko "Komponentit"
                ::hinta/maara 1
                ::hinta/yleiskustannuslisa 0}
               {::hinta/id 2
                ::hinta/otsikko "Yleiset materiaalit"
                ::hinta/maara 666
                ::hinta/yleiskustannuslisa 0}
               {::hinta/id 3
                ::hinta/otsikko "Matkakulut"
                ::hinta/maara 3
                ::hinta/yleiskustannuslisa 0}
               {::hinta/id 4
                ::hinta/otsikko "Muut kulut"
                ::hinta/maara 4
                ::hinta/yleiskustannuslisa 12}]
              ::h/tyot [{:toimenpidekoodi-id 1
                         :hinta-nimi nil
                         :maara 60}
                        {:toimenpidekoodi-id nil
                         :hinta-nimi "Päivän hinta"
                         :maara 30}]}))))

  (testing "Hinnoittele kentän yleiskustannuslisä"
    (let [vanha-tila testitila
          uusi-tila (->> (e! (tiedot/->AloitaToimenpiteenHinnoittelu 1) vanha-tila)
                         (e! (tiedot/->HinnoitteleToimenpideKentta {::hinta/otsikko "Yleiset materiaalit"
                                                                    ::hinta/yleiskustannuslisa 12})))]
      (is (nil? (get-in vanha-tila [:hinnoittele-toimenpide ::h/hinnat])))
      (is (= (:hinnoittele-toimenpide uusi-tila)
             {::to/id 1
              ::h/hinnat
              [{::hinta/id 1
                ::hinta/otsikko "Komponentit"
                ::hinta/maara 1
                ::hinta/yleiskustannuslisa 0}
               {::hinta/id 2
                ::hinta/otsikko "Yleiset materiaalit"
                ::hinta/maara 2
                ::hinta/yleiskustannuslisa 12}
               {::hinta/id 3
                ::hinta/otsikko "Matkakulut"
                ::hinta/maara 3
                ::hinta/yleiskustannuslisa 0}
               {::hinta/id 4
                ::hinta/otsikko "Muut kulut"
                ::hinta/maara 4
                ::hinta/yleiskustannuslisa 12}]
              ::h/tyot [{:toimenpidekoodi-id 1
                         :hinta-nimi nil
                         :maara 60}
                        {:toimenpidekoodi-id nil
                         :hinta-nimi "Päivän hinta"
                         :maara 30}]})))))

(deftest hintaryhman-kentan-hinnoittelu
  (testing "Hinnoittele hintaryhmän kentän rahamäärä"
    (let [vanha-tila testitila
          uusi-tila (->> (e! (tiedot/->AloitaHintaryhmanHinnoittelu 666) vanha-tila)
                         (e! (tiedot/->HinnoitteleHintaryhmaKentta {::hinta/otsikko tiedot/hintaryhman-hintakentta-otsikko
                                                                    ::hinta/maara 123})))]
      (is (nil? (get-in vanha-tila [:hinnoittele-toimenpide ::h/hinnat])))
      (is (= (:hinnoittele-hintaryhma uusi-tila)
             {::h/id 666
              ::h/hinnat
              [{::hinta/id 1
                ::hinta/otsikko tiedot/hintaryhman-hintakentta-otsikko
                ::hinta/maara 123
                ::hinta/yleiskustannuslisa 0}]})))))

(deftest toimenpiteen-hinnoittelun-tallennus
  (vaadi-async-kutsut
    #{tiedot/->ToimenpiteenHinnoitteluTallennettu
      tiedot/->ToimenpiteenHinnoitteluEiTallennettu}

    (is (= {:toimenpiteen-hinnoittelun-tallennus-kaynnissa? true}
           (e! (tiedot/->HinnoitteleToimenpide 1)
               {:toimenpiteen-hinnoittelun-tallennus-kaynnissa? false})))))

(deftest hintaryhman-hinnoittelun-tallennus
  (vaadi-async-kutsut
    #{tiedot/->HintaryhmanHinnoitteluTallennettu
      tiedot/->HintaryhmanHinnoitteluEiTallennettu}

    (is (= {:hintaryhman-hinnoittelun-tallennus-kaynnissa? true}
           (e! (tiedot/->HinnoitteleHintaryhma 1)
               {:hintaryhman-hinnoittelun-tallennus-kaynnissa? false})))))

(deftest toimenpiteen-hinnoittelu-tallennettu
  (let [hinnoiteltava-toimenpide-id 1
        ;; Asetetaan hinnoittelu päälle ja testataan miten tila muuttuu,
        ;; kun saadaan tallennukseen vastaus
        vanha-tila (assoc testitila
                     :toimenpiteen-hinnoittelun-tallennus-kaynnissa? true
                     :hinnoittele-toimenpide
                     {::to/id hinnoiteltava-toimenpide-id
                      ::h/hinnat
                      [{::hinta/otsikko "Komponentit"
                        ::hinta/maara 20
                        ::hinta/yleiskustannuslisa 0}
                       {::hinta/otsikko "Yleiset materiaalit"
                        ::hinta/maara 30
                        ::hinta/yleiskustannuslisa 0}
                       {::hinta/otsikko "Matkakulut"
                        ::hinta/maara 40
                        ::hinta/yleiskustannuslisa 0}
                       {::hinta/otsikko "Muut kulut"
                        ::hinta/maara 50
                        ::hinta/yleiskustannuslisa 0}]})
        uusi-tila (e! (tiedot/->ToimenpiteenHinnoitteluTallennettu
                        {::h/hinnat
                         [{::hinta/otsikko "Komponentit"
                           ::hinta/maara 20
                           ::hinta/yleiskustannuslisa 0}
                          {::hinta/otsikko "Yleiset materiaalit"
                           ::hinta/maara 30
                           ::hinta/yleiskustannuslisa 0}
                          {::hinta/otsikko "Matkakulut"
                           ::hinta/maara 40
                           ::hinta/yleiskustannuslisa 0}
                          {::hinta/otsikko "Muut kulut"
                           ::hinta/maara 50
                           ::hinta/yleiskustannuslisa 0}]
                         ::h/hintaryhma? false
                         ::h/id 666
                         ::h/nimi "Hinnoittelu"
                         :harja.domain.muokkaustiedot/poistettu? false})
                      vanha-tila)
        paivitettu-toimenpide (first (filter #(= (::to/id %) hinnoiteltava-toimenpide-id)
                                             (:toimenpiteet uusi-tila)))]

    ;; Hinnoittelu ei ole enää päällä
    (is (false? (:toimenpiteen-hinnoittelun-tallennus-kaynnissa? uusi-tila)))
    (is (nil? (get-in uusi-tila [:hinnoittele-toimenpide ::to/id])))
    (is (nil? (get-in uusi-tila [:hinnoittele-toimenpide ::h/hinnat])))

    ;; Toimenpiteeseen päivittyi uudet hinnoitteutiedot
    (is (= (::to/oma-hinnoittelu paivitettu-toimenpide)
           {::h/hinnat
            [{::hinta/otsikko "Komponentit"
              ::hinta/maara 20
              ::hinta/yleiskustannuslisa 0}
             {::hinta/otsikko "Yleiset materiaalit"
              ::hinta/maara 30
              ::hinta/yleiskustannuslisa 0}
             {::hinta/otsikko "Matkakulut"
              ::hinta/maara 40
              ::hinta/yleiskustannuslisa 0}
             {::hinta/otsikko "Muut kulut"
              ::hinta/maara 50
              ::hinta/yleiskustannuslisa 0}]
            ::h/hintaryhma? false
            ::h/id 666
            ::h/nimi "Hinnoittelu"
            :harja.domain.muokkaustiedot/poistettu? false}))))

(deftest hintaryhman-hinnoittelu-tallennettu
  (let [hinnoiteltava-hintaryhma-id 1
        ;; Asetetaan hinnoittelu päälle ja testataan miten tila muuttuu,
        ;; kun saadaan tallennukseen vastaus
        vanha-tila (assoc testitila
                     :hinnoittele-hintaryhma
                     :hintaryhman-hinnoittelun-tallennus-kaynnissa? true
                     {::h/id hinnoiteltava-hintaryhma-id
                      ::h/hinnat
                      [{::hinta/otsikko "Ryhmähinta"
                        ::hinta/maara 123
                        ::hinta/yleiskustannuslisa 0}]})
        palvelimen-vastaus {::h/hinnat [{::hinta/yleiskustannuslisa 0
                                         ::hinta/maara 123
                                         ::hinta/otsikko "Ryhmähinta"
                                         ::hinta/id 1}]
                            ::h/hintaryhma? true
                            ::h/nimi "Hietasaaren poijujen korjaus"
                            ::h/id 9}
        uusi-tila (e! (tiedot/->HintaryhmanHinnoitteluTallennettu
                        palvelimen-vastaus)
                      vanha-tila)
        paivitetyt-hintaryhmat (:hintaryhmat uusi-tila)]

    ;; Hinnoittelu ei ole enää päällä
    (is (false? (:hintaryhman-hinnoittelun-tallennus-kaynnissa? uusi-tila)))
    (is (nil? (get-in uusi-tila [:hinnoittele-hintaryhma ::h/id])))
    (is (nil? (get-in uusi-tila [:hinnoittele-hintaryhma ::h/hinnat])))

    ;; Hintaryhmiksi asetettiin palvelimen vastaus
    (is (= paivitetyt-hintaryhmat palvelimen-vastaus))))

(deftest toimenpiteen-hinnoittelu-ei-tallennettu
  (is (= {:toimenpiteen-hinnoittelun-tallennus-kaynnissa? false}
         (e! (tiedot/->ToimenpiteenHinnoitteluEiTallennettu {:msg :error})
             {:toimenpiteen-hinnoittelun-tallennus-kaynnissa? true}))))

(deftest hintaryhman-hinnoittelu-ei-tallennettu
  (is (= {:hintaryhman-hinnoittelun-tallennus-kaynnissa? false}
         (e! (tiedot/->HintaryhmanHinnoitteluEiTallennettu {:msg :error})
             {:hintaryhman-hinnoittelun-tallennus-kaynnissa? true}))))

(deftest toimenpiteen-hinnoittelun-peruminen
  (let [vanha-tila testitila
        uusi-tila (e! (tiedot/->PeruToimenpiteenHinnoittelu)
                      vanha-tila)]
    (is (nil? (get-in uusi-tila [:hinnoittele-toimenpide ::h/hinnat])))))

(deftest hintaryhman-hinnoittelun-peruminen
  (let [vanha-tila testitila
        uusi-tila (e! (tiedot/->PeruHintaryhmanHinnoittelu)
                      vanha-tila)]
    (is (nil? (get-in uusi-tila [:hinnoittele-hintaryhma ::h/hinnat])))))

(deftest hintaryhman-korostaminen
  (testing "Hintaryhmän korostus"
    (let [tulos (e! (tiedot/->KorostaHintaryhmaKartalla {::h/id 1})
                    {:turvalaitteet [{::tu/turvalaitenro 1
                                      ::tu/sijainti {:type :point, :coordinates [367529.053512741 7288034.99009309]}}]
                     :toimenpiteet [{::to/hintaryhma-id 1 ::to/turvalaite {::tu/turvalaitenro 1}}
                                    {::to/hintaryhma-id 1 ::to/turvalaite {::tu/turvalaitenro 2}}
                                    {::to/hintaryhma-id 2 ::to/turvalaite {::tu/turvalaitenro 1}}]})]
      (is (= 1 (:korostettu-hintaryhma tulos)))
      (is (= #{1 2} (:korostetut-turvalaitteet tulos)))
      (is (not-empty (:turvalaitteet-kartalla tulos)))))

  (testing "Hintaryhmän korostamisen poistaminen"
    (let [tulos (e! (tiedot/->PoistaHintaryhmanKorostus))]
      ;; false, koska näkymässä on hintaryhmä jonka id on nil
      (is (= false (:korostettu-hintaryhma tulos)))
      (is (nil? (:korostetut-turvalaitteet tulos))))))

(deftest toimenpiteiden-vaylat
  (testing "Valitaan toimenpiteiden väylät"
    (is (= (to/toimenpiteiden-vaylat (:toimenpiteet testitila))
           [{::va/nimi "Kuopio, Iisalmen väylä"
             ::va/id 1}
            {::va/nimi "Varkaus, Kuopion väylä"
             ::va/id 2}]))))

(deftest hintaryhma-korostettu?
  (is (true? (tiedot/hintaryhma-korostettu? {::h/id 1} {:korostettu-hintaryhma 1})))
  (is (true? (tiedot/hintaryhma-korostettu? {::h/id nil} {:korostettu-hintaryhma nil})))
  (is (false? (tiedot/hintaryhma-korostettu? {::h/id 2} {:korostettu-hintaryhma 1})))
  (is (false? (tiedot/hintaryhma-korostettu? {::h/id 1} {:korostettu-hintaryhma 2})))
  (is (false? (tiedot/hintaryhma-korostettu? {::h/id 1} {:korostettu-hintaryhma false})))
  (is (false? (tiedot/hintaryhma-korostettu? {::h/id false} {:korostettu-hintaryhma false}))))

(deftest tunnista-kok-hint-siirretty-ryhma
  (is (true? (tiedot/kokonaishintaisista-siirretyt-hintaryhma? {::h/id -1})))
  (is (false? (tiedot/kokonaishintaisista-siirretyt-hintaryhma? {::h/id 1})))
  (is (false? (tiedot/kokonaishintaisista-siirretyt-hintaryhma? {::h/id nil}))))

(deftest tunnista-reimarin-lisatyot-ryhma
  (is (true? (tiedot/reimarin-lisatyot-hintaryhma? {::h/id -2})))
  (is (false? (tiedot/reimarin-lisatyot-hintaryhma? {::h/id 2})))
  (is (false? (tiedot/reimarin-lisatyot-hintaryhma? {::h/id nil}))))

(deftest tunnista-valiaikainen-ryhma
  (is (true? (tiedot/valiaikainen-hintaryhma? {::h/id -2})))
  (is (true? (tiedot/valiaikainen-hintaryhma? {::h/id -1})))
  (is (false? (tiedot/valiaikainen-hintaryhma? {::h/id nil})))
  (is (false? (tiedot/valiaikainen-hintaryhma? {::h/id 2})))
  (is (false? (tiedot/valiaikainen-hintaryhma? nil))))

(deftest toimenpiteet-valiaikaisiin-ryhmiin
  (is (= [1 -1 -2 -1]
         (mapv ::to/hintaryhma-id
               (tiedot/hintaryhmattomat-toimenpiteet-valiaikaisiin-ryhmiin
                 [{::to/hintaryhma-id 1}
                  {::to/hintaryhma-id nil}
                  {::to/hintaryhma-id nil ::to/reimari-lisatyo? true}
                  {::to/hintaryhma-id nil ::to/reimari-lisatyo? false}])))))

(deftest poista-ryhman-korostus
  (is (= {:korostettu-hintaryhma false} (tiedot/poista-hintaryhmien-korostus {})))
  (is (= {:korostettu-hintaryhma false} (tiedot/poista-hintaryhmien-korostus {:korostettu-hintaryhma false})))
  (is (= {:korostettu-hintaryhma false} (tiedot/poista-hintaryhmien-korostus {:korostettu-hintaryhma true}))))

(deftest suunniteltujen-toiden-tyhjennys
  (is (= (e! (tiedot/->TyhjennaSuunnitellutTyot)
             {:suunnitellut-tyot [1 2 3]})
         {:suunnitellut-tyot nil})))

(deftest suunniteltujen-toiden-haku
  (testing "Haun aloittaminen"
    (vaadi-async-kutsut
      #{tiedot/->SuunnitellutTyotHaettu tiedot/->SuunnitellutTyotEiHaettu}

      (is (true? (:suunniteltujen-toiden-haku-kaynnissa? (e! (tiedot/->HaeSuunnitellutTyot)
                                                             {:valinnat {:urakka-id 1}}))))))

  (testing "Uusi haku kun haku on jo käynnissä"
    (vaadi-async-kutsut
      ;; Ei saa aloittaa uusia hakuja
      #{}

      (let [tila {:suunniteltujen-toiden-haku-kaynnissa? true
                  :valinnat {:urakka-id 1}}]
        (is (= tila
               (e! (tiedot/->HaeSuunnitellutTyot) tila)))))))

(deftest suunniteltujen-toiden-hakemisen-valmistuminen
  (is (= (e! (tiedot/->SuunnitellutTyotHaettu [{:id 1}])
             {:suunniteltujen-toiden-haku-kaynnissa? true})
         {:suunniteltujen-toiden-haku-kaynnissa? false
          :suunnitellut-tyot [{:id 1}]})))

(deftest suunniteltujen-toiden-hakemisen-epaonnistuminen
  (is (= (e! (tiedot/->SuunnitellutTyotEiHaettu)
             {:suunniteltujen-toiden-haku-kaynnissa? true})
         {:suunniteltujen-toiden-haku-kaynnissa? false})))

(deftest lisaa-hinnoiteltava-tyorivi
  (is (= (e! (tiedot/->LisaaHinnoiteltavaTyorivi))
         {:hinnoittele-toimenpide {::h/tyot [{:maara 0}]}})))

(deftest poista-hinnoiteltava-tyorivi
  (is (= (e! (tiedot/->PoistaHinnoiteltavaTyorivi {:index 1})
             {:hinnoittele-toimenpide {::h/tyot [{:maara 0}
                                                 {:maara 1}
                                                 {:maara 2}]}})
         {:hinnoittele-toimenpide {::h/tyot [{:maara 0}
                                             {:maara 2}]}})))

(deftest aseta-tyoriville-tiedot
  (is (= (e! (tiedot/->AsetaTyorivilleTiedot {:index 1
                                              :maara 666
                                              :toimenpidekoodi-id 1})
             {:hinnoittele-toimenpide {::h/tyot [{:maara 0}
                                                 {:maara 1}
                                                 {:maara 2}]}})
         {:hinnoittele-toimenpide {::h/tyot [{:maara 0}
                                             {:maara 666
                                              :toimenpidekoodi-id 1}
                                             {:maara 2}]}})))