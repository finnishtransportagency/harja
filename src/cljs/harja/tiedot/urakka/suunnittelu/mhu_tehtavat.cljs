(ns harja.tiedot.urakka.suunnittelu.mhu-tehtavat
  (:require [tuck.core :refer [process-event] :as tuck]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.ui.viesti :as viesti]
            [reagent.core :as r]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]))

(defonce taulukko-tila (r/atom {}))
(defonce taulukko-avatut-vetolaatikot (r/atom #{}))
(defonce taulukko-virheet (r/atom {}))

(defrecord NaytaAluetehtavat [tila])
(defrecord NaytaSuunniteltavatTehtavat [tila])
(defrecord ValitseTaso [arvo taso])
(defrecord HaeTehtavat [parametrit])
(defrecord TehtavaHakuOnnistui [tehtavat parametrit])
(defrecord HakuEpaonnistui [])
(defrecord MaaraHakuOnnistui [maarat prosessoi-tulokset])
(defrecord TehtavaTallennusOnnistui [vastaus])
(defrecord TehtavaTallennusEpaonnistui [vastaus])
(defrecord TallennaTehtavamaara [tehtava])
(defrecord TallennaMuuttunutAluemaara [tehtava])
(defrecord SamatTulevilleMoodi [samat?])
(defrecord SopimuksenHakuOnnistui [tulos])
(defrecord TallennaSopimus [tallennettu])
(defrecord SopimuksenTallennusOnnistui [vastaus])
(defrecord SopimuksenTallennusEpaonnistui [vastaus])
(defrecord HaeSopimuksenTila [])
(defrecord JokaVuosiErikseenKlikattu [ruksittu? vanhempi id])
(defrecord SopimuksenTilaHaettu [vastaus])
(defrecord SopimuksenTilaEiHaettu [vastaus])
(defrecord SopimuksenTehtavaTallennusOnnistui [vastaus])
(defrecord SopimuksenTehtavaTallennusEpaonnistui [vastaus])
(defrecord TallennaSopimuksenAluemaara [tehtava])
(defrecord TallennaSopimuksenTehtavamaara [tehtava])
(defrecord AsetaOletusHoitokausi [])

(def toimenpiteet #{:talvihoito
                    :liikenneympariston-hoito
                    :sorateiden-hoito
                    :paallystepaikkaukset
                    :mhu-yllapito
                    :mhu-korvausinvestointi})

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
  (let [formatoitava (get maarat-tahan-asti vuosi)
        luku (if (string? formatoitava)
               (js/parseFloat formatoitava)
               formatoitava)]
    (+ summa luku)))

(defn- laske-sopimusmaarat
  [rivi]
  (let [{:keys [samat-maarat-vuosittain?]} rivi
        maarat-tahan-asti (get rivi :maarat)
        hoitokaudet (range 
                      (-> @tiedot/yleiset :urakka :alkupvm pvm/vuosi) 
                      (-> @tiedot/yleiset :urakka :loppupvm pvm/vuosi))
        sovitut-maarat (reduce (r/partial summaa-maarat (get rivi :sopimuksen-tehtavamaarat)) 0 hoitokaudet)
        syotetyt-maarat-yhteensa (reduce (r/partial summaa-maarat maarat-tahan-asti) 0 hoitokaudet)]
    (assoc rivi
      :sopimuksen-tehtavamaara (get-in 
                                 rivi 
                                 [:sopimuksen-tehtavamaarat
                                  (-> rivi
                                    :sopimuksen-tehtavamaarat
                                    (dissoc :samat-maarat-vuosittain?)
                                    keys
                                    first)])
                                        ; nämä käytössä suunniteltavien määrien syöttönäkymässä
      :sopimuksen-tehtavamaarat-yhteensa sovitut-maarat
      :sovittuja-jaljella (sovittuja-jaljella sovitut-maarat syotetyt-maarat-yhteensa))))

(defn- map->id-map-maaralla
  [hoitokausi rivi]
  (let [{:keys [samat-maarat-vuosittain? aluetieto?]} rivi]
    [(:id rivi)
     (cond-> rivi
       true (assoc  
              :hoitokausi hoitokausi
              :joka-vuosi-erikseen? (if (some? samat-maarat-vuosittain?)
                                      (not samat-maarat-vuosittain?)
                                      false))
       (not aluetieto?) (assoc :maara
                          (get-in rivi [:maarat hoitokausi]))
       aluetieto? (assoc :muuttunut-aluetieto-maara (get-in rivi [:maarat hoitokausi]))
       (not aluetieto?) laske-sopimusmaarat)]))

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

