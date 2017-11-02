(ns harja.kyselyt.kanavat.kanavan-toimenpide-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.kyselyt.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))

(deftest kanavan-toimenpiteet
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        odotettu {:harja.domain.kanavat.kanavan-toimenpide/kohde
                  {:harja.domain.kanavat.kanavan-kohde/id 3
                   :harja.domain.kanavat.kanavan-kohde/nimi "Tikkalansaaren avattava ratasilta"
                   :harja.domain.kanavat.kanavan-kohde/tyyppi :silta}
                  :harja.domain.kanavat.kanavan-toimenpide/kuittaaja
                  {:harja.domain.kayttaja/kayttajanimi "jvh"
                   :harja.domain.kayttaja/etunimi "Jalmari"
                   :harja.domain.kayttaja/sukunimi "Järjestelmävastuuhenkilö"
                   :harja.domain.kayttaja/puhelin "040123456789"
                   :harja.domain.kayttaja/sahkoposti "jalmari@example.com"
                   :harja.domain.kayttaja/id 2}
                  :harja.domain.kanavat.kanavan-toimenpide/suorittaja
                  {:harja.domain.kayttaja/etunimi "Jalmari"
                   :harja.domain.kayttaja/id 2
                   :harja.domain.kayttaja/kayttajanimi "jvh"
                   :harja.domain.kayttaja/sahkoposti "jalmari@example.com"
                   :harja.domain.kayttaja/sukunimi "Järjestelmävastuuhenkilö"
                   :harja.domain.kayttaja/puhelin "040123456789"}
                  :harja.domain.kanavat.kanavan-toimenpide/lisatieto "Testitoimenpide"
                  :harja.domain.kanavat.kanavan-toimenpide/toimenpidekoodi
                  {:harja.domain.toimenpidekoodi/id 2997
                   :harja.domain.toimenpidekoodi/nimi "Ei yksilöity"}
                  :harja.domain.kanavat.kanavan-toimenpide/tyyppi :kokonaishintainen
                  :harja.domain.kanavat.kanavan-toimenpide/huoltokohde
                  {:harja.domain.kanavat.kanavan-huoltokohde/nimi "ASENNONMITTAUSLAITTEET"
                   :harja.domain.kanavat.kanavan-huoltokohde/id 5}
                  :harja.domain.kanavat.kanavan-toimenpide/pvm #inst "2017-10-31T22:00:00.000-00:00"
                  :harja.domain.kanavat.kanavan-toimenpide/id 1}]
    (is (= (first (kanavan-toimenpide/hae-sopimuksen-toimenpiteet-aikavalilta
                    db
                    40
                    (harja.pvm/luo-pvm 2016 1 1)
                    (harja.pvm/luo-pvm 2018 1 1)
                    597
                    "kokonaishintainen"))
           odotettu))))
