(ns harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma

  (:require [clojure.data.json :as json]
            [harja.pvm :as pvm]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]))

(defn date-writer [key value]
  (if (or (= java.sql.Date (type value))
          (= java.sql.Timestamp (type value)))
    (pvm/aika-iso8601-aikavyohykkeen-kanssa value)
    value))

(defn muodosta [urakka paallystysilmoitukset]
  (assert (= 1 (count paallystysilmoitukset)) "Kohteita täytyy olla 1")
  (let [p (:paallystysilmoitus (first paallystysilmoitukset))
        _ (println "petar kohde " (pr-str p))
        sanoma {:alkusijainti {:osa (:kohdeosa_aosa p)
                               :tie (:kohdeosa_tie p)
                               :etaisyys (:kohdeosa_aet p)
                               :ajorata (:kohdeosa_ajorata p)}
                :loppusijainti {:osa (:kohdeosa_losa p)
                                :tie (:kohdeosa_tie p)
                                :etaisyys (:kohdeosa_let p)
                                :ajorata (:kohdeosa_ajorata p)}
                :sijaintirakenne {:kaista (:kohdeosa_kaista p)}
                :ominaisuudet {:toimenpide "blabla"}
                :lahdejarjestelman-id "petar what here?"
                :lahdejarjestelma "lahdejarjestelma/lj06"
                :alkaen (:aloituspvm p)
                :paatyen (:valmispvm-kohde p)}]
    (json/write-str sanoma
                    :value-fn date-writer)))


