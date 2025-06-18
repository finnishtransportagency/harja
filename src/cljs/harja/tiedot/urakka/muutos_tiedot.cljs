(ns harja.tiedot.urakka.muutos-tiedot
  "Urakan muutosten tiedot."
  (:require [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]))

(defonce +muutoksien-kayttoonoton-hoitokauden-alkuvuosi+ 2025)

(defn ennen-muutoksien-kayttoonotto? [valittu-hoitokausi]
  (when valittu-hoitokausi
    (< (pvm/vuosi (first valittu-hoitokausi))
       +muutoksien-kayttoonoton-hoitokauden-alkuvuosi+)))

;; Hae muutostiedot
(defrecord HaeUrakanMuutostiedot [urakka])
(defrecord HaeUrakanMuutostiedotOnnnistui [vastaus])
(defrecord HaeUrakanMuutostiedotEpaonnistui [vastaus])

;; Vaihda hoitokausi
(defrecord HoitokausiVaihdettu [urakka hoitokausi])
(defrecord MuokkaaMuutosta [rivi])
(defrecord TallennaMuutos [muutos])
(defrecord TallennaMuutosEpaonnistui [vastaus])
(defrecord ToggleTaulukonNakyvyys [taulukon-avain])
(defrecord MuokkaaLaskettujenMuutoksienSyita [])
(defrecord MuokkaaRahavaraustenMuutoksienSyita [])

(defrecord TallennaLaskettujenMuutostenSyyt [rivit])
(defrecord TallennaRahavarausmuutostenSyyt [rivit])
(defrecord TallennaRahavarausmuutostenSyytEpaonnistui [vastaus])


;; Liitteet
(defrecord LisaaLiite [liite])
(defrecord PoistaLisattyLiite [])
(defrecord PoistaTallennettuLiite [liite-id])
(defrecord PoistaPoistetutLiitteet [liite-id])
;; aika ennen 2025-2026 hoitovuotta
(defrecord LisaaTavoitehintojenMuutos [])
(defrecord LisaaSuunniteltujenMaarienMuutos [])
;; Päänäkymä ja listaus
(defrecord ValitseUrakka [urakka])
(defrecord NakymastaPoistuttiin [])
(defrecord PaivitaLomake [lomake])
;; Tehtävä määrämuutokset 
(defrecord HaeTehtavaMaaraMuutokset [])
(defrecord HaeTehtavaMaaramuutoksetOnnistui [vastaus])
(defrecord HaeTehtavaMaaramuutoksetEpaonnistui [vastaus])


(defn- hae-tehtava-maaramuutokset [app tehtavaryhma-id alkuvuosi]
  (tuck-apurit/post! app
    :hae-tehtava-maaramuutokset
    {:urakka-id (-> @tila/yleiset :urakka :id)
     :tehtavaryhma tehtavaryhma-id
     :hoitokauden-alkuvuosi alkuvuosi}
    {:onnistui ->HaeTehtavaMaaramuutoksetOnnistui
     :epaonnistui ->HaeTehtavaMaaramuutoksetEpaonnistui}))


(defn valitse-urakka [app urakka]
  (let [hoitokaudet (u/hoito-tai-sopimuskaudet urakka)
        vanha-hoitokausi (:valittu-hoitokausi app)
        uusi-hoitokausi (if (contains? (set hoitokaudet) vanha-hoitokausi)
                          vanha-hoitokausi
                          (u/paattele-valittu-hoitokausi hoitokaudet))]
    (-> @tila/muutokset
        (assoc :urakan-hoitokaudet hoitokaudet)
        (assoc :valittu-hoitokausi uusi-hoitokausi))))


(defn hae-urakan-muutostiedot
  "Hakee urakan muutostiedot, eli miten tavoitehinta ja tehtävä- ja määräluettelo ovat muuttuneet alkuperäisiin tietoihin nähden."
  ([app] (hae-urakan-muutostiedot app (:urakka @tila/yleiset)))
  ([app urakka]
   (tuck-apurit/post! :hae-urakan-muutostiedot
     {:urakka-id (:id urakka)
      :valittu-hoitokausi (:valittu-hoitokausi app)}
     {:onnistui ->HaeUrakanMuutostiedotOnnnistui
      :epaonnistui ->HaeUrakanMuutostiedotEpaonnistui})))


