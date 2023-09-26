(ns harja.palvelin.raportointi.raportit.vastaanottotarkastus
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.palvelin.raportointi.raportit.yllapidon-aikataulu :as yllapidon-aikataulu]
            [jeesql.core :refer [defqueries]]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as ypk-yleiset]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]))

(defqueries "harja/palvelin/raportointi/raportit/vastaanottotarkastus.sql")

(defn yllapitokohteet-taulukko [yllapitokohteet taulukkotyyppi vuosi urakka-id]
  (let [nimi (case taulukkotyyppi
               :yha "YHA-kohteet"
               :paikkaus "Muut kohteet")
        ;; Ryhmitä kohteet jos isompi konteksti valittuna 
        kohteet (if (some? urakka-id)
                  yllapitokohteet
                  (group-by #(:hallintayksikko_id %) yllapitokohteet))

        fn-formatoi-kohdetiedot (fn [yllapitokohde]
                                  (case taulukkotyyppi
                                    :yha
                                    (concat
                                      [(:urakka yllapitokohde)
                                       (:kohdenumero yllapitokohde)
                                       (:tunnus yllapitokohde)
                                       (:nimi yllapitokohde)
                                       [:boolean {:arvo (:yotyo yllapitokohde)}]
                                       (:tr-numero yllapitokohde)
                                       (:tr-alkuosa yllapitokohde)
                                       (:tr-alkuetaisyys yllapitokohde)
                                       (:tr-loppuosa yllapitokohde)
                                       (:tr-loppuetaisyys yllapitokohde)
                                       (:pituus yllapitokohde)
                                       (:kvl yllapitokohde)
                                       (:yplk yllapitokohde)
                                       (:sopimuksen-mukaiset-tyot yllapitokohde)
                                       (:maaramuutokset yllapitokohde)]
                                      (when-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
                                        [(:arvonvahennykset yllapitokohde)
                                         (:sakot-ja-bonukset yllapitokohde)])
                                      [(:bitumi-indeksi yllapitokohde)
                                       (:kaasuindeksi yllapitokohde)
                                       (:maku-paallysteet yllapitokohde)
                                       (:kokonaishinta yllapitokohde)])

                                    :paikkaus
                                    (concat
                                      [(:urakka yllapitokohde)
                                       (:kohdenumero yllapitokohde)
                                       (:tunnus yllapitokohde)
                                       (:nimi yllapitokohde)
                                       [:boolean {:arvo (:yotyo yllapitokohde)}]
                                       (:tr-numero yllapitokohde)
                                       (:tr-alkuosa yllapitokohde)
                                       (:tr-alkuetaisyys yllapitokohde)
                                       (:tr-loppuosa yllapitokohde)
                                       (:tr-loppuetaisyys yllapitokohde)
                                       (:pituus yllapitokohde)
                                       (:kvl yllapitokohde)
                                       (:yplk yllapitokohde)
                                       (:toteutunut-hinta yllapitokohde)]
                                      (when-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
                                        [(:arvonvahennykset yllapitokohde)
                                         (:sakot-ja-bonukset yllapitokohde)])
                                      [(:bitumi-indeksi yllapitokohde)
                                       (:kaasuindeksi yllapitokohde)
                                       (:maku-paallysteet yllapitokohde)
                                       (:kokonaishinta yllapitokohde)])))]

    [:taulukko {:otsikko nimi
                :tyhja (when (empty? kohteet) "Ei kohteita.")
                :sheet-nimi nimi
                :rivi-ennen [{:sarakkeita (if (= taulukkotyyppi :yha)
                                            14
                                            13)}
                             {:sarakkeita 1}
                             {:teksti "Hintamuutokset"
                              :sarakkeita 3
                              :luokka "paallystys-tausta-tumma"
                              :tummenna-teksti? true
                              :tasaa :keskita}
                             {:sarakkeita 1}]
                :lisaa-excel-valiotsikot true}
     (into []
       (concat
         [{:otsikko "Urakka" :leveys 10}
          {:otsikko "Kohde\u00ADnumero" :leveys 5}
          {:otsikko "Tunnus" :leveys 5}
          {:otsikko "Nimi" :leveys 10}
          {:otsikko "Yö\u00ADtyö" :leveys 3 :fmt :boolean}
          {:otsikko "Tie\u00ADnumero" :leveys 3 :tasaa :oikea}
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
         (when-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
           [{:otsikko "Arvon muu\u00ADtok\u00ADset" :leveys 5 :fmt :raha}
            {:otsikko "Sakko\u00AD/bonus" :leveys 5 :fmt :raha}])
         [{:otsikko "Bitu\u00ADmi-indek\u00ADsi" :leveys 5 :fmt :raha :otsikkorivi-luokka "paallystys-tausta-tumma"}
          {:otsikko "Neste\u00ADkaasu ja kevyt poltto\u00ADöljy" :leveys 10 :fmt :raha :otsikkorivi-luokka "paallystys-tausta-tumma"}
          {:otsikko "MAKU-pääl\u00ADlys\u00ADteet" :leveys 5 :fmt :raha :otsikkorivi-luokka "paallystys-tausta-tumma"}
          {:otsikko "Kokonais\u00ADhinta" :leveys 5 :fmt :raha}]))

     (if (some? urakka-id)
       ;; Jos urakka valittuna, ei tehdä hallintayksikköryhmittelyä
       (map
         fn-formatoi-kohdetiedot
         yllapitokohteet)
       ;; Isompi konteksti valittuna 
       (mapcat
         (fn [kohde]
           (concat [;; Muodosta hallintayksikön otsikko
                    {:otsikko (str
                                (format "%02d"
                                  (-> kohde second  first :hallintayksikko_id)) " "
                                (-> kohde second  first :hallintayksikko_nimi)) :leveys 10}]
             (map
               fn-formatoi-kohdetiedot
               (second kohde))))
         kohteet))]))

(defn- korostettu-yhteensa-rivi [arvo]
  [:arvo-ja-yksikko-korostettu {:arvo arvo
                                :fmt :raha
                                :korosta-hennosti? true}])

(defn yhteensa-taulukko [yllapitokohteet muut-kustannukset urakan-sanktiot vuosi]
  (let [kohdistamattomat-sanktiot-yhteensa (reduce + 0 (keep :maara urakan-sanktiot))
        muut-kustannukset-yhteensa (reduce + 0 (keep :hinta muut-kustannukset))]
    [:taulukko {:otsikko "Yhteenveto"
                :tyhja (when (empty? yllapitokohteet) "Ei kohteita.")
                :sheet-nimi "Ylläpitokohteet yhteensä"}
     (concat
       [{:otsikko "" :leveys 5}
        {:otsikko "" :leveys 5}
        {:otsikko "" :leveys 3}
        {:otsikko "" :leveys 3}
        {:otsikko "" :leveys 3}
        {:otsikko "" :leveys 3}
        {:otsikko "" :leveys 3}
        {:otsikko "Toteu\u00ADtunut hinta (muut kohteet)" :nimi :toteutunut-hinta
         :fmt :raha :leveys 5}
        {:otsikko (str "Sakot ja bonukset"
                    (when-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
                      " (muut kuin kohteisiin liittyvät)"))
         :leveys 5 :fmt :raha}
        {:otsikko "Muut kustannukset" :leveys 5 :fmt :raha}]
       (when-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
         [{:otsikko "Arvonväh." :leveys 5 :fmt :raha}
          {:otsikko "Sakko/bonus" :leveys 5 :fmt :raha}])
       [{:otsikko "Tarjous\u00ADhinta" :leveys 5 :fmt :raha}
        {:otsikko "Määrä\u00ADmuutok\u00ADset" :leveys 5 :fmt :raha}
        {:otsikko "Bitu\u00ADmi-indek\u00ADsi" :leveys 5 :fmt :raha}
        {:otsikko "Neste\u00ADkaasu ja kevyt poltto\u00ADöljy" :leveys 5 :fmt :raha}
        {:otsikko "MAKU-pääl\u00ADlys\u00ADteet" :leveys 5 :fmt :raha}
        {:otsikko "Kokonais\u00ADhinta" :leveys 5 :fmt :raha}])
     [(concat
        [(korostettu-yhteensa-rivi nil)
         (korostettu-yhteensa-rivi nil)
         (korostettu-yhteensa-rivi nil)
         (korostettu-yhteensa-rivi nil)
         (korostettu-yhteensa-rivi nil)
         (korostettu-yhteensa-rivi nil)
         (korostettu-yhteensa-rivi nil)
         (korostettu-yhteensa-rivi (reduce + 0 (keep :toteutunut-hinta yllapitokohteet)))
         (korostettu-yhteensa-rivi kohdistamattomat-sanktiot-yhteensa)
         (korostettu-yhteensa-rivi muut-kustannukset-yhteensa)]
        (when-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
          [(korostettu-yhteensa-rivi (reduce + 0 (keep :arvonvahennykset yllapitokohteet)))
           (korostettu-yhteensa-rivi (reduce + 0 (keep :sakot-ja-bonukset yllapitokohteet)))])
        [(korostettu-yhteensa-rivi (reduce + 0 (keep :sopimuksen-mukaiset-tyot yllapitokohteet)))
         (korostettu-yhteensa-rivi (reduce + 0 (keep :maaramuutokset yllapitokohteet)))
         (korostettu-yhteensa-rivi (reduce + 0 (keep :bitumi-indeksi yllapitokohteet)))
         (korostettu-yhteensa-rivi (reduce + 0 (keep :kaasuindeksi yllapitokohteet)))
         (korostettu-yhteensa-rivi (reduce + 0 (keep :maku-paallysteet yllapitokohteet)))
         (korostettu-yhteensa-rivi 
           (+ (reduce + 0 (keep :kokonaishinta yllapitokohteet))
           kohdistamattomat-sanktiot-yhteensa
           muut-kustannukset-yhteensa))])]]))

(defn muut-kustannukset-taulukko [muut-kustannukset urakan-sanktiot urakka-id]
  (let [nimi "Muut kustannukset"
        ;; Ryhmitä sanktiot isommassa kontekstissa
        sanktiot (if (some? urakka-id)
                   urakan-sanktiot
                   (group-by #(get-in % [:hallintayksikko :id]) urakan-sanktiot))

        fn-formatoi-kustannus (fn [kustannus]
                                (let [kohdetiedot (when-not (:selite kustannus)
                                                    (yllapitokohteet-domain/fmt-kohteen-nimi-ja-yhaid-opt kustannus))]
                                  (-> [(:pvm kustannus)
                                       (or (:selite kustannus)
                                         (case (:sakkoryhma kustannus)
                                           "yllapidon_sakko" (str "Sakko" kohdetiedot)
                                           "yllapidon_bonus" (str "Bonus" kohdetiedot)))
                                       (or (:hinta kustannus)
                                         (:maara kustannus))])))]
    [:taulukko {:otsikko nimi
                :tyhja (when (empty? muut-kustannukset) "Ei muita kustannuksia.")
                :sheet-nimi nimi
                :lisaa-excel-valiotsikot true}
     [{:otsikko "Pvm" :leveys 10 :fmt :pvm}
      {:otsikko "Selitys" :leveys 10}
      {:otsikko "Summa" :leveys 10 :fmt :raha}]

     (if (some? urakka-id)
       (map
         fn-formatoi-kustannus
         (apply conj muut-kustannukset urakan-sanktiot))

       (mapcat (fn [kustannus]
                 (concat [;; Muodosta hallintayksikön otsikko  
                          {:otsikko (str
                                      (format "%02d"
                                        (-> kustannus second  first (get-in [:hallintayksikko :id]))) " "
                                      (-> kustannus second  first (get-in [:hallintayksikko :nimi]))) :leveys 10}]
                   (map
                     fn-formatoi-kustannus
                     (apply conj muut-kustannukset (second kustannus)))))
         sanktiot))]))

(defn suorita [db user {:keys [urakka-id vuosi hallintayksikko-id] :as tiedot}]
  (let [raportin-nimi (if urakka-id
                        "Vastaanottotarkastus"
                        "Päällystysurakoiden yhteenveto")
        konteksti (cond
                    urakka-id :urakka
                    hallintayksikko-id :hallintayksikko
                    :else :koko-maa)
        raportin-nimi (case konteksti
                        :urakka (str
                                  (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                                  ", " raportin-nimi " " vuosi)
                        :hallintayksikko (str
                                           raportin-nimi ", "
                                           (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                                           " " vuosi)
                        :koko-maa (str raportin-nimi ", KOKO MAA " vuosi))

        yllapitokohteet+kustannukset (->> (into []
                                                (hae-yllapitokohteet db {:urakka urakka-id
                                                                         :vuosi vuosi
                                                                         :hallintayksikko hallintayksikko-id}))
                                       
                                       (map #(assoc % :kokonaishinta (yllapitokohteet-domain/yllapitokohteen-kokonaishinta % vuosi)))
                                       
                                       (yllapitokohteet-domain/jarjesta-yllapitokohteet))

        muut-kustannukset (hae-muut-kustannukset db {:urakka urakka-id
                                                     :vuosi vuosi
                                                     :hallintayksikko hallintayksikko-id})
        urakan-sanktiot (->>
                          (map konv/alaviiva->rakenne
                            (hae-yllapitourakan-sanktiot db {:urakka urakka-id
                                                             :vuosi vuosi
                                                             :hallintayksikko hallintayksikko-id}))
                          (map #(assoc % :maara (- (:maara %)))))]

    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     ;; Yha kohteet
     (yllapitokohteet-taulukko (filter :yhaid yllapitokohteet+kustannukset) :yha vuosi urakka-id)
     ;; Muut kohteet 
     (yllapitokohteet-taulukko (filter (comp not :yhaid) yllapitokohteet+kustannukset) :paikkaus vuosi urakka-id)
     ;; Muut kustannukset
     (muut-kustannukset-taulukko muut-kustannukset urakan-sanktiot urakka-id)
     ;; Näytetään aikataulu vain urakan kontekstissa 
     (when urakka-id
       (mapcat (fn [[_ otsikko raportti-fn]]
                 (concat [[:otsikko otsikko]]
                   (yleinen/osat (raportti-fn db user tiedot))))
         [[:yllapidon-aikataulu "Aikataulu" yllapidon-aikataulu/suorita]]))
     ;; Yhteenveto 
     (yhteensa-taulukko yllapitokohteet+kustannukset muut-kustannukset urakan-sanktiot vuosi)]))
