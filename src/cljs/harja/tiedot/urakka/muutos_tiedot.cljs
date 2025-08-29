(ns harja.tiedot.urakka.muutos-tiedot
  "Urakan muutosten tiedot."
  (:require [tuck.core :as tuck]
            [reagent.core :refer [atom]]

            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.ui.lomake :as lomake]
            [harja.ui.viesti :as viesti]
            [harja.ui.liitteet :as liitteet]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tyokalut.tuck :as tuck-apurit]))


(def johto-ja-hallintokorvausmuutokset-atom (atom nil))
(def muutoksien-kayttoonoton-hoitokauden-alkuvuosi 2025)
(def pakolliset-kentat-fmt {:nimi "Nimi"
                            :tyyppi "Tyyppi"
                            :syy "Muutoksen syy"
                            :voimassa_alkaen "Voimassa alkaen"})


;; Hae muutostiedot
(defrecord HaeUrakanMuutostiedot [urakka])
(defrecord HaeUrakanMuutostiedotOnnistui [vastaus])
(defrecord HaeUrakanMuutostiedotEpaonnistui [vastaus])


(defrecord HaeYksikkohinnatOnnistui [vastaus])
(defrecord HaeYksikkohinnatEpaonnistui [vastaus])


(defrecord KuluhakuOnnistui [vastaus])
(defrecord KuluhakuEpaonnistui [vastaus])


;; Vaihda hoitokausi
(defrecord HoitokausiVaihdettu [urakka hoitokausi])
(defrecord MuokkaaMuutosta [rivi])
(defrecord TallennaMuutos [muutos])
(defrecord TallennaMuutosEpaonnistui [vastaus])
(defrecord ToggleTaulukonNakyvyys [taulukon-avain])
(defrecord MuokkaaLaskettujenMuutoksienSyita [])
(defrecord MuokkaaRahavaraustenMuutoksienSyita [])
(defrecord HaeMuutoksenTiedot [muutos])
(defrecord HaeMuutoksenTiedotOnnistui [vastaus muutos valittu-hoitokausi])
(defrecord HaeMuutoksenTiedotEpaonnistui [vastaus])
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


;; Tehtävä- määrämuutokset 
(defrecord AvaaYksikkohintaModal [valittu-modal-tehtava tehtava_id])
(defrecord SuljeYksikkohintaModal [])
(defrecord HaeTehtavaMaaramuutoksetOnnistui [vastaus])
(defrecord HaeTehtavaMaaramuutoksetEpaonnistui [vastaus])
(defrecord TallennaTehtavaMaaramuutokset [rivit])
(defrecord TallennaTehtavaMaaramuutoksetOnnistui [vastaus])
(defrecord TallennaTehtavaMaaramuutoksetEpaonnistui [vastaus])


;; ---------------------------------------------
;; 
(defn valitse-urakka [app urakka]
  (let [hoitokaudet (u/hoito-tai-sopimuskaudet urakka)
        vanha-hoitokausi (:valittu-hoitokausi app)
        uusi-hoitokausi (if (contains? (set hoitokaudet) vanha-hoitokausi)
                          vanha-hoitokausi
                          (u/paattele-valittu-hoitokausi hoitokaudet))]
    (-> @tila/muutokset
        (assoc :urakan-hoitokaudet hoitokaudet)
        (assoc :valittu-hoitokausi uusi-hoitokausi))))


(defn johto-ja-hallintokorvausmuutoksen-rivit
  "Luo johto-ja-hallintokorvausmuutoksen rivit eli kulut. Yhdistää tyhjät rivit ja kannasta tulevat kulut."
  [valittu-hoitokausi kulut]
  (let [avaimet (pvm/aikavalin-kuukaudet-pvm-vektorina valittu-hoitokausi)
        ;; backend ja frontend pvm:t vähän erimuotoisia. Koska niitä käytetään avaimina, normalisoidaan ensin
        normalisoi #(when (pvm/pvm? %)
                      (pvm/luo-pvm (pvm/vuosi %) (dec (pvm/kuukausi %)) (pvm/paiva %)))
        kulut-normalisoitu (map #(update % :pvm normalisoi) kulut)
        kulut-map (group-by :pvm kulut-normalisoitu)
        normalisoidut-avaimet (map normalisoi avaimet)
        parit (mapcat (fn [pvm]
                        [pvm (or (first (get kulut-map pvm))
                               {:pvm pvm :tavoitehinnan-muutos 0})])
                normalisoidut-avaimet)]
    (apply array-map parit)))


