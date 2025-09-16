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
            [harja.ui.nakymasiirrin :as siirrin]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tyokalut.tuck :as tuck-apurit]))


(defonce nakymassa? (atom false))
(def johto-ja-hallintokorvausmuutokset-atom (atom nil))
(def muutoksien-kayttoonoton-hoitokauden-alkuvuosi 2025)
(def pakolliset-kentat-fmt {:nimi "Nimi"
                            :tyyppi "Tyyppi"
                            :syy "Muutoksen syy"
                            :voimassa_alkaen "Voimassa alkaen"})
(defonce ^{:private true} nollatut-valinnat {:muokattava-muutos nil
                                             :haku-kaynnissa? false
                                             :tallennus-kesken? false
                                             :kirjatut-muutokset nil
                                             :tehtava-maaramuutokset nil
                                             :rahavarausten-muutokset nil
                                             :tavoitehinnan-muutokset nil
                                             :suunniteltujen-maarien-muutokset nil
                                             :budjettitavoitteet nil
                                             :taulukko-nakyvissa? {:kirjatut-muutokset true
                                                                   :lasketut-muutokset true
                                                                   :rahavarausten-muutokset true
                                                                   :tavoitehinnan-muutokset true
                                                                   :suunniteltujen-maarien-muutokset true}})


;; Hae muutostiedot
(defrecord HaeUrakanMuutostiedot [])
(defrecord HaeUrakanMuutostiedotOnnistui [vastaus])
(defrecord HaeUrakanMuutostiedotEpaonnistui [vastaus])


;; Vaihda hoitokausi
(defrecord MuokkaaMuutosta [rivi])
(defrecord TallennaMuutos [muutos])
(defrecord TallennaMuutosEpaonnistui [vastaus])
(defrecord ToggleTaulukonNakyvyys [taulukon-avain])
(defrecord MuokkaaRahavaraustenMuutoksienSyita [])
(defrecord HaeMuutoksenTiedot [muutos])
(defrecord HaeMuutoksenTiedotOnnistui [vastaus muutos valittu-hoitokausi])
(defrecord HaeMuutoksenTiedotEpaonnistui [vastaus])
(defrecord TallennaRahavarausmuutostenSyyt [rivit])
(defrecord TallennaRahavarausmuutostenSyytEpaonnistui [vastaus])
(defrecord TallennaRahavarausmuutostenSyytOnnistui [vastaus])


;; Liitteet
(defrecord LisaaLiite [liite])
(defrecord PoistaLisattyLiite [])
(defrecord PoistaTallennettuLiite [liite-id])
(defrecord PoistaPoistetutLiitteet [liite-id])


;; aika ennen 2025-2026 hoitovuotta
(defrecord LisaaTavoitehintojenMuutos [])
(defrecord LisaaSuunniteltujenMaarienMuutos [])


;; Päänäkymä ja listaus
(defrecord PaivitaLomake [lomake])


;; Tehtävä- määrämuutokset 
(defrecord AvaaYksikkohintaModal [valittu-modal-tehtava tehtava_id])
(defrecord SuljeYksikkohintaModal [])
(defrecord TallennaTehtavaMaaramuutokset [rivit])
(defrecord TallennaTehtavaMaaramuutoksetOnnistui [vastaus])
(defrecord TallennaTehtavaMaaramuutoksetEpaonnistui [vastaus])
(defrecord MuokkaaYksikkohintaa [rivi hoitokausien-yksikkohinnat])
(defrecord TallennaYksikkohinta [rivi])
(defrecord TallennaYksikkohintaOnnistui [vastaus])
(defrecord TallennaYksikkohintaEpaonnistui [vastaus])


;; ---------------------------------------------
(defn scrollaa-viimeksi-valitulle-riville []
  (.setTimeout js/window (fn [] (siirrin/kohde-elementti-luokka "viimeksi-valittu-tausta")) 150))


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
  (tuck-apurit/post! app :hae-urakan-muutostiedot
    {:urakka-id (-> @tila/yleiset :urakka :id)
     :hoitokaudet @u/valitun-urakan-hoitokaudet
     :valittu-hoitokausi (:valittu-hoitokausi app)}
    {:onnistui ->HaeUrakanMuutostiedotOnnistui
     :epaonnistui ->HaeUrakanMuutostiedotEpaonnistui}))


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


