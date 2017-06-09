(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset-test
  (:require [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.loki :refer [log]]
            [harja.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]
            [harja.pvm :as pvm]
            [harja.domain.toteuma :as tot]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.vesivaylat.vayla :as va]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.hinta :as hinta]
            [harja.domain.urakka :as u]
            [cljs-time.core :as t]
            [cljs.spec.alpha :as s]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as jaetut-tiedot]))

(def testitila {:nakymassa? true
                :infolaatikko-nakyvissa? false
                :valinnat {:urakka-id nil
                           :sopimus-id nil
                           :aikavali [nil nil]
                           :vaylatyyppi :kauppamerenkulku
                           :vayla nil
                           :tyolaji :kiintea
                           :tyoluokka :kuljetuskaluston-huolto-ja-kunnossapito
                           :toimenpide :alukset-ja-veneet}
                :hinnoittele-toimenpide {::to/id nil
                                         ::h/hintaelementit nil}
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
                                ::to/oma-hinnoittelu {::h/hinnat [{::hinta/id 0
                                                                   ::hinta/otsikko "Työ"
                                                                   ::hinta/maara 0
                                                                   ::hinta/yleiskustannuslisa 0}
                                                                  {::hinta/id 1
                                                                   ::hinta/otsikko "Komponentit"
                                                                   ::hinta/maara 1
                                                                   ::hinta/yleiskustannuslisa 0}
                                                                  {::hinta/id 2
                                                                   ::hinta/otsikko "Yleiset materiaalit"
                                                                   ::hinta/maara 2
                                                                   ::hinta/yleiskustannuslisa 0}
                                                                  {::hinta/id 3
                                                                   ::hinta/otsikko "Matkat"
                                                                   ::hinta/maara 3
                                                                   ::hinta/yleiskustannuslisa 0}
                                                                  {::hinta/id 4
                                                                   ::hinta/otsikko "Muut kulut"
                                                                   ::hinta/maara 4
                                                                   ::hinta/yleiskustannuslisa 12}]}
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

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))

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

(deftest toimenpiteiden-vaylat
  (testing "Valitaan toimenpiteiden väylät"
    (is (= (to/toimenpiteiden-vaylat (:toimenpiteet testitila))
           [{::va/nimi "Kuopio, Iisalmen väylä"
             ::va/id 1}
            {::va/nimi "Varkaus, Kuopion väylä"
             ::va/id 2}]))))

(deftest hakuargumenttien-muodostus
  (testing "Hakuargumenttien muodostus toimii"
    (let [alku (t/now)
          loppu (t/plus (t/now) (t/days 5))
          hakuargumentit (tiedot/kyselyn-hakuargumentit {:urakka-id 666
                                                         :sopimus-id 777
                                                         :aikavali [alku loppu]
                                                         :vaylatyyppi :muu
                                                         :vayla 1
                                                         :tyolaji :poijut
                                                         :tyoluokka :asennus-ja-huolto
                                                         :toimenpide :autot-traktorit
                                                         :vain-vikailmoitukset? true})]
      (is (= (dissoc hakuargumentit :alku :loppu)
             {::to/urakka-id 666
              ::to/sopimus-id 777
              ::va/vaylatyyppi :muu
              ::to/vayla-id 1
              ::to/reimari-tyolaji (to/reimari-tyolaji-avain->koodi :poijut)
              ::to/reimari-tyoluokat (to/reimari-tyoluokka-avain->koodi :asennus-ja-huolto)
              ::to/reimari-toimenpidetyypit (to/reimari-toimenpidetyyppi-avain->koodi :autot-traktorit)
              :vikailmoitukset? true
              :tyyppi :yksikkohintainen}))
      (is (pvm/sama-pvm? (:alku hakuargumentit) alku))
      (is (pvm/sama-pvm? (:loppu hakuargumentit) loppu))
      (is (s/valid? ::to/hae-vesivaylien-toimenpiteet-kysely hakuargumentit))))

  (testing "Kaikki-valinta toimii"
    (let [hakuargumentit (tiedot/kyselyn-hakuargumentit {:urakka-id 666
                                                         :sopimus-id 777
                                                         :tyolaji nil
                                                         :tyoluokka nil
                                                         :toimenpide nil})]
      (is (= hakuargumentit
             {::to/urakka-id 666
              ::to/sopimus-id 777
              :tyyppi :yksikkohintainen}))
      (is (s/valid? ::to/hae-vesivaylien-toimenpiteet-kysely hakuargumentit))))

  (testing "Hakuargumenttien muodostus toimii vajailla argumenteilla"
    (let [hakuargumentit (tiedot/kyselyn-hakuargumentit {:urakka-id 666
                                                         :sopimus-id 777})]
      (is (= hakuargumentit {::to/urakka-id 666
                             ::to/sopimus-id 777
                             :tyyppi :yksikkohintainen}))
      (is (s/valid? ::to/hae-vesivaylien-toimenpiteet-kysely hakuargumentit)))))

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

