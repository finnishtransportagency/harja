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
                :infolaatikko-nakyvissa {}
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
                                            ::va/vaylanro 1}
                                ::to/tyoluokka :asennus-ja-huolto
                                ::to/toimenpide :huoltotyo
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                               {::to/id 1
                                ::to/tyolaji :viitat
                                ::to/vayla {::va/nimi "Kuopio, Iisalmen väylä"
                                            ::va/vaylanro 1}
                                ::to/tyoluokka :asennus-ja-huolto
                                ::to/toimenpide :huoltotyo
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}
                                :valittu? true}
                               {::to/id 2
                                ::to/tyolaji :viitat
                                ::to/vayla {::va/nimi "Kuopio, Iisalmen väylä"
                                            ::va/vaylanro 1}
                                ::to/tyoluokka :asennus-ja-huolto
                                ::to/toimenpide :huoltotyo
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                               {::to/id 3
                                ::to/tyolaji :viitat
                                ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                            ::va/vaylanro 2}
                                ::to/tyoluokka :asennus-ja-huolto
                                ::to/toimenpide :huoltotyo
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                               {::to/id 4
                                ::to/tyolaji :kiinteat
                                ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                            ::va/vaylanro 2}
                                ::to/tyoluokka :asennus-ja-huolto
                                ::to/toimenpide :huoltotyo
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                               {::to/id 5
                                ::to/tyolaji :poijut
                                ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                            ::va/vaylanro 2}
                                ::to/tyoluokka :asennus-ja-huolto
                                ::to/toimenpide :huoltotyo
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                               {::to/id 6
                                ::to/tyolaji :poijut
                                ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                            ::va/vaylanro 2}
                                ::to/tyoluokka :asennus-ja-huolto
                                ::to/toimenpide :huoltotyo
                                ::to/pvm (pvm/nyt)
                                ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}]})

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
      (is (false? (tiedot/mitaan-ei-valittu? kaikki-valittu)))
      (is (false? (tiedot/mitaan-ei-valittu? osa-valittu)))
      (is (true? (tiedot/mitaan-ei-valittu? mitaan-ei-valittu))))

    (testing "joku-valittu?"
      (is (true? (tiedot/joku-valittu? kaikki-valittu)))
      (is (true? (tiedot/joku-valittu? osa-valittu)))
      (is (false? (tiedot/joku-valittu? mitaan-ei-valittu))))

    (testing "Checkboxin tila"
      (is (true? (tiedot/valinnan-tila kaikki-valittu)))
      (is (false? (tiedot/valinnan-tila mitaan-ei-valittu)))
      (is (= indeterminate (tiedot/valinnan-tila osa-valittu))))))


(deftest valitut-toimenpiteet
  (is (= '({:id 1 :valittu? true}
            {:id 3 :valittu? true}
            {:id 4 :valittu? true})
         (tiedot/valitut-toimenpiteet [{:id 1 :valittu? true}
                                       {:id 2 :valittu? false}
                                       {:id 3 :valittu? true}
                                       {:id 4 :valittu? true}
                                       {:id 5 :valittu? false}]))))

