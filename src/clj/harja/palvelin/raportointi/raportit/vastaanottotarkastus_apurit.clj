(ns harja.palvelin.raportointi.raportit.vastaanottotarkastus-apurit
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.palvelin.raportointi.raportit.yllapidon-aikataulu :as yllapidon-aikataulu]
            [harja.tyokalut.big :as big]
            [jeesql.core :refer [defqueries]]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as ypk-yleiset]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.tyokalut.big :as big]))


(defn korostettu-yhteensa-rivi
  "Luo yhteensä-rivin, jossa arvo on korostettu."
  [arvo]
  [:arvo-ja-yksikko-korostettu {:arvo arvo
                                :fmt :raha
                                :korosta-hennosti? true}])

(defn pkluokka-rivi
  "Luo PK-luokka-rivin taulukolle."
  [rivi korosta? lihavoi?]
  (let [formatoi-arvo (fn [a]
                        [:arvo {:arvo a
                                :jos-tyhja ""
                                :korosta-hennosti? korosta?
                                :desimaalien-maara 2
                                :ryhmitelty? true}])]
    {:lihavoi? lihavoi?
     :rivi
     (into []
       (concat
         [[:arvo {:arvo (:nimi rivi) :korosta-hennosti? korosta?}]]
         [(formatoi-arvo (or (:pk1 rivi) (when (= "PK1" (:pkluokka rivi)) (:kokonaishinta rivi))))]
         [(formatoi-arvo (or (:pk2 rivi) (when (= "PK2" (:pkluokka rivi)) (:kokonaishinta rivi))))]
         [(formatoi-arvo (or (:pk3 rivi) (when (= "PK3" (:pkluokka rivi)) (:kokonaishinta rivi))))]
         [(formatoi-arvo (or (:eitiedossa rivi) (when (or (= "" (:pkluokka rivi))
                                                        (nil? (:pkluokka rivi))
                                                        (= "Ei tiedossa" (:pkluokka rivi)))
                                                  (:kokonaishinta rivi))))]
         [(formatoi-arvo (:muut-kustannukset rivi))]))}))

