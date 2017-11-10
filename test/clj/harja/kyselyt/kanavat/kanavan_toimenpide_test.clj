(ns harja.kyselyt.kanavat.kanavan-toimenpide-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.kyselyt.kanavat.kanavan-toimenpide :as kanava-q]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [taoensso.timbre :as log]))

(deftest kanavan-toimenpiteet
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        vastaus (kanava-q/hae-sopimuksen-toimenpiteet-aikavalilta
                  db
                  {:urakka (hae-saimaan-kanavaurakan-id)
                   :sopimus (hae-saimaan-kanavaurakan-paasopimuksen-id)
                   :alkupvm (harja.pvm/luo-pvm 2016 1 1)
                   :loppupvm (harja.pvm/luo-pvm 2018 1 1)
                   :toimenpidekoodi 597
                   :tyyppi "kokonaishintainen"})]

    (log/debug vastaus)

    (is (every? ::kanavan-toimenpide/id vastaus))
    (is (every? ::kanavan-toimenpide/kohde vastaus))
    (is (every? ::kanavan-toimenpide/toimenpidekoodi vastaus))
    (is (every? ::kanavan-toimenpide/huoltokohde vastaus))))
