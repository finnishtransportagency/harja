(ns harja.palvelin.integraatiot.reimari.turvalaiteryhmahaku
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.reimari.apurit :as r-apurit]
            [harja.palvelin.integraatiot.reimari.sanomat.hae-turvalaiteryhmat :as turvalaiteryhmat-sanoma]
            [harja.domain.vesivaylat.turvalaiteryhma :as turvalaiteryhma]
            [harja.kyselyt.vesivaylat.turvalaiteryhmat :as turvalaiteryhmat-kysely]
            [harja.pvm :as pvm]
            [clojure.java.jdbc :as jdbc]
            [specql.core :as specql]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [clojure.set :refer [rename-keys]]
            [namespacefy.core :refer [unnamespacefy]]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]))


(defn lisaa-muokkaustiedot
  [turvalaiteryhma]
  (merge turvalaiteryhma {:luoja "Integraatio"
                          :muokkaaja "Integraatio"}))

(defn kasittele-turvalaiteryhma-vastaus [db vastaus-xml]
  (jdbc/with-db-transaction [db db]
                            (let [sanoman-tiedot (turvalaiteryhmat-sanoma/lue-hae-turvalaiteryhmat-vastaus vastaus-xml)
                                  kanta-tiedot (for [turvalaiteryhma-tiedot-raaka sanoman-tiedot
                                                     :let [turvalaiteryhma-tiedot (-> turvalaiteryhma-tiedot-raaka
                                                                                      unnamespacefy
                                                                                      konv/turvalaiteryhman-turvalaitteet->array
                                                                                      lisaa-muokkaustiedot)]]
                                                 (konv/array->vec
                                                   (turvalaiteryhmat-kysely/vie-turvalaiteryhmatauluun<! db turvalaiteryhma-tiedot) :turvalaitteet))]
                              (vec kanta-tiedot))))

(defn hae-turvalaiteryhmat [db integraatioloki pohja-url kayttajatunnus salasana]
  (let [hakuparametrit {:soap-action "http://www.liikennevirasto.fi/xsd/harja/reimari/HaeTurvalaiteryhmat"
                        :sanoma-fn (partial r-apurit/kysely-sanoma-aikavali "HaeTurvalaiteryhmat")
                        :vastaus-fn kasittele-turvalaiteryhma-vastaus
                        :haun-nimi "hae-turvalaiteryhmat"
                        :db db
                        :pohja-url pohja-url
                        :integraatioloki integraatioloki
                        :kayttajatunnus kayttajatunnus
                        :salasana salasana}]
    (r-apurit/kutsu-reimari-integraatiota hakuparametrit)))