(ns harja.palvelin.raportointi.raportit.vastaanottotarkastus
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.palvelin.raportointi.raportit.vastaanottotarkastus-apurit :as apurit]
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

(defqueries "harja/palvelin/raportointi/raportit/vastaanottotarkastus.sql")

(defn yllapitokohteet-taulukko [yllapitokohteet taulukkotyyppi vuosi urakka-tai-hallintayksikko?]
  (let [nimi (case taulukkotyyppi
               :yha "YHA-kohteet"
               :paikkaus "Muut kohteet")
        ;; Ryhmitä kohteet jos isompi konteksti valittuna 
        kohteet (if urakka-tai-hallintayksikko?
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
                                       (:kokonaishinta yllapitokohde)])))
        ;; Sarakkeiden määrä
        sarakkeita (if (= taulukkotyyppi :yha) 14 13)
        ;; Kun näytetään arvonmuutos, lisätään sarakkeita jotta taulukon välitykset ovat oikeat
        sarakkeita (if-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi) (+ sarakkeita 2) sarakkeita)]

    [:taulukko {:otsikko nimi
                :tyhja (when (empty? kohteet) "Ei kohteita.")
                :sheet-nimi nimi
                :rivi-ennen [{:sarakkeita sarakkeita}
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

     (if urakka-tai-hallintayksikko?
       ;; Jos urakka tai yksikkö valittuna, ei tehdä hallintayksikköryhmittelyä
       (map fn-formatoi-kohdetiedot yllapitokohteet)
       ;; Isompi konteksti valittuna 
       (mapcat
         (fn [kohde]
           (concat [;; Muodosta hallintayksikön otsikko
                    {:otsikko (str
                                (format "%02d"
                                  (-> kohde second first :hallintayksikko_id)) " "
                                (-> kohde second first :hallintayksikko_nimi)) :leveys 10}]
             (map fn-formatoi-kohdetiedot (second kohde))))
         kohteet))]))

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
        {:otsikko "" :leveys 3}]
       ;; Piilotetaan sakot 2021 vuonna ja sitä aiemmin
       (if-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
         [{:otsikko "Toteu\u00ADtunut hinta (muut kohteet)" :nimi :toteutunut-hinta
           :fmt :raha :leveys 5}]
         [{:otsikko "Toteu\u00ADtunut hinta (muut kohteet)" :nimi :toteutunut-hinta
           :fmt :raha :leveys 5}
          {:otsikko (str "Sakot ja bonukset"
                      (when-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
                        " (muutkin kuin kohteisiin liittyvät)"))
           :leveys 5 :fmt :raha}])
       [{:otsikko "Muut kustannukset" :leveys 5 :fmt :raha}]
       ;; Näytetään 2022 vuonna ja sitä myöhemmin
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
        [(apurit/korostettu-yhteensa-rivi nil)
         (apurit/korostettu-yhteensa-rivi nil)
         (apurit/korostettu-yhteensa-rivi nil)
         (apurit/korostettu-yhteensa-rivi nil)
         (apurit/korostettu-yhteensa-rivi nil)
         (apurit/korostettu-yhteensa-rivi nil)
         (apurit/korostettu-yhteensa-rivi nil)]
        (if-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
          [(apurit/korostettu-yhteensa-rivi (reduce + 0 (keep :toteutunut-hinta yllapitokohteet)))]
          [(apurit/korostettu-yhteensa-rivi (reduce + 0 (keep :toteutunut-hinta yllapitokohteet)))
           (apurit/korostettu-yhteensa-rivi kohdistamattomat-sanktiot-yhteensa)])
        [(apurit/korostettu-yhteensa-rivi muut-kustannukset-yhteensa)]
        (when-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
          [(apurit/korostettu-yhteensa-rivi (reduce + 0 (keep :arvonvahennykset yllapitokohteet)))
           (apurit/korostettu-yhteensa-rivi (reduce + 0 (keep :sakot-ja-bonukset yllapitokohteet)))])
        [(apurit/korostettu-yhteensa-rivi (reduce + 0 (keep :sopimuksen-mukaiset-tyot yllapitokohteet)))
         (apurit/korostettu-yhteensa-rivi (reduce + 0 (keep :maaramuutokset yllapitokohteet)))
         (apurit/korostettu-yhteensa-rivi (reduce + 0 (keep :bitumi-indeksi yllapitokohteet)))
         (apurit/korostettu-yhteensa-rivi (reduce + 0 (keep :kaasuindeksi yllapitokohteet)))
         (apurit/korostettu-yhteensa-rivi (reduce + 0 (keep :maku-paallysteet yllapitokohteet)))
         (apurit/korostettu-yhteensa-rivi
           (+ (reduce + 0 (keep :kokonaishinta yllapitokohteet))
             (or (when (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi) kohdistamattomat-sanktiot-yhteensa) 0)
             muut-kustannukset-yhteensa))])]]))

