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

(defn yllapitokohteet-taulukko [yllapitokohteet taulukkotyyppi]
  (let [nimi (case taulukkotyyppi
               :yha "YHA-kohteet"
               :paikkaus "Harjan paikkauskohteet ja muut kohteet")]
    [:taulukko {:otsikko nimi
                :tyhja (when (empty? yllapitokohteet) "Ei kohteita.")
                :sheet-nimi nimi}
     (concat
       [{:otsikko "Kohde\u00ADnumero" :leveys 5}
        {:otsikko "Tunnus" :leveys 5}
        {:otsikko "Nimi" :leveys 10}
        {:otsikko "Tie\u00ADnumero" :leveys 3 :tasaa :oikea}
        {:otsikko "Ajorata" :leveys 3 :tasaa :oikea}
        {:otsikko "Kaista" :leveys 3 :tasaa :oikea}
        {:otsikko "Aosa" :leveys 3 :tasaa :oikea}
        {:otsikko "Aet" :leveys 3 :tasaa :oikea}
        {:otsikko "Losa" :leveys 3 :tasaa :oikea}
        {:otsikko "Let" :leveys 3 :tasaa :oikea}
        {:otsikko "Pit. (m)" :leveys 3 :tasaa :oikea}
        {:otsikko "KVL" :leveys 3 :tasaa :oikea}
        {:otsikko "YP-lk" :leveys 3}]
       (when (= taulukkotyyppi :yha)
         [{:otsikko "Tarjous\u00ADhinta" :leveys 5 :fmt :raha}
          {:otsikko "Määrä\u00ADmuu\u00ADtokset" :leveys 5 :fmt :raha}])
       (when (= taulukkotyyppi :paikkaus)
         [{:otsikko "Toteutunut hinta" :leveys 10 :fmt :raha}])
       [{:otsikko "Arvon muu\u00ADtok\u00ADset" :leveys 5 :fmt :raha}
        {:otsikko "Sakko\u00AD/bonus" :leveys 5 :fmt :raha}
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
            (:arvonvahennykset yllapitokohde)
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
            (:arvonvahennykset yllapitokohde)
            (:sakot-ja-bonukset yllapitokohde)
            (:bitumi-indeksi yllapitokohde)
            (:kaasuindeksi yllapitokohde)
            (:kokonaishinta yllapitokohde)]))
       yllapitokohteet)]))

(defn yhteensa-taulukko [yllapitokohteet muut-kustannukset urakan-sanktiot]
  (let [muut-kustannukset-yhteensa (reduce + 0 (concat (keep :hinta muut-kustannukset)
                                                       (keep :maara urakan-sanktiot)))]
    [:taulukko {:otsikko "Yhteenveto"
                :tyhja (when (empty? yllapitokohteet) "Ei kohteita.")
                :sheet-nimi "Ylläpitokohteet yhteensä"}
     [{:otsikko "" :leveys 5}
      {:otsikko "" :leveys 5}
      {:otsikko "" :leveys 10}
      {:otsikko "" :leveys 3}
      {:otsikko "" :leveys 3}
      {:otsikko "" :leveys 3}
      {:otsikko "" :leveys 3}
      {:otsikko "" :leveys 3}
      {:otsikko "" :leveys 3}
      {:otsikko "" :leveys 3}
      {:otsikko "" :leveys 3}
      {:otsikko "" :leveys 3}
      {:otsikko "" :leveys 3}
      {:otsikko "Muut kustannukset" :leveys 10 :fmt :raha}
      {:otsikko "Arvonväh." :leveys 5 :fmt :raha}
      {:otsikko "Sakko/bonus" :leveys 5 :fmt :raha}
      {:otsikko "Bitumi-indeksi" :leveys 5 :fmt :raha}
      {:otsikko "Kaasu\u00ADindeksi" :leveys 5 :fmt :raha}
      {:otsikko "Koko\u00ADnais\u00ADhinta (indek\u00ADsit mukana)" :leveys 5 :fmt :raha}]
     [[nil
       nil
       nil
       nil
       nil
       nil
       nil
       nil
       nil
       nil
       nil
       nil
       nil
       muut-kustannukset-yhteensa
       (reduce + 0 (keep :arvonvahennykset yllapitokohteet))
       (reduce + 0 (keep :sakot-ja-bonukset yllapitokohteet))
       (reduce + 0 (keep :bitumi-indeksi yllapitokohteet))
       (reduce + 0 (keep :kaasuindeksi yllapitokohteet))
       (+ (reduce + 0 (keep :kokonaishinta yllapitokohteet))
          muut-kustannukset-yhteensa)]]]))

(defn muut-kustannukset-taulukko [muut-kustannukset urakan-sanktiot]
  (let [nimi "Muut kustannukset"]
    [:taulukko {:otsikko nimi
                :tyhja (when (empty? muut-kustannukset) "Ei muita kustannuksia.")
                :sheet-nimi nimi}
     [{:otsikko "Pvm" :leveys 10 :fmt :pvm}
      {:otsikko "Selitys" :leveys 10}
      {:otsikko "Summa" :leveys 10 :fmt :raha}]
     (map
       (fn [kustannus]
         (-> [(:pvm kustannus)
              (or (:selite kustannus)
                  (case (:sakkoryhma kustannus)
                    "yllapidon_sakko" "Sakko"
                    "yllapidon_bonus" "Bonus"))
              (or (:hinta kustannus)
                  (:maara kustannus))]))
       (apply conj muut-kustannukset urakan-sanktiot))]))

(defn suorita [db user {:keys [urakka-id vuosi] :as tiedot}]
  (let [raportin-nimi "Vastaanottotarkastus"
        otsikko (str (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                     ", " raportin-nimi ", suoritettu " (fmt/pvm (pvm/nyt)))
        yllapitokohteet+kustannukset (->> (into []
                                                (map #(konv/string->keyword % [:yllapitokohdetyotyyppi]))
                                                (hae-yllapitokohteet db {:urakka urakka-id
                                                                         :vuosi vuosi}))
                                          (ypk-yleiset/liita-yllapitokohteisiin-maaramuutokset db)
                                          (map #(ypk-yleiset/lisaa-yllapitokohteelle-pituus db %))
                                          (map #(assoc % :kokonaishinta (yllapitokohteet-domain/yllapitokohteen-kokonaishinta %)))
                                          (yllapitokohteet-domain/jarjesta-yllapitokohteet))
        muut-kustannukset (hae-muut-kustannukset db {:urakka urakka-id
                                                     :vuosi vuosi})
        urakan-sanktiot (->> (hae-kohteisiin-kuulumattomat-sanktiot db {:urakka urakka-id
                                                                    :vuosi vuosi})
                            (map #(assoc % :maara (- (:maara %)))))]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     (yllapitokohteet-taulukko (filter :yhaid yllapitokohteet+kustannukset) :yha)
     (when (some :maaramuutokset-ennustettu? yllapitokohteet+kustannukset) [:teksti "Taulukko sisältää ennustettuja määrämuutoksia."])
     (yllapitokohteet-taulukko (filter (comp not :yhaid) yllapitokohteet+kustannukset) :paikkaus)
     (muut-kustannukset-taulukko muut-kustannukset urakan-sanktiot)
     (yhteensa-taulukko yllapitokohteet+kustannukset muut-kustannukset urakan-sanktiot)

     (mapcat (fn [[aja-parametri otsikko raportti-fn]]
               (concat [[:otsikko otsikko]]
                       (yleinen/osat (raportti-fn db user tiedot))))
             [[:yllapidon-aikataulu "Aikataulu" yllapidon-aikataulu/suorita]])]))
