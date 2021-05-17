(ns harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma
  (:require [clojure.data.json :as json]
            [harja.pvm :as pvm]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]))

(defn paallystyskerroksesta-velho-muottoon
  "Konvertoi annettu paallystyskerros JSON-lle, velho skeeman mukaan"
  [paallystysilmoitus paallystekerros koodisto-muunnin]
  (let [pi paallystysilmoitus
        p paallystekerros
        sanoma {:alkusijainti {:osa (:tr-alkuosa p)
                               :tie (:tr-numero p)
                               :etaisyys (:tr-alkuetaisyys p)
                               :ajorata (:tr-ajorata p)}
                :loppusijainti {:osa (:tr-loppuosa p)
                                :tie (:tr-numero p)
                                :etaisyys (:tr-loppuetaisyys p)
                                :ajorata (:tr-ajorata p)}
                :sijaintirakenne {:kaista (:tr-kaista p)}
                :ominaisuudet {:toimenpide (koodisto-muunnin "v/trtp" (:toimenpide p))
                               :sidottu-paallysrakenne {:tyyppi "sidotun-paallysrakenteen-tyyppi/spt01" ; "kulutuskerros" aina
                                                        :paallysteen-tyyppi (koodisto-muunnin "v/pt" (:toimenpide p))


                                                        }




                               }
                :lahdejarjestelman-id "what here?"
                :lahdejarjestelma "lahdejarjestelma/lj06"
                :alkaen (:aloituspvm p)
                :paatyen (:valmispvm-kohde p)}]
    sanoma))

(defn date-writer [key value]
  (if (or (= java.sql.Date (type value))
          (= java.sql.Timestamp (type value)))
    (pvm/aika-iso8601-aikavyohykkeen-kanssa value)
    value))

(defn muodosta
  "Ennen kun tiedämme enemmän, muodostetaan kaikki päällystyskerrokset ja alustat erikseen"
  [urakka paallystysilmoitus koodisto-muunnin]
  (let [sanoma {:paallystekerros (as-> (:paallystekerros paallystysilmoitus) a
                                       (map #(paallystyskerroksesta-velho-muottoon paallystysilmoitus % koodisto-muunnin) a)
                                       (map #(json/write-str % :value-fn date-writer) a)
                                       (vec a))
                :alusta nil}]
    (println "petar sanoma je " (pr-str sanoma))
    sanoma))