(deftest hakemisen-aloitus
  (testing "Haun aloittaminen"
    (vaadi-async-kutsut
      #{tiedot/->ToimenpiteetHaettu tiedot/->ToimenpiteetEiHaettu}

      (is (true? (:haku-kaynnissa? (e! (tiedot/->HaeToimenpiteet {:urakka-id 1})))))))

  (testing "Uusi haku kun haku on jo käynnissä"
    (vaadi-async-kutsut
      ;; Ei saa aloittaa uusia hakuja
      #{}

      (let [tila {:foo :bar :id 1 :haku-kaynnissa? true}]
        (is (= tila (e! (tiedot/->HaeToimenpiteet {}) tila)))))))

(deftest toimenpiteen-hinnoittelu
  (testing "Aloita toimenpiteen hinnoittelu, ei aiempia hinnoittelutietoja"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->AloitaToimenpiteenHinnoittelu 0)
                        vanha-tila)]
      (is (nil? (get-in vanha-tila [:hinnoittele-toimenpide ::to/id])))
      (is (= (:hinnoittele-toimenpide uusi-tila)
             {::to/id 0
              ::h/hintaelementit
              [{::hinta/id nil
                ::hinta/otsikko "Työ"
                ::hinta/maara 0
                ::hinta/yleiskustannuslisa false}
               {::hinta/id nil
                ::hinta/otsikko "Komponentit"
                ::hinta/maara 0
                ::hinta/yleiskustannuslisa false}
               {::hinta/id nil
                ::hinta/otsikko
                "Yleiset materiaalit"
                ::hinta/maara 0
                ::hinta/yleiskustannuslisa false}
               {::hinta/id nil
                ::hinta/otsikko "Matkat"
                ::hinta/maara 0
                ::hinta/yleiskustannuslisa false}
               {::hinta/id nil
                ::hinta/otsikko "Muut kulut"
                ::hinta/maara 0
                ::hinta/yleiskustannuslisa false}]}))))

  (testing "Aloita toimenpiteen hinnoittelu, aiemmat hinnoittelutiedot olemassa"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->AloitaToimenpiteenHinnoittelu 1)
                        vanha-tila)]
      (is (nil? (get-in vanha-tila [:hinnoittele-toimenpide ::to/id])))
      (is (= (:hinnoittele-toimenpide uusi-tila)
             {::to/id 1
              ::h/hintaelementit
              [{::hinta/id 0
                ::hinta/otsikko "Työ"
                ::hinta/maara 0
                ::hinta/yleiskustannuslisa false}
               {::hinta/id 1
                ::hinta/otsikko "Komponentit"
                ::hinta/maara 1
                ::hinta/yleiskustannuslisa false}
               {::hinta/id 2
                ::hinta/otsikko "Yleiset materiaalit"
                ::hinta/maara 2
                ::hinta/yleiskustannuslisa false}
               {::hinta/id 3
                ::hinta/otsikko "Matkat"
                ::hinta/maara 3
                ::hinta/yleiskustannuslisa false}
               {::hinta/id 4
                ::hinta/otsikko "Muut kulut"
                ::hinta/maara 4
                ::hinta/yleiskustannuslisa true}]})))))

