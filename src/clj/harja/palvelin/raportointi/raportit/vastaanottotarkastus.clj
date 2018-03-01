(ns harja.palvelin.raportointi.raportit.vastaanottotarkastus
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.palvelin.raportointi.raportit.yllapidon-aikataulu :as yllapidon-aikataulu]
            [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as ypk-yleiset]
            [clj-time.core :as t]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [harja.math :as math]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]))

(defqueries "harja/palvelin/raportointi/raportit/vastaanottotarkastus.sql")

(defn yllapitokohteet-taulukko [yllapitokohteet raportin-nimi taulukkotyyppi]
  [:taulukko {:otsikko (case taulukkotyyppi
                         :yha "YHA-kohteet"
                         :paikkaus "Harjan paikkauskohteet ja muut kohteet")
              :tyhja (when (empty? yllapitokohteet) "Ei raportoitavaa.")
              :sheet-nimi raportin-nimi}
   (concat
     [{:otsikko "Kohde\u00ADnumero" :leveys 5}
      {:otsikko "Tunnus" :leveys 5}
      {:otsikko "Nimi" :leveys 10}
      {:otsikko "Tie\u00ADnumero" :leveys 5}
      {:otsikko "Ajorata" :leveys 5}
      {:otsikko "Kaista" :leveys 5}
      {:otsikko "Aosa" :leveys 5}
      {:otsikko "Aet" :leveys 5}
      {:otsikko "Losa" :leveys 5}
      {:otsikko "Let" :leveys 5}
      {:otsikko "Pit. (m)" :leveys 5}
      {:otsikko "KVL" :leveys 5}
      {:otsikko "YP-lk" :leveys 5}]
     (when (= taulukkotyyppi :yha)
       [{:otsikko "Tarjous\u00ADhinta" :leveys 5 :fmt :raha}
        {:otsikko "Määrä\u00ADmuu\u00ADtokset" :leveys 5 :fmt :raha}])
     (when (= taulukkotyyppi :paikkaus)
       [{:otsikko "Toteutunut hinta" :leveys 5 :fmt :raha}])
     [{:otsikko "Arvon muu\u00ADtok\u00ADset" :leveys 5 :fmt :raha}
      {:otsikko "Sakko/bonus" :leveys 5 :fmt :raha}
      {:otsikko "Bitumi-indeksi" :leveys 5 :fmt :raha}
      {:otsikko "Kaasu\u00ADindeksi" :leveys 5 :fmt :raha}
      {:otsikko "Koko\u00ADnais\u00ADhinta (indek\u00ADsit mukana)" :leveys 5 :fmt :raha}])
   (map
     (fn [yllapitokohde]
       (case taulukkotyyppi
         :yha
         [(:kohdenumero yllapitokohde)
          (:tunnus yllapitokohde)
          (:nimi yllapitokohde)
          (:tr-numero yllapitokohde)
          (:tr-ajorata yllapitokohde)
          (:tr-kaista yllapitokohde)
          (:tr-alkuosa yllapitokohde)
          (:tr-alkuetaisyys yllapitokohde)
          (:tr-loppuosa yllapitokohde)
          (:tr-loppuetaisyys yllapitokohde)
          (:pituus yllapitokohde)
          (:kvl yllapitokohde)
          (:yplk yllapitokohde)
          (:sopimuksen-mukaiset-tyot yllapitokohde)
          (:maaramuutokset yllapitokohde)
          (:arvonmuutokset yllapitokohde)
          (:sakot-ja-bonukset yllapitokohde)
          (:bitumi-indeksi yllapitokohde)
          (:kaasuindeksi yllapitokohde)
          (:kokonaishinta yllapitokohde)]
         :paikkaus
         [(:kohdenumero yllapitokohde)
          (:tunnus yllapitokohde)
          (:nimi yllapitokohde)
          (:tr-numero yllapitokohde)
          (:tr-ajorata yllapitokohde)
          (:tr-kaista yllapitokohde)
          (:tr-alkuosa yllapitokohde)
          (:tr-alkuetaisyys yllapitokohde)
          (:tr-loppuosa yllapitokohde)
          (:tr-loppuetaisyys yllapitokohde)
          (:pituus yllapitokohde)
          (:kvl yllapitokohde)
          (:yplk yllapitokohde)
          (:toteutunut-hinta yllapitokohde)
          (:arvonmuutokset yllapitokohde)
          (:sakot-ja-bonukset yllapitokohde)
          (:bitumi-indeksi yllapitokohde)
          (:kaasuindeksi yllapitokohde)
          (:kokonaishinta yllapitokohde)]))
     yllapitokohteet)])

(defn suorita [db user {:keys [urakka-id] :as tiedot}]
  (let [konteksti :urakka
        raportin-nimi "Vastaanottotarkastus"
        otsikko (str (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                     ", " raportin-nimi ", suoritettu " (fmt/pvm (pvm/nyt)))
        yllapitokohteet (->> (into []
                                   (map #(konv/string->keyword % [:yllapitokohdetyotyyppi]))
                                   (hae-yllapitokohteet db {:urakka urakka-id}))
                             (ypk-yleiset/liita-yllapitokohteisiin-maaramuutokset db)
                             (map #(ypk-yleiset/lisaa-yllapitokohteelle-pituus db %))
                             (map #(assoc % :kokonaishinta (yllapitokohteet-domain/yllapitokohteen-kokonaishinta %)))
                             (yllapitokohteet-domain/jarjesta-yllapitokohteet))]
    [:raportti {:nimi raportin-nimi}

     (yllapitokohteet-taulukko (filter :yhaid yllapitokohteet) raportin-nimi :yha)
     (yllapitokohteet-taulukko (filter (comp not :yhaid) yllapitokohteet) raportin-nimi :paikkaus)

     ;; TODO Yhteenvetorivi
     ;; TODO Info, jos sisältää ennustettuja määrämuutoksia?

     (mapcat (fn [[aja-parametri otsikko raportti-fn]]
               (concat [[:otsikko otsikko]]
                       (yleinen/osat (raportti-fn db user tiedot))))
             [[:yllapidon-aikataulu "Aikataulu" yllapidon-aikataulu/suorita]])]))