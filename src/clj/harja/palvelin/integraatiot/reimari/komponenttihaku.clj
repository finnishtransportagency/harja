(ns harja.palvelin.integraatiot.reimari.komponenttihaku
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.reimari.apurit :as r-apurit]
            [harja.domain.vesivaylat.komponenttityyppi :as komponenttityyppi]
            [harja.domain.vesivaylat.turvalaitekomponentti :as turvalaitekomponentti]
            [harja.palvelin.integraatiot.reimari.sanomat.hae-komponenttityypit :as kt-sanoma]
            [harja.palvelin.integraatiot.reimari.sanomat.hae-turvalaitekomponentit :as tl-sanoma]
            [harja.pvm :as pvm]
            [clojure.java.jdbc :as jdbc]
            [specql.core :as specql]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [clojure.set :refer [rename-keys]]))

(defn kasittele-komponenttityypit-vastaus [db vastaus-xml]
  (log/debug "kasittele-komponenttityypit-vastaus" vastaus-xml)
  (let [sanoman-tiedot (kt-sanoma/lue-hae-komponenttityypit-vastaus vastaus-xml)
        kanta-tiedot (for [kt-tiedot sanoman-tiedot]
                       (specql/upsert! db ::komponenttityyppi/komponenttityyppi kt-tiedot))]
    (vec kanta-tiedot)))

(defn kasittele-turvalaitekomponentit-vastaus [db vastaus-xml]
  (log/debug "kasittele-turvalaitekomponentit-vastaus" vastaus-xml)
  (let [sanoman-tiedot (tl-sanoma/lue-hae-turvalaitekomponentit-vastaus vastaus-xml)
        kanta-tiedot (for [tlk-tiedot sanoman-tiedot]
                       (specql/upsert! db ::turvalaitekomponentti/turvalaitekomponentti tlk-tiedot))]
    (vec kanta-tiedot)))

(defn hae-komponenttityypit [db integraatioloki pohja-url kayttajatunnus salasana]
  (let [hakuparametrit {:soap-action "http://www.liikennevirasto.fi/xsd/harja/reimari/HaeKomponenttiTyypit"
                        :sanoma-fn (partial r-apurit/kysely-sanoma-aikavali "HaeKomponenttiTyypit")
                        :vastaus-fn kasittele-komponenttityypit-vastaus
                        :haun-nimi "hae-komponenttityypit"
                        :db db
                        :pohja-url pohja-url
                        :integraatioloki integraatioloki
                        :kayttajatunnus kayttajatunnus
                        :salasana salasana}]
    (r-apurit/kutsu-reimari-integraatiota hakuparametrit)))

(defn hae-turvalaitekomponentit [db integraatioloki pohja-url kayttajatunnus salasana]
  (let [hakuparametrit {:soap-action "http://www.liikennevirasto.fi/xsd/harja/reimari/HaeTurvalaiteKomponentit"
                        :sanoma-fn (partial r-apurit/kysely-sanoma-aikavali "HaeTurvalaiteKomponentit")
                        :vastaus-fn kasittele-turvalaitekomponentit-vastaus
                        :haun-nimi "hae-turvalaitekomponentit"
                        :db db
                        :pohja-url pohja-url
                        :integraatioloki integraatioloki
                        :kayttajatunnus kayttajatunnus
                        :salasana salasana}]
    (r-apurit/kutsu-reimari-integraatiota hakuparametrit)))