(deftest poista-toimenpiteet
  (is (= '({::to/id 1}
            {::to/id 3}
            {::to/id 4})
         (tiedot/poista-toimenpiteet '({::to/id 1}
                                        {::to/id 2}
                                        {::to/id 3}
                                        {::to/id 4}
                                        {::to/id 5})
                                     #{2 5}))))

(deftest toimenpiteiden-toiminto-str
  (is (= "2 toimenpidettä heitetty." (tiedot/toimenpiteiden-toiminto-suoritettu 2 "heitetty")))
  (is (= "1 toimenpide keitetty." (tiedot/toimenpiteiden-toiminto-suoritettu 1 "keitetty"))))

(deftest tilan-yhdistaminen

  (testing "Tilan yhdistäminen muuttaa vain valintoja"
    (let [a (atom {:id 1 :foo :bar})
         b (atom {:id 2 :baz :barbaz})]
     (is (= a (tiedot/yhdista-tilat! a b)) "Funktio palauttaa ensimmäisen atomin")
     (is (= @a @(tiedot/yhdista-tilat! a b)) "Sisältö ei muuttunut kutsun aikana")))

  (testing "Tilan yhdistäminen yhdistää :valinnat mäpin"
    (let [a (atom {:id 1 :valinnat {:sopimus 1 :urakka 1}})
          b (atom {:id 2 :baz {:urakka 2 :organisaatio 2}})]
      (is (= a (tiedot/yhdista-tilat! a b)) "Funktio palauttaa ensimmäisen atomin"))

    (let [a (atom {:id 1 :valinnat {:sopimus 1 :urakka 1}})
          b (atom {:id 2 :valinnat {:urakka 2 :organisaatio 2}})]
      (is (= {:id 1 :valinnat {:sopimus 1 :urakka 2 :organisaatio 2}}
            @(tiedot/yhdista-tilat! a b)) ":valinnat avain yhdistettiin"))))

(deftest korosta-kartalla?
  (let [korosta? (tiedot/korosta-turvalaite-kartalla? {:korostetut-turvalaitteet #{1 2}})]
    (is (fn? korosta?))
    (is (true? (korosta? {::tu/turvalaitenro 1})))
    (is (false? (korosta? {::tu/turvalaitenro 3}))))

  (let [korosta? (tiedot/korosta-turvalaite-kartalla? {:korostetut-turvalaitteet nil})]
    (is (fn? korosta?))
    (is (false? (korosta? {::tu/turvalaitenro 1})))
    (is (false? (korosta? {::tu/turvalaitenro 3})))))

(deftest turvalaitteen-toimenpiteet
  (let [tulos (tiedot/turvalaitteen-toimenpiteet {::tu/turvalaitenro 1}
                                                 {:toimenpiteet [{::to/turvalaite {::tu/turvalaitenro 1}
                                                                  ::to/id 1}
                                                                 {::to/turvalaite {::tu/turvalaitenro 1}
                                                                  ::to/id 2}
                                                                 {::to/turvalaite {::tu/turvalaitenro 2}
                                                                  ::to/id 3}]})]
    (is (= [{::to/turvalaite {::tu/turvalaitenro 1}
             ::to/id 1}
            {::to/turvalaite {::tu/turvalaitenro 1}
             ::to/id 2}]
           tulos))
    ;; Infopaneelissa kaivetaan tuloksesta kamaa indeksillä, siksi vektori eikä lista
    (is (vector? tulos) "Turvalaitteiden toimenpiteiden pitää olla vektori, infopaneelin takia.")))

(deftest kartalla-naytettavat
  (let [tu [{::tu/turvalaitenro 1} {::tu/turvalaitenro 2} ::tu/turvalaitenro 3]]
    (is (= tu (tiedot/kartalla-naytettavat-turvalaitteet tu {:korostetut-turvalaitteet nil})))
    (is (= tu (tiedot/kartalla-naytettavat-turvalaitteet tu {:korostetut-turvalaitteet #{}})))

    (is (= [{::tu/turvalaitenro 1} {::tu/turvalaitenro 2}]
           (tiedot/kartalla-naytettavat-turvalaitteet tu {:korostetut-turvalaitteet #{1 2}})))))

(deftest turvalaitteet-kartalle
  (let [laitteet [{::tu/turvalaitenro 1
                   ::tu/koordinaatit {:type :point, :coordinates [367529.053512741 7288034.99009309]}}
                  {::tu/turvalaitenro 2
                   ::tu/koordinaatit {:type :point, :coordinates [367529.053512741 7288034.99009309]}}]
        tila {:toimenpiteet [{::to/turvalaite {::tu/turvalaitenro 1}
                              ::to/id 1}
                             {::to/turvalaite {::tu/turvalaitenro 1}
                              ::to/id 2}
                             {::to/turvalaite {::tu/turvalaitenro 2}
                              ::to/id 3}]}
        tulos (tiedot/turvalaitteet-kartalle laitteet tila)]
    (is (or
          (= [{::to/turvalaite {::tu/turvalaitenro 1}
               ::to/id 1}
              {::to/turvalaite {::tu/turvalaitenro 1}
               ::to/id 2}]
             (:toimenpiteet (first tulos)))
          (= [{::to/turvalaite {::tu/turvalaitenro 1}
               ::to/id 1}
              {::to/turvalaite {::tu/turvalaitenro 1}
               ::to/id 2}]
             (:toimenpiteet (second tulos)))))))

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

(deftest rivien-valinta
  (testing "Rivien valitseminen"
    (is (= {:toimenpiteet [{:valittu? true ::to/id 1}
                           {:valittu? false ::to/id 2}
                           {:valittu? true ::to/id 3}
                           {:valittu? false ::to/id 4}
                           {:valittu? true ::to/id 5}]}
           (e! (tiedot/->ValitseToimenpiteet
                 true
                 [{::to/id 1}
                  {::to/id 3}
                  {::to/id 5}])
               {:toimenpiteet [{:valittu? false ::to/id 1}
                               {:valittu? false ::to/id 2}
                               {:valittu? false ::to/id 3}
                               {:valittu? false ::to/id 4}
                               {:valittu? false ::to/id 5}]}))))

  (testing "Rivien valinnan poistaminen"
    (is (= {:toimenpiteet [{:valittu? false ::to/id 1}
                           {:valittu? true ::to/id 2}
                           {:valittu? false ::to/id 3}
                           {:valittu? true ::to/id 4}
                           {:valittu? false ::to/id 5}]}
           (e! (tiedot/->ValitseToimenpiteet
                 false
                 [{::to/id 1}
                  {::to/id 3}
                  {::to/id 5}])
               {:toimenpiteet [{:valittu? false ::to/id 1}
                               {:valittu? true ::to/id 2}
                               {:valittu? true ::to/id 3}
                               {:valittu? true ::to/id 4}
                               {:valittu? false ::to/id 5}]})))))

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

(deftest vaylan-rivien-valinta
  (testing "Valitaan Iisalmen väylä"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->ValitseVayla {:vaylanro 1
                                                :valinta true}
                                               (:toimenpiteet vanha-tila))
                        vanha-tila)
          viitat (to/toimenpiteet-vaylalla (:toimenpiteet uusi-tila) 1)]
      (is (every? true? (map :valittu? viitat)))))

  (testing "Asetetaan valinnat pois Iisalmen väylältä"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->ValitseVayla {:vaylanro 1
                                                :valinta false}
                                               (:toimenpiteet vanha-tila))
                        vanha-tila)
          viitat (to/toimenpiteet-vaylalla (:toimenpiteet uusi-tila) 1)]
      (is (every? false? (map :valittu? viitat))))))

(deftest infolaatikon-tila
  (testing "Asetetaan infolaatikko näkyviin"
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->AsetaInfolaatikonTila :listaus-1 true []) vanha-tila)]
      (is (nil? (:listaus-1 (:infolaatikko-nakyvissa vanha-tila))))
      (is (true? (:listaus-1 (:infolaatikko-nakyvissa uusi-tila))))))

  (testing "Lisäfunktiot ajetaan kun infolaatikon tila asetetaan"
    (let [vanha-tila (assoc testitila :numero 0)
          mun-inc (fn [app] (update app :numero inc))
          uusi-tila (e! (tiedot/->AsetaInfolaatikonTila :listaus-1 true [mun-inc mun-inc]) vanha-tila)]
      (is (nil? (:listaus-1 (:infolaatikko-nakyvissa vanha-tila))))
      (is (= 2 (:numero uusi-tila)))
      (is (true? (:listaus-1 (:infolaatikko-nakyvissa uusi-tila)))))))

(deftest toimenpiteet-siirretty
  (is (= {:toimenpiteet [{::to/id 2}]
          :siirto-kaynnissa? false}
         (e! (tiedot/->ToimenpiteetSiirretty #{1}) {:toimenpiteet [{::to/id 1} {::to/id 2}]}))))

(deftest toimenpiteet-ei-siirretty
  (is (= {:siirto-kaynnissa? false}
         (e! (tiedot/->ToimenpiteetEiSiirretty)))))

(deftest liitteen-lisaaminen-toimenpiteelle
  (vaadi-async-kutsut
    #{tiedot/->LiiteLisatty tiedot/->LiiteEiLisatty}
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->LisaaToimenpiteelleLiite
                          {:liite {:id 1}
                           ::to/id 1})
                        vanha-tila)]
      (is (true? (:liitteen-lisays-kaynnissa? uusi-tila))))))

(deftest liite-lisatty
  (testing "Uusi liite lisätty toimenpiteelle"
    (let [vanha-tila testitila
          toimenpide-id 2
          uusi-tila (e! (tiedot/->LiiteLisatty
                          nil
                          {:liite {:id 1}
                           ::to/id toimenpide-id})
                        vanha-tila)]
      (is (false? (:liitteen-lisays-kaynnissa? uusi-tila)))
      ;; Liitteen tiedot lisättiin oikealle toimenpiteelle
      (is (= (to/toimenpide-idlla (:toimenpiteet uusi-tila) toimenpide-id)
             (-> (to/toimenpide-idlla (:toimenpiteet uusi-tila) toimenpide-id)
                 (assoc ::to/liitteet [{:id 1}])))))))

(deftest liite-ei-lisatty
  (let [vanha-tila testitila
        uusi-tila (e! (tiedot/->LiiteEiLisatty)
                      vanha-tila)]
    (is (false? (:liitteen-lisays-kaynnissa? uusi-tila)))))

(deftest liitteen-poistaminen-toimenpiteelta
  (vaadi-async-kutsut
    #{tiedot/->LiitePoistettu tiedot/->LiiteEiPoistettu}
    (let [vanha-tila testitila
          uusi-tila (e! (tiedot/->PoistaToimenpiteenLiite
                          {::to/liite-id 666
                           ::to/id 6})
                        vanha-tila)]
      (is (true? (:liitteen-poisto-kaynnissa? uusi-tila))))))

(deftest liite-poistettu
  (testing "Uusi liite lisätty toimenpiteelle"
    (let [vanha-tila testitila
          toimenpide-id 6
          uusi-tila (e! (tiedot/->LiitePoistettu
                          nil
                          {::to/liite-id 666
                           ::to/id toimenpide-id})
                        vanha-tila)]
      (is (false? (:liitteen-poisto-kaynnissa? uusi-tila)))
      ;; Liitteen tiedot poistetaan oikealta toimenpiteeltä
      (is (= (to/toimenpide-idlla (:toimenpiteet uusi-tila) toimenpide-id)
             (-> (to/toimenpide-idlla (:toimenpiteet uusi-tila) toimenpide-id)
                 (assoc ::to/liitteet [])))))))

(deftest liite-ei-poistettu
  (let [vanha-tila testitila
        uusi-tila (e! (tiedot/->LiiteEiPoistettu)
                      vanha-tila)]
    (is (false? (:liitteen-poisto-kaynnissa? uusi-tila)))))

(deftest turvalaitteet-kartalle-event
  (testing "Turvalaitteiden hakemisen aloitus"
    (vaadi-async-kutsut
     #{tiedot/->TurvalaitteetKartalleHaettu tiedot/->TurvalaitteetKartalleEiHaettu}

     (is (= {:kartalle-haettavat-toimenpiteet #{1 2}}
            (e! (tiedot/->HaeToimenpiteidenTurvalaitteetKartalle [{::to/turvalaite {::tu/turvalaitenro 1}}
                                                                  {::to/turvalaite {::tu/turvalaitenro 1}}
                                                                  {::to/turvalaite {::tu/turvalaitenro 2}}]))))))

  (testing "Kartan tyhjennys kun toimenpidelista on tyhjä"
    (is (= {:kartalle-haettavat-toimenpiteet nil
            :turvalaitteet-kartalla nil
            :turvalaitteet nil
            :korostetut-turvalaitteet nil}
           (e! (tiedot/->HaeToimenpiteidenTurvalaitteetKartalle []))))

    (is (= {:kartalle-haettavat-toimenpiteet nil
            :turvalaitteet-kartalla nil
            :turvalaitteet nil
            :korostetut-turvalaitteet nil}
           (e! (tiedot/->HaeToimenpiteidenTurvalaitteetKartalle nil)))))

  (testing "Haun valmistuminen"
    (let [payload [{::tu/koordinaatit {:type :point, :coordinates [367529.053512741 7288034.99009309]}}]
          tulos (e! (tiedot/->TurvalaitteetKartalleHaettu payload #{1 2})
                    {:kartalle-haettavat-toimenpiteet #{1 2}})]
      (is (nil? (:kartalle-haettavat-toimenpiteet tulos)))
      (is (= payload (:turvalaitteet tulos)))
      (is (nil? (:korostetut-turvalaitteet tulos)))
      (is (not-empty (:turvalaitteet-kartalla tulos)))))

  (testing "Vanhentuneen haun valmistuminen"
    (is (= {:kartalle-haettavat-toimenpiteet #{1 2}}
          (e! (tiedot/->TurvalaitteetKartalleHaettu {} #{3 2})
              {:kartalle-haettavat-toimenpiteet #{1 2}}))))

  (testing "Haun epäonnistuminen"
    (is (= {:kartalle-haettavat-toimenpiteet nil
            :turvalaitteet-kartalla nil
            :turvalaitteet nil
            :korostetut-turvalaitteet nil}
           (e! (tiedot/->TurvalaitteetKartalleEiHaettu nil #{1 2})
               {:kartalle-haettavat-toimenpiteet #{1 2}}))))

  (testing "Vanhentuneen haun epäonnistuminen"
    (is (= {:kartalle-haettavat-toimenpiteet #{1 2}}
          (e! (tiedot/->TurvalaitteetKartalleEiHaettu nil #{3 2})
              {:kartalle-haettavat-toimenpiteet #{1 2}})))))

(deftest toimenpiteen-korostaminen
  (let [mun-inc (fn [app] (update app :numero inc))
        tulos (e! (tiedot/->KorostaToimenpideKartalla {::to/turvalaite {::tu/turvalaitenro 1}}
                                                      [mun-inc mun-inc])
                  {:numero 0
                   :turvalaitteet [{::tu/turvalaitenro 1
                                    ::tu/koordinaatit {:type :point, :coordinates [367529.053512741 7288034.99009309]}}]})]
    (is (= 2 (:numero tulos)))
    (is (= #{1} (:korostetut-turvalaitteet tulos)))
    (is (= [{:harja.domain.vesivaylat.turvalaite/turvalaitenro 1,
             :tyyppi-kartalla :turvalaite,
             :sijainti {:type :point, :coordinates [367529.053512741 7288034.99009309]},
             :toimenpiteet [], :type :turvalaite, :nimi "Turvalaite",
             :selite {:teksti "", :img "images/tuplarajat/pinnit/pinni-musta.svg"},
             :alue {:scale 1, :img "images/tuplarajat/pinnit/pinni-musta.svg",
                    :type :merkki,
                    :color nil
                    :coordinates '(367529.053512741 7288034.99009309)}}]
           (:turvalaitteet-kartalla tulos)))))

(deftest valittujen-siirto
  (vaadi-async-kutsut
    #{tiedot/->ToimenpiteetSiirretty tiedot/->ToimenpiteetEiSiirretty}
    (is (= {:siirto-kaynnissa? true}
           (tiedot/siirra-valitut! :foo {})))))

(deftest toimenpiteiden-vaylat
  (testing "Valitaan toimenpiteiden väylät"
    (is (= (to/toimenpiteiden-vaylat (:toimenpiteet testitila))
           [{::va/nimi "Kuopio, Iisalmen väylä"
             ::va/vaylanro 1}
            {::va/nimi "Varkaus, Kuopion väylä"
             ::va/vaylanro 2}]))))
