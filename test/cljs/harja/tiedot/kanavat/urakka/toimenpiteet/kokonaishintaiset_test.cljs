(ns harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset-test
  (:require [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.tiedot.kanavat.urakka.toimenpiteet :as toimenpiteet]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]
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
    #{tiedot/->HaeToimenpiteet}
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
  (is (= {:avattu-toimenpide {::kanavan-toimenpide/sopimus-id nil
                               ::kanavan-toimenpide/kuittaaja
                               {::kayttaja/id nil
                                ::kayttaja/etunimi nil
                                ::kayttaja/sukunimi nil}}}
         (e! (tiedot/->UusiToimenpide)))))

(deftest TyhjennaValittuToimenpide
  (is (nil? (:avattu-toimenpide (e! (tiedot/->TyhjennaAvattuToimenpide))))))

(deftest AsetaToimenpiteenTiedot
  (let [toimenpide {:testi-pieni "Olen vain"}]
    (is (= toimenpide (:avattu-toimenpide (e! (tiedot/->AsetaLomakkeenToimenpiteenTiedot toimenpide)))))))

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
      tiedot/->ToimenpiteidenTallentaminenEpaonnistui}
    (let [{tallennus-kaynnissa? :tallennus-kaynnissa?} (e! (tiedot/->TallennaToimenpide {:foo "bar"}))]
      (is tallennus-kaynnissa?))))

(deftest ToimenpideTallennettu
  (let [haetut-toimenpiteet [{:foo "bar"}]
        {:keys [tallennus-kaynnissa? avattu-toimenpide toimenpiteet]}
        (e! (tiedot/->ToimenpideTallennettu haetut-toimenpiteet))]
    (is (false? tallennus-kaynnissa?))
    (is (nil? avattu-toimenpide))
    (is (= haetut-toimenpiteet toimenpiteet))))

(deftest ToimenpiteidenTallentaminenEpaonnistui
  (is (false? (:tallennus-kaynnissa? (e! (tiedot/->ToimenpiteidenTallentaminenEpaonnistui))))))

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