(defn hae-urakan-muutostiedot
  "Hakee urakan muutostiedot, eli miten tavoitehinta ja tehtävä- ja määräluettelo ovat muuttuneet alkuperäisiin tietoihin nähden."
  [app]
  (tuck-apurit/post! :hae-urakan-muutostiedot
    {:urakka-id (-> @tila/yleiset :urakka :id)
     :valittu-hoitokausi (:valittu-hoitokausi app)}
    {:onnistui ->HaeUrakanMuutostiedotOnnistui
     :epaonnistui ->HaeUrakanMuutostiedotEpaonnistui}))


(defn hae-tehtava-maaramuutokset [app]
  (tuck-apurit/post! app :hae-tehtava-maaramuutokset
    {:urakka-id (-> @tila/yleiset :urakka :id)
     :valittu-hoitokausi (:valittu-hoitokausi app)}
    {:onnistui ->HaeTehtavaMaaramuutoksetOnnistui
     :epaonnistui ->HaeTehtavaMaaramuutoksetEpaonnistui}))


(defn ennen-muutoksien-kayttoonotto? [valittu-hoitokausi]
  (when valittu-hoitokausi
    (< (pvm/vuosi (first valittu-hoitokausi))
      muutoksien-kayttoonoton-hoitokauden-alkuvuosi)))


(defn alusta-tyyppikohtaisia-arvoja [tyyppi valittu-hoitokausi]
  (case tyyppi
    "johto-ja-hallintokorvaus"
    (reset! johto-ja-hallintokorvausmuutokset-atom
      (johto-ja-hallintokorvausmuutoksen-rivit valittu-hoitokausi []))
    :default))


(defn- poista-liite [app liite-id]
  (let [liitteet (get-in app [:muokattava-muutos :liitteet])]
    (assoc-in app [:muokattava-muutos :liitteet]
      (filter (fn [liite] (not= (:id liite) liite-id))
        liitteet))))


