(ns harja.tiedot.urakka.urakan-tyotunnit-test
  (:require [tuck.core :refer [tuck]]
            [cljs.test :as t :refer-macros [deftest is]]
            [harja.testutils :refer [fake-palvelut-fixture fake-palvelukutsu jvh-fixture]]
            [harja.testutils.shared-testutils :as u]
            [harja.tiedot.urakka.urakan-tyotunnit :as urakan-tyotunnit]
            [harja.domain.urakan-tyotunnit :as ut])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]
                   [cljs.core.async.macros :refer [go]]))

(t/use-fixtures :each u/komponentti-fixture fake-palvelut-fixture jvh-fixture)

(deftest urakan-tyotunnit-naytettavasta-tallennettavaksi
  (let [naytettavat [{:vuosi 2017
                      :ensimmainen-vuosikolmannes 111
                      :toinen-vuosikolmannes 222
                      :kolmas-vuosikolmannes 333}
                     {:vuosi 2016
                      :ensimmainen-vuosikolmannes 444
                      :toinen-vuosikolmannes 555
                      :kolmas-vuosikolmannes 666}]
        odotetut [{:ut/urakka-id 666
                   :ut/vuosi 2017
                   :ut/vuosikolmannes 1
                   :ut/tyotunnit 111}
                  {:ut/urakka-id 666
                   :ut/vuosi 2017
                   :ut/vuosikolmannes 2
                   :ut/tyotunnit 222}
                  {:ut/urakka-id 666
                   :ut/vuosi 2017
                   :ut/vuosikolmannes 3
                   :ut/tyotunnit 222}
                  {:ut/urakka-id 666
                   :ut/vuosi 2016
                   :ut/vuosikolmannes 1
                   :ut/tyotunnit 444}
                  {:ut/urakka-id 666
                   :ut/vuosi 2016
                   :ut/vuosikolmannes 2
                   :ut/tyotunnit 555}
                  {:ut/urakka-id 666
                   :ut/vuosi 2016
                   :ut/vuosikolmannes 3
                   :ut/tyotunnit 555}]
        tallennettavat (urakan-tyotunnit/tyotunnit-tallennettavana 666 naytettavat)]
    (is (= odotetut tallennettavat))))

(deftest urakan-tyotunnit-palvelusta-naytettavaksi
  (let [vuodet [{:vuosi 2017}
                {:vuosi 2016}
                {:vuosi 2015}]
        palvelusta [{:ut/tyotunnit 666
                     :ut/id 27
                     :ut/vuosi 2017
                     :ut/lahetys-onnistunut false
                     :ut/vuosikolmannes 2
                     :ut/urakka-id 4}]
        naytettavat (urakan-tyotunnit/tyotunnit-naytettavana vuodet palvelusta)
        odotetut [{:kolmas-vuosikolmannes nil
                   :vuosi 2017
                   :ensimmainen-vuosikolmannes nil
                   :toinen-vuosikolmannes 666}
                  {:kolmas-vuosikolmannes nil
                   :vuosi 2016
                   :ensimmainen-vuosikolmannes nil
                   :toinen-vuosikolmannes nil}
                  {:kolmas-vuosikolmannes nil
                   :vuosi 2015
                   :ensimmainen-vuosikolmannes nil
                   :toinen-vuosikolmannes nil}]]
    (is (= odotetut naytettavat))))