(defn- vastaus-haku-onnistui [app vastaus]
  (assoc app
    ;; suljetaan aina lomake kun on saatu uudet muutostiedot
    :muokattava-muutos nil
    :haku-kaynnissa? false
    :tallennus-kesken? false
    :kirjatut-muutokset (:kirjatut-muutokset vastaus)
    :tehtava-maaramuutokset (:lasketut-muutokset vastaus)
    :rahavarausten-muutokset (:rahavarausten-muutokset vastaus)
    :tavoitehinnan-muutokset (:tavoitehinnan-muutokset vastaus)
    :suunniteltujen-maarien-muutokset (:suunniteltujen-maarien-muutokset vastaus)
    :budjettitavoitteet (:budjettitavoitteet vastaus)))


;; ------------------------------------
;; Tuck 
(extend-protocol tuck/Event

  HaeUrakanMuutostiedot
  (process-event [_ app]
    (hae-urakan-muutostiedot
      (assoc
        (tuck-apurit/nollaa-tuck-tila app nollatut-valinnat)
        :haku-kaynnissa? true
        :valittu-hoitokausi @u/valittu-hoitokausi
        :urakan-hoitokaudet @u/valitun-urakan-hoitokaudet)))


  HaeUrakanMuutostiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (vastaus-haku-onnistui app vastaus))


  HaeUrakanMuutostiedotEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Muutostietojen hakeminen epäonnistui" :varoitus viesti/viestin-nayttoaika-keskipitka)
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
  (process-event [_ app]
    (viesti/nayta-toast! "Muutoksen tietojen hakeminen epäonnistui!" :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)


  MuokkaaYksikkohintaa
  (process-event [{:keys [rivi hoitokausien-yksikkohinnat]} app]
    (let [valittu-hoitokauden-alkuvuosi (->>
                                          hoitokausien-yksikkohinnat
                                          (filter #(= (:arvo %) (:yksikkohinta rivi)))
                                          first
                                          :hoitokauden-alkuvuosi)]
      ;; Kutsutaan kun modalista valitaan yksikköhinta (ei tallenneta vielä)
      (-> app
        ;; Päivitä yksikköhinta 
        (update :valittu-modal-tehtava merge rivi)
        ;; Päivitä valittu yksikköhinnan hk 
        (assoc-in [:valittu-modal-tehtava :yksikkohinnan_alkuvuosi] valittu-hoitokauden-alkuvuosi))))


  TallennaTehtavaMaaramuutokset
  (process-event [{:keys [rivit]}
                  {:keys [_valittu-rivi] :as app}]
    (let [parametrit {:rivit rivit
                      :urakka-id (-> @tila/yleiset :urakka :id)
                      :hoitokaudet @u/valitun-urakan-hoitokaudet
                      :valittu-hoitokausi (:valittu-hoitokausi app)}]
      ;; Kutsutaan gridin tallenna napista, ei modalista 
      (tuck-apurit/post! app :tallenna-tehtava-maaramuutokset
        parametrit
        {:onnistui ->TallennaTehtavaMaaramuutoksetOnnistui
         :epaonnistui ->TallennaTehtavaMaaramuutoksetEpaonnistui})
      (-> app
        (assoc :haku-kaynnissa? true))))


  TallennaYksikkohinta
  (process-event [{:keys [rivi]}
                  {:keys [_valittu-rivi] :as app}]
    ;; Kutsutaan kun modalista tallennetaan valittu yksikköhinta 
    (let [parametrit {:rivi rivi
                      :urakka-id (-> @tila/yleiset :urakka :id)
                      :hoitokaudet @u/valitun-urakan-hoitokaudet
                      :valittu-hoitokausi (:valittu-hoitokausi app)}]

      (tuck-apurit/post! app :tallenna-maaramuutos-yksikkohinta
        parametrit
        {:onnistui ->TallennaYksikkohintaOnnistui
         :epaonnistui ->TallennaYksikkohintaEpaonnistui})
      (-> app
        (assoc
          :haku-kaynnissa? true
          :yksikkohinta-modal-auki? false))))


  TallennaTehtavaMaaramuutoksetOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Tallennus onnistui" :onnistui viesti/viestin-nayttoaika-lyhyt)
    (assoc app
      :tehtava-maaramuutokset vastaus
      :haku-kaynnissa? false))


  TallennaTehtavaMaaramuutoksetEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Tehtävä- ja määrämuutosten tallennus epäonnistui" :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))


  TallennaYksikkohintaOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Yksikköhinta tallennettu" :onnistui viesti/viestin-nayttoaika-lyhyt)
    (assoc app
      :haku-kaynnissa? false
      :tehtava-maaramuutokset vastaus))


  TallennaYksikkohintaEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Yksikköhinnan tallennus epäonnistui" :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))


  AvaaYksikkohintaModal
  (process-event [{:keys [valittu-modal-tehtava _tehtava_id]}
                  {:keys [_yksikkohinta-modal-auki?] :as app}]
    (assoc app
      :yksikkohinta-modal-auki? true
      :valittu-modal-tehtava valittu-modal-tehtava))


  SuljeYksikkohintaModal
  (process-event [_ {:keys [_yksikkohinta-modal-auki?] :as app}]
    (assoc app
      :valittu-modal-tehtava nil
      :yksikkohinta-modal-auki? false))


  MuokkaaMuutosta
  (process-event [{:keys [rivi]} app]
    (if (some? rivi)
      (assoc app  :viimeksi-valittu rivi :muokattava-muutos rivi)
      (assoc app :muokattava-muutos rivi)))


  TallennaMuutos
  (process-event [{:keys [muutos]} app]
    (let [urakka (:urakka @tila/yleiset)
          puuttuvat-pakolliset-kentat (map
                                        #(get pakolliset-kentat-fmt %)
                                        (lomake/puuttuvat-pakolliset-kentat muutos))
          muutos (lomake/ilman-lomaketietoja muutos)
          kulut (when (= (:tyyppi muutos) "johto-ja-hallintokorvaus")
                  ;; luodaan vain kuluja, joiden summa on eri suuri kuin 0 (eli niillä on jotain vaikutusta laskentoihin)
                  (filter #(and
                             (some? (:tavoitehinnan-muutos %))
                             (not= 0 (:tavoitehinnan-muutos %)))
                    (vals @johto-ja-hallintokorvausmuutokset-atom)))
          muutos (assoc muutos :kulut kulut)]

      (if-not (empty? puuttuvat-pakolliset-kentat)
        (assoc-in app [:muokattava-muutos :puuttuvat-pakolliset-kentat] puuttuvat-pakolliset-kentat)
        (do
          (tuck-apurit/post! :tallenna-muutos
            {:urakka-id (:id urakka)
             :valittu-hoitokausi (:valittu-hoitokausi app)
             :hoitokaudet @u/valitun-urakan-hoitokaudet
             :muutos muutos}
            {:onnistui ->HaeUrakanMuutostiedotOnnistui ;; voidaan käyttää samaa eventtiä, koska haetaan uudet muutostiedot tallennuksen jälkeen
             :epaonnistui ->TallennaMuutosEpaonnistui
             :paasta-virhe-lapi? true})
          (assoc app :tallennus-kesken? true)))))


  TallennaMuutosEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Muutoksen tallentaminen epäonnistui! "
                           (get-in vastaus [:response :virhe])) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :tallennus-kesken? false))


  TallennaRahavarausmuutostenSyytEpaonnistui
  (process-event [{:keys [_vastaus]} app]
    (viesti/nayta-toast! "Rahavarauksien muutosten syiden tallentaminen epäonnistui!" :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)


  TallennaRahavarausmuutostenSyytOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Tallennus onnistui" :onnistui viesti/viestin-nayttoaika-lyhyt)
    (vastaus-haku-onnistui app vastaus))


  ToggleTaulukonNakyvyys
  (process-event [{taulukon-avain :taulukon-avain} app]
    (assoc-in app [:taulukko-nakyvissa? taulukon-avain]
      (not (get-in app [:taulukko-nakyvissa? taulukon-avain]))))


  MuokkaaRahavaraustenMuutoksienSyita
  (process-event [_ app]
    (assoc app :rahavarausten-syyt-muokattavana? true))


  TallennaRahavarausmuutostenSyyt
  (process-event [{:keys [rivit]} app]
    (let [urakka (:urakka @tila/yleiset)]
      (tuck-apurit/post! :tallenna-rahavarausmuutosten-syyt
        {:urakka-id (:id urakka)
         :hoitokaudet @u/valitun-urakan-hoitokaudet
         :valittu-hoitokausi (:valittu-hoitokausi app)
         :rivit (map #(select-keys % [:id :syy]) rivit)}
        {:onnistui ->TallennaRahavarausmuutostenSyytOnnistui
         :epaonnistui ->TallennaRahavarausmuutostenSyytEpaonnistui})
      app))


  HaeMuutoksenTiedot
  (process-event [{:keys [muutos]} app]
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
    [{:keys [liite]} app]
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


  PaivitaLomake
  (process-event [{:keys [lomake]} app]
    ;; (prn "PaivitaLomake: " lomake)
    (assoc app :muokattava-muutos lomake)))
