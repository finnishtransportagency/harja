(ns harja.palvelin.integraatiot.api.sanomat.paikkaustoteuma-sanoma
  (:require [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]))

(defn nil-turvallinen-bigdec [arvo]
  (when (not (nil? arvo))
    (bigdec arvo)))

(defn materiaalit [materiaalit]
  (map (fn [{{:keys [esiintyma
                     km-arvo
                     muotoarvo
                     sideainetyyppi
                     pitoisuus
                     lisa-aineet]} :kivi-ja-sideaine}]
         (hash-map ::paikkaus/esiintyma esiintyma
                   ::paikkaus/kuulamylly-arvo km-arvo
                   ::paikkaus/muotoarvo muotoarvo,
                   ::paikkaus/lisa-aineet lisa-aineet,
                   ::paikkaus/pitoisuus (nil-turvallinen-bigdec pitoisuus)
                   ::paikkaus/sideainetyyppi sideainetyyppi))
       materiaalit))

(defn tienkohdat [ajoradat]
  (map (fn [{:keys [ajorata tienkohdat]}]
         (hash-map ::paikkaus/ajorata ajorata
                   ::paikkaus/ajourat (mapv :ajoura (:ajourat tienkohdat))
                   ::paikkaus/ajouravalit (mapv :ajouravali (:ajouravalit tienkohdat))
                   ::paikkaus/reunat (mapv :reuna (:reunat tienkohdat))
                   ::paikkaus/keskisaumat (mapv :keskisauma (:keskisauamt tienkohdat))))
       ajoradat))

(defn api->domain [urakka-id {:keys [paikkauskohde
                                     sijainti
                                     alkuaika
                                     loppuaika
                                     massatyyppi
                                     kuulamylly
                                     massamenekki
                                     raekoko
                                     leveys
                                     tyomenetelma
                                     kivi-ja-sideaineet] :as paikkaus}]
  {::paikkaus/ulkoinen-id (get-in paikkaus [:tunniste :id])
   ::paikkaus/urakka-id urakka-id
   ::paikkaus/alkuaika (json-tyokalut/aika-string->java-sql-date alkuaika)
   ::paikkaus/loppuaika (json-tyokalut/aika-string->java-sql-date loppuaika)
   ::paikkaus/tyomenetelma tyomenetelma
   ::paikkaus/massatyyppi massatyyppi
   ::paikkaus/kuulamylly kuulamylly
   ::paikkaus/massamenekki massamenekki
   ::paikkaus/raekoko raekoko
   ::paikkaus/leveys (nil-turvallinen-bigdec leveys)
   ::paikkaus/paikkauskohde {::paikkaus/nimi (:nimi paikkauskohde)
                             ::paikkaus/ulkoinen-id (get-in paikkauskohde [:tunniste :id])}
   ::paikkaus/tierekisteriosoite {::tierekisteri/tie (:tie sijainti)
                                  ::tierekisteri/aet (:aet sijainti)
                                  ::tierekisteri/let (:let sijainti)
                                  ::tierekisteri/aosa (:aosa sijainti)
                                  ::tierekisteri/losa (:losa sijainti)}
   ::paikkaus/materiaalit (materiaalit kivi-ja-sideaineet)
   ::paikkaus/tienkohdat (tienkohdat (:ajoradat sijainti))})