(ns harja.views.urakka.paallystys-indeksit
  "Näkymä päällystysurakan indeksien valintaan ja tietojen näyttämiseen."
  (:require [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.hallinta.indeksit :as indeksit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log]]))

(def raaka-aine-nimi {"bitumi" "Bitumi"
                      "kevyt_polttooljy" "Kevyt polttoöljy"
                      "nestekaasu" "Nestekaasu"})

(defn- indeksinimi-ja-lahtotaso-fmt
  [rivi]
  (str (:indeksinimi rivi)
       (when (:arvo rivi)
         (str " (lähtötaso: "
              (:arvo rivi) " €/t)"))))

(defn- indeksinimen-kentta
  [nimi otsikko valinnat]
  {:otsikko otsikko :nimi nimi :tyyppi :valinta :leveys 12
   :valinta-nayta #(if (:id %) (:indeksinimi %) "- valitse -")
   :fmt indeksinimi-ja-lahtotaso-fmt
   :valinnat valinnat})

(defn paallystysurakan-indeksit
  "Käyttöliittymä päällystysurakassa käytettävien indeksien valintaan."
  [ur]
  (komp/luo
    (fn [ur]
      (let [indeksivalinnat (indeksit/urakkatyypin-indeksit :paallystys)]
        [grid/grid
         {:otsikko "Urakan käyttämät indeksit"
          :tyhja "Ei indeksejä."
          :tallenna (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset (:id ur))
                      #(indeksit/tallenna-paallystysurakan-indeksit {:urakka-id (:id ur) :tiedot %}))}
         [{:otsikko "Indeksi" :nimi :indeksi :leveys 5
           :fmt indeksinimi-ja-lahtotaso-fmt
           :tyyppi :valinta
           :valinnat indeksivalinnat
           :valinta-nayta :indeksinimi}
          {:otsikko "Raaka-aine" :nimi :raakaaine :leveys 3
           :hae #(get-in % [:indeksi :raakaaine])
           :fmt raaka-aine-nimi
           :tyyppi :valinta :muokattava? (constantly false)}

          {:otsikko "Lähtö\u00ADtason vuosi" :nimi :lahtotason-vuosi :tyyppi :positiivinen-numero
           :leveys 1
           :validoi [[:rajattu-numero-tai-tyhja nil 2000 2030 "Anna vuosiluku"]]}
          {:otsikko "Lähtö\u00ADtason kk" :nimi :lahtotason-kuukausi :tyyppi :positiivinen-numero
           :leveys 1
           :validoi [[:rajattu-numero-tai-tyhja nil 1 12 "Anna kuukausi"]]}]
         @urakka/paallystysurakan-indeksitiedot]))))
