(ns harja.domain.kulut-test
  (:require [clojure.test :refer [deftest is]]
            [harja.pvm :as pvm]))

(deftest koontilaskun-kuukausi->kuukausi-toimii
  (is (= 1 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "tammikuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "tammikuu ei muuttunut 1:ksi")
  (is (= 2 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "helmikuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "helmikuu ei muuttunut 2:ksi")
  (is (= 3 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "maaliskuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "maaliskuu ei muuttunut 3:ksi")
  (is (= 4 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "huhtikuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "huhtikuu ei muuttunut 4:ksi")
  (is (= 5 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "toukokuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "toukokuu ei muuttunut 5:ksi")
  (is (= 6 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "kesäkuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "kesäkuu ei muuttunut 6:ksi")
  (is (= 7 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "heinäkuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "heinäkuu ei muuttunut 7:ksi")
  (is (= 8 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "elokuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "elokuu ei muuttunut 8:ksi")
  (is (= 9 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "syyskuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "syyskuu ei muuttunut 9:ksi")
  (is (= 10 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "lokakuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "lokakuu ei muuttunut 10:ksi")
  (is (= 11 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "marraskuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "marraskuu ei muuttunut 11:ksi")
  (is (= 12 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "joulukuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "joulukuu ei muuttunut 12:ksi"))

(deftest koontilaskun-kuukausi->vuosi-toimii
  (println "(pvm/->pvm \"1.10.2023\")" (pvm/->pvm "1.10.2023"))
  (println "(pvm/->pvm \"30.9.2027\")" (pvm/->pvm "30.9.2027"))
  (is (= 2023 (harja.domain.kulut/koontilaskun-kuukausi->vuosi "lokakuu/1-hoitovuosi" (pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2028"))) "vuosi ei muuttunut 2023:ksi")
  (is (= 2024 (harja.domain.kulut/koontilaskun-kuukausi->vuosi "marraskuu/2-hoitovuosi" (pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2028"))) "vuosi ei muuttunut 2024:ksi")
  (is (= 2025 (harja.domain.kulut/koontilaskun-kuukausi->vuosi "maaliskuu/2-hoitovuosi" (pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2028"))) "vuosi ei muuttunut 2025:ksi")
  (is (= 2025 (harja.domain.kulut/koontilaskun-kuukausi->vuosi "lokakuu/3-hoitovuosi" (pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2028"))) "vuosi ei muuttunut 2025:ksi")
  (is (= 2026 (harja.domain.kulut/koontilaskun-kuukausi->vuosi "lokakuu/4-hoitovuosi" (pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2028"))) "vuosi ei muuttunut 2026:ksi")
  (is (= 2027 (harja.domain.kulut/koontilaskun-kuukausi->vuosi "huhtikuu/4-hoitovuosi" (pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2028"))) "vuosi ei muuttunut 2026:ksi")
  (is (= 2028 (harja.domain.kulut/koontilaskun-kuukausi->vuosi "toukokuu/5-hoitovuosi" (pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2028"))) "vuosi ei muuttunut 2027:ksi"))