(defn muut-kustannukset-rivi [rivi korosta? lihavoi?]
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
         [[:arvo {:arvo (:pvm rivi) :fmt :pvm}]]
         [[:arvo {:arvo (:urakka-nimi rivi)}]]
         [[:arvo {:arvo (:teksti rivi)}]]
         [(formatoi-arvo (or (:hinta rivi) (:maara rivi)))]))}))

(defn pkluokka-yotyo-rivi [rivi korosta? lihavoi?]
  (let [laske-prosentti (fn [yotyo paivatyo]
                          (let [yhteensa (bigdec (+ yotyo paivatyo))
                                yotyo (bigdec yotyo)
                                paivatyo (bigdec paivatyo)
                                yotyo (if (nil? yotyo) 0 yotyo)
                                paivatyo (if (nil? paivatyo) 0 paivatyo)]
                            (cond
                              (= (bigdec 0) yotyo) 0
                              (= (bigdec 0) paivatyo) 100
                              :else (* 100 (with-precision 4 (/ yotyo yhteensa))))))
        formatoitu-pituus (fn [a]
                            [:arvo {:arvo a
                                    :jos-tyhja ""
                                    :korosta-hennosti? korosta?
                                    :desimaalien-maara 2
                                    :ryhmitelty? true}])
        formatoitu-prosentti (fn [p]
                               [:arvo-ja-yksikko {:arvo p
                                                  :jos-tyhja ""
                                                  :yksikko "%"
                                                  :korosta-hennosti? korosta?
                                                  :desimaalien-maara 2
                                                  :tasaa :vasen
                                                  :ryhmitelty? true}])]
    {:lihavoi? lihavoi?
     :rivi
     (into []
       (concat
         [[:arvo {:arvo (:nimi rivi) :korosta-hennosti? korosta?}]]
         [(formatoitu-pituus (:pk1-pituus-yotyo rivi))]
         [(formatoitu-prosentti (laske-prosentti (:pk1-pituus-yotyo rivi) (:pk1-pituus rivi)))]
         [(formatoitu-pituus (:pk2-pituus-yotyo rivi))]
         [(formatoitu-prosentti (laske-prosentti (:pk2-pituus-yotyo rivi) (:pk2-pituus rivi)))]
         [(formatoitu-pituus (:pk3-pituus-yotyo rivi))]
         [(formatoitu-prosentti (laske-prosentti (:pk3-pituus-yotyo rivi) (:pk3-pituus rivi)))]
         [(formatoitu-pituus (:eitiedossa-pituus-yotyo rivi))]
         [(formatoitu-prosentti (laske-prosentti (:eitiedossa-pituus-yotyo rivi) (:eitiedossa-pituus rivi)))]))}))

(defn pkluokka-taulukko [rivit]
  (let [valtakunnallisesti-yhteensa (reduce (fn [yht-rivi rivi]
                                              (assoc yht-rivi
                                                :pk1 (+ (:pk1 yht-rivi) (:pk1 rivi))
                                                :pk2 (+ (:pk2 yht-rivi) (:pk2 rivi))
                                                :pk3 (+ (:pk3 yht-rivi) (:pk3 rivi))
                                                :eitiedossa (+ (:eitiedossa yht-rivi) (:eitiedossa rivi))
                                                :muut-kustannukset (+ (:muut-kustannukset yht-rivi) (:muut-kustannukset rivi))))

                                      {:nimi "Valtakunnallisesti yhteensä" :pk1 0 :pk2 0 :pk3 0 :eitiedossa 0 :muut-kustannukset 0 :kokonaishinta 0}
                                      rivit)
        elyttain-jaoteltu (sort-by first (group-by :hallintayksikko_nimi rivit))
        formatoi-elyt-fn (fn [ely]
                           (let [elyrivi {:otsikko (str (:elynumero (first (second ely))) " " (first ely))}
                                 kohteet-lista (mapv (fn [kohde] (apurit/pkluokka-rivi kohde false false)) (second ely))
                                 ely-yhteensa (reduce (fn [yht-rivi rivi]
                                                        (let [pk1 (:pk1 rivi)
                                                              pk2 (:pk2 rivi)
                                                              pk3 (:pk3 rivi)
                                                              ei-tiedossa (:eitiedossa rivi)]

                                                          (assoc yht-rivi
                                                            :pk1 (+ (:pk1 yht-rivi) pk1)
                                                            :pk2 (+ (:pk2 yht-rivi) pk2)
                                                            :pk3 (+ (:pk3 yht-rivi) pk3)
                                                            :eitiedossa (+ (:eitiedossa yht-rivi) ei-tiedossa)
                                                            :muut-kustannukset (+ (:muut-kustannukset yht-rivi) (:muut-kustannukset rivi)))))

                                                {:nimi (str (first ely) " yhteensä") :pk1 0 :pk2 0 :pk3 0 :eitiedossa 0 :muut-kustannukset 0}
                                                (second ely))]

                             (vec (flatten [elyrivi (into [] kohteet-lista) (apurit/pkluokka-rivi ely-yhteensa true true)]))))

        valtukunnallinen-rivi (apurit/pkluokka-rivi valtakunnallisesti-yhteensa true true)]
    [:taulukko {:otsikko "Eurot / PK-luokka"
                :tyhja (when (empty? rivit) "Ei kohteita.")
                :sheet-nimi "Eurot / PK-luokka"}
     ;; Otsikot
     (concat
       [{:otsikko "Urakka" :leveys 10}
        {:otsikko "PK1" :leveys 2 :fmt :raha}
        {:otsikko "PK2" :leveys 2 :fmt :raha}
        {:otsikko "PK3" :leveys 2 :fmt :raha}
        {:otsikko "Ei tiedossa" :leveys 2 :fmt :raha}
        {:otsikko "Ei kohdistettu" :leveys 2 :fmt :raha}])
     ;; Data
     (concat (into [] (mapcat formatoi-elyt-fn elyttain-jaoteltu)) [valtukunnallinen-rivi])]))

(defn pkluokka-yotyo-taulukko [urakkarivit]
  (let [yhtrivi-pohja (fn [otsikko]
                        {:nimi otsikko
                         :pk1-pituus-yotyo 0 :pk1-pituus 0
                         :pk2-pituus-yotyo 0 :pk2-pituus 0
                         :pk3-pituus-yotyo 0 :pk3-pituus 0
                         :eitiedossa-pituus-yotyo 0 :eitiedossa-pituus 0})
        fn-laske-yhteen (fn [yht-rivi rivi]
                          (assoc yht-rivi
                            :pk1-pituus-yotyo (+ (:pk1-pituus-yotyo yht-rivi) (:pk1-pituus-yotyo rivi))
                            :pk1-pituus (+ (:pk1-pituus yht-rivi) (:pk1-pituus rivi))
                            :pk2-pituus-yotyo (+ (:pk2-pituus-yotyo yht-rivi) (:pk2-pituus-yotyo rivi))
                            :pk2-pituus (+ (:pk2-pituus yht-rivi) (:pk2-pituus rivi))
                            :pk3-pituus-yotyo (+ (:pk3-pituus-yotyo yht-rivi) (:pk3-pituus-yotyo rivi))
                            :pk3-pituus (+ (:pk3-pituus yht-rivi) (:pk3-pituus rivi))
                            :eitiedossa-pituus-yotyo (+ (:eitiedossa-pituus-yotyo yht-rivi) (:eitiedossa-pituus-yotyo rivi))
                            :eitiedossa-pituus (+ (:eitiedossa-pituus yht-rivi) (:eitiedossa-pituus rivi))))
        valtakunnallisesti-yhteensa (reduce
                                      fn-laske-yhteen
                                      (yhtrivi-pohja "Valtakun. yht. / KA%")
                                      urakkarivit)

        ;; Groupataan ja järjestellään rivit hallintayksiköittäin, kuten pk-luokka taulukossakin
        elyttain-jaoteltu (sort-by first (group-by :hallintayksikko_nimi urakkarivit))

        formatoi-elyt-fn (fn [ely]
                           (let [elyrivi {:otsikko (str (:elynumero (first (second ely))) " " (first ely))}
                                 urakat-lista (mapv
                                                (fn [rivi]
                                                  (pkluokka-yotyo-rivi rivi false false))
                                                (second ely))
                                 ely-yhteensa (reduce fn-laske-yhteen
                                                (yhtrivi-pohja (str (first ely) " yht. / KA%"))
                                                (second ely))]

                             (vec (flatten [elyrivi (into [] urakat-lista) (pkluokka-yotyo-rivi ely-yhteensa true true)]))))

        valtukunnallinen-rivi (pkluokka-yotyo-rivi valtakunnallisesti-yhteensa true true)]

    [:taulukko {:tyhja (when (empty? urakkarivit) "Ei kohteita.")
                :sheet-nimi "Yötyö / PK-luokka"
                :rivi-ennen [{:sarakkeita 1 :leveys 10}
                             {:teksti "PK1 yötyö"
                              :sarakkeita 2
                              :luokka "paallystys-tausta-tumma"
                              :tummenna-teksti? true
                              :tasaa :keskita}
                             {:teksti "PK2 yötyö"
                              :sarakkeita 2
                              ;;:luokka "paallystys-tausta-tumma"
                              :tummenna-teksti? true
                              :tasaa :keskita}
                             {:teksti "PK3 yötyö"
                              :sarakkeita 2
                              :luokka "paallystys-tausta-tumma"
                              :tummenna-teksti? true
                              :tasaa :keskita}
                             {:teksti "Ei tiedossa yötyö"
                              :sarakkeita 2
                              ;;:luokka "paallystys-tausta-tumma"
                              :tummenna-teksti? true
                              :tasaa :keskita}]}
     ;; Otsikot
     (concat
       [{:otsikko "Urakka" :leveys 10}
        {:otsikko "Pituus (km)" :leveys 2 :fmt :numero :tasaa :oikea}
        {:otsikko "Prosenttiosuus" :leveys 2 :fmt :prosentti :tasaa :vasen}
        {:otsikko "Pituus (km)" :leveys 2 :fmt :numero :tasaa :oikea}
        {:otsikko "Prosenttiosuus" :leveys 2 :fmt :prosentti :tasaa :vasen}
        {:otsikko "Pituus (km)" :leveys 2 :fmt :numero :tasaa :oikea}
        {:otsikko "Prosenttiosuus" :leveys 2 :fmt :prosentti :tasaa :vasen}
        {:otsikko "Pituus (km)" :leveys 2 :fmt :numero :tasaa :oikea}
        {:otsikko "Prosenttiosuus" :leveys 2 :fmt :prosentti :tasaa :vasen}])
     ;; Data
     (concat (into [] (mapcat formatoi-elyt-fn elyttain-jaoteltu)) [valtukunnallinen-rivi])]))