(extend-protocol tuck/Event
  HoitokausiVaihdettu
  (process-event [{urakka :urakka hoitokausi :hoitokausi} app]
    (let [app (assoc app :valittu-hoitokausi hoitokausi)]
      (hae-urakan-muutostiedot app urakka)
      app))

  HaeUrakanMuutostiedot
  (process-event [{urakka :urakka} app]
    (let [;; Lupauksia voidaan hakea myös välikatselmuksesta, niin tarkistetaan hoitokauden tila sitä ennen
          app (if (:valittu-hoitokausi app)
                app
                (assoc app :valittu-hoitokausi [(pvm/hoitokauden-alkupvm (:hoitokauden-alkuvuosi app))
                                                (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc (:hoitokauden-alkuvuosi app))))]))]
      (hae-urakan-muutostiedot app urakka)
      app))


  HaeUrakanMuutostiedotOnnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      :kirjatut-muutokset (:kirjatut-muutokset vastaus)
      :rahavarausten-muutokset (:rahavarausten-muutokset vastaus)
      :tavoitehinnan-muutokset (:tavoitehinnan-muutokset vastaus)
      :suunniteltujen-maarien-muutokset (:suunniteltujen-maarien-muutokset vastaus)
      :budjettitavoitteet (:budjettitavoitteet vastaus)))

  HaeUrakanMuutostiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Muutostietojen hakeminen epäonnistui!" :varoitus)
    app)

  MuokkaaMuutosta
  (process-event [{rivi :rivi} app]
    (assoc app :muokattava-muutos rivi))

  TallennaMuutos
  (process-event [{muutos :muutos} app]
    (prn "tallenna muutos: " muutos)
    (let [urakka (:urakka @tila/yleiset)]
      (tuck-apurit/post! :tallenna-muutos
        {:urakka-id (:id urakka)
         :valittu-hoitokausi (:valittu-hoitokausi app)
         :muutos muutos}
        {:onnistui ->HaeUrakanMuutostiedotOnnnistui         ;; voidaan käyttää samaa eventtiä, koska haetaan uudet muutostiedot tallennuksen jälkeen
         :epaonnistui ->TallennaMuutosEpaonnistui})))

  TallennaMuutosEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Muutoksen tallentaminen epäonnistui!" :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  TallennaRahavarausmuutostenSyytEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Rahavarauksien muutosten syiden tallentaminen epäonnistui!" :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  ToggleTaulukonNakyvyys
  (process-event [{taulukon-avain :taulukon-avain} app]
    (assoc-in app [:taulukko-nakyvissa? taulukon-avain]
      (not (get-in app [:taulukko-nakyvissa? taulukon-avain]))))

  MuokkaaLaskettujenMuutoksienSyita
  (process-event [_ app]
    ;; TODO: aloita laskettujen muutosten syiden muokkaus taulukossa, ei avata lomaketta
    app)

  TallennaLaskettujenMuutostenSyyt
  (process-event [_ app]
    ;; TODO: Tallenna laskettujen muutosten syyt, mallia vaikka rahavarausmuutosten syiden tallentamisesta
    app)

  MuokkaaRahavaraustenMuutoksienSyita
  (process-event [_ app]
    (assoc app :rahavarausten-syyt-muokattavana? true))

  TallennaRahavarausmuutostenSyyt
  (process-event [{rivit :rivit} app]
    (let [urakka (:urakka @tila/yleiset)]
      (tuck-apurit/post! :tallenna-rahavarausmuutosten-syyt
        {:urakka-id (:id urakka)
         :valittu-hoitokausi (:valittu-hoitokausi app)
         :rivit (map #(select-keys % [:id :syy]) rivit)}
        {:onnistui ->HaeUrakanMuutostiedotOnnnistui         ;; voidaan käyttää samaa eventtiä, koska haetaan uudet muutostiedot tallennuksen jälkeen
         :epaonnistui ->TallennaRahavarausmuutostenSyytEpaonnistui})
      app))

  LisaaLiite
  (process-event
    [{liite :liite} app]
    (prn "LisaaLiite")
    (-> app
      (update-in [:muokattava-muutos :liitteet] conj liite)
      (assoc :uusi-liite liite)))

  PoistaPoistetutLiitteet
  (process-event
    [{:keys [liite-id]} app]
    (prn "PoistaPoistetutLiitteet")

    (let [liitteet (get-in app [:muokattava-muutos :liitteet])]
      (assoc-in app [:muokattava-muutos :liitteet]
        (filter (fn [liite]
                  (not= (:id liite) liite-id))
          liitteet))))

  PoistaTallennettuLiite
  (process-event
    [{:keys [liite-id]} app]
    (prn "PoistaTallennettuLiite, liite-id: " liite-id)
    (let [{urakka-id :id} @nav/valittu-urakka
          e! (tuck/current-send-function)
          _ (liitteet/poista-liite-kannasta
              {:urakka-id urakka-id
               :domain :muutokset
               :domain-id (get-in app [:muokattava-muutos :id])
               :liite-id liite-id
               :poistettu-fn #(e! (->PoistaPoistetutLiitteet liite-id))})]
      app))

  PoistaLisattyLiite
  (process-event
    [_ app]
    (prn "PoistaLisattyLiite")
    (assoc app :uusi-liite nil))

  LisaaTavoitehintojenMuutos
  (process-event [_ app] app)

  LisaaSuunniteltujenMaarienMuutos
  (process-event [_ app] app)

  ValitseUrakka
  (process-event [{urakka :urakka} app]
    (valitse-urakka app urakka))

  NakymastaPoistuttiin
  (process-event [_ app] app)

  PaivitaLomake
  (process-event [{lomake :lomake} app]
    (assoc app :muokattava-muutos lomake))
  
  HaeTehtavaMaaraMuutokset
  (process-event [_ app]
    (let [;; _ (println "\n valittu: " (-> @u/valittu-hoitokausi first (pvm/vuosi)) " or : " (:hoitokauden-alkuvuosi app))
          ;; 
          ]
      (hae-tehtava-maaramuutokset app 0 (or 
                                          (:hoitokauden-alkuvuosi app) 
                                          (-> @u/valittu-hoitokausi first (pvm/vuosi))))
      app))

  HaeTehtavaMaaramuutoksetOnnistui
  (process-event [{:keys [vastaus]} app]
    ;; (println "\n suunnitellut:: " vastaus " \n ") 
    (assoc app :lasketut-muutokset vastaus)
    ;; 
    )

  HaeTehtavaMaaramuutoksetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Suunnitellun määrän haku epäonnistui" :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  ;; 
  )
