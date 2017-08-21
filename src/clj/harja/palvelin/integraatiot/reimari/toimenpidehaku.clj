(ns harja.palvelin.integraatiot.reimari.toimenpidehaku
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.reimari.apurit :as r-apurit]
            [harja.palvelin.integraatiot.reimari.sanomat.hae-toimenpiteet :as toimenpiteet-sanoma]
            [harja.domain.vesivaylat.toimenpide :as toimenpide]
            [harja.pvm :as pvm]
            [clojure.java.jdbc :as jdbc]
            [specql.core :as specql]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [clojure.set :refer [rename-keys]]))

(def avainmuunnokset {::toimenpide/id ::toimenpide/reimari-id
                      ::toimenpide/luotu ::toimenpide/reimari-luotu
                      ::toimenpide/muokattu ::toimenpide/reimari-muokattu
                      ::toimenpide/urakoitsija ::toimenpide/reimari-urakoitsija
                      ::toimenpide/turvalaite ::toimenpide/reimari-turvalaite
                      ::toimenpide/alus ::toimenpide/reimari-alus
                      ::toimenpide/vayla ::toimenpide/reimari-vayla
                      ::toimenpide/tyolaji ::toimenpide/reimari-tyolaji
                      ::toimenpide/tyoluokka ::toimenpide/reimari-tyoluokka
                      ::toimenpide/tyyppi ::toimenpide/reimari-tyyppi
                      ::toimenpide/tila ::toimenpide/reimari-tila
                      ::toimenpide/asiakas ::toimenpide/reimari-asiakas
                      ::toimenpide/vastuuhenkilo ::toimenpide/reimari-vastuuhenkilo
                      ::toimenpide/henkilo-lkm ::toimenpide/reimari-henkilo-lkm
                      ::toimenpide/komponentit ::toimenpide/reimari-komponentit
                      ::toimenpide/lisatyo? ::toimenpide/reimari-lisatyo?})

(defn lisatyo->hintatyyppi [tiedot]
  (-> tiedot
      (assoc ::toimenpide/hintatyyppi (if (::toimenpide/lisatyo? tiedot)
                                        :yksikkohintainen
                                        :kokonaishintainen))
      (dissoc ::toimenpide/lisatyo?)))

(defn kasittele-toimenpiteet-vastaus [db vastaus-xml]
  (let [sanoman-tiedot (toimenpiteet-sanoma/lue-hae-toimenpiteet-vastaus vastaus-xml)
        kanta-tiedot (for [toimenpide-tiedot sanoman-tiedot]
                       (specql/upsert! db ::toimenpide/reimari-toimenpide
                                       #{::toimenpide/reimari-id}
                                       (rename-keys toimenpide-tiedot avainmuunnokset)))]
    (vec kanta-tiedot)))

(defn toimenpiteet-kysely-sanoma [muutosaika]
  (xml/tee-xml-sanoma
   [:soap:Envelope {:xmlns:soap "http://schemas.xmlsoap.org/soap/envelope/"}
    [:soap:Body
     [:HaeToimenpiteet {:xmlns "http://www.liikennevirasto.fi/xsd/harja/reimari"}
      [:HaeToimenpiteetRequest {:muutosaika (r-apurit/formatoi-aika muutosaika)}]]]]))

(defn hae-toimenpiteet [db integraatioloki pohja-url kayttajatunnus salasana]
  (let [hakuparametrit {:soap-action "http://www.liikennevirasto.fi/xsd/harja/reimari/HaeToimenpiteet"
                        :sanoma-fn toimenpiteet-kysely-sanoma
                        :vastaus-fn kasittele-toimenpiteet-vastaus
                        :haun-nimi "hae-toimenpiteet"
                        :db db
                        :pohja-url pohja-url
                        :integraatioloki integraatioloki
                        :kayttajatunnus kayttajatunnus
                        :salasana salasana}]
    (r-apurit/kutsu-reimari-integraatiota hakuparametrit)))

;; repl-testaus: (vaihda päivämäärä lähimenneisyyteen)
;; (r-apurit/kutsu-interaktiivisesti hae-toimenpiteet harja.palvelin.main/harja-jarjestelma #inst "2017-08-01T00:00:00")
