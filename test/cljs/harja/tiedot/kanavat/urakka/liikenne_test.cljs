(ns harja.tiedot.kanavat.urakka.liikenne-test
  (:require [harja.tiedot.kanavat.urakka.liikenne :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]

            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.kohdekokonaisuus :as kok]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.lt-toiminto :as toiminto]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohteenosa :as osa]
            [harja.domain.sopimus :as sop]
            [harja.domain.urakka :as ur]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.kayttaja :as kayttaja]
            [harja.ui.modal :as modal]
            [harja.tiedot.navigaatio :as nav]))

(deftest uusi-tapahtuma
  (is (= {::lt/kuittaaja {::kayttaja/id 1}
          ::lt/aika 1234
          ::lt/sopimus {::sop/id :a ::sop/nimi :b}
          ::lt/urakka {::ur/id :foo}}
         (tiedot/uusi-tapahtuma
           (atom {:id 1})
           (atom [:a :b])
           (atom {:id :foo})
           1234))))

(deftest hakuparametrit
  (is (= {:urakka-idt #{1}}
         (tiedot/hakuparametrit {:valinnat {:kayttajan-urakat '({:id 1 :valittu? true} {:id 2 :valittu? false})}})))

  (is (= {:urakka-idt #{1}
          :bar 2}
         (tiedot/hakuparametrit {:valinnat {:kayttajan-urakat '({:id 1 :valittu? true})
                                            :foo nil
                                            :bar 2}}))))

(deftest palvelumuoto-gridiin
  (is (= "Kauko"
         (tiedot/palvelumuoto->str
           {::lt/toiminnot [{::toiminto/palvelumuoto :kauko}
                       {::toiminto/palvelumuoto :kauko}
                       {::toiminto/palvelumuoto :kauko}]})))
  (is (= "Kauko"
         (tiedot/palvelumuoto->str
           {::lt/toiminnot [{::toiminto/palvelumuoto :kauko}
                       {::toiminto/palvelumuoto :kauko}
                       {::toiminto/palvelumuoto :kauko}]})))
  (is (= "Kauko, Paikallis"
         (tiedot/palvelumuoto->str
           {::lt/toiminnot [{::toiminto/palvelumuoto :kauko}
                       {::toiminto/palvelumuoto :paikallis}
                       {::toiminto/palvelumuoto :kauko}]})))
  (is (= "Itsepalvelu (15 kpl), Kauko"
         (tiedot/palvelumuoto->str
           {::lt/toiminnot [{::toiminto/palvelumuoto :kauko}
                       {::toiminto/palvelumuoto :itse
                        ::toiminto/lkm 15}
                       {::toiminto/palvelumuoto :kauko}]})))
  (is (= "Itsepalvelu (150 kpl)"
         (tiedot/palvelumuoto->str
           {::lt/toiminnot [{::toiminto/palvelumuoto :itse
                        ::toiminto/lkm 150}]}))))

(deftest toimenpide-gridiin
  (is (= "Sillan avaus, Sulutus"
         (tiedot/toimenpide->str {::lt/toiminnot [{::toiminto/toimenpide :sulutus}
                                             {::toiminto/toimenpide :avaus}]})))

  (is (= "Ei avausta, Tyhjennys"
         (tiedot/toimenpide->str {::lt/toiminnot [{::toiminto/toimenpide :tyhjennys}
                                             {::toiminto/toimenpide :ei-avausta}]})))
  (is (= "Ei avausta, Sulutus, Tyhjennys"
         (tiedot/toimenpide->str {::lt/toiminnot [{::toiminto/toimenpide :tyhjennys}
                                                  {::toiminto/toimenpide :sulutus}
                                                  {::toiminto/toimenpide :ei-avausta}]})))

  (is (= "Sillan avaus"
         (tiedot/toimenpide->str {::lt/toiminnot [{::toiminto/toimenpide :avaus}]})))

  (is (= "Ei avausta"
         (tiedot/toimenpide->str {::lt/toiminnot [{::toiminto/toimenpide :ei-avausta}]}))))

(deftest tapahtumarivit
  (testing "Jokaisesta nipusta ja aluksesta syntyy oma rivi"
    (let [tapahtuma {:foo :bar
                     ::lt/alukset [{::lt-alus/suunta :ylos
                                    ::lt-alus/nimi "Ronsu"}
                                   {::lt-alus/suunta :ylos
                                    ::lt-alus/nimi "Ransu"}]}]
      (is (= [{::lt-alus/suunta :ylos
               ::lt-alus/nimi "Ronsu"
               :foo :bar
               ::lt/alukset [{::lt-alus/suunta :ylos
                              ::lt-alus/nimi "Ronsu"}
                             {::lt-alus/suunta :ylos
                              ::lt-alus/nimi "Ransu"}]}
              {::lt-alus/suunta :ylos
               ::lt-alus/nimi "Ransu"
               :foo :bar
               ::lt/alukset [{::lt-alus/suunta :ylos
                              ::lt-alus/nimi "Ronsu"}
                             {::lt-alus/suunta :ylos
                              ::lt-alus/nimi "Ransu"}]}]
             (tiedot/tapahtumarivit nil tapahtuma)))))

  (testing "Jos ei aluksia tai nippuja, syntyy silti yksi rivi taulukossa"
    (let [tapahtuma {:foo :bar
                     ::lt/alukset []}]
      (is (= [{:foo :bar
               ::lt/alukset []}]
             (tiedot/tapahtumarivit nil tapahtuma))))))

(deftest koko-tapahtuma
  (is (= {::lt/id 1 :foo :baz}
         (tiedot/koko-tapahtuma {::lt/id 1}
                                {:haetut-tapahtumat [{::lt/id 2 :foo :bar}
                                                     {::lt/id 1 :foo :baz}]}))))

(deftest tallennusparametrien-kasaus
  (is (= {::lt/kuittaaja-id 1
          ::lt/kohde-id 1
          ::lt/urakka-id nil ;; tulee atomista, siksi nil.. :/
          ::lt/sopimus-id 1
          ::lt/alukset [{::m/poistettu? true
                         ::lt-alus/id 1}]
          ::lt/toiminnot [{::toiminto/id 1
                           ::toiminto/toimenpide :ei-avausta
                           ::toiminto/palvelumuoto nil
                           ::toiminto/lkm nil}
                          {::toiminto/id 2
                           ::toiminto/toimenpide :avaus
                           ::toiminto/palvelumuoto :kauko
                           ::toiminto/lkm 1}
                          {::toiminto/id 2
                           ::toiminto/toimenpide :avaus
                           ::toiminto/palvelumuoto :itse
                           ::toiminto/lkm 15}]}
         (tiedot/tallennusparametrit
           {:grid-virheita? false
            ::lt/kuittaaja {::kayttaja/id 1}
            ::lt/kohde {::kohde/id 1}
            ::lt/sopimus {::sop/id 1}
            ::lt/alukset [{:poistettu true
                           ::m/poistettu? false
                           :id 1
                           :harja.ui.grid/virheet []
                           ::lt-alus/id 1}]
            ::lt/toiminnot [{::toiminto/id 1
                             ::toiminto/toimenpide :ei-avausta
                             ::toiminto/palvelumuoto :kauko
                             ::osa/id 1
                             ::toiminto/lkm 15}
                            {::toiminto/id 2
                             ::toiminto/toimenpide :avaus
                             ::toiminto/palvelumuoto :kauko
                             ::toiminto/lkm 15
                             ::osa/id 1}
                            {::toiminto/id 2
                             ::toiminto/toimenpide :avaus
                             ::toiminto/palvelumuoto :itse
                             ::toiminto/lkm 15
                             ::osa/id 1}]}))))

(deftest voiko-tallentaa?
  (is (true? (tiedot/voi-tallentaa? {:grid-virheita? false
                                     ::lt/alukset [{::lt-alus/suunta :ylos
                                                    ::lt-alus/lkm 1
                                                    ::lt-alus/laji :HUV}]})))
  (is (not (tiedot/voi-tallentaa? {:grid-virheita? false
                                     ::lt/alukset []
                                     ::lt/toiminnot [{::toiminto/palvelumuoto :itse}]})))

  (is (false? (tiedot/voi-tallentaa? {:grid-virheita? false
                                     ::lt/alukset []
                                     ::lt/toiminnot [{::toiminto/palvelumuoto :itse}
                                                     {::toiminto/palvelumuoto :kauko}]})))
  (is (false? (tiedot/voi-tallentaa? {:grid-virheita? false
                                     ::lt/alukset [{::lt-alus/suunta :ylos}]})))

  (is (false? (tiedot/voi-tallentaa? {:grid-virheita? true
                                      ::lt/alukset [{::lt-alus/suunta :ylos
                                                     ::lt-alus/laji :HUV}]})))
  (is (false? (tiedot/voi-tallentaa? {:grid-virheita? false
                                      ::lt/alukset [{::lt-alus/suunta :ylos
                                                     ::lt-alus/laji :HUV}]})))
  (is (false? (tiedot/voi-tallentaa? {:grid-virheita? false
                                      ::lt/alukset [{::lt-alus/laji :HUV}]}))))


(deftest sama-alus?
  (is (true? (tiedot/sama-alusrivi? {::lt-alus/id 1} {::lt-alus/id 1})))
  (is (true? (tiedot/sama-alusrivi? {:id 1} {:id 1})))

  (is (false? (tiedot/sama-alusrivi? {::lt-alus/id 2} {::lt-alus/id 1})))
  (is (false? (tiedot/sama-alusrivi? {:id 2} {:id 1})))
  (is (false? (tiedot/sama-alusrivi? {::lt-alus/id 1} {:id 1})))

  (is (false? (tiedot/sama-alusrivi? {::lt-alus/id 1} {:id 1 ::lt-alus/id 2}))))

(deftest osatietojen-paivittaminen
  (is (= {::lt/toiminnot [{::toiminto/kohteenosa-id 1
                      ::toiminto/palvelumuoto :kauko}
                     {::toiminto/kohteenosa-id 2}], ::lt/alukset ()}
         (tiedot/paivita-toiminnon-tiedot
           {::lt/toiminnot [{::toiminto/kohteenosa-id 1}
                       {::toiminto/kohteenosa-id 2}]}
           {::toiminto/kohteenosa-id 1
            ::toiminto/palvelumuoto :kauko}))))

(deftest osatietojen-yhdistaminen
  (testing "Olemassaolevaan yhdistäminen"
    (is (= {::lt/id 1
            ::lt/kohde {::kohde/id 1
                        ::kohde/kohteenosat [{::osa/id 1
                                              ::osa/kohde-id 1
                                              ::osa/tyyppi :silta
                                              ::osa/nimi "Iso silta"
                                              ::osa/oletuspalvelumuoto :kauko}]}
            ::lt/toiminnot [{::lt/id 1
                        ::toiminto/kohteenosa-id 1
                        ::toiminto/kohde-id 1
                        ::toiminto/palvelumuoto :itse
                        ::toiminto/lkm 15
                        ::toiminto/toimenpide :avaus

                        ::osa/tyyppi :silta
                        ::osa/nimi "Iso silta"
                        ::osa/oletuspalvelumuoto :kauko}]}
          (tiedot/kohteenosatiedot-toimintoihin
            {::lt/id 1
             ::lt/kohde {::kohde/id 1
                         ::kohde/kohteenosat [{::osa/id 1
                                               ::osa/kohde-id 1
                                               ::osa/tyyppi :silta
                                               ::osa/nimi "Iso silta"
                                               ::osa/oletuspalvelumuoto :kauko}]}
             ::lt/toiminnot [{::lt/id 1
                         ::toiminto/kohteenosa-id 1
                         ::toiminto/kohde-id 1
                         ::toiminto/palvelumuoto :itse
                         ::toiminto/lkm 15
                         ::toiminto/toimenpide :avaus}]}
            {::kohde/id 1
             ::kohde/kohteenosat [{::osa/id 1
                                   ::osa/kohde-id 1
                                   ::osa/tyyppi :silta
                                   ::osa/nimi "Iso silta"
                                   ::osa/oletuspalvelumuoto :kauko}]}))))
  (testing "Uuteen yhdistäminen"
    (is (= {::lt/id 1
            ::lt/kohde {::kohde/id 1
                        ::kohde/kohteenosat [{::osa/id 1
                                              ::osa/kohde-id 1
                                              ::osa/tyyppi :silta
                                              ::osa/nimi "Iso silta"
                                              ::osa/oletuspalvelumuoto :kauko}]}
            ::lt/toiminnot [{::osa/tyyppi :silta
                        ::osa/nimi "Iso silta"
                        ::osa/oletuspalvelumuoto :kauko

                        ::toiminto/kohteenosa-id 1
                        ::toiminto/kohde-id 1
                        ::toiminto/lkm 1
                        ::toiminto/toimenpide :ei-avausta
                        ::toiminto/palvelumuoto :kauko}]}
           (tiedot/kohteenosatiedot-toimintoihin
             {::lt/id 1}
             {::kohde/id 1
              ::kohde/kohteenosat [{::osa/id 1
                                    ::osa/kohde-id 1
                                    ::osa/tyyppi :silta
                                    ::osa/nimi "Iso silta"
                                    ::osa/oletuspalvelumuoto :kauko}]})))))

(deftest tapahtuma-sisaltaa-sulun?
  (is (true? (tiedot/tapahtuman-kohde-sisaltaa-sulun? {::lt/kohde {::kohde/kohteenosat [{::osa/tyyppi :sulku}]}})))
  (is (true? (tiedot/tapahtuman-kohde-sisaltaa-sulun? {::lt/kohde {::kohde/kohteenosat [{::osa/tyyppi :sulku}
                                                                                        {::osa/tyyppi :sulku}]}})))
  (is (true? (tiedot/tapahtuman-kohde-sisaltaa-sulun? {::lt/kohde {::kohde/kohteenosat [{::osa/tyyppi :sulku}
                                                                                        {::osa/tyyppi :silta}]}})))

  (is (false? (tiedot/tapahtuman-kohde-sisaltaa-sulun? {::lt/kohde {::kohde/kohteenosat []}})))
  (is (false? (tiedot/tapahtuman-kohde-sisaltaa-sulun? {::lt/kohde {::kohde/kohteenosat [{::osa/tyyppi :rautatiesilta}
                                                                                         {::osa/tyyppi :silta}]}}))))

(deftest aseta-suunta
  (is (= {:valittu-suunta nil}
         (tiedot/aseta-suunta {} {::kohde/kohteenosat [{::osa/tyyppi :sulku}
                                                       {::osa/tyyppi :silta}]})))

  (is (= {:valittu-suunta :molemmat}
         (tiedot/aseta-suunta {} {::kohde/kohteenosat [{::osa/tyyppi :silta}]}))))

(deftest uusien-alusten-kasittely
  (is (= [{:foo :bar, :harja.domain.kanavat.lt-alus/suunta nil}]
         (tiedot/kasittele-suunta-alukselle {::lt/id -1} [{:foo :bar}])))

  (is (= [{:foo :bar, :harja.domain.kanavat.lt-alus/suunta nil}
          {:foo :bar, :harja.domain.kanavat.lt-alus/suunta nil}]
         (tiedot/kasittele-suunta-alukselle {::lt/id 1
                                          :valittu-suunta :molemmat}
                                         [{:foo :bar}
                                          {:foo :bar}])))

  (is (= [{:foo :bar
           ::lt-alus/suunta :ylos}
          {:foo :bar
           ::lt-alus/suunta :ylos}]
         (tiedot/kasittele-suunta-alukselle {::lt/id 1
                                          :valittu-suunta :ylos}
                                         [{:foo :bar}
                                          {:foo :bar}])))
  (is (= [{:foo :bar
           ::lt-alus/suunta :alas}
          {:foo :bar
           ::lt-alus/suunta :alas}]
         (tiedot/kasittele-suunta-alukselle {::lt/id 1
                                          :valittu-suunta :alas}
                                         [{:foo :bar}
                                          {:foo :bar}]))))

(deftest ketjutuksen-poisto
  (let [app {:edelliset {:ylos {:edelliset-alukset [{::lt-alus/id 1}
                                          {::lt-alus/id 2}
                                          {::lt-alus/id 3}]}
                         :alas {:edelliset-alukset [{::lt-alus/id 4}]}
                         :ei-suuntaa {:edelliset-alukset [{::lt-alus/id 5}]}}}]
    (is (= {:edelliset {:ylos {:edelliset-alukset [{::lt-alus/id 1}
                                         {::lt-alus/id 2}
                                         {::lt-alus/id 3}]}
                        :alas {:edelliset-alukset []}
                        :ei-suuntaa {:edelliset-alukset [{::lt-alus/id 5}]}}}
           (tiedot/poista-ketjutus app 4)))

    (is (= {:edelliset {:ylos {:edelliset-alukset [{::lt-alus/id 1}
                                         {::lt-alus/id 2}]}
                        :alas {:edelliset-alukset [{::lt-alus/id 4}]}
                        :ei-suuntaa {:edelliset-alukset [{::lt-alus/id 5}]}}}
           (tiedot/poista-ketjutus app 3)))
    
    (is (= {:edelliset {:ylos {:edelliset-alukset [{::lt-alus/id 1}
                                                   {::lt-alus/id 2}
                                                   {::lt-alus/id 3}]}
                        :alas {:edelliset-alukset [{::lt-alus/id 4}]}
                        :ei-suuntaa {:edelliset-alukset ()}}}
           (tiedot/poista-ketjutus app 5)))))

(deftest nayta-palvelumuoto
  (is (true? (tiedot/nayta-palvelumuoto? {})))
  (is (false? (tiedot/nayta-palvelumuoto? {::toiminto/toimenpide :ei-avausta}))))

(deftest nayta-itsepalvelu
  (is (true? (tiedot/nayta-itsepalvelut? {::toiminto/toimenpide :avaus
                                          ::toiminto/palvelumuoto :itse})))

  (is (false? (tiedot/nayta-itsepalvelut? {::toiminto/toimenpide :ei-avausta
                                           ::toiminto/palvelumuoto :itse})))
  (is (false? (tiedot/nayta-itsepalvelut? {::toiminto/toimenpide :avaus
                                           ::toiminto/palvelumuoto :kauko}))))

(deftest suuntavalinta-str
  (is (= "Ylös"
         (tiedot/suuntavalinta-str [{:foo :bar}] {:ylos {:edelliset-alukset [{:foo :bar}]}} :ylos)))
  (is (= "Alas"
         (tiedot/suuntavalinta-str [{:foo :bar}] {:ylos {:edelliset-alukset [{:foo :bar}]}} :alas))))

(deftest nayta-edelliset
  (is (true? (tiedot/nayta-edelliset-alukset? {:haetut-sopimukset [{::sop/id 1
                                                                    ::sop/ketjutus true}]
                                               :valittu-liikennetapahtuma {::lt/sopimus {::sop/id 1}
                                                                           :valittu-suunta :alas
                                                                           ::lt/id -1
                                                                           ::lt/kohde {:foo :bar}}
                                               :edellisten-haku-kaynnissa? false
                                               :edelliset {:alas {}}})))
  
  (is (false? (tiedot/nayta-edelliset-alukset? {:haetut-sopimukset [{::sop/id 1
                                                                    ::sop/ketjutus false}]
                                               :valittu-liikennetapahtuma {::lt/sopimus {::sop/id 1}
                                                                           :valittu-suunta :alas
                                                                           ::lt/id -1
                                                                           ::lt/kohde {:foo :bar}}
                                               :edellisten-haku-kaynnissa? false
                                               :edelliset {:alas {}}})))

  (is (false? (tiedot/nayta-edelliset-alukset? {:haetut-sopimukset [{::sop/id 1
                                                                     ::sop/ketjutus true}]
                                                :valittu-liikennetapahtuma {::lt/sopimus {::sop/id 1}
                                                                            :valittu-suunta :alas
                                                                            ::lt/id -1}
                                                :edellisten-haku-kaynnissa? false
                                                :edelliset {:alas {}}})))
  
  (is (false? (tiedot/nayta-edelliset-alukset? {:haetut-sopimukset [{::sop/id 1
                                                                     ::sop/ketjutus true}]
                                                :valittu-liikennetapahtuma {::lt/sopimus {::sop/id 1}
                                                                            :valittu-suunta :alas
                                                                            ::lt/id -1
                                                                            ::lt/kohde {:foo :bar}}
                                                :edellisten-haku-kaynnissa? true
                                                :edelliset {:alas {}}})))
  
  (is (false? (tiedot/nayta-edelliset-alukset? {:haetut-sopimukset [{::sop/id 1
                                                                     ::sop/ketjutus true}]
                                                :valittu-liikennetapahtuma {::lt/sopimus {::sop/id 1}
                                                                            :valittu-suunta :alas
                                                                            ::lt/id -1
                                                                            ::lt/kohde {:foo :bar}}
                                                :edellisten-haku-kaynnissa? false
                                                :edelliset {}})))
  
  (is (false? (tiedot/nayta-edelliset-alukset? {:haetut-sopimukset [{::sop/id 1
                                                                     ::sop/ketjutus true}]
                                                :valittu-liikennetapahtuma {::lt/sopimus {::sop/id 1}
                                                                            ::lt/id -1
                                                                            ::lt/kohde {:foo :bar}}
                                                :edellisten-haku-kaynnissa? false
                                                :edelliset {:alas {}}})))
  
  (is (false? (tiedot/nayta-edelliset-alukset? {:haetut-sopimukset [{::sop/id 1
                                                                     ::sop/ketjutus true}]
                                                :valittu-liikennetapahtuma {::lt/sopimus {::sop/id 1}
                                                                            :valittu-suunta :alas
                                                                            ::lt/id 1
                                                                            ::lt/kohde {:foo :bar}}
                                                :edellisten-haku-kaynnissa? false
                                                :edelliset {:alas {}}}))))

(deftest nayta-ketjutukset
  (is (true? (tiedot/nayta-suunnan-ketjutukset?
               {:valittu-liikennetapahtuma {:valittu-suunta :ylos}}
               :ylos
               {})))
  (is (true? (tiedot/nayta-suunnan-ketjutukset?
               {:valittu-liikennetapahtuma {:valittu-suunta :molemmat}}
               :ylos
               {})))
  (is (false? (tiedot/nayta-suunnan-ketjutukset?
               {:valittu-liikennetapahtuma {:valittu-suunta :ylos}}
               :alas
               {})))
  (is (false? (tiedot/nayta-suunnan-ketjutukset?
               {:valittu-liikennetapahtuma {:valittu-suunta :molemmat}}
               :ylos
               nil))))

(deftest nayta-liikennegrid
  (is (true? (tiedot/nayta-liikennegrid? {:valittu-liikennetapahtuma {::lt/kohde {:foo :bar}
                                                                      ::lt/id 1}})))
  (is (true? (tiedot/nayta-liikennegrid? {:valittu-liikennetapahtuma {::lt/kohde {:foo :bar}
                                                                      ::lt/id -1
                                                                      :valittu-suunta :molemmat}})))
  (is (true? (tiedot/nayta-liikennegrid? {:valittu-liikennetapahtuma {::lt/kohde {:foo :bar}
                                                                      ::lt/id -1
                                                                      :valittu-suunta :ylos}})))

  (is (false? (tiedot/nayta-liikennegrid? {:valittu-liikennetapahtuma {::lt/kohde {:foo :bar}
                                                                      ::lt/id -1
                                                                      :valittu-suunta nil}}))))

(deftest nayta-lisatiedot
  (is (true? (tiedot/nayta-lisatiedot? {:valittu-liikennetapahtuma {::lt/kohde {:foo :bar}
                                                                      ::lt/id 1}})))
  (is (true? (tiedot/nayta-lisatiedot? {:valittu-liikennetapahtuma {::lt/kohde {:foo :bar}
                                                                      ::lt/id -1
                                                                      :valittu-suunta :molemmat}})))
  (is (true? (tiedot/nayta-lisatiedot? {:valittu-liikennetapahtuma {::lt/kohde {:foo :bar}
                                                                      ::lt/id -1
                                                                      :valittu-suunta :ylos}})))

  (is (false? (tiedot/nayta-lisatiedot? {:valittu-liikennetapahtuma {::lt/kohde {:foo :bar}
                                                                       ::lt/id -1
                                                                       :valittu-suunta nil}}))))

(deftest kuittausta-odottavat
  (is (= [{::lt-alus/id 2}
          {::lt-alus/id 3}]
         (tiedot/kuittausta-odottavat {:siirretyt-alukset #{1}}
                                      [{::lt-alus/id 1}
                                       {::lt-alus/id 2}
                                       {::lt-alus/id 3}]))))

(deftest ketjutuksen-poisto-kaynnissa
  (is (true? (tiedot/ketjutuksen-poisto-kaynnissa? {:ketjutuksen-poistot #{1}} {::lt-alus/id 1})))
  (is (false? (tiedot/ketjutuksen-poisto-kaynnissa? {:ketjutuksen-poistot #{3 4 5}} {::lt-alus/id 1}))))

(deftest ketjtuksen-voi-siirtaa
  (is (true? (tiedot/ketjutuksen-voi-siirtaa-tapahtumasta? {:siirretyt-alukset #{1}} {::lt-alus/id 1})))
  (is (false? (tiedot/ketjutuksen-voi-siirtaa-tapahtumasta? {:siirretyt-alukset #{1 3 5}} {::lt-alus/id 10}))))

(deftest grid-virheita
  (is (true? (tiedot/grid-virheita? {1 {:poistettu true
                                        :harja.ui.grid/virheet [1]}
                                     2 {:poistettu true
                                        :harja.ui.grid/virheet []}
                                     3 {:poistettu false
                                        :harja.ui.grid/virheet [1]}
                                     4 {:poistettu false
                                        :harja.ui.grid/virheet []}})))

  (is (false? (tiedot/grid-virheita? {1 {:poistettu true
                                        :harja.ui.grid/virheet [1]}
                                     2 {:poistettu true
                                        :harja.ui.grid/virheet []}})))
  (is (false? (tiedot/grid-virheita? {1 {:poistettu true
                                         :harja.ui.grid/virheet [1]}
                                      2 {:poistettu true
                                         :harja.ui.grid/virheet []}
                                      4 {:poistettu false
                                         :harja.ui.grid/virheet []}}))))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))

(deftest liikennetapahtumien-hakeminen
  ;; FIXME Kiva testi, mutta ei oikein toimi tuck-palvelukutsun viiveen kanssa
  #_(vaadi-async-kutsut
    #{tiedot/->LiikennetapahtumatHaettu tiedot/->LiikennetapahtumatEiHaettu}
    (is (= {:liikennetapahtumien-haku-kaynnissa? true
            :valinnat {::ur/id 1 ::sop/id 1}}
           (e! (tiedot/->HaeLiikennetapahtumat)
               {:valinnat {::ur/id 1 ::sop/id 1}}))))

  (testing "Uusi haku ei lähde jos haku on jo käynnissä"
    (vaadi-async-kutsut
      #{}
      (is (= {:liikennetapahtumien-haku-kaynnissa? true
              :valinnat {::ur/id 1 ::sop/id 1}}
             (e! (tiedot/->HaeLiikennetapahtumat)
                 {:liikennetapahtumien-haku-kaynnissa? true
                  :valinnat {::ur/id 1 ::sop/id 1}}))))))

(deftest tapahtumat-haettu
  ;; Ei välttämättä vastaa täysin oikeaa dataa
  ;; mutta näiden pitäisi tulla yhteenvetoon mukaan eli testataan yhteenvedon toimivuutta samalla 
  (let [sulutus-alas {::lt/kohde {::kohde/kohdekokonaisuus {::kok/nimi "Saimaa"}
                                  ::kohde/nimi "Sulutus alas test"
                                  ::kohde/tyyppi :sulku}

                      ::lt/toiminnot [{::toiminto/lkm 1
                                       ::toiminto/palvelumuoto :itse
                                       ::toiminto/toimenpide :sulutus}]

                      ::lt/alukset [{::lt-alus/suunta :alas}]}

        sulutus-ylos {::lt/kohde {::kohde/kohdekokonaisuus {::kok/nimi "Saimaa"}
                                  ::kohde/nimi "Sulutus ylös test"
                                  ::kohde/tyyppi :sulku}

                      ::lt/toiminnot [{::toiminto/palvelumuoto :kauko
                                       ::toiminto/toimenpide :sulutus}]

                      ::lt/alukset [{::lt-alus/suunta :ylos}]}

        sillan-avaus {::lt/kohde {::kohde/kohdekokonaisuus {::kok/nimi "Saimaa"}
                                  ::kohde/nimi "Sillan avaus test"
                                  ::kohde/tyyppi :silta}

                      ::lt/toiminnot [{::toiminto/palvelumuoto :paikallis
                                       ::toiminto/toimenpide :avaus}]}

        tyhjennys {::lt/kohde {::kohde/kohdekokonaisuus {::kok/nimi "Saimaa"}
                               ::kohde/nimi "Tyhjennys test"
                               ::kohde/tyyppi :sulku}

                   ::lt/toiminnot [{::toiminto/palvelumuoto :muu
                                    ::toiminto/toimenpide :tyhjennys}]}]
    
    (is (= {:liikennetapahtumien-haku-kaynnissa? false
            :liikennetapahtumien-haku-tulee-olemaan-kaynnissa? false
            :haetut-tapahtumat [sulutus-alas sulutus-ylos sillan-avaus tyhjennys]
            :tapahtumarivit [(merge sulutus-alas {::lt-alus/suunta :alas})
                             (merge sulutus-ylos {::lt-alus/suunta :ylos}) 
                             sillan-avaus 
                             tyhjennys]
            :raporttiparametrit {:nimi :kanavien-liikennetapahtumat,
                                 :konteksti "monta-urakkaa",
                                 :urakoiden-nimet (),
                                 :parametrit {:valitut-urakat ()
                                              :alkupvm nil 
                                              :loppupvm nil
                                              :urakkatyyppi :vesivayla-kanavien-hoito
                                              :yhteenveto {:toimenpiteet {:sulutukset-ylos 1, :sulutukset-alas 1, :sillan-avaukset 1, :tyhjennykset 1, :yhteensa 4}, 
                                                           :palvelumuoto {:paikallispalvelu 1, :kaukopalvelu 1, :itsepalvelu 1, :muu 1, :yhteensa 4}}}}}
           
           (e! (tiedot/->LiikennetapahtumatHaettu [sulutus-alas sulutus-ylos sillan-avaus tyhjennys]))))))

(deftest tapahtumia-ei-haettu
  (is (= {:liikennetapahtumien-haku-kaynnissa? false
          :liikennetapahtumien-haku-tulee-olemaan-kaynnissa? false}
         (e! (tiedot/->LiikennetapahtumatEiHaettu {})))))

(deftest tapahtuman-valitseminen
  (testing "Tyhjän valitseminen"
    (is (= {:valittu-liikennetapahtuma nil
            :siirretyt-alukset #{}
            :ketjutuksen-poistot #{}}
           (e! (tiedot/->ValitseTapahtuma nil)))))

  (testing "Olemassaolevan tapahtuman valitseminen liittää toimintoihin kohteenosan tiedot"
    (is (= {:siirretyt-alukset #{}
            :ketjutuksen-poistot #{}
            :valittu-liikennetapahtuma {::lt/id 1
                                        ::lt/kohde {::kohde/id 1
                                                    ::kohde/kohteenosat [{::osa/id 1
                                                                          ::osa/tyyppi :silta
                                                                          ::osa/nimi "Iso silta"
                                                                          ::osa/oletuspalvelumuoto :kauko}]}
                                        ::lt/toiminnot [{::toiminto/id 1
                                                    ::toiminto/kohteenosa-id 1
                                                    ::toiminto/kohde-id 1
                                                    ::toiminto/lkm 15
                                                    ::toiminto/palvelumuoto :itse
                                                    ::osa/tyyppi :silta
                                                    ::osa/nimi "Iso silta"
                                                    ::osa/oletuspalvelumuoto :kauko
                                                    ::toiminto/toimenpide :sillan-avaus}]}
            :haetut-tapahtumat [{::lt/id 1
                                 ::lt/kohde {::kohde/id 1
                                             ::kohde/kohteenosat [{::osa/id 1
                                                                   ::osa/tyyppi :silta
                                                                   ::osa/nimi "Iso silta"
                                                                   ::osa/oletuspalvelumuoto :kauko}]}
                                 ::lt/toiminnot [{::toiminto/id 1
                                             ::toiminto/kohteenosa-id 1
                                             ::toiminto/kohde-id 1
                                             ::toiminto/lkm 15
                                             ::toiminto/palvelumuoto :itse
                                             ::toiminto/toimenpide :sillan-avaus}]}]}
           (e! (tiedot/->ValitseTapahtuma
                 {::lt/id 1})
               {:haetut-tapahtumat [{::lt/id 1
                                     ::lt/kohde {::kohde/id 1
                                                 ::kohde/kohteenosat [{::osa/id 1
                                                                       ::osa/tyyppi :silta
                                                                       ::osa/nimi "Iso silta"
                                                                       ::osa/oletuspalvelumuoto :kauko}]}
                                     ::lt/toiminnot [{::toiminto/id 1
                                                 ::toiminto/kohteenosa-id 1
                                                 ::toiminto/kohde-id 1
                                                 ::toiminto/lkm 15
                                                 ::toiminto/palvelumuoto :itse
                                                 ::toiminto/toimenpide :sillan-avaus}]}]}))))

  (testing "Uuden tapahtuman avaaminen"
    (is (= {:siirretyt-alukset #{}
            :ketjutuksen-poistot #{}
            :valittu-liikennetapahtuma {:foo :bar
                                        ::lt/toiminnot []
                                        ::lt/kohde nil}
            :haetut-tapahtumat [{::lt/id 1
                                 ::lt/kohde {::kohde/id 1
                                             ::kohde/kohteenosat [{::osa/id 1
                                                                   ::osa/tyyppi :silta
                                                                   ::osa/nimi "Iso silta"
                                                                   ::osa/oletuspalvelumuoto :kauko}]}
                                 ::lt/toiminnot [{::toiminto/id 1
                                             ::toiminto/kohteenosa-id 1
                                             ::toiminto/kohde-id 1
                                             ::toiminto/lkm 15
                                             ::toiminto/palvelumuoto :itse
                                             ::toiminto/toimenpide :sillan-avaus}]}]}
           (e! (tiedot/->ValitseTapahtuma {:foo :bar})
               {:haetut-tapahtumat [{::lt/id 1
                                     ::lt/kohde {::kohde/id 1
                                                 ::kohde/kohteenosat [{::osa/id 1
                                                                       ::osa/tyyppi :silta
                                                                       ::osa/nimi "Iso silta"
                                                                       ::osa/oletuspalvelumuoto :kauko}]}
                                     ::lt/toiminnot [{::toiminto/id 1
                                                 ::toiminto/kohteenosa-id 1
                                                 ::toiminto/kohde-id 1
                                                 ::toiminto/lkm 15
                                                 ::toiminto/palvelumuoto :itse
                                                 ::toiminto/toimenpide :sillan-avaus}]}]})))))

(deftest edellisten-haku
  (vaadi-async-kutsut
    #{tiedot/->EdellisetTiedotHaettu tiedot/->EdellisetTiedotEiHaettu}
    (is (= {:edellisten-haku-kaynnissa? true}
           (e! (tiedot/->HaeEdellisetTiedot {}))))))

(deftest edelliset-haettu
  (is (= {:edelliset {:tama {::lt/vesipinta-alaraja 1
                             ::lt/vesipinta-ylaraja 2}
                      :ylos {:foo :bar}
                      :alas {:baz :baz}
                      :ei-suuntaa {:baz :baz}}
          :edellisten-haku-kaynnissa? false
          :valittu-liikennetapahtuma {::lt/vesipinta-alaraja 1
                                      ::lt/vesipinta-ylaraja 2}}
         (e! (tiedot/->EdellisetTiedotHaettu {:edellinen {::lt/vesipinta-alaraja 1
                                                          ::lt/vesipinta-ylaraja 2}
                                              :ylos {:foo :bar}
                                              :alas {:baz :baz}
                                              :ei-suuntaa {:baz :baz}})))))

(deftest edelliset-ei-haettu
  (is (= {:edellisten-haku-kaynnissa? false}
         (e! (tiedot/->EdellisetTiedotEiHaettu {})))))

(deftest suodatinten-päivittäminen
  (testing "Vanhoja tietoja ei ylikirjoiteta"
    (vaadi-async-kutsut
      #{tiedot/->HaeLiikennetapahtumat}
      (is (= {:valinnat {:foo 1}}
             (e! (tiedot/->PaivitaValinnat {})
                 {:valinnat {:foo 1}})))))

  (testing "Ainoastaan tietyt avaimet parametrista valitaan"
    (vaadi-async-kutsut
      #{tiedot/->HaeLiikennetapahtumat}
      (is (= {:valinnat {:foo 1
                         :aikavali 1}}
             (e! (tiedot/->PaivitaValinnat {:bar 2
                                            :aikavali 1})
                 {:valinnat {:foo 1}})))))

  (testing "Parametrien ylikirjoitus"
    (vaadi-async-kutsut
      #{tiedot/->HaeLiikennetapahtumat}
      (is (= {:valinnat {:aikavali 3}}
             (e! (tiedot/->PaivitaValinnat {:aikavali 3})
                 {:valinnat {:aikavali 1}}))))))

(deftest tapahtuman-muokkaus
  (is (= {:valittu-liikennetapahtuma {:foo :bar}}
         (e! (tiedot/->TapahtumaaMuokattu {:foo :bar})))))

(deftest alusten-muokkaus
  (is (= {:valittu-liikennetapahtuma {::lt/alukset [{:foo :bar, :harja.domain.kanavat.lt-alus/suunta nil}]
                                      :grid-virheita? false}}
         (e! (tiedot/->MuokkaaAluksia [{:foo :bar}] false)
             {:valittu-liikennetapahtuma {}})))

  (is (= {:valittu-liikennetapahtuma nil}
         (e! (tiedot/->MuokkaaAluksia [{:foo :bar}] true)
             {:valittu-liikennetapahtuma nil}))))

(deftest suunnan-vaihto
  (is (= {:valittu-liikennetapahtuma {::lt/alukset [{::lt-alus/id 1 ::lt-alus/suunta :ei-suuntaa}
                                                    {::lt-alus/id 2 ::lt-alus/suunta :alas}]}}
         (e! (tiedot/->VaihdaSuuntaa {::lt-alus/id 1 ::lt-alus/suunta :alas} :alas)
             {:valittu-liikennetapahtuma {::lt/alukset [{::lt-alus/id 1 ::lt-alus/suunta :alas}
                                                        {::lt-alus/id 2 ::lt-alus/suunta :alas}]}})))
  
  (is (= {:valittu-liikennetapahtuma {::lt/alukset [{::lt-alus/id 1 ::lt-alus/suunta :alas}
                                                    {::lt-alus/id 2 ::lt-alus/suunta :alas}]}}
         (e! (tiedot/->VaihdaSuuntaa {::lt-alus/id 1 ::lt-alus/suunta :ylos} :ylos)
             {:valittu-liikennetapahtuma {::lt/alukset [{::lt-alus/id 1 ::lt-alus/suunta :ylos}
                                                        {::lt-alus/id 2 ::lt-alus/suunta :alas}]}})))

  (is (= {:valittu-liikennetapahtuma {::lt/alukset [{:id -1 ::lt-alus/suunta :ylos}
                                                    {:id -2 ::lt-alus/suunta :alas}]}}
         (e! (tiedot/->VaihdaSuuntaa {:id -1 ::lt-alus/suunta :ei-suuntaa} :ei-suuntaa)
             {:valittu-liikennetapahtuma {::lt/alukset [{:id -1 ::lt-alus/suunta :ei-suuntaa}
                                                        {:id -2 ::lt-alus/suunta :alas}]}}))))

(deftest tallennus
  (vaadi-async-kutsut
    #{tiedot/->TapahtumaTallennettu tiedot/->TapahtumaEiTallennettu}
    (is (= {:tallennus-kaynnissa? true}
           (e! (tiedot/->TallennaLiikennetapahtuma {})
               {:tallennus-kaynnissa? false}))))

  (vaadi-async-kutsut
    #{}
    (is (= {:tallennus-kaynnissa? true}
           (e! (tiedot/->TallennaLiikennetapahtuma {})
               {:tallennus-kaynnissa? true})))))

(deftest tallennus-valmis
  (swap! modal/modal-sisalto assoc :nakyvissa? true)

  (let [tapahtuma1 {::lt/kohde {::kohde/nimi "Iso mutka"}
                    ::lt/alukset []
                    ::lt/niput []}
        tapahtuma2 {::lt/kohde {::kohde/nimi "Iso mutka"}
                    ::lt/alukset [{::lt-alus/suunta :ylos
                                   ::lt-alus/nimi "Ronsu"}]}]
    (is (= {:tallennus-kaynnissa? false
            :valittu-liikennetapahtuma nil
            :liikennetapahtumien-haku-kaynnissa? false
            :liikennetapahtumien-haku-tulee-olemaan-kaynnissa? false
            :haetut-tapahtumat [tapahtuma1 tapahtuma2]
            :tapahtumarivit [tapahtuma1
                             (merge
                              tapahtuma2
                              {::lt-alus/suunta :ylos
                               ::lt-alus/nimi "Ronsu"})]
            :raporttiparametrit {:nimi :kanavien-liikennetapahtumat, :konteksti "monta-urakkaa", :urakoiden-nimet (),
                                 :parametrit {:valitut-urakat ()
                                              :alkupvm nil
                                              :loppupvm nil
                                              :urakkatyyppi :vesivayla-kanavien-hoito
                                              :yhteenveto {:toimenpiteet {:sulutukset-ylos 0, :sulutukset-alas 0, :sillan-avaukset 0, :tyhjennykset 0, :yhteensa 0}, 
                                                           :palvelumuoto {:paikallispalvelu 0, :kaukopalvelu 0, :itsepalvelu 0, :muu 0, :yhteensa 0}}}}}
           (e! (tiedot/->TapahtumaTallennettu [tapahtuma1 tapahtuma2])))))

  (is (false? (:nakyvissa? @modal/modal-sisalto))))

(deftest ei-tallennettu
  (is (= {:tallennus-kaynnissa? false}
         (e! (tiedot/->TapahtumaEiTallennettu {})))))

(deftest siirra-kaikki
  (is (= {:siirretyt-alukset #{1 2 3}
          :valittu-liikennetapahtuma {::lt/alukset [{::lt-alus/id 1} {::lt-alus/id 2} {::lt-alus/id 3}]}}
         (e! (tiedot/->SiirraKaikkiTapahtumaan [{::lt-alus/id 1} {::lt-alus/id 2} {::lt-alus/id 3}])))))

(deftest siirra-tapahtumaan
  (is (= {:siirretyt-alukset #{1}
          :valittu-liikennetapahtuma {::lt/alukset [{::lt-alus/id 1}]}}
         (e! (tiedot/->SiirraTapahtumaan {::lt-alus/id 1})
             {:valittu-liikennetapahtuma {::lt/alukset []}}))))

(deftest siirra-tapahtumasta
  (is (= {:valittu-liikennetapahtuma {::lt/alukset [{::lt-alus/id 2, :poistettu true, :harja.domain.kanavat.lt-alus/suunta :ylos}]}
          :siirretyt-alukset #{}}
         (e! (tiedot/->SiirraTapahtumasta {::lt-alus/id 2})
             {:valittu-liikennetapahtuma {::lt/alukset [{::lt-alus/id 2}]}
              :siirretyt-alukset #{2}}))))

(deftest poista-ketjutus
  (vaadi-async-kutsut
    #{tiedot/->KetjutusEiPoistettu tiedot/->KetjutusPoistettu}
    (is (= {:ketjutuksen-poistot #{1 2}}
           (e! (tiedot/->PoistaKetjutus {::lt-alus/id 1})
               {:ketjutuksen-poistot #{2}}))))

  (vaadi-async-kutsut
    #{}
    (is (= {:ketjutuksen-poistot #{1 2}}
           (e! (tiedot/->PoistaKetjutus {::lt-alus/id 1})
               {:ketjutuksen-poistot #{1 2}})))))

(deftest ketjutus-poistettu
  (is (= {:ketjutuksen-poistot #{}
          :edelliset {:ylos {:edelliset-alukset [{::lt-alus/id 2}]}
                      :alas {:edelliset-alukset []}
                      :ei-suuntaa {:edelliset-alukset ()}}}
         (e! (tiedot/->KetjutusPoistettu {} 1)
             {:ketjutuksen-poistot #{1}
              :edelliset {:ylos {:edelliset-alukset [{::lt-alus/id 1}
                                           {::lt-alus/id 2}]}}}))))

(deftest ketjutus-ei-poistettu
  (is (= {:ketjutuksen-poistot #{}
          :edelliset {:ylos {:edelliset-alukset [{::lt-alus/id 1}
                                       {::lt-alus/id 2}]}}}
         (e! (tiedot/->KetjutusEiPoistettu {} 1)
             {:ketjutuksen-poistot #{1}
              :edelliset {:ylos {:edelliset-alukset [{::lt-alus/id 1}
                                           {::lt-alus/id 2}]}}}))))