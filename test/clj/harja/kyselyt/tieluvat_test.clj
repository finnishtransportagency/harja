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


(deftest onko-olemassa-ulkoisella-tunnisteella
  (let [db (tietokanta/luo-tietokanta testitietokanta)]
    (is (false? (tielupa-q/onko-olemassa-ulkoisella-tunnisteella? db nil)))
    (is (true? (tielupa-q/onko-olemassa-ulkoisella-tunnisteella? db 666)))
    (is (false? (tielupa-q/onko-olemassa-ulkoisella-tunnisteella? db 2345)))
    (is (false? (tielupa-q/onko-olemassa-ulkoisella-tunnisteella? db "foo")))))