(defn muut-kustannukset-taulukko [muut-kustannukset urakan-sanktiot urakka-tai-hallintayksikko?]
  (let [nimi "Muut kustannukset"
        ;; Yhdistetään muut-kustannukset ja sanktiot yhdeksi listaksi
        ;; Ja muokataan sanktiot sisältämään hallintayksikön tiedot
        urakan-sanktiot (map
                          (fn [sanktio]
                            (assoc sanktio :hallintayksikko_nimi (get-in sanktio [:hallintayksikko :nimi])
                              :elynumero (get-in sanktio [:hallintayksikko :id])))
                          urakan-sanktiot)
        elyjaottelu-rivit (group-by :hallintayksikko_nimi (concat [] muut-kustannukset urakan-sanktiot))
        formatoi-elyt-fn (fn [ely]
                           (let [elyrivi {:otsikko (str (:elynumero (first (second ely))) " " (first ely))}
                                 rivi-lista (mapv (fn [rivi]
                                                    (let [kohdetiedot (when-not (:selite rivi)
                                                                        (yllapitokohteet-domain/fmt-kohteen-nimi-ja-yhaid-opt rivi))]
                                                      (muut-kustannukset-rivi
                                                        (assoc rivi
                                                          :teksti (or (:selite rivi)
                                                                    (case (:sakkoryhma rivi)
                                                                      "yllapidon_sakko" (str "Sakko" kohdetiedot)
                                                                      "yllapidon_bonus" (str "Bonus" kohdetiedot))))
                                                        false false))) (second ely))]

                             (vec (flatten [elyrivi (into [] rivi-lista)]))))

        fn-formatoi-kustannus (fn [kustannus]
                                (let [kohdetiedot (when-not (:selite kustannus)
                                                    (yllapitokohteet-domain/fmt-kohteen-nimi-ja-yhaid-opt kustannus))]
                                  (-> [(:pvm kustannus)
                                       (:urakka-nimi kustannus)
                                       (or (:selite kustannus)
                                         (case (:sakkoryhma kustannus)
                                           "yllapidon_sakko" (str "Sakko" kohdetiedot)
                                           "yllapidon_bonus" (str "Bonus" kohdetiedot)))
                                       (or (:hinta kustannus)
                                         (:maara kustannus))])))
        rivit (vec (if urakka-tai-hallintayksikko?
                     ;; Älä tee hallintayksikön ryhmitystä ellei kokomaa valittuna
                     (map fn-formatoi-kustannus (apply conj muut-kustannukset urakan-sanktiot))
                     ;; Tee yksiköiden ryhmitys
                     (mapcat formatoi-elyt-fn elyjaottelu-rivit)))]
    [:taulukko {:otsikko nimi
                :tyhja (when (empty? muut-kustannukset) "Ei muita kustannuksia.")
                :sheet-nimi nimi
                :lisaa-excel-valiotsikot true}
     [{:otsikko "Pvm" :leveys 2 :fmt :pvm}
      {:otsikko "Urakka" :leveys 4}
      {:otsikko "Selitys" :leveys 4}
      {:otsikko "Summa" :leveys 2 :fmt :raha}]
     rivit]))

(defn suorita [db user {:keys [urakka-id vuosi hallintayksikko-id] :as tiedot}]
  (let [urakka-tai-hallintayksikko? (or
                                      (some? urakka-id)
                                      (and (some? hallintayksikko-id) (not urakka-id)))

        raportin-nimi (if urakka-id
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
                          (map #(assoc % :maara (- (:maara %)))))
        ;; Yhteenvetoraportilla on lisäksi Eurot / PK-luokka osio
        pkluokkien-kustannukset (when-not urakka-id
                                  (sort-by
                                    :elynumero
                                    (pkluokkien-kustannukset-urakoittain db {:vuosi vuosi
                                                                             :hallintayksikko hallintayksikko-id})))
        pkluokkien-kustannukset (when-not urakka-id
                                  (apurit/formatoi-pkluokkarivit-taulukolle pkluokkien-kustannukset muut-kustannukset
                                    urakan-sanktiot yllapitokohteet+kustannukset vuosi))




        pkluokkien-yotyot (when-not urakka-id
                            (sort-by
                              :elynumero
                              (pkluokkien-yotyot-hallintayksikoittain db {:vuosi vuosi
                                                                          :hallintayksikko hallintayksikko-id})))
        pkluokkien-yotyot-elyittain-jaoteltuna (apurit/formatoi-yotyorivit-taulukolle pkluokkien-yotyot yllapitokohteet+kustannukset)]

    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     ;; Yha kohteet
     (yllapitokohteet-taulukko (filter :yhaid yllapitokohteet+kustannukset) :yha vuosi urakka-tai-hallintayksikko?)
     ;; Muut kohteet
     (yllapitokohteet-taulukko (filter (comp not :yhaid) yllapitokohteet+kustannukset) :paikkaus vuosi urakka-tai-hallintayksikko?)
     ;; Muut kustannukset
     (muut-kustannukset-taulukko muut-kustannukset urakan-sanktiot urakka-tai-hallintayksikko?)
     ;; Näytetään aikataulu vain urakan kontekstissa
     (when urakka-id
       (mapcat (fn [[_ otsikko raportti-fn]]
                 (concat [[:otsikko otsikko]]
                   (yleinen/osat (raportti-fn db user tiedot))))
         [[:yllapidon-aikataulu "Aikataulu" yllapidon-aikataulu/suorita]]))
     ;; Yhteenveto
     (yhteensa-taulukko yllapitokohteet+kustannukset muut-kustannukset urakan-sanktiot vuosi)

     ;; Eurot / PK-luokka - Näytetään vain hallintayksiköille ja valtakunnallisesti
     (when-not urakka-id
       (pkluokka-taulukko pkluokkien-kustannukset))

     ;; Yötyö / PK-luokka - Näytetään vain hallintayksiköille ja valtakunnallisesti
     (when-not urakka-id
       (concat []
         [[:otsikko (str "Yötyö / PK-luokka")]]
         [[:teksti (str "Pituus (km) on urakassa ko. PK-luokan kohteille yötyönä tehdyt päällystyskilometrit.")]]
         [[:teksti (str "Prosenttiosuus on urakassa/ELY:ssä ko. PK-luokan yötyönä tehdyt päällystyskilometrit suhteututtuna kaikkiin päällystyskilometreihin.")]]
         [(pkluokka-yotyo-taulukko pkluokkien-yotyot-elyittain-jaoteltuna)]))]))
