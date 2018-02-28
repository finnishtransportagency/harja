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
            [clj-time.core :as t]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [harja.math :as math]))

(defqueries "harja/palvelin/raportointi/raportit/vastaanottotarkastus.sql")

(defn suorita [db user {:keys [urakka-id] :as tiedot}]
  (let [konteksti :urakka
        raportin-nimi "Vastaanottotarkastus"
        otsikko (str (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                     ", " raportin-nimi ", suoritettu " (fmt/pvm (pvm/nyt)))
        yllapitokohteet (hae-yllapitokohteet db {:urakka urakka-id})]
    [:raportti {:nimi raportin-nimi}

     [:taulukko {:otsikko otsikko
                 :tyhja (when (empty? yllapitokohteet) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi}
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
       {:otsikko "Pit. (m)" :leveys 5} ; TODO LASKE!
       {:otsikko "KVL" :leveys 5}
       {:otsikko "YP-lk" :leveys 5}
       {:otsikko "Tarjous\u00ADhinta" :leveys 5}
       {:otsikko "Määrä\u00ADmuu\u00ADtokset" :leveys 5} ; TODO LASKE
       {:otsikko "Arvon muu\u00ADtok\u00ADset" :leveys 5}
       {:otsikko "Sakko/bonus" :leveys 5} ; TODO LASKE
       {:otsikko "Bitumi-indeksi" :leveys 5}
       {:otsikko "Kaasu\u00ADindeksi" :leveys 5}
       {:otsikko "Koko\u00ADnais\u00ADhinta (indek\u00ADsit mukana)" :leveys 5}] ; TODO LASKE
      (map
        (fn [yllapitokohde]
          [(:kohdenumero yllapitokohde)
           (:nimi yllapitokohde)
           (:tr-numero yllapitokohde)
           (:tr-ajorata yllapitokohde)
           (:tr-kaista yllapitokohde)
           (:tr-alkuosa yllapitokohde)
           (:tr-alkuetaisyys yllapitokohde)
           (:tr-loppuosa yllapitokohde)
           (:tr-loppuetaisyys yllapitokohde)
           (:pituus yllapitokohde) ; TODO LASKE
           (:kvl yllapitokohde)
           (:yplk yllapitokohde)
           (:tarjoushinta yllapitokohde)
           (:maaramuutokset yllapitokohde) ; TODO LASKE
           (:arvonmuutokset yllapitokohde)
           (:sakot-ja-bonukset yllapitokohde) ; TODO LASKE
           (:bitumi-indeksi yllapitokohde)
           (:kaasuindeksi yllapitokohde)
           (:ykokonaishinta yllapitokohde) ; TODO LASKE
           ])
        yllapitokohteet)]

     (mapcat (fn [[aja-parametri otsikko raportti-fn]]
               (concat [[:otsikko otsikko]]
                       (yleinen/osat (raportti-fn db user tiedot))))
             [[:yllapidon-aikataulu "Aikataulu" yllapidon-aikataulu/suorita]])]))