(ns harja.palvelin.palvelut.yllapitokohteet.tiemerkinta-apurit
  "Tiemerkintöjen kustannuksien apufunktiot"
  (:require
   [taoensso.timbre :as log]
   [slingshot.slingshot :refer [throw+]]

   [harja.pvm :as pvm]
   [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]))


(defn default-kustannuslista
  "Palauttaa oletus nolla-arvot vuosille, joille ei ole merkitty kustannuksia
   urakan alkamisvuoden ja loppumisvuoden perusteella."
  [urakka-id alkuvuosi loppuvuosi]
  (for [x (range alkuvuosi (+ 1 loppuvuosi))]
    (assoc {} :urakka urakka-id :kustannusvuosi x :kustannus 0 :pk1 0 :pk2 0 :pk3 0)))


(defn tee-valmis-kustannuslista [vastaus default-lista]
  (let [filteroi-arvoilla-fn (fn [data arvot]
                               (filterv #(not (some (set arvot) (vals %))) data))
        filteroi-vuodet (into [] (map :kustannusvuosi vastaus))
        filteroitu-lista (filteroi-arvoilla-fn default-lista filteroi-vuodet)]
    (sort-by :kustannusvuosi (concat vastaus filteroitu-lista))))


(defn validoi-kustannuskirjaus-rivi
  "Validointi tiemerkintöjen korjaus kustannuksille"
  [rivi]
  (let [summa (->> [:pk1 :pk2 :pk3]
                (map #(get rivi % 0))
                (reduce +)
                float)]
    (when-not (= 100.0 summa)
      (log/error "PK-osuuksien summan on oltava 100, saatiin:" summa)
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+
                          :viesti "PK-osuuksien summan on oltava 100"}]}))))


(defn- laske-prosentti [kokonais-hinta osa]
  (if (pos? kokonais-hinta) (/ (* osa 100.0) kokonais-hinta) 0))


(defn koosta-palautettu-arvo
  "Halutaan varmistaa että kaikki kentät palauttaa yhtenäisen mapin"
  [tyyppi kokonais-hinta
   pk1-hinta pk1-prosentti
   pk2-hinta pk2-prosentti
   pk3-hinta pk3-prosentti
   ei-luokkaa-hinta ei-luokkaa-prosentti]

  {:id (gensym)
   :tyyppi tyyppi
   :kustannus kokonais-hinta

   :pk1-hinta pk1-hinta
   :pk1-prosentti pk1-prosentti

   :pk2-hinta pk2-hinta
   :pk2-prosentti pk2-prosentti

   :pk3-hinta pk3-hinta
   :pk3-prosentti pk3-prosentti

   :ei-luokkaa-hinta ei-luokkaa-hinta
   :ei-luokkaa-prosentti ei-luokkaa-prosentti})


