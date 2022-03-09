(ns harja.tiedot.urakka.suunnittelu.mhu-tehtavat
  (:require [tuck.core :refer [process-event] :as tuck]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.ui.viesti :as viesti]
            [reagent.core :as r]
            [harja.ui.kentat :as kentat]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]))

(defonce taulukko-tila (r/atom {}))
(defonce taulukko-avatut-vetolaatikot (r/atom #{}))
(defonce taulukko-virheet (r/atom {}))

(defrecord ValitseTaso [arvo taso])
(defrecord HaeTehtavat [parametrit])
(defrecord TehtavaHakuOnnistui [tehtavat parametrit])
(defrecord HakuEpaonnistui [])
(defrecord MaaraHakuOnnistui [maarat prosessoi-tulokset])
(defrecord TehtavaTallennusOnnistui [vastaus])
(defrecord TehtavaTallennusEpaonnistui [vastaus])
(defrecord TallennaTehtavamaara [tehtava])
#_(defrecord HaeMaarat [parametrit])
(defrecord SamatTulevilleMoodi [samat?])
#_(defrecord HaeSopimuksenTiedot [])
(defrecord SopimuksenHakuOnnistui [tulos])
(defrecord TallennaSopimus [tallennettu])
(defrecord SopimuksenTallennusOnnistui [vastaus])
(defrecord SopimuksenTallennusEpaonnistui [vastaus])
(defrecord HaeSopimuksenTila [])
(defrecord SopimuksenTilaHaettu [vastaus])
(defrecord SopimuksenTilaEiHaettu [vastaus])
(defrecord SopimuksenTehtavaTallennusOnnistui [vastaus])
(defrecord SopimuksenTehtavaTallennusEpaonnistui [vastaus])
(defrecord TallennaSopimuksenTehtavamaara [tehtava])
(defrecord AsetaOletusHoitokausi [])

(def toimenpiteet #{:talvihoito
                    :liikenneympariston-hoito
                    :sorateiden-hoito
                    :paallystepaikkaukset
                    :mhu-yllapito
                    :mhu-korvausinvestointi})

(defonce sopimuksen-tehtavamaarat-kaytossa? true)

(defn maarille-tehtavien-tiedot
  [maarat-map {:keys [id maarat] :as _r}]
  (assoc-in maarat-map [id] maarat))

(defn sopimuksen-maarille-tehtavien-tiedot
  [maarat-map {:keys [tehtava-id] :as rivi}]
  (assoc-in maarat-map [tehtava-id] rivi))

(defn- sovittuja-jaljella
  [sovitut-maarat syotetyt-maarat-yhteensa]
  (cond 
    (zero? sovitut-maarat) 
    (str (fmt/piste->pilkku (fmt/desimaaliluku (- sovitut-maarat syotetyt-maarat-yhteensa) 0 2 false)) " (-%)")

    sovitut-maarat
    (str 
      (fmt/piste->pilkku (fmt/desimaaliluku (- sovitut-maarat syotetyt-maarat-yhteensa) 0 2 false))
      " (" 
      (fmt/prosentti (* 
                       (/ 
                         (- sovitut-maarat syotetyt-maarat-yhteensa) 
                         sovitut-maarat) 
                       100)) 
      ")")

    :else
    nil))

(defn- summaa-maarat
  [maarat-tahan-asti summa vuosi]
  (+ summa (get maarat-tahan-asti vuosi)))

(defn- map->id-map-maaralla
  [hoitokausi rivi]
  (let [maarat-tahan-asti (get rivi :maarat)
        hoitokaudet (range 
                      (-> @tiedot/yleiset :urakka :alkupvm pvm/vuosi) 
                      (-> @tiedot/yleiset :urakka :loppupvm pvm/vuosi))
        sovitut-maarat (reduce (r/partial summaa-maarat (get rivi :sopimuksen-tehtavamaarat)) 0 hoitokaudet)
        syotetyt-maarat-yhteensa (reduce (r/partial summaa-maarat maarat-tahan-asti) 0 hoitokaudet)
        samat-maarat-vuosittain? (get rivi :samat-maarat-vuosittain?)]
    [(:id rivi)  
     (assoc rivi 
       :hoitokausi hoitokausi
       :maara (get-in rivi [:maarat hoitokausi])
       :joka-vuosi-erikseen? (if (some? samat-maarat-vuosittain?)
                               (not samat-maarat-vuosittain?)
                               false) 
       :sopimuksen-tehtavamaarat (get rivi :sopimuksen-tehtavamaarat)
       :sopimuksen-tehtavamaara (when samat-maarat-vuosittain? 
                                  (get-in 
                                    rivi 
                                    [:sopimuksen-tehtavamaarat
                                     (-> rivi
                                       :sopimuksen-tehtavamaarat
                                       (dissoc :samat-maarat-vuosittain?)
                                       keys
                                       first)]))
       ; nämä käytössä suunniteltavien määrien syöttönäkymässä
       :sopimuksen-tehtavamaarat-yhteensa sovitut-maarat
       :sovittuja-jaljella (sovittuja-jaljella sovitut-maarat syotetyt-maarat-yhteensa))]))

(defn liita-sopimusten-tiedot 
  [sopimusten-maarat rivi]
  (let [{:keys [maara]} (get sopimusten-maarat (:id rivi))]
       (assoc rivi :sopimuksen-maara maara)))

(defn nayta-valittu-toimenpide-tai-kaikki
  [valittu-toimenpide {:keys [sisainen-id] :as toimenpide}]  
  (assoc toimenpide
    :nayta-toimenpide?
    (or 
      (= :kaikki valittu-toimenpide) 
      (= sisainen-id valittu-toimenpide))))

(defn luo-taulukon-rakenne-ja-liita-tiedot
  [{:keys [hoitokausi]} {:keys [nimi id tehtavat]}]
  (let [taulukkorakenne (into 
                          {} 
                          (map (r/partial map->id-map-maaralla hoitokausi)) 
                          tehtavat)] 
    (swap! taulukko-tila 
      assoc id taulukkorakenne)                                 
    {:nimi nimi 
     :sisainen-id id
     :taulukko taulukkorakenne
     :nayta-toimenpide? true}))

(defn paivita-tehtavien-maarat-hoitokaudelle 
  [hoitokausi [id tehtava]] 
  [id (assoc tehtava :maara (get (:maarat tehtava) hoitokausi))])

(defn paivita-toimenpiteiden-tehtavien-maarat-hoitokaudelle
  [hoitokausi [vanhempi-id tehtavat]]
  [vanhempi-id (into {} 
                 (map (r/partial paivita-tehtavien-maarat-hoitokaudelle hoitokausi)) 
                 tehtavat)])

(defn toimenpiteen-tehtavien-maarat-taulukolle-hoitokauden-tiedoilla
  [taulukon-tila hoitokausi]
  (into {} (map (r/partial paivita-toimenpiteiden-tehtavien-maarat-hoitokaudelle hoitokausi) taulukon-tila)))

(defn muodosta-taulukko 
  [tehtavat-ja-toimenpiteet valinnat]
  (reset! taulukko-tila {})
  (let [taulukon-rakenteet (into []  
                             (map (r/partial luo-taulukon-rakenne-ja-liita-tiedot {:hoitokausi (:hoitokausi valinnat)}))
                             tehtavat-ja-toimenpiteet)]
    taulukon-rakenteet))

(defn paivita-taulukko-valitulle-tasolle
  [taulukko valinnat]
  (swap! taulukko-tila 
    toimenpiteen-tehtavien-maarat-taulukolle-hoitokauden-tiedoilla 
    (-> valinnat :hoitokausi))
  (into []  
    (map (r/partial nayta-valittu-toimenpide-tai-kaikki (-> valinnat :toimenpide :id)))              
    taulukko))

(defn paivita-maarat-ja-laske-sovitut
  [tehtavan-tiedot {:keys [sopimuksen-tehtavamaarat maarat]}]
  (let [taulukkorakenne (assoc-in tehtavan-tiedot [:maarat] maarat)
        maarat-yhteensa (reduce (r/partial summaa-maarat maarat) 0 (keys maarat))
        sovitut-maarat (reduce (r/partial summaa-maarat sopimuksen-tehtavamaarat) 0 (keys sopimuksen-tehtavamaarat))]    
    (assoc-in taulukkorakenne [:sovittuja-jaljella] (sovittuja-jaljella sovitut-maarat maarat-yhteensa))))

(defn paivita-sovitut-jaljella-sarake
  [taulukon-tila {:keys [vanhempi id] :as tehtava}]
  (update-in taulukon-tila [vanhempi id] paivita-maarat-ja-laske-sovitut tehtava))

(defn vain-taso-3 
  [data]
  (= 3 (:taso data)))

(defn vain-taso-4
  [data]
  (= 4 (:taso data)))

(def valitason-toimenpiteet
  (filter vain-taso-3))

(defn yksikoton
  "Tämä näyttää hölmöltä mutta toimii, lambda palauttaa funktion cond->:issa"
  [_]
  true)

(defn sopimuksen-tehtavamaarallinen
  [_]
  true)

(defn kaikki-sopimusmaarat
  [sopimuksen-tehtavamaarat hoitokaudet _]
  (every? (fn [vuosi]
            (some? (get sopimuksen-tehtavamaarat vuosi))) hoitokaudet))

(defn sopimus-maara-syotetty 
  [virheet-kaikki r]
  (let [{:keys [yksikko sopimuksen-tehtavamaarat sopimuksen-tehtavamaara]} (second r)
        hoitokaudet (range 
                      (-> @tiedot/yleiset :urakka :alkupvm pvm/vuosi) 
                      (-> @tiedot/yleiset :urakka :loppupvm pvm/vuosi))
        id (first r)
        kaikki-maarat-fn (r/partial kaikki-sopimusmaarat 
                           sopimuksen-tehtavamaarat
                           hoitokaudet)
        syotetty? (cond-> false
                    (or (nil? yksikko)
                      (= "" yksikko)
                      (= "-" yksikko)) yksikoton
                    
                    (some? sopimuksen-tehtavamaara) sopimuksen-tehtavamaarallinen
                    
                    (some? sopimuksen-tehtavamaarat) kaikki-maarat-fn)] 
    (if-not syotetty?
      (assoc-in virheet-kaikki [id :sopimuksen-tehtavamaara] ["Syötä 0 tai luku"])
      virheet-kaikki)))

(defn toimenpiteet-sopimuksen-tehtavamaarat-syotetty
  [virheet-kaikki [_ taulukkorakenne]]
  (reduce sopimus-maara-syotetty virheet-kaikki taulukkorakenne))

(defn tarkista-sovitut-maarat
  [taulukko]
  (reduce toimenpiteet-sopimuksen-tehtavamaarat-syotetty {} taulukko))

(defn syotetty-maara-tuleville-vuosille 
  [tehtava hoitokausi]
  (update tehtava :maarat assoc hoitokausi (:maara tehtava)))

(defn tayta-vuodet [sopimuksen-tehtavamaara vuosi] 
  [vuosi sopimuksen-tehtavamaara])

(defn paivita-vuosien-maarat 
  [taulukko-tila {:keys [id vanhempi sopimuksen-tehtavamaara joka-vuosi-erikseen? hoitokausi]}]
  (if-not joka-vuosi-erikseen? 
    (let [urakan-vuodet 
          (range 
            (-> @tiedot/yleiset
              :urakka
              :alkupvm
              pvm/vuosi)
            (-> @tiedot/yleiset
              :urakka
              :loppupvm
              pvm/vuosi))]
      (assoc-in taulukko-tila [vanhempi id :sopimuksen-tehtavamaarat]
        (into {} 
          (map (r/partial tayta-vuodet sopimuksen-tehtavamaara)) 
          urakan-vuodet)))
    (assoc-in taulukko-tila [vanhempi id :sopimuksen-tehtavamaarat hoitokausi] sopimuksen-tehtavamaara)))

(extend-protocol tuck/Event
  AsetaOletusHoitokausi
  (process-event 
    [_ app]
    (assoc-in app [:valinnat :hoitokausi] (if (< (pvm/kuukausi (pvm/nyt)) 10)
                                            (dec (pvm/vuosi (pvm/nyt)))
                                            (pvm/vuosi (pvm/nyt)))))
  SopimuksenTallennusOnnistui
  (process-event 
    [{:keys [vastaus]} app]
    (viesti/nayta! "Tallennus onnistui")
    (-> app 
      (assoc :sopimukset-syotetty? (:tallennettu vastaus))
      (update-in [:valinnat :noudetaan] dec)))
  SopimuksenTallennusEpaonnistui
  (process-event 
    [{:keys [vastaus]} app]
    (viesti/nayta! "Sopimuksen määrien tallennus epäonnistui" :danger)
    (update-in app [:valinnat :noudetaan] dec))
  TallennaSopimus
  (process-event
    [{:keys [tallennettu]} app]
    (let [app (dissoc app :virhe-kaikkia-syottaessa?)
          virheet (tarkista-sovitut-maarat @taulukko-tila)
          kaikki-arvot-syotetty? (empty? (keys virheet))] 
      (if (or kaikki-arvot-syotetty? 
            (false? tallennettu)) 
        (do 
          (tuck-apurit/post! :tallenna-sopimuksen-tila
              {:urakka-id (-> @tiedot/yleiset :urakka :id)
               :tallennettu tallennettu}
              {:onnistui ->SopimuksenTallennusOnnistui
               :epaonnistui ->SopimuksenTallennusEpaonnistui})
          (update-in app [:valinnat :noudetaan] inc))
        (when (not (empty? (keys virheet))) 
          (reset! taulukko-virheet virheet)
          (assoc app :virhe-sopimuksia-syottaessa? true)))))
  SopimuksenTilaEiHaettu
  (process-event 
    [{:keys [vastaus]} app]
    (viesti/nayta! "Sopimuksen määrien tilan tarkastus epäonnistui!" :danger)
    (update-in app [:valinnat :noudetaan] dec))
  SopimuksenTilaHaettu
  (process-event 
    [{:keys [vastaus]} {:keys [taulukko] :as app}]
    (-> app 
      (assoc :sopimukset-syotetty? (:tallennettu vastaus))
      (update-in [:valinnat :noudetaan] dec)))
  HaeSopimuksenTila
  (process-event
    [_ app]
    (tuck-apurit/post! :hae-sopimuksen-tila
      {:urakka-id (-> @tiedot/yleiset :urakka :id)}
      {:onnistui ->SopimuksenTilaHaettu
       :epaonnistui ->SopimuksenTilaEiHaettu})
    (update-in app [:valinnat :noudetaan] inc))
  TehtavaTallennusEpaonnistui
  (process-event
    [_ app]
    (viesti/nayta! "Tallennus epäonnistui" :danger) 
    (-> app      
      (assoc-in [:valinnat :virhe-tallennettaessa] true)
      (assoc-in [:valinnat :tallennetaan] false)))
  TehtavaTallennusOnnistui
  (process-event
    [vastaus {:keys [sopimuksen-maarat] :as app}]
    (-> app 
      (assoc-in [:valinnat :virhe-tallennettaessa] false)
      (assoc-in [:valinnat :tallennetaan] false)))
  SopimuksenTehtavaTallennusOnnistui
  (process-event 
    [{:keys [vastaus]} app]
    (dissoc app :tallennettava))
  SopimuksenTehtavaTallennusEpaonnistui
  (process-event 
    [{:keys [vastaus]} {:keys [tallennettava] :as app}]
    (viesti/nayta! "Tallennus epäonnistui" :danger)
    (let [{:keys [id]} tallennettava
          virheet (assoc-in {} [id :sopimuksen-tehtavamaara] ["Tallennus epäonnistui"])] 
      (reset! taulukko-virheet virheet))      
    (dissoc app :tallennettava))
  TallennaSopimuksenTehtavamaara
  (process-event 
    [{{:keys [sopimuksen-tehtavamaara id vanhempi joka-vuosi-erikseen? hoitokausi] :as tehtava} :tehtava} {:keys [taulukko] :as app}]
    (swap! taulukko-tila paivita-vuosien-maarat tehtava)
    (-> app                             
      (assoc :tallennettava tehtava)
      (tuck-apurit/post! :tallenna-sopimuksen-tehtavamaara
        {:urakka-id (-> @tiedot/yleiset :urakka :id)
         :tehtava-id id
         :hoitovuosi hoitokausi
         :samat-maarat-vuosittain? (not (true? joka-vuosi-erikseen?))
         :maara sopimuksen-tehtavamaara}
        {:onnistui ->SopimuksenTehtavaTallennusOnnistui
         :epaonnistui ->SopimuksenTehtavaTallennusEpaonnistui})))
  TallennaTehtavamaara
  (process-event
    [{tehtava :tehtava} {{samat-tuleville? :samat-tuleville :keys [hoitokausi] :as valinnat} :valinnat taulukko :taulukko :as app}]
    (let [{:keys [id maara]} tehtava
          urakka-id (-> @tiedot/yleiset :urakka :id)
          tehtava (if samat-tuleville? 
                    (reduce syotetty-maara-tuleville-vuosille 
                        tehtava 
                        (range hoitokausi
                          (-> @tiedot/yleiset
                            :urakka
                            :loppupvm
                            pvm/vuosi)))
                    (-> tehtava
                      (assoc :hoitokausi hoitokausi)
                      (update :maarat assoc hoitokausi (:maara tehtava))))]
      (if samat-tuleville?
        (doseq [vuosi (mapv (comp keyword str)
                        (range hoitokausi
                          (-> @tiedot/yleiset
                            :urakka
                            :loppupvm
                            pvm/vuosi)))]
          (tuck-apurit/post! :tallenna-tehtavamaarat
            {:urakka-id             urakka-id
             :hoitokauden-alkuvuosi (-> vuosi
                                      name
                                      js/parseInt)
             :tehtavamaarat         [{:tehtava-id id
                                      :maara      maara}]}
            {:onnistui           ->TehtavaTallennusOnnistui
             :epaonnistui        ->TehtavaTallennusEpaonnistui
             :paasta-virhe-lapi? true}))
        (tuck-apurit/post! :tallenna-tehtavamaarat
          {:urakka-id             urakka-id
           :hoitokauden-alkuvuosi hoitokausi
           :tehtavamaarat         [{:tehtava-id id
                                    :maara      maara}]}
          {:onnistui           ->TehtavaTallennusOnnistui
           :epaonnistui        ->TehtavaTallennusEpaonnistui
           :paasta-virhe-lapi? true})) 
      (swap! taulukko-tila paivita-sovitut-jaljella-sarake tehtava)
      (-> app 
        #_(assoc-in [:maarat id hoitokausi] maara)
        (update :valinnat assoc 
          :virhe-tallennettaessa false
          :tallennetaan true))))
  HakuEpaonnistui
  (process-event
    [_ app]
    (update app :valinnat #(assoc %
                             :virhe-noudettaessa true
                             :noudetaan (dec (:noudetaan %)))))
  TehtavaHakuOnnistui
  (process-event
    [{:keys [tehtavat parametrit]} {:keys [valinnat] :as app}]
    (let [toimenpide {:nimi "0 KAIKKI" :id :kaikki}           
          vetolaatikot-auki (into #{} 
                              (keep 
                                (fn [r]
                                  (when (false? (get r :samat-maarat-vuosittain?))
                                    (:id r)))) 
                              (mapcat :tehtavat tehtavat))
          taulukko (muodosta-taulukko tehtavat valinnat)]
      (reset! taulukko-avatut-vetolaatikot vetolaatikot-auki)
      (-> app
        (assoc :tehtavat-ja-toimenpiteet tehtavat)
        (assoc :taulukko taulukko)
        (assoc :taso-4-tehtavat (into #{} (comp 
                                            (mapcat :tehtavat)
                                            (filter vain-taso-4))
                                  tehtavat))
        (assoc :vetolaatikot-auki vetolaatikot-auki)
        (update :valinnat #(assoc % 
                             :noudetaan (dec (:noudetaan %))
                             :toimenpide-valikko-valinnat (sort-by :nimi 
                                                            (concat 
                                                              [{:nimi "0 KAIKKI" :id :kaikki}] 
                                                              (into [] 
                                                                valitason-toimenpiteet 
                                                                tehtavat)))
                             
                             :toimenpide toimenpide)))))
  HaeTehtavat
  (process-event
    [{parametrit :parametrit} app]
    (-> app
        (tuck-apurit/post! :tehtavamaarat-hierarkiassa
                           {:urakka-id (:id (-> @tiedot/tila :yleiset :urakka))
                            :hoitokauden-alkuvuosi :kaikki}
                           {:onnistui            ->TehtavaHakuOnnistui
                            :epaonnistui         ->HakuEpaonnistui
                            :onnistui-parametrit [parametrit]
                            :paasta-virhe-lapi?  true})
        (update :valinnat #(assoc %
                             :virhe-noudettaessa false
                             :noudetaan (inc (:noudetaan %))))))
  ValitseTaso
  (process-event
    [{:keys [arvo taso]} {:keys [taulukko valinnat] :as app}]
    (as-> app a      
      (assoc-in a [:valinnat taso] arvo)
      (assoc a :taulukko (paivita-taulukko-valitulle-tasolle taulukko (:valinnat a)))))
  SamatTulevilleMoodi
  (process-event [{:keys [samat?]} app]
    (assoc-in app [:valinnat :samat-tuleville] samat?)))
