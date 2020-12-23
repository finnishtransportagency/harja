(ns harja.integraatio
  (:require [harja.palvelin.integraatiot.sampo.tyokalut :as sampo-tk]
            [harja.palvelin.integraatiot.tloik.tyokalut :as tloik-tk]
            [harja.tyokalut.env :as env]))

(def sonja-asetukset {:url (str "tcp://" (env/env "HARJA_SONJA_BROKER_HOST" "localhost") ":" (env/env "HARJA_SONJA_BROKER_PORT" 61616))
                      :kayttaja ""
                      :salasana ""
                      :tyyppi :activemq
                      :paivitystiheys-ms 3000})

(def itmf-asetukset {:url (str "tcp://" (env/env "HARJA_ITMF_BROKER_HOST" "localhost") ":" (env/env "HARJA_ITMF_BROKER_PORT" 61626))
                     :kayttaja ""
                     :salasana ""
                     :tyyppi :activemq
                     :paivitystiheys-ms 3000})

(def integraatio-sampo-asetukset {:lahetysjono-sisaan sampo-tk/+lahetysjono-sisaan+
                                  :kuittausjono-sisaan sampo-tk/+kuittausjono-sisaan+
                                  :lahetysjono-ulos sampo-tk/+lahetysjono-ulos+
                                  :kuittausjono-ulos sampo-tk/+kuittausjono-ulos+
                                  :paivittainen-lahetysaika nil})
