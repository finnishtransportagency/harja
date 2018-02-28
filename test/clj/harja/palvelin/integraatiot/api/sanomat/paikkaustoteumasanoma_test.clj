(ns harja.palvelin.integraatiot.api.sanomat.paikkaustoteumasanoma-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.sanomat.paikkaustoteumasanoma :as sanoma]
            [taoensso.timbre :as log]))

(deftest paikkaustoteuma-api->domain
  (let [paikkaustoteuma {:tunniste {:id 123}
                         :paikkauskohde {:nimi "Kuusamontien paikkaus"
                                         :tunniste {:id 567}}
                         :kirjausaika "2018-01-30T12:00:00Z"
                         :kokonaishintaiset-kustannukset [{:kokonaishintainen-kustannus {:selite "Liikennejärjestelyt"
                                                                                         :hinta 3500}}
                                                          {:kokonaishintainen-kustannus {:selite "Lähtömaksu"
                                                                                         :hinta 256.70}}]
                         :yksikkohintaiset-kustannukset [{:yksikkohintainen-kustannus {:selite "asfaltti"
                                                                                       :yksikko "t"
                                                                                       :yksikkohinta 2000
                                                                                       :maara 5}}
                                                         {:yksikkohintainen-kustannus {:selite "bitumi"
                                                                                       :yksikko "kg"
                                                                                       :yksikkohinta 32.5
                                                                                       :maara 12.5}}]}
        odotettu [{:harja.domain.paikkaus/yksikkohinta 2000M,
                   :harja.domain.paikkaus/maara 5M,
                   :harja.domain.paikkaus/kirjattu #inst"2018-01-30T12:00:00.000-00:00",
                   :harja.domain.paikkaus/selite "asfaltti",
                   :harja.domain.paikkaus/tyyppi "yksikkohintainen",
                   :harja.domain.paikkaus/ulkoinen-id 123,
                   :harja.domain.paikkaus/urakka-id 666,
                   :harja.domain.paikkaus/yksikko "t",
                   :harja.domain.paikkaus/paikkauskohde {:harja.domain.paikkaus/nimi "Kuusamontien paikkaus",
                                                         :harja.domain.paikkaus/ulkoinen-id 567}}
                  {:harja.domain.paikkaus/yksikkohinta 32.5M,
                   :harja.domain.paikkaus/maara 12.5M,
                   :harja.domain.paikkaus/kirjattu #inst"2018-01-30T12:00:00.000-00:00",
                   :harja.domain.paikkaus/selite "bitumi",
                   :harja.domain.paikkaus/tyyppi "yksikkohintainen",
                   :harja.domain.paikkaus/ulkoinen-id 123,
                   :harja.domain.paikkaus/urakka-id 666,
                   :harja.domain.paikkaus/yksikko "kg",
                   :harja.domain.paikkaus/paikkauskohde {:harja.domain.paikkaus/nimi "Kuusamontien paikkaus",
                                                         :harja.domain.paikkaus/ulkoinen-id 567}}
                  {:harja.domain.paikkaus/urakka-id 666,
                   :harja.domain.paikkaus/ulkoinen-id 123,
                   :harja.domain.paikkaus/tyyppi "kokonaishintainen",
                   :harja.domain.paikkaus/kirjattu #inst"2018-01-30T12:00:00.000-00:00",
                   :harja.domain.paikkaus/paikkauskohde {:harja.domain.paikkaus/nimi "Kuusamontien paikkaus",
                                                         :harja.domain.paikkaus/ulkoinen-id 567},
                   :harja.domain.paikkaus/hinta 3500M,
                   :harja.domain.paikkaus/selite "Liikennejärjestelyt"}
                  {:harja.domain.paikkaus/urakka-id 666,
                   :harja.domain.paikkaus/ulkoinen-id 123,
                   :harja.domain.paikkaus/tyyppi "kokonaishintainen",
                   :harja.domain.paikkaus/kirjattu #inst"2018-01-30T12:00:00.000-00:00",
                   :harja.domain.paikkaus/paikkauskohde {:harja.domain.paikkaus/nimi "Kuusamontien paikkaus",
                                                         :harja.domain.paikkaus/ulkoinen-id 567},
                   :harja.domain.paikkaus/hinta 256.70M,
                   :harja.domain.paikkaus/selite "Lähtömaksu"}]]
               (is (= odotettu (sanoma/api->domain 666 paikkaustoteuma)))))