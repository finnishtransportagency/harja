(ns harja.palvelin.integraatiot.reimari.turvalaiteryhmahaku
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.reimari.apurit :as r-apurit]
            [harja.palvelin.integraatiot.reimari.sanomat.hae-turvalaiteryhmat :as turvalaiteryhmat-sanoma]
            [harja.domain.vesivaylat.turvalaiteryhma :as turvalaiteryhma]
            [harja.pvm :as pvm]
            [clojure.java.jdbc :as jdbc]
            [specql.core :as specql]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [clojure.set :refer [rename-keys]]))


(defn kasittele-turvalaiteryhma-vastaus [db vastaus-xml]
  (let [sanoman-tiedot (turvalaiteryhmat-sanoma/lue-hae-turvalaiteryhmat-vastaus vastaus-xml)
        kanta-tiedot (for [turvalaiteryhma-tiedot-raaka sanoman-tiedot
                           :let [turvalaiteryhma-tiedot turvalaiteryhma-tiedot-raaka]]
                       (specql/upsert! db ::turvalaiteryhma/reimari-turvalaiteryhma #{::turvalaiteryhma/tunnus} turvalaiteryhma-tiedot))]
    (vec kanta-tiedot)))

(defn hae-turvalaiteryhmat [db integraatioloki pohja-url kayttajatunnus salasana]
  (let [hakuparametrit {:soap-action "http://www.liikennevirasto.fi/xsd/harja/reimari/HaeTurvalaiteryhmat"
                        :sanoma-fn (partial r-apurit/kysely-sanoma-aikavali "HaeTurvalaiteryhmat")
                        :vastaus-fn kasittele-turvalaiteryhma-vastaus
                        :haun-nimi "hae-turvalaitteet"
                        :db db
                        :pohja-url pohja-url
                        :integraatioloki integraatioloki
                        :kayttajatunnus kayttajatunnus
                        :salasana salasana}]
    (r-apurit/kutsu-reimari-integraatiota hakuparametrit)))

;; repl-testaus: (vaihda päivämäärä lähimenneisyyteen)
;; (harja.palvelin.integraatiot.reimari.apurit/kutsu-interaktiivisesti harja.palvelin.integraatiot.reimari.turvalaiteryhmahaku/hae-turvalaiteryhmat harja.palvelin.main/harja-jarjestelma #inst "2016-08-01T00:00:00" #inst "2016-09-01T00:00:00")
