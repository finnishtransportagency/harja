(ns harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma
  (:require [clojure.string :as s]))

(defn paallystekerros->velho-muoto
  "Konvertoi annettu paallystekerros JSON-lle, velho skeeman mukaan"
  [paallystekerros urakka koodisto-muunnin]
  (let [p paallystekerros
        runkoaine-materiaali (as-> (:runkoaine-koodit p) runkoaine-koodit
                                   (s/split runkoaine-koodit #"\s*,\s*")
                                   (map #(koodisto-muunnin "v/jkm" (Integer/parseInt %)) runkoaine-koodit)
                                   (vec runkoaine-koodit))
        paallystemassa (merge
                         {:asfalttirouheen-osuus-asfalttimassassa (:rc% p)}
                         {:bitumiprosentti (:pitoisuus p)
                          :paallystemassan-runkoaine {:materiaali runkoaine-materiaali ; todo ? mikko tarkistaa
                                                      :uusiomateriaalin-kayttomaara nil ; todo ? mikko tarkistaa
                                                      :kuulamyllyarvo (:km-arvo p)
                                                      :kuulamyllyarvon-luokka (:kuulamyllyluokka p)
                                                      :litteysluku (:muotoarvo p)
                                                      :maksimi-raekoko (koodisto-muunnin "v/mrk" (:max-raekoko p))}
                          :paallystemassan-sideaine {:sideaine (koodisto-muunnin "v/sm" (:sideainetyyppi p))}
                          :paallystemassan-lisa-aine {:materiaali (koodisto-muunnin "v/at" (:lisaaine-koodi p))}})
        sidottu-paallysrakenne {:tyyppi "sidotun-paallysrakenteen-tyyppi/spt01" ; "kulutuskerros" aina
                                :paallysteen-tyyppi (koodisto-muunnin "v/pt" (:paallystetyyppi p))
                                :paallystemassa paallystemassa}
        sanoma {:alkusijainti {:osa (:tr-alkuosa p)
                               :tie (:tr-numero p)
                               :etaisyys (:tr-alkuetaisyys p)
                               :ajorata (:tr-ajorata p)}
                :loppusijainti {:osa (:tr-loppuosa p)
                                :tie (:tr-numero p)
                                :etaisyys (:tr-loppuetaisyys p)
                                :ajorata (:tr-ajorata p)}
                :sijaintitarkenne {:kaista (:tr-kaista p)}
                :ominaisuudet {:toimenpide (koodisto-muunnin "v/trtp" (:pot2-tyomenetelma p))
                               :sidottu-paallysrakenne sidottu-paallysrakenne
                               :sitomattomat-pintarakenteet nil
                               :paallysrakenteen-lujitteet nil
                               :paksuus nil
                               :leveys (:leveys p)
                               :syvyys nil
                               :pinta-ala (:pinta-ala p)
                               :massamaara (:massamenekki p) ; todo ? mikon kanssa selviämme (katri jne)
                               :lisatieto  (:lisatieto p)
                               :urakan-ulkoinen-tunniste (:sampoid urakka)
                               :korjauskohteen-ulkoinen-tunniste (:kohde-id p)
                               :korjauskohdeosan-ulkoinen-tunniste (:kohdeosa-id p)}
                :lahdejarjestelman-id (:pot-id p)
                :lahdejarjestelma "lahdejarjestelma/lj06"
                :alkaen (:alkaen p)
                :paattyen nil}]
    sanoma))

(defn alusta->velho-muoto
  "Konvertoi annettu alusta JSON-lle, velho skeeman mukaan"
  [alusta urakka koodisto-muunnin]
  (let [verkko-toimenpide 3
        a alusta
        paallysrakenteen-lujitteet (when (= verkko-toimenpide (:toimenpide a))
                                     {:materiaali (koodisto-muunnin "v/mt" (:verkon-tyyppi a))
                                      :toiminnallinen-kayttotarkoitus (koodisto-muunnin "v/vtk" (:verkon-tarkoitus a))
                                      :verkon-sijainti (koodisto-muunnin "v/vs" (:verkon-sijainti a))})
        sanoma {:alkusijainti {:osa (:tr-alkuosa a)
                               :tie (:tr-numero a)
                               :etaisyys (:tr-alkuetaisyys a)
                               :ajorata (:tr-ajorata a)}
                :loppusijainti {:osa (:tr-loppuosa a)
                                :tie (:tr-numero a)
                                :etaisyys (:tr-loppuetaisyys a)
                                :ajorata (:tr-ajorata a)}
                :sijaintirakenne {:kaista (:tr-kaista a)}
                :ominaisuudet {:toimenpide (koodisto-muunnin "v/at" (:toimenpide a))
                               :sidottu-paallysrakenne nil
                               :sitomattomat-pintarakenteet nil
                               :paallysrakenteen-lujitteet paallysrakenteen-lujitteet
                               :paksuus (:lisatty-paksuus a)
                               :leveys (:leveys a)
                               :syvyys (:syvyys a)
                               :pinta-ala (:pinta-ala a)
                               :massamaara (:massamaara a)
                               :lisatieto  nil
                               :urakan-ulkoinen-tunniste (:sampoid urakka)
                               :korjauskohteen-ulkoinen-tunniste (:paallystyskohde a)
                               :korjauskohdeosan-ulkoinen-tunniste (:pot2a_id a)}
                :lahdejarjestelman-id (:pot-id a)
                :lahdejarjestelma "lahdejarjestelma/lj06"
                :alkaen (:alkaen a)
                :paattyen nil}]
    sanoma))

(defn muodosta
  "Ennen kun tiedämme enemmän, muodostetaan kaikki päällystyskerrokset ja alustat erikseen"
  [urakka kohde koodisto-muunnin]
  (let [sanoma {:paallystekerros (->> (:paallystekerrokset kohde)
                                      (map #(paallystekerros->velho-muoto % urakka koodisto-muunnin))
                                      vec)
                :alusta (->> (:alustat kohde)
                             (map #(alusta->velho-muoto % urakka koodisto-muunnin))
                             vec)}]
    sanoma))