(defn erottele-alueet-ja-maaralliset
  [kaikki [id rivi]]
  (let [luokka (if (:aluetieto? rivi) :alueet :maarat)]
    (assoc-in kaikki [luokka id] rivi)))

(defn paivita-alueet-ja-maarat
  [{:keys [alueet maarat]} vanhempi ttila]
  (cond-> ttila
    (some? alueet) (assoc-in [:alueet vanhempi] alueet)
    (some? maarat) (assoc-in [:maarat vanhempi] maarat)))

(defn luo-taulukon-rakenne-ja-liita-tiedot
  [{:keys [hoitokausi]} {:keys [nimi id tehtavat]}]
  (let [taulukkorakenne (into 
                          {} 
                          (map (r/partial map->id-map-maaralla hoitokausi)) 
                          tehtavat)
        taulukkorakenne-alueet-ja-maarat-eroteltuna (reduce erottele-alueet-ja-maaralliset {} taulukkorakenne)]
    (swap! taulukko-tila 
      (r/partial paivita-alueet-ja-maarat
        taulukkorakenne-alueet-ja-maarat-eroteltuna id))
    {:nimi nimi 
     :sisainen-id id
     :alue-tehtavia (count (:alueet taulukkorakenne-alueet-ja-maarat-eroteltuna))
     :maara-tehtavia (count (:maarat taulukkorakenne-alueet-ja-maarat-eroteltuna))
     :nayta-toimenpide? true}))

(defn paivita-tehtavien-maarat-hoitokaudelle
  "Taulukolla näkyvälle kentälle päivitetään valitun hoitokauden määrät"
  [hoitokausi [id tehtava]] 
  [id (assoc tehtava (if (:aluetieto? tehtava) :muuttunut-aluetieto-maara :maara) (get-in tehtava [:maarat hoitokausi]))])

(defn paivita-toimenpiteiden-tehtavien-maarat-hoitokaudelle
  [hoitokausi [vanhempi-id tehtavat]]
  [vanhempi-id (into {} 
                 (map (r/partial paivita-tehtavien-maarat-hoitokaudelle hoitokausi)) 
                 tehtavat)])

(defn toimenpiteen-tehtavien-maarat-taulukolle-hoitokauden-tiedoilla
  "Taulukon tila päivitetään hoitokautta vaihtaessa"
  [taulukon-tila hoitokausi]
  (-> taulukon-tila
    (update :maarat
      #(into {} (map (r/partial paivita-toimenpiteiden-tehtavien-maarat-hoitokaudelle hoitokausi) %)))
    (update :alueet
      #(into {} (map (r/partial paivita-toimenpiteiden-tehtavien-maarat-hoitokaudelle hoitokausi) %)))))

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
  (update-in taulukon-tila [:maarat vanhempi id] paivita-maarat-ja-laske-sovitut tehtava))

(defn vain-taso-3 
  [data]
  (= 3 (:taso data)))

(defn vain-taso-4
  [data]
  (= 4 (:taso data)))

(defn vain-yksikolliset
  [{:keys [yksikko]}]
  (not
    (or (nil? yksikko)
      (= "" yksikko)
      (= "-" yksikko))))

(def valitason-toimenpiteet
  (filter vain-taso-3))

(defn sopimuksen-tehtavamaarallinen
  [_]
  true)

(defn kaikki-sopimusmaarat
  [sopimuksen-tehtavamaarat hoitokaudet _]
  (every? (fn [vuosi]
            (some? (get sopimuksen-tehtavamaarat vuosi))) hoitokaudet))

(defn sopimus-maara-syotetty 
  [virheet-kaikki r]
  (let [{:keys [yksikko sopimuksen-tehtavamaarat sopimuksen-tehtavamaara sopimuksen-aluetieto-maara aluetieto?]} (second r)
        hoitokaudet (range 
                      (-> @tiedot/yleiset :urakka :alkupvm pvm/vuosi) 
                      (-> @tiedot/yleiset :urakka :loppupvm pvm/vuosi))
        id (first r)
        kaikki-maarat-fn (r/partial kaikki-sopimusmaarat 
                           sopimuksen-tehtavamaarat
                           hoitokaudet)
        virheviesti ["Syötä 0 tai luku"]
        syotetty? (cond-> false
                    (or (nil? yksikko)
                      (= "" yksikko)
                      (= "-" yksikko)) ((constantly true)) 

                    (and
                      aluetieto?
                      (some? sopimuksen-aluetieto-maara)) ((constantly true))
                    
                    (and
                      (not aluetieto?)
                      (some? sopimuksen-tehtavamaara)) ((constantly true))

                    (and
                      (not aluetieto?)
                      (some? sopimuksen-tehtavamaarat)) kaikki-maarat-fn)] 
    (cond
      (and
        (not syotetty?)
        (not aluetieto?))
      (assoc-in virheet-kaikki [id :sopimuksen-tehtavamaara] virheviesti)

      (and
        (not syotetty?)
        aluetieto?)
      (assoc-in virheet-kaikki [id :sopimuksen-aluetieto-maara] virheviesti)
      
      :else
      virheet-kaikki)))

(defn toimenpiteet-sopimuksen-tehtavamaarat-syotetty
  [virheet-kaikki [_ taulukkorakenne]]
  (reduce sopimus-maara-syotetty virheet-kaikki taulukkorakenne))

(defn tarkista-sovitut-maarat
  [taulukko]
  (let [alueet-ja-maarat (merge-with merge (:alueet taulukko) (:maarat taulukko))]
    (reduce toimenpiteet-sopimuksen-tehtavamaarat-syotetty {} alueet-ja-maarat)))

(defn syotetty-maara-tuleville-vuosille 
  [tehtava hoitokausi]
  (update tehtava :maarat assoc hoitokausi (:maara tehtava)))

(defn tayta-vuodet [sopimuksen-tehtavamaara vuosi] 
  [vuosi sopimuksen-tehtavamaara])

(defn paivita-vuosien-maarat 
  [taulukko-tila {:keys [id vanhempi sopimuksen-tehtavamaara joka-vuosi-erikseen? hoitokausi]}]
  (if joka-vuosi-erikseen?                  
    (assoc-in taulukko-tila [:maarat vanhempi id :sopimuksen-tehtavamaarat hoitokausi] sopimuksen-tehtavamaara)
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
      (assoc-in taulukko-tila [:maarat vanhempi id :sopimuksen-tehtavamaarat]
        (into {} 
          (map (r/partial tayta-vuodet sopimuksen-tehtavamaara)) 
          urakan-vuodet)))))

(defn kopioi-tarvittaessa-sopimusmaarat-maariin
  [rivi]
  (let [{:keys [sopimuksen-tehtavamaarat maarat]} rivi
        maarat (into {}
                 (map
                   (fn [[vuosi sopimuksen-maara]]
                     [vuosi (if (some? (get maarat vuosi))
                              (get maarat vuosi)
                              sopimuksen-maara)]))
                 sopimuksen-tehtavamaarat)]
    (assoc rivi :maarat maarat)))

(defn laske-tehtavalle-sopimusmaarat 
  [[id tehtava]]
  [id (-> tehtava laske-sopimusmaarat kopioi-tarvittaessa-sopimusmaarat-maariin)])

(defn laske-toimenpiteen-sopimusmaarat 
  [[avain toimenpiteen-tehtavat]]
  [avain 
   (into {} 
     (map 
       laske-tehtavalle-sopimusmaarat)
     toimenpiteen-tehtavat)])

(defn laske-kaikki-sopimusmaarat 
  [taulukon-tila] 
  (update
    taulukon-tila
    :maarat
    #(into {} 
       (map laske-toimenpiteen-sopimusmaarat) 
       %)))

(defn paivita-kaikki-maarat
  "Luodaan taulukon tila tarjousmääriä vahvistaessa"
  [taulukon-tila valinnat]
  (-> taulukon-tila 
    laske-kaikki-sopimusmaarat
    (toimenpiteen-tehtavien-maarat-taulukolle-hoitokauden-tiedoilla (:hoitokausi valinnat))))

(defn erikseen-syotetyt-vuodet-auki
  [r]
  (when (false? (get r :samat-maarat-vuosittain?))
    (:id r)))

(defn tallenna
  [app {:keys [polku parametrit]} payload]
  (tuck-apurit/post! app polku payload parametrit))

(defn tallenna-sopimuksen-tehtavamaara
  [app {:keys [tehtava maara vuosi samat?]}]
  (tallenna
    app
    {:polku :tallenna-sopimuksen-tehtavamaara
     :parametrit {:onnistui ->SopimuksenTehtavaTallennusOnnistui
                  :epaonnistui ->SopimuksenTehtavaTallennusEpaonnistui}}
    {:urakka-id (-> @tiedot/yleiset :urakka :id)
     :tehtava-id tehtava
     :hoitovuosi vuosi
     :samat-maarat-vuosittain? samat?
     :maara maara}))

(defn tallenna-tehtavamaarat
  [app {:keys [hoitokausi tehtavamaarat]}]
  (tallenna
    app
    {:polku :tallenna-tehtavamaarat
     :parametrit
     {:onnistui           ->TehtavaTallennusOnnistui
      :epaonnistui        ->TehtavaTallennusEpaonnistui
      :paasta-virhe-lapi? true}}
    {:urakka-id (-> @tiedot/yleiset :urakka :id)
     :nykyinen-hoitokausi hoitokausi
     :tehtavamaarat tehtavamaarat}))

(extend-protocol tuck/Event
  NaytaAluetehtavat
  (process-event [{tila :tila} app]
    (assoc-in app [:valinnat :nayta-aluetehtavat?] tila))
  
  NaytaSuunniteltavatTehtavat
  (process-event [{tila :tila} app]
    (assoc-in app [:valinnat :nayta-suunniteltavat-tehtavat?] tila))
  
  AsetaOletusHoitokausi
  (process-event 
    [_ app]
    (assoc-in app [:valinnat :hoitokausi] (if (< (pvm/kuukausi (pvm/nyt)) 10)
                                            (dec (pvm/vuosi (pvm/nyt)))
                                            (pvm/vuosi (pvm/nyt)))))
  SopimuksenTallennusOnnistui
  (process-event 
    [{:keys [vastaus]} {:keys [valinnat] :as app}]
    (do
      ;; Päivitetään vielä varalta koko taulukon sisältö
      (tuck/action!
        (fn [e!]
          (e! (->HaeTehtavat {:hoitokausi :kaikki}))))

      (viesti/nayta-toast! "Tallennus onnistui")
      (swap! taulukko-tila paivita-kaikki-maarat valinnat)
      (-> app
        (assoc :sopimukset-syotetty? (:tallennettu vastaus))
        (update-in [:valinnat :noudetaan] dec))))

  SopimuksenTallennusEpaonnistui
  (process-event 
    [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Sopimuksen määrien tallennus epäonnistui" :danger)
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
          (reset! taulukko-virheet {})
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
    (viesti/nayta-toast! "Sopimuksen määrien tilan tarkastus epäonnistui!" :danger)
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
    (viesti/nayta-toast! "Tallennus epäonnistui" :danger)
    (let [{:keys [id]} tallennettava
          virheet (assoc-in {} [id :sopimuksen-tehtavamaara] ["Tallennus epäonnistui"])] 
      (reset! taulukko-virheet virheet))      
    (dissoc app :tallennettava))

  TallennaMuuttunutAluemaara
  (process-event
    [{tehtava :tehtava} {{samat-tuleville? :samat-tuleville :keys [hoitokausi] :as valinnat} :valinnat taulukko :taulukko :as app}]
    (let [{:keys [id muuttunut-aluetieto-maara vanhempi]} tehtava]
      (swap! taulukko-tila assoc-in [:alueet vanhempi id :maarat hoitokausi] muuttunut-aluetieto-maara)
      (-> app
        (tallenna-tehtavamaarat
          {:hoitokausi hoitokausi
           :tehtavamaarat (into [] (map
                                     #(-> {:tehtava-id id
                                           :hoitokauden-alkuvuosi %
                                           :maara      muuttunut-aluetieto-maara}))
                            (range hoitokausi (-> @tiedot/yleiset :urakka :loppupvm pvm/vuosi)))})           
        (update :valinnat 
          assoc 
          :virhe-tallennettaessa false
          :tallennetaan true))))

  TallennaSopimuksenAluemaara
  (process-event
    [{:keys [tehtava]} app]
    (let [{:keys [id sopimuksen-aluetieto-maara]} tehtava]     
      (-> app                             
        (assoc :tallennettava tehtava)
        (tallenna-sopimuksen-tehtavamaara {:maara sopimuksen-aluetieto-maara
                                           :vuosi (-> @tiedot/yleiset :urakka :alkupvm pvm/vuosi)
                                           :tehtava id
                                           :samat? false}))))

  TallennaSopimuksenTehtavamaara
  (process-event 
    [{{:keys [sopimuksen-tehtavamaara id vanhempi joka-vuosi-erikseen? hoitokausi] :as tehtava} :tehtava} {:keys [taulukko] :as app}]
    (swap! taulukko-tila paivita-vuosien-maarat tehtava)
    (-> app                             
      (assoc :tallennettava tehtava)
      (tallenna-sopimuksen-tehtavamaara {:maara sopimuksen-tehtavamaara
                                         :samat? (not (true? joka-vuosi-erikseen?))
                                         :tehtava id
                                         :vuosi hoitokausi})))

  TallennaTehtavamaara
  (process-event
    [{tehtava :tehtava} {{samat-tuleville? :samat-tuleville :keys [hoitokausi] :as valinnat} :valinnat taulukko :taulukko :as app}]
    (let [{:keys [id maara]} tehtava
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
          (tallenna-tehtavamaarat app {:hoitokausi hoitokausi
                                       :tehtavamaarat [{:tehtava-id id
                                                        :maara      maara
                                                        :hoitokauden-alkuvuosi (-> vuosi
                                                                                 name
                                                                                 js/parseInt)}]}))
        (tallenna-tehtavamaarat app {:hoitokausi hoitokausi
                                     :tehtavamaarat [{:tehtava-id id
                                                      :maara maara
                                                      :hoitokauden-alkuvuosi hoitokausi}]})) 
      (swap! taulukko-tila paivita-sovitut-jaljella-sarake tehtava)
      
      (update app :valinnat 
        assoc 
        :virhe-tallennettaessa false
        :tallennetaan true)))
  
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
                                erikseen-syotetyt-vuodet-auki) 
                              (mapcat :tehtavat tehtavat))
          taulukko (muodosta-taulukko tehtavat valinnat)]
      (reset! taulukko-avatut-vetolaatikot vetolaatikot-auki)
      (-> app
        (assoc :tehtavat-ja-toimenpiteet tehtavat)
        (assoc :taulukko taulukko)
        (assoc :taso-4-tehtavat (into #{} (comp 
                                            (mapcat :tehtavat)
                                            (filter vain-taso-4)
                                            (filter vain-yksikolliset))
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
                             :hoitokausi (pvm/hoitokauden-alkuvuosi-nykyhetkesta (pvm/nyt))
                             :toimenpide toimenpide)))))

  HaeTehtavat
  (process-event
    [{parametrit :parametrit} app]
    (-> app
      (tuck-apurit/post! :tehtavamaarat-hierarkiassa
        {:urakka-id (:id (-> @tiedot/tila :yleiset :urakka))
         :hoitokauden-alkuvuosi :kaikki}
        {:onnistui ->TehtavaHakuOnnistui
         :epaonnistui ->HakuEpaonnistui
         :onnistui-parametrit [parametrit]
         :paasta-virhe-lapi? true})
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
    (assoc-in app [:valinnat :samat-tuleville] samat?))

  JokaVuosiErikseenKlikattu
  (process-event [{:keys [ruksittu? vanhempi id]} app]
    (let [vuosi (-> @tiedot/yleiset
                  :urakka
                  :alkupvm
                  pvm/vuosi)
          loppuvuosi (-> @tiedot/yleiset
                       :urakka
                       :loppupvm
                       pvm/vuosi)
          sopimuksen-tehtavamaara (get-in @taulukko-tila [:maarat vanhempi id :sopimuksen-tehtavamaara])]
      (if-not ruksittu?
        (tallenna-sopimuksen-tehtavamaara
          app
          {:tehtava id
           :vuosi vuosi
           :samat? (not (true? ruksittu?))
           :maara sopimuksen-tehtavamaara})        
        (do
          (doseq [vuosi (range vuosi loppuvuosi)]
            (tallenna-sopimuksen-tehtavamaara
              app
              {:tehtava id
               :vuosi vuosi
               :samat? (not (true? ruksittu?))
               :maara (get-in @taulukko-tila [:maarat vanhempi id :sopimuksen-tehtavamaarat vuosi])}))
          app)))))
