(ns harja.palvelin.integraatiot.reimari.vikahaku
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.reimari.apurit :as r-apurit]
            [harja.palvelin.integraatiot.reimari.sanomat.hae-viat :as viat-sanoma]
            [harja.domain.vesivaylat.vikailmoitus :as vv-vikailmoitus]
            [harja.pvm :as pvm]
            [clojure.java.jdbc :as jdbc]
            [specql.core :as specql]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [clojure.set :refer [rename-keys]]))

(def avainmuunnokset {::vv-vikailmoitus/id ::vv-vikailmoitus/reimari-id
                      ::vv-vikailmoitus/turvalaitenro ::vv-vikailmoitus/reimari-turvalaitenro
                      ::vv-vikailmoitus/epakunnossa ::vv-vikailmoitus/reimari-epakunnossa?
                      ::vv-vikailmoitus/ilmoittaja ::vv-vikailmoitus/reimari-ilmoittaja
                      ::vv-vikailmoitus/ilmoittajan-yhteystieto ::vv-vikailmoitus/reimari-ilmoittajan-yhteystieto
                      ::vv-vikailmoitus/havaittu ::vv-vikailmoitus/reimari-havaittu
                      ::vv-vikailmoitus/kirjattu ::vv-vikailmoitus/reimari-kirjattu
                      ::vv-vikailmoitus/korjattu ::vv-vikailmoitus/reimari-korjattu
                      ::vv-vikailmoitus/muokattu ::vv-vikailmoitus/reimari-muokattu
                      ::vv-vikailmoitus/tyyppikoodi ::vv-vikailmoitus/reimari-tyyppikoodi
                      ::vv-vikailmoitus/tilakoodi ::vv-vikailmoitus/reimari-tilakoodi
                      ::vv-vikailmoitus/lisatiedot ::vv-vikailmoitus/reimari-lisatiedot
                      ::vv-vikailmoitus/luontiaika ::vv-vikailmoitus/reimari-luontiaika
                      ::vv-vikailmoitus/luoja ::vv-vikailmoitus/reimari-luoja
                      ::vv-vikailmoitus/muokkaaja ::vv-vikailmoitus/reimari-muokkaaja
                      })

(defn kasittele-viat-vastaus [db vastaus-xml]
  (log/debug "kasittele-viat-vastaus" vastaus-xml)
  (let [sanoman-tiedot (viat-sanoma/lue-hae-viat-vastaus vastaus-xml)
        kanta-tiedot (for [viat-tiedot sanoman-tiedot]
                       (specql/upsert! db ::vv-vikailmoitus/vikailmoitus
                                       (rename-keys viat-tiedot avainmuunnokset)))]
    (vec kanta-tiedot)))

(defn hae-viat [db integraatioloki pohja-url kayttajatunnus salasana]
  (let [hakuparametrit {:soap-action "http://www.liikennevirasto.fi/xsd/harja/reimari/HaeViat"
                        :sanoma-fn (partial r-apurit/kysely-sanoma-aikavali "HaeViat")
                        :vastaus-fn kasittele-viat-vastaus
                        :haun-nimi "hae-viat"
                        :db db
                        :pohja-url pohja-url
                        :integraatioloki integraatioloki
                        :kayttajatunnus kayttajatunnus
                        :salasana salasana}]
    (r-apurit/kutsu-reimari-integraatiota hakuparametrit)))