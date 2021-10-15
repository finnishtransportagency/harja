(ns harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma
  (:require [clojure.string :as s]
            [harja.pvm :as pvm]))

(defn paallystekerros->velho-muoto
  "Konvertoi annettu paallystekerros JSON-lle, velho skeeman mukaan"
  [paallystekerros urakka koodisto-muunnin]
  (let [p paallystekerros
        runkoaine-materiaali (as-> (:runkoaine-koodit p) runkoaine-koodit
                                   (s/split runkoaine-koodit #"\s*,\s*")
                                   (map #(koodisto-muunnin "v/jkm" (Integer/parseInt %)) runkoaine-koodit)
                                   (vec runkoaine-koodit))
        runkoaine-materiaali "materiaali/m03"               ; petar täällä oli vector, mitä nyt?
        paallystemassa (merge
                         ; petar tämä ei tarvitse?      {:asfalttirouheen-osuus-asfalttimassassa (:rc% p)}
                         {                                  ; petar tämä ei tarvitse?   :bitumiprosentti (:pitoisuus p)
                          :paallystemassan-runkoaine {:materiaali runkoaine-materiaali ; todo ? mikko tarkistaa
                                                      ;petar ei enää? :uusiomateriaalin-kayttomaara nil ; todo ? mikko tarkistaa
                                                      :kuulamyllyarvo (:km-arvo p)
                                                      ;petar ei enää? :kuulamyllyarvon-luokka (:kuulamyllyluokka p)
                                                      :litteysluku (:muotoarvo p)
                                                      :maksimi-raekoko (koodisto-muunnin "v/mrk" (:max-raekoko p))}
                          :paallystemassan-sideaine {:sideaine (koodisto-muunnin "v/sm" (:sideainetyyppi p))
                                                     :sideainepitoisuus (:pitoisuus p)}
                          :paallystemassan-lisa-aine {:materiaali (koodisto-muunnin "v/at" (:lisaaine-koodi p))}}) ; petar pitäisi olla lisaaineen-materiaali/lm02
        sidottu-paallysrakenne {:tyyppi ["sidotun-paallysrakenteen-tyyppi/spt01"] ; "kulutuskerros" aina
                                :paallysteen-tyyppi (koodisto-muunnin "v/pt" (:paallystetyyppi p))
                                :paallystemassa paallystemassa}
        sanoma {:alkusijainti {:osa (:tr-alkuosa p)
                               :tie (:tr-numero p)
                               :etaisyys (:tr-alkuetaisyys p)},
                :loppusijainti {:osa (:tr-loppuosa p)
                                :tie (:tr-numero p)
                                :etaisyys (:tr-loppuetaisyys p)},
                :ominaisuudet {:sidottu-paallysrakenne sidottu-paallysrakenne,
                               :leveys (:leveys p),
                               :korjauskohdeosan-ulkoinen-tunniste (str (:kohdeosa-id p)),
                               :massamaara (:massamenekki p),
                               :vaikutukset nil,
                               :syvyys nil,
                               :urakan-ulkoinen-tunniste (:sampoid urakka),
                               :pinta-ala (:pinta-ala p),
                               :materiaali nil,
                               :korjauskohteen-ulkoinen-tunniste (str (:kohde-id p)),
                               :kiviaineksen-maksimi-raekoko nil,
                               :lisatieto (:lisatieto p),
                               :toimenpide (koodisto-muunnin "v/trtp" (:pot2-tyomenetelma p)),
                               :paksuus nil,
                               :toimenpiteen-kohdeluokka ["paallyste-ja-pintarakenne/sidotut-paallysrakenteet"],
                               :paikkaustoimenpide nil},
                :lahdejarjestelman-id (str (:pot-id p)),
                :paattyen nil,
                :lahdejarjestelma "lahdejarjestelma/lj06",
                :schemaversio 1,
                :sijaintitarkenne {:ajoradat [(str "ajorata/ajr" (:tr-ajorata p))],
                                   :kaistat [(str "kaista-numerointi/kanu" (:tr-kaista p))]},
                :alkaen (pvm/iso8601 (:alkaen p))}]
    sanoma))

(defn alusta->velho-muoto
  "Konvertoi annettu alusta JSON-lle, velho skeeman mukaan"
  [alusta urakka koodisto-muunnin]
  (let [a alusta
        verkko-toimenpide 3
        verkko? (= verkko-toimenpide (:toimenpide a))
        paallysrakenteen-lujitteet-verkko (when verkko?
                                            {:materiaali (koodisto-muunnin "v/mt" (:verkon-tyyppi a))
                                             :toiminnallinen-kayttotarkoitus (koodisto-muunnin "v/vtk" (:verkon-tarkoitus a))
                                             :verkon-sijainti (koodisto-muunnin "v/vs" (:verkon-sijainti a))})
        sanoma {:alkusijainti {:osa (:tr-alkuosa a)
                               :tie (:tr-numero a)
                               :etaisyys (:tr-alkuetaisyys a)},
                :loppusijainti {:osa (:tr-loppuosa a)
                                :tie (:tr-numero a)
                                :etaisyys (:tr-loppuetaisyys a)},
                :sijaintitarkenne {:ajoradat [(str "ajorata/ajr" (:tr-ajorata a))],
                                   :kaistat [(str "kaista-numerointi/kanu" (:tr-kaista a))]},
                :ominaisuudet (merge
                                {:leveys (:leveys a),
                                 :korjauskohdeosan-ulkoinen-tunniste (str (:pot2a_id a)),
                                 :massamaara (:massamaara a),
                                 :vaikutukset nil,
                                 :syvyys (:syvyys a),
                                 :urakan-ulkoinen-tunniste (str (:sampoid urakka)),
                                 :pinta-ala (:pinta-ala a),
                                 :materiaali nil,
                                 :korjauskohteen-ulkoinen-tunniste (str (:paallystyskohde a)),
                                 :kiviaineksen-maksimi-raekoko nil,
                                 :kantava-kerros (when (:murske-tyyppi a)
                                                   {:materiaali (koodisto-muunnin "v/kkm" (:murske-tyyppi a)),
                                                    :rakeisuus (koodisto-muunnin "v/skkr" (:rakeisuus a)),
                                                    :iskunkestavyys (koodisto-muunnin "v/skki" (:iskunkestavyys a))}),
                                 :lisatieto nil,
                                 :toimenpide (koodisto-muunnin "v/at" (:toimenpide a)),
                                 :paksuus (:lisatty-paksuus a),
                                 :toimenpiteen-kohdeluokka ["paallysrakennekerrokset/kantavat-kerrokset"],
                                 :paikkaustoimenpide nil}
                                (when verkko? {:paallysrakenteen-lujite {:verkko paallysrakenteen-lujitteet-verkko}})),
                :lahdejarjestelman-id (str (:pot-id a)),
                :paattyen nil,
                :lahdejarjestelma "lahdejarjestelma/lj06",
                :schemaversio 1,
                :alkaen (pvm/iso8601 (:alkaen a))}]
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


