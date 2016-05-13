(ns harja.palvelin.integraatiot.yha.sanomat.urakan-kohdehakuvastaussanoma
  (:require [hiccup.core :refer [html]]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]))

(def +xsd-polku+ "xsd/yha/")

(defn lue-urakat [data]
  (mapv (fn [urakka]
          (hash-map :yhaid (z/xml1-> urakka :yha-id z/text xml/parsi-kokonaisluku)
                    :elyt (vec (mapcat #(z/xml-> % :ely z/text) (z/xml-> urakka :elyt)))
                    :vuodet (vec (mapcat #(z/xml-> % :vuosi z/text xml/parsi-kokonaisluku) (z/xml-> urakka :vuodet)))
                    :yhatunnus (z/xml1-> urakka :tunnus z/text)
                    :sampotunnus (z/xml1-> urakka :sampotunnus z/text)))
        (z/xml-> data :urakat :urakka)))

(defn lue-virhe [data]
  (z/xml1-> data :virhe z/text))

(defn lue-tierekisteriosoitevali [tierekisteriosoitevali]
  (hash-map :karttapaivamaara (z/xml1-> tierekisteriosoitevali :karttapaivamaara z/text xml/parsi-paivamaara)
            :tienumero (z/xml1-> tierekisteriosoitevali :tienumero z/text xml/parsi-kokonaisluku)
            :ajorata (z/xml1-> tierekisteriosoitevali :ajorata z/text xml/parsi-kokonaisluku)
            :kaista (z/xml1-> tierekisteriosoitevali :kaista z/text xml/parsi-kokonaisluku)
            :aosa (z/xml1-> tierekisteriosoitevali :aosa z/text xml/parsi-kokonaisluku)
            :aet (z/xml1-> tierekisteriosoitevali :aet z/text xml/parsi-kokonaisluku)
            :losa (z/xml1-> tierekisteriosoitevali :losa z/text xml/parsi-kokonaisluku)
            :let (z/xml1-> tierekisteriosoitevali :let z/text xml/parsi-kokonaisluku)))

(defn lue-paallystystoimenpide [paallystystoimenpide]
  (hash-map :uusi-paallyste (z/xml1-> paallystystoimenpide :uusi-paallyste z/text xml/parsi-kokonaisluku)
            :raekoko (z/xml1-> paallystystoimenpide :raekoko z/text xml/parsi-kokonaisluku)
            :kokonaismassamaara (z/xml1-> paallystystoimenpide :kokonaismassamaara z/text xml/parsi-kokonaisluku)
            :rc-prosentti (z/xml1-> paallystystoimenpide :rc-prosentti z/text xml/parsi-kokonaisluku)
            :kuulamylly (z/xml1-> paallystystoimenpide :kuulamylly z/text xml/parsi-kokonaisluku)
            :paallystetyomenetelma (z/xml1-> paallystystoimenpide :paallystetyomenetelma z/text xml/parsi-kokonaisluku)))

(defn lue-alikohteet [alikohteet]
  (mapv (fn [alikohde]
          (hash-map
            :yha-id (z/xml1-> alikohde :yha-id z/text xml/parsi-kokonaisluku)
            :tierekisteriosoitevali (lue-tierekisteriosoitevali (z/xml1-> alikohde :tierekisteriosoitevali))
            :tunnus (z/xml1-> alikohde :tunnus z/text)
            :paallystystoimenpide (z/xml1-> alikohde :paallystystoimenpide lue-paallystystoimenpide)))
        (z/xml-> alikohteet :alikohde)))

(defn lue-kohteet [data]
  (mapv (fn [kohde]
          (hash-map :yha-id (z/xml1-> kohde :yha-id z/text xml/parsi-kokonaisluku)
                    :kohdetyyppi (z/xml1-> kohde :kohdetyyppi z/text keyword)
                    :tunnus (z/xml1-> kohde :tunnus z/text)
                    :yllapitoluokka (z/xml1-> kohde :yllapitoluokka z/text xml/parsi-kokonaisluku)
                    :keskimaarainen-vuorokausiliikenne (z/xml1-> kohde :keskimaarainen-vuorokausiliikenne z/text xml/parsi-kokonaisluku)
                    :nykyinen-paallyste (z/xml1-> kohde :nykyinen-paallyste z/text xml/parsi-kokonaisluku)
                    :tierekisteriosoitevali (lue-tierekisteriosoitevali (z/xml1-> kohde :tierekisteriosoitevali))
                    :alikohteet (lue-alikohteet (z/xml1-> kohde :alikohteet))))
        (z/xml-> data :kohteet :kohde)))

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