(deftest toimenpiteen-kentan-hinnoittelu
  (testing "Hinnoittele kentän rahamäärä"
    (let [vanha-tila testitila
          uusi-tila (->> (e! (tiedot/->AloitaToimenpiteenHinnoittelu 1) vanha-tila)
                         (e! (tiedot/->HinnoitteleToimenpideKentta {::hinta/otsikko "Yleiset materiaalit"
                                                                    ::hinta/maara 666})))]
      (is (nil? (get-in vanha-tila [:hinnoittele-toimenpide ::h/hintaelementit])))
      (is (= (:hinnoittele-toimenpide uusi-tila)
             {::to/id 1
              ::h/hintaelementit
              [{::hinta/id 0
                ::hinta/otsikko "Työ"
                ::hinta/maara 0
                ::hinta/yleiskustannuslisa false}
               {::hinta/id 1
                ::hinta/otsikko "Komponentit"
                ::hinta/maara 1
                ::hinta/yleiskustannuslisa false}
               {::hinta/id 2
                ::hinta/otsikko "Yleiset materiaalit"
                ::hinta/maara 666
                ::hinta/yleiskustannuslisa false}
               {::hinta/id 3
                ::hinta/otsikko "Matkat"
                ::hinta/maara 3
                ::hinta/yleiskustannuslisa false}
               {::hinta/id 4
                ::hinta/otsikko "Muut kulut"
                ::hinta/maara 4
                ::hinta/yleiskustannuslisa true}]}))))

  (testing "Hinnoittele kentän yleiskustannuslisä"
    (let [vanha-tila testitila
          uusi-tila (->> (e! (tiedot/->AloitaToimenpiteenHinnoittelu 1) vanha-tila)
                         (e! (tiedot/->HinnoitteleToimenpideKentta {::hinta/otsikko "Yleiset materiaalit"
                                                                    ::hinta/yleiskustannuslisa true})))]
      (is (nil? (get-in vanha-tila [:hinnoittele-toimenpide ::h/hintaelementit])))
      (is (= (:hinnoittele-toimenpide uusi-tila)
             {::to/id 1
              ::h/hintaelementit
              [{::hinta/id 0
                ::hinta/otsikko "Työ"
                ::hinta/maara 0
                ::hinta/yleiskustannuslisa false}
               {::hinta/id 1
                ::hinta/otsikko "Komponentit"
                ::hinta/maara 1
                ::hinta/yleiskustannuslisa false}
               {::hinta/id 2
                ::hinta/otsikko "Yleiset materiaalit"
                ::hinta/maara 2
                ::hinta/yleiskustannuslisa true}
               {::hinta/id 3
                ::hinta/otsikko "Matkat"
                ::hinta/maara 3
                ::hinta/yleiskustannuslisa false}
               {::hinta/id 4
                ::hinta/otsikko "Muut kulut"
                ::hinta/maara 4
                ::hinta/yleiskustannuslisa true}]}))))

  (testing "Peru hinnoittelu"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->PeruToimenpiteenHinnoittelu)
                        vanha-tila)]
      (is (nil? (get-in uusi-tila [:hinnoittele-toimenpide ::h/hintaelementit]))))))

