(ns harja.palvelin.integraatiot.reimari.toimenpidehaku
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.reimari.apurit :as r-apurit]
            [harja.palvelin.integraatiot.reimari.sanomat.hae-toimenpiteet :as toimenpiteet-sanoma]
            [harja.domain.vesivaylat.toimenpide :as toimenpide]
            [harja.domain.vesivaylat.sopimus :as sopimus]
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

(defn sopimustiedot-ok? [toimenpide-tiedot]
  ;; Sopimustiedot määriteltiin alunperin pakollisiksi rajapinnassa, mutta
  ;; onkin poikkeustapauksia jossa reimarin tietokantaan päätyy sopimuksettomia
  ;; toimenpiteitä. Näitä emme osaa käsitellä joten ne jätetään tuomatta Harjaan.
  (let [sop (-> toimenpide-tiedot ::toimenpide/reimari-sopimus)
        sisaltaa-tekstia #(-> sop % str not-empty)]
    (and (sisaltaa-tekstia ::sopimus/r-nro)
         (sisaltaa-tekstia ::sopimus/r-nimi))))

(defn kasittele-toimenpiteet-vastaus [db vastaus-xml]
  (let [sanoman-tiedot (toimenpiteet-sanoma/lue-hae-toimenpiteet-vastaus vastaus-xml)
        kanta-tiedot (for [toimenpide-tiedot-raaka sanoman-tiedot
                           :let [toimenpide-tiedot (rename-keys toimenpide-tiedot-raaka avainmuunnokset)]
                           :when (sopimustiedot-ok? toimenpide-tiedot)]
                       ;; Hintatyypin asettaminen tehdään triggerissä
                       (specql/upsert! db ::toimenpide/reimari-toimenpide #{::toimenpide/reimari-id} toimenpide-tiedot))]

    (vec kanta-tiedot)))

(defn hae-toimenpiteet [db integraatioloki pohja-url kayttajatunnus salasana]
  (let [hakuparametrit {:soap-action "http://www.liikennevirasto.fi/xsd/harja/reimari/HaeToimenpiteet"
                        :sanoma-fn (partial r-apurit/kysely-sanoma-aikavali "HaeToimenpiteet")
                        :vastaus-fn kasittele-toimenpiteet-vastaus
                        :haun-nimi "hae-toimenpiteet"
                        :db db
                        :pohja-url pohja-url
                        :integraatioloki integraatioloki
                        :kayttajatunnus kayttajatunnus
                        :salasana salasana}]
    (r-apurit/kutsu-reimari-integraatiota hakuparametrit)))