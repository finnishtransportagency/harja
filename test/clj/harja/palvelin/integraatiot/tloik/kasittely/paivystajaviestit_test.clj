(ns harja.palvelin.integraatiot.tloik.kasittely.paivystajaviestit-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.integraatiot.tloik.kasittely.paivystajaviestit :as paivystajaviestit]))

(deftest sama-sahkoposti-saa-viestin-vain-kerran
  (let [paatokset (paivystajaviestit/lahetyspaatokset
                    {:ilmoitustyyppi "toimenpidepyynto"}
                    [{:sahkoposti "Matti@Example.org"}
                     {:sahkoposti " matti@example.org "}
                     {:sahkoposti "toinen@example.org"}])]
    (is (= [true false true] (mapv :laheta-sahkoposti? paatokset)))
    (is (= [false true false] (mapv :duplikaatti-sahkoposti? paatokset)))))

(deftest tyhjia-sahkopostiosoitteita-ei-pideta-duplikaatteina
  (let [paatokset (paivystajaviestit/lahetyspaatokset
                    {:ilmoitustyyppi "toimenpidepyynto"}
                    [{:sahkoposti nil} {:sahkoposti ""} {:sahkoposti "  "}])]
    (is (every? :laheta-sahkoposti? paatokset))
    (is (not-any? :duplikaatti-sahkoposti? paatokset))))

(deftest duplikaatin-sms-lahetetaan-silti
  (let [paatokset (paivystajaviestit/lahetyspaatokset
                    {:ilmoitustyyppi "toimenpidepyynto"}
                    [{:sahkoposti "a@example.org" :vastuuhenkilo true}
                     {:sahkoposti "a@example.org" :vastuuhenkilo true}])]
    (is (= [false true] (mapv :duplikaatti-sahkoposti? paatokset)))
    (is (= [true true] (mapv :laheta-sms? paatokset)))))

(deftest sms-lahetetaan-vain-vastuuhenkilolle-toimenpidepyynnosta
  (testing "merkkijonotyyppi (T-Loik) ja keyword-tyyppi (kannasta haettu)"
    (is (true? (paivystajaviestit/laheta-sms-paivystajalle? {:ilmoitustyyppi "toimenpidepyynto"} {:vastuuhenkilo true})))
    (is (true? (paivystajaviestit/laheta-sms-paivystajalle? {:ilmoitustyyppi :toimenpidepyynto} {:vastuuhenkilo true}))))
  (testing "ei vastuuhenkilö tai muu ilmoitustyyppi"
    (is (false? (paivystajaviestit/laheta-sms-paivystajalle? {:ilmoitustyyppi "toimenpidepyynto"} {:vastuuhenkilo false})))
    (is (false? (paivystajaviestit/laheta-sms-paivystajalle? {:ilmoitustyyppi "tiedoitus"} {:vastuuhenkilo true})))
    (is (false? (paivystajaviestit/laheta-sms-paivystajalle? {} {:vastuuhenkilo true})))))

(deftest tyhja-paivystajalista-palauttaa-tyhjan
  (is (= [] (paivystajaviestit/lahetyspaatokset {:ilmoitustyyppi "toimenpidepyynto"} []))))
