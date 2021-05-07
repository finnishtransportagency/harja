(ns harja.palvelin.integraatiot.velho.sanomat.toimenpiteet-tienrakennetoimenpiteet-lahetyssanoma

  (:require [clojure.data.json :as json]
            [harja.pvm :as pvm]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]))

(defn date-writer [key value]
  (if (= java.sql.Date (type value))
    (pvm/aika-iso8601-aikavyohykkeen-kanssa value)
    value))

(defn muodosta [urakka kohteet]
  (let [sanoma {:alkusijainti {:osa 1
                               :tie 2
                               :etaisyys 2
                               :ajorata 34}
                :loppusijainti {:osa 3

                                }



                }]
    (json/write-str {:stvarno sanoma :petar urakka :teodosin kohteet}
                    :value-fn date-writer)))