(deftest toimenpiteen-hinnoittelu-tallennettu
  (testing "Toimenpiteen hinnoittelu tallennettu"
    (let [vanha-tila (assoc testitila
                       :hinnoittele-toimenpide
                       {:harja.domain.vesivaylat.toimenpide/id 1
                        :harja.domain.vesivaylat.hinnoittelu/hintaelementit
                        [{:harja.domain.vesivaylat.hinta/otsikko "Työ"
                          :harja.domain.vesivaylat.hinta/maara 10
                          :harja.domain.vesivaylat.hinta/yleiskustannuslisa false}
                         {:harja.domain.vesivaylat.hinta/otsikko "Komponentit"
                          :harja.domain.vesivaylat.hinta/maara 20
                          :harja.domain.vesivaylat.hinta/yleiskustannuslisa false}
                         {:harja.domain.vesivaylat.hinta/otsikko "Yleiset materiaalit"
                          :harja.domain.vesivaylat.hinta/maara 30
                          :harja.domain.vesivaylat.hinta/yleiskustannuslisa false}
                         {:harja.domain.vesivaylat.hinta/otsikko "Matkat"
                          :harja.domain.vesivaylat.hinta/maara 40
                          :harja.domain.vesivaylat.hinta/yleiskustannuslisa false}
                         {:harja.domain.vesivaylat.hinta/otsikko "Muut kulut"
                          :harja.domain.vesivaylat.hinta/maara 50
                          :harja.domain.vesivaylat.hinta/yleiskustannuslisa false}]})
          uusi-tila (e! (tiedot/->ToimenpiteenHinnoitteluTallennettu
                          {:harja.domain.vesivaylat.hinnoittelu/hinnat
                           [{:harja.domain.vesivaylat.hinta/otsikko "Työ"
                             :harja.domain.vesivaylat.hinta/maara 10
                             :harja.domain.vesivaylat.hinta/yleiskustannuslisa false}
                            {:harja.domain.vesivaylat.hinta/otsikko "Komponentit"
                             :harja.domain.vesivaylat.hinta/maara 20
                             :harja.domain.vesivaylat.hinta/yleiskustannuslisa false}
                            {:harja.domain.vesivaylat.hinta/otsikko "Yleiset materiaalit"
                             :harja.domain.vesivaylat.hinta/maara 30
                             :harja.domain.vesivaylat.hinta/yleiskustannuslisa false}
                            {:harja.domain.vesivaylat.hinta/otsikko "Matkat"
                             :harja.domain.vesivaylat.hinta/maara 40
                             :harja.domain.vesivaylat.hinta/yleiskustannuslisa false}
                            {:harja.domain.vesivaylat.hinta/otsikko "Muut kulut"
                             :harja.domain.vesivaylat.hinta/maara 50
                             :harja.domain.vesivaylat.hinta/yleiskustannuslisa false}]
                           :harja.domain.vesivaylat.hinnoittelu/hintaryhma? false
                           :harja.domain.vesivaylat.hinnoittelu/id 666
                           :harja.domain.vesivaylat.hinnoittelu/nimi "Hinnoittelu"
                           :harja.domain.muokkaustiedot/poistettu? false})
                        vanha-tila)]

      ;; Hinnoittelu ei ole enää päällä
      (is (false? (:hinnoittelun-tallennus-kaynnissa? uusi-tila)))
      (is (nil? (get-in uusi-tila [:hinnoittele-toimenpide ::to/id])))
      (is (nil? (get-in uusi-tila [:hinnoittele-toimenpide ::h/hintaelementit])))
      ;; TODO TESTAA ETTÄ TALLENNETTU HINNOITTELU TALLENTUI OIKEALLE TOIMENPITEELLE HINTATIEDOIKSI
      ))

  (testing "Peru hinnoittelu"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->PeruToimenpiteenHinnoittelu)
                        vanha-tila)]
      (is (nil? (get-in uusi-tila [:hinnoittele-toimenpide ::h/hintaelementit]))))))

(deftest hakemisen-valmistuminen
  (let [tulos (e! (tiedot/->ToimenpiteetHaettu [{:id 1}]) {:toimenpiteet []})]
    (is (false? (:haku-kaynnissa? tulos)))
    (is (= [{:id 1}] (:toimenpiteet tulos)))))

(deftest hakemisen-epaonnistuminen
  (let [tulos (e! (tiedot/->ToimenpiteetEiHaettu nil))]
    (is (false? (:haku-kaynnissa? tulos)))))