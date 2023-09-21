(ns harja.tiedot.urakka.suunnittelu.mhu-tehtavat
  (:require [tuck.core :refer [process-event] :as tuck]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.tiedot.urakka :as urakka]
            [harja.ui.viesti :as viesti]
            [reagent.core :as r]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.asiakas.kommunikaatio :as k]))

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
(defrecord TestiTallennaKaikkiinTehtaviinArvo [parametrit])
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
  (let [suunnitellut-maarat (get rivi :suunnitellut-maarat)
        hoitokaudet (range 
                      (-> @tiedot/yleiset :urakka :alkupvm pvm/vuosi) 
                      (-> @tiedot/yleiset :urakka :loppupvm pvm/vuosi))
        sopimuksen-maarat (reduce (r/partial summaa-maarat (get rivi :sopimuksen-tehtavamaarat)) 0 hoitokaudet)
        syotetyt-maarat-yhteensa (reduce (r/partial summaa-maarat suunnitellut-maarat) 0 hoitokaudet)]
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
      :sopimuksen-tehtavamaarat-yhteensa sopimuksen-maarat
      :sovittuja-jaljella (sovittuja-jaljella sopimuksen-maarat syotetyt-maarat-yhteensa))))

(defn- map->id-map-maaralla
  [hoitokausi rivi]
  (let [{:keys [samat-maarat-vuosittain? aluetieto?]} rivi
        sopimuksen-aluetietomaara (first (vals (:sopimuksen-aluetieto-maara rivi)))
        muuttunut-tarjouksesta? (if aluetieto?
                                  (not= (get-in rivi [:suunnitellut-maarat hoitokausi]) sopimuksen-aluetietomaara)
                                  true)]
    [(:id rivi)
     (cond-> rivi
       true (assoc  
              :hoitokausi hoitokausi
              :joka-vuosi-erikseen? (if (some? samat-maarat-vuosittain?)
                                      (not samat-maarat-vuosittain?)
                                      false))
       muuttunut-tarjouksesta? (assoc :maara-muuttunut-tarjouksesta (get-in rivi [:suunnitellut-maarat hoitokausi])) ;; Hoitovuoden suunniteltu määrä (input kenttä)
       aluetieto? (assoc :sopimus-maara sopimuksen-aluetietomaara)
       (not aluetieto?) (assoc :sopimus-maara (get-in rivi [:sopimuksen-tehtavamaarat hoitokausi]))
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
  (let [muuttunut-tarjouksesta? (if (:aluetieto? tehtava)
                                  (not= (get-in tehtava [:sopimuksen-aluetieto-maara hoitokausi]) (get-in tehtava [:suunnitellut-maarat hoitokausi]))
                                  ;; Suunniteltavat määrät ovat aina "muuttuneet" tarjouksesta
                                  true)]
    [id (assoc tehtava :maara-muuttunut-tarjouksesta (if muuttunut-tarjouksesta?
                                                      (get-in tehtava [:suunnitellut-maarat hoitokausi])
                                                      nil))]))

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

(defn- paivita-maarat-ja-laske-sovitut
  [{:keys [suunnitellut-maarat sopimuksen-tehtavamaarat] :as taulukon-tila}]
  (let [taulukkorakenne (assoc-in taulukon-tila [:suunnitellut-maarat] suunnitellut-maarat)
        suunnitellut-maarat-yhteensa (reduce (r/partial summaa-maarat suunnitellut-maarat) 0 (keys suunnitellut-maarat))
        sovitut-maarat (reduce (r/partial summaa-maarat sopimuksen-tehtavamaarat) 0 (keys sopimuksen-tehtavamaarat))
        sovittuja-jaljella-yht (sovittuja-jaljella sovitut-maarat suunnitellut-maarat-yhteensa)]
    (assoc-in taulukkorakenne [:sovittuja-jaljella] sovittuja-jaljella-yht)))

(defn paivita-sovitut-jaljella-sarake
  [taulukon-tila {:keys [vanhempi id] :as tehtava} samat-tuleville? hoitokauden-alkuvuosi urakan-loppuvuosi]
  (let [nykyiset-suunnitellut-maarat (get-in taulukon-tila [:maarat vanhempi id :suunnitellut-maarat])
        uusi-arvo (get-in taulukon-tila [:maarat vanhempi id :maara-muuttunut-tarjouksesta])
        kasiteltavat-vuodet (if samat-tuleville?
                              (range hoitokauden-alkuvuosi urakan-loppuvuosi)
                              [hoitokauden-alkuvuosi])
        uudet-suunnitellut-maarat (reduce (fn [lopputulos vuosi]
                                            (assoc-in lopputulos [vuosi] uusi-arvo))
                                    nykyiset-suunnitellut-maarat kasiteltavat-vuodet)
        taulukon-tila (assoc-in taulukon-tila [:maarat vanhempi id :suunnitellut-maarat] uudet-suunnitellut-maarat)]
    (-> taulukon-tila
      (update-in [:maarat vanhempi id] paivita-maarat-ja-laske-sovitut))))

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

(defn- sopimusmaarat-taulukosta [taulukko tyyppi]
  (map #(select-keys % [:sopimus-maara :sopimuksen-tehtavamaarat])
          (flatten (map vals (vals (tyyppi taulukko))))))

(defn aluetietoja-puuttuu? []
  (let [keratyt-aluemaarat (sopimusmaarat-taulukosta @taulukko-tila :alueet)
        puutteita-aluetiedoissa? (boolean (some nil? (map :sopimus-maara keratyt-aluemaarat)))]
    puutteita-aluetiedoissa?))

(defn maaratietoja-puuttuu?
  []
  (let [keratyt-tehtavamaarat (sopimusmaarat-taulukosta @taulukko-tila :maarat)
        ;; kerätään ne rivit ensin, joissa kaikille vuosille saman arvon asettava kenttä on nil
        puutteita-sopimusmaarissa? (keep #(when (nil? (:sopimus-maara %))
                                            (identity %)) keratyt-tehtavamaarat)
        ;; näistä kelvollisia ovat ne, joilla on kaikille hoitokausille vuosikohtainen arvo
        ;; sitä on kuitenkin tarkasteltava vielä erikseen
        on-riveja-joilta-maaratieto-puuttuu? (keep #(or
                                                      (nil? (:sopimuksen-tehtavamaarat %))
                                                      (when
                                                        (map? (:sopimuksen-tehtavamaarat %))
                                                        ;; vaaditaan että vuosikohtaisissa on jokaiselle MHU:n vuodelle arvo
                                                        (when (< (count (vals (:sopimuksen-tehtavamaarat %))) (count @urakka/valitun-urakan-hoitokaudet))
                                                          (identity %)))) puutteita-sopimusmaarissa?)]
    (boolean (seq on-riveja-joilta-maaratieto-puuttuu?))))

(defn syotetty-maara-tuleville-vuosille 
  [tehtava hoitokausi]
  (update tehtava :maarat assoc hoitokausi (:maara tehtava)))

(defn tayta-vuodet [sopimuksen-tehtavamaara vuosi] 
  [vuosi sopimuksen-tehtavamaara])

(defn paivita-vuosien-maarat 
  [taulukko-tila {:keys [id vanhempi sopimus-maara joka-vuosi-erikseen? hoitokausi]}]
  (if joka-vuosi-erikseen?
    (assoc-in taulukko-tila [:maarat vanhempi id :sopimuksen-tehtavamaarat hoitokausi] sopimus-maara)
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
          (map (r/partial tayta-vuodet sopimus-maara))
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
    (do
      (reset! taulukko-virheet {})
      (tuck-apurit/post! :tallenna-sopimuksen-tila
        {:urakka-id (-> @tiedot/yleiset :urakka :id)
         :tallennettu tallennettu}
        {:onnistui ->SopimuksenTallennusOnnistui
         :epaonnistui ->SopimuksenTallennusEpaonnistui})
      (update-in app [:valinnat :noudetaan] inc)))

  TestiTallennaKaikkiinTehtaviinArvo
  (process-event
    [{parametrit :parametrit} app]
    (when (k/kehitysymparistossa?)
      (tallenna
        app
        {:polku :tallenna-sopimuksen-tehtavamaara-kaikille-tehtaville-test
         :parametrit
         {:onnistui ->TehtavaHakuOnnistui
          :epaonnistui ->HakuEpaonnistui
          :onnistui-parametrit [parametrit]
          :paasta-virhe-lapi? true}}
        {:urakka-id (-> @tiedot/yleiset :urakka :id)})))

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
    (let [onnistunut-tehtava-id (:harja.domain.toimenpidekoodi/id (first vastaus))
          virheet (dissoc @taulukko-virheet onnistunut-tehtava-id)]
      (reset! taulukko-virheet virheet)
      (dissoc app :tallennettava)))

  SopimuksenTehtavaTallennusEpaonnistui
  (process-event 
    [{:keys [vastaus]} {:keys [tallennettava] :as app}]
    (viesti/nayta-toast! "Tallennus epäonnistui" :varoitus)
    (let [{:keys [id]} tallennettava
          virheet (assoc-in {} [id :sopimus-maara] ["Tallennus epäonnistui"])]
      (reset! taulukko-virheet virheet))      
    (dissoc app :tallennettava))

  TallennaMuuttunutAluemaara
  (process-event
    [{tehtava :tehtava} {{samat-tuleville? :samat-tuleville :keys [hoitokausi] :as valinnat} :valinnat taulukko :taulukko :as app}]
    (let [{:keys [id maara-muuttunut-tarjouksesta vanhempi]} tehtava
          urakan-vuodet (range
                                   hoitokausi
                                   (-> @tiedot/yleiset :urakka :loppupvm pvm/vuosi))]
      ;; Päivitä myös viewin käyttämä atomi
      (doseq [vuosi urakan-vuodet]
        (swap! taulukko-tila assoc-in [:alueet vanhempi id :suunnitellut-maarat vuosi] maara-muuttunut-tarjouksesta))
      (-> app
        (tallenna-tehtavamaarat
          {:hoitokausi hoitokausi
           :tehtavamaarat (into [] (map
                                     #(-> {:tehtava-id id
                                           :hoitokauden-alkuvuosi %
                                           :maara maara-muuttunut-tarjouksesta}))
                            urakan-vuodet)})
        (update :valinnat
          assoc
          :virhe-tallennettaessa false
          :tallennetaan true))))

  TallennaSopimuksenAluemaara
  (process-event
    [{:keys [tehtava]} app]
    (let [{:keys [id sopimus-maara]} tehtava]
      (-> app                             
        (assoc :tallennettava tehtava)
        (tallenna-sopimuksen-tehtavamaara {:maara sopimus-maara
                                           :vuosi (-> @tiedot/yleiset :urakka :alkupvm pvm/vuosi)
                                           :tehtava id
                                           :samat? false}))))

  TallennaSopimuksenTehtavamaara
  (process-event 
    [{{:keys [sopimus-maara id vanhempi joka-vuosi-erikseen? hoitokausi] :as tehtava} :tehtava} {:keys [taulukko] :as app}]
    (swap! taulukko-tila paivita-vuosien-maarat tehtava)
    (-> app                             
      (assoc :tallennettava tehtava)
      (tallenna-sopimuksen-tehtavamaara {:maara sopimus-maara
                                         :samat? (not (true? joka-vuosi-erikseen?))
                                         :tehtava id
                                         :vuosi hoitokausi})))

  TallennaTehtavamaara
  (process-event
    [{tehtava :tehtava} {{samat-tuleville? :samat-tuleville :keys [hoitokausi] :as valinnat} :valinnat taulukko :taulukko :as app}]
    (let [{:keys [id maara-muuttunut-tarjouksesta]} tehtava
          urakan-loppuvuosi (-> @tiedot/yleiset :urakka :loppupvm pvm/vuosi)
          tehtava (if samat-tuleville? 
                    (reduce syotetty-maara-tuleville-vuosille tehtava (range hoitokausi urakan-loppuvuosi))
                    (-> tehtava
                      (assoc :hoitokausi hoitokausi)
                      (update :maarat assoc hoitokausi (:maara tehtava))))]
      (if samat-tuleville?
        (doseq [vuosi (mapv (comp keyword str)
                        (range hoitokausi urakan-loppuvuosi))]
          (tallenna-tehtavamaarat app {:hoitokausi hoitokausi
                                       :tehtavamaarat [{:tehtava-id id
                                                        :maara maara-muuttunut-tarjouksesta
                                                        :hoitokauden-alkuvuosi (-> vuosi
                                                                                 name
                                                                                 js/parseInt)}]}))
        (tallenna-tehtavamaarat app {:hoitokausi hoitokausi
                                     :tehtavamaarat [{:tehtava-id id
                                                      :maara maara-muuttunut-tarjouksesta
                                                      :hoitokauden-alkuvuosi hoitokausi}]})) 
      (swap! taulukko-tila paivita-sovitut-jaljella-sarake tehtava samat-tuleville? hoitokausi urakan-loppuvuosi)
      
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
    (let [vuosi (-> @tiedot/yleiset :urakka :alkupvm pvm/vuosi)
          loppuvuosi (-> @tiedot/yleiset :urakka :loppupvm pvm/vuosi)
          sopimuksen-tehtavamaara (get-in @taulukko-tila [:maarat vanhempi id :sopimus-maara])
          sopimuksen-tehtavamaarat (get-in @taulukko-tila [:maarat vanhempi id :sopimuksen-tehtavamaarat])
          sopimuksen-tehtavamaara (if (nil? sopimuksen-tehtavamaara)
                                    (let [uusi-tehtavamaara (first (filter #(when-not (nil? %) %)
                                                                     (vals sopimuksen-tehtavamaarat)))
                                          _ (swap! taulukko-tila assoc-in [:maarat vanhempi id :sopimus-maara] uusi-tehtavamaara)]
                                      uusi-tehtavamaara)
                                    sopimuksen-tehtavamaara)
          ;; Päivitä yhdestä arvosta jokaiselle vuodelle arvo, jos ruksittu?
          _ (when (and ruksittu? (not (nil? sopimuksen-tehtavamaara)))
              (swap! taulukko-tila assoc-in [:maarat vanhempi id :sopimuksen-tehtavamaarat]
                (reduce (fn [lopputulos vuosi] (assoc-in lopputulos [vuosi] sopimuksen-tehtavamaara))
                  sopimuksen-tehtavamaarat (range vuosi loppuvuosi))))]

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
