(ns harja.domain.pot2-test
  (:require [clojure.test :refer [deftest is testing]]
            [harja.domain.pot2 :as pot2-domain]))

(def kelvollinen-paallystekerrosrivi
  {:toimenpide 12 :tr-numero 20 :tr-alkuosa 1 :tr-alkuetaisyys 1 :tr-loppuosa 2 :tr-loppuetaisyys 5})

(def kelvollinen-alustarivi
  (assoc kelvollinen-paallystekerrosrivi :tr-ajorata 1 :tr-kaista 11))

(deftest luonnoksen-tallennus-estetty-riveilla
  (testing "Materiaalin, leveyden ja massan puute ei estä luonnoksen tallennusta"
    (is (false? (pot2-domain/luonnoksen-tallennus-estetty-riveilla?
                  [(assoc kelvollinen-paallystekerrosrivi :materiaali nil :leveys nil :kokonaismassamaara nil)]
                  [kelvollinen-alustarivi]))))
  (testing "Päällystekerrosrivin toimenpiteen puute estää tallennuksen"
    (is (true? (pot2-domain/luonnoksen-tallennus-estetty-riveilla?
                 [(dissoc kelvollinen-paallystekerrosrivi :toimenpide)]
                 []))))
  (testing "Uusi tyhjä rivi, jolla on vain tie, estää tallennuksen"
    (is (true? (pot2-domain/luonnoksen-tallennus-estetty-riveilla?
                 [{:tr-numero 20}]
                 []))))
  (testing "Alustarivin ajoradan tai kaistan puute estää tallennuksen"
    (is (true? (pot2-domain/luonnoksen-tallennus-estetty-riveilla?
                 []
                 [(dissoc kelvollinen-alustarivi :tr-kaista)])))
    (is (true? (pot2-domain/luonnoksen-tallennus-estetty-riveilla?
                 []
                 [(assoc kelvollinen-alustarivi :tr-ajorata nil)]))))
  (testing "Päällystekerrosrivi ei vaadi ajorataa eikä kaistaa"
    (is (false? (pot2-domain/luonnoksen-tallennus-estetty-riveilla?
                  [kelvollinen-paallystekerrosrivi]
                  []))))
  (testing "Poistettuja rivejä ei huomioida"
    (is (false? (pot2-domain/luonnoksen-tallennus-estetty-riveilla?
                  [{:tr-numero 20 :poistettu true}]
                  [{:poistettu true}]))))
  (testing "Ei rivejä ei estä tallennusta"
    (is (false? (pot2-domain/luonnoksen-tallennus-estetty-riveilla? nil nil)))))
