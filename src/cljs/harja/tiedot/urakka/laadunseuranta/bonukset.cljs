(ns harja.tiedot.urakka.laadunseuranta.bonukset
  (:require [clojure.set :as set]
            [tuck.core :as tuck]
            [harja.ui.lomake :as lomake]
            [harja.ui.viesti :as viesti]
            [harja.ui.liitteet :as liitteet]

            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as urakka]

            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [clojure.spec.alpha :as s]))

(def konversioavaimet
  {:perintapvm :pvm
   :summa :rahasumma
   :perustelu :lisatieto
   :laji :tyyppi
   :indeksi :indeksin_nimi
   :kasittelyaika :laskutuskuukausi})

(def valitut-avaimet [:pvm :rahasumma :toimenpideinstanssi :tyyppi :lisatieto :indeksikorjaus :sijainti
                      :laskutuskuukausi :kasittelytapa :indeksin_nimi :id :suorasanktio])

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
  (let [konversiot (set/map-invert konversioavaimet)
        tee-valitut-avaimet (map #(-> [% (get lomake %)]))
        keywordisoi-tiedot (map #(let [[avain tiedot] %]
                                   [avain (keywordisoi avain tiedot)]))
        konvertoi-avaimet (map #(let [[avain tiedot] %]                                 
                                  [(or (get konversiot avain) avain) tiedot]))        
        assoc-in-avaimet (map assoc-in-muotoon)
        poistettu? (:poistettu lomake)]
    (merge
      (reduce
        (fn [acc [avain tieto]]
          (assoc-in acc avain tieto))
        {}
        (into [] (comp tee-valitut-avaimet konvertoi-avaimet keywordisoi-tiedot assoc-in-avaimet) valitut-avaimet))
      {:bonus true :suorasanktio true :poistettu poistettu?})))


;; ---------

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

(defn- tallenna-bonus-mhu
  "Muodostaa payloadin bonuslomakkeesta ja tallentaa bonuksen tiedot erilliskustannus-tauluun."
  [{:keys [lomake] :as app}]
  (let [lomakkeen-tiedot (select-keys lomake valitut-avaimet)
        payload (merge lomakkeen-tiedot
                  {:urakka-id (:id @nav/valittu-urakka)
                   :tyyppi (-> lomakkeen-tiedot :tyyppi name)
                   :palauta-tallennettu? true
                   :liitteet (:liitteet lomake)})]

    (tuck-apurit/post! app :tallenna-erilliskustannus
      payload
      {:onnistui ->TallennusOnnistui
       :epaonnistui ->TallennusEpaonnistui})))


(defn- tallenna-bonus-yllapito
  "Muodostaa payloadin bonuslomakkeesta ja tallentaa bonuksen sanktio-tauluun.
  Yllapidon urakoiden bonus tallennetaan poikkeuksellisesti sanktiona sanktio-tauluun."
  [{:keys [lomake] :as app}]
  (let [payload {:sanktio {:laji :yllapidon_bonus
                           :suorasanktio true
                           :summa (:rahasumma lomake)
                           :indeksi (:indeksin_nimi lomake)
                           :kasittelyaika (:pvm lomake)
                           :perintapvm (:laskutuskuukausi lomake)
                           :toimenpideinstanssi (:toimenpideinstanssi lomake)}
                 :laatupoikkeama {:tekijanimi @istunto/kayttajan-nimi
                                  :urakka (:id @nav/valittu-urakka)
                                  :yllapitokohde (:id (:yllapitokohde lomake))
                                  ;; Laatupoikkeamalla on pakko olla jokin "havaintoaika", vaikka tässä on kyseessä bonus.
                                  ;; Asetetaan se samaksi kuin käsittelyaika.
                                  :aika (:pvm lomake)
                                  ;; Päätös on poikkeuksellisesti "sanktio" vaikka oikeasti kyseessä on bonus.
                                  :paatos {:paatos "sanktio"
                                           :perustelu (:lisatieto lomake)
                                           :kasittelyaika (:pvm lomake)
                                           :kasittelytapa (:kasittelytapa lomake)}
                                  :liitteet (:liiteet lomake)}
                 :hoitokausi @urakka/valittu-hoitokausi}]

    (tuck-apurit/post! app :tallenna-suorasanktio
      payload
      {:onnistui ->TallennusOnnistui
       :epaonnistui ->TallennusEpaonnistui})))

