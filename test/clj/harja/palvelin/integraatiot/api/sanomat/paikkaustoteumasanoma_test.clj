(ns harja.palvelin.integraatiot.api.sanomat.paikkaustoteumasanoma-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.sanomat.paikkaustoteumasanoma :as sanoma]))

(deftest paikkaustoteuma-api->domain
  (let [paikkaustoteuma {:tunniste {:id 123}
                         :paikkauskohde {:nimi "Kuusamontien paikkaus"
                                         :tunniste {:id 567}}
                         :kirjausaika "2018-01-30T12:00:00Z"
                         :kokonaishintaiset-kustannukset [{:kokonaishintainen-kustannus {:selite "Liikennejärjestelyt"
                                                                                         :hinta 3500}}]
                         :yksikkohintaiset-kustannukset [{:yksikkohintainen-kustannus {:selite "asfaltti"
                                                                                       :yksikko "tonnia/€"
                                                                                       :yksikkohinta 200
                                                                                       :maara 13.2}}]}
        odotettu [{:harja.domain.paikkaus/urakka-id 666,
                   :harja.domain.paikkaus/ulkoinen-id 123,
                   :harja.domain.paikkaus/tyyppi "kokonaishintainen",
                   :harja.domain.paikkaus/kirjattu #inst"2018-01-30T12:00:00.000-00:00",
                   :harja.domain.paikkaus/paikkauskohde {:harja.domain.paikkaus/nimi "Kuusamontien paikkaus",
                                                         :harja.domain.paikkaus/ulkoinen-id 567},
                   :harja.domain.paikkaus/hinta nil,
                   :harja.domain.paikkaus/selite "asfaltti"}
                  {:harja.domain.paikkaus/yksikkohinta nil,
                   :harja.domain.paikkaus/maara nil,
                   :harja.domain.paikkaus/kirjattu #inst"2018-01-30T12:00:00.000-00:00",
                   :harja.domain.paikkaus/selite "Liikennejärjestelyt",
                   :harja.domain.paikkaus/tyyppi "yksikkohintainen",
                   :harja.domain.paikkaus/ulkoinen-id 123,
                   :harja.domain.paikkaus/urakka-id 666,
                   :harja.domain.paikkaus/yksikko nil,
                   :harja.domain.paikkaus/paikkauskohde {:harja.domain.paikkaus/nimi "Kuusamontien paikkaus",
                                                         :harja.domain.paikkaus/ulkoinen-id 567}}]]
    (is (= odotettu (sanoma/api->domain 666 paikkaustoteuma)))))