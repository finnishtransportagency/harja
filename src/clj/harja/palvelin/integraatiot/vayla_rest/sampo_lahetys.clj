(ns harja.palvelin.integraatiot.vayla-rest.sampo-lahetys
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-samposta-sanoma :as kuittaus-sampoon-sanoma])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn laheta-sampoviesti-rajapintaan [db integraatioloki api-sampo-asetukset integraatio sampoviesti-xml]
  (try+
    (let [{palvelin :palvelin
           lahetys-url :lahetys-url
           kayttajatunnus :kayttajatunnus
           salasana :salasana} api-sampo-asetukset
          url (str palvelin lahetys-url)
          vastaus (integraatiotapahtuma/suorita-integraatio
                    db integraatioloki "sampo" integraatio nil
                    (fn [konteksti]
                      (let [http-asetukset {:metodi :POST
                                            :url url
                                            :otsikot {"Content-Type" "application/xml"}
                                            :kayttajatunnus kayttajatunnus
                                            :salasana salasana}
                            {body :body headers :headers status :status} (integraatiotapahtuma/laheta konteksti :http http-asetukset sampoviesti-xml)]
                        (kuittaus-sampoon-sanoma/lue-kuittaus body))))
          _ (log/debug "rest-api Sampo lähetys onnistui")]
      vastaus)
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      (do
        (log/error "rest-api sähköpostin lähetys epäonnistui! " virheet)
        false))))
