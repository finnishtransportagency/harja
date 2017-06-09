(ns harja.palvelin.integraatiot.reimari.toimenpidehaku
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.domain.vesivaylat.alus :as vv-alus]
            [harja.domain.vesivaylat.toimenpide :as toimenpide]
            [harja.domain.vesivaylat.turvalaite :as turvalaite]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.integraatiot.reimari.sanomat.hae-toimenpiteet :as sanoma]
            [harja.pvm :as pvm]
            [clojure.java.jdbc :as jdbc]
            [specql.core :as specql]
            [clojure.string :as s]
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
                      ::toimenpide/komponentit ::toimenpide/reimari-komponentit
                      })

(defn kasittele-vastaus [db urakka-id vastaus-xml]
  ;; (log/debug "kasittele-vastaus" vastaus-xml)
  (let [sanoman-tiedot (sanoma/lue-hae-toimenpiteet-vastaus vastaus-xml)
        kanta-tiedot (for [toimenpide-tiedot sanoman-tiedot]
                       (specql/upsert! db ::toimenpide/reimari-toimenpide
                                       (merge (rename-keys toimenpide-tiedot avainmuunnokset)
                                              {::toimenpide/urakka-id urakka-id})))]
    (vec kanta-tiedot)))

(defn- formatoi-aika [muutosaika]
  (let [aika-ilman-vyohyketta (xml/formatoi-xsd-datetime muutosaika)]
    (if (s/ends-with? aika-ilman-vyohyketta "Z")
      aika-ilman-vyohyketta
      (str aika-ilman-vyohyketta "Z"))))

(defn kysely-sanoma [muutosaika]
  (xml/tee-xml-sanoma
   [:soap:Envelope {:xmlns:soap "http://schemas.xmlsoap.org/soap/envelope/"}
    [:soap:Body
     [:HaeToimenpiteet {:xmlns "http://www.liikennevirasto.fi/xsd/harja/reimari"}
      [:HaeToimenpiteetRequest {:muutosaika (formatoi-aika muutosaika)}]]]]))

(defn hae-toimenpiteet* [konteksti db pohja-url kayttajatunnus salasana muutosaika]
  (let [otsikot {"Content-Type" "text/xml"
                 "SOAPAction" "http://www.liikennevirasto.fi/xsd/harja/reimari/HaeToimenpiteet"}
        http-asetukset {:metodi :POST
                        :url pohja-url
                        :otsikot otsikot
                        :kayttajatunnus kayttajatunnus
                        :salasana salasana
                        :muutosaika muutosaika}
        {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset (kysely-sanoma muutosaika))]
    (integraatiotapahtuma/lisaa-tietoja konteksti (str "Haetaan uudet toimenpiteet alkaen " muutosaika))
    (kasittele-vastaus db nil body))) ;; TODO Täytyy päätellä urakka reimari-toimenpiteelle

(defn edellisen-integraatiotapahtuman-alkuaika [db jarjestelma nimi]
  (last (sort-by ::integraatiotapahtuma/alkanut
                 (specql/fetch db ::integraatiotapahtuma/tapahtuma
                               #{::integraatiotapahtuma/id ::integraatiotapahtuma/alkanut
                                 [::integraatiotapahtuma/integraatio #{:harja.palvelin.integraatiot/nimi
                                                                      :harja.palvelin.integraatiot/jarjestelma}] }
                               {::integraatiotapahtuma/integraatio {:harja.palvelin.integraatiot/jarjestelma jarjestelma
                                                                   :harja.palvelin.integraatiot/nimi nimi}}))))

(defn hae-toimenpiteet [db integraatioloki pohja-url kayttajatunnus salasana]
  (let [muutosaika (edellisen-integraatiotapahtuman-alkuaika db "hae-toimenpiteet" "reimari")]
    (if-not muutosaika
      (log/info "Reimarin toimenpidehaku: ei löytynyt edellistä toimenpiteiden hakuaikaa, hakua ei tehdä")
      (lukko/yrita-ajaa-lukon-kanssa
       db "reimari-hae-toimenpiteet"
       (fn []
         (integraatiotapahtuma/suorita-integraatio
          db integraatioloki "reimari" "hae-toimenpiteet"
          #(hae-toimenpiteet* % db pohja-url kayttajatunnus salasana muutosaika)))))))
