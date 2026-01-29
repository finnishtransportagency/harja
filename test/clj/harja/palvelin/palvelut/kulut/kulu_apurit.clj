(ns harja.palvelin.palvelut.kulut.kulu-apurit
  (:require [harja.testi :refer :all]
            [harja.pvm :as pvm]
            [harja.domain.kulut :as domain-kulut]))

(defn uusi-kulu [urakka-id summa vuosi urakan-alkupvm]
  (let [erapaiva (pvm/->pvm (str "1.11." vuosi))]
    {:id nil
     :urakka urakka-id
     :viite "12345678"
     :erapaiva erapaiva
     :kokonaissumma summa
     :tyyppi "laskutettava"
     :kohdistukset [{:kohdistus-id nil
                     :rivi 1
                     :summa (/ summa 2)
                     :toimenpideinstanssi (hae-toimenpideinstanssi-id urakka-id "23116")
                     :tehtavaryhma (hae-tehtavaryhman-id "V - Vesakonraivaukset ja puun poisto")
                     :tehtava (hae-tehtavan-id-nimella "Runkopuiden poisto")
                     :tyyppi :hankintakulu
                     :tavoitehintainen :true}
                    {:kohdistus-id nil
                     :rivi 2
                     :summa (/ summa 2)
                     :toimenpideinstanssi (hae-toimenpideinstanssi-id urakka-id "23116")
                     :tehtavaryhma (hae-tehtavaryhman-id "V - Vesakonraivaukset ja puun poisto")
                     :tehtava (hae-tehtavan-id-nimella "Runkopuiden poisto")
                     :tyyppi :hankintakulu
                     :tavoitehintainen :true}]
     :koontilaskun-kuukausi (domain-kulut/pvm->koontilaskun-kuukausi erapaiva urakan-alkupvm)}))
