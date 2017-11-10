(ns harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset-test
  (:require [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.tiedot.kanavat.urakka.toimenpiteet :as toimenpiteet]
            [clojure.test :refer-macros [deftest is testing]]
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
                  ::kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen
                  :alkupvm (pvm/luo-pvm 2017 1 1)
                  :loppupvm (pvm/luo-pvm 2018 1 1)}]
    (is (= (toimenpiteet/muodosta-hakuargumentit {:urakka {:id 666}
                                                  :sopimus-id 666
                                                  :toimenpide {:id 666}
                                                  :aikavali aikavali}
                                                 :kokonaishintainen)
           odotettu))
    (is (s/valid? ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely odotettu))))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))

(deftest PaivitaValinnat
  (vaadi-async-kutsut
    #{tiedot/->HaeToimenpiteet}
    (is (= {:valinnat {:foo :bar}}
           (e! (tiedot/->PaivitaValinnat {:foo :bar}))))))

(deftest HaeKokonaishintaisetToimenpiteet
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