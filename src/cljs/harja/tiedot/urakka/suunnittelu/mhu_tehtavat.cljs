(ns harja.tiedot.urakka.suunnittelu.mhu-tehtavat
  (:require [tuck.core :refer [process-event] :as tuck]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.ui.viesti :as viesti]
            [reagent.core :as r]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]))

(defrecord ValitseTaso [arvo taso])
(defrecord HaeTehtavat [parametrit])
(defrecord TehtavaHakuOnnistui [tehtavat parametrit])
(defrecord HakuEpaonnistui [])
(defrecord MaaraHakuOnnistui [maarat prosessoi-tulokset])
(defrecord TehtavaTallennusOnnistui [vastaus])
(defrecord TehtavaTallennusEpaonnistui [vastaus])
(defrecord TallennaTehtavamaara [tehtava])
(defrecord HaeMaarat [parametrit])
(defrecord SamatTulevilleMoodi [samat?])
(defrecord HaeSopimuksenTiedot [])
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

(def toimenpiteet #{:talvihoito
                    :liikenneympariston-hoito
                    :sorateiden-hoito
                    :paallystepaikkaukset
                    :mhu-yllapito
                    :mhu-korvausinvestointi})

(defonce sopimuksen-tehtavamaarat-kaytossa? true)
(def sopimuksen-tehtavamaara-mock-arvot {:fizz 300
                                         :buzz 500
                                         :fizzbuzz 1000
                                         :else 250})

(defn maarille-tehtavien-tiedot
  [maarat-map {:keys [id maarat] :as _r}]
  (println "gigels " _r id maarat)
  (assoc-in maarat-map [id] maarat))

(defn sopimuksen-maarille-tehtavien-tiedot
  [maarat-map {:keys [tehtava-id] :as rivi}]
  (assoc-in maarat-map [tehtava-id] rivi))

(defn- sovittuja-jaljella
  [sovitut-maarat syotetyt-maarat-yhteensa]
  (when sovitut-maarat (str 
    (fmt/piste->pilkku (- sovitut-maarat syotetyt-maarat-yhteensa))
    " (" 
    
    (fmt/prosentti (* 
                     (/ 
                       (- sovitut-maarat syotetyt-maarat-yhteensa) 
                       sovitut-maarat) 
                     100))) 
    ")"))

(defn- summaa-maarat
  [maarat-tahan-asti summa vuosi]
  (+ summa (get maarat-tahan-asti vuosi)))

(defn- map->id-map-maaralla
  [maarat hoitokausi rivi]
  (let [maarat-tahan-asti (get maarat (:id rivi))
        hoitokaudet (range 
                      (-> @tiedot/yleiset :urakka :alkupvm pvm/vuosi) 
                      (-> @tiedot/yleiset :urakka :loppupvm pvm/vuosi))
        sovitut-maarat (when sopimuksen-tehtavamaarat-kaytossa? (get rivi :sopimuksen-maara))
        syotetyt-maarat-yhteensa (when sopimuksen-tehtavamaarat-kaytossa? (reduce (r/partial summaa-maarat maarat-tahan-asti) 0 hoitokaudet))]
    [(:id rivi) (merge  (assoc rivi 
                          :hoitokausi hoitokausi
                          :maarat maarat-tahan-asti
                          :maara (get-in maarat [(:id rivi) hoitokausi]))
                  (when sopimuksen-tehtavamaarat-kaytossa? {:sovittuja-jaljella (sovittuja-jaljella sovitut-maarat syotetyt-maarat-yhteensa)}))]))

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

(defn luo-rakenne-ja-liita-tiedot
  [{:keys [hoitokausi maarat-tehtavilla]} {:keys [nimi id tehtavat]}]
                    {:nimi nimi 
                     :sisainen-id id
                     :nayta-toimenpide? true
                     :atomi (r/atom 
                              (into 
                                {} 
                                (map (r/partial map->id-map-maaralla maarat-tehtavilla hoitokausi)) 
                                tehtavat))})

(defn muodosta-atomit 
  [tehtavat-ja-toimenpiteet valinnat maarat-tehtavilla]
  (into [] (comp 
             #_(map (r/partial nayta-valittu-toimenpide-tai-kaikki (-> valinnat :toimenpide :id))) 
             (map (r/partial luo-rakenne-ja-liita-tiedot {:hoitokausi (:hoitokausi valinnat)
                                                          :maarat-tehtavilla maarat-tehtavilla})))
    tehtavat-ja-toimenpiteet))

(defn paivita-atomit-hoitokaudelle
  [taulukon-atomit valinnat]
  (println valinnat)
  (into [] (comp 
             (map (r/partial nayta-valittu-toimenpide-tai-kaikki (-> valinnat :toimenpide :id))) 
             #_(map (r/partial luo-rakenne-ja-liita-tiedot {:hoitokausi (:hoitokausi valinnat)
                                                          :maarat-tehtavilla maarat-tehtavilla})))
    taulukon-atomit))

(defn etsi-oikea-toimenpide 
  [{:keys [vanhempi id]} {:keys [sisainen-id atomi]}] 
  (when (= sisainen-id vanhempi)
    (get @atomi id)))

(defn hae-vanha-rivi
  [atomit rivi]
  (let [{:keys [vanhempi id]} rivi]
    (some (r/partial etsi-oikea-toimenpide {:vanhempi vanhempi :id id})
      atomit)))

(defn paivita-sovitut-jaljella-sarake-atomit
  [atomit rivi]
  (let [{:keys [vanhempi sopimuksen-maara maara id]} rivi]
    (some (fn [{:keys [sisainen-id] :as nimi-atomi}]
            (when (= sisainen-id vanhempi)
              (update nimi-atomi :atomi
                swap!  
                assoc-in [id :sovittuja-jaljella] (sovittuja-jaljella sopimuksen-maara maara))
              true)) 
      atomit)))

(def valitason-toimenpiteet
  (filter
    (fn [data]
      (= 3 (:taso data)))))

(defn sopimus-maara-syotetty 
  [r]
  (println (-> r second))
  (let [{:keys [yksikko sopimus-maara]} (second r)] 
    (or 
      (or (nil? yksikko)
        (= "" yksikko)
        (= "-" yksikko))
      (some? sopimus-maara))))

(defn toimenpiteet-sopimuksen-tehtavamaarat-syotetty
  [{:keys [nimi atomi]}]
  (println nimi)
  (every? sopimus-maara-syotetty @atomi))

(defn tarkista-sovitut-maarat
  [app]
  (every? toimenpiteet-sopimuksen-tehtavamaarat-syotetty (:taulukon-atomit app)))

(extend-protocol tuck/Event
  SopimuksenTallennusOnnistui
  (process-event 
    [{:keys [vastaus]} app]
    (println "tallennettu" vastaus)
    (viesti/nayta! "Tallennus onnistui")
    (-> app 
      (assoc :sopimukset-syotetty? (:tallennettu vastaus))
      (update-in [:valinnat :noudetaan] dec)))
  SopimuksenTallennusEpaonnistui
  (process-event 
    [{:keys [vastaus]} app]
    (viesti/nayta! "Sopimuksen määrien tallennus epäonnistui" :danger)
    (println "ei tallennettu" vastaus)
    (update-in app [:valinnat :noudetaan] dec))
  TallennaSopimus
  (process-event
    [{:keys [tallennettu]} app]
    (let [kaikki-arvot-syotetty? (tarkista-sovitut-maarat app)] 
      (if kaikki-arvot-syotetty? 
        (do 
          (tuck-apurit/post! :tallenna-sopimuksen-tila
              {:urakka-id (-> @tiedot/yleiset :urakka :id)
               :tallennettu tallennettu}
              {:onnistui ->SopimuksenTallennusOnnistui
               :epaonnistui ->SopimuksenTallennusEpaonnistui})
          (update-in app [:valinnat :noudetaan] inc))
        (do
          (viesti/nyata! "Sopimuksen määrien tallennus epäonnistui. Tarkista, että olet syöttänyt joka kohtaan vähintään 0!" :danger 7000)
          app))))
  SopimuksenTilaEiHaettu
  (process-event 
    [{:keys [vastaus]} app]
    (viesti/nayta! "Sopimuksen määrien tilan tarkastus epäonnistui!" :danger)
    (println "ei haeettu" vastaus)
    (update-in app [:valinnat :noudetaan] dec))
  SopimuksenTilaHaettu
  (process-event 
    [{:keys [vastaus]} app]
    (println "haeettu" vastaus)
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
    (let [vanha-rivi (-> app :valinnat :vanha-rivi)] 
      (-> app 
        (update :taulukon-atomit (if sopimuksen-tehtavamaarat-kaytossa? 
                                   paivita-sovitut-jaljella-sarake-atomit
                                   (constantly (:taulukon-atomit app))) vanha-rivi)
        (update :valinnat dissoc :vanha-rivi)
        (assoc-in [:valinnat :virhe-tallennettaessa] true)
        (assoc-in [:valinnat :tallennetaan] false))))
  TehtavaTallennusOnnistui
  (process-event
    [vastaus {:keys [sopimuksen-maarat] :as app}]
    (-> app 
      (update :valinnat dissoc :vanha-rivi)
      (assoc-in [:valinnat :virhe-tallennettaessa] false)
      (assoc-in [:valinnat :tallennetaan] false)))
  SopimuksenTehtavaTallennusOnnistui
  (process-event 
    [{:keys [vastaus]} app]
    (println "sop onnistui " vastaus)
    app)
  SopimuksenTehtavaTallennusEpaonnistui
  (process-event 
    [{:keys [vastaus]} app]
    (println "sop onnistui " vastaus)
    app)
  TallennaSopimuksenTehtavamaara
  (process-event 
    [{{:keys [syotetty-sopimusmaara id] :as tehtava} :tehtava} app]
    (println "tallenna sop" tehtava (type syotetty-sopimusmaara))
    (-> app
      (tuck-apurit/post! :tallenna-sopimuksen-tehtavamaara
        {:urakka-id (-> @tiedot/yleiset :urakka :id)
         :tehtava-id id
         :maara syotetty-sopimusmaara}
        {:onnistui ->SopimuksenTehtavaTallennusOnnistui
         :epaonnistui ->SopimuksenTehtavaTallennusEpaonnistui})))
  TallennaTehtavamaara
  (process-event
    [{tehtava :tehtava} {{samat-tuleville? :samat-tuleville :keys [hoitokausi] :as valinnat} :valinnat taulukon-atomit :taulukon-atomit :as app}]
    (let [{:keys [id maara]} tehtava
          urakka-id (-> @tiedot/yleiset :urakka :id)
          vanha-rivi (when sopimuksen-tehtavamaarat-kaytossa? 
                       (hae-vanha-rivi taulukon-atomit tehtava))]
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
      (when sopimuksen-tehtavamaarat-kaytossa? 
        (paivita-sovitut-jaljella-sarake-atomit taulukon-atomit tehtava))
      (-> app 
        (assoc-in [:maarat id hoitokausi] maara)
        (assoc-in [:valinnat :vanha-rivi] vanha-rivi)
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
    (let [{urakka-id :id urakka-alkupvm :alkupvm} (-> @tiedot/tila :yleiset :urakka)
          alkuvuosi (pvm/vuosi urakka-alkupvm)
          toimenpide {:nimi "0 KAIKKI" :id :kaikki} 
          {hoitokausi :hoitokausi} parametrit
          maarat-tehtavilla (reduce 
                              maarille-tehtavien-tiedot
                              {}
                              (apply concat (mapv :tehtavat tehtavat)))]
      
      (-> app
        (assoc :tehtavat-ja-toimenpiteet tehtavat)
        (assoc :taulukon-atomit (muodosta-atomit tehtavat valinnat maarat-tehtavilla))
        (assoc :maarat maarat-tehtavilla)
        (update :valinnat #(assoc % 
                             :toimenpide-valikko-valinnat (sort-by :nimi 
                                                            (concat 
                                                              [{:nimi "0 KAIKKI" :id :kaikki}] 
                                                              (into [] 
                                                                valitason-toimenpiteet 
                                                                tehtavat)))
                             :hoitokausi (pvm/vuosi (pvm/nyt))
                             :toimenpide toimenpide)))))
  MaaraHakuOnnistui
  (process-event
    [{:keys [maarat]} {:keys [tehtavat-ja-toimenpiteet tehtavat-taulukko valinnat sopimuksen-maarat] :as app}]
    (let [maarat-tehtavilla (reduce 
                              maarille-tehtavien-tiedot
                              {}
                              (apply concat (mapv :tehtavat maarat)))]
      (-> app
        (assoc :taulukon-atomit (muodosta-atomit tehtavat-ja-toimenpiteet valinnat maarat-tehtavilla))
        (assoc :maarat maarat-tehtavilla)
        (update-in [:valinnat :noudetaan] dec))))
  SopimuksenHakuOnnistui
  (process-event
    [{:keys [tulos]} app]
    (let [sopimuksen-maarat (reduce sopimuksen-maarille-tehtavien-tiedot {} tulos)] 
      (-> app 
        (assoc :sopimuksen-maarat sopimuksen-maarat) 
        (assoc-in [:valinnat :sopimukset-syotetty?] (> (count tulos) 0)))))
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
  HaeMaarat
  (process-event
    [{:keys [parametrit]} app]
    (let [{:keys [hoitokausi]} parametrit
          {urakka-id :id urakka-alkupvm :alkupvm} (-> @tiedot/tila :yleiset :urakka)]
      (-> app
        (tuck-apurit/post! :tehtavamaarat-hierarkiassa
          {:urakka-id             urakka-id
           :hoitokauden-alkuvuosi :kaikki #_(or hoitokausi
                                              (pvm/vuosi urakka-alkupvm))}
          {:onnistui           ->MaaraHakuOnnistui
           :epaonnistui        ->HakuEpaonnistui
           :paasta-virhe-lapi? true})
        (update :valinnat #(assoc %
                             :virhe-noudettaessa false
                             :noudetaan (inc (:noudetaan %))))
        (assoc-in [:valinnat :hoitokausi] hoitokausi))))
  HaeSopimuksenTiedot
  (process-event 
    [_ {:keys [tehtavat-ja-toimenpiteet] :as app}]
    (let [urakka-id (-> @tiedot/tila :yleiset :urakka :id)]
      (-> app 
        (tuck-apurit/post! :sopimuksen-tehtavamaarat
          {:urakka-id urakka-id}
          {:onnistui ->SopimuksenHakuOnnistui
           :epaonnistui ->HakuEpaonnistui}))))
  ValitseTaso
  (process-event
    [{:keys [arvo taso]} {:keys [tehtavat-taulukko taulukon-atomit tehtavat-ja-toimenpiteet maarat valinnat] :as app}]
    (as-> app a      
      (assoc-in a [:valinnat taso] arvo)
      (assoc a :taulukon-atomit (paivita-atomit-hoitokaudelle taulukon-atomit (:valinnat a)))))
  SamatTulevilleMoodi
  (process-event [{:keys [samat?]} app]
    (assoc-in app [:valinnat :samat-tuleville] samat?)))
