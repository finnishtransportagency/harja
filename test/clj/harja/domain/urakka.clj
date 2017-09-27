(ns harja.domain.urakka
  (:require [clojure.test :refer :all]
            [harja.domain.urakka :as u]))

(deftest vesivaylaurakan-tunnistaminen
  (testing "Tunnistetaanko vesiväyläurakaksi"
    (is (true? (u/vesivaylaurakka?
                 {:tyyppi :vesivayla-hoito})))

    (is (true? (u/vesivaylaurakka?
                 {:tyyppi :vesivayla-ruoppaus})))

    (is (true? (u/vesivaylaurakka?
                 {:tyyppi :vesivayla-turvalaitteiden-korjaus})))

    (is (false? (u/vesivaylaurakka? {:tyyppi nil})))

    (is (false? (u/vesivaylaurakka? {:tyyppi :hoito})))

    (is (false? (u/vesivaylaurakka? nil))))

  (testing "Kanavaurakat ovat myös vesiväyläurakoita"
    (is (true? (u/vesivaylaurakka?
                {:tyyppi :vesivayla-kanavien-hoito})))

    (is (true? (u/vesivaylaurakka?
                 {:tyyppi :vesivayla-kanavien-korjaus}))))

  (testing "Kanavaurakat ovat myös vesiväyläurakoita"
    (is (true? (u/kanavaurakka?
                 {:tyyppi :vesivayla-kanavien-hoito})))

    (is (true? (u/kanavaurakka?
                 {:tyyppi :vesivayla-kanavien-korjaus})))

    (is (false? (u/kanavaurakka?
                 {:tyyppi :vesivayla-ruoppaus})))

    (is (false? (u/kanavaurakka? {:tyyppi nil})))
    (is (false? (u/kanavaurakka? {:tyyppi :hoito})))
    (is (false? (u/kanavaurakka? nil)))))