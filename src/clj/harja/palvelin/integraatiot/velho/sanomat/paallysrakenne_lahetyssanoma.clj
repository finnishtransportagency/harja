(ns harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma
  (:require [clojure.data.json :as json]
            [harja.pvm :as pvm]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]))

(defn paallystekerroksesta-velho-muottoon
  "Konvertoi annettu paallystyskerros JSON-lle, velho skeeman mukaan"
  [paallystekerros koodisto-muunnin]
  (println "petar dobio " (pr-str paallystekerros))
  (let [p paallystekerros
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
                                                        :paallysteen-tyyppi (koodisto-muunnin "v/pt" (:massatyyppi p))
                                                        :paallystemassa {:asfalttirouheen-osuus-asfalttimassassa 0 ; todo ?
                                                                         :bitumiprosentti 6.6 ; todo ?
                                                                         :paallystemassan-runkoaine {:materiaali [] ; todo ?
                                                                                                     :uusiomateriaalin-kayttomaara 6 ; todo ?
                                                                                                     :kuulamyllyarvo 5 ; todo ?
                                                                                                     :kuulamyllyarvon-luokka "KM-arvoluokka" ; todo ?
                                                                                                     :litteysluku 1 ; todo ?
                                                                                                     :maksimi-raekoko (koodisto-muunnin "v/mrk" (:max-raekoko p))}
                                                                         :paallystemassan-sideaine {:sideaine (koodisto-muunnin "v/sm" 1)} ; todo ?
                                                                         :paallystemassan-lisa-aine {:materiaali (koodisto-muunnin "v/at" 14)} ; todo ?
                                                                         }}
                               :sitomattomat-pintarakenteet nil ; todo ?
                               :paallysrakenteen-lujitteet nil ; todo ?
                               :paksuus 1                   ; todo ?
                               :leveys (:leveys p)
                               :syvyys 1                    ; todo ? alusta?
                               :pinta-ala 1                 ; todo ? alusta?
                               :massamaara 1                ; todo ? alusta?
                               :lisatieto  (:lisatieto p)
                               :urakan-ulkoinen-tunniste "Esim. Sampon ID" ; todo ?
                               :yllapitokohteen-ulkoinen-tunniste "666" ; todo ?
                               :yllapitokohdeosan-ulkoinen-tunniste "666/1" ; todo ?
                               }
                :lahdejarjestelman-id "what here?"          ; todo ?
                :lahdejarjestelma "lahdejarjestelma/lj06"
                :alkaen (:aloituspvm p)                     ; todo ?
                :paatyen (:valmispvm-kohde p)}]             ; todo ?
    sanoma))

(defn date-writer [key value]
  (if (or (= java.sql.Date (type value))
          (= java.sql.Timestamp (type value)))
    (pvm/aika-iso8601-aikavyohykkeen-kanssa value)
    value))

(defn muodosta
  "Ennen kun tiedämme enemmän, muodostetaan kaikki päällystyskerrokset ja alustat erikseen"
  [urakka kohte koodisto-muunnin]
  (let [sanoma {:paallystekerros (as-> (:paallystekerrokset kohte) a
                                       (map #(paallystekerroksesta-velho-muottoon % koodisto-muunnin) a)
                                       (map #(json/write-str % :value-fn date-writer) a)
                                       (vec a))
                :alusta nil}]
    (println "petar sanoma je " (pr-str sanoma))
    sanoma))


