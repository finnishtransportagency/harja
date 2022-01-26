(ns harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma
  (:require [clojure.string :as s]
            [harja.pvm :as pvm]))

(defn paallystemassa-rakenne [{:keys [runkoaine-koodit km-arvo muotoarvo max-raekoko
                              sideainetyyppi pitoisuus lisaaine-koodi]} koodisto-muunnin]
  (let [runkoaine-materiaali (when (not-empty runkoaine-koodit)
                                       (as-> runkoaine-koodit runkoaine-koodit
                                             (s/split runkoaine-koodit #"\s*,\s*")
                                             (map #(koodisto-muunnin "v/m" %) runkoaine-koodit)
                                             (vec runkoaine-koodit)))
        paallystemassa (merge
                         {:paallystemassan-runkoaine {:materiaali runkoaine-materiaali
                                                      :kuulamyllyarvo km-arvo
                                                      :litteysluku muotoarvo
                                                      :maksimi-raekoko (koodisto-muunnin "v/mrk" max-raekoko)}
                          :paallystemassan-sideaine (merge {:sideaine (koodisto-muunnin "v/sm" sideainetyyppi)}
                                                           (when (some? pitoisuus)
                                                             {:sideainepitoisuus (Math/round (float pitoisuus))}))
                          :paallystemassan-lisa-aine {:materiaali (koodisto-muunnin "v/lm" lisaaine-koodi)}})]
    paallystemassa))

(defn paallystekerros->velho-muoto
  "Konvertoi annettu paallystekerros JSON-lle, velho skeeman mukaan"
  [paallystekerros urakka koodisto-muunnin]
  (let [p paallystekerros
        paallystemassa (paallystemassa-rakenne p koodisto-muunnin)
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
                               :massamaara (Math/round (float (:massamenekki p))),
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
        ab-toimenpiteet #{2 21 22}
        verkko? (= verkko-toimenpide (:toimenpide a))
        ab? (contains? ab-toimenpiteet (:toimenpide a))
        paallysrakenteen-lujitteet-verkko (when verkko?
                                            {:materiaali (koodisto-muunnin "v/mt" (:verkon-tyyppi a))
                                             :toiminnallinen-kayttotarkoitus (koodisto-muunnin "v/vtk" (:verkon-tarkoitus a))
                                             :verkon-sijainti (koodisto-muunnin "v/vs" (:verkon-sijainti a))})
        sidottu-paallysrakenne (when ab?
                                 {:tyyppi ["sidotun-paallysrakenteen-tyyppi/spt02"] ; "alusta" aina
                                  :paallysteen-tyyppi (koodisto-muunnin "v/pt-ab" (:toimenpide a))
                                  :paallystemassa (paallystemassa-rakenne a koodisto-muunnin)})
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
                                 :lisatieto nil,
                                 :toimenpide (koodisto-muunnin "v/at" (:toimenpide a)),
                                 :paksuus (:lisatty-paksuus a),
                                 :toimenpiteen-kohdeluokka [(koodisto-muunnin "v/toimenpiteen-kohdeluokka" (:toimenpide a))],
                                 :paikkaustoimenpide nil}
                                (when (:murske-tyyppi a)
                                  {:kantava-kerros {:materiaali (koodisto-muunnin "v/kkm" (:murske-tyyppi a)),
                                                    :rakeisuus (koodisto-muunnin "v/skkr" (:rakeisuus a)),
                                                    :iskunkestavyys (koodisto-muunnin "v/skki" (:iskunkestavyys a))}})
                                (when verkko? {:paallysrakenteen-lujite {:verkko paallysrakenteen-lujitteet-verkko}})
                                (when ab? {:sidottu-paallysrakenne sidottu-paallysrakenne})),
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


