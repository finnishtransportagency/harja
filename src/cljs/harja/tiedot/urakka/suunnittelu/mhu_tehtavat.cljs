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
        sovitut-maarat (when sopimuksen-tehtavamaarat-kaytossa? (get rivi :sopimuksen-tehtavamaara))
        syotetyt-maarat-yhteensa (when sopimuksen-tehtavamaarat-kaytossa? (reduce (r/partial summaa-maarat maarat-tahan-asti) 0 hoitokaudet))]
    [(:id rivi) (merge  
                  (assoc rivi 
                    :hoitokausi hoitokausi
                    :maara (get-in rivi [:maarat hoitokausi]))
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

(defn luo-taulukon-rakenne-ja-liita-tiedot
  [{:keys [hoitokausi]} {:keys [nimi id tehtavat]}]
  {:nimi nimi 
   :sisainen-id id
   :nayta-toimenpide? true
   :atomi (r/atom 
            (into 
              {} 
              (map (r/partial map->id-map-maaralla hoitokausi)) 
              tehtavat))})

(defn hoitokauden-rivin-maarat
  [hoitokausi [id rivi]]
  [id (assoc rivi :maara (get (:maarat rivi) hoitokausi))])

(defn toimenpiteen-tehtavien-maarat-taulukolle-hoitokauden-tiedoilla
  [hoitokausi rivit]
  (into {} (map (r/partial hoitokauden-rivin-maarat hoitokausi) rivit)))

(defn paivita-oikean-hoitokauden-tiedot
  [hoitokausi toimenpide]
  (swap! (:atomi toimenpide) (r/partial toimenpiteen-tehtavien-maarat-taulukolle-hoitokauden-tiedoilla hoitokausi))
  toimenpide)

(defn muodosta-taulukkoatomit 
  [tehtavat-ja-toimenpiteet valinnat]
  (into []  
    (map (r/partial luo-taulukon-rakenne-ja-liita-tiedot {:hoitokausi (:hoitokausi valinnat)}))
    tehtavat-ja-toimenpiteet))

(defn paivita-taulukkoatomit-valitulle-tasolle
  [taulukon-atomit valinnat]
  (into [] (comp 
             (map (r/partial nayta-valittu-toimenpide-tai-kaikki (-> valinnat :toimenpide :id))) 
             (map (r/partial paivita-oikean-hoitokauden-tiedot (-> valinnat :hoitokausi))))
    taulukon-atomit))

(defn etsi-oikea-toimenpide 
  [{:keys [vanhempi id]} {:keys [sisainen-id atomi]}] 
  (when (= sisainen-id vanhempi)
    (get @atomi id)))

(defn hae-vanha-tehtavarivi
  [atomit rivi]
  (let [{:keys [vanhempi id]} rivi]
    (some (r/partial etsi-oikea-toimenpide {:vanhempi vanhempi :id id})
      atomit)))

(defn paivita-maarat-ja-laske-sovitut
  [{:keys [sopimuksen-tehtavamaara maarat id]} atomi]
  (let [atomi 
        (assoc-in atomi [id :maarat] maarat)
        maarat (get-in atomi [id :maarat])
        maarat-yhteensa (reduce (r/partial summaa-maarat maarat) 0 (keys maarat))]
    (assoc-in atomi [id :sovittuja-jaljella] (sovittuja-jaljella sopimuksen-tehtavamaara maarat-yhteensa))))

(defn hae-toimenpide-ja-paivita-maarat 
  [{:keys [vanhempi sopimuksen-tehtavamaara maarat id]} {:keys [sisainen-id] :as nimi-atomi}]
  (when (= sisainen-id vanhempi)
    (update nimi-atomi :atomi
      swap!  
      (r/partial paivita-maarat-ja-laske-sovitut {:id id :maarat maarat :sopimuksen-tehtavamaara sopimuksen-tehtavamaara}))
    true))

(defn paivita-sovitut-jaljella-sarake-atomit
  [atomit rivi]
  (some (r/partial hae-toimenpide-ja-paivita-maarat rivi) 
    atomit))

(defn vain-taso-3 
  [data]
  (= 3 (:taso data)))

(def valitason-toimenpiteet
  (filter vain-taso-3))

(defn sopimus-maara-syotetty 
  [r]
  (let [{:keys [yksikko sopimuksen-tehtavamaara]} (second r)] 
    (or
      (or (nil? yksikko)
        (= "" yksikko)
        (= "-" yksikko))
      (some? sopimuksen-tehtavamaara))))

(defn toimenpiteet-sopimuksen-tehtavamaarat-syotetty
  [{:keys [nimi atomi]}]
  (every? sopimus-maara-syotetty @atomi))

(defn tarkista-sovitut-maarat
  [app]
  (every? toimenpiteet-sopimuksen-tehtavamaarat-syotetty (:taulukon-atomit app)))

(defn syotetty-maara-tuleville-vuosille 
  [tehtava hoitokausi]
  (update tehtava :maarat assoc hoitokausi (:maara tehtava)))

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
    (update-in app [:valinnat :noudetaan] dec))
  SopimuksenTilaHaettu
  (process-event 
    [{:keys [vastaus]} {:keys [taulukon-atomit] :as app}]
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
    app)
  SopimuksenTehtavaTallennusEpaonnistui
  (process-event 
    [{:keys [vastaus]} app]
    app)
  TallennaSopimuksenTehtavamaara
  (process-event 
    [{{:keys [sopimuksen-tehtavamaara id] :as tehtava} :tehtava} app]
    (-> app
      (tuck-apurit/post! :tallenna-sopimuksen-tehtavamaara
        {:urakka-id (-> @tiedot/yleiset :urakka :id)
         :tehtava-id id
         :maara sopimuksen-tehtavamaara}
        {:onnistui ->SopimuksenTehtavaTallennusOnnistui
         :epaonnistui ->SopimuksenTehtavaTallennusEpaonnistui})))
  TallennaTehtavamaara
  (process-event
    [{tehtava :tehtava} {{samat-tuleville? :samat-tuleville :keys [hoitokausi] :as valinnat} :valinnat taulukon-atomit :taulukon-atomit :as app}]
    (let [{:keys [id maara]} tehtava
          urakka-id (-> @tiedot/yleiset :urakka :id)
          vanha-rivi (when sopimuksen-tehtavamaarat-kaytossa? 
                       (hae-vanha-tehtavarivi taulukon-atomit tehtava))
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
    (let [toimenpide {:nimi "0 KAIKKI" :id :kaikki} 
          maarat-tehtavilla (reduce 
                              maarille-tehtavien-tiedot
                              {}
                              (apply concat (mapv :tehtavat tehtavat)))]
      
      (-> app
        (assoc :tehtavat-ja-toimenpiteet tehtavat)
        (assoc :taulukon-atomit (muodosta-taulukkoatomit tehtavat valinnat))
        (assoc :maarat maarat-tehtavilla)
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
    [{:keys [arvo taso]} {:keys [tehtavat-taulukko taulukon-atomit tehtavat-ja-toimenpiteet maarat valinnat] :as app}]
    (as-> app a      
      (assoc-in a [:valinnat taso] arvo)
      (assoc a :taulukon-atomit (paivita-taulukkoatomit-valitulle-tasolle taulukon-atomit (:valinnat a)))))
  SamatTulevilleMoodi
  (process-event [{:keys [samat?]} app]
    (assoc-in app [:valinnat :samat-tuleville] samat?)))
