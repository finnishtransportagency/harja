(ns harja.palvelin.integraatiot.yha.sanomat.urakan-kohdehakuvastaussanoma
  (:require [hiccup.core :refer [html]]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]))

(def +xsd-polku+ "xsd/yha/")

(defn lue-urakat [data]
  (mapv (fn [urakka]
          (hash-map :yhaid (z/xml1-> urakka :yha:yha-id z/text xml/parsi-kokonaisluku)
                    :elyt (vec (mapcat #(z/xml-> % :yha:ely z/text) (z/xml-> urakka :yha:elyt)))
                    :vuodet (vec (mapcat #(z/xml-> % :yha:vuosi z/text xml/parsi-kokonaisluku) (z/xml-> urakka :yha:vuodet)))
                    :yhatunnus (z/xml1-> urakka :yha:tunnus z/text)
                    :sampotunnus (z/xml1-> urakka :yha:sampotunnus z/text)))
        (z/xml-> data :yha:urakat :yha:urakka)))

(defn lue-virhe [data]
  (z/xml1-> data :yha:virhe z/text))

(defn lue-tierekisteriosoitevali [tierekisteriosoitevali]
  (hash-map :karttapaivamaara (z/xml1-> tierekisteriosoitevali :yha:karttapaivamaara z/text xml/parsi-paivamaara)
            :tienumero (z/xml1-> tierekisteriosoitevali :yha:tienumero z/text xml/parsi-kokonaisluku)
            :ajorata (z/xml1-> tierekisteriosoitevali :yha:ajorata z/text xml/parsi-kokonaisluku)
            :kaista (z/xml1-> tierekisteriosoitevali :yha:kaista z/text xml/parsi-kokonaisluku)
            :aosa (z/xml1-> tierekisteriosoitevali :yha:aosa z/text xml/parsi-kokonaisluku)
            :aet (z/xml1-> tierekisteriosoitevali :yha:aet z/text xml/parsi-kokonaisluku)
            :losa (z/xml1-> tierekisteriosoitevali :yha:losa z/text xml/parsi-kokonaisluku)
            :let (z/xml1-> tierekisteriosoitevali :yha:let z/text xml/parsi-kokonaisluku)))

(defn lue-paallystystoimenpide [paallystystoimenpide]
  (hash-map :uusi-paallyste (z/xml1-> paallystystoimenpide :yha:uusi-paallyste z/text xml/parsi-kokonaisluku)
            :raekoko (z/xml1-> paallystystoimenpide :yha:raekoko z/text xml/parsi-kokonaisluku)
            :kokonaismassamaara (z/xml1-> paallystystoimenpide :yha:kokonaismassamaara z/text xml/parsi-kokonaisluku)
            :rc-prosentti (z/xml1-> paallystystoimenpide :yha:rc-prosentti z/text xml/parsi-kokonaisluku)
            :kuulamylly (z/xml1-> paallystystoimenpide :yha:kuulamylly z/text xml/parsi-kokonaisluku)
            :paallystetyomenetelma (z/xml1-> paallystystoimenpide :yha:paallystetyomenetelma z/text xml/parsi-kokonaisluku)))

(defn lue-alikohteet [alikohteet]
  (mapv (fn [alikohde]
          (hash-map
            :yha-id (z/xml1-> alikohde :yha:yha-id z/text xml/parsi-kokonaisluku)
            :tierekisteriosoitevali (lue-tierekisteriosoitevali (z/xml1-> alikohde :yha:tierekisteriosoitevali))
            :tunnus (z/xml1-> alikohde :yha:tunnus z/text)
            :paallystystoimenpide (z/xml1-> alikohde :yha:paallystystoimenpide lue-paallystystoimenpide)))
        (z/xml-> alikohteet :yha:alikohde)))

(defn lue-kohteet [data]
  (mapv (fn [kohde]
          (hash-map :yhaid (z/xml1-> kohde :yha:yha-id z/text xml/parsi-kokonaisluku)
                    :kohdetyyppi (z/xml1-> kohde :yha:kohdetyyppi z/text keyword)
                    :tunnus (z/xml1-> kohde :yha:tunnus z/text)
                    :yllapitoluokka (z/xml1-> kohde :yha:yllapitoluokka z/text xml/parsi-kokonaisluku)
                    :keskimaarainen-vuorokausiilikenne (z/xml1-> kohde :yha:keskimaarainen-vuorokausiilikenne z/text xml/parsi-kokonaisluku)
                    :nykyinen-paallyste (z/xml1-> kohde :yha:nykyinen-paallyste z/text xml/parsi-kokonaisluku)
                    :tierekisteriosoitevali (lue-tierekisteriosoitevali (z/xml1-> kohde :yha:tierekisteriosoitevali))
                    :alikohteet (z/xml1-> kohde :yha:alikohteet lue-alikohteet)))
        (z/xml-> data :yha:kohteet :yha:kohde)))

(defn lue-sanoma [viesti]
  (when (not (xml/validoi +xsd-polku+ "yha.xsd" viesti))
    (throw (new RuntimeException "XML-sanoma ei ole XSD-skeeman yha.xsd mukaan validi.")))
  (let [data (xml/lue viesti)
        kohteet (lue-kohteet data)
        virhe (lue-virhe data)
        vastaus {:kohteet kohteet}]
    (if virhe
      (assoc vastaus :virhe virhe)
      vastaus)))