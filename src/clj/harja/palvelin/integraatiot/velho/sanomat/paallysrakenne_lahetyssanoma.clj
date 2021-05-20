(ns harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma
  (:require [clojure.data.json :as json]
            [clojure.string :as s]
            [harja.pvm :as pvm]
            [harja.domain.pot2 :as pot2]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]))

(defn paallystekerroksesta-velho-muottoon
  "Konvertoi annettu paallystekerros JSON-lle, velho skeeman mukaan"
  [paallystekerros koodisto-muunnin]
  (println "petar dobio " (pr-str paallystekerros))
  (let [p paallystekerros
        runkoaine-materiaali (as-> (:runkoaine-koodit p) runkoaine-koodit
                                   (s/split runkoaine-koodit #"\s*,\s*")
                                   (map #(koodisto-muunnin "v/jkm" (Integer/parseInt %)) runkoaine-koodit)
                                   (vec runkoaine-koodit))
        paallystemassa (merge
                         (when (some? (:rc% p))
                           {:asfalttirouheen-osuus-asfalttimassassa (:rc% p)})
                         {:bitumiprosentti 6.6              ; todo ?
                          :paallystemassan-runkoaine {:materiaali runkoaine-materiaali ; todo ?
                                                      :uusiomateriaalin-kayttomaara 6 ; todo ?
                                                      :kuulamyllyarvo (:kuulamyllyarvo p)
                                                      :kuulamyllyarvon-luokka "KM-arvoluokka" ; todo ?
                                                      :litteysluku (:litteysluku p)
                                                      :maksimi-raekoko (koodisto-muunnin "v/mrk" (:max-raekoko p))}
                          :paallystemassan-sideaine {:sideaine (koodisto-muunnin "v/sm" (:sideainetyyppi p))}
                          :paallystemassan-lisa-aine {:materiaali (koodisto-muunnin "v/at" (:lisaaine-koodi p))}})
        sidottu-paallysrakenne {:tyyppi "sidotun-paallysrakenteen-tyyppi/spt01" ; "kulutuskerros" aina
                                :paallysteen-tyyppi (koodisto-muunnin "v/pt" (:massatyyppi p))
                                :paallystemassa paallystemassa}
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
                               :sidottu-paallysrakenne sidottu-paallysrakenne
                               :sitomattomat-pintarakenteet nil
                               :paallysrakenteen-lujitteet nil
                               :paksuus 1                   ; todo ?
                               :leveys (:leveys p)
                               :syvyys 1                    ; todo ? alusta?
                               :pinta-ala (:pinta-ala p)
                               :massamaara (:kokonaismassamaara p)
                               :lisatieto  (:lisatieto p)
                               :urakan-ulkoinen-tunniste "Esim. Sampon ID" ; todo ?
                               :yllapitokohteen-ulkoinen-tunniste "666" ; todo ?
                               :yllapitokohdeosan-ulkoinen-tunniste "666/1"} ; todo ?
                :lahdejarjestelman-id "what here?"          ; todo ?
                :lahdejarjestelma "lahdejarjestelma/lj06"
                :alkaen (:aloituspvm p)                     ; todo ?
                :paatyen (:valmispvm-kohde p)}]             ; todo ?
    (println "petar napravio " (pr-str sanoma))
    sanoma))

(defn alustasta-velho-muottoon
  "Konvertoi annettu alusta JSON-lle, velho skeeman mukaan"
  [alusta koodisto-muunnin]
  (println "petar dobio alusta " (pr-str alusta))
  (let [verkko-toimenpide 3
        a alusta
        sanoma {:alkusijainti {:osa (:tr-alkuosa a)
                               :tie (:tr-numero a)
                               :etaisyys (:tr-alkuetaisyys a)
                               :ajorata (:tr-ajorata a)}
                :loppusijainti {:osa (:tr-loppuosa a)
                                :tie (:tr-numero a)
                                :etaisyys (:tr-loppuetaisyys a)
                                :ajorata (:tr-ajorata a)}
                :sijaintirakenne {:kaista (:tr-kaista a)}
                :ominaisuudet {:toimenpide (koodisto-muunnin "v/trtp" (:toimenpide a))
                               :sidottu-paallysrakenne nil
                               :sitomattomat-pintarakenteet nil
                               :paallysrakenteen-lujitteet {}
                               :paksuus (:lisatty-paksuus a)                   ; todo ?
                               :leveys (:leveys a)
                               :syvyys (:syvyys a)                    ; todo ? alusta?
                               :pinta-ala (:pinta-ala a)                 ; todo ? alusta?
                               :massamaara (:massamaara a)                ; todo ? alusta?
                               :lisatieto  nil
                               :urakan-ulkoinen-tunniste "Esim. Sampon ID" ; todo ?
                               :yllapitokohteen-ulkoinen-tunniste "666" ; todo ?
                               :yllapitokohdeosan-ulkoinen-tunniste "666/1" ; todo ?
                               }
                :lahdejarjestelman-id "what here?"          ; todo ?
                :lahdejarjestelma "lahdejarjestelma/lj06"
                :alkaen (:aloituspvm a)                     ; todo ?
                :paatyen (:valmispvm-kohde a)}]             ; todo ?
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
                :alusta (as-> (:alustat kohte) a
                              (map #(alustasta-velho-muottoon % koodisto-muunnin) a)
                              (map #(json/write-str % :value-fn date-writer) a)
                              (vec a))}]
    (println "petar sanoma je " (pr-str sanoma))
    sanoma))


