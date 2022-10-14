(ns harja.tiedot.urakka.laadunseuranta.bonukset
  (:require [tuck.core :as tuck]
            [harja.ui.lomake :as lomake]
            [harja.ui.viesti :as viesti]
            [harja.ui.liitteet :as liitteet]
            
            [harja.tiedot.navigaatio :as nav]
            
            [harja.tyokalut.tuck :as tuck-apurit]))

(def konversioavaimet
  {:perintapvm :pvm
   :summa :rahasumma
   :perustelu :lisatieto
   :laji :tyyppi
   :indeksi :indeksin_nimi
   :kasittelyaika :laskutuskuukausi})

(def valitut-avaimet [:pvm :rahasumma :toimenpideinstanssi :tyyppi :lisatieto :indeksikorjaus :sijainti
                      :laskutuskuukausi :kasittelytapa :indeksin_nimi :id :suorasanktio])

(defn- reversoi-mappi
  [mappi]
  (into {}
    (map (fn [[avain arvo]]
           [arvo avain]))
    mappi))

(defn- keywordisoi
  [avain tiedot]
  (let [keywordisoi? (some? (get #{:laji :kasittelytapa} avain))]
    (if keywordisoi?
      (keyword tiedot)
      tiedot)))

(defn- assoc-in-muotoon
  [[avain tiedot]]
  (let [konversiot {:sijainti [:laatupoikkeama :sijainti]
                    :kasittelyaika [:laatupoikkeama :paatos :kasittelyaika]
                    :perustelu [:laatupoikkeama :paatos :perustelu]}]
    [(or (get konversiot avain) [avain]) tiedot]))

(defn- lomake->sanktiolistaus
  [lomake]
  (let [konversiot (reversoi-mappi konversioavaimet)
        tee-valitut-avaimet (map #(-> [% (get lomake %)]))
        keywordisoi-tiedot (map #(let [[avain tiedot] %]
                                   [avain (keywordisoi avain tiedot)]))
        konvertoi-avaimet (map #(let [[avain tiedot] %]
                                 
                                  [(or (get konversiot avain) avain) tiedot]))        
        assoc-in-avaimet (map assoc-in-muotoon)]
    (merge
      (reduce
        (fn [acc [avain tieto]]
          (println avain tieto acc)
          (assoc-in acc avain tieto))
        {}
        (into [] (comp tee-valitut-avaimet konvertoi-avaimet keywordisoi-tiedot assoc-in-avaimet) valitut-avaimet))
      {:bonus true :suorasanktio true})))

(defrecord LisaaLiite [liite])
(defrecord PoistaLisattyLiite [])
(defrecord PoistaTallennettuLiite [liite-id])
(defrecord PaivitaLomaketta [lomake])
(defrecord TallennaBonus [])
(defrecord PoistaBonus [])
(defrecord TallennusOnnistui [vastaus])
(defrecord TallennusEpaonnistui [vastaus])
(defrecord TyhjennaLomake [sulje-fn])
(defrecord PoistaPoistetutLiitteet [liite-id])
(defrecord LiitteidenHakuOnnistui [liitteet])
(defrecord LiitteidenHakuEpaonnistui [vastaus])
(defrecord HaeLiitteet [])

(extend-protocol tuck/Event
  PoistaPoistetutLiitteet
  (process-event
    [{:keys [liite-id]} app]    
    (let [liitteet (get-in app [:lomake :liitteet])]
      (assoc-in app [:lomake :liitteet]
        (filter (fn [liite]
                  (not= (:id liite) liite-id))
          liitteet))))
  PoistaLisattyLiite
  (process-event
    [_ app]
    (assoc-in app [:uusi-liite] nil))
  PoistaTallennettuLiite
  (process-event
    [{:keys [liite-id]} app]
    (let [{urakka-id :id} @nav/valittu-urakka
          e! (tuck/current-send-function)]
      (liitteet/poista-liite-kannasta
        {:urakka-id urakka-id
         :domain :bonukset
         :domain-id (get-in app [:lomake :id])
         :liite-id liite-id
         :poistettu-fn #(e! (->PoistaPoistetutLiitteet liite-id))})))
  LisaaLiite
  (process-event
    [{liite :liite} app]
    (-> app
      (update-in [:lomake :liitteet] conj liite)
      (assoc-in [:uusi-liite] liite)))
  LiitteidenHakuEpaonnistui
  (process-event
    [_ app]
    (viesti/nayta-toast! "Liitteiden haku epäonnistui" :varoitus)
    app)
  LiitteidenHakuOnnistui
  (process-event
    [{:keys [liitteet]} app]
    (println "liitteet" liitteet)
    (-> app
      (assoc :liitteet-haettu? true)
      (assoc-in [:lomake :liitteet] liitteet)))
  HaeLiitteet
  (process-event
    [_ app]
    (-> app
      (tuck-apurit/post! :hae-bonuksen-liitteet
        {:urakka-id (:id @nav/valittu-urakka)
         :bonus-id (-> app :lomake :id)}
        {:onnistui ->LiitteidenHakuOnnistui
         :epaonnistui ->LiitteidenHakuEpaonnistui})))
  TyhjennaLomake
  (process-event
    [{sulje-fn :sulje-fn} {:keys [tallennettu-lomake] :as app}]
    (sulje-fn (lomake->sanktiolistaus tallennettu-lomake))
    (-> app
      (dissoc :lomake)
      (assoc :voi-sulkea? false)))
  PaivitaLomaketta
  (process-event
    [{lomake :lomake} app]
    (let [{viimeksi-muokattu ::lomake/viimeksi-muokattu-kentta
           muokatut ::lomake/muokatut} lomake
          pvm-muokattu-viimeksi? (= :pvm viimeksi-muokattu)
          laskutuskuukausi-muokattuihin? (and pvm-muokattu-viimeksi?
                                           (nil? (:laskutuskuukausi muokatut))
                                           (some? (:laskutuskuukausi lomake)))
          lomake (if laskutuskuukausi-muokattuihin?
                   (update lomake ::lomake/muokatut conj :laskutuskuukausi)
                   lomake)]
      (assoc app :lomake lomake)))
  TallennusOnnistui
  (process-event    
    [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Tallennus onnistui")
    (assoc app :tallennus-kaynnissa? false :voi-sulkea? true :tallennettu-lomake vastaus))
  TallennusEpaonnistui
  (process-event
    [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Tallennus epäonnistui" :varoitus)
    (assoc app :tallennus-kaynnissa? false))
  PoistaBonus
  (process-event
    [_ app]
    (let [lomakkeen-tiedot (select-keys (:lomake app) valitut-avaimet)
          payload (assoc lomakkeen-tiedot
            :poistettu true
            :urakka-id (:id @nav/valittu-urakka)
            :tyyppi (-> lomakkeen-tiedot :tyyppi name)
            :palauta-tallennettu? true)]
      (-> app
        (tuck-apurit/post! :tallenna-erilliskustannus
               payload
               {:onnistui ->TallennusOnnistui
                :epaonnistui ->TallennusEpaonnistui})
        (assoc :tallennus-kaynnissa? true))))
  TallennaBonus
  (process-event
    [_ app]    
    (let [lomakkeen-tiedot (select-keys (:lomake app) valitut-avaimet)
          payload (merge lomakkeen-tiedot
                    {:urakka-id (:id @nav/valittu-urakka)
                     :tyyppi (-> lomakkeen-tiedot :tyyppi name)
                     :palauta-tallennettu? true
                     :liitteet (:liitteet (:lomake app))})]
      (-> app
        (tuck-apurit/post! :tallenna-erilliskustannus
               payload
               {:onnistui ->TallennusOnnistui
                :epaonnistui ->TallennusEpaonnistui})
        (assoc :tallennus-kaynnissa? true)))))
