(ns harja-laadunseuranta.kaynnistyspainike-test
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [reagent.core :as reagent :refer [atom]]
            [dommy.core :as dommy]
            [cljs-react-test.simulate :as sim]
            [harja-laadunseuranta.testutils :refer [sel sel1]]
            [harja-laadunseuranta.main :as main])
  (:require-macros [harja-laadunseuranta.test-macros :refer [with-component prepare-component-tests]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))


#_(prepare-component-tests)

#_(defn- sisapainike-valkkyy? [painike-div]
  (dommy/has-class? (dommy/sel1 painike-div [:div.sisapainike]) "blink"))

#_(deftest kaynnistyspainikkeen-toiminta
  (testing "Käynnistyspainikkeen painaminen"
    (let [kaynnissa (atom false)
          kaynnistetaan (atom false)]
      (with-component [main/kaynnistyspainike kaynnissa kaynnistetaan]
        (let [painike-div (sel1 [:div.painike])]
          (testing "Painike ei alussa välky"
            (is (not (sisapainike-valkkyy? painike-div))))
          
          (testing "Painikkeen klikkaaminen aiheuttaa tallennustilan muutospyynnön"
            (sim/click painike-div nil)
            (reagent/flush)
            (is (not @kaynnissa))
            (is @kaynnistetaan)
            (is (not (sisapainike-valkkyy? painike-div))))
          
          (testing "Kun tallennus on päällä, painike vilkkuu"
            (reset! kaynnissa true)
            (reset! kaynnistetaan false)
            (reagent/flush)
            (is (sisapainike-valkkyy? painike-div))))))))

