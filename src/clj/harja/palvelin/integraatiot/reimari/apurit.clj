(ns harja.palvelin.integraatiot.reimari.apurit
  (:require [clojure.string :as s]
            [specql.core :as specql]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.pvm :as pvm]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.tyokalut.lukot :as lukko]))

(defn formatoi-aika [muutosaika]
  (log/debug "formatoi-aika: saatiin" muutosaika)
  (let [aika-ilman-vyohyketta (xml/formatoi-xsd-datetime muutosaika)]
    (if (s/ends-with? aika-ilman-vyohyketta "Z")
      aika-ilman-vyohyketta
      (str aika-ilman-vyohyketta "Z"))))

(defn edellisen-integraatiotapahtuman-alkuaika [db jarjestelma nimi]
  (::integraatiotapahtuma/alkanut
   (last (sort-by ::integraatiotapahtuma/alkanut
                  (specql/fetch db ::integraatiotapahtuma/tapahtuma
                                #{::integraatiotapahtuma/id ::integraatiotapahtuma/alkanut
                                  [::integraatiotapahtuma/integraatio #{:harja.palvelin.integraatiot/nimi
                                                                        :harja.palvelin.integraatiot/jarjestelma}] }
                                {::integraatiotapahtuma/integraatio {:harja.palvelin.integraatiot/jarjestelma jarjestelma
                                                                     :harja.palvelin.integraatiot/nimi nimi}})))))

(defn soap-kutsu! [soap-nimi lokiviesti sanoma-fn kasittele-vastaus-fn]
  (fn [konteksti db pohja-url kayttajatunnus salasana muutosaika]
    (let [otsikot {"Content-Type" "text/xml"
                   "SOAPAction" (str "http://www.liikennevirasto.fi/xsd/harja/reimari/" soap-nimi)}
          http-asetukset {:metodi :POST
                          :url pohja-url
                          :otsikot otsikot
                          :kayttajatunnus kayttajatunnus
                          :salasana salasana
                          :muutosaika muutosaika}
          {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset (sanoma-fn muutosaika))]
      (integraatiotapahtuma/lisaa-tietoja konteksti lokiviesti)

      (kasittele-vastaus-fn db body))))
