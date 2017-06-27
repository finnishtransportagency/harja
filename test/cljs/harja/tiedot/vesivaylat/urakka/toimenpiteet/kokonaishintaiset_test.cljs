(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset-test
  (:require [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as jaetut-tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.loki :refer [log]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]
            [harja.pvm :as pvm]
            [harja.domain.toteuma :as tot]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.vesivaylat.vayla :as va]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [harja.domain.vesivaylat.kiintio :as kiintio]
            [cljs-time.core :as t]
            [cljs.spec.alpha :as s]))

(def testitila {:nakymassa? true
                :infolaatikko-nakyvissa {}
                :valinnat {:urakka-id nil
                           :sopimus-id nil
                           :aikavali [nil nil]
                           :vaylatyyppi :kauppamerenkulku
                           :vayla nil
                           :tyolaji :kiintea
                           :tyoluokka :kuljetuskaluston-huolto-ja-kunnossapito
                           :toimenpide :alukset-ja-veneet
                           :vain-vikailmoitukset? false}
                :toimenpiteet [{::to/id 0
                                ::to/tyolaji :viitat
                                ::to/vayla {::va/nimi "Kuopio, Iisalmen väylä"
                                            ::va/id 1}
                                ::to/tyoluokka :asennus-ja-huolto
                                ::to/toimenpide :huoltotyo
                                ::to/pvm (pvm/nyt)
                                ::to/vikakorjaus true
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
                                                     :toimenpide :autot-traktorit
                                                     :vain-vikailmoitukset? true})
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
        (is (= (get-in uusi-tila [:valinnat :toimenpide]) :autot-traktorit))

        (is (false? (get-in vanha-tila [:valinnat :vain-vikailmoitukset?])))
        (is (true? (get-in uusi-tila [:valinnat :vain-vikailmoitukset?]))))))

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
          hakuargumentit (jaetut-tiedot/toimenpiteiden-hakukyselyn-argumentit
                           {:urakka-id 666
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
              :vikailmoitukset? true}))
      (is (pvm/sama-pvm? (:alku hakuargumentit) alku))
      (is (pvm/sama-pvm? (:loppu hakuargumentit) loppu))
      (is (s/valid? ::to/hae-vesivaylien-toimenpiteet-kysely hakuargumentit))))

  (testing "Kaikki-valinta toimii"
    (let [hakuargumentit (jaetut-tiedot/toimenpiteiden-hakukyselyn-argumentit {:urakka-id 666
                                                                               :sopimus-id 777
                                                                               :tyolaji nil
                                                                               :tyoluokka nil
                                                                               :toimenpide nil})]
      (is (= hakuargumentit
             {::to/urakka-id 666
              ::to/sopimus-id 777}))
      (is (s/valid? ::to/hae-vesivaylien-toimenpiteet-kysely hakuargumentit))))

  (testing "Hakuargumenttien muodostus toimii vajailla argumenteilla"
    (let [hakuargumentit (jaetut-tiedot/toimenpiteiden-hakukyselyn-argumentit {:urakka-id 666
                                                                               :sopimus-id 777})]
      (is (= hakuargumentit {::to/urakka-id 666
                             ::to/sopimus-id 777}))
      (is (s/valid? ::to/hae-vesivaylien-toimenpiteet-kysely hakuargumentit)))))

(deftest yksikkohintaisiin-siirto
  (testing "Siirron aloittaminen"
    (vaadi-async-kutsut
      #{jaetut-tiedot/->ToimenpiteetSiirretty jaetut-tiedot/->ToimenpiteetEiSiirretty}
      (let [vanha-tila testitila
            uusi-tila (e! (tiedot/->SiirraValitutYksikkohintaisiin)
                          vanha-tila)]
        (is (true? (:siirto-kaynnissa? uusi-tila)))))))

(deftest yksikkohintaisiin-siirretty
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
  (let [tulos (e! (tiedot/->ToimenpiteetHaettu [{:id 1}]) {:toimenpiteet []})]
    (is (false? (:toimenpiteiden-haku-kaynnissa? tulos)))
    (is (= [{:id 1}] (:toimenpiteet tulos)))))

(deftest toimenpiteiden-hakemisen-epaonnistuminen
  (let [tulos (e! (tiedot/->ToimenpiteetEiHaettu nil))]
    (is (false? (:toimenpiteiden-haku-kaynnissa? tulos)))))


(deftest kiintioiden-hakemisen-aloitus
  (testing "Haun aloittaminen"
    (vaadi-async-kutsut
      #{tiedot/->KiintiotHaettu tiedot/->KiintiotEiHaettu}

      (is (true? (:kiintioiden-haku-kaynnissa?
                   (e! (tiedot/->HaeKiintiot)))))))

  (testing "Uusi haku kun haku on jo käynnissä"
    (vaadi-async-kutsut
      ;; Ei saa aloittaa uusia hakuja
      #{}

      (let [tila {:foo :bar :id 1 :kiintioiden-haku-kaynnissa? true}]
        (is (= tila (e! (tiedot/->HaeKiintiot) tila)))))))

(deftest kiintioiden-hakemisen-valmistuminen
  (let [tulos (e! (tiedot/->KiintiotHaettu [{:id 1}])
                  {:kiintiot nil})]
    (is (false? (:kiintioiden-haku-kaynnissa? tulos)))
    (is (= [{:id 1}] (:kiintiot tulos)))))

(deftest kiintioiden-hakemisen-epaonnistuminen
  (let [tulos (e! (tiedot/->KiintiotEiHaettu nil))]
    (is (false? (:kiintioiden-haku-kaynnissa? tulos)))))

(deftest valitse-kiintio
  (let [tulos (e! (tiedot/->ValitseKiintio 666))]
    (is (= (:valittu-kiintio-id tulos) 666))))

(deftest liita-toimenpiteet-kiintioon
  (vaadi-async-kutsut
    #{tiedot/->ToimenpiteetLiitettyKiintioon tiedot/->ToimenpiteetEiLiitettyKiintioon}
    (let [tulos (e! (tiedot/->LiitaToimenpiteetKiintioon))]
      (is (true? (:kiintioon-liittaminen-kaynnissa? tulos))))))

(deftest toimenpiteet-liitetty-kiintioon
  (let [tulos (e! (tiedot/->ToimenpiteetLiitettyKiintioon)
                  {:liita-kiintioon 123})]
    (is (false? (:kiintioon-liittaminen-kaynnissa? tulos)))
    (is (nil? (:liita-kiintioon tulos)))))

(deftest toimenpiteet-ei-liitetty-kiintioon
  (let [tulos (e! (tiedot/->ToimenpiteetEiLiitettyKiintioon)
                  {:liita-kiintioon 123})]
    (is (false? (:kiintioon-liittaminen-kaynnissa? tulos)))
    (is (nil? (:liita-kiintioon tulos)))))