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

(def toimenpiteet #{:talvihoito
                    :liikenneympariston-hoito
                    :sorateiden-hoito
                    :paallystepaikkaukset
                    :mhu-yllapito
                    :mhu-korvausinvestointi})

(defonce sopimuksen-tehtavamaarat-kaytossa? false)
(def sopimuksen-tehtavamaara-mock-arvot {:fizz 300
                                         :buzz 500
                                         :fizzbuzz 1000
                                         :else 250})

(defn maarille-tehtavien-tiedot
  [maarat-map {:keys [hoitokauden-alkuvuosi tehtava-id maara]}]
  (if-not (nil? hoitokauden-alkuvuosi)
    (assoc-in maarat-map [tehtava-id hoitokauden-alkuvuosi] maara)
    maarat-map))

(defn sopimuksen-maarille-tehtavien-tiedot
  [maarat-map {:keys [tehtava-id] :as rivi}]
  (assoc-in maarat-map [tehtava-id] rivi))

(defn- sovittuja-jaljella
  [sovitut-maarat syotetyt-maarat-yhteensa]
  (str 
    (fmt/piste->pilkku (- sovitut-maarat syotetyt-maarat-yhteensa))
    " (" 
    (fmt/prosentti (* 
                     (/ 
                       (- sovitut-maarat syotetyt-maarat-yhteensa) 
                       sovitut-maarat) 
                     100)) 
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
                          :maara (get-in maarat [(:id rivi) hoitokausi]))
                  (when sopimuksen-tehtavamaarat-kaytossa? {:sovittuja-jaljella (sovittuja-jaljella sovitut-maarat syotetyt-maarat-yhteensa)}))]))

(defn liita-sopimusten-tiedot 
  [sopimusten-maarat rivi]
  (let [{:keys [maara]} (get sopimusten-maarat (:id rivi))]
       (assoc rivi :sopimuksen-maara maara)))

(defn nayta-valittu-toimenpide-tai-kaikki
  [valittu-toimenpide {:keys [id]}]  
  (or 
    (= :kaikki valittu-toimenpide) 
    (= id valittu-toimenpide)))

(defn luo-rakenne-ja-liita-tiedot
  [{:keys [hoitokausi sopimusten-maarat maarat-tehtavilla]} {:keys [nimi id tehtavat]}]
                    {:nimi nimi 
                     :sisainen-id id
                     :atomi (r/atom 
                              (into 
                                {} 
                                (comp 
                                  (map (if sopimuksen-tehtavamaarat-kaytossa? 
                                         (r/partial liita-sopimusten-tiedot sopimusten-maarat)
                                         identity))
                                  (map (r/partial map->id-map-maaralla maarat-tehtavilla hoitokausi))) 
                                tehtavat))})

(defn muodosta-atomit 
  [tehtavat-ja-toimenpiteet valinnat maarat-tehtavilla sopimusten-maarat]
  (into [] (comp 
             (filter (r/partial nayta-valittu-toimenpide-tai-kaikki (-> valinnat :toimenpide :id))) 
             (map (r/partial luo-rakenne-ja-liita-tiedot {:hoitokausi (:hoitokausi valinnat)
                                                          :maarat-tehtavilla maarat-tehtavilla
                                                          :sopimusten-maarat sopimusten-maarat})))
    tehtavat-ja-toimenpiteet))

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
    (mapv (fn [{:keys [sisainen-id] :as nimi-atomi}]
            (when (= sisainen-id vanhempi)
              (update nimi-atomi :atomi
                swap!  
                assoc-in [id :sovittuja-jaljella] (sovittuja-jaljella sopimuksen-maara maara)))
            nimi-atomi
            ) atomit)))

(def valitason-toimenpiteet
  (filter
    (fn [data]
      (= 3 (:taso data)))))

(extend-protocol tuck/Event
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
      (-> app 
        (assoc-in [:maarat id hoitokausi] maara)
        (assoc-in [:valinnat :vanha-rivi] vanha-rivi)
        (update :taulukon-atomit (if sopimuksen-tehtavamaarat-kaytossa? 
                                   paivita-sovitut-jaljella-sarake-atomit
                                   (constantly taulukon-atomit)) tehtava)
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
          {hoitokausi         :hoitokausi} parametrit]
      
      (-> app
        (assoc :tehtavat-ja-toimenpiteet tehtavat)
        (update :valinnat #(assoc % :noudetaan (do
                                                 (tuck-apurit/post! :tehtavamaarat-hierarkiassa
                                                   {:urakka-id             urakka-id
                                                    :hoitokauden-alkuvuosi (or hoitokausi
                                                                             (:hoitokausi valinnat)
                                                                             alkuvuosi)}
                                                   
                                                   {:onnistui           ->MaaraHakuOnnistui
                                                    :epaonnistui        ->HakuEpaonnistui
                                                    :paasta-virhe-lapi? true})
                                                 (:noudetaan %))
                             :toimenpide-valikko-valinnat (sort-by :nimi 
                                                            (concat 
                                                              [{:nimi "0 KAIKKI" :id :kaikki}] 
                                                              (into [] 
                                                                valitason-toimenpiteet 
                                                                tehtavat)))
                             :hoitokausi (pvm/hoitokauden-alkuvuosi-nykyhetkesta (pvm/nyt))
                             :toimenpide toimenpide)))))
  MaaraHakuOnnistui
  (process-event
    [{:keys [maarat]} {:keys [tehtavat-ja-toimenpiteet tehtavat-taulukko valinnat sopimuksen-maarat] :as app}]
    (let [maarat-tehtavilla (reduce 
                              maarille-tehtavien-tiedot
                              {}
                              maarat)]
      (-> app
        (assoc :taulukon-atomit (muodosta-atomit tehtavat-ja-toimenpiteet valinnat maarat-tehtavilla sopimuksen-maarat))
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
        (tuck-apurit/post! :tehtavat
                           {:urakka-id (:id (-> @tiedot/tila :yleiset :urakka))}
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
    (let [urakka-id (-> @tiedot/tila :yleiset :urakka :id)
          ;; jos alla olevat unohtuu poistaa, niin huomauta
          ;; devausta varten ilman bäkkipalveluita
          feikki-payload (fn [tehtavat]
                           (let [flatattu 
                                 (reduce (fn [acc r]
                                           (concat acc (:tehtavat r)))
                                   []
                                   tehtavat)]
                             (into [] 
                               (map (fn [rivi] {:id (gensym 1) :urakka urakka-id :maara (get sopimuksen-tehtavamaara-mock-arvot (cond (and 
                                                     (zero? (rem (:id rivi) 5))
                                                     (zero? (rem (:id rivi) 3)))
                                                 
                                                   :fizzbuzz

                                                   (zero? (rem (:id rivi) 5))
                                                   :buzz

                                                   (zero? (rem (:id rivi) 3))
                                                   :fizz
                                                   
                                                   :else
                                                   :else)) :tehtava-id (:id rivi)}))
                               flatattu)))
          feikki-post! (fn [_ _ _ {:keys [onnistui epaonnistui onnistuiko? payload]}]
                         (let [e! (tuck/current-send-function)]
                           (if onnistuiko? 
                             (e! (onnistui payload))
                             (e! (epaonnistui payload)))))]
      (-> app (feikki-post! :sopimuksen-tehtavamaarat
                {:urakka-id urakka-id}
                {:onnistui ->SopimuksenHakuOnnistui
                 :epaonnistui ->HakuEpaonnistui
                 :onnistuiko? true
                 :payload (feikki-payload tehtavat-ja-toimenpiteet)}))))
  ValitseTaso
  (process-event
    [{:keys [arvo taso]} {:keys [tehtavat-taulukko tehtavat-ja-toimenpiteet maarat valinnat sopimuksen-maarat] :as app}]
    (as-> app a      
      (assoc-in a [:valinnat taso] arvo)
      (assoc a :taulukon-atomit (muodosta-atomit tehtavat-ja-toimenpiteet (:valinnat a) maarat sopimuksen-maarat))))
  SamatTulevilleMoodi
  (process-event [{:keys [samat?]} app]
    (assoc-in app [:valinnat :samat-tuleville] samat?)))