(defn formatoi-pkluokkarivit-taulukolle
  "Muodostaa PK-luokkarivit taulukolle sopivampaan muotoon."
  [pkluokkien-kustannukset muut-kustannukset urakan-sanktiot
                                          yllapitokohteet+kustannukset vuosi]
  (let [hallintayksikon-korjausluokkasummat (map
                                              #(assoc % :kokonaishinta (yllapitokohteet-domain/yllapitokohteen-kokonaishinta % vuosi))
                                              pkluokkien-kustannukset)
        ;; Yhdistetään saman urakan pkluokkarivit samalle riville
        ;; Ota uniikki urakka_id, jotta niiden perusteella voidaan koota uusi lista
        urakkalistaus (into #{} (map :urakka_id yllapitokohteet+kustannukset))
        urakkalistaus (mapv (fn [urakka_id]
                              (let [;; Muodostetaan urakkariville pohja, johon lasketaan pkluokat ja muut kustannukset yhteen
                                    uusi-urakkarivi {:hallintayksikko_id 0 :nimi "" :hallintayksikko_nimi "" :elynumero "" :urakka_id urakka_id :pk1 0 :pk2 0 :pk3 0 :eitiedossa 0 :muut-kustannukset 0}]
                                ;; Käy läpi kaikki hallintayksiköittäin jaotellut pkluokkasummat ja muodosta yhtä urakkaa kohti aina yksi uusi-urakkarivi
                                (reduce (fn [yht-rivi urakkarivi]
                                          (let [yht-rivi (if (= (:urakka_id urakkarivi) (:urakka_id yht-rivi))
                                                           (assoc yht-rivi
                                                             :hallintayksikko_id (:hallintayksikko_id urakkarivi)
                                                             :nimi (:nimi urakkarivi)
                                                             :hallintayksikko_nimi (:hallintayksikko_nimi urakkarivi)
                                                             :elynumero (:elynumero urakkarivi)
                                                             :pk1 (+ (:pk1 yht-rivi) (if (= "PK1" (:pkluokka urakkarivi))
                                                                                       (:kokonaishinta urakkarivi)
                                                                                       0))
                                                             :pk2 (+ (:pk2 yht-rivi) (if (= "PK2" (:pkluokka urakkarivi))
                                                                                       (:kokonaishinta urakkarivi)
                                                                                       0))
                                                             :pk3 (+ (:pk3 yht-rivi) (if (= "PK3" (:pkluokka urakkarivi))
                                                                                       (:kokonaishinta urakkarivi)
                                                                                       0))
                                                             :eitiedossa (+ (:eitiedossa yht-rivi) (if (or (nil? (:pkluokka urakkarivi)) (= "Ei tiedossa" (:pkluokka urakkarivi)))
                                                                                                     (:kokonaishinta urakkarivi)
                                                                                                     0)))
                                                           yht-rivi)]
                                            yht-rivi))
                                  uusi-urakkarivi
                                  hallintayksikon-korjausluokkasummat)))
                        urakkalistaus)
        ;; Poista urakkarivit, joissa elynumero on tyhjä
        urakkalistaus (remove #(and (= "" (:elynumero %)) (= "" (:hallintayksikko_nimi %))) urakkalistaus)
        ;; Lisätään kohdistamattomat kustannukset pk-luokkien kustannuksiin eli muut kustannukset ja sanktiot
        hallintayksikon-korjausluokkasummat (map
                                              (fn [rivi]
                                                (let [muut-kustannukset (apply + (keep (fn [kustannus]
                                                                                         (when (= (or (:urakka_id kustannus) (get-in kustannus [:urakka :id])) (:urakka_id rivi))
                                                                                           (or (:hinta kustannus) (:maara kustannus))))
                                                                                   (concat [] muut-kustannukset urakan-sanktiot)))]
                                                  (assoc rivi :muut-kustannukset muut-kustannukset)))
                                              urakkalistaus)]
    hallintayksikon-korjausluokkasummat))

(defn formatoi-yotyorivit-taulukolle
  "Muodostaa yötyörivit taulukolle sopivampaan muotoon."
  [rivit yllapitokohteet+kustannukset]
  (let [;; Mäppää yllapitokohtaiset rivit urakkakohtaisiksi
        urakkalistaus (into #{} (map :urakka_id yllapitokohteet+kustannukset))
        urakkalistaus (mapv (fn [urakka_id]
                              (let [;; Muodostetaan urakkariville pohja, johon lasketaan pkluokat ja muut kustannukset yhteen
                                    uusi-urakkarivi {:nimi ""
                                                     :hallintayksikko_id ""
                                                     :hallintayksikko_nimi ""
                                                     :elynumero ""
                                                     :urakka_id urakka_id
                                                     :pk1-pituus-yotyo 0 :pk1-pituus 0
                                                     :pk2-pituus-yotyo 0 :pk2-pituus 0
                                                     :pk3-pituus-yotyo 0 :pk3-pituus 0
                                                     :eitiedossa-pituus-yotyo 0 :eitiedossa-pituus 0}]
                                ;; Käy läpi kaikki yötyörivit ja muodosta yhtä urakkaa kohti aina yksi uusi-urakkarivi
                                (reduce (fn [yht-rivi urakkarivi]
                                          (let [yht-rivi (if (= (:urakka_id urakkarivi) (:urakka_id yht-rivi))
                                                           (let [pk1-pituus-yotyo (if (and (true? (:yotyo urakkarivi))
                                                                                        (= "PK1" (:pkluokka urakkarivi)))
                                                                                    (:pituus urakkarivi) 0)
                                                                 pk1-pituus (if (and (false? (:yotyo urakkarivi))
                                                                                  (= "PK1" (:pkluokka urakkarivi)))
                                                                              (:pituus urakkarivi) 0)
                                                                 pk2-pituus-yotyo (if (and (true? (:yotyo urakkarivi))
                                                                                        (= "PK2" (:pkluokka urakkarivi)))
                                                                                    (:pituus urakkarivi) 0)
                                                                 pk2-pituus (if (and (false? (:yotyo urakkarivi))
                                                                                  (= "PK2" (:pkluokka urakkarivi)))
                                                                              (:pituus urakkarivi) 0)
                                                                 pk3-pituus-yotyo (if (and (true? (:yotyo urakkarivi))
                                                                                        (= "PK3" (:pkluokka urakkarivi)))
                                                                                    (:pituus urakkarivi) 0)
                                                                 pk3-pituus (if (and (false? (:yotyo urakkarivi))
                                                                                  (= "PK3" (:pkluokka urakkarivi)))
                                                                              (:pituus urakkarivi) 0)
                                                                 ei-tiedossa-pituus-yotyo (if (and (= "true" (:yotyo urakkarivi))
                                                                                                (or (= "" (:pkluokka urakkarivi))
                                                                                                  (nil? (:pkluokka urakkarivi))
                                                                                                  (= "Ei tiedossa" (:pkluokka urakkarivi))))
                                                                                            (:pituus urakkarivi)
                                                                                            0)
                                                                 ei-tiedossa-pituus (if (and (false? (:yotyo urakkarivi))
                                                                                          (or (= "" (:pkluokka urakkarivi))
                                                                                            (nil? (:pkluokka urakkarivi))
                                                                                            (= "Ei tiedossa" (:pkluokka urakkarivi))))
                                                                                      (:pituus urakkarivi)
                                                                                      0)]

                                                             (assoc yht-rivi
                                                               :hallintayksikko_id (:hallintayksikko_id urakkarivi)
                                                               :nimi (:nimi urakkarivi)
                                                               :hallintayksikko_nimi (:hallintayksikko_nimi urakkarivi)
                                                               :elynumero (:elynumero urakkarivi)
                                                               :pk1-pituus-yotyo (+ (:pk1-pituus-yotyo yht-rivi) pk1-pituus-yotyo)
                                                               :pk1-pituus (+ (:pk1-pituus yht-rivi) pk1-pituus)
                                                               :pk2-pituus-yotyo (+ (:pk2-pituus-yotyo yht-rivi) pk2-pituus-yotyo)
                                                               :pk2-pituus (+ (:pk2-pituus yht-rivi) pk2-pituus)
                                                               :pk3-pituus-yotyo (+ (:pk3-pituus-yotyo yht-rivi) pk3-pituus-yotyo)
                                                               :pk3-pituus (+ (:pk3-pituus yht-rivi) pk3-pituus)
                                                               :eitiedossa-pituus-yotyo (+ (:eitiedossa-pituus-yotyo yht-rivi) ei-tiedossa-pituus-yotyo)
                                                               :eitiedossa-pituus (+ (:eitiedossa-pituus yht-rivi) ei-tiedossa-pituus)))
                                                           yht-rivi)]
                                            yht-rivi))
                                  uusi-urakkarivi
                                  rivit)))
                        urakkalistaus)

        ;; Poista urakkarivit, joissa elynumero on tyhjä
        urakkalistaus (remove #(and (= "" (:elynumero %)) (= "" (:hallintayksikko_nimi %))) urakkalistaus)]
    urakkalistaus))
