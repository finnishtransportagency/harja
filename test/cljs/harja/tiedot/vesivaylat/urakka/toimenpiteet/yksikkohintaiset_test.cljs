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
                                         :hinnoittelutiedot
                                         [{:nimi "Työ" :tunniste :tyo :arvo 0}
                                          {:nimi "Komponentit" :tunniste :komponentit :arvo 0}
                                          {:nimi "Yleiset materiaalit" :tunniste :yleiset-materiaalit :arvo 0}
                                          {:nimi "Matkat" :tunniste :matkat :arvo 0}
                                          {:nimi "Muut kulut" :tunniste :muut-kulut :arvo 0}]}
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
  (testing "Aloita toimenpiteen hinnoittelu"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->AloitaToimenpiteenHinnoittelu 1))]
    (is (nil? (get-in vanha-tila [:hinnoittele-toimenpide ::to/id])))
    (is (= (:hinnoittele-toimenpide uusi-tila)
           {::to/id 1
            :hinnoittelutiedot
            [{:nimi "Työ" :tunniste :tyo :arvo 0}
             {:nimi "Komponentit" :tunniste :komponentit :arvo 0}
             {:nimi "Yleiset materiaalit" :tunniste :yleiset-materiaalit :arvo 0}
             {:nimi "Matkat" :tunniste :matkat :arvo 0}
             {:nimi "Muut kulut" :tunniste :muut-kulut :arvo 0}]})))))

(deftest toimenpiteen-kentan-hinnoittelu
  (testing "Hinnoittele kenttiä"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->HinnoitteleToimenpideKentta {:tunniste :yleiset-materiaalit :arvo 5})
                        vanha-tila)]
      (is (= (get-in vanha-tila [:hinnoittele-toimenpide :hinnoittelutiedot])
             [{:nimi "Työ" :tunniste :tyo :arvo 0}
              {:nimi "Komponentit" :tunniste :komponentit :arvo 0}
              {:nimi "Yleiset materiaalit" :tunniste :yleiset-materiaalit :arvo 0}
              {:nimi "Matkat" :tunniste :matkat :arvo 0}
              {:nimi "Muut kulut" :tunniste :muut-kulut :arvo 0}]))
      (is (= (get-in uusi-tila [:hinnoittele-toimenpide :hinnoittelutiedot])
             [{:nimi "Työ" :tunniste :tyo :arvo 0}
              {:nimi "Komponentit" :tunniste :komponentit :arvo 0}
              {:nimi "Yleiset materiaalit" :tunniste :yleiset-materiaalit :arvo 5}
              {:nimi "Matkat" :tunniste :matkat :arvo 0}
              {:nimi "Muut kulut" :tunniste :muut-kulut :arvo 0}]))))

  (testing "Peru hinnoittelu"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->PeruToimenpiteenHinnoittelu)
                        vanha-tila)]
      (is (= vanha-tila uusi-tila)))))

(deftest hakemisen-valmistuminen
  (let [tulos (e! (tiedot/->ToimenpiteetHaettu [{:id 1}]) {:toimenpiteet []})]
    (is (false? (:haku-kaynnissa? tulos)))
    (is (= [{:id 1}] (:toimenpiteet tulos)))))

(deftest hakemisen-epaonnistuminen
  (let [tulos (e! (tiedot/->ToimenpiteetEiHaettu nil))]
    (is (false? (:haku-kaynnissa? tulos)))))