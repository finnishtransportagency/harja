(ns harja.palvelin.integraatiot.reimari.apurit
  (:require [clojure.string :as s]
            [specql.core :as specql]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.tyokalut.lukot :as lukko]))

(defn aikaleima [text]
  (when-not (str/blank? text)
    (.toDate (xml/parsi-xsd-datetime-ms-aikaleimalla text))))

(defn paivamaara [text]
  (when-not (str/blank? text)
    (xml/parsi-paivamaara text)))

(defn formatoi-aika [muutosaika]
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
                                                                     :harja.palvelin.integraatiot/nimi nimi}
                                 ::integraatiotapahtuma/onnistunut true })))))


(defn kutsu-reimari-integraatiota* [{:keys [db pohja-url kayttajatunnus salasana alkuaika loppuaika muutosaika] :as hakuparametrit} konteksti]
  ;; {:pre [(assert (and db pohja-url kayttajatunnus salasana muutosaika) [db pohja-url kayttajatunnus salasana muutosaika])]}
  (let [otsikot {"Content-Type" "text/xml"
                 "SOAPAction" (:soap-action hakuparametrit)}
        http-asetukset {:metodi :POST
                        :url pohja-url
                        :otsikot otsikot
                        :kayttajatunnus kayttajatunnus
                        :salasana salasana
                        :muutosaika muutosaika}
        {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset ((:sanoma-fn hakuparametrit) (or muutosaika [alkuaika loppuaika])))]
    (integraatiotapahtuma/lisaa-tietoja konteksti (str "Haku: " (:haun-nimi hakuparametrit) " ajalta: " (or muutosaika [alkuaika loppuaika])))

    ((:vastaus-fn hakuparametrit) db body)))

(defn kutsu-reimari-integraatiota
  [{:keys [db integraatioloki haun-nimi] :as hakuparametrit}]
  (let [muutosaika (edellisen-integraatiotapahtuman-alkuaika db "reimari" haun-nimi)]
    (if-not muutosaika
      (log/info "Reimari-integraatio: ei löytynyt edellistä onnistunutta" haun-nimi "-tapahtumaa")
      (lukko/yrita-ajaa-lukon-kanssa
       db (str haun-nimi)
       (fn []
         (integraatiotapahtuma/suorita-integraatio
          db integraatioloki "reimari" haun-nimi
          (fn [konteksti]
            (kutsu-reimari-integraatiota* (assoc hakuparametrit :muutosaika muutosaika) konteksti))))))))



;; käyttö:
;; (kutsu-interaktiivisesti hae-viat harja.palvelin.main/harja-jarjestelma #inst "2017-08-01T00:00:00")
;; tai
;; (kutsu-interaktiivisesti hae-viat (assoc-in harja.palvelin.main/harja-jarjestelma  [:reimari :salasana] "asdf") #inst "2017-08-01T00:00:00")
(defn kutsu-interaktiivisesti [fn j alkuaika]
  (let [[db il rk] (as-> j x
                     (select-keys x [:db :integraatioloki :reimari])
                     (map second x))
        [kt ss pu] (as-> rk x
                     (select-keys x [:kayttajatunnus :salasana :pohja-url])
                     (map second x))]
    (log/debug "tunnus" kt "url" pu)
    (with-redefs [edellisen-integraatiotapahtuman-alkuaika (constantly alkuaika)]
      (fn db il pu kt ss))))


(defn kysely-sanoma [tyyppi attribuutit]

  (let [;; esim tyyppi = HaeKomponenttiTyypit -> :HaeKomponenttiTyypit ja :HaeKomponenttiTyypitRequest
        tyyppi-kw (keyword tyyppi)
        tyyppi-request-kw (keyword (str tyyppi "Request"))]
    (xml/tee-xml-sanoma
     [:soap:Envelope {:xmlns:soap "http://schemas.xmlsoap.org/soap/envelope/"}
      [:soap:Body
       [tyyppi-kw {:xmlns "http://www.liikennevirasto.fi/xsd/harja/reimari"}
        [tyyppi-request-kw attribuutit]]]])))

(defn kysely-sanoma-aikavali [tyyppi [alkuaika loppuaika]]
  (kysely-sanoma tyyppi {:alkuaika (formatoi-aika alkuaika)
                         :loppuaika (formatoi-aika loppuaika)}))

(defn kysely-sanoma-muutosaika [tyyppi muutosaika]
  (kysely-sanoma tyyppi {:muutosaika (formatoi-aika muutosaika)}))