(defn laske-yhteensa 
  "Laskee yhteenvedolle viimeisen yhteensä- rivin"
  [yhteenveto]
  (let [kokonais-hinta (reduce + 0 (map :kustannus yhteenveto))
        pk1-hinta (reduce + 0 (map #(or (:pk1-hinta %) 0) yhteenveto))
        pk2-hinta (reduce + 0 (map #(or (:pk2-hinta %) 0) yhteenveto))
        pk3-hinta (reduce + 0 (map #(or (:pk3-hinta %) 0) yhteenveto))
        ei-luokkaa-hinta  (reduce + 0 (map #(or (:ei-luokkaa-hinta %) 0) yhteenveto))]
 
    (koosta-palautettu-arvo
      :yhteensa kokonais-hinta
      pk1-hinta (laske-prosentti kokonais-hinta pk1-hinta)
      pk2-hinta (laske-prosentti kokonais-hinta pk2-hinta)
      pk3-hinta (laske-prosentti kokonais-hinta pk3-hinta)
      ei-luokkaa-hinta (laske-prosentti kokonais-hinta ei-luokkaa-hinta))))


(defn laske-korjaukset
  "Laskee korjaus kustannukset yhteenvedolle valitun aikavälin perusteella"
  [korjaus-kustannukset [alku loppu]]
  (let [suodatettu (filter (fn [{vuosi :kustannusvuosi}]
                             (when vuosi
                               (let [kustannuksen-pvm (pvm/vuoden-eka-pvm vuosi)]
                                 (and
                                   (not (pvm/ennen? kustannuksen-pvm alku))
                                   (not (pvm/jalkeen? kustannuksen-pvm loppu))))))
                     korjaus-kustannukset)

        kokonais-hinta (reduce + 0 (map :kustannus suodatettu))

        pk1-hinta (reduce + (map (fn [{:keys [kustannus pk1]}] (* kustannus (/ pk1 100.0))) suodatettu))
        pk2-hinta (reduce + (map (fn [{:keys [kustannus pk2]}] (* kustannus (/ pk2 100.0))) suodatettu))
        pk3-hinta (reduce + (map (fn [{:keys [kustannus pk3]}] (* kustannus (/ pk3 100.0))) suodatettu))

        pk1-prosentti (if (pos? kokonais-hinta) (/ (* pk1-hinta 100.0) kokonais-hinta) 0)
        pk2-prosentti (if (pos? kokonais-hinta) (/ (* pk2-hinta 100.0) kokonais-hinta) 0)
        pk3-prosentti (if (pos? kokonais-hinta) (/ (* pk3-hinta 100.0) kokonais-hinta) 0)]

    [(koosta-palautettu-arvo
       :korjaus kokonais-hinta
       pk1-hinta pk1-prosentti
       pk2-hinta pk2-prosentti
       pk3-hinta pk3-prosentti nil nil)]))


(defn laske-tiemerkintakustannukset
  "Laskee uusien päällysteiden merkinnät yhteenvedolle valitun aikavälin perusteella"
  [kustannukset tyyppi]
  ;;({:pienmerkinnat 60000.00M,  :linjamerkinnat 10000.00M, :jyrsinnat 30000.00M :pk-luokka PK2},
  ;; {:pienmerkinnat 1100.00M,  :linjamerkinnat 0M, :jyrsinnat 0M :pk-luokka PK1 })
  ;;({:pienmerkinnat 15000.00M, :linjamerkinnat 35000.00M, :pk-luokka Ei tiedossa, :jyrsinnat 10000.00M},
  ;; {:pienmerkinnat 0.00M, :linjamerkinnat 5000.00M, :pk-luokka PK2, :jyrsinnat 0.00M})
  (let [ryhmat (group-by :pk-luokka kustannukset)
        laske-summa-fn (fn [luokka]
                         ;; Summaa kaikki kustannukset pk luokan mukaan 
                         (reduce + 0 (map (fn [{:keys [pienmerkinnat linjamerkinnat jyrsinnat]}]
                                            (+ (or pienmerkinnat 0)
                                               (or linjamerkinnat 0)
                                               (or jyrsinnat 0)))
                                       (get ryhmat luokka))))

        pk1-hinta (laske-summa-fn "PK1")
        pk2-hinta (laske-summa-fn "PK2")
        pk3-hinta (laske-summa-fn "PK3")
        ei-luokkaa-hinta (laske-summa-fn "Ei tiedossa")
        kokonais-hinta (+ pk1-hinta pk2-hinta pk3-hinta ei-luokkaa-hinta)]

    [(koosta-palautettu-arvo
       tyyppi kokonais-hinta
       pk1-hinta (laske-prosentti kokonais-hinta pk1-hinta)
       pk2-hinta (laske-prosentti kokonais-hinta pk2-hinta)
       pk3-hinta (laske-prosentti kokonais-hinta pk3-hinta)
       ei-luokkaa-hinta (laske-prosentti kokonais-hinta ei-luokkaa-hinta))]))


(defn laske-sakot
  "Laskee sakot ja bonukset yhteenvedolle"
  [sakot]
  (let [ryhmat (group-by :laji sakot)
        summa (fn [laji] (reduce + 0 (map :summa (get ryhmat laji))))
        sakko-hinta (summa :yllapidon_sakko)
        bonus-hinta (summa :yllapidon_bonus)
        tee-rivi (fn [tyyppi hinta]
                   (koosta-palautettu-arvo
                     tyyppi hinta
                     nil nil
                     nil nil
                     nil nil
                     hinta 100.0))]
    [(tee-rivi :sakko sakko-hinta)
     (tee-rivi :bonus bonus-hinta)]))


(defn laske-muut
  "Laskee muut kustannukset yhteenvetoon.
   Pelkästään Arvomuutokset, ja Muut kustannukset halutaan erotella.
   
   Eli kaikki nämä lasketaan muihin kustannuksiin yhteenvedolle:
   :lisatyo :muu :muutostyo :indeksi :sopimusalueen-muutos

   Paitsi :arvomuutos tulee omana rivinään"
  [rivit]
  (let [normalisoitu (map
                       ;; Palautetaan joko :muut tai :arvomuutos 
                       #(assoc % :tyyppi (if (= (:tyyppi %) :arvonmuutos) :arvonmuutos :muut))
                       rivit)

        ryhmat (group-by :tyyppi normalisoitu)

        laske-pk-summa-fn (fn [tyyppi pk]
                            ;; Laske pk luokittainen summa 
                            ;; Pk luokka pitää noukkia :yllapitoluokka avaimesta
                            (reduce + 0
                              (keep (fn [{:keys [hinta yllapitoluokka]}]
                                      (when (= (:nimi yllapitoluokka) pk)
                                        (or hinta 0)))
                                (get ryhmat tyyppi))))

        laske-kaikki-fn (fn [tyyppi]
                          ;; Laskee kokonaishinnan (kaikki yht)
                          (reduce + 0 (map :hinta (get ryhmat tyyppi))))

        koosta-vastaus (fn [tyyppi]
                         (let [yhteenveto-tyypit {:muut :muut-kustannukset
                                                  :arvonmuutos :arvonmuutokset}
                               kokonais-hinta (laske-kaikki-fn tyyppi)
                               pk1-hinta (laske-pk-summa-fn tyyppi "PK1")
                               pk2-hinta (laske-pk-summa-fn tyyppi "PK2")
                               pk3-hinta (laske-pk-summa-fn tyyppi "PK3")
                               ei-luokkaa-hinta (laske-pk-summa-fn tyyppi "Ei pk-luokkaa")]

                           (koosta-palautettu-arvo
                             (tyyppi yhteenveto-tyypit) kokonais-hinta
                             pk1-hinta (laske-prosentti kokonais-hinta pk1-hinta)
                             pk2-hinta (laske-prosentti kokonais-hinta pk2-hinta)
                             pk3-hinta (laske-prosentti kokonais-hinta pk3-hinta)
                             ei-luokkaa-hinta (laske-prosentti kokonais-hinta ei-luokkaa-hinta))))]

    (mapv koosta-vastaus [:muut :arvonmuutos])))