(extend-protocol tuck/Event
  PoistaPoistetutLiitteet
  (process-event
    [{:keys [liite-id]} app]
    (log/debug "PoistaPoistetutLiitteet")

    (let [liitteet (get-in app [:lomake :liitteet])]
      (assoc-in app [:lomake :liitteet]
        (filter (fn [liite]
                  (not= (:id liite) liite-id))
          liitteet))))

  PoistaLisattyLiite
  (process-event
    [_ app]
    (log/debug "PoistaLisattyLiite")

    (assoc-in app [:uusi-liite] nil))

  PoistaTallennettuLiite
  (process-event
    [{:keys [liite-id]} app]
    (log/debug "PoistaTallennettuLiite")

    (let [{urakka-id :id} @nav/valittu-urakka
          e! (tuck/current-send-function)
          _ (liitteet/poista-liite-kannasta
              {:urakka-id urakka-id
               :domain :bonukset
               :domain-id (get-in app [:lomake :id])
               :liite-id liite-id
               :poistettu-fn #(e! (->PoistaPoistetutLiitteet liite-id))})]
      app))

  LisaaLiite
  (process-event
    [{liite :liite} app]
    (log/debug "LisaaLiite")

    (-> app
      (update-in [:lomake :liitteet] conj liite)
      (assoc-in [:uusi-liite] liite)))

  LiitteidenHakuEpaonnistui
  (process-event
    [_ app]
    (log/debug "LiitteidenHakuEpaonnistui")

    (viesti/nayta-toast! "Liitteiden haku epäonnistui" :varoitus)
    app)

  LiitteidenHakuOnnistui
  (process-event
    [{:keys [liitteet]} app]
    (log/debug "LiitteidenHakuOnnistui")

    (-> app
      (assoc :liitteet-haettu? true)
      (assoc-in [:lomake :liitteet] liitteet)))

  HaeLiitteet
  (process-event
    [_ app]
    (log/debug "HaeLiitteet")

    (-> app
      (tuck-apurit/post! :hae-bonuksen-liitteet
        {:urakka-id (:id @nav/valittu-urakka)
         :bonus-id (-> app :lomake :id)}
        {:onnistui ->LiitteidenHakuOnnistui
         :epaonnistui ->LiitteidenHakuEpaonnistui})))

  TyhjennaLomake
  (process-event
    [{sulje-fn :sulje-fn} {:keys [tallennettu-lomake] :as app}]
    (log/debug "TyhjennaLomake")

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
    [{vastaus :vastaus optiot :optiot} app]
    (log/debug "TallennusOnnistui")

    (viesti/nayta-toast! "Tallennus onnistui")
    (let [tallennettu-bonus (merge vastaus optiot)
          [hoitokausi-alku hoitokausi-loppu] @urakka/valittu-hoitokausi
          bonus-hoitokaudella? (pvm/valissa? (:pvm tallennettu-bonus) hoitokausi-alku hoitokausi-loppu)]
      (assoc app :tallennus-kaynnissa? false :voi-sulkea? true
        :tallennettu-lomake (when bonus-hoitokaudella? (merge vastaus optiot)))))

  TallennusEpaonnistui
  (process-event
    [{vastaus :vastaus} app]
    (log/debug "TallennusEpaonnistui")

    (viesti/nayta-toast! "Tallennus epäonnistui" :varoitus)
    (assoc app :tallennus-kaynnissa? false))

  PoistaBonus
  (process-event
    [_ app]
    (log/debug "PoistaBonus")

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
    [_ {:keys [lomake] :as app}]
    (log/debug "TallennaBonus: " lomake)

    (let [lomakkeen-tiedot (select-keys lomake valitut-avaimet)
          bonustyyppi (:tyyppi lomakkeen-tiedot)
          tallenna-fn (fn [app]
                        (if (= :yllapidon_bonus bonustyyppi)
                          (tallenna-bonus-yllapito app)
                          (tallenna-bonus-mhu app)))]
      (-> app
        tallenna-fn
        (assoc :tallennus-kaynnissa? true)))))
