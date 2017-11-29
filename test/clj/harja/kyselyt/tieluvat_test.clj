(ns harja.kyselyt.tieluvat-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.tielupa :as tielupa]
            [harja.kyselyt.tielupa :as tielupa-q]))

(deftest hae-kanavan-toimenpiteet
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        haettu-osasto "Osasto 123"
        vastaus (tielupa-q/hae-tieluvat db {::tielupa/hakija-osasto haettu-osasto})]
    (is (every? #(= haettu-osasto (::tielupa/hakija-osasto %)) vastaus) "Jokainen löytynyt tietue vastaa hakuehtoa")))
