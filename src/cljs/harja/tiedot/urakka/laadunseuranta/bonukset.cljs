(ns harja.tiedot.urakka.laadunseuranta.bonukset
  (:require [tuck.core :as tuck]
            [harja.ui.lomake :as lomake]
            [harja.ui.viesti :as viesti]
            [harja.ui.liitteet :as liitteet]

            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as urakka]

            [harja.tyokalut.tuck :as tuck-apurit]
            [taoensso.timbre :as log]))

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
  (let [lomake (lomake/ilman-lomaketietoja lomake)
        payload {:id (:id lomake)
                 :tyyppi (-> lomake :laji name)
                 :toimenpideinstanssi (:toimenpideinstanssi lomake)
                 :urakka-id (:id @nav/valittu-urakka)

                 :pvm (:kasittelyaika lomake)
                 ;; Erilliskustannus-taulussa ei ole saraketta 'perintapvm', joten perintapvm-kentän arvo
                 ;; tallennetaan sarakkeeseen 'laskutuskuukausi'
                 :laskutuskuukausi (:perintapvm lomake)
                 :rahasumma (:summa lomake)
                 :indeksin_nimi (:indeksi lomake)
                 :lisatieto (:lisatieto lomake)
                 :kasittelytapa (:kasittelytapa lomake)
                 :liitteet (:liitteet lomake)}]

    (tuck-apurit/post! app :tallenna-erilliskustannus
      payload
      {:onnistui ->TallennusOnnistui
       :epaonnistui ->TallennusEpaonnistui})))


(defn- tallenna-bonus-yllapito
  "Muodostaa payloadin bonuslomakkeesta ja tallentaa bonuksen sanktio-tauluun.
  Yllapidon urakoiden bonus tallennetaan poikkeuksellisesti sanktiona sanktio-tauluun."
  [{:keys [lomake] :as app}]
  (let [lomake (lomake/ilman-lomaketietoja lomake)
        payload {:sanktio {:id (:id lomake)
                           :laji :yllapidon_bonus
                           :suorasanktio true
                           :summa (:summa lomake)
                           :indeksi (:indeksi lomake)
                           :perintapvm (:perintapvm lomake)
                           :toimenpideinstanssi (:toimenpideinstanssi lomake)}
                 :laatupoikkeama {:tekijanimi @istunto/kayttajan-nimi
                                  :urakka (:id @nav/valittu-urakka)
                                  :yllapitokohde (:id (:yllapitokohde lomake))
                                  ;; Laatupoikkeamalla on pakko olla jokin "havaintoaika", vaikka tässä on kyseessä bonus.
                                  ;; Asetetaan se samaksi kuin käsittelyaika.
                                  :aika (:kasittelyaika lomake)
                                  ;; Päätös on poikkeuksellisesti "sanktio" vaikka oikeasti kyseessä on bonus.
                                  :paatos {:paatos "sanktio"
                                           :perustelu (:lisatieto lomake)
                                           :kasittelyaika (:kasittelyaika lomake)
                                           :kasittelytapa (:kasittelytapa lomake)}
                                  :liitteet (:liiteet lomake)}
                 :hoitokausi @urakka/valittu-hoitokausi}]

    (tuck-apurit/post! app :tallenna-suorasanktio
      payload
      {:onnistui ->TallennusOnnistui
       :epaonnistui ->TallennusEpaonnistui})))

(defn poista-bonus-mhu [{:keys [lomake] :as app}]
  (let [payload {:id (:id lomake)
                 :urakka-id (:id @nav/valittu-urakka)}]
    (-> app
      (tuck-apurit/post! :poista-erilliskustannus
        payload
        {:onnistui ->TallennusOnnistui
         :epaonnistui ->TallennusEpaonnistui})
      (assoc :tallennus-kaynnissa? true))))

(defn poista-bonus-yllapito [{:keys [lomake] :as app}]
  (let [payload {:id (:id lomake)
                 :urakka-id (:id @nav/valittu-urakka)}]
    (-> app
      (tuck-apurit/post! :poista-suorasanktio
        payload
        {:onnistui ->TallennusOnnistui
         :epaonnistui ->TallennusEpaonnistui})
      (assoc :tallennus-kaynnissa? true))))

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
    [{sulje-fn :sulje-fn} {:keys [tallennus-onnistui?] :as app}]
    (log/debug "TyhjennaLomake")

    ;; Välitetään tieto tallentumisen onnistumisesta sulje-fn:lle.
    ;; Sanktiot & bonukset listaus päivitetään tarvittaessa uudestaan tiedon perusteella.
    (sulje-fn tallennus-onnistui?)

    (-> app
      (dissoc :lomake :tallennus-onnistui?)
      (assoc :voi-sulkea? false)))

  PaivitaLomaketta
  (process-event
    [{lomake :lomake} app]
    (let [{viimeksi-muokattu ::lomake/viimeksi-muokattu-kentta
           muokatut ::lomake/muokatut} lomake
          pvm-muokattu-viimeksi? (= :kasittelyaika viimeksi-muokattu)
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

    ;; Merkitään tallennus onnistuneeksi, jotta tieto voidaan välittää ylemmälle kerrokselle
    ;; Sanktiot & bonukset listaus päivitetään tarvittaessa uudestaan tiedon perusteella.
    ;; Bonuksia tallennetaan erilliskustannust-tauluun (MHU ym.) ja sanktio-tauluun (ylläpito),
    ;; joten on yksinkertaisempaa päivittää listauksen tiedot sanktiot & bonukset näkymässä tallennuksen jälkeen.
    (assoc app :tallennus-kaynnissa? false :tallennus-onnistui? true :voi-sulkea? true))

  TallennusEpaonnistui
  (process-event
    [{vastaus :vastaus} app]
    (log/debug "TallennusEpaonnistui")

    (viesti/nayta-toast! "Tallennus epäonnistui" :varoitus)
    (assoc app :tallennus-kaynnissa? false :tallennus-onnistui? false))

  PoistaBonus
  (process-event
    [_ {:keys [lomake] :as app}]
    (log/debug "PoistaBonus")

    (let [bonuksen-laji (:laji lomake)
          poista-fn (fn [app]
                        ;; Ylläpidon urakoiden bonukset käsitellään eri tavalla kuin MH-urakoiden bonukset,
                        ;; joten bonuksen lajin perusteella valitaan oikea polku tallennuksen loppuun viemiselle.
                        (if (= :yllapidon_bonus bonuksen-laji)
                          (poista-bonus-yllapito app)
                          (poista-bonus-mhu app)))]
      (-> app
        poista-fn
        (assoc :tallennus-kaynnissa? true))))

  TallennaBonus
  (process-event
    [_ {:keys [lomake] :as app}]
    (log/debug "TallennaBonus: " lomake)

    (let [bonuksen-laji (:laji lomake)
          tallenna-fn (fn [app]
                        ;; Ylläpidon urakoiden bonukset käsitellään eri tavalla kuin MH-urakoiden bonukset,
                        ;; joten bonuksen lajin perusteella valitaan oikea polku tallennuksen loppuun viemiselle.
                        (if (= :yllapidon_bonus bonuksen-laji)
                          (tallenna-bonus-yllapito app)
                          (tallenna-bonus-mhu app)))]
      (-> app
        tallenna-fn
        (assoc :tallennus-kaynnissa? true)))))
