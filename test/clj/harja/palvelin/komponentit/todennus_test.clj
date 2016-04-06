(ns harja.palvelin.komponentit.todennus-test
  (:require [harja.palvelin.komponentit.todennus :as todennus]
            [clojure.test :as t :refer [deftest is use-fixtures]]))

(def testiroolit {"root" {:nimi "root"
                          :kuvaus "Pääkäyttäjä"}
                  "valvoja" {:nimi "valvoja"
                             :kuvaus "Urakan valvoja"
                             :linkki "urakka"}
                  "katsoja" {:nimi "katsoja"
                             :kuvaus "Katsoja"}
                  "urakoitsija" {:nimi "urakoitsija"
                                 :kuvaus "Urakoitsijan käyttäjä"
                                 :linkki "urakoitsija"}
                  "paivystaja" {:nimi "paivystaja"
                                :kuvaus "Urakan päivystäjä"
                                :linkki "urakka"}})

(def urakat {"u123" 666})
(def urakoitsijat {"Y123456" 42})

(def oikeudet (partial todennus/kayttajan-roolit urakat urakoitsijat testiroolit))

(deftest lue-oikeudet-oam-groupsista

  (is (= {:roolit #{"root"} :urakkaroolit {} :organisaatioroolit {}}
         (oikeudet "root")))

  (is (= {:roolit #{} :urakkaroolit {666 #{"valvoja"}} :organisaatioroolit {}}
         (oikeudet "u123_valvoja")))

  (is (= {:roolit #{} :urakkaroolit {666 #{"paivystaja"}}
          :organisaatioroolit {42 #{"urakoitsija"}}}
         (oikeudet "Y123456_urakoitsija,u123_paivystaja"))))