;; ------------------------------------
;; Tuck 
(extend-protocol tuck/Event
  HoitokausiVaihdettu
  (process-event [{urakka :urakka hoitokausi :hoitokausi} app]
    (let [app (-> app
                (assoc :valittu-hoitokausi hoitokausi))]
      (hae-urakan-muutostiedot app)
      (hae-tehtava-maaramuutokset app)
      app))

  AvaaYksikkohintaModal
  (process-event [{:keys [valittu-modal-tehtava tehtava_id]}
                  {:keys [yksikkohinta-modal-auki?] :as app}]
    ;; Haetaan edellisten vuosien yksikköhinnat jos yksikköhintaa ei voitu laskea 
    (let [parametrit {:urakka-id (-> @tila/yleiset :urakka :id)
                      :hoitokaudet @u/valitun-urakan-hoitokaudet
                      :tehtava_id tehtava_id}]
      
      (tuck-apurit/post! app :hae-hoitovuosien-yksikkohinnat
      parametrit
      {:onnistui ->HaeYksikkohinnatOnnistui
       :epaonnistui ->HaeYksikkohinnatEpaonnistui})
      
    (assoc app
      :ladataan-modal? true
      :yksikkohinta-modal-auki? true
      :valittu-modal-tehtava valittu-modal-tehtava)))

  SuljeYksikkohintaModal
  (process-event [_
                  {:keys [yksikkohinta-modal-auki?] :as app}]
    (assoc app
      :valittu-modal-tehtava nil
      :yksikkohinta-modal-auki? false))

  HaeUrakanMuutostiedot
  (process-event [{urakka :urakka} app]
    (let [;; Lupauksia voidaan hakea myös välikatselmuksesta, niin tarkistetaan hoitokauden tila sitä ennen
          app (if (:valittu-hoitokausi app)
                app
                (assoc app :valittu-hoitokausi [(pvm/hoitokauden-alkupvm (:hoitokauden-alkuvuosi app))
                                                (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc (:hoitokauden-alkuvuosi app))))]))]

      (hae-urakan-muutostiedot app)
      (hae-tehtava-maaramuutokset app)
      app))

  HaeUrakanMuutostiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      ;; suljetaan aina lomake kun on saatu uudet muutostiedot
      :muokattava-muutos nil
      :tallennus-kesken? false ;; tallennuksen jälkeinen haku tulee tähän handleriin
      :kirjatut-muutokset (:kirjatut-muutokset vastaus)
      :lasketut-muutokset (:lasketut-muutokset vastaus)
      :rahavarausten-muutokset (:rahavarausten-muutokset vastaus)
      :tavoitehinnan-muutokset (:tavoitehinnan-muutokset vastaus)
      :suunniteltujen-maarien-muutokset (:suunniteltujen-maarien-muutokset vastaus)
      :budjettitavoitteet (:budjettitavoitteet vastaus)))

  HaeUrakanMuutostiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Muutostietojen hakeminen epäonnistui" :varoitus)
    app)

  
  HaeYksikkohinnatOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :hoitokausien-yksikkohinnat vastaus :ladataan-modal? false))

  
  HaeYksikkohinnatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Yksikköhintojen haku epäonnistui" :varoitus)
    (assoc app :ladataan-modal? false))


  HaeTehtavaMaaramuutoksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :tehtava-maaramuutokset vastaus :ladataan-modal? false))

  
  HaeTehtavaMaaramuutoksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Tehtävä- ja määrämuutosten haku epäonnistui" :varoitus)
    (assoc app :ladataan-modal? false))

  
  KuluhakuOnnistui
  (process-event [{vastaus :vastaus} app]
    (println "\n kulut: " vastaus)
    (assoc app :kirjatut-kulut vastaus))

  
  KuluhakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Kulujen haku epäonnistui" :varoitus)
    app)

  
  HaeMuutoksenTiedotOnnistui
  (process-event [{vastaus :vastaus
                   muutos :muutos
                   valittu-hoitokausi :valittu-hoitokausi} app]
    (let [uudet-liitteet (:liitteet vastaus)
          app (assoc-in app [:muokattava-muutos :liitteet] uudet-liitteet)]
      (case (:tyyppi muutos)
        "johto-ja-hallintokorvaus"
        (reset! johto-ja-hallintokorvausmuutokset-atom
          (johto-ja-hallintokorvausmuutoksen-rivit valittu-hoitokausi (:kulut vastaus)))

        :default)
      app))

  
  HaeMuutoksenTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Muutoksen tietojen hakeminen epäonnistui!" :varoitus)
    app)

  
  TallennaTehtavaMaaramuutokset
  (process-event [{:keys [rivit]} {:keys [valittu-rivi] :as app}]
    (let [parametrit {:urakka-id (-> @tila/yleiset :urakka :id)
                      :valittu-hoitokausi (:valittu-hoitokausi app)}]

      (tuck-apurit/post! app :tallenna-tehtava-maaramuutokset
        parametrit
        {:onnistui ->TallennaTehtavaMaaramuutoksetOnnistui
         :epaonnistui ->TallennaTehtavaMaaramuutoksetEpaonnistui})
      (-> app
        (assoc :haku-kaynnissa? true)
        (assoc :tehtava-maaramuutokset nil))))


  TallennaTehtavaMaaramuutoksetOnnistui
  (process-event [{vastaus :vastaus} app]
    ;; TODO 
    (assoc app :tehtava-maaramuutokset vastaus :ladataan-modal? false))


  TallennaTehtavaMaaramuutoksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Tehtävä- ja määrämuutosten tallennus epäonnistui" :varoitus)
    (assoc app :ladataan-modal? false))


  MuokkaaMuutosta
  (process-event [{rivi :rivi} app]
    (assoc app :muokattava-muutos rivi))


  TallennaMuutos
  (process-event [{muutos :muutos} app]
    (let [urakka (:urakka @tila/yleiset)
          puuttuvat-pakolliset-kentat (map
                                        #(get pakolliset-kentat-fmt %)
                                        (lomake/puuttuvat-pakolliset-kentat muutos))
          muutos (lomake/ilman-lomaketietoja muutos)
          kulut (when (= (:tyyppi muutos) "johto-ja-hallintokorvaus")
                                              ;; luodaan vain kuluja, joiden summa on eri suuri kuin 0 (eli niillä on jotain vaikutusta laskentoihin)
                  (filter #(and (some? (:tavoitehinnan-muutos %))
                             (not= 0 (:tavoitehinnan-muutos %)))
                    (vals @johto-ja-hallintokorvausmuutokset-atom)))
          muutos (assoc muutos :kulut kulut)]
      (if-not (empty? puuttuvat-pakolliset-kentat)
        (assoc-in app [:muokattava-muutos :puuttuvat-pakolliset-kentat] puuttuvat-pakolliset-kentat)
        (do
          (tuck-apurit/post! :tallenna-muutos
            {:urakka-id (:id urakka)
             :valittu-hoitokausi (:valittu-hoitokausi app)
             :muutos muutos}
            {:onnistui ->HaeUrakanMuutostiedotOnnistui ;; voidaan käyttää samaa eventtiä, koska haetaan uudet muutostiedot tallennuksen jälkeen
             :epaonnistui ->TallennaMuutosEpaonnistui
             :paasta-virhe-lapi? true})
          (assoc app :tallennus-kesken? true)))))


  TallennaMuutosEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Muutoksen tallentaminen epäonnistui! "
                           (get-in vastaus [:response :virhe])) :varoitus)
    (assoc app :tallennus-kesken? false))


  TallennaRahavarausmuutostenSyytEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Rahavarauksien muutosten syiden tallentaminen epäonnistui!" :varoitus)
    app)


  ToggleTaulukonNakyvyys
  (process-event [{taulukon-avain :taulukon-avain} app]
    (assoc-in app [:taulukko-nakyvissa? taulukon-avain]
      (not (get-in app [:taulukko-nakyvissa? taulukon-avain]))))


  MuokkaaLaskettujenMuutoksienSyita
  (process-event [_ app]
    ;; TODO: aloita laskettujen muutosten syiden muokkaus taulukossa, ei avata lomaketta
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
        {:onnistui ->HaeUrakanMuutostiedotOnnistui         ;; voidaan käyttää samaa eventtiä, koska haetaan uudet muutostiedot tallennuksen jälkeen
         :epaonnistui ->TallennaRahavarausmuutostenSyytEpaonnistui})
      app))


  HaeMuutoksenTiedot
  (process-event [{muutos :muutos} app]
    (let [valittu-hoitokausi (:valittu-hoitokausi app)]
      (when (:id muutos)
        (tuck-apurit/post! :hae-muutoksen-tiedot
          {:urakka-id @nav/valittu-urakka-id
           :muutos {:id (:id muutos)
                    :versio (:versio muutos)
                    :tyyppi (:tyyppi muutos)
                    :liite-idt (into #{}
                                 (map :id (:liitteet muutos)))}}
          {:onnistui ->HaeMuutoksenTiedotOnnistui
           :onnistui-parametrit [muutos valittu-hoitokausi]
           :epaonnistui ->HaeMuutoksenTiedotEpaonnistui}))
      app))


  LisaaLiite
  (process-event
    [{liite :liite} app]
    (prn "LisaaLiite")
    (-> app
      (update-in [:muokattava-muutos :liitteet] conj liite)))

  PoistaPoistetutLiitteet
  (process-event
    [{:keys [liite-id]} app]
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
          ;; hanskataan tässä myös tilanne, jossa muutosta ei ole vielä tallennettu
          _ (when (get-in app [:muokattava-muutos :id])
              (liitteet/poista-liite-kannasta
                {:urakka-id urakka-id
                 :domain :muutokset
                 :domain-id (get-in app [:muokattava-muutos :id])
                 :liite-id liite-id
                 :poistettu-fn #(e! (->PoistaPoistetutLiitteet liite-id))}))]
      (poista-liite app liite-id)))

  
  PoistaLisattyLiite
  (process-event [_ app]
    (prn "PoistaLisattyLiite")
    (assoc app :uusi-liite nil))

  
  LisaaTavoitehintojenMuutos
  (process-event [_ app]
    app)

  
  LisaaSuunniteltujenMaarienMuutos
  (process-event [_ app]
    app)

  
  ValitseUrakka
  (process-event [{urakka :urakka} app]
    (valitse-urakka app urakka))

  
  NakymastaPoistuttiin
  (process-event [_ app]
    app)

  
  PaivitaLomake
  (process-event [{lomake :lomake} app]
    (prn "PaivitaLomake: " lomake)
    (assoc app :muokattava-muutos lomake)))
