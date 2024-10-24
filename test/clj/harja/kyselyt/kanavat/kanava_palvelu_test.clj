(ns harja.kyselyt.kanavat.kanava-palvelu-test
  (:require [clojure.test :refer :all]
            [harja [testi :refer :all]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.kanavat.liikennetapahtumat :as liikennetapahtumat]))


(defn fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :kan-liikennetapahtumat (component/using
                                    (liikennetapahtumat/->Liikennetapahtumat)
                                    [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each fixture)


(deftest hae-kayttajan-kanavaurakat-toimii
  (let [urakka-id-saimaa (hae-urakan-id-nimella "Saimaan kanava")
        urakka-id-joensuu (hae-urakan-id-nimella "Joensuun kanava")
        kanava-hallintayksikko 1
         ;; Aseta Saimaan kanava päättyneeksi, pitäisi tulla tuloksiin silti
        _ (u "UPDATE urakka SET alkupvm = '1996-12-19', loppupvm = '1996-12-20' WHERE id = " urakka-id-saimaa " AND hallintayksikko = " kanava-hallintayksikko ";")
        ;; Haetaan jvh:n kanavaurakat, pitäisi tulla saimaan ja joensuun kanavat
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-kayttajan-kanavaurakat +kayttaja-jvh+
                  {:hallintayksikko kanava-hallintayksikko
                   :urakka-id urakka-id-saimaa})
        vastaus-urakat (-> vastaus first :urakat)
        vastaus-joensuu (first (filter #(= (:nimi %) "Joensuun kanava") vastaus-urakat))
        vastaus-saimaa (first (filter #(= (:nimi %) "Saimaan kanava") vastaus-urakat))]

    (is (= (:id vastaus-joensuu) urakka-id-joensuu))
    (is (= (:nimi vastaus-joensuu) "Joensuun kanava"))
    (is (= (:urakkanro vastaus-joensuu) "089123"))
    (is (= (:id vastaus-saimaa) urakka-id-saimaa))
    (is (= (:nimi vastaus-saimaa) "Saimaan kanava"))
    (is (= (:urakkanro vastaus-saimaa) "089123"))))
