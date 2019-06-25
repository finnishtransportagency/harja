(ns harja.palvelin.integraatiot.yha.sanomat.urakan-kohdehakuvastaussanoma
  "Käytetän purkamaan YHA:sta vastaanotettu XML-sanoma, joka sisältää urakan kohdeluettelon. Kohdeluettelo koostuu
  joko päällystys- tai paikkauskohteista. Ennen varsinaista käsittelyä sanoma validoidaan XSD-skeemaa vasten.
  lue-sanoma funktio palauttaa vektorin mäppejä, joista jokainen kuvaa yhden kohteen. "

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
  (merge
    {:karttapaivamaara (z/xml1-> tierekisteriosoitevali :karttapaivamaara z/text xml/parsi-paivamaara)}
    (into {}
          (map (juxt identity #(z/xml1-> tierekisteriosoitevali % z/text xml/parsi-kokonaisluku)))
          [:aosa :aet :losa :let :tienumero])))

(defn lue-tierekisteriosoitevali-kaistalla-ja-ajoradalla [tierekisteriosoitevali]
  (merge (lue-tierekisteriosoitevali tierekisteriosoitevali)
         (into {}
               (map (juxt identity #(z/xml1-> tierekisteriosoitevali % z/text xml/parsi-kokonaisluku)))
               [:ajorata :kaista])))

(defn lue-paallystystoimenpide [paallystystoimenpide]
  (hash-map :uusi-paallyste (z/xml1-> paallystystoimenpide :uusi-paallyste z/text xml/parsi-kokonaisluku)
            :raekoko (z/xml1-> paallystystoimenpide :raekoko z/text xml/parsi-kokonaisluku)
            :kokonaismassamaara (z/xml1-> paallystystoimenpide :kokonaismassamaara z/text xml/parsi-desimaaliluku)
            :rc-prosentti (z/xml1-> paallystystoimenpide :rc-prosentti z/text xml/parsi-kokonaisluku)
            :kuulamylly (z/xml1-> paallystystoimenpide :kuulamylly z/text xml/parsi-kokonaisluku)
            :paallystetyomenetelma (z/xml1-> paallystystoimenpide :paallystetyomenetelma z/text xml/parsi-kokonaisluku)))

(defn lue-alikohteet [alikohteet]
  (mapv (fn [alikohde]
          (hash-map
            :yha-id (z/xml1-> alikohde :yha-id z/text xml/parsi-kokonaisluku)
            :tierekisteriosoitevali (lue-tierekisteriosoitevali-kaistalla-ja-ajoradalla (z/xml1-> alikohde :tierekisteriosoitevali))
            :tunnus (z/xml1-> alikohde :tunnus z/text)
            :paallystystoimenpide (z/xml1-> alikohde :paallystystoimenpide lue-paallystystoimenpide)
            :yllapitoluokka (z/xml1-> alikohde :yllapitoluokka z/text xml/parsi-kokonaisluku)
            :keskimaarainen-vuorokausiliikenne (z/xml1-> alikohde :keskimaarainen-vuorokausiliikenne z/text xml/parsi-kokonaisluku)
            :nykyinen-paallyste (z/xml1-> alikohde :nykyinen-paallyste z/text xml/parsi-kokonaisluku)))
        (z/xml-> alikohteet :alikohde)))

(defn kasittele-kohdetyyppi [tyyppi]
  (case tyyppi
    :1 "paallyste"
    :2 "kevytliikenne"
    :3 "sora"
    "paallyste"))

(defn lue-kohteet [data]
  (mapv (fn [kohde]
          (hash-map :yha-id (z/xml1-> kohde :yha-id z/text xml/parsi-kokonaisluku)
                    :yha-kohdenumero (z/xml1-> kohde :kohdenumero z/text xml/parsi-kokonaisluku)
                    :yllapitokohdetyyppi (kasittele-kohdetyyppi (z/xml1-> kohde :kohdetyyppi z/text keyword))
                    :yllapitokohdetyotyyppi (z/xml1-> kohde :kohdetyotyyppi z/text keyword)
                    :nimi (z/xml1-> kohde :nimi z/text)
                    :tunnus (z/xml1-> kohde :tunnus z/text)
                    :tierekisteriosoitevali (lue-tierekisteriosoitevali (z/xml1-> kohde :tierekisteriosoitevali))
                    :alikohteet (lue-alikohteet (z/xml1-> kohde :alikohteet))))
        (z/xml-> data :kohteet :kohde)))

(defn lue-sanoma [viesti]
  (when (not (xml/validi-xml? +xsd-polku+ "yha.xsd" viesti))
    (throw (new RuntimeException "XML-sanoma ei ole XSD-skeeman yha.xsd mukaan validi.")))
  (let [data (xml/lue viesti)
        kohteet (lue-kohteet data)
        virhe (lue-virhe data)
        vastaus {:kohteet kohteet}]
    (if virhe
      (assoc vastaus :virhe virhe)
      vastaus